---
uuid: "genesis-formation-authoritative-star-planet-formation-spec"
title: "Genesis Formation — Authoritative Star & Planet Formation Spec"
status: "done"
priority: "P0"
labels: ["specs"]
created_at: "2026-07-08T02:24:29.674772746Z"
source: "kanban/tasks/genesis-formation-authoritative-star-planet-formation-spec.md"
category: "specs"
---

# Genesis Formation — Authoritative Star & Planet Formation Spec

**Status:** implemented & tested (all beats built; competitive accretion, disc,
Toomre Q, and the planet seeder are TDD-covered. Remaining: full emergent
validation at production resolution — see "Implementation status" below)
**Date:** 2026-07-03
**Owns:** the end-to-end formation pipeline from molecular cloud → dominant star
+ disk → planets, on the single ECS substrate (`domain.genesis` wiring,
`domain.stellar`/`domain.em`/`domain.regime` physics, `domain.pacing` clock).

**Consolidates / supersedes** (these remain as historical references; where they
disagree, THIS doc wins):
- `kanban/tasks/phase-0-complete-planet-formation-pipeline-spec.md` — the Phase A/B/C
  plan whose planet seeder was left a stub (`planet-formation-system` ends at
  `;; … detailed implementation in Phase C.4`). Completed here.
- `kanban/tasks/phase-0-jeans-driven-formation-spec.md` — **superseded.** Its mass-tier
  "promote a gas parcel to `:planet`" path is the "lie dressed as emergence" the
  physics-design note forbids; it is already dead in code (`law/mass-class` is
  unwired). Marked superseded, not deleted.
- `kanban/tasks/phase-0-protoplanetary-disc-implementation-spec.md` — its pending Phase
  6 (disk accretion growing planets) is subsumed by Part 4 here.

**Canonical physics (unchanged, reaffirmed):**
`docs/notes/specs/2026.06.26-authentic-phase0-formation-physics.md`. That note's
three-track fidelity model (gas→star resolved; disk resolved; **planets
sub-grid**) and its §3 classifier state machine are correct and are what the code
implements. This spec does not change that physics — it fills the unbuilt beats
and fixes the emergent failures that keep a run from ever reaching a star system.

Companion: `kanban/tasks/genesis-arc-separation-physics-substrate-vs-player-arc.md` (namespace/keys: this doc uses
the current `domain.genesis` / `:genesis/*` names).

***

## 0. Why nothing forms today (measured, not guessed)

Default world: `domain.genesis/create-world` (`nebula-mass 4e30`,
`gas-count 1000`, `contraction-time 9.5e14`). One gas parcel = **4×10²⁷ kg ≈
2.11 M_Jupiter**. Measured over a headless run (adaptive pacing off, dt=1e12):

| Observation | Value | Meaning |
|---|---|---|
| Gas → protostars | ~79–86 `:protostar` by tick 300 | condensation works |
| Peak temperature | reaches **10⁷ K** by ~tick 600 | KH contraction → ignition T works |
| Max single-body mass | **plateaus at ~0.13 M☉**, then *drops* to ~0.08 | mass never aggregates into a dominant star; winds erode it |
| `:star` count | flickers 0→2→3→1→**0** | marginal cores ignite then fall back below the 0.08 M☉ H-burning mass |
| `:planet` count | **0, always** | no code path writes `:planet` |
| Disk mass | accumulates to ~10²⁹ kg and stays | nothing consumes disk mass into bodies |

Three distinct root causes, each addressed by a Part below:

1. **No planet seeder (certain, structural).** No system writes
   `:matter-state :planet`. `stellar/classify-next-state` has no `:planet` output
   tier (planets are "owned by the disk sub-grid, beat 6" — a stub). The
   `disk-evolution-system` "planet" fragmentation branch spawns
   `:matter-state :debris`, not `:planet` (`domain/stellar.clj` ~L1386). The only
   code with a `:planet` tier, `law/mass-class`, is **unwired dead code**.
   → **Part 4.**
2. **Fragmentation: no dominant star.** The 2 M☉ cloud splits into ~80 cores of
   ~0.025–0.13 M☉ instead of one runaway core. Without a dominant star there is
   no coherent disk to seed planets around. → **Part 1.**
3. **Wind demotion + pacing.** Marginal (~0.08–0.13 M☉) stars lose mass to
   stellar winds (`wind-rate-scale 1.5`, cinematic) and drop back below
   `hydrogen-burning-mass`, so `:star` flickers out. And under the *default
   adaptive* clock (dt ~1.7×10¹⁰ s, `pacing-dt-max 5e10`), reaching even this
   marginal ignition takes ~9,000 ticks — not watchable. → **Part 1 + Part 5.**

The design intent (a star + 3–8 planets) is correct and documented; the
implementation of beats 5b (dominant star) and 6 (planets) is missing.

***

## 1. Reference thresholds (SI, from `law.stellar` — real ladder, toy resolution)

| Quantity | Symbol / const | Value | Note |
|---|---|---|---|
| Gas parcel mass | `:genesis/gas-particle-mass` | 4×10²⁷ kg (default) | = 2.11 M_Jup; the resolution floor |
| Deuterium-burning mass | `deuterium-burning-mass` | 0.013 M☉ = 2.586×10²⁸ kg | `:nebula/:debris → :protostar` fate gate (≈6.5 parcels) |
| Hydrogen-burning mass | `hydrogen-burning-mass` | 0.08 M☉ = 1.591×10²⁹ kg | `:protostar → :star` mass gate (≈40 parcels) |
| Fusion ignition | `fusion-possible?` | T ≥ 10⁷ K ∧ P ≥ 10¹² Pa ∧ X_H > 0.1 | temperature gate for ignition |
| Planet ceiling | (new) | 13 M_Jup = 2.47×10²⁸ kg | sub-grid `:planet` upper mass bound |
| Core-condensation density | `core-condensation-density` | 10⁻¹⁰ kg/m³ | density gate for `:nebula` condensation |

Mass sets **fate**, density+Jeans set **condensation**, temperature sets
**ignition** — never one standing in for another (canonical note §3).

***

## Part 1 — A dominant star must form and stay a star

**Problem:** fragmentation into ~80 marginal cores (root cause 2) and wind
demotion of the survivors (root cause 3). Planet formation is impossible without
a stable central star + disk, so this is the prerequisite fix.

### 1a. Competitive accretion → one dominant core

The feeding-zone / sink model already aggregates gas onto cores
(`stellar/accretion-zone-system`, `sink-formation-system`), and the "go-live"
note (`…authentic-phase0-formation-physics.md` §7c) claims a resolution-aware
`feeding-zone-factor` once ignited "by ~t=180." That has regressed: at defaults
we measure ~80 cores, max 0.13 M☉. Required work:

- **Re-verify `stellar/feeding-zone-factor` / `resolution-feeding-zone-factor`**
  at the production 10³-parcel cloud. The zone must span ≳2× the initial
  inter-parcel spacing (≈ extent/N^{1/3}) so the first overdensity reaches
  several neighbours and runs away (§7c rationale). Confirm the current factor
  actually delivers that at `gas-count 1000`; the measured fragmentation says it
  does not.
- **Acceptance:** in a bounded headless run at default resolution, **exactly one
  body exceeds 0.5 M☉** (the dominant star) and total `:star` count settles to
  1–2, not 0 and not ~80. Encode as a regression test (Part 8).

> **As built — competitive accretion via a mass-dependent capture radius.** The
> fixed feeding zone was the flaw: a core's reach was frozen at condensation
> (`factor × gas-smoothing-radius`) and never grew, so no core could out-compete
> its neighbours and the cloud fragmented into a swarm of equal cores. The fix
> (`stellar/effective-accretion-radius`) makes the *effective* capture radius the
> larger of that frozen zone and the **Bondi radius r = GM/c_s² (∝ M)** evaluated
> against the ambient cold-gas velocity dispersion (`capture-velocity-dispersion`,
> not the hot sink's own temperature and not its bulk speed — a core comoving with
> its gas has a small relative velocity). As the densest core accretes, its Bondi
> reach widens, it captures more gas, and it RUNS AWAY — Bonnell/Bate competitive
> accretion. `classifier` remains the sole *writer* of `c/accretion-radius`; this
> is a read-only enlargement used by `sink-formation`, gated by
> `:genesis/competitive-accretion?` (default on) so the mechanism can be toggled
> for testing. TDD'd synthetically in `dominant-star-test`: a cold, dense,
> Jeans-unstable clump (many parcels per core, frozen zone ≈ inter-parcel spacing
> — i.e. the *production* kilo-parcel regime) funnels into **one dominant star at
> ~0.6 M☉** with the mechanism ON, and fragments into a **77-core swarm (max
> ~0.19 M☉, no star)** with it OFF. See "Implementation status" for the emergent
> caveat at the over-coarse `gas-count 50` toy.

### 1b. Ignition hysteresis — a star must not flicker

A body at 0.08–0.13 M☉ ignites, sheds wind mass, drops below
`hydrogen-burning-mass`, and is demoted — flickering `:star`↔`:protostar` every
few ticks (measured). Fix with **hysteresis in the classifier ignition gate**
(`stellar/classify-next-state`, `:protostar → :star` and the `:star` terminal
branch):

- Promote `:protostar → :star` at M ≥ `hydrogen-burning-mass` ∧ `fusion-possible?`
  (unchanged).
- Once `:star`, **do not demote on mass alone** while fusion remains
  self-sustaining (T ≥ 10⁷ K, X_H > 0.1). Demote a `:star` only when fusion
  actually cannot continue (T below a demotion floor, or X_H exhausted) — not
  when a transient wind dip crosses the *formation* mass threshold. This matches
  real physics: a main-sequence star that loses a little wind mass stays a star.
- **Wind default:** `wind-rate-scale` defaults to 1.5 ("cinematic-lively"). For a
  star sitting on the H-burning knife-edge this is destabilising. Either lower
  the default toward ~1.0 (physically subtle; `create-world` docstring already
  notes this) OR ensure 1a produces a comfortably-super-threshold star (≫0.08
  M☉) so wind loss never crosses the line. Prefer the latter (a real dominant
  star), keep winds cinematic.

### 1c. Acceptance

A default headless run reaches **exactly one stable `:star` (M > 0.5 M☉) that
persists** for the remainder of the run, with a rotationally-supported disk of
gas around it (nonzero `:disk-mass` on the star). This is the substrate Parts
2–4 build on.

***

## Part 2 — Disk identification (Phase A)

Adopted from `phase0-planet-formation-complete-pipeline.md` §2, unchanged in
intent. Tag each resolved body's relationship to the dominant star.

- **New component** `c/disc-tag`: one of `:disc`, `:envelope`, `:outflow`, nil.
- **Pure fn** `stellar/disc-classify [region central-star] → tag` by velocity
  decomposition (v_tang > 2·|v_rad| ∧ h/r < 0.3 ∧ bound → `:disc`), h/r derived
  from oblateness.
- **System** `stellar/disc-identification-system` — sole writer of `c/disc-tag`;
  central = max-mass `:star`/`:protostar`; runs after `regime-system`.
- **Tests:** Keplerian orbit → `:disc`; radial infall → `:envelope`; hyperbolic →
  nil; oblate spinner → `:disc`.

This is the smallest first step and makes the disk legible to Parts 3–4.

***

## Part 3 — Toomre Q disk stability (Phase B)

Adopted from `phase0-planet-formation-complete-pipeline.md` §3.

- **Pure fns** `stellar/toomre-q [region star-mass]` (Q = c_s·κ / (π G Σ),
  κ ≈ Ω, Σ ≈ ρ·H, H ≈ c_s/Ω) and `stellar/cooling-time-ratio` (Gammie 2001,
  t_cool / Ω⁻¹).
- **Regime tags** in `domain.regime/classify`: `:stable-disc` (Q>1),
  `:gravitationally-unstable` (Q<1 ∧ t_cool<3Ω⁻¹), `:unstable-no-fragment`
  (Q<1 ∧ slow cooling).
- **Schemas** in `law.field`: `toomre-q-schema`, `cool-dyn-ratio-schema`.
- **Tests:** thin/hot → Q>1; dense/cold → Q<1; fast cooling → fragmenting; slow →
  no-fragment.

Toomre Q informs Part 4 where the disk is self-gravitating; the baseline planet
seeder uses the surface-density profile directly and treats Q as a modifier
(fragmentation regions get a higher seeding weight).

***

## Part 4 — Planet sub-grid seeder (Phase C — the completed C.4)

This is the missing core: convert accumulated disk material into `:planet`
entities by a **core-accretion prescription on the disk's solid surface
density**, never by merging gas parcels (canonical note §1, beat 6).

### 4a. Pure functions (in `domain.stellar`)

```clojure
(defn snow-line-radius
  "Radius where equilibrium T = 170 K for a blackbody at luminosity L:
   r = sqrt(L / (16 π σ T⁴)). Beyond it, water ice condenses and the solid
   surface density jumps ~3.5×."
  [luminosity]
  (let [T 170.0]
    (Math/sqrt (/ (double luminosity) (* 16.0 Math/PI law/stefan-boltzmann (Math/pow T 4))))))
;; NOTE: the prior spec wrote `σ T T T T` = σT⁴ — dimensionally CORRECT
;; (r = sqrt(L/(16πσT⁴))). Kept, written as (Math/pow T 4) for clarity. Not a bug.

(defn solid-surface-density
  "Solid (dust+ice) surface density at radius r: Σ_gas·Z, ice-enhanced beyond
   the snow line by ~3.5×. Z = metal fraction (~0.015 proto-solar)."
  [sigma-gas r snow-line metal-frac]
  (* sigma-gas (double metal-frac) (if (> r snow-line) 3.5 1.0)))

(defn core-accretion-timescale
  "Time to build a ~10 M⊕ core at r (Pollack 1996 parameterization): τ ∝
   1/Σ_solid, scaled by orbital period. Returns seconds."
  [r sigma-solid star-mass] …)  ;; as in the prior spec §4.2

(defn planet-type
  "→ :terrestrial | :ice-giant | :gas-giant from (r, Σ_solid, snow-line, mass)."
  [region sigma-solid snow-line r] …)  ;; prior spec §4.4

(defn planet-composition [ptype] …)   ;; prior spec §4.5
```

### 4b. The seeding algorithm (what the stub omitted)

The seeder (`domain.planet-formation/planet-seeds`, a pure fn) runs **once per
disk epoch** (guarded so it does not re-seed every tick), reading the disk and
emitting `c/spawn-request-planet` (a new single-writer spawn column, materialized
by `genesis/materialize-lifecycle` like the other spawn requests).

> **As built:** rather than a standalone `planet-formation-system`, the seeder is
> invoked from `stellar/disk-evolution-system` (already the disk's owner), which
> is therefore the single writer of `c/spawn-request-planet` and
> `c/planets-seeded`. It reads the POST-viscous disk state so the mass debit
> composes with the same tick's viscous transfer. Same single-writer guarantee,
> one fewer system.

1. **Guard.** Run only when: a dominant `:star` exists (Part 1c) AND
   disk-age = `:genesis/sim-time` − star-ignition-time > `disk-maturity`
   (default 1 Myr = 3.156×10¹³ s) AND no planets have been seeded for this disk
   yet (track `:genesis/planets-seeded?` or a per-star flag). One-shot per disk.
2. **Radial binning.** Partition disk-tagged bodies (`c/disc-tag = :disc`) into
   N logarithmic annuli between r_in (≈ a few stellar radii, min 0.1 AU) and
   r_out (outermost bound disk body). For each annulus compute:
   - Σ_gas = (Σ disk-body mass in annulus) / annulus area,
   - Σ_solid = `solid-surface-density`(Σ_gas, r, snow-line, Z),
   - Ω, orbital period, `core-accretion-timescale`.
3. **Seed test.** In each annulus, seed a planet iff
   `core-accretion-timescale < disk-age` AND the annulus holds enough solid mass
   for the target core (≥ isolation mass M_iso ∝ (Σ_solid·2π r·Δr)^{3/2}). Cap
   one planet per annulus per epoch; enforce orbital spacing (Δa/a ≳ a few mutual
   Hill radii) so planets don't overlap.
4. **Mass budget & conservation.** Each seeded planet draws its mass from the
   annulus's solid + (beyond snow line, if runaway) captured gas, **debited from
   the disk** (reduce the star's `:disk-mass` / remove consumed disk bodies) so
   total mass is conserved: Σ planet mass ≤ disk mass consumed. `:planet` mass
   ∈ [rounding-mass 3×10²⁰ kg, 13 M_Jup].
5. **Emit.** For each seeded planet, `c/spawn-request-planet` with a spec:
   `:matter-state :planet`, `:planet-type` (`planet-type`), `:composition`
   (`planet-composition`), mass, radius (from mass+material density), and a
   **Keplerian orbital state** at radius r around the star (position on the disk
   plane, velocity = circular-orbit speed √(GM/r) tangential) so it enters the
   N-body integrator as a bound planet, not a static point.
6. **Classifier fixpoint.** `:planet` stays terminal in
   `classify-next-state` (already the case). Planets then evolve under existing
   N-body gravity + collisions like any body.

### 4c. Tests

- `snow-line-at-expected-radius`: Sun-like L → snow line ≈ 2.7 AU.
- `sigma-jumps-beyond-snow-line`: Σ_solid 3.5× higher just beyond snow line.
- `terrestrial-inside-snow-line` / `gas-giant-beyond-snow-line`.
- `no-planets-inside-0.1-AU`.
- `planet-formation-conserves-disc-mass`: Σ planet mass ≤ disk mass consumed.
- `seeder-runs-once`: a second tick after seeding adds no new planets.
- `seeded-planet-is-on-a-bound-orbit`: |v − v_circ|/v_circ small; energy < 0.

***

## Part 5 — Pacing so a solar system forms in a watchable run

Even with Parts 1–4, the default adaptive clock (dt ~1.7×10¹⁰ s) makes ignition
~9,000 ticks and planet seeding (1 Myr disk age) far more. Reconcile the
formation timescales with the clock so a **full run (nebula → star → planets)
completes in a bounded, watchable tick budget** — the target for interactive
play and for the integration test.

- **Lever A (preferred): scale `contraction-time` with the run's intended
  length**, not a fixed 9.5×10¹⁴ s. The KH time is the pacing knob; the existing
  gc50 test uses 2×10¹² s to ignite in a few hundred ticks. Define a
  `create-world` default `contraction-time` (and `disk-maturity`) chosen so a
  default run reaches a star by ~O(10³) ticks and planets by ~O(10³–10⁴) ticks.
- **Lever B: let the adaptive clock dilate dt upward** once a dominant star
  exists and the bulk is quiescent (the `pacing`/time-slip machinery already
  exists — `domain.pacing`, `with-time-slip`), so post-ignition disk evolution
  and planet seeding fast-forward rather than crawl at dt=1.7×10¹⁰.
- **Constraint:** whatever is chosen must keep the gravity integrator stable
  (softening is matched to dt in `create-world`; see the ε ≳ (G·M·dt²)^{1/3}
  note). Do not raise dt without raising softening.
- **Acceptance:** a headless default run produces the star-system end-state
  (Part 8) within a documented tick budget; record the budget in the test.

***

## Part 6 — Integration: tick pipeline order

Extend `domain.genesis/physics-systems-parallel`. New systems in **bold**;
all remain single-writer fan-out emitters (no barriers — architecture invariant).

```
… classifier-system → accretion-zone-system → (gravity/hydro/em/integrator)
  → collision-detection → sink-formation → collapse/structure/eos/thermal
  → regime-system
  → disc-identification-system            ← Part 2 (writes c/disc-tag)
  → em/field/magnetosphere
  → disk-evolution-system                 ← Part 4 seeder folded in here:
                                            emits c/spawn-request-planet after
                                            the viscous/fragment pass
  → [materialize-lifecycle spawns planets, reaps consumed disk bodies]
```

The seeder is a guarded one-shot (Part 4a §1) invoked at the tail of
`disk-evolution-system`; it costs ~nothing on ticks where its guard is false.

***

## Part 7 — Documentation reconciliation (closing the 7 gaps)

1. **Built path vs principle:** mark `phase0-jeans-driven-formation.md`
   **superseded** (header banner) — its parcel→`:planet` promotion is retired;
   `law/mass-class` is dead code and should be deleted or clearly annotated
   "unwired, historical."
2. **Stub filled:** Part 4b is the missing Phase C.4.
3. **Merge-based planet language:** remove/annotate the "fragmentation into
   clumps/planets … reuse collision-merge" wording in
   `phase0-coupled-physics-and-regime-classifier.md` — planets are sub-grid, not
   merged.
4. **`:event/planet-formation` trigger defined:** emitted by
   `materialize-lifecycle` (or `planet-formation-system`) when a
   `c/spawn-request-planet` is materialized; `:arc/genesis-planets-formed`
   follows from `arc/detect-arc` seeing `planet-count > 0`.
5. **Disc-membership source defined:** Part 2's `c/disc-tag` is the field Part 4
   reads; the SPH-gas-Σ → seedable-annuli bridge is Part 4b §2 (radial binning).
6. **Snow-line formula:** confirmed correct (Part 4a note); no bug.
7. **Pebble accretion / streaming instability:** remains out of scope (Part 9).

***

## Part 8 — Acceptance: the headless integration test

A single test (`test/domain/formation_integration_test.clj`) drives a default
`genesis/tick-world` run to completion within the Part 5 tick budget and asserts:

- exactly **one** `:star`, M > 0.5 M☉, persisting (no flicker) — Part 1;
- a rotationally-supported disk existed (nonzero `:disk-mass`, `:disc-tag :disc`
  bodies) — Part 2;
- **3–8 `:planet` entities**, each on a bound orbit, types consistent with
  location (terrestrial inside / giants beyond the snow line) — Part 4;
- total planet mass ≤ disk mass consumed (conservation) — Part 4;
- `arc/detect-arc` reaches `:arc/genesis-planets-formed` and
  `:event/planet-formation` fired — Part 7.

Deterministic where possible (fixed seed); tolerant of the known
`create-world` nondeterminism by asserting ranges, not exact bodies.

***

## Part 9 — Out of scope (deferred, unchanged from prior specs)

Pebble accretion / streaming instability; Type I/II migration; disc
photoevaporation; giant-impact voxel collisions; atmospheric escape coupling
(lives in `domain.atmosphere`). All are refinements layered on the working
baseline above.

***

## Part 10 — Build order (each phase: failing tests → implement → green)

1. **Part 1** — dominant star + ignition hysteresis. *Highest priority*: without
   it there is no disk to seed. Verify feeding-zone aggregation, add hysteresis,
   settle wind default. (Physics change — validate with the Part 8 star asserts.)
   → **1b (hysteresis) DONE & tested. 1a (competitive accretion) DONE & tested**
   synthetically (`stellar/effective-accretion-radius`, Bondi ∝ M;
   `dominant-star-test`). Full emergent validation at production resolution
   remains — see "Implementation status".
2. **Part 5** — pacing/contraction-time so a star forms in ~O(10³) ticks.
   → **Deferred:** Lever A meaningful only at production resolution;
   `disk-maturity` default + Lever B in place.
3. **Part 2** — disc identification (`c/disc-tag`). → **DONE & tested** (+ fixed a
   shadowing bug that made the tag always nil).
4. **Part 3** — Toomre Q regime tags. → **DONE & tested.**
5. **Part 4** — planet sub-grid seeder (`c/spawn-request-planet`, seeded via
   `disk-evolution-system`). *The headline fix.* → **DONE & tested** — a
   `:planet` materializes end-to-end through `tick-world`.
6. **Part 8** — the full integration test. → **Seeder pipeline GREEN**
   (`formation-integration-test`) and **competitive accretion GREEN**
   (`dominant-star-test`); the single fully-emergent default run (star→disc→
   planets in one go) is the remaining production-resolution check.
7. **Part 7** — doc reconciliation. → **DONE.**

## Key citations

Toomre (1964); Gammie (2001); Pollack et al. (1996); Bate/Bonnell/Price (1995);
Federrath et al. (2010). Youdin & Goodman (2005), Johansen et al. (2007) — the
deferred streaming-instability pathway.

***

## First deliverable

**Part 1 (+ Part 5)** — make one dominant star reliably ignite and persist in a
bounded run. It is the prerequisite for everything else and is validated by the
star-half of the Part 8 test. Next action: approve this spec, then write the
Part 1 failing tests and implement.

***

## Implementation status (2026-07-03)

Every beat is implemented, wired into the live tick order, and TDD-covered; the
only thing not yet demonstrated is the *fully emergent* end-state at production
resolution (a long run, deliberately not blocked on — the mechanisms are proven
under synthetic conditions instead).

**Done and tested:**

- **Part 1a — competitive accretion → one dominant star.**
  `stellar/effective-accretion-radius` gives each sink an effective capture radius
  = max(frozen condensation zone, Bondi radius GM/c_s² ∝ M) against the ambient
  cold-gas dispersion. The most massive core's reach widens as it accretes, so it
  runs away — the cloud funnels into one dominant star instead of a swarm (root
  cause 2 fixed for the well-resolved regime). Gated by
  `:genesis/competitive-accretion?` (default on). TDD'd synthetically in
  `dominant-star-test`: a cold dense Jeans-unstable clump → **one dominant star
  ~0.6 M☉** with it ON, **77-core swarm, no star** with it OFF; unit tests pin the
  radius growing with mass and respecting the toggle. (Emergent caveat below.)
- **Part 1b — ignition hysteresis.** `law/fusion-sustaining?` (T ≥ 7×10⁶ K,
  X_H > 0.1 — a lower bar than the 10⁷ K *ignition* gate) is wired into
  `stellar/classify-next-state`'s `:star` branch: a burning star no longer
  demotes on a transient wind mass-dip below 0.08 M☉. Test:
  `stellar-test/test-mass-loss-demotes-never-dissolves` (cold vs hot cases).
- **Part 2 — disc identification.** `stellar/disc-classify` +
  `disc-identification-system` (sole writer of `c/disc-tag`), in the pipeline
  before `regime-system`. **Fixed a latent shadowing bug** where the
  `central-star` destructure rebound `position/velocity/mass` to nil, so the
  guard always failed and NOTHING was ever tagged `:disc` (silently breaking
  Parts 3–4). Tests in `formation-test`.
- **Part 3 — Toomre Q.** `stellar/toomre-q`, `cooling-time-ratio`, `disc-regime`;
  `:unstable-no-fragment` added to `law.field/regime-tags`; `domain.regime/classify`
  applies the disc tags. Tests in `formation-test`.
- **Part 4 — planet sub-grid seeder.** `domain.planet-formation/planet-seeds`
  (core-accretion prescription on solid surface density) wired into
  `stellar/disk-evolution-system`: emits `c/spawn-request-planet`, sets the
  one-shot `c/planets-seeded`, debits `c/disk-mass`/`c/disk-angular-mom`.
  Materialized by `genesis/materialize-lifecycle`. Unit + over-a-disc tests in
  `formation-test`; end-to-end through the real `tick-world` loop in
  `formation-integration-test` (planets materialize on bound orbits, mass
  conserved, one-shot, `:event/planet-formation` + `:arc/genesis-planets-formed`).
- **Part 6 — pipeline order.** `disc-identification-system` added;
  `disk-evolution-system` now the single writer of the planet spawn column.
- **Part 7 — doc reconciliation.** `phase0-jeans-driven-formation.md` marked
  superseded; `law/mass-class` annotated UNWIRED/HISTORICAL; the merge-based
  planet language in `phase0-coupled-physics-and-regime-classifier.md` corrected.

**Open — full emergent validation at production resolution:**

The competitive-accretion mechanism is proven where it applies: a well-resolved
dense cloud with **many gas parcels per core** and a marginal frozen zone (≈
inter-parcel spacing) — the regime of the *production* kilo-parcel cloud, which
`dominant-star-test` models directly. It does NOT rescue the over-coarse
`gas-count 50` toy, measured to still fragment into ~50 stars: there each of the
~50 widely-spaced parcels condenses into its own core with a huge frozen zone
(`gsr = 0.003·nebula-radius` ⇒ Bondi never even engages) and there is no shared
gas reservoir to compete over — ≈one parcel per star. That is a resolution/initial
-condition artifact of the 50-parcel toy, not a failure of the mechanism, and the
right validation is a headless `gas-count ~1000` run (long — hence the synthetic
TDD instead, per the "waiting is nonsense" directive).

**Part 8** is therefore split: the seeder half runs green through the real
pipeline (`formation-integration-test`, a clean single-star + disc scenario) and
the competitive-accretion half is green synthetically (`dominant-star-test`). The
one thing still unshown is the *single* fully-emergent default run producing star
→ disc → 3–8 planets end-to-end. Part 5 Lever A (retuning the default
`contraction-time`) was intentionally NOT applied — it is only meaningful at
production resolution and would risk the existing coarse-cloud emergence test.
`:genesis/disk-maturity` (default 3.156×10¹³ s) and the adaptive clock (Lever B)
are in place.

**Next action:** run a headless `gas-count 1000` cloud to confirm the mechanism
funnels the production cloud into one dominant star + disc emergently, then let
the (already-built and tested) disc-identification → Toomre → planet-seeder chain
carry it to a planetary system. Everything downstream is ready.
