---
category: "specs"
labels: ["specs", "phase1", "voxel", "persistence", "epic-planetary-voxel-substrate"]
write-id: "1784768178181-0.q5ultb0mkvqygham275"
source: "kanban/tasks/voxel-field-bias-persistence.md"
title: "Voxel: field-bias persistence (save-story completion)"
priority: "P2"
status: "done"
estimate: "3"
uuid: "voxel-field-bias-persistence"
created_at: "2026-07-22T00:00:00Z"
---

# Voxel: field-bias persistence (save-story completion)

> Parent epic: `kanban/tasks/planetary-voxel-substrate.md`
> Surfaced by: Voxel 4 review (2026-07-22), nit 1.

**Gap:** The §7.3 save story (field-seed + edit diff) covers VOXEL edits
only. God-scale sculpt ops (Voxel 4) also bias the MACRO field (plate
velocities, convection speeds, resource-cell masses). Those biases leave no
persistent trace: on an unresolved world the bias is invisible to saves; on
a resolved world the band diff survives while the field-side debit (e.g.
erosion's resource export) evaporates on load — cross-session field/band
divergence. Latent today (no save/load exists), but every sculpt op widens
the hole.

## Scope

- Extend the edit-diff vocabulary (or add a sibling field-diff record) so
  macro-field biases persist alongside voxel diffs in the same ordered,
  replayable stream: load = regenerate seed + replay field-diffs + replay
  voxel diffs.
- Sculpt ops emit their bias as a replayable record at op time (the op IS
  the diff — likely re-emit the op record itself with magnitude/anchor/tick).
- Round-trip tests: sculpt on unresolved world -> save -> load -> field
  identical; sculpt on resolved world -> field and band both consistent
  after load.

## Done when

- Field biases replay losslessly; no cross-session field/band divergence
  path remains; suite + architecture green.

---
Created 2026-07-22: routed from the Voxel 4 review — the only unrouted
save hole in the substrate.

Triage 2026-07-22: Voxel 4+5 done (thru bf519c4). Picking this up directly — every sculpt op widens the save hole; fix before slice 6 era. ready -> in_progress.

Complete + reviewed 2026-07-22. The save hole is CLOSED: law.voxel/field-diff-schema (the op IS the diff — full sculpt-op record + fold tick + optional body); sculpt fold-ops emits per-op records in fold order; :voxel-focus stamps/validates/appends to c/voxel-field-diffs (5th write column, sole writer). domain.voxel.load: save-state/load-state — load = regenerate seed + replay field-diffs (same pure apply-op, stream order, bit-for-bit) + replay voxel diffs onto the biased field. Round-trips proven: unresolved-world field identical (with negative control), resolved-world field+band consistent, interleaved ops/edits compose, collision diffs still replay. Review PASS-WITH-NITS, both resolved: save-state now THROWS on live band (fail-loud precondition + pin test); seed-voxel-bias invariant named in field-diff-schema docstring. Design doc §7.3 extended. No-band collision gap honestly stays (no macro-field consequence exists to persist). Suite 758/14076 green; architecture green; write-conflicts {}. in_progress -> done.
---