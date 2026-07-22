---
category: "specs"
labels: ["specs", "phase1", "voxel", "epic-planetary-voxel-substrate"]
write-id: "1784757170540-0.20oc0j0eopfohwgohfb"
source: "kanban/tasks/planet-candidate-to-voxel-seed.md"
title: "Voxel 2: planet-candidate -> macro geology field seed"
priority: "P1"
status: "done"
estimate: "5"
uuid: "planet-candidate-to-voxel-seed"
created_at: "2026-07-22T00:00:00Z"
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

Triage 2026-07-22: Voxel 1 done + committed (acb3e59), schemas reviewed. Dispatching impl agent for the seed generator. blocked -> in_progress.

Complete + reviewed 2026-07-22. domain.interior pure deterministic seed: body figure via uniform-density inversion R=3g/(4pi*G*rho) (candidate lacks mass/radius); layer template core=FeNi/0.85 clamped, mass-exact shells; mineral enrichment redistributing (downwelling Fe/Ni x3, upwelling S x2, polar ice gated); thermal surface=equilibrium-temp + 15K/km capped. Determinism: no PRNG, closed-form Fibonacci spirals pinned to rotation-axis. Review PASS-WITH-NITS, all 3 resolved: Earth anchor pinned (R/M within 20%), iron lever zero-centered per class (icy rho 1265->2015, exposed + fixed latent ice-shell volume inconsistency), FP reductions sorted (spec-stable). :gaseous candidates fail loud (no solid surface). Suite 722/13834 green; architecture green. in_progress -> done. Unblocks voxel-focus-promotion-demotion (the keystone).
---