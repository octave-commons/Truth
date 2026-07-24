---
uuid: "spark-6dof-input-mapping"
title: "6DOF input mapping: mouse aim + rebindable keyboard piloting layer"
status: "todo"
priority: "P1"
labels: ["infra", "player", "spark", "input", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/spark-6dof-input-mapping.md"
category: "specs"
estimate: 5
---

# 6DOF input mapping

> spark-flight epic, Wave 2 card 4 of 10. Design: `docs/designs/spark-flight-and-camera.md` §7.
> Wire real controls to the thrust/torque channels: mouse aims yaw/pitch; keyboard
> drives roll, the six translational thrusters, boost, and flight-assist toggle.
> Everything rebindable. Manual mode only.

## Grounded integration (from design investigation, cite file:line)
- Input capture lives in `src/infra/render/input.clj` (mouse look `:176-200`,
  scroll `:119-126`, mode cycle `:144-145`) and the frame loop
  `src/infra/dev/window/loop.clj:266-294`. Replace the `observer-move-velocity`
  path (`camera/navigation/input.clj:102-119`) — it produced a velocity for the
  deleted `drift`; now produce a **thrust command** (body-frame force + torque)
  enqueued through the existing intent queue (`loop.clj:23-34`) so the sim thread
  stays sole writer.
- Binding map (all rebindable; see `docs/designs/spark-flight-and-camera.md` §7
  for the full table and why these keys — many are chosen around already-taken
  keys `C`/`R`/`L`/arrows/`,`/`.`/`G`/`H`/`J`):
  - Mouse X/Y → yaw / pitch (aims the mote)
  - Hold **middle-mouse** + move → camera orbit, decoupled from ship
  - `Q` / `E` → roll L / R  (free keys)
  - `W` / `S` → throttle fwd / back
  - `A` / `D` → strafe L / R
  - `Space` / `Left-Ctrl` → vertical up / down
  - `Left-Shift` (held) → boost (coherence-costed; economy in card 5)
  - `F` → flight-assist toggle (card 3)
  - Focus (arrows + `,`/`.`) and abilities (`G`/`Shift+G`/`H`/`J`) are SEPARATE
    role groups — do not touch them here.
- Only active in `:manual` camera mode (current gate at `loop.clj:266`). Mouse
  aims the SHIP by default; camera orbit moves to **middle-mouse** (design §7,
  §5.1). Reconcile with today's mouse handling: left-click stays entity-pick, and
  confirm left-drag no longer orbits (`render/input.clj:194-213`). Resolve this
  capture ownership explicitly here.
- Store bindings in the tunables/settings surface
  (`exposed-tunables-and-settings-menu-spec.md`) so they are rebindable, not
  hard-coded.

## Done when (player-visible via live pm2 window)
- Mouse aims the mote's nose; `WASD` translate; `Space`/`Ctrl` climb/descend;
  `Z`/`C` roll; `E` boosts forward; `R` toggles flight-assist — all as forces, no
  jumps.
- Rebinding a key in settings takes effect without restart.
- `Q` still focuses; camera mode cycle still works.
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green.

## Risks
Mouse-aim-ship vs mouse-look-camera contention is the main design hazard — pin it
down before coding. Key-repeat vs held-key semantics for thrust. Intent-queue
ordering so commands apply before the tick (`loop.clj` drain-before-tick).

## Dependencies
Cards 2 (channels to drive), 3 (FA toggle). Benefits from card 5 (boost cost).
Unblocks the felt flight experience.
