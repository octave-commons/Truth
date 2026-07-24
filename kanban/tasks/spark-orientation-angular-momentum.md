---
uuid: "spark-orientation-angular-momentum"
title: "Spark gains orientation + angular momentum (rotation integrator, single writer)"
status: "todo"
priority: "P1"
labels: ["domain", "physics", "player", "spark", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/spark-orientation-angular-momentum.md"
category: "specs"
estimate: 5
---

# Spark gains orientation + angular momentum

> spark-flight epic, Wave 1 card 1 of 10. Design: `docs/designs/spark-flight-and-camera.md` §3.2.
> The substrate for real 6DOF piloting: the spark must have a heading and be able
> to spin, as physics state, so thrust can be applied along body axes and FA-off
> spin can persist. No input wiring here — just the rotational substrate + a
> single-writer integrator.

## Grounded integration (from design investigation, cite file:line)
- The spark is a first-class ECS body with `c/position c/velocity c/mass c/radius
  c/body-kind` (`src/domain/player/state.clj:36-63`). Add two new components to
  the observer eid: `c/orientation` (unit quaternion) and `c/angular-velocity`
  (ω, rad/s). Define Malli schemas in `law/` first (workflow: schema → failing
  test → impl).
- Add ONE new **rotation-integrator system** to
  `genesis/physics-systems-parallel` (`src/domain/genesis/systems.clj:40-129`,
  linear integrator at `:47`). It is the sole writer of `c/orientation` and
  `c/angular-velocity`; it sums torque channels (none exist yet — card 2 adds
  them) with one-tick Jacobi lag, exactly like the linear integrator
  (`kinematics.clj:76-79`). Declare its write-set in `domain.ecs.registry` — one
  writer per component type or `write-conflicts` fails (`registry.clj:536`).
- Moment of inertia may be a constant for v1; a radius-derived `I` (radius shrinks
  with formation-progress, `stellar/geometry.clj:129-150`) is an optional
  refinement, not required.
- Spawn defaults: identity orientation, zero angular velocity
  (`state.clj:36-63`).

## Done when (player-visible via live pm2 window)
- The spark carries a stable orientation + angular velocity every tick; with a
  scripted constant torque it spins up and (with no damping yet) keeps spinning —
  visible once the mote shader shows a heading (card 9) or via a debug readout.
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green;
  `write-conflicts {}`.

## Risks
Single-writer discipline (rotation integrator must be the ONLY writer of the two
new components); quaternion normalization drift across ticks; the components are
inert until card 2 feeds torque — land it green but expect no felt change alone.

## Dependencies
None. Unblocks cards 2 (torque channels), 6 (camera roll inheritance),
9 (heading flare).
