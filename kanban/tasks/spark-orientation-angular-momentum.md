---
category: "specs"
labels: ["domain", "physics", "player", "spark", "spark-flight"]
write-id: "1791311342443-0.guobs98pufdf1zarvq9"
source: "kanban/tasks/spark-orientation-angular-momentum.md"
title: "Spark gains orientation + angular momentum (rotation integrator, single writer)"
priority: "P1"
status: "review"
estimate: "5"
uuid: "spark-orientation-angular-momentum"
created_at: "2026-07-23T00:00:00Z"
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

---
2026-10-06 readiness audit: the approved design docs/designs/spark-flight-and-camera.md cites scratchpad/spark-flight-controls-research.md, absent from checkout and all available git history. No historical findings can be fabricated. Triage this existing 5-point feature back to Breakdown while restoring its research-to-design chain with a tracked, narrowly scoped primary-source quaternion-integration note. Research agent verifies convention, integration, normalization, and simulated-time boundaries; design will cite the new note and preserve provenance that it is new evidence, not recovered scratchpad. After the note and design link exist, re-admit the same feature slice: schemas and red tests first, one declared rotation-integrator writer, spawn defaults, zero/constant torque and unit-norm tests, strict analysis and architecture gates. No source implementation before the grounding repair.

2026-10-06 grounding repaired before implementation: docs/research/physics/spark-rotation-integration.md now cites verified Sola 2017 arXiv:1711.02508v1 sections 3, 4.5.1, 4.6.1 and the official AHRS AngularRate documentation; design section 3.2 links it. New note explicitly preserves missing-scratchpad provenance and confines current evidence to the rotational substrate. 5-point slice remains unchanged: scalar-first body-to-world unit quaternion, world-frame angular velocity, same simulation dt, one declared rotation-integrator writer, schema/red tests before implementation. Rheos refused requested todo-to-breakdown with No transition; no status bypass was used and code was held until this tracked research repair existed. Claim now proceeds through the legal todo-to-in_progress edge; strict tests, architecture, write-conflicts, and analysis remain review gates.

Canonical gated review transition completed on exact source revision a3609f6931951f8e011950ec5eab0a24265e93cd. This card independently executed clojure -M:test: 899 tests, 15635 assertions, 0 failures, 0 errors; then bin/analyze --strict: exit 0, no blocking findings. Move in_progress to review exited 0. Full per-transition log: .ημ/diagnostics/playable-foundation/rheos-review-orientation-a3609f6.log. Prior scoped rotation/architecture tests and research provenance remain attached to this source; current gate was executed anew rather than reused. This does not claim chase-camera/input-feel or live Gate completion. Hosted review and final done policy remain outstanding.
---