---
uuid: "flight-no-jump-accel"
title: "Minimal no-jump flight: replace the position-teleport with acceleration-based movement"
status: "review"
priority: "P1"
labels: ["domain", "infra", "physics", "player", "spark", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/flight-no-jump-accel.md"
category: "specs"
estimate: 3
---

# Minimal no-jump flight

> spark-flight epic, Wave 0 (fast-path to playable loop). Design:
> `docs/designs/spark-flight-and-camera.md` §3, §8. The smallest change that stops
> the WASD jump and makes flying to a planet pleasant — WITHOUT the full 6DOF
> orientation/torque machinery (that's Wave 1). This is a deliberate stepping stone
> that `spark-flight-force-channels` (Wave 1 card 2) later extends.

## Grounded integration (from design investigation, cite file:line)
- **The jump:** `domain.player.focus/drift` (`src/domain/player/focus.clj:19-45`)
  writes `pos' = pos + velocity·wall_dt` directly onto `c/position` every frame —
  a second writer racing the gravity integrator (`kinematics.clj:76-79`). Two
  writers on one component per frame = visible jump. The camera lerp at `t=0.35`
  (`tracking.clj:147-149`) only masks it.
- **Minimal fix:** replace the teleport with a thrust ACCELERATION channel summed
  by the existing linear integrator (`genesis/systems.clj:47`). Input (WASD +
  vertical) produces a desired acceleration along the CAMERA/aim direction for now
  (full body-frame thrust waits for orientation, Wave 1); add light linear damping
  so releasing input coasts to a stop (a proto-flight-assist). Emit as a channel;
  do NOT write `c/position`/`c/velocity` directly — the integrator stays the sole
  writer (`registry.clj:536`). Delete `drift` + its call site
  (`loop.clj:266-294`).
- Keep it in `:manual` mode only, through the existing intent queue
  (`loop.clj:23-34`) so the sim thread stays sole writer.
- Scope guard: NO quaternion, NO torque, NO FA toggle, NO coherence gating yet —
  just smooth accel + damping. Those are Waves 1-2.

## Done when (player-visible via live pm2 window)
- Holding WASD accelerates the mote smoothly and releasing coasts to a gentle
  stop — no jump, no fighting the gravity integrator; gravity still curves the
  path when you let go.
- Flying up to a planet is smooth enough to actually approach and (with
  `focus-follows-pilot`) resolve one.
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green;
  `write-conflicts {}`.

## Risks
Accel magnitude + damping tuning (live-tune in pm2). Removing `drift` touches the
window loop and any readers of the old velocity path. Keep the scope minimal —
resist adding orientation here; that's Wave 1 and would balloon the card.

## Dependencies
None. Wave 0. Extended by `spark-flight-force-channels` (Wave 1 card 2).

## Implementation notes (2026-07-23, Wave 0 build)

- `drift` deleted (`domain.player.focus` + `domain.player` facade) along with
  its `loop.clj` call site; `cam/observer-move-velocity` went with it (replaced
  by `cam/thrust-direction`, a unit direction — Space/LCtrl verticals added per
  the §7 map).
- Thrust rides the `:player/thrust` world key (the `:genesis/interventions`
  precedent): intent writes the direction, the new `:player-thrust` fan-out
  system (`src/domain/player/flight.clj`) is sole writer of the new
  `c/accel-thrust` influence channel, summed by the integrator
  (`influence-registry`).
- **Constants (dt-normalized, so dilation-proof):** `default-thrust-dv-per-tick`
  = 6.0e13 m/s per tick (terminal ≈ 2.0e15 m/s ≈ 2 ru/s);
  `default-damping-retention` = 0.97 per tick (coast to ~5% in ≈ 98 ticks ≈
  1.6 s at the sim's ~60 Hz pace). Live knobs `:genesis/spark-thrust-dv` and
  `:genesis/spark-damping-retention`, wired into the Spark menu panel; the
  dead `:move-speed` View-panel knob was removed and the view bar now shows
  the spark's live speed.

## Work notes (2026-07-23)

Implemented + live-verified. `domain.player.flight`: set-thrust intent →
`:player/thrust` world key → `:player-thrust` fan-out (sole writer of
`c/accel-thrust`, summed by the integrator via `:velocity :accumulate`) —
`drift` and its loop.clj call site deleted; integrator stays sole
c/position writer. WASD+Space/LCtrl via camera-basis `thrust-direction`.
FIRST LIVE TEST FLUNG THE SPARK TO 1e26 m: the original constant (6e13
m/s Δv per tick) ignored that the integrator advances x by v·:sim/dt —
one tick at dt=4.1e9 moved 8e24 m (physics-dt-unit-mismatch, again).
Fixed: displacement-targeted sizing — terminal v·dt = D (3e14 m/tick,
~world-crossing in 5 s wall) at ANY dilated dt; pinned by
`thrust-terminal-displacement-is-dt-invariant` (doseq over dt 1e7..5e12).
Live verify (thrust-direction override through the real wiring): 96% of
expected v_term, disp/tick ≈ 0.7·D mid-ramp, coast 29,466→1,276 m/s in
3 s after release, mode-exit clear works.
