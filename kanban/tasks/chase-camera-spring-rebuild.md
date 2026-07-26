---
uuid: "chase-camera-spring-rebuild"
title: "Third-person chase camera: critically-damped springs + velocity look-ahead"
status: "todo"
priority: "P1"
labels: ["infra", "camera", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/chase-camera-spring-rebuild.md"
category: "specs"
estimate: 8
---

# Third-person chase camera rebuild

> spark-flight epic, Wave 3 card 6 of 10. Design: `docs/designs/spark-flight-and-camera.md` §5.1.
> Rebuild `:manual` — the mode the player flies in — as a proper spring-damped
> chase rig with velocity-biased look-ahead. This is what makes third-person feel
> like Ace Combat / Elite rather than a rigid rod, and fixes "going directly at
> camera-forward feels wrong."

## Grounded integration (from design investigation, cite file:line)
- Replace `update-camera-manual`
  (`src/infra/camera/navigation/tracking.clj:143-152`) and its single
  `lerp-toward` at `t=0.35` (`tracking.clj:135-141`) with:
  1. **Anchor** behind the ship on a velocity-stabilized frame, FIXED chase
     distance (do NOT velocity-scale — no road to read scale against; sell speed
     via FOV/parallax/vignette).
  2. **Velocity-biased look-ahead** aim target:
     `ship.pos + normalize(v)·lead·clamp01(|v|/vRef)`, fading to body-forward near
     zero speed so the camera doesn't hunt while stationary.
  3. **Two critically-damped springs** (ζ=1, closed-form — see
     `docs/designs/spark-flight-and-camera.md` §5.1 for the update formula): one
     for chase position, a SLOWER one for the aim target. Decoupling the two is
     what stops the "stiff rod" feel.
  4. **Partial roll inheritance:** camera roll = ship roll × factor (~0.3–0.6),
     itself spring-damped. First-class tunable, not a binary. Uses `c/orientation`
     (card 1).
- Camera math runs in render units after the `phase0-view-scale = 1e15` divide
  (`projection.clj:17-19`); target/position derivation in
  `camera/navigation/input.clj:65-75`. Z-up stays `[0 0 1]`
  (`render/scene/setup.clj:66`) — never `[0 1 0]`.
- Camera reads the live `c/position` via `observer-render-position`
  (`tracking.clj:56-63`) — already correct single-source; keep it.
- Mouse-look orbit around the anchor stays available but eases back to the
  velocity-aligned rest pose after idle (decouple aim from movement). Coordinate
  with card 4's mouse-aims-ship decision.

## Done when (player-visible via live pm2 window)
- Flying feels like a chase cam: the mote sits lower-center in frame, camera leads
  the velocity direction, banking shows in-frame, no jitter or bounce even under
  gravity drift.
- Releasing input and letting gravity move the spark keeps the camera smoothly
  behind it (no "jumps around after sitting a while").
- `clojure -M:test` + architecture-test + camera tests
  (`test/infra/camera_test.clj`) green.

## Risks
Spring stiffness tuning (overshoot vs lag); roll-inheritance nausea if factor too
high; interaction with the still-present tether (`tether.clj`) — this rebuild
should make the tether redundant for manual; confirm it's disabled/removed for
manual flight. Live-tune in pm2.

## Dependencies
Card 1 (orientation for roll inheritance); benefits from card 2 (real velocity to
lead). Unblocks card 7.
