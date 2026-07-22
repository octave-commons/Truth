---
category: "specs"
labels: ["specs", "phase1", "phase2", "voxel", "geology", "chemistry", "epic"]
write-id: "1784749026039-0.69oskqfw2m6dewu8kwv"
source: "kanban/tasks/planetary-voxel-substrate.md"
title: "Epic: Planetary voxel substrate (chemistry, geology, sculpting)"
priority: "P1"
status: "breakdown"
estimate: "34"
uuid: "planetary-voxel-substrate"
created_at: "2026-07-22T00:00:00Z"
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

Design promoted 2026-07-22 (Claude): canonical doc at docs/designs/planetary-voxel-substrate.md. Duality: planet is a coarse macro-geology field everywhere, voxel mass only inside the focus cone (same statistical/voxel duality as Phase 0, one scale deeper). M5 :planet-candidate seeds the initial layer/mineral/thermal template. TWO TIERS one substrate: god-scale sculpting (Phase 1 palette) biases the macro field; character-scale mining/construction edits voxels directly. Collisions carve via shock-physics scaling laws (no hydrocode), reusing existing collision events. PROPOSED CHILD BREAKDOWN (in the doc, NOT yet materialized as cards — awaiting owner review): 1 voxel-substrate-law-schema (3), 2 planet-candidate-to-voxel-seed (5), 3 voxel-focus-promotion-demotion (5), 4 voxel-god-scale-sculpting-ops (5), 5 collision-shock-voxel-carving (5), 6 character-scale-mining-construction (5). THREE OPEN QUESTIONS for owner: (a) performance envelope — no voxel-size / per-tick frame budget vs the 60Hz constraint; (b) interior/voxel boundary rule — where voxels stop and the macro field resumes, and what happens digging past it; (c) save strategy for a sculpted crust (sparse octree vs region files vs regenerate-from-field+diff).
---