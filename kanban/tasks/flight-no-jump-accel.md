---
category: "specs"
labels: ["domain", "infra", "physics", "player", "spark", "spark-flight"]
write-id: "1791317264882-0.vi9h73634xh0hgyzcfs"
source: "kanban/tasks/flight-no-jump-accel.md"
title: "Minimal no-jump flight: replace the position-teleport with acceleration-based movement"
priority: "P1"
status: "in_progress"
estimate: "3"
uuid: "flight-no-jump-accel"
created_at: "2026-07-23T00:00:00Z"
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

---
Reopen existing estimate3 smooth-approach acceptance for the reproduced precision-range defect. Accepted flight design docs/designs/spark-flight-and-camera.md section3.5 now grounds Cruise/Fine sizing in docs/notes/2026-10-06-manual-flight-precision-boundary.md and its production Jacobi probe. Root and independent board_scout reviewed the scoped amendment with no blocking findings. Existing minimum D1e12 gives6.68459AU settled single-input pulse versus1AU binding radius. Scope: preserve defaultCruise3e14 and maximum1e16; expose Fine1e7 through existing Spark setting/intent, lower stepper floor, preserve momentum and all physics ownership. Correct misleading retention/wall60Hz docs. RED must discover actual panel actions and exercise real thrust/integrator with lag, pulse/coast, exposedretention.8/.97/.999, multiple fixed dt and characterized dtchange; justify long .999 settling tail. No local-frame assistance, capture controller or new binding law. Native moving-target commitment/sculpt stays separately unverified. Existing design body link remains authoritative; canonical frontmatter CLI refused adding descriptive design key (exit1 keys not allowed), an upstream authoring gap to record, not permission for a local board writer.

2026-10-06 precision repair: GREEN24cbffd passes focused30tests194assertions0failures/errors, with independent source review. Actual native ordinary-nebula controls accepted Cruise3e14 to Fine1e7 to custom2e7 to Cruise3e14, correct highlights, unchanged retention .97/manual mode and no service/UI errors. Full120second1280x720 recording and all47 closed hashes preserved under .ημ/diagnostics/flight-precision/native-24cbffd/. Real coast remains nonzero after Fine; exact only-displacement mutation is proved by tests, not cross-tick readbacks. Later38f073b merges working review-runtime/evidence parent with no src/test/deps change. Full gate and real moving-target approach/resolve acceptance remain open; no done claim.
---