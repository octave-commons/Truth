---
uuid: "fix-warp-disabled-stale-write"
title: "Fix stale-force bug when a fan-out force is disabled at runtime (:warp)"
status: "todo"
priority: "P2"
labels: ["domain", "physics", "bug"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/fix-warp-disabled-stale-write.md"
category: "specs"
estimate: 1
---

# Fix stale-force bug when a fan-out force is disabled at runtime

> Found while implementing `dark-matter-static-halo` (2026-07-23).

## Root cause
`apply-write-set` (`src/domain/ecs/tick.clj:43-81`) MERGES a write-set into a
component column per-entity — it does not replace the whole column. So the
common "disabled → emit `{ctype {}}`" shortcut does NOT clear values written on a
previous tick: entities that received a force last tick keep it forever once the
force is toggled off at runtime. The dark-matter emitter avoided this by using
`tick/contribution-write-set` against the prior tick's carried eids; `:warp`
(`domain.intervention/warp-acceleration-system`) still uses the buggy `{ctype {}}`
shortcut for its fully-disabled branch. (`:observer-accel` had it too but is being
deleted by `remove-passive-halo-invert-influence`.)

## Done when
- Toggling `:warp` off at runtime (no active wells) clears `c/accel-warp` from all
  entities within one tick (no stale residual force), verified by a test that
  emits a well, disables it, and asserts the channel empties.
- Uses the same `contribution-write-set`-against-prior-eids pattern as
  `domain.gravity.dark-matter`.
- `clojure -M:test` green; `write-conflicts {}`.

## Notes
Small, isolated. Audit other `{ctype {}}` disabled branches while here.
