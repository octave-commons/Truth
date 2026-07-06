# Roadmap: Phase 0 Physics Honesty — Chemistry, Disks, Plasma, Inspection

**Status:** roadmap
**Supersedes:** the original `epic-phase0-physics-honesty.md` epic (kept in git history).
**Scope:** sequence every step needed so Phase 0 stops emitting planetesimals from stars, tracks real element composition end-to-end, grows planets by accretion, treats stellar wind as plasma, and lets the player see it — **including the work currently deferred, each with an explicit trigger.**

**Member specs (current):**
- `docs/specs/nebular-chemistry-realspec.md` — composition data model, condensation, consumers
- `docs/specs/metal-enrichment-and-seeding-realspec.md` — where metals come from (root fix)
- `docs/specs/protoplanetary-disk-planet-formation-realspec.md` — disk regime + GI fragmentation + seeder wiring
- `docs/specs/core-accretion-physics-realspec.md` — accretion-derived planet mass + sub-grid planetesimals
- `docs/specs/gradual-mass-transfer-realspec.md` — rate-limited sink accretion + Roche-lobe overflow; replaces whole-parcel swallowing
- `docs/specs/radial-disk-structure-realspec.md` — Σ(r)/T(r) profile (deferred capability, gated)
- `docs/specs/phase0-chemistry-differentiation.md` — malleability, collision chemistry, differentiation
- `docs/specs/phase0-habitability-handoff.md` — `:planet-candidate` contract
- `docs/specs/stellar-wind-plasma-state-realspec.md` — hot ionized wind parcels
- `docs/specs/rich-entity-inspection-ui-realspec.md` — visual verification panel

---

## 1. Why this exists

Phase 0 has a credibility chain, and today it is broken at the first link:

1. **Metals never exist.** Every parcel is seeded with primordial (zero-metal) composition (`domain/genesis.clj:70`, `domain/stellar.clj:2320`). O, C, Fe, Si, Mg are permanently 0 in a live run.
2. Because metals never exist, the **condensation partition** (`c/comp-condensed`) is derived every tick but describes nothing, and it has **no consumers** anyway.
3. Because nothing reads the partition, **render material color** silently reads retired keys (`:metals :ice :H2O`) that no longer exist, so every body renders gas-tan (`infra/render.clj:816`).
4. **Planet mass is a disk-fraction heuristic** (`min(0.3·annulus-mass …)`, `planet_formation.clj:177`), not accretion-derived, and **planet composition is a static lookup table** (`planet_formation.clj:73`) that ignores where the planet formed.
5. **Differentiation, volatile budget, and the habitability handoff contract** are specified but coded-not-wired or missing.

This roadmap fixes the chain in dependency order and gives every deferred item a trigger, so "later" is never a dead end.

---

## 2. Locked decisions (2026-07-06 design session)

| # | Decision | Consequence |
|---|----------|-------------|
| 1 | **Metallicity default = Population-I floor** (Z=0.0167, Asplund 2009). `:primordial` is an opt-in preset via a `:genesis/metallicity` world-creation knob. | Metals exist from tick 0; the whole downstream chain becomes live. |
| 2 | **Every deferral is capability-gated.** No item is deferred without a named precondition **and** a trigger describing when it becomes active. | See §5 register. |
| 3 | **Migration is deferred behind the radial disk profile** (D2 ⟵ D1). | Seeded planets stay static until Σ(r)/T(r) exists. |
| 4 | **Condensation uses a smooth sigmoid**, `f_solid = 1/(1+exp((T−Tc)/ΔT))`, ΔT≈30 K; categories (rock/ice/metal) derived on demand from elements + T. No molecular network, no pressure scaling yet. | M2 fidelity bounded; D3/D8 gated. |
| 5 | **Planet mass is accretion-derived**: critical core mass + viscous-supply-limited runaway gas accretion. Pebble accretion is deferred. | M3. |
| 6 | **Planetesimal formation (streaming instability) is sub-grid**: a solid-surface-density + formation-timescale gate feeding the seeder; no explicit clumps. | M3; explicit clumping is D9. |
| 7 | **The `:planet-candidate` handoff contract is built now** as Phase 0's canonical output; only the downstream *consumer* is capability-gated. | M5; consumer is D-consumer. |

---

## 3. Epic-level invariants (unchanged from the original epic, restated)

1. **One composition source of truth.** `c/composition` is an explicit element map. `c/comp-condensed`, `:planet-type`, and render bars are all derived.
2. **Planets come only from core accretion.** `:planet` entities are created only by `domain.planet-formation/planet-seeds`. Direct disk fragmentation produces only `:gas-giant` embryos under strict GI conditions.
3. **Plasma is a state, not a matter-state.** Stellar ejecta stays `:nebula` but carries `c/ionization-fraction` near 1.0; EM coupling scales with ionization.
4. **Render verifies physics.** The rich inspector exposes composition, thermal history, disk regime, hierarchy, and events.
5. **Mass, momentum, angular momentum, and element fractions are conserved** across every transformation.

---

## 4. Milestone sequence

Each milestone lists its spec, entry precondition, exit/verification, and the gap findings it closes. Milestones are ordered by dependency; do not start one before its predecessor's exit criteria hold.

### M0 — Composition substrate (LARGELY DONE)
- **Spec:** nebular-chemistry-realspec §3–5.
- **State:** element-resolved `c/composition`, mass-weighted `blend-compositions`, H→He `burn-step`/`nucleosynthesis-system`, deuterium depletion, and `c/comp-condensed` derivation are implemented and folded in the tick (`integrator.clj:485`, `registry.clj:104`, `chemistry.clj:308`). Schemas in `law/composition.clj`.
- **Remaining:** none blocking; this is the foundation M1–M5 build on.

### M1 — Metals exist (ROOT FIX)
- **Spec:** metal-enrichment-and-seeding-realspec.
- **Closes:** chemistry gap #1 (metals never seeded), and unblocks #2/#3.
- **Entry:** M0.
- **Work:** add `:genesis/metallicity` knob (`:population-i` default, `:primordial` opt-in); seed `seed-clump`/`spawn-clump` and every spawn spec (wind, disk fragment, shatter, flare) and every absorb/accrete packet with an explicit `:composition`.
- **Exit:** in a live run every `:nebula` parcel has nonzero O/C/Fe/Si summing to Z≈0.0167; `composition-sums-to-unity` holds after seeding, blending, and accretion.

### M2 — Condensation & consumers
- **Spec:** nebular-chemistry-realspec (revised) §6–8.
- **Closes:** chemistry gaps #2 (comp-condensed has no consumers), #3 (render reads retired keys), #7 (hard-step condensation).
- **Entry:** M1.
- **Work:** sigmoid `partition-solids`; derive `bulk-categories` on demand; fix `composition->material-color` to read `bulk-categories`; make planet seeding consume `c/comp-condensed`.
- **Exit:** `partition-solids-water-ice-at-snow-line` matches Lodders; a live run renders bodies by material class (rock/ice/metal/gas), not uniform tan.

### M3 — Core accretion physics

- **Spec:** core-accretion-physics-realspec + protoplanetary-disk-planet-formation-realspec (revised) + gradual-mass-transfer-realspec.
- **Closes:** planet gaps #1 (streaming instability), #2 (runaway gas accretion), #3 (condensation-sequence composition), #5 (seeder ignores regime σ_solid), #7 (Gammie α(β)), and the all-or-nothing mass-transfer pathology identified in the sink-particle epic.
- **Entry:** M2 (needs `c/comp-condensed` for composition & solid budget).
- **Work:** critical-core-mass + viscous-supply-limited runaway gas accretion; sub-grid planetesimal-formation timescale (Stokes + metallicity gate) as the seeder's solid feed; seeded-planet composition from the condensation sequence at formation radius; derive Gammie α from cooling β. Underlying all of this, implement gradual BHL sink accretion (gradual-mass-transfer-realspec) so planets and stars grow by draining gas, not by swallowing parcels whole.
- **Exit:** planet mass emerges from accretion (not a disk fraction); planet composition varies with formation radius relative to snow line; `planet-seeds-are-only-planet-source` and conservation tests still pass.

### M4 — Differentiation & collision chemistry
- **Spec:** phase0-chemistry-differentiation (revised).
- **Closes:** chemistry gaps #4 (differentiation unwired), #6 (collision composition/volatile loss), #7 volatile-budget.
- **Entry:** M2.
- **Work:** wire `differentiation-system` (molten bodies, `malleability > 0.8`); add `c/volatile-budget`; merges lose volatiles by impact temperature; fragments inherit density-biased composition.
- **Exit:** `molten-body-differentiates`, `volatiles-lost-in-hot-collision`, `differentiation-conserves-mass` pass.

### M5 — Habitability handoff contract
- **Spec:** phase0-habitability-handoff (revised).
- **Closes:** chemistry gap #5 (handoff contract missing).
- **Entry:** M3 + M4 (material class, thermal band, volatile budget feed the record).
- **Work:** build the full `:planet-candidate` record + `:phase0-handoff` event; material-class/thermal-band/atmosphere-class/retained-species/orbit-stability.
- **Exit:** `handoff-record-contains-required-keys` passes; a settled solar-analog run emits a candidate record with all keys.

### M6 — Rich inspection verifies it all
- **Spec:** rich-entity-inspection-ui-realspec.
- **Entry:** M1–M5 (there must be real state to show).
- **Exit:** inspector renders composition bars, thermal sparkline, disk regime, hierarchy, and events for a selected body.

**Plasma track (parallel, independent of M1–M6 ordering except M6):** `stellar-wind-plasma-state-realspec` (hot ionized wind, ionization-weighted Lorentz, plasma cooling). It shares only the composition dependency (M1) — wind parcels carry star composition. Slot it after M1, in parallel with M2–M4.

---

## 5. Deferred capability register (every "later" has a trigger)

Deferrals are legitimate only with a **precondition** (what must exist first) and a **trigger** (the observable that says "now"). Anything not in this table is in-scope for M1–M6.

| ID | Capability | Precondition | Trigger (activate when…) | Spec |
|----|-----------|--------------|--------------------------|------|
| **D1** | Radial disk structure Σ(r)/T(r), per-annulus regimes | M2–M3 landed | the single-annulus Toomre Q at fixed `disk-outer-temperature=100 K` misclassifies a disk that a radial profile would resolve — i.e. a test disk that should fragment in its outer annulus but not globally | radial-disk-structure-realspec (drafted, status: deferred) |
| **D2** | Planet migration (Type I/II torque) | **D1** (torque needs Σ(r), T(r)) | D1 landed **and** ≥1 seeded planet coexists with a non-dissipated disk | new spec at trigger time |
| **D3** | Pressure-dependent condensation Tc(P) | **D1** (needs local pressure) | D1 landed; fold Tc(P) into `partition-solids` | fold into nebular-chemistry-realspec at trigger |
| **D4** | SN enrichment events, metallicity-dependent yields | a star reaching a core-collapse end-state exists in-sim (no stellar death is modelled yet) | `stellar-death`/remnant events are emitted (see stellar-winds spec) — then `supernova-enrichment` fires on them | fold into metal-enrichment-and-seeding-realspec at trigger |
| **D5** | Multi-star / circumbinary disks | a bound stellar binary can persist (GI binary threshold already exists, `stellar.clj:1677`) | a bound binary survives > N ticks sharing disk mass | new spec at trigger |
| **D6** | Pebble accretion branch | **D1** (Stokes number needs local Σ_gas, T) | D1 landed **and** planetesimal-only accretion (M3) fails to grow a gas giant within the disk lifetime in a solar-analog run | fold into core-accretion-physics-realspec at trigger |
| **D7** | Collision bounce/graze outcome | M4 merge/shatter wired | brittle high-speed **grazing** impacts are demonstrably mishandled by the merge-or-shatter binary (measured merger rate for high impact-parameter cold collisions) | fold into phase0-chemistry-differentiation at trigger |
| **D8** | Full C/O molecular network (CO/CO₂/H₂O/CH₄/NH₃) | M2 categories + Phase 1 atmosphere chemistry | atmospheric C/O becomes an observable a consumer reads (Phase 1 exists) | new spec at trigger |
| **D9** | Explicit streaming-instability clumping (particles) | M3 sub-grid timescale wired | the sub-grid timescale cannot reproduce inspectable planetesimal-belt structure the player needs | new spec at trigger |
| **D10** | Radiogenic/isotope heating, interior EoS | M4 differentiation | Phase 1 planetary geology consumes interior state | new spec at trigger (`domain.interior`) |
| **D-consumer** | Phase-1 consumer of `:planet-candidate` | M5 contract emitted | Phase 1 (planetary cooling / prebiotic chemistry) begins | Phase 1 epic |

Helium enrichment (research §2.3) is **not deferred — it is a decision**: the Pop-I floor already carries the enriched helium value, so no separate tracking is needed in Phase 0.

---

## 6. Small-gap task cards (no dedicated spec; tracked in `kanban/tasks/`)

These are discrete fixes that live inside a milestone but are worth their own card:

- **render-material-color-element-keys** — `composition->material-color` reads retired `:metals/:ice/:H2O`; switch to `bulk-categories`. (M2; live bug.)
- **disk-regime-tag-unification** — `disk-regime-map` (`stellar.clj:1568`) and `disc-regime` (`stellar.clj:1544`) emit two different tag vocabularies; neither matches `law/field` closed sets. Unify. (M3.)
- **law-planet-formation-namespace** — create `src/law/planet_formation.clj`; relocate `snow-line-temperature`, `ice-enhancement-factor`, `proto-solar-metal-frac` out of `domain/planet_formation.clj:14` per the constants-in-`law/` convention. (M3.)
- **gammie-alpha-beta-coupling** — derive `disk-viscous-alpha` from cooling β instead of the `^:const` (`stellar.clj:1419`). (M3.)
- **ecology-water-gate-snowline** — `moisture-from-composition` (`ecology.clj:383`) counts raw `:H`, trivially satisfying `has-water`; gate on condensed `:ice` category + temperature. (M4.)

---

## 7. Acceptance criteria (epic-level)

- [ ] `:genesis/metallicity` knob exists; default run has nonzero metals (M1).
- [ ] Bodies render by material class from live composition (M2).
- [ ] `c/comp-condensed` has at least one consumer (planet seeding + render) (M2).
- [ ] Planet mass is accretion-derived; composition varies with formation radius (M3).
- [ ] Disk fragmentation produces only `:gas-giant` embryos, only under Q<1 ∧ β<3 (existing, keep green).
- [ ] Molten bodies differentiate; hot collisions lose volatiles (M4).
- [ ] `:phase0-handoff` emits a full `:planet-candidate` record (M5).
- [ ] Rich inspector renders composition, disk regime, hierarchy, events (M6).
- [ ] Every deferred item in §5 has a live trigger condition (this doc).
- [ ] `clojure -M:test` green; `bin/analyze --strict` clean after each milestone.

---

## 8. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Composition maps bloat ECS memory | Plain maps, 17 elements; categories/molecules derived on demand, never stored. |
| Deriving `c/comp-condensed` every tick is costly | Derive only for resolved bodies; it is already folded once per tick — measure before caching (nebular-chemistry-realspec open Q, now resolved: on-demand). |
| Accretion-derived mass destabilises seeding tests | Implement M3 behind the existing one-shot seeder gate; keep conservation tests as the contract. |
| Restricting GI fragments reduces planet diversity | Sub-grid seeder still produces terrestrial/ice/gas-giant from disk solids (M3). |
| A deferral's trigger never fires (dead capability) | Each trigger in §5 is an **observable in a standard run or test**, not a calendar date; if a trigger cannot be observed, the capability is genuinely unneeded. |

---

## 9. Open questions (epic level)

(none — the round-1/round-2 design session resolved the spine; per-spec open questions are resolved in each spec's decisions section with capability-gated conditions.)
