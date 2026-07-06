# Phase 0 Chemistry & Differentiation Spec

**Status:** ready for implementation
**Milestone:** M4 in `docs/specs/epic-phase0-physics-honesty.md`
**Goal:** Make composition a first-class simulation driver in Phase 0: it should determine material class, collision outcome, and the rough volatile budget a planet carries to Phase 1.
**Principle:** Chemistry is not decorative. The same mass fractions that describe a nebula clump must also describe a finished planet, and the transition between them is governed by temperature and gravity.

> **Model update (2026-07-06):** This spec predates the element-resolved composition model. The `:metals` lump referenced below is **retired** (`law/composition.clj:9`); composition is now an explicit element map (`:H :He :O :C :Fe :Si …`), and material groups (`:rock :metal :ice :gas`) are **derived on demand** via `domain.chemistry/bulk-categories` — see `nebular-chemistry-realspec.md`. Read §2 in that light: "metals" means the derived metal category, not a stored key.

> **Already wired (do not rebuild):** malleability (`law.stellar/malleability`) and the merge/shatter branch (`stellar-merge-handler`, `stellar.clj:2254` → `shatter-bodies`, `stellar.clj:2215`) exist and are load-bearing; merges apply mass-weighted composition blend + impact heating (`integrator.clj:181`). M4 adds the **missing** pieces: the differentiation system, the volatile budget, volatile loss on hot merges, and density-biased fragment composition. Bounce/graze is deferred (D7).

---

## 1. Background from notes

From `docs/designs/truth-phase-0-stellar-nebula-design.md`:

> Start with a minimal elemental inventory sufficient for cosmological and planetary differentiation: hydrogen, helium, oxygen, carbon, silicon, iron, and a coarse “heavy elements” bucket if needed.

> Track composition statistically for distant matter and explicitly for focused matter.

From the formation/rendering investigation notes:

> Collision outcomes should depend on temperature/malleability: merge (molten), bounce, or fragment (brittle).

Current code:
- `domain.chemistry` exists with elemental abundance tables and helpers.
- `c/composition` is on every matter entity.
- `law.stellar/malleability` exists as a constant.
- `domain.stellar/stellar-merge-handler` **always merges**; it does not read malleability or composition.

---

## 2. Elemental model

### 2.1 Mass fractions

Composition is a map of mass fractions summing to 1.0:

```clojure
{:H 0.74 :He 0.24 :O 0.008 :C 0.003 :Si 0.002 :Fe 0.001 :metals 0.006}
```

`metals` is the coarse heavy-element bucket for everything not explicitly tracked.

### 2.2 Derived groups

| Group | Formula | Use |
|---|---|---|
| Volatiles | `H + He + H₂O-proxy + CO/CO₂-proxy` | Atmosphere retention, icy bodies |
| Silicates | `Si + O tied to Si` | Rocky mantle |
| Metals | `Fe + :metals` | Core, density |
| Organics | `C + O tied to C` | Prebiotic carbon budget |

The `O` not consumed by Si/C is available as free oxygen/water.

---

## 3. Temperature-dependent material state

At any temperature `T`, a body has a **malleability** `m ∈ [0,1]`:

```clojure
m(T) = 1 / (1 + exp((T - T_melt) / T_width))
```

where `T_melt` depends on composition (silicates ~1500 K, ices ~250 K, H/He ~0 K).

| Malleability | Behavior |
|---|---|
| `m > 0.8` | Molten — collisions merge, body differentiates by density |
| `0.3 < m < 0.8` | Ductile — collisions merge with angular momentum exchange |
| `m < 0.3` | Brittle — collisions can fragment if relative kinetic energy is high |

---

## 4. Collision outcomes

`domain.physics.collision_response` should be extended (or a new `collision-outcome-system` added) to choose among:

### 4.1 Merge

Conditions:
- `malleability` > 0.5, OR
- Relative speed at contact < escape speed of the combined body, OR
- Either body is a `:protostar` or `:star`.

Actions:
- Combine mass, momentum, angular momentum, composition (mass-weighted).
- Add impact-heating to temperature.
- Preserve larger body's `:accretion-radius` if it is a sink.

### 4.2 Fragmentation

Conditions:
- `malleability` < 0.3 (brittle).
- Relative kinetic energy at contact > cohesive energy of the smaller body.
- Neither body is a star/protostar.

Actions:
- Replace the smaller body with `k` fragments (2–5) whose total mass/momentum/angular momentum equals the original.
- Fragments inherit composition biased by density: metal/silicate enriched toward larger fragments; volatiles prefer smaller fragments.
- Emit `:event/fragmentation` to ledger.

### 4.3 Bounce / graze — DEFERRED (D7)

**Status:** deferred, capability-gated. The current model is merge-or-shatter only; a grazing elastic outcome is a third branch.

**Trigger (activate when):** brittle, high-speed **grazing** impacts (large impact parameter, low bound energy) are demonstrably mishandled by the merge-or-shatter binary — measured as an unphysically high merger rate for high-impact-parameter cold collisions in a standard run. Until that is observed, merge-or-shatter is an acceptable simplification.

When promoted:
- Condition: `malleability` low, kinetic energy below fragmentation threshold, impact parameter large.
- Apply an elastic-ish impulse conserving momentum and angular momentum; convert a fraction of relative KE to heat; do not merge.

---

## 5. Differentiation

When a molten body (`malleability > 0.8`) exists for many ticks, its composition separates by density:

| Layer | Composition | Driver |
|---|---|---|
| Core | Fe + heavy metals | Highest density, sinks |
| Mantle | Silicates | Intermediate density |
| Crust/volatiles | H/He/organics/ices | Lowest density, rises or escapes |

Differentiation is represented by updating a `:differentiated-layers` component:

```clojure
{:core-fraction     double   ; mass fraction in core
 :mantle-fraction   double
 :volatile-fraction double
 :surface-composition {:H ... :He ... :H2O ...}}
```

This does not create new ECS entities; it is a derived property used by the habitability handoff and rendering.

---

## 6. Schema additions

### 6.1 Components

```clojure
(def differentiated-layers :component/differentiated-layers)
(def malleability          :component/malleability)       ;; double [0,1]
(def volatile-budget       :component/volatile-budget)     ;; kg
```

### 6.2 law additions

- `law.chemistry/composition-schema` — mass fractions summing to 1.0.
- `law.chemistry/material-group-schema` — `:volatiles`, `:silicates`, `:metals`, `:organics`.
- `law.chemistry/malleability-schema` — double in [0,1].
- `law.chemistry/differentiated-layers-schema` — layer fractions summing to 1.0.

---

## 7. Implementation plan

### Phase 1 — Composition validation and derived groups

**Tests:**
- `composition-sums-to-one`: validator rejects mass fractions not summing to 1.0.
- `volatile-group-computed`: a high-H/He composition has high volatile fraction.
- `silicate-group-computed`: a high-Si composition has high silicate fraction.

**Implementation:**
- Move/extend `domain.chemistry` with `mass-fractions-valid?`, `material-groups`, `mean-molecular-mass`.
- Add `law.chemistry` schemas.

### Phase 2 — Malleability and collision outcomes

**Tests:**
- `molten-bodies-merge`: two warm rocky bodies merge.
- `cold-bodies-fragment`: two cold rocky bodies at high speed fragment.
- `grazing-collision-bounces`: high impact parameter, low energy → no merge.

**Implementation:**
- Add `domain.chemistry/malleability` pure function.
- Extend `stellar-merge-handler` (or add `collision-outcome-system`) to branch on malleability and kinetic energy.
- Add `domain.chemistry/fragment` helper.

### Phase 3 — Differentiation

**Tests:**
- `molten-body-differentiates`: a hot body gains core/mantle/volatile layer fractions over time.
- `cold-body-stays-undifferentiated`: a cold body retains uniform composition.
- `differentiation-conserves-mass`: total mass in layers equals body mass.

**Implementation:**
- Add `domain.chemistry/differentiation-system`.
- Run it after `thermal-system` for bodies with `malleability > 0.8`.
- Write `:component/differentiated-layers`.

### Phase 4 — Volatile budget and atmosphere seed

**Tests:**
- `volatile-budget-from-composition`: a body with high H/He/organics has a large volatile budget.
- `volatiles-lost-in-hot-collision`: impact heating can drive off volatiles.

**Implementation:**
- Add `domain.chemistry/volatile-budget` helper.
- Update collision handlers to reduce volatile budget when impact temperature is high.
- Feed volatile budget into the habitability handoff (`phase0-habitability-handoff.md`).

---

## 8. Rendering / feel

- Body color already uses composition via `infra.render/composition->material-color`.
- Fragmentation produces a brief particle spray (reuse nebula fog puff renderer).
- Differentiated rocky bodies can show a subtle core/mantle distinction in cross-section if the renderer ever supports cutaways.

---

## 9. Out of scope

- Full equation-of-state for planetary interiors — belongs to `domain.interior` in the disk spec.
- Actual atmospheric chemistry simulation — Phase 1.
- Nuclear isotope tracking for radiogenic heating — future refinement.

---

## 10. First deliverable

**Phase 1** (composition validation + derived groups) is the smallest step. It makes the existing `c/composition` data trustworthy and gives the rest of the game a vocabulary for material classes.

Next action: approve this spec, then write schemas, failing tests, and Phase 1 implementation.
