---
uuid: "radial-disk-structure-spec-deferred-capability-d1"
title: "Radial Disk Structure Spec (deferred capability D1)"
status: "todo"
priority: "P1"
labels: ["specs"]
created_at: "2026-07-06T16:24:25.293741153Z"
source: "kanban/tasks/radial-disk-structure-spec-deferred-capability-d1.md"
category: "specs"
---

# Radial Disk Structure Spec (deferred capability D1)

**Status:** deferred — capability-gated
**Trigger (activate when):** the single-annulus disk regime — one Toomre Q evaluated at a fixed `disk-outer-temperature = 100 K` (`stellar.clj:1443,1554`) — demonstrably misclassifies a disk that a radial profile would resolve. Concretely: a test disk whose *outer* annulus is Toomre-unstable and fast-cooling while its global/average Q is stable, so the scalar classifier reports `:stable-disc` and refuses to fragment where a real disk would. When such a case appears in M3 testing or play, promote this spec.
**Precondition:** M2–M3 landed (there is a solid budget and a working single-annulus regime to compare against).
**Unblocks:** D2 (migration), D3 (pressure-dependent condensation), D6 (pebble accretion).
**Depends on:** `docs/research/physics/protoplanetary-disks-planet-formation.md` §5 (toy `T(r)`, `Σ(r)`), `kanban/tasks/protoplanetary-disk-and-planet-formation-spec.md`.

***

## 1. Why this is deferred, not abandoned

Today the disk is two scalars per star: `c/disk-mass` + `c/disk-angular-mom`. The regime classifier evaluates one Toomre Q at a single fictitious annulus with a fixed temperature. This is honest enough while:

- planets are seeded one-shot and stay static (no migration to place radially),
- condensation uses a single reference pressure,
- accretion is planetesimal-based (no Stokes number needed).

The moment any of those stops being true — migration, pressure-dependent snow lines, pebble accretion — we need `Σ(r)` and `T(r)`. So this capability is the shared precondition for D2/D3/D6, and its own trigger is "the scalar model provably lies."

***

## 2. What it adds

### 2.1 Radial profile component

```clojure
(def disk-profile :component/disk.profile)
;; value: annulus-resolved arrays over log-spaced radii r_i
{:radii        [r0 r1 …]        ; m, log-spaced from r_in to r_out
 :sigma-gas    [Σg0 Σg1 …]      ; kg/m²
 :sigma-solid  [Σs0 Σs1 …]      ; kg/m²
 :temperature  [T0 T1 …]        ; K
 :pressure     [P0 P1 …]        ; bar (for D3)
 :toomre-q     [Q0 Q1 …]
 :cooling-beta [β0 β1 …]}
```

Seed the profile from the research toy model (§5.1) then evolve it viscously:

```
T(r) = T0 · (r / 1 AU)^(−1/2)      (flared, passively irradiated; Chiang & Goldreich 1997)
Σ(r) ∝ r^(−3/2)                    normalized so ∫2πr Σ dr = c/disk-mass
P(r) from Σ, T, and vertical hydrostatic balance
```

### 2.2 Per-annulus regime

Replace the single scalar Toomre Q with a per-annulus `[:toomre-q]`/`[:cooling-beta]` array; the disk's regime becomes "unstable in annuli where Q≤1 ∧ β<3." GI fragments spawn at the radius of the most-unstable annulus, not from global disk mass.

### 2.3 Migrating snow line

The snow line becomes the radius where `T(r) = Tc(H₂O)`; as the star brightens, `T(r)` rises and the snow line moves outward — the solid-surface-density jump (currently a fixed 3.5×) follows it.

***

## 3. Consumers unblocked by this spec

| Consumer | Needs from the profile |
|----------|------------------------|
| **D2 migration** | `Σ(r)`, `T(r)` for Type I/II torque; local `Σ_gas` gradient |
| **D3 pressure condensation** | `P(r)` to scale `Tc(P)` in `partition-solids` |
| **D6 pebble accretion** | Stokes number `τ_s(r)` from local `Σ_gas`, `T`, drift velocity |
| M3 seeder (retrofit) | per-annulus `Σ_solid` instead of the single-annulus value |

***

## 4. Tests (write at promotion)

1. `outer-annulus-fragments-when-global-stable` — the exact case in the trigger: scalar says stable, radial profile fragments the outer annulus.
2. `snow-line-moves-outward-as-star-brightens`.
3. `profile-conserves-total-disk-mass` — `∫2πr Σ dr = c/disk-mass`.
4. `regime-per-annulus-matches-scalar-in-flat-limit` — a flat disk reproduces the single-annulus result (backward-compatibility guard).

***

## 5. Promotion path (at trigger)

| File | Change |
|------|--------|
| `src/domain/ecs/components.clj` | Add `disk-profile`. |
| `src/law/planet_formation.clj` | Profile schema; `T(r)`, `Σ(r)` constants. |
| `src/domain/stellar.clj` | `disk-evolution-system` maintains `c/disk-profile`; per-annulus regime. |
| `src/domain/planet_formation.clj` | Seeder + (future) migration read the profile. |

***

## 6. Decisions

1. **Log-spaced annuli**, count TBD at promotion (start ~16; enough to resolve one snow line and an unstable outer edge).
2. **Passive-flared temperature** to start; viscous/accretion heating is a later refinement gated on need.
3. **Backward compatible:** the flat-disk limit must reproduce the M3 single-annulus regime, guarded by test §4.4.

## 7. Open questions

1. Annulus count vs. cost — decide empirically at promotion.
2. Whether to store the profile as a component or recompute per tick from scalars + a shape function (memory vs. CPU) — decide at promotion.
