# Design: Resolution Regimes & Scale Coupling

**Status:** design (decided 2026-07-06)
**Owner handoff:** Claude Code → OpenCode (kimi)
**Relates to:** `docs/specs/seed-and-grow-condensation-realspec.md`, epic M5 habitability
handoff (`:planet-candidate`), `domain.lod`, observer-focus / dual-representation,
`docs/designs/ux-architecture.md`.

---

## 1. The problem

The sim must eventually represent matter across ~27 orders of magnitude:

| scale | mass |
|---|---|
| gas parcel (Phase 0 grain) | ~4e27 kg |
| Earth (life-bearing world) | ~6e24 kg |
| Moon (smallest *necessary* body) | ~7e22 kg |
| Chicxulub impactor (*ideal* smallest) | ~1e15 kg |
| crust voxel (~1 km³ rock) | ~1e12 kg |
| organism | ~1e0 kg |

No single flat representation spans this. A uniform Lagrangian parcel set fine
enough for a bacterium would need ~1e27 parcels; the tick is already super-linear
in N (1000→32 ms, 8000→332 ms), so brute-force refinement dies immediately.

## 2. Lagrangian vs Eulerian — and where the floor really is

- **Eulerian:** discretize *space* (fixed grid); matter flows through cells.
- **Lagrangian (SPH, our Phase 0):** discretize *matter* (moving parcels); each
  carries a fixed mass quantum.

Lagrangian genuinely floors one thing: you cannot resolve structure *inside* one
gas parcel. It does NOT force a floor on *resolved bodies* — that floor came from
the separate rule "a body is made of whole parcels," which seed-and-grow removes
(see the companion spec). So: gas stays coarse (fine — gas needs no km detail),
bodies get their own scale.

## 3. Principle: scale-separated regimes, nested as ECS content layers

Do not make matter uniformly fine. Give each *scale* its own resolution regime,
and a body carries the finer regime **only while it matters**. This is the
single-substrate rule applied across scale: one ECS world; each finer regime is
components + systems layered on an entity, materialized on demand — never a
parallel simulation.

| regime | substrate | quantum | when instantiated |
|---|---|---|---|
| **stellar system** (Phase 0) | Lagrangian gas parcels | ~10²–10³ M⊕ (parcels); bodies down to seed mass via seed-and-grow | always |
| **planet interior** (geology) | crust/mantle voxel field on the planet entity | ~1e12 kg/voxel | planet focused / M5 handoff |
| **life / ecology** | biomass density fields → agents within biome cells | ≪ | biosphere active / zoomed |

## 4. Two complementary mechanisms

**(A) Seed-and-grow** — lowers the *astronomical* body floor. Condensation seeds a
small physical body (asteroid→planetesimal) and grows it via M3 accretion; body
mass decouples from parcel mass. Gets you Chicxulub/Moon/Earth-scale bodies inside
Phase 0. See `seed-and-grow-condensation-realspec.md`. **Do this first.**

**(B) Nested per-entity budgets** — required for *sub-astronomical* scales (crust
voxels, life). A planet's crust/biosphere lives in **its own conserved mass
budget**, coupled to the planet's bulk at the boundary. Not built yet; Phase-1.

They are complementary, and the split is forced by numerics (§5), not taste.

## 5. The hard limit: float precision (not Lagrangian)

Doubles hold ~15–16 significant digits. A 4e27 kg parcel cannot register a change
below ~1e12 kg (its ULP). Consequences:

- Seed-and-grow works for bodies ≥ ~1e15 kg (asteroid; ~12 orders below a parcel,
  at the edge) — safe as parcel debits.
- Crust voxels (1e12) and organisms (1e0) CANNOT be bookkept as changes against a
  gas parcel — they vanish in roundoff. They must live in a **separate accumulator
  / per-planet budget** (mechanism B). Never subtract tiny-from-huge.

This numeric boundary is *why* B is a distinct regime, not just "smaller seeds."

## 6. Coupling contract (aggregate ↔ detail)

- The coarse aggregate is the **budget** the finer layer partitions. A planet's
  Phase-0 bulk mass/composition/thermal/angular-momentum (the M5 `:planet-candidate`
  record) is what the crust voxels must sum back to. Conservation across the scale
  boundary is the contract — same discipline as the M3 mass-conservation fix, one
  level up.
- **LOD instantiate / collapse:** zoom in → materialize the fine layer procedurally
  from (aggregate + seed), conserving totals; zoom out → collapse to the aggregate
  (summarize/freeze). `domain.lod` + observer-focus are the existing seam (today
  they gate detail; extend them to gate *which regime is instantiated*).
- **Regenerate-from-seed:** if the fine layer is a deterministic function of
  (aggregate + seed), collapse can discard it and re-expand identically — this is
  what bounds memory. **Prerequisite:** fix the known `create-world`
  nondeterminism (fixed seed still differs run-to-run) before regenerate-from-seed
  is trustworthy.

## 7. Roadmap position

- **Now / Phase 0:** seed-and-grow (A). Parcel grain stays ~669 M⊕ (compute-locked
  for real-time); bodies decouple via seeding.
- **Phase 1:** nested per-planet budgets (B) — crust voxel field + biome/biomass
  layers as LOD-gated content on the planet entity, coupled by the M5 handoff.
- **Cross-cutting prerequisite:** `create-world` determinism (for regenerate-from-seed).

## 8. Non-goals / rejected

- Global parcel refinement to reach sub-astronomical scale (compute-dead, §1).
- A parallel/second world model per scale (violates the single-substrate rule).
- Bookkeeping organism/voxel mass against gas-parcel mass in one number (float-dead, §5).
