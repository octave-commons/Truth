# Gates of Truth

A full simulated universe in pure Clojure (JVM) — the 3D redesign of Gates of
Aker. You begin Phase 0 as a quantum spark witnessing a stellar nebula collapse
into a star system, and the same world continues, cooling and becoming more
articulate, into geology, ecology, and civilization.

## The one rule that matters most

**There is a single simulation substrate: the ECS world (`domain.ecs.core`).**

Every phase is a *content layer* over that one world — new components and new
ordered systems — **never a parallel simulation with its own world model**. Phase
0 is `domain.phase0`; it is the only Phase 0 sim. There is one renderer,
`infra.render`, which reads the ECS world as pure data.

This is not a style preference. A second world model was added once (a particle
gas field running beside the ECS world) and silently became the thing the live
window rendered, while new physics went into the ECS path — so the two drifted
apart. That is now removed and forbidden, and `test/architecture_test.clj` fails
the build if it returns. If you want new physics, add a system + components to
`domain.phase0/physics-systems`; do not start a second engine.

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
on the single ECS world. The tick pipeline (`domain.phase0/physics-systems`):

```
gravity → regime → EM → collapse(hydro) → fusion → thermal → classify → collision
```

- **`domain.regime`** — the dimensionless-number classifier (plasma β, Mach,
  Alfvén-Mach, Jeans ratio) that tags each clump's dominant physics. The keystone.
- **`domain.em`** — magnetic field per clump: flux-freezing (B ∝ ρ^⅔ under
  compression), magnetic-pressure support, non-ideal resistive decay.
- **`law.field`** — SI constants and field invariants (the codebase is SI; the
  research notes are Gaussian — see the design docs).

Design docs live in `docs/designs/`, research notes in `docs/notes/`.

## Running

Requires a JDK and the Clojure CLI.

```bash
clojure -M:test                 # run the test suite (incl. architecture guards)
clojure -M:run                  # Phase 0 console simulation
clojure -M:run demo             # render one Sun/Earth/Moon frame to /tmp/truth-view.png
clojure -M:dev                  # live GLFW dev window + nREPL on 127.0.0.1:7888
```

With the dev service running, connect a REPL and drive the live window:

```clojure
(require '[infra.dev.window :as w])
(swap! (:camera @w/service-state) assoc :distance 80.0)
(w/reload-shaders!)                       ; after editing infra.render shaders
(w/take-screenshot! "/tmp/truth-dev.png")
```

## Conventions

See `AGENTS.md` for the full house rules: threading macros over nested lets,
`!`-suffixed names for impurity, docstrings on public vars, and tests as
epistemic contracts (write the spec/test before the implementation).
