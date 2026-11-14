# Gates of Truth — Agent Instructions

Single source of truth for every agent harness here (Claude Code, Codex,
OpenCode, eta-mu). `CLAUDE.md` imports this file and adds only Claude-Code-only
notes; put every shared rule here.

## Mission

You are an expert collaborator on **Gates of Truth**, a full-stack pure Clojure
3D planetary simulation game — the successor to Gates of Aker. The project lives
at the intersection of simulation engineering, procedural myth, and interactive
fiction. Your role is architect, pair programmer, epistemic partner, and lore
collaborator.

It is a full simulated universe in pure Clojure (JVM), rendered with
LWJGL/OpenGL. Phase 0 simulates a stellar nebula collapsing into a star system
with real formation physics (gravity, SPH hydro, MHD-lite, chemistry, stellar
structure). Planned, not yet in `src/`: a lore layer seeded from a folder of
media (markdown, PDF, TXT, images, audio), read by a multimodal LLM (Gemma4:e4b
or equivalent), with a co-modal embedding model powering the Facet system.

## Working rules

- Read the newest ~20 receipts and recent session-mycology lessons before major
  decisions (see Shared ledgers).
- Ground work in specs, always: `docs/specs/`, `docs/notes/specs/`, `kanban/tasks/`.
- Never cut corners: no fallbacks, no stopgaps; fail loudly and gracefully.
  Correct is better than easy.
- Document everything: shared agent rules in `AGENTS.md` (Claude-only notes in
  `CLAUDE.md`), plus `README.md`, `docs/specs`, `docs/research`, `docs/notes`.
- Use the shared project memory (see Shared memory).
- Task flow and the Definition of Done gates: `PROCESS.md`.

## Commands

```bash
clojure -M:test                         # full suite incl. architecture guards (-n ns / -v var to narrow)
bin/test architecture                   # grouped runners: unit integration domain infra law shape render architecture
bin/analyze --strict                    # static-analysis gate; must exit 0 (--fix runs cljfmt)
bin/bench :ecs :gravity                 # criterium benchmarks; stop gates-of-truth-dev first
clojure -M:demo check                   # validate the seven demo scenario/arc contracts
```

Full command reference, demo and capture notes, and the pm2/nREPL runbook:
`docs/OPERATIONS.md`. Testing detail: `docs/TESTING.md`. The legacy `:run`
alias targets the absent `infra.main`; it is not a launch route.

The dev window + nREPL (`clj -M:dev`, 127.0.0.1:7888) runs continuously under
pm2 as `gates-of-truth-dev`, with watching off. Load code changes with
`pm2 restart gates-of-truth-dev` (fresh nebula) or hot-reload through the nREPL
to keep the live world. "Address already in use" on 7888 means the pm2 instance
is up; never start a second one.

## Architecture Invariants

`test/architecture_test.clj` fails the build on the tested rules below. If it
fails, you are splitting reality in two: converge, never add a flag or exemption.

### Namespace law (four quadrants, no junk drawers)

- `domain/`: pure simulation logic, ZERO I/O (physics, ECS, ecology, orbital
  mechanics, civilization, myth engine, narrative arc).
- `infra/`: rendering (LWJGL/OpenGL), dev window, input dispatch, inspection,
  persistence I/O, and the future LLM/embedding model calls.
- `shape/`: coordinate transforms, sphere/geodesic geometry, vectors, spatial.
- `law/`: Malli schemas, contracts, guards, physical constants (SI units).

`domain/` never imports `infra/` (tested). No `utils/` or `helpers/` namespaces
(convention; not covered by the test). Every cross-boundary call must name a
Malli validator.

### Single Simulation Substrate (ONE PATH)

There is exactly ONE world model: the ECS world (`domain.ecs.core`). Every phase,
from the Phase 0 nebula through civilization, is a content layer over it (new
components + systems), never a parallel simulation with its own world type.

- Phase 0 physics is `domain.genesis` (renamed from `domain.phase0`; keywords
  `:genesis/*`; narrative events `:arc/*` live in `domain.arc`). Never add a
  second world representation; the test forbids the old `domain.particles` path
  and `:genesis/field`, `:genesis/mesh`, `:genesis/mode` world keys.
- New physics = a new system + components added to
  `domain.genesis/physics-systems-parallel`, not a new engine. Worked example:
  the magnetic field (`domain.em`) and regime classifier (`domain.regime`).
- One renderer, `infra.render`, reads the ECS world as pure data. Never fork a
  second renderer namespace (tested).
- A second world model was added once, silently became what the window
  rendered, and drifted. That is why this is a test, not a preference.

### Double-buffer / single-writer tick

`domain.genesis.tick/step-physics` runs ONE Jacobi fan-out per tick
(`domain.ecs.tick/run-parallel`). Every system reads the same frozen snapshot
and emits a write-set for the component types it exclusively owns
(`{:id kw :writes #{ctype ...} :run (fn [frozen] write-set)}`), with reads and
writes declared in `domain.ecs.registry`.

- System order does not matter; writes are disjoint and fold at one barrier.
  Forces propagate with one tick of Jacobi lag.
- There is NO serial "barrier" tier and no exemptions: every system, including
  collision, sink formation and disk evolution, is a fan-out emitter. Do not
  reintroduce serial post-fold phases.
- Entities are created and removed through lifecycle markers (`spawn-request.*`,
  `consumed.*`) materialized/reaped at world construction
  (`materialize-lifecycle`). Each owner has its own `consumed.*` marker; never
  emit another system's.
- One writer per component type (`reg/write-conflicts` empty), tested. Declare
  every component a system writes in its registry `:writes` and every one it
  reads in `:reads`; undeclared access is a latent single-writer violation the
  guard cannot see, a bug even when the build is green.

Specs: `docs/notes/specs/2026.06.26-ecs-double-buffer-single-writer-spec.md`,
`docs/notes/specs/2026.06.29-unified-physical-state-integrator-spec.md`,
`kanban/tasks/perf-60fps-parallel-tick.md`.

### No special cases: everything rides the uniform path

The tick has one way to do each thing. If a feature "doesn't quite fit," it
reshapes to fit or the uniform mechanism grows; it never gets an exemption.

- Forces, mass flux, recoil and torque all flow through
  `domain.integrator.base/influence-registry`. A system emits a self-owned
  influence component on each affected entity; the integrator, sole writer of
  physical state, folds every contributor with the generic `:compose :sum`. A
  new influence = one keyword in an `:accumulate` list + one `def` in
  `domain.ecs.components`, never a hand-written scan/route/dispatch inside the
  integrator.
- Cross-entity effects stay uniform: a transfer (donor → sink) emits the
  self-owned influence on both entities directly (one component type written to
  several entity ids in one write-set is still single-writer), never one rich
  event the integrator unpacks and routes by id. Worked example:
  `domain.mass-transfer` → `c/mass-flux-transfer` / `c/dv-transfer`.
- The urge to special-case is the signal to stop and make the general mechanism
  handle it, or ask. A special case is a fork in reality.

### Performance is a correctness property (no O(N) storms)

Every system runs every entity, every tick, and a nebula is thousands of gas
parcels. Before writing a per-entity loop, ask what it costs at N=10⁴.

- Never run a spatial neighbour query (or any O(N) inner scan) over all parcels
  unless the physics is genuinely all-pairs. Accretion sinks are resolved bodies
  only (`sink-states` in `domain.mass-transfer`, not `:nebula`); treating every
  parcel as a sink is ~2N wasted queries per tick and physically wrong
  (gas-on-gas capture is SPH/merge's job). This exact bug shipped once.
- Filter the entity set before the expensive work (`entities-with` + a cheap
  state predicate), not query-then-discard.
- If you bound coverage (top-N, sampling, skip-frames), log what you dropped.
- Measure hot-path changes with `bin/bench` before and after. A green suite does
  not prove you didn't 10× the tick.

### Time model

Fixed 60 Hz ticks; `dt` dilates with the bulk-collapse dynamical time
(`domain.pacing`). Any process paced "per tick" (condensation, magnetic braking,
SPH smoothing-length adaptation) must scale by sim-time/`dt` or it silently
changes speed with the clock. This class of bug has recurred.

### Coordinates and rendering

The stack is z-up (sim spins about z, disk in the xy plane); camera, render and
movement code must match. Never reintroduce a `[0 1 0]` up vector. Bodies render
true-scale (linear radii, no exaggeration). The nebula is ray-marched volumetric
froxel fog, not sprites; offscreen GL → PNG works headlessly. `:body` uses the
model matrix while `:particle`/`:line` use raw positions, so debug markers
cannot validate body placement. `docs/designs/ux-architecture.md` is canonical
for all user interaction; much current UX/render code is ad hoc, not design intent.

## Conventions

- New behavior: schema in `law/` → failing test in `test/` → minimal
  implementation in `domain/`. Tests are epistemic contracts: Red = provisional
  observation, Green = validated fact. Write μ (spec/test) before implementation.
- Threading macros (`->`, `->>`) over nested `let`; `when-let` over `let` + `if`.
- All functions pure unless the name ends in `!` or the namespace is under `infra/`.
- Docstrings mandatory on public vars: one-line summary, blank line, detail.
- Units are SI throughout `law/` and `domain/` (research notes may be Gaussian).
- Spawned orbiters (disk fragments, planets) must use
  `law.stellar/softened-circular-speed`; unsoftened Kepler inside the softening
  length ejects them.
- World entities are ECS entity ids carrying components (see Single Simulation
  Substrate). Use `defrecord` for fixed-shape values (e.g. `shape.core/Shape`)
  and `defprotocol` for genuine roles (e.g. `domain.ecs.rewindable/Rewindable`).
- Async: virtual threads or `core.async` with explicit, named channels; never
  anonymous fire-and-forget.
- Dependencies: `deps.edn` `:deps` is the core set (Clojure 1.11.1, Malli
  0.16.4, LWJGL 3.3.3 for `infra/` rendering); tooling stays alias-scoped.
  Adding a core dependency is a policy change; say so in the PR. No HTTP or
  rendering libraries in `domain/`. LLM and embedding model calls live
  exclusively in `infra/myth_engine.clj` (not yet created).

## Static Analysis Gate

`bin/analyze --strict` must exit 0. As of 2026-07-25 all six tools gate:
clj-kondo errors *and* warnings, Splint, clojure-lsp dead code, cljfmt,
structural HARD, and jscpd against the `threshold` in `.jscpd.json` (skipped,
not failed, when node/npx is absent). Each was at zero when promoted.

**A red gate is a blocker, not a backlog item.** If you cannot fix a finding,
file a regression card. Never demote a tier back to advisory, and never leave a
kanban card `done` whose finding has returned. The opposite happened:
`static-analysis` failed on 33 consecutive pushes to `main` between 2026-07-10
and 2026-07-21 and every one landed anyway, while twelve cards read `done` with
their findings back (`kanban/tasks/static-analysis-regression-2026-07-24.md`).

`main` is protected: the `analyze` check is required with `enforce_admins: true`,
so a red tree cannot reach `main` by any route, for anyone, the owner included.

Suppressions are allowed; each carries its reason at the site:

| marker | for |
|---|---|
| `^:export` | a var deliberately public with no internal consumer (`law/` schemas, contracts, constants; debug accessors; compat aliases) |
| `UNUSED-PENDING` + `#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}` + a `kanban/tasks/…` link | "coded but never ticked" features (see Gotchas); verbose on purpose so pending work reads as pending |
| `;; Intentional:` + `#_{:splint/disable [rule]}` | a Splint rule that is wrong at that site |

Config-level exclusions live in `.lsp/config.edn`, `.clj-kondo/config.edn` and
`.splint.edn`, with inline reasons, only for genuine false positives. Details
and the verified list of working mechanisms: `docs/STATIC-ANALYSIS.md` ›
Suppression conventions.

## Gotchas

- `create-world` is nondeterministic run-to-run despite a fixed seed
  (pre-existing, unrooted).
- Merges must not volume-sum radii for compact objects (stars once ballooned
  into cold blobs). Star temperature is re-derived from virial(M, R) every tick,
  so radius bugs surface as temperature bugs.
- Several features are coded but never ticked (inter-body EM field coupling,
  chemistry evolution paths, some player mechanics). They are incomplete, not
  abandoned.
- Cards and implementation specs live in `kanban/tasks/` (board config
  `openhax.kanban.json`), design docs in `docs/designs/`, dated engineering
  notes in `docs/notes/`. Change card status through the eta-mu kanban CLI,
  never by hand-editing `status:`.

## Agent workflow

All harnesses share this repository's state and must write shared artifacts in
the same format. Assume other agents work concurrently: stage explicit paths and
never sweep up or reset unrelated dirt.

### Agent skills

Canonical global skills live in `~/.agents/skills/`. Project-local skills live
in `.agents/skills/<name>/` (`SKILL.md` + `CONTRACT.edn`; the root
`CONTRACT.edn` is eta-mu's registry). Prefer them to ad-hoc guidance:

- **dedicated-influence-channel**: add a conserved-quantity ECS flow safely.
- **receipt-driven-regression-recovery**: search `receipts.edn` for the prior
  design before rewriting regression code.
- **physics-dt-unit-mismatch**: audit `dt`/time reads when a feature suddenly
  stops producing results.
- **whitespace-tolerant-require-audits**: audit requires/usages before deleting
  or deprecating code.
- **agent-notes-splitter** (`.opencode/skill/agent-notes-splitter/SKILL.md`):
  split large conversation exports in `docs/notes/`; recovers Claude sessions
  from JSONL logs.

### Shared ledgers

- **Receipt River** (`~/.agents/skills/receipt-river/SKILL.md`): this repository
  is in legacy mode, and its one ledger is the root `receipts.edn`. Never create
  `.ημ/receipts.edn` or `.ημ/receipts/` outside a dedicated migration PR.
  Append with the skill's `rr-append.bb`, read with `rr-tail.bb --limit 20`, and
  never edit, delete, reorder or rewrite past lines. Expect entries from other
  owners (for example `:owner "opencode"`).
- **Session mycology** (`~/.agents/skills/session-mycology/SKILL.md`): after a
  hard or repetitive turn, log it with `sm-log.bb`; at candidate score ≥ 0.7
  write a spore under `.ημ/session-mycology/spores/` with `sm-spore.bb`. History
  so far is `.ημ/session-mycology/ledger.md`; never rewrite it. Never promote
  your own spore in the session that created it; the `spore-reviewer` actor
  (`.ημ/actors/spore-reviewer/`) decides.
- **Fork tax (Π)** (`~/.agents/skills/fork-tax/SKILL.md`): on a request for a
  handoff, snapshot or "full dump". Artifacts: `.ημ/Π_STATE.sexp`,
  `.ημ/Π_MANIFEST.sexp`, `.ημ/Π_LAST.md`; tag `Π-YYYYMMDDhhmmss`. Record
  concurrent dirt you did not absorb, and push failures verbatim.

### Shared memory

Durable project context lives in Claude Code's project memory,
`~/.claude/projects/<slug>/memory/` (`<slug>` = the checkout's absolute path with
`/` → `-`). OpenCode reads and writes the same files through the
`claude_memory_*` tools in `.opencode/plugin/index.ts`. Review a memory another
agent wrote as you would your own. Each memory is a Markdown file with YAML
frontmatter `name: <slug>`, `description: <one line>` and
`metadata: {node_type: memory, type: project}`, a body that may use
`[[other-memory]]` wiki links, and one `MEMORY.md` index line:
`- [Memory slug](memory-slug.md) — one-line summary`.

### Actors and research

ημ actor definitions live in `.ημ/actors/`. When asked to do or review research,
read and update `docs/research/ACTORS.md` for each research actor. When you
create or modify an actor, make sure the actor dashboard (`clj -M:dashboard`,
<http://127.0.0.1:7889/>) shows it and mention the dashboard in related docs.
The dashboard still reads the gitignored `.eta-mu/actors/` path; see
`docs/OPERATIONS.md` › Actor dashboard.
