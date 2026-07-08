# Gates of Truth

A full simulated universe in pure Clojure (JVM) — the 3D redesign of Gates of
Aker. You begin Phase 0 as a quantum spark witnessing a stellar nebula collapse
into a star system, and the same world continues, cooling and becoming more
articulate, into geology, ecology, and civilization.

## The one rule that matters most

**There is a single simulation substrate: the ECS world (`domain.ecs.core`).**

Every phase is a *content layer* over that one world — new components and new
ordered systems — **never a parallel simulation with its own world model**. Phase
0 physics is `domain.genesis` (its narrative/quest layer is `domain.arc`); it is
the only Phase 0 sim. There is one renderer, `infra.render`, which reads the ECS
world as pure data.

This is not a style preference. A second world model was added once (a particle
gas field running beside the ECS world) and silently became the thing the live
window rendered, while new physics went into the ECS path — so the two drifted
apart. That is now removed and forbidden, and `test/architecture_test.clj` fails
the build if it returns. If you want new physics, add a system + components to
`domain.genesis/physics-systems-parallel`; do not start a second engine.

## Architecture (four quadrants)

```
src/
  domain/   ← pure simulation logic, zero I/O (physics, ECS, myth engine)
  infra/    ← rendering (LWJGL/OpenGL), persistence, input, model calls
  shape/    ← coordinate transforms, sphere/geodesic geometry, vectors
  law/      ← Malli/contract schemas, invariants, physical constants
```

`domain/` never imports from `infra/`. New systems are introduced as schema
(`law/`) → failing test (`test/`) → minimal implementation (`domain/`).

## Phase 0 physics

Phase 0 couples gravity, thermodynamics, and an electromagnetic / MHD-lite layer
on the single ECS world. Each force/field is its own **system** owning the
components it writes; the tick fans them out concurrently over a frozen snapshot
and folds the disjoint writes at one barrier, so **system order does not matter**
(see `docs/notes/2026.06.26-ecs-double-buffer-single-writer-spec.md`). Each
component has exactly one writer — gravity→`accel.gravity`, motion→`position`/
`velocity`, structure→`radius`/`density`, classifier→`matter-state`,
thermal→`temperature`, field→`b-field`, … — enforced by `architecture_test`.

The double-buffer fan-out (`domain.genesis/physics-systems-parallel`, run by
`domain.ecs.tick/run-parallel`) is the **only** tick path — the old sequential
pipeline is deleted, and there is no serial "barrier" tier: every system,
lifecycle included, reads the frozen snapshot and emits a single-writer write-set
(entity spawn/reap goes through `spawn-request.*`/`consumed.*` markers
materialized at world-construction). The formation physics — Jeans collapse →
protostar → ignition / brown-dwarf — is grounded in real solar-system formation
(`docs/notes/2026.06.26-authentic-phase0-formation-physics.md`).

- **`domain.regime`** — the dimensionless-number classifier (plasma β, Mach,
  Alfvén-Mach, Jeans ratio) that tags each clump's dominant physics. The keystone.
- **`domain.em`** — magnetic field per clump: flux-freezing (B ∝ ρ^⅔ under
  compression), magnetic-pressure support, non-ideal resistive decay.
- **`law.field`** — SI constants and field invariants (the codebase is SI; the
  research notes are Gaussian — see the design docs).

## Research program

A family of periodic deep-research actors investigates every domain the
simulation touches: cosmology, physics, geology, atmosphere, biology, and
culture. Each produces notebooks with governing equations in LaTeX, Clojure
pseudocode mapped to ECS patterns, charts, and a promotion path to domain code.

**Research index:** [`docs/research/INDEX.md`](docs/research/INDEX.md) — browse
all notebooks by domain, status, and phase coverage.

### Domains

| Domain | Notebook(s) | Status |
|--------|-------------|--------|
| **Cosmology** | [Primordial nucleosynthesis yields](docs/research/cosmology/primordial-nucleosynthesis-yields.md) — `Y_p=0.247`, `D/H=2.53e-5`, Li7 gap | `spec-derivation` |
| | [BBN calculator (Jupyter)](docs/research/cosmology/bbn_yields.ipynb) — Clojure BBN with ASCII charts, 4/4 validation | `validated` |
| | [Stellar SED template grid](docs/research/cosmology/stellar-sed-template-grid.md) — 12-template minimum grid, band ratios 10²–10⁴× | `validated` |
| **Physics** | [Phase 1 radiation & plasma](docs/research/phase1-radiation-plasma-truth.md) — panchromatic SEDs, 4-layer atmospheres, Parker winds, XUV escape | `spec-derivation` |
| **Atmosphere** | [XUV escape regime transition](docs/research/atmosphere/xuv-escape-regime-transition.md) — `R = t_rec/t_flow` controls escape regime, `F_crit ~ 10⁴ erg/cm²/s` | `validated` |

Research has already produced three spec files in `src/law/`:

| Spec File | Source | Contents |
|-----------|--------|----------|
| [`law/composition.clj`](src/law/composition.clj) | Cosmology BBN yields | Primordial H/He/D/Li composition, metallicity, composition schema |
| [`law/sed.clj`](src/law/sed.clj) | Phase 1 radiation §2–3 | SED bands, profiles, atmosphere shells, band helpers |
| [`law/plasma.clj`](src/law/plasma.clj) | Phase 1 radiation §4–6 | Wind profiles, plasma wind parcels, atmospheric escape, space-weather events |

New ECS component keywords added: `sed-bands`, `atmosphere-shells`,
`wind-profile`, `atmosphere-escape`, `event-source`, `lod-level`.

### Actors

Research is produced by seven periodic ημ actors (see
[`.eta-mu/actors/`](.eta-mu/actors/)):

- `truth-research-cosmology` (48h) — stellar physics, nucleosynthesis, CMB
- `truth-research-physics` (48h) — SPH, N-body, MHD, orbital mechanics
- `truth-research-geology` (48h) — tectonics, mantle convection, cratering
- `truth-research-biology` (48h) — ecology, evolution, abiogenesis
- `truth-research-atmosphere` (48h) — radiative transfer, climate, escape
- `truth-research-culture` (48h) — agent-based social models, mythogenesis
- `truth-research-coordinator` (72h) — cross-domain index, gap analysis

## Design docs and specs

Design documents capture architectural decisions and simulation design:

- [World generation phases](docs/designs/gates-of-truth-world-gen-phases.md)
- [Phase 0 stellar nebula design](docs/designs/truth-phase-0-stellar-nebula-design.md)
- [Phase 0 coupled physics & regime classifier](docs/designs/phase0-coupled-physics-and-regime-classifier.md)
- [Phase 0 volumetric renderer](docs/designs/phase0-volumetric-renderer.md)
- [Sink particle formation](docs/designs/phase0-sink-particle-formation.md)
- [Simulation methods research](docs/designs/simulation-methods-research.md)

Implementation specs (driven by the Kanban process):

- [Jeans-driven formation](kanban/tasks/phase-0-jeans-driven-formation-spec.md)
- [SPH density field](kanban/tasks/phase-0-sph-density-field-spec.md)
- [Protoplanetary disk](kanban/tasks/phase-0-protoplanetary-disc-implementation-spec.md)
- [Planet formation pipeline](kanban/tasks/phase-0-complete-planet-formation-pipeline-spec.md)
- [Chemistry & differentiation](kanban/tasks/phase-0-chemistry-differentiation-spec.md)
- [Stellar winds & mass loss](kanban/tasks/phase-0-stellar-winds-mass-transfer-remnants-technical-spec.md)
- [Sink formation](kanban/tasks/stage-2-sink-formation-technical-spec.md)
- [Habitability handoff](kanban/tasks/phase-0-habitability-handoff-spec.md)
- [Player focus & dual representation](kanban/tasks/phase-0-player-focus-dual-representation-spec.md)
- [Narrator presence](kanban/tasks/phase-0-narrator-presence-spec.md)

## Current codebase

| Module | Contents |
|--------|----------|
| [`domain.ecs`](src/domain/ecs/) | ECS core — world, entities, components, systems, DSL |
| [`domain.phase0`](src/domain/phase0.clj) | Phase 0 physics pipeline — gravity, hydro, MHD-lite |
| [`domain.gravity`](src/domain/gravity/) | Gravitational solver (Barnes-Hut tree) |
| [`domain.em`](src/domain/em.clj) | Magnetic field — flux-freezing, magnetic pressure, resistive decay |
| [`domain.regime`](src/domain/regime.clj) | Dimensionless-number regime classifier (β, Mach, Jeans) |
| [`domain.hydro`](src/domain/hydro.clj) | Hydrodynamics — SPH density, pressure forces |
| [`domain.stellar`](src/domain/stellar.clj) | Stellar structure — polytropes, fusion ignition, mass-radius |
| [`domain.chemistry`](src/domain/chemistry.clj) | Chemical network — ionisation, molecules, dust |
| [`domain.orbital`](src/domain/orbital/) | Orbital mechanics — N-body integration, Kepler |
| [`domain.spatial`](src/domain/spatial/) | Spatial partitioning — kd-tree, neighbor search |
| [`domain.integrator`](src/domain/integrator.clj) | Time integration — Leapfrog, adaptive timestep |
| [`domain.pacing`](src/domain/pacing.clj) | Simulation pacing — wall-clock mapping, LOD zones |
| [`domain.intervention`](src/domain/intervention.clj) | Player interventions — narrative injection |
| [`domain.player`](src/domain/player.clj) | Player state — focus, perception, camera |
| [`domain.world_bootstrap`](src/domain/world_bootstrap.clj) | World initialization — initial conditions |
| [`infra.render`](src/infra/render.clj) | LWJGL/OpenGL renderer — shaders, camera, pipeline |
| [`infra.dev`](src/infra/dev/) | Dev tools — window, nREPL, debugging |
| [`law.*`](src/law/) | Malli schemas — composition, SED, plasma, field, contracts |
| [`shape.*`](src/shape/) | Geometry — coordinate transforms, sphere math |

## Static analysis

Six deterministic code-quality tools gate the build:

```bash
bin/analyze            # full report (clj-kondo, smells, splint, lsp, jscpd, cljfmt)
bin/analyze --strict   # CI mode — fails on HARD structural breaches
bin/analyze --fix      # auto-format with cljfmt
```

See [`docs/STATIC-ANALYSIS.md`](docs/STATIC-ANALYSIS.md) for thresholds and
individual tool commands.

## Running

Requires a JDK and the Clojure CLI.

```bash
clojure -M:test                 # run the test suite (incl. architecture guards)
clojure -M:run                  # Phase 0 console simulation
clojure -M:run demo             # render one Sun/Earth/Moon frame to /tmp/truth-view.png
clojure -M:dev                  # live GLFW dev window + nREPL on 127.0.0.1:7888
```

With the dev service running (managed by pm2 as `gates-of-truth-dev`), connect a
REPL and drive the live window:

```clojure
(require '[infra.dev.window :as w])
(swap! (:camera @w/service-state) assoc :distance 80.0)
(w/reload-shaders!)                       ; after editing infra.render shaders
(w/take-screenshot! "/tmp/truth-dev.png")
```

## Actor Dashboard

There is a lightweight web dashboard for the ημ actor system at
[`src/infra/dev/actor_dashboard.clj`](src/infra/dev/actor_dashboard.clj). It
auto-discovers every actor under `.eta-mu/actors/`, including `fork-tax-tender`,
and shows session counts, inbox/outbox activity, live process status, and recent
research notebooks.

Run it with:

```bash
clj -M:dashboard
```

Then open <http://127.0.0.1:7889/>. The page auto-refreshes every 30 seconds.

Agents should mention the dashboard when creating, changing, or debugging actors
so that humans and other agents can monitor them without reading the filesystem.

## Conventions

See `AGENTS.md` for the full house rules: threading macros over nested lets,
`!`-suffixed names for impurity, docstrings on public vars, and tests as
epistemic contracts (write the spec/test before the implementation).
