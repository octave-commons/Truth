---
category: "specs"
labels: ["specs", "phase1", "voxel", "epic-planetary-voxel-substrate"]
write-id: "1784755533841-0.wpz1yefacrpa3loi3dg"
source: "kanban/tasks/voxel-substrate-law-schema.md"
title: "Voxel 1: voxel-substrate law schemas"
priority: "P1"
status: "done"
estimate: "3"
uuid: "voxel-substrate-law-schema"
created_at: "2026-07-22T00:00:00Z"
---

# Voxel 1: voxel-substrate law schemas

> Parent epic: `kanban/tasks/planetary-voxel-substrate.md`
> Design: `docs/designs/planetary-voxel-substrate.md` §3, §7.

**Goal:** The `law/` vocabulary the whole voxel ladder speaks — schemas only,
no behavior.

## Scope

- `law/voxel.clj`:
  - Voxel record schema: material (mineral enum — open set, see design §7.4),
    density, temperature, state (`:solid`/`:melt`/`:vapor`/`:suspended`),
    cohesion.
  - Macro geology field record schemas: plate, mantle-convection-cell,
    resource-field cell (the coarse field that exists everywhere).
  - **Edit-diff record schema** (owner save-strategy decision 2026-07-22:
    field-seed + edit diff): the persisted unit of a player/collision edit
    against the regenerable field seed — target region, voxel delta,
    provenance (`:sculpt`/`:mine`/`:construct`/`:collision`), tick.
  - Reuse `law.composition` element buckets; do not duplicate them.
- Malli validators for each; registered per `law/` conventions.
- No behavior, no systems — slices 2-6 build on these types.

## Done when

- Schemas + validators compile and round-trip representative records in tests.
- `clojure -M:test` green; `architecture-test` green.

---
Created 2026-07-22 (resumed session): slice 1 of the approved 6-slice
breakdown (design doc §8). Owner decisions baked in: edit-diff schema is the
persistence unit (field-seed + diff save strategy).

Complete + reviewed 2026-07-22. law.voxel: voxel-schema (material open-set w/ seed-materials, density kg/m3, temperature K, state solid/melt/vapor/suspended, cohesion Pa), plate/mantle-convection-cell/resource-cell macro-field schemas (element-density reusing law.composition/element-set), edit-diff-schema (persistence unit: region + delta + provenance + tick), edit-budget-ms-per-tick 2.0 constant pinned in law/. Review verdict PASS-WITH-NITS; both load-bearing nits resolved: optional :body on edit-diff (multi-body collision saves), docstring-pinned canonical-grid convention + intra-tick replay order = collection order; added missing voxel-edit?/edit-provenance?/element-density? predicates. Suite 711/13692 green; architecture green. in_progress -> done. Unblocks planet-candidate-to-voxel-seed.
---