---
uuid: "narrowing-tether-default-camera-modes"
title: "Narrowing: tether actuates in default camera modes (not just :manual)"
status: "todo"
priority: "P1"
labels: ["render", "camera", "narrowing", "integration-debt"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/narrowing-tether-default-camera-modes.md"
category: "specs"
estimate: 3
---

# Narrowing: tether actuates in default camera modes

> Integration-debt card (2026-07-23 re-triage). The binding tether the
> `narrowing-frame-handoff` card built only runs in `:manual` mode — which is
> not the default — so a player never feels it unless they discover the
> mode-cycle key.

## Root cause (investigation)
`cam/tether-step` is gated `(when (= :manual (:mode cam-settings)) ...)`
(`loop.clj:277-278`); the default mode (absent `:mode`) is `:fit-all`
(`camera/navigation.clj:127`). The auto tracking modes
(`:follow-selection`/`:track-largest-cluster`/`:fit-all`) overwrite target and
distance every frame (`tracking.clj:135-148`), which would erase any tether
pull — this is exactly the gap the tether's own docstring flags
(`tether.clj:30-34`). Also `follow-selection-target`'s hard center-snap branch
(distance < 1000 render units, or within 4 body radii) is what makes the spark
flap in/out (see `spark-planet-binding`).

## Done when (player-visible)
- The binding tether pulls the frame in at least the default startup mode
  (`:fit-all`) and `:follow-selection`, once binding > 0, without the player
  pressing a mode-cycle key first.
- The `follow-selection` center-snap no longer produces the in/out flicker
  (replace the hard snap with the continuous tether lerp, or route `tether-step`
  for every mode instead of overwriting `:target`/`:distance` unconditionally).
- Verified via headless-PNG diff or camera-state test showing continuous frame
  movement across several ticks while a planet moves.

## Dependencies
Pairs with `narrowing-worldscale-overlap-gate` (no point tethering to a target
that binds indiscriminately). If `spark-planet-binding` approach (B) is chosen,
coordinate: the camera may then follow the spark's real position rather than
puppet it.
