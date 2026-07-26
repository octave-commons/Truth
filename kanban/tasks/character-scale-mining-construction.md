---
uuid: "character-scale-mining-construction"
title: "Voxel 6: character-scale mining + construction"
status: "blocked"
priority: "P2"
labels: ["specs", "phase1", "voxel", "character", "epic-planetary-voxel-substrate"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/character-scale-mining-construction.md"
category: "specs"
estimate: 5
---

# Voxel 6: character-scale mining + construction

> Parent epic: `kanban/tasks/planetary-voxel-substrate.md`
> Design: `docs/designs/planetary-voxel-substrate.md` §5, §7.5.
> Blocked on: `voxel-focus-promotion-demotion`, `voxel-god-scale-sculpting-ops`.
> Explicitly deferred until the character-scale narrowing rung exists
> (`embodied-character-voxel-mode`, icebox).

**Goal:** Direct voxel remove/place/reshape tool verbs for the far-future
single-character mode, plus a first construction-stability check.

## Scope

- Mining: remove voxel, yield material by depth/region (ore veins, mantle
  xenoliths per design §7.4); digging past the band bottom interacts with
  the focus-driven dynamic band rule (owner decision: deeper focus deepens
  the world — escalating cost is the natural steer, confirm at build time).
- Construction: place/reshape voxels; first stability model over supported
  voxel columns (rigid-body proxy per design §7.5 — the honest first model,
  not a stress-relaxation engine).
- All edits through the deferred queue; all deviations emit edit diffs.

## Done when

- Mine/place/reshape verbs work on a resolved band with yields, stability
  check flags unsupported structures; suite + architecture green.

---
Created 2026-07-22 (resumed session): slice 6 of the approved breakdown.
Deferred behind the character-scale rung by design.
---
