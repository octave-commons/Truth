---
category: "specs"
labels: ["specs", "phase1", "voxel", "epic-planetary-voxel-substrate"]
write-id: "1784759848220-0.8x488pn2fncdcods9qf"
source: "kanban/tasks/voxel-focus-promotion-demotion.md"
title: "Voxel 3: voxel focus promotion/demotion (dynamic band)"
priority: "P1"
status: "done"
estimate: "5"
uuid: "voxel-focus-promotion-demotion"
created_at: "2026-07-22T00:00:00Z"
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

Triage 2026-07-22: Voxel 1+2 done + committed (acb3e59, 867680f), both reviewed. Dispatching impl agent for the keystone: dynamic-band promotion/demotion + deferred queue + edit-diff emission. blocked -> in_progress.

Complete + reviewed 2026-07-22 — THE KEYSTONE LANDS. Architecture: (1) BAND = c/voxel-band on the committed world (commitment write-once, no churn); conservation is representational band = field+diffs, exact by construction, honestly distinct from Phase 0's ledger-debit form (edit-mass debit belongs to slices 4-6 resource cells). (2) QUEUE = c/voxel-edit-queue single drain path, estimated cost never wall-clock, strict FIFO, chunk = 1950 voxels = exactly 2ms; replay order == drain order; retarget sweep ~5 ticks visible. (3) DIFFS = c/voxel-edit-diffs component (save representation, one writer), per-provenance-group records, untouched regions emit nothing. Depth = 10km x coherence x focus-intensity (observation-effect — attention rule projected downward), clamped. Review PASS-WITH-NITS, all 4 resolved: real provable voxel cap (n(h,d) bound + pin test), carve round-trip test, per-chunk multi-provenance fold test, edits->jobs chunking helper + max-edits-per-job for slices 4-6. Suite 735/13896 green; architecture green; write-conflicts {}. in_progress -> done. Substrate PROVEN — unblocks slices 4+5 (owner said hold 4-6 until 1-3 prove out; that condition is now met).
---