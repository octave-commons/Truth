---
category: "specs"
labels: "domain,physics,bug,hygiene"
write-id: "1791331596664-0.qxsntza5d7wy4zpr8t"
source: "kanban/tasks/fix-warp-disabled-stale-write.md"
title: "Fix stale-force bug when a fan-out force is disabled at runtime (:warp)"
priority: "P2"
status: "in_progress"
estimate: "1"
uuid: "fix-warp-disabled-stale-write"
created_at: "2026-07-23T00:00:00Z"
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

---

2026-10-07 root admits the existing one-point task as a mechanical lifecycle bug repair under PROCESS mechanical/hygiene exemption, with independent scope review. This restores an already-written removal contract; it is not a no-behavior-change refactor or a new force policy. Grounding: docs/notes/specs/2026.06.26-ecs-double-buffer-single-writer-spec.md section3 owner removal contract; current domain.ecs.tick/apply-write-set and contribution-write-set; working domain.gravity.dark-matter emitter; warp emitter documented auto-clearing and existing card acceptance. Use the established prior-eid removal mechanism for c/accel-warp, including no-active-wells, expiry and partial recipient loss. Preserve force calculation, costs, TTL, input, uniform influence registry, single-writer ownership and ordinary one-tick Jacobi carry. RED must exercise emit then fold then expire/remove then fold and subsequent integrator motion, rather than inspect an empty emitter map on a fresh world. Out-of-range recipients must clear while still-affected recipients retain their legitimate contribution. Other stale-emitter findings are audit results only, not added implementation scope. Existing TODO estimate1 fits the scoped repair. Root must commit observed RED before GREEN; full suite and all six strict gates required. No native paid-action or Gate completion claim.

---