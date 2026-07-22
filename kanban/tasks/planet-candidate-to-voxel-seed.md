---
uuid: "planet-candidate-to-voxel-seed"
title: "Voxel 2: planet-candidate -> macro geology field seed"
status: "blocked"
priority: "P1"
labels: ["specs", "phase1", "voxel", "epic-planetary-voxel-substrate"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/planet-candidate-to-voxel-seed.md"
category: "specs"
estimate: 5
---

# Voxel 2: planet-candidate -> macro geology field seed

> Parent epic: `kanban/tasks/planetary-voxel-substrate.md`
> Design: `docs/designs/planetary-voxel-substrate.md` §4.
> Blocked on: `voxel-substrate-law-schema`.

**Goal:** Pure functions turning the M5 `:planet-candidate` handoff record
into the initial macro geology field — the deterministic seed the whole
save/persistence strategy regenerates from.

## Scope

- `domain/interior.clj` (or agreed name): `:planet-candidate` ->
  macro geology field: layer template (core/mantle/crust fractions from
  `:bulk-composition` + `:surface-gravity`), mineral/ore distribution
  (richer near convergent margins, hotspots, impact sites per design §7.4's
  qualitative steer), initial thermal state consistent with
  `domain.environment`.
- MUST be a pure deterministic function of the candidate record — the
  field-seed + edit-diff save strategy (owner decision 2026-07-22) depends on
  regenerate-from-seed producing the identical field.
- Layer-thickness translation is a design gap (§7): pick an honest first
  model (documented constants in `law/`), do not invent false precision.
- Tests: seed determinism (same candidate -> identical field), composition
  conservation (field mass == candidate mass), thermal agreement with
  environment state, schema conformance of every emitted record.

## Done when

- Deterministic seed + tests green; `architecture-test` green.

---
Created 2026-07-22 (resumed session): slice 2 of the approved breakdown.
---
