---
uuid: "planetary-voxel-substrate"
title: "Epic: Planetary voxel substrate (chemistry, geology, sculpting)"
status: "breakdown"
priority: "P1"
labels: ["specs", "phase1", "phase2", "voxel", "geology", "chemistry", "epic"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/planetary-voxel-substrate.md"
category: "specs"
estimate: 34
---

# Epic: Planetary voxel substrate (chemistry, geology, sculpting)

> Canonical design: `docs/designs/planetary-voxel-substrate.md` (being promoted
> from the user's research transcript — child breakdown lives there).
> Seeded by: `ecology-water-gate-snowline` M5 `:planet-candidate` record.
> Gated behind: `the-first-narrowing-star-to-planet` (you must be bound to a
> world before it voxelizes).

**Goal:** Turn a committed world from a statistical/scalar field into a **voxel
mass** the player can sculpt — selective resolution driven by the focus cone
(NOT a whole-planet grid). Planetary chemistry, minerals/ore fields, geology,
and the collision→voxel-carving pipeline live here. This is the substrate the
Phase 1 sculpting palette (Atmosphere/Hydrography/Tectonics/…) and the far-future
single-character mining mode both act through — "Minecraft, but voxel-aware
planetary physics."

**The duality (design principle):** outside the focus cone = statistical field;
inside it = voxel mass. Same ECS world; resolution is a scheduling decision, one
engine, no parallel simulator.

## Status

This epic is the single largest gap between the current code and the vision:
there is NO voxel namespace in `src/` today. The canonical design doc is being
authored now; its proposed child breakdown (voxel data model + focus-driven
resolution, planetary chemistry/mineral fields, M5-seeded initial world,
collision→carving pipeline, sculpting/mining verbs) becomes the child cards once
the owner reviews the design. Estimate is a placeholder epic size.

## Done when

- `docs/designs/planetary-voxel-substrate.md` reviewed + accepted by the owner.
- Child cards broken out and executed; a committed world resolves to voxels
  under focus and back to a field when unfocused, conserving mass.
- Sculpting verbs edit voxels; impacts carve them; chemistry/ore fields resolve
  under focus. `architecture-test` green throughout.

---
Created 2026-07-22 (Claude): manifested from Aaron's vision (planet voxels,
chemistry, sculpting geography; later single-character mining). Child breakdown
pending the design-doc promotion.
---
