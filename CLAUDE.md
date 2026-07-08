# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Gates of Truth — a full simulated universe in pure Clojure (JVM), rendered with
LWJGL/OpenGL. Phase 0 simulates a stellar nebula collapsing into a star system
with real formation physics (gravity, SPH hydro, MHD-lite, chemistry, stellar
structure). `README.md` and `AGENTS.md` carry the project narrative and house
rules, but parts of them predate the `domain.phase0` → `domain.genesis` rename
and the parallel-tick migration — this file and the code are current.

## Commands

```bash
clojure -M:test                              # full test suite (incl. architecture guards)
clojure -M:test -n domain.stellar-test       # one test namespace (cognitect test-runner)
clojure -M:test -v domain.stellar-test/a-var # one test var

bin/analyze                                  # static analysis (clj-kondo, splint, smells, cljfmt, jscpd)
bin/analyze --strict                         # CI mode — also fails on HARD structural breaches
bin/analyze --fix                            # auto-format with cljfmt

bin/coverage --text                          # cloverage text summary (fast)
bin/bench :ecs :gravity                      # criterium benchmarks (:ecs :gravity :hydro :phase0 :profile)
bin/mutate                                   # mutation testing (Heretic, local-only, domain/law/shape)

clojure -M:run                               # Phase 0 console simulation
clojure -M:run demo                          # render one frame to /tmp/truth-view.png
```

### Dev service (pm2)

The live GLFW window + nREPL (`clj -M:dev`) runs continuously under pm2 as
`gates-of-truth-dev`, with watching OFF. To load code changes:
`pm2 restart gates-of-truth-dev` (fresh nebula), or hot-reload through the nREPL
on `127.0.0.1:7888` to keep the live world. "Address already in use" on port
7888 means the pm2 instance is already up — don't start a second one.

```clojure
(require '[infra.dev.window :as w])
(w/reload-shaders!)                       ; after editing infra.render shaders
(w/take-screenshot! "/tmp/truth-dev.png") ; note: uses its own bodies-fn/camera,
                                          ; not identical to the live window
```

## Architecture

### Four quadrants (no junk drawers)

```
src/
  domain/   ← pure simulation logic, ZERO I/O (physics, ECS, ecology, arc)
  infra/    ← rendering (LWJGL/OpenGL), dev window, input, inspection
  shape/    ← coordinate transforms, sphere/geodesic geometry, vectors
  law/      ← Malli schemas, contracts, physical constants (SI units)
```

`domain/` never imports `infra/`. No `utils/` or `helpers/` namespaces.
`test/architecture_test.clj` enforces these rules and fails the build on
violation — if it fails, converge; never add a flag or exemption.

### Single ECS substrate (the one rule that matters most)

There is exactly ONE world model: the ECS world (`domain.ecs.core`). Every
phase is a content layer over it — new components + systems — never a parallel
simulation. Phase 0 physics lives in `domain.genesis` (renamed from
`domain.phase0`; keywords are `:genesis/*`, narrative events `:arc/*` in
`domain.arc`). There is one renderer, `infra.render`, which reads the ECS world
as pure data. New physics = a new system + components added to
`genesis/physics-systems-parallel`, not a new engine. A second world model was
added once, silently became what the window rendered, and drifted — that is why
this is a test, not a preference.

### Double-buffer / single-writer tick (fundamental)

`domain.genesis/step-physics` runs ONE Jacobi fan-out per tick
(`domain.ecs.tick/run-parallel`): every system reads the same frozen snapshot
and emits a write-set for the component types it exclusively owns
(`{:id kw :writes #{ctype ...} :run (fn [frozen] write-set)}`), declared in
`domain.ecs.registry`. Consequences:

- System order does not matter; writes are disjoint and fold at one barrier.
- Forces propagate with one tick of Jacobi lag (the integrator sums accel
  channels emitted last tick).
- There is NO serial "barrier" tier and no exemptions — every system, including
  collision, sink formation, and disk evolution, is a fan-out emitter. Do not
  reintroduce serial post-fold phases.
- Entity creation/removal goes through lifecycle markers (`spawn-request.*`,
  `consumed.*` components) materialized/reaped at world-construction
  (`materialize-lifecycle`), which is Jacobi-consistent by construction.
- One writer per component type, enforced by `architecture_test`.

Spec: `docs/notes/2026.06.26-ecs-double-buffer-single-writer-spec.md` and
`kanban/tasks/perf-60fps-parallel-tick.md`.

### Time model

Fixed 60Hz ticks; `dt` dilates with the bulk-collapse dynamical time. Any
process paced "per tick" (condensation, magnetic braking, SPH smoothing-length
adaptation) must scale by sim-time/dt or it silently changes speed with the
clock — this class of bug has recurred.

### Coordinates and rendering

The whole stack is z-up (sim spins about z, disk in the xy plane); camera,
render, and movement code must match — never reintroduce a `[0 1 0]` up vector.
Body rendering is true-scale (linear radii, no exaggeration). The nebula is a
ray-marched volumetric froxel fog, not sprites; offscreen GL → PNG works
headlessly. Two coordinate paths exist in the renderer: `:body` uses the
model-matrix, `:particle`/`:line` use raw positions — debug markers therefore
cannot validate body placement.

`docs/designs/ux-architecture.md` is canonical for all user interaction; much
current UX/render code is acknowledged ad-hoc, not design intent.

## Conventions

- Workflow for new behavior: schema in `law/` → failing test in `test/` →
  minimal implementation in `domain/`. Tests are epistemic contracts.
- Threading macros (`->`, `->>`) over nested `let`; `when-let` over `let`+`if`.
- Impure functions end in `!` or live in `infra/`. Everything else is pure.
- Docstrings mandatory on public vars (one-line summary, blank line, detail).
- Units are SI throughout `law/` and `domain/` (research notes may be Gaussian).
- Spawned orbiters (disk fragments, planets) must use
  `law.stellar/softened-circular-speed` — unsoftened Kepler inside the
  softening length ejects them.

## Agent workflow (shared with OpenCode)

This repository is cohabited by **Claude Code** and **OpenCode**. Both agents
read the same project state and must write shared artifacts in the same format.
The canonical skills live under `~/.agents/skills/`.

### Receipt River

Non-trivial work must leave a trace in the append-only `receipts.edn`. The
project already has one at the repository root (the `.ημ/` copy is used only if
it already exists; do not create a second ledger).

Use the harness-agnostic bb scripts:

```bash
~/.agents/skills/receipt-river/scripts/rr-tail.bb --limit 20
~/.agents/skills/receipt-river/scripts/rr-append.bb \
  --kind :observation \
  --origin "domain.genesis refactor" \
  --note "Starting refactor of accretion logic"
```

Canonical keys: `ts kind origin owner dod pi host manifest refs` (required),
plus `note tests decisions drift`. Read the last ~20 lines before any major
decision; never edit or delete past lines.

### Session Mycology

After a hard or repetitive turn, score it and append a reflection to
`.ημ/session-mycology/ledger.md`. If the pattern generalizes with confidence
`>= 0.7`, create a candidate spore under `.ημ/session-mycology/spores/`.

```bash
~/.agents/skills/session-mycology/scripts/sm-log.bb \
  --task "Refactored hydro cache rebuild" \
  --efficiency 0.75 \
  --friction 0.4 \
  --candidate 0.8 \
  --note "Cache-equivalence test had to become windowed; document this"
~/.agents/skills/session-mycology/scripts/sm-spore.bb \
  --slug "windowed-cache-equivalence" \
  --task "Refactored hydro cache rebuild" \
  --problem "Cache-equivalence tests fail when physics changes from stale geometry" \
  --pattern "Any Jacobi-lag cache needs a windowed equivalence test, not bit-equality" \
  --better-path "Write a spec for windowed equivalence before changing the cache" \
  --candidate 0.8
```

Do not promote your own spores during the same session; the `spore-reviewer`
actor decides promotion.

### Fork Tax (Π)

When the user asks for a handoff, snapshot, or "full dump", pay the fork tax.
Assume the workspace may be shared with other agents.

```bash
# Verify, write .ημ/Π_* artifacts, commit, tag, push.
# Prefer path-scoped staging; never blanket-reset unrelated dirt.
```

Steps:

1. Check `git status` and split dirt into owned paths, concurrent/unowned paths,
   and blocked/generated paths.
2. Run the smallest verification that covers the owned paths.
3. Write/update `.ημ/Π_STATE.sexp`, `.ημ/Π_MANIFEST.sexp`, and `.ημ/Π_LAST.md`,
   explicitly noting any concurrent dirt you did not absorb.
4. Commit only the owned/stageable changes.
5. Create a deterministic tag `Π-YYYYMMDDhhmmss`.
6. Push branch + tag; record failures verbatim if blocked.

### Shared memory system

Claude Code's project memories live under
`~/.claude/projects/-home-err-spaces-Truth/memory/`. OpenCode has a local plugin
(`.opencode/plugins/claude-memory-bridge/`) that reads and writes the same
Markdown + YAML-frontmatter files and keeps `MEMORY.md` index in sync.

Treat that directory as the single source of durable project context. When you
create or update a memory, keep the same format:

```markdown
---
name: memory-slug
description: One-line summary for the index
metadata:
  node_type: memory
  type: project
---

Detailed content with [[other-memory]] wiki links.
```

And update `MEMORY.md`:

```markdown
- [Memory slug](memory-slug.md) — one-line summary for the index
```

### Working alongside OpenCode

- Read `receipts.edn` tail before major decisions; expect `:owner "opencode"`
  entries.
- Do not delete, re-order, or rewrite receipt lines.
- Prefer the bb scripts for receipt/session-mycology operations so both agents
  get the same formatting.
- When OpenCode creates a memory, it will appear in the same Claude memory
  directory; review it as you would a memory you wrote yourself.

## Gotchas

- `create-world` is nondeterministic run-to-run despite a fixed seed
  (pre-existing, unrooted).
- Merges must not volume-sum radii for compact objects (past bug: stars
  ballooned into cold blobs); star temperature is re-derived from virial(M, R)
  every tick, so radius bugs surface as temperature bugs.
- Several features are coded but never ticked (inter-body EM field coupling,
  chemistry evolution paths, some player mechanics) — they look dead but are
  incomplete, not abandoned.
- Kanban process state lives in `kanban/`; design docs in `docs/designs/`,
  implementation specs in `kanban/tasks/`, dated engineering notes in
  `docs/notes/`.
