---
uuid: "focus-follows-pilot"
title: "Focus follows the pilot: bind/resolve/aim a planet while manually flying"
status: "review"
priority: "P1"
labels: ["domain", "infra", "player", "spark", "spark-flight", "narrowing"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/focus-follows-pilot.md"
category: "specs"
estimate: 3
---

# Focus follows the pilot

> spark-flight epic, Wave 0 (fast-path to playable loop), THE LINCHPIN. Design:
> `docs/designs/spark-flight-and-camera.md` §7.5. This is the single reason "I fly
> toward a planet but it never resolves into voxels": the resolve pipeline keys off
> the focus point, and focus only auto-tracks in NON-manual camera modes. Fix that
> and the whole voxel payoff becomes reachable while piloting.

## Grounded integration (from design investigation, cite file:line)
- Focus auto-tracks a target ONLY in non-manual camera modes:
  `sync-observer-focus-to-camera` snaps `:focus-position` to the camera target
  every frame (`src/infra/dev/window/loop.clj:128-140`). In `:manual` it does
  not — so while flying, focus is frozen.
- Manual focus aiming is ~20,000× too coarse to be usable: arrows move focus
  3.0e15 m/press (`src/infra/render/input.clj:76-84`) vs `world-focus-radius`
  ≈ 1 AU ≈ 1.5e11 m (`src/law/narrowing.clj`). Hand-aiming a planet is
  impractical; the camera-follow path is currently the only route.
- The resolve pipeline that consumes focus: binding accrues on focus/body overlap
  (`domain.narrowing`, `c/binding` system, `narrowing.clj:113-156`) → commitment
  (`narrowing.clj:319-376`) → voxel band populates+renders
  (`domain.voxel.focus`, `infra.render.scene.voxel`). All gated on focus overlap.
- **Fix:** in `:manual` mode, drive `:focus-position` from the mote — its
  `c/position`, or better its aim/velocity heading a short distance ahead — so
  flying up to a planet accrues binding and lets `G/H/J`/sculpt land on it. Keep
  arrows/`,`/`.` as manual fine-tune overrides (and/or rescale their step to the
  focus radius so they're actually usable). `:focus-position`/`:focus-radius` live
  in the `c/observer` map (`src/domain/player/state.clj:17`); keep the single
  writer discipline for however focus is updated.

## Done when (player-visible via live pm2 window)
- Fly the mote toward a formed planet in manual mode: focus rides with you, the
  binding readout climbs, the world commits, and its voxel band renders — WITHOUT
  switching to a debug/follow camera mode.
- Manual arrow focus nudges are usable (steps scaled to the focus radius), not
  effectively no-ops.
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green.

## Risks
Deciding the focus-follow law (mote position vs aim-lead) affects feel — live-tune.
Must not double-write `:focus-position` (manual override vs auto-follow — pick a
single resolution order). Interacts with the camera work later; keep it simple now
(follow the mote), refine with the chase camera (card 6).

## Dependencies
None hard (works with today's teleport movement). Pairs with `flight-no-jump-accel`
and `voxel-sculpt-verb-palette-wiring` to complete the Wave 0 playable loop.

## Implementation notes (2026-07-23, Wave 0 build)

- **Law chosen:** position-only, no aim-lead — every manual-mode frame the window
  loop enqueues `player/focus-follow` with the player's persistent
  `:focus-offset`, pinning `:focus-position` = spark `c/position` + offset
  (`src/domain/player/focus.clj`, call site `src/infra/dev/window/loop.clj`).
  Lead/tuning deliberately deferred to live pm2 tuning + the chase camera
  (Wave 3 card 6).
- **Resolution order:** there is no competing writer to order against — arrows
  edit the config-side `:focus-offset` (`src/infra/render/input.clj`), never the
  position, so nudge and auto-follow commute and the nudge always lands.
  `:focus-position` keeps one writer per mode (follow intent in `:manual`,
  camera-target sync in tracking modes).
- **Arrow step rescaled** from 3.0e15 m to `focus-nudge-step` = 0.1 ×
  `law.narrowing/world-focus-radius` (~0.1 AU ≈ 1.5e10 m).

## Work notes (2026-07-23)

Implemented + live-verified. `domain.player.focus/focus-follow` (pure,
reads c/position, writes only the c/observer map) enqueued every manual-
mode frame through the intent queue; focus = spark pos + config
`:focus-offset`. No competing writer: arrows edit the OFFSET (commutes
with auto-follow, always lands), step rescaled 3e15 → 0.1 ×
world-focus-radius (~0.1 AU). Tracking modes keep the camera-target sync.
Live: focus rides the spark at ~1-tick lag (verified diff ~2e12 m at
1e26-scale positions during the flight-fling investigation).
