---
category: "specs"
labels: ["specs", "phase1", "phase2", "voxel", "geology", "chemistry", "epic"]
write-id: "1784768196746-0.a3k5uaeh0nwl0miuft"
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

Owner decisions 2026-07-22 (Aaron, via question tool): (1) PERF = deferred edit queue, hard 2ms/tick cap, budget constant in law/ — big carves visibly form over ~a second (felt mass, a feature); (2) BOUNDARY = focus-driven dynamic band — voxels follow focus intensity including depth, deeper play deepens the world; band must round-trip losslessly through field+diff as focus moves; (3) SAVE = field-seed + edit diff — persist only deviations from the deterministically regenerated macro-field seed; (4) BREAKDOWN APPROVED — 6 slices materialized as cards (voxel-substrate-law-schema READY; planet-candidate-to-voxel-seed, voxel-focus-promotion-demotion, voxel-god-scale-sculpting-ops, collision-shock-voxel-carving, character-scale-mining-construction blocked in dependency order); sequence 1->2->3 first, hold 4-6. Design doc §7 items 1-3 marked RESOLVED, §8 marked APPROVED. Dispatching slice 1.

Epic progress 2026-07-22: slices 1-5 + field-bias persistence ALL done, reviewed, committed (acb3e59 -> b2ea489). The substrate is real end-to-end: law schemas -> deterministic seed -> dynamic-band promotion/demotion + 2ms queue + edit diffs -> god-scale sculpting (Resonance-spent, macro-drives-local) -> collision carving (research-grounded scaling laws) -> save/load (regenerate + replay field-diffs + voxel diffs). Suite 758/14076 green throughout. REMAINING: slice 6 (character-scale mining) deferred by design until embodied-character-voxel-mode (icebox) exists. Epic stays breakdown as umbrella until then.

INTEGRATION-DEBT CORRECTION 2026-07-23 (Claude, re-triage after Aaron's field report): "done end-to-end" above means DOMAIN-complete and tick-wired — it is NOT player-visible. A grep confirms NOTHING in src/infra/ reads any voxel component: there is no render path (`c/voxel-band` is never drawn — zoom shows a plain sphere), the god-scale sculpt verbs are built but bound to no keys (`sculpt.clj` docstring records the gap), and the band only materializes AFTER the commitment horizon with no HUD feedback that it happened. The design doc's own `infra/render/` mesher/renderer line (§ line 158-159) was never materialized as a card. This epic is NOT playable and cannot honestly close until the new integration-debt cards land: voxel-band-render-path, voxel-sculpt-verb-palette-wiring, narrowing-hud-binding-commitment-readout, narrowing-worldscale-overlap-gate, spark-planet-binding. Individual slice cards (voxel-focus-promotion-demotion, voxel-god-scale-sculpting-ops, collision-shock-voxel-carving) are domain-done but player-invisible — read them with that caveat.
---