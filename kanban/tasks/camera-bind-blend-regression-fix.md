---
uuid: "camera-bind-blend-regression-fix"
title: "Fix camera regressions from the auto-mode bind-blend (zoom lock + bounce)"
status: "review"
priority: "P0"
labels: ["render", "camera", "bug", "regression"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/camera-bind-blend-regression-fix.md"
category: "specs"
estimate: 2
---

# Fix camera regressions from the auto-mode bind-blend

> Both bugs trace to commit e685159 (narrowing-tether-default-camera-modes):
> `blend-toward-binding` in `src/infra/camera/navigation/tracking.clj`.

## Root cause (investigation 2026-07-23)
- **Zoom lock:** `blend-toward-binding` (`tracking.clj:164-181`) unconditionally
  lerps camera `:distance` toward `frame-margin*r-ru` (4 body-radii) whenever the
  observer has any binding (`s = tether-strength(b) > 0`), in
  `update-camera-follow-selection` (:231) and `update-camera-fit-all`
  (`bind-blend? true`). Unlike `tether-step` (which is fightable / honors
  `:input-active?`), it has NO player-override, so it overrides scroll-zoom.
- **Bounce:** a closed feedback loop — camera `:target` → `:focus-position`
  (`sync-observer-focus-to-camera`) → binding accrual (focus overlap) → `s` →
  bind-blend pulls target/distance → target moves → repeat; `s` oscillates as the
  focus point flickers across `world-focus-radius`, and since the blend weight IS
  `s` (not a small rate), swings translate to visible bounce. Camera never reads
  predicted positions (verified); prediction is confined to the spark's spring.

## Decision
The gravity-bound-spark redesign supersedes the spring/tether-blend mechanism, so
**remove** the auto-mode bind-blend rather than patch it.

## Done when
- Scroll-zoom into a body is fully authoritative again in all camera modes (no
  auto-pull overriding it) — the pre-e685159 fightable behavior.
- No target/distance bounce while looking at / orbiting a bound body.
- KEEP card e685159's good parts: `follow-selection-target`'s continuous lerp
  (the hard-snap removal), and the de-occlusion standoff
  (`domain.narrowing/standoff-position`).
- Add the secondary fix: `update-camera-manual` (`tracking.clj:187-192`) lerps
  toward `observer-render-position` instead of a hard `assoc` (zero-smoothing),
  so spark-position jitter is damped before reaching the camera (matters more
  under the redesign, where the spark's position is physically integrated).
- Remove/adjust the now-obsolete bind-blend tests added in e685159
  (`test-fit-all-*-tethers*`, follow-selection-tethers) HONESTLY — they assert a
  mechanism being removed; don't keep dead assertions.
- `clojure -M:test` + architecture-test green.

## Out of scope
The "frame tightens as you bind" feel — returns via the camera following the
gravity-bound spark (spark redesign cards), not via a camera-side blend.
