---
category: "specs"
labels: ["specs", "em", "chemistry"]
write-id: "1784772546286-0.hsxq8fmct7hlnesmyxl"
source: "kanban/tasks/render-material-color-element-keys.md"
title: "Nebular Chemistry and Composition Spec"
priority: "P1"
status: "done"
uuid: "nebular-chemistry-and-composition-spec"
created_at: "2026-07-06T16:24:25.290930634Z"
---

# Nebular Chemistry and Composition Spec

**Status:** ready for implementation
**Milestone:** M0 (substrate, done) + M2 (condensation & consumers) in `kanban/tasks/roadmap-phase-0-physics-honesty-chemistry-disks-plasma-inspection.md`
**Scope:** the composition *data model* — explicit elements, the derived temperature-dependent solid/gas partition, and the consumers that read it. Where metals *come from* (seeding, enrichment) is `kanban/tasks/metal-enrichment-and-seeding-spec.md`.
**Depends on:** `docs/research/physics/nebular-chemistry-metal-enrichment.md`

***

## 1. Goal

The simulation currently treats metals as a single opaque fraction (`:metals 0.01`) and seeds every parcel with the same primordial lump. This hides the physical origin of heavy elements, makes planet composition non-emergent, and prevents honest planetesimal chemistry.

This spec makes composition:
- **traceable**: every element has a source (BBN, stellar nucleosynthesis, supernova enrichment);
- **conserved**: mass and element fractions are preserved through accretion, merging, winds, and burning;
- **phase-aware**: a derived solid/gas partition reflects the dust condensation sequence at each body's temperature.

***

## 2. Invariants

1. `c/composition` is always a map of element keyword → mass fraction.
2. The sum of mass fractions in `c/composition` is 1.0 (within floating-point tolerance).
3. Elements heavier than lithium are produced only by stellar processing / enrichment events, not asserted at world creation beyond the Population-I floor.
4. `c/comp-condensed` is a **derived** read-only partition `{:solid element-map :gas element-map}`; it does not mutate atomic totals.
5. No code outside `domain.chemistry` and `domain.integrator` computes element blends.

***

## 3. Element inventory

Track the following elements by mass fraction:

```clojure
#{:H :He :D :He3 :Li7 :C :N :O :Ne :Na :Mg :Al :Si :S :Ca :Fe :Ni}
```

Molecules (`:H2O :CO2 :NH3 :CH4 :volatiles :ices :silicates :metals`) are derived categories, not independent composition entries.

***

## 4. Primordial and Population-I compositions

### 4.1 Primordial (Big-Bang Nucleosynthesis)

| Element | Mass fraction | Source |
|---------|---------------|--------|
| :H      | 0.7540        | BBN    |
| :He     | 0.2460        | BBN    |
| :D      | 5.0e-6        | BBN    |
| :He3    | 1.5e-6        | BBN    |
| :Li7    | 8.3e-10       | BBN    |
| all others | 0.0        |        |

### 4.2 Population-I cloud floor

A young star-forming cloud starts with the solar/Population-I mass fractions from Asplund+2009:

```clojure
{:H 0.7346 :He 0.2485 :O 5.92e-3 :C 2.40e-3 :Ne 1.76e-3 :Fe 1.30e-3
 :N 6.96e-4 :Si 6.51e-4 :Mg 5.78e-4 :S 4.42e-4 :Al 4.90e-5
 :Ca 6.20e-5 :Na 3.30e-5 :Ni 2.70e-5}
```

Total metallicity `Z = 0.0167`.

***

## 5. Component changes

### 5.1 `c/composition`

Change from `{:H 0.75 :He 0.24 :metals 0.01}` to explicit element map.

### 5.2 New component: `c/comp-condensed`

```clojure
(def comp-condensed :component/comp.condensed)
```

Value shape:

```clojure
{:solid {:Fe 0.0013 :Si 0.00065 ...}
 :gas   {:H 0.7346 :He 0.2485 ...}}
```

### 5.3 `c/comp-burn` (existing)

Continues to carry the post-H-burning element map for stars/protostars. The integrator applies it to `c/composition`.

***

## 6. System responsibilities

### 6.1 `domain.chemistry`

New public functions:

- `(solar-composition)` → Population-I element map.
- `(primordial-composition)` → BBN element map.
- `(solid-fraction temperature Tc)` → smooth condensed fraction, **sigmoid** `1/(1+exp((T−Tc)/ΔT))`, ΔT≈30 K (research §4.3). **Replaces the current hard `(< temp tc)` step** (`chemistry.clj:105`).
- `(partition-solids element-map temperature)` → `{:solid ... :gas ...}`, per-element split by `solid-fraction` against the Lodders `Tc` table. Elements partly condensed at `T≈Tc` are split proportionally, not all-or-nothing.
- `(bulk-categories element-map temperature)` → `{:gas :rock :metal :ice}`, **derived on demand** from the partition (never stored — resolves open Q2).
- `(condensed-inventory element-map temperature)` → combines the above.
- `(blend-compositions c1 m1 c2 m2)` → mass-weighted element map.
- `(burn-composition element-map fraction)` → H → He conversion, conserving mass.
- `(enrich-composition element-map mass delta-mz yield-map)` → add metals from an event.
- `(wind-composition star-composition)` → surface composition of a wind parcel.

### 6.2 `domain.stellar` (seeding — see metal-enrichment-and-seeding-realspec)

Seeding and per-spawn composition are specified in full in `metal-enrichment-and-seeding-realspec.md` (M1). Summary: `spawn-clump` seeds the Pop-I floor by default (`:genesis/metallicity`); all spawn specs and accrete packets carry explicit `:composition`.

### 6.3 `domain.integrator`

- Register `c/comp-condensed` as a derived component in the influence registry. **(Done: `integrator.clj:485`, `registry.clj:104`.)**
- In `composition-ws`, after applying `c/comp-burn` and absorbed blends, compute and write `c/comp-condensed` from the body's temperature. **(Done.)** Derive for resolved bodies only; do not cache (resolves open Q1 — on-demand is cheap enough at one fold/tick).

### 6.4 `domain.em`

- Lorentz acceleration scales with `c/ionization-fraction`. Neutral parcels (`ionization < 0.01`) feel zero Lorentz force. **(Appears wired: `em.clj:146,378`.)**

### 6.5 Consumers of `c/comp-condensed` (M2 — this is the missing half)

The partition is derived every tick but has **no readers** today. M2 gives it consumers:

- **`infra.render/composition->material-color`** (`render.clj:816`) currently reads retired keys `:metals :ice :H2O :volatiles`, which do not exist in the element map — so every body renders gas-tan. **Fix:** read `chemistry/bulk-categories` (`:rock :metal :ice :gas`) and map those to color. This is a live silent bug (task card `render-material-color-element-keys`).
- **`domain.planet-formation/planet-seeds`** must set seeded-planet bulk composition from `c/comp-condensed` at the formation radius (see core-accretion-physics-realspec), not the static per-type lookup at `planet_formation.clj:73`.
- **Disk GI fragments** (`stellar.clj:1717`) copy parent `c/composition` verbatim; that is acceptable for gas-giant embryos (they capture gas, not condensate), so no change required — but note it explicitly so it is a decision, not an oversight.

***

## 7. Tests

1. `composition-sums-to-unity` for primordial, solar, and blended maps.
2. `burn-conserves-mass-and-converts-h-to-he`.
3. `partition-solids-water-ice-at-snow-line` matches Lodders condensation temperatures.
4. `accretion-blend-is-mass-weighted`.
5. `neutral-parcel-feels-no-lorentz-force`.
6. `stellar-wind-carries-star-composition`.

***

## 8. Promotion path

| File | Change |
|------|--------|
| `src/law/composition.clj` | Add element set, solar/BBN compositions, bulk-category schema, condensation table. |
| `src/domain/chemistry.clj` | Implement partition, blend, burn, enrich, inventory functions. |
| `src/domain/ecs/components.clj` | Add `comp-condensed`. |
| `src/domain/integrator.clj` | Register and derive `c/comp-condensed`; update composition blend. |
| `src/domain/stellar.clj` | Seed explicit composition in all spawn specs and packets. |
| `src/domain/em.clj` | Scale Lorentz force by ionization fraction. |
| `test/domain/chemistry_test.clj` | Add tests for all new functions. |
| `test/domain/integrator_test.clj` | Verify blend + condensation derivation. |

***

## 9. Decisions

1. **Disk planet seeding:** keep the snow-line model for the radial location split, but use `c/comp-condensed` to set the seeded planet's bulk composition (rock/metal inside, rock/ice/volatiles outside).
2. **Supernova enrichment:** a background parameter (`:genesis/metallicity`) for Phase 0; explicit ledger events are deferred to galactic-scale chemistry in later phases.
3. **Helium enrichment:** not tracked in Phase 0; the Population-I floor already includes the enriched helium value.

## 10. Resolved questions (were open)

1. **Perf cost of deriving `c/comp-condensed` per body per tick** → **On demand, no cache.** It is folded once per tick for resolved bodies only; measure before optimizing. If a profile ever shows it hot, cache is a follow-up, not a precondition.
2. **Molecules derived on demand vs cached component** → **On demand.** `bulk-categories`/molecule views are pure functions of `(element-map, T)`; never stored. Keeps ECS memory flat (epic risk §8).
3. **Sigmoid vs hard-step condensation** → **Sigmoid**, ΔT≈30 K (§6.1).
4. **Pressure-dependent Tc** → **Deferred (D3)**, gated on the radial disk profile (radial-disk-structure-realspec) which supplies local pressure. Single 10⁻⁴-bar Lodders table until then.
5. **Full C/O molecular network** → **Deferred (D8)**, gated on a Phase-1 atmosphere-chemistry consumer of C/O.

---
Triage 2026-07-10 (stays ready): PARTIAL — M0 element-resolved composition substrate + M2 render/consumer done & tested. Remaining ready slice: §6.1 sigmoid solid-fraction (currently hard step in chemistry/partition-solids) + §6.5 planet composition from c/comp-condensed at formation radius (currently static pfc/planet-composition table).

Triage 2026-07-22: differentiation sibling card done (5bc368a). Picking up the remaining slice per 2026-07-10 triage: §6.1 sigmoid solid-fraction (replace hard step, ΔT≈30K) + §6.5 planet composition from c/comp-condensed at formation radius (replace static pfc/planet-composition table). Dispatching impl agent. ready -> in_progress.

Remaining slice complete + reviewed 2026-07-22 — CARD COMPLETE. Sigmoid condensation s(T)=1/(1+exp((T-Tc)/30K)) replaces the hard step in partition-solids AND bulk-categories (render/hydro/classifier consumers get smoothness, no regressions; no consumer depended on step sharpness). Planet seeding: local midplane T via the same blackbody as the snow line (T=170K exactly at the split — radial model kept), core = normalized ACCRETABLE condensate from c/comp-condensed (gas-former filter at the accretion boundary — review confirmed physically correct site: absolute 30K sigmoid leaks ~6.5% H at 100K, filtered species stay in the disk, mass conservation intact), envelope mass-weighted core-m/gas-m via blend-compositions. Static table fully deleted. Review PASS-WITH-NITS, meaningful nits fixed (per-mille->percent docstring; comp-condensed grain-inventory warning on the component). Suite 777/14372 green; architecture green; write-conflicts {}. in_progress -> done.
---