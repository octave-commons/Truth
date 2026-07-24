---
uuid: "debug-view-state-restore"
title: "Debug/cinematic views: store & restore manual camera state; fix tracking offset"
status: "todo"
priority: "P2"
labels: ["infra", "camera", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/debug-view-state-restore.md"
category: "specs"
estimate: 3
---

# Debug/cinematic view state restore

> spark-flight epic, Wave 3 card 7 of 10. Design: `docs/designs/spark-flight-and-camera.md` §5.2.
> Every view other than `:manual` is debug/cinematic (owner decision). They must
> stop clobbering the player's manual view state. Fixes the "switching views
> resets the camera / mote jumps to where it used to be / jumps back when I move"
> cluster and the tracking-mode offset.

## Grounded integration (from design investigation, cite file:line)
- Camera modes: `[:manual :follow-selection :track-largest-cluster :fit-all]`
  (`src/infra/camera/navigation/input.clj:125`), dispatched at
  `navigation.clj:123-131`, cycled via `render/input.clj:144-145`.
- The non-manual modes reset yaw/pitch to defaults every frame they're active
  (`tracking.clj:207-208`, shared `tracking-camera-update` `:193-227`) and never
  restore the player's angle — THIS is the "reset on switch" + "jump back" report.
  Fix: **snapshot `{:yaw :pitch :distance :target}` when leaving `:manual`, restore
  it when returning.** Debug views stay free to reframe; manual is inviolable.
- Label the three non-manual modes as debug/cinematic in the UI
  (`infra/menu/widgets.clj`) so intent is explicit.
- **Tracking offset:** `distance-for-radius` (`tracking.clj:111-116`), cluster
  centroid + 27-neighbor binning, and the radius floor (`:193-227`) — the target
  read is correct (`observer-render-position` `tracking.clj:56-63`), so the offset
  is framing math. Verify against the live window and
  `test/infra/camera_test.clj` (`test-update-camera-track-largest-cluster`); fix
  the margin/floor so the tracked body is centered, not offset.

## Done when (player-visible via live pm2 window)
- Switch manual → any debug view → back to manual: the manual camera returns to
  EXACTLY the angle/distance/target it had before, no jump.
- Tracking a body frames it centered, not offset.
- Debug/cinematic views are labeled as such in the menu.
- `clojure -M:test` + architecture-test + camera tests green.

## Risks
Where to stash the saved manual state (camera record vs settings) — keep it out of
the per-frame hot path. Restore must not fight the chase-camera spring (card 6) on
re-entry — restore then let the spring settle. Frame-latency between sim/render
threads may look like offset; rule it out first.

## Dependencies
Card 6 (chase rebuild defines manual state to save/restore). Independent of the
physics cards otherwise.
