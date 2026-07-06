# Metal Enrichment and Seeding Spec

**Status:** ready for implementation
**Milestone:** M1 (root fix) in `docs/specs/epic-phase0-physics-honesty.md`
**Scope:** make metals *exist* in a live simulation — seed clouds at the Population-I floor by default, add a `:genesis/metallicity` world-creation knob, and thread explicit `:composition` through every spawn and accretion path. Defer explicit supernova-enrichment events behind a concrete trigger.
**Depends on:** `docs/research/physics/nebular-chemistry-metal-enrichment.md`, `docs/specs/nebular-chemistry-realspec.md` (element model).

---

## 1. The gap this closes

The composition *substrate* is real and tested (element-resolved `c/composition`, blend, burn, condensation derivation). But the substrate is fed **zero metals**:

- `domain/genesis.clj:70` and `domain/stellar.clj:2320` (`default-composition`) seed every parcel with `lcomp/primordial-composition` — H, He, D, He3, Li7 only.
- `enrich-composition` (`chemistry.clj:81`) and `supernova-enrichment` (`chemistry.clj:287`) exist but are **never called** anywhere in `src/`.
- There is no `:genesis/metallicity` parameter in the tick.

Net effect: O, C, Fe, Si, Mg… are permanently 0.0 in a live run. Condensation, material color, planet composition, and differentiation all key off metals that never appear. **This is the first broken link in the credibility chain; nothing downstream can be honest until it is fixed.**

---

## 2. Decision (locked)

**Default `:genesis/metallicity` is `:population-i`** — clouds start at the solar/Pop-I floor `Z = 0.0167` (Asplund 2009). `:primordial` (BBN, zero-metal) is an opt-in preset for modelling first-generation clouds.

Rationale: a Phase-0 star-forming cloud in the present universe *is* enriched; the metals were made by previous stellar generations that Phase 0 does not simulate. Asserting the Pop-I floor is the honest representation of "an enriched cloud," not a cheat. Building metals up from zero would require modelling supernova death (not yet present) and would leave every default run metal-free.

---

## 3. Invariants

1. Every matter-bearing entity carries a `c/composition` whose mass fractions sum to 1.0.
2. With `:genesis/metallicity :population-i`, the total metallicity of a freshly seeded cloud is `Z = Σ(fractions of elements heavier than He) ≈ 0.0167`.
3. Metals are **never created at world creation beyond the Pop-I floor.** Additional metals appear only via enrichment events (deferred, D4).
4. Composition is conserved through seeding, spawning, accretion, merging, and winds (element fractions mass-weighted).
5. No spawn spec or accretion packet leaves composition implicit; every one carries `:composition`.

---

## 4. The metallicity knob

### 4.1 Parameter

Add `:genesis/metallicity` to the world-creation config, read once at `create-world`:

```clojure
{:genesis/metallicity :population-i}   ; default
;; or
{:genesis/metallicity :primordial}     ; opt-in, first-generation cloud
```

### 4.2 Preset → composition

| Preset | Source fn (`domain.chemistry` / `law.composition`) | Z |
|--------|-----------------------------------------------------|---|
| `:population-i` | `solar-composition` (Asplund 2009 table already in `law/composition.clj`) | 0.0167 |
| `:primordial` | `primordial-composition` (BBN) | ~8e-10 |

The preset resolves to a single "cloud floor" composition map, passed to the seeder. No per-parcel metallicity variation in Phase 0 (a metallicity gradient is not a listed capability; add one only under a future trigger).

### 4.3 Wiring

- `domain.stellar/default-composition` (`stellar.clj:2320`) takes the resolved floor instead of hard-coding primordial.
- `seed-clump` / `spawn-clump` seed the floor composition on every `:nebula` parcel.
- `domain.genesis/create-world` (`genesis.clj:70`) reads `:genesis/metallicity` and threads the resolved floor to the seeder; absent → `:population-i`.
- `domain.intervention` / Spark menu: expose `:genesis/metallicity` as a live world-creation knob (consistent with the dark-halo/`:genesis/*` intent pattern).

---

## 5. Composition on every spawn and packet

Per nebular-chemistry-realspec §6.2, close the "implicit composition" holes:

| Spawn / packet | File (current) | Composition it must carry |
|----------------|----------------|---------------------------|
| Wind parcel | `stellar.clj` stellar-wind-system | `wind-composition(star-composition)` (photospheric; fractionation is D4) |
| Disk GI fragment | `stellar.clj:1717` | star envelope composition |
| Shatter fragments | `shatter-bodies` `stellar.clj:2215` | parent composition, density-biased in M4 |
| Flare / ejecta | `stellar.clj` | star surface composition |
| Absorb/accrete packet | `stellar.clj:1617`, `integrator.clj:181` | source-parcel composition so the integrator blends correctly |

The integrator's `absorb-comp-blend` (`integrator.clj:181`) already does mass-weighted blending; it just needs every packet to actually carry a composition.

---

## 6. Enrichment (deferred — D4, but the mechanism spec lives here)

`enrich-composition` and `supernova-enrichment` (`chemistry.clj:81,287`) stay in the codebase as **tested pure functions with no live caller** until D4's trigger fires. Do not delete them; they are the enrichment mechanism, waiting on an enrichment *source*.

**D4 trigger:** a star reaches a core-collapse / supernova end-state in-sim. Phase 0 does not yet model stellar death (see `phase0-stellar-winds-and-mass-loss.md`); when `stellar-death`/remnant events are emitted, `supernova-enrichment` fires on the neighborhood using the yield vector from research §7.3:

```clojure
;; mass-fraction of ejected metals, per element (research §7.3)
{:O 0.36 :C 0.10 :Ne 0.06 :Mg 0.12 :Si 0.15 :S 0.05 :Ca 0.01 :Fe 0.18 :Ni 0.02}
```

**Resolved open questions (from research §9):**
- **Q3 metallicity-dependent yields** (α-enhanced Pop III/II): deferred with D4 — a single solar-calibrated yield vector suffices until progenitor metallicity is tracked.
- **Q5 wind fractionation** (metal-depleted winds): deferred — winds carry photospheric composition now; fractionation folds in when spectroscopic realism has a consumer (rich inspector diagnostics).
- **Q4 dust-to-gas ratio** (3% condensed vs 1% observed): handled in nebular-chemistry-realspec (condensation), not here; the coagulation/settling correction is gated on D1 (radial disk).

---

## 7. Tests

1. `default-metallicity-is-population-i` — a world created with no `:genesis/metallicity` seeds parcels with Z≈0.0167 and nonzero :O/:C/:Fe/:Si.
2. `primordial-preset-has-no-metals` — `:primordial` seeds Z<1e-6.
3. `seeded-composition-sums-to-unity` — for both presets.
4. `every-spawn-carries-composition` — sweep spawn specs; none produces an entity/packet with nil composition.
5. `accretion-blend-preserves-metallicity` — accreting a metal-rich packet onto a metal-poor sink raises the sink's Z by the mass-weighted amount.
6. `enrich-composition-conserves-mass` — (already testable) enrichment adds metals without changing total mass. Guards the D4 mechanism.

---

## 8. Promotion path

| File | Change |
|------|--------|
| `src/domain/genesis.clj` | Read `:genesis/metallicity`; thread resolved floor to seeder (was `genesis.clj:70`). |
| `src/domain/stellar.clj` | `default-composition` takes the floor; all spawn specs + packets carry `:composition`. |
| `src/law/composition.clj` | Add `metallicity-preset->composition`; SN yield vector const. |
| `src/domain/intervention.clj` | Expose `:genesis/metallicity` knob (Spark menu). |
| `test/domain/chemistry_test.clj` | Seeding + enrichment tests. |
| `test/domain/genesis_test.clj` | Default-metallicity + spawn-composition tests. |

---

## 9. Decisions

1. **Pop-I floor is the default** (§2). No per-parcel metallicity gradient in Phase 0.
2. **Enrichment functions stay live-uncalled** until D4 (§6); they are mechanism, not dead code — annotate them so the "unwired features" memory stays accurate.
3. **Winds carry photospheric composition**; fractionation deferred (§6).

## 10. Open questions

(none — research Q3/Q4/Q5 resolved above with capability gates.)
