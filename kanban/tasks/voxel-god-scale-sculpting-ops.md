---
uuid: "voxel-god-scale-sculpting-ops"
title: "Voxel 4: god-scale sculpting ops (palette -> field bias)"
status: "blocked"
priority: "P2"
labels: ["specs", "phase1", "voxel", "epic-planetary-voxel-substrate"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/voxel-god-scale-sculpting-ops.md"
category: "specs"
estimate: 5
---

# Voxel 4: god-scale sculpting ops (palette -> field bias)

> Parent epic: `kanban/tasks/planetary-voxel-substrate.md`
> Design: `docs/designs/planetary-voxel-substrate.md` §5 (macro-drives-local).
> Blocked on: `planet-candidate-to-voxel-seed`, `voxel-focus-promotion-demotion`.

**Goal:** Wire the Phase 1 ability palette (Tectonics/Hydrography/Atmosphere)
to bias the macro geology field and, where focus overlaps, drive the
resulting local voxel edits (erosion, uplift, volcanism).

## Scope

- Palette verbs bias the MACRO field statistically; local voxel edits fall
  out of the field change where the band is resolved — never direct
  god-finger voxel pokes (macro-drives-local rule, design §5).
- All voxel edits flow through the deferred queue (2 ms/tick cap).
- Resonance/Agency costs wire through the existing commitment/palette state
  (`c/palette`, Narrowing B) — check what the palette actually carries by
  then; gap honestly if verbs are still infra-side.

## Done when

- At least uplift + erosion + volcanism bias the field and produce queued
  local edits under focus; conservation respected; suite + architecture
  green; `write-conflicts {}`.

---
Created 2026-07-22 (resumed session): slice 4 of the approved breakdown.
Held until slices 1-3 prove out (owner sequencing call).
---
