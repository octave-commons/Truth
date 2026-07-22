---
uuid: "voxel-focus-promotion-demotion"
title: "Voxel 3: voxel focus promotion/demotion (dynamic band)"
status: "blocked"
priority: "P1"
labels: ["specs", "phase1", "voxel", "epic-planetary-voxel-substrate"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/voxel-focus-promotion-demotion.md"
category: "specs"
estimate: 5
---

# Voxel 3: voxel focus promotion/demotion (dynamic band)

> Parent epic: `kanban/tasks/planetary-voxel-substrate.md`
> Design: `docs/designs/planetary-voxel-substrate.md` §5, §7; also
> `docs/designs/commitment-and-resonance.md` §5.5.
> Blocked on: `voxel-substrate-law-schema`, `planet-candidate-to-voxel-seed`.

**Goal:** Extend the proven promotion/demotion conservation machinery
(Player Focus epic) one level deeper: materialize a voxel band under the
observer's focus from the macro geology field; demote it back, conserving
mass and composition.

## Scope

- **Focus-driven dynamic band** (owner decision 2026-07-22): the voxel band
  follows focus intensity, including depth — deeper focus literally deepens
  the resolved band. The same attention rule as the horizontal focus cone,
  projected downward. Band persist/unpersist as focus moves MUST round-trip
  losslessly through the field + edit diff.
- **Deferred edit queue, hard 2 ms/tick cap** (owner decision 2026-07-22):
  promotion/demotion and all later voxel edits drain through a budgeted
  queue; budget constant in `law/`.
- **Edit-diff emission** (owner decision 2026-07-22): demotion folds resolved
  voxels back into the macro field and emits edit-diff records (slice 1
  schema) for every deviation from the regenerated seed.
- Conservation: mass + composition conserved across promote/demote, proven
  by tests in the style of `focus_conservation_test.clj` (7 named tests
  earned their keep — match that rigor).
- Single writer per component; declared reads/writes; no `c/matter-state`
  writes.

## Done when

- Band materializes/demotes under focus with proven conservation;
  edit diffs emitted; queue respects the 2 ms budget;
  `clojure -M:test` + `architecture-test` green; `write-conflicts {}`.

---
Created 2026-07-22 (resumed session): slice 3 of the approved breakdown.
All three owner decisions land here — this is the keystone slice.
---
