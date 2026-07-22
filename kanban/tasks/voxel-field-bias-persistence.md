---
uuid: "voxel-field-bias-persistence"
title: "Voxel: field-bias persistence (save-story completion)"
status: "ready"
priority: "P2"
labels: ["specs", "phase1", "voxel", "persistence", "epic-planetary-voxel-substrate"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/voxel-field-bias-persistence.md"
category: "specs"
estimate: 3
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
---
