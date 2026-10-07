---
category: "specs"
labels: "domain,physics,bug,hygiene"
write-id: "1791334658641-0.0ev8htmgelpfwui4s04m"
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

BEFORE hot-path baseline completed on unchanged RED e434a8384e9e87342b0a1012b6b87b33acbb3a46: existing quick-bench / registered Phase0 closure plus four actual emitter+fold snapshots. Means: phase0-500 19.1907ms; clean324.551ns; active252.746us; expired290.280ns; partial204.208us. Expired/partial retain stale cells BEFORE; cheap broken clearing is not a performance target. Frozen fixture2c275009a46b8bc813bd1541e7b3bdb73d51e1e7d2112b8aa0a1962f3b6fa096; exact source/load/intervals and preserved failed callback serialization attempt are in .ημ/diagnostics/warp-lifecycle/before/RESULT.md. Retry capture and benchmark exited0/reaped; original RED15hashes unchanged. Narrow known-handler encoding independently reviewed; no generic coercion or reader eval. No source changes, GREEN/native/FPS claim or state transition. Root owns checkpoint and GREEN authorization.

GREEN restores the existing lifecycle with contribution-write-set and the old explicit empty-column result shape; registry adds only accel-warp self-read. Final production eed4697f/eb5df312. Focused36/225 pass; full948/16775 pass on identical production; all6 strict pass after exactly3 indentation fixes in the live regression test (reverse-byte proof and equal reader forms; frozen RED/fixtures/adapter unchanged). Initial focused empty-shape failure and initial formatting-only strict failure preserved. Matched five-case AFTER completed/reaped, exact fixture2c275009: expired500->0, partial500->250, active500 retained; independent physical columns and paid agency unchanged. Performance is NOT qualified neutral: broad Phase0-500 mean19.1907->34.8343ms (+81.5%) with separated reported intervals; active252.746->287.367us, required expired clearing297.214us, partial385.291us. Background load differs; causation unresolved and not explained away. See green/RESULT.md and after/RESULT.md under .ημ/diagnostics/warp-lifecycle. Parent exact native pause/resume preserved; no native input or extra cost run. Local independent source review found no blocker but is not hosted approval. Card remains InProgress1; root owns checkpoint, committed-source gate, performance disposition and publication.

---