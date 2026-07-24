---
uuid: "spark-flight-force-channels"
title: "Replace the WASD position-teleport with thrust-force + torque channels"
status: "todo"
priority: "P1"
labels: ["domain", "infra", "physics", "player", "spark", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/spark-flight-force-channels.md"
category: "specs"
estimate: 8
---

# Replace the WASD teleport with thrust-force + torque channels

> spark-flight epic, Wave 1 card 2. Design: `docs/designs/spark-flight-and-camera.md` §3.
> EXTENDS `flight-no-jump-accel` (Wave 0, which already replaced the teleport with
> camera-relative acceleration + damping). This card upgrades that to true
> body-frame thrust (along `c/orientation`) plus TORQUE channels for rotation —
> the full 6DOF core. The jump is already fixed by Wave 0; this is the depth pass.

## Grounded integration (from design investigation, cite file:line)
- **DELETE the teleport:** `domain.player.focus/drift`
  (`src/domain/player/focus.clj:19-45`) writes `pos' = pos + velocity·wall_dt`
  straight onto `c/position` — a second writer fighting the gravity integrator
  every frame. That double-write IS the "spark jumps on WASD." Remove it and its
  call site in `src/infra/dev/window/loop.clj:266-294`.
- **Add force/torque channels:** input becomes a `c/thrust-command` (or reuse an
  accel-channel component) holding desired body-frame linear force + torque for
  the tick. A thrust system converts command → acceleration channel consumed by
  the linear integrator (`genesis/systems.clj:47`, `kinematics.clj:76-79`) and a
  torque channel consumed by the rotation integrator (card 1). NEITHER writes
  `c/position`/`c/velocity`/`c/orientation`/`c/angular-velocity` directly — they
  emit channels the integrators own. Declare write-sets in `domain.ecs.registry`;
  respect one-writer-per-type (`registry.clj:536`).
- **Body-frame thrust:** forward/strafe/vertical thrust is applied along the axes
  derived from `c/orientation` (card 1), NOT camera axes (that decoupling is the
  "real spaceship" feel and fixes "going toward camera-forward feels wrong").
- **Thrust asymmetry:** forward accel ≈ 1.5–2× lateral/vertical (Elite ratio) so
  forward reads as the propulsion axis. Constants in `law/` (alongside
  `law/spark.clj`).
- Actual key→command wiring is card 4; this card can drive channels from a
  scripted/test command to prove the physics, plus a minimal W/S/A/D stopgap.

## Done when (player-visible via live pm2 window)
- Holding thrust accelerates the spark smoothly (velocity builds, momentum
  carries) instead of teleporting; releasing thrust leaves it coasting on
  momentum + gravity — no jump, no fight with the integrator.
- Applying rotation input spins the nose (visible via mote heading, card 9, or
  debug readout).
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green;
  `write-conflicts {}`.

## Risks
Removing `drift` touches the window loop and any readers of the old velocity path;
body-frame vs world-frame confusion; ensuring exactly one writer per motion
component. Do on its own branch; live-tune accel magnitudes in the pm2 window.

## Dependencies
Card 1 (needs `c/orientation` for body-frame thrust + torque channels). Unblocks
cards 3, 4, 5.
