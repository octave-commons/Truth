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
`docs/specs/perf-60fps-parallel-tick.md`.

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
  implementation specs in `docs/specs/`, dated engineering notes in
  `docs/notes/`.
