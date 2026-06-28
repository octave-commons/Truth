# Phase 0 — Complete Planet Formation Pipeline Spec

**Status:** draft — ready for implementation  
**Date:** 2026-06-27  
**Goal:** Close the three gaps between the current stellar-formation engine and a complete, physically-grounded planet-formation pipeline: **(1) Toomre Q disc classification, (2) disc identification from oblate geometry, (3) core-accretion planet sub-grid on disk surface density.**  
**Principle:** Every new term grounded in published physics. No shortcuts. Single ECS substrate.

---

## 0. What already works (the 70%)

The codebase already implements the full star-formation pipeline from nebula to ignition:

| Component | Status | Key file |
|---|---|---|
| SPH gas parcels with adaptive h | ✅ | `domain.hydro` |
| Barnes–Hut self-gravity | ✅ | `domain.gravity.barnes_hut` |
| Jeans instability + density-gated condensation | ✅ | `domain.stellar` |
| Sink formation (isolation criterion + convert-and-seed) | ✅ | `domain.stellar` |
| KH contraction → virial heating → fusion ignition | ✅ | `domain.stellar` |
| Oblate collapse (spin-up, flattening, flux-freeze) | ✅ | `domain.stellar` |
| Magnetic field (Lorentz, braking, resistive decay) | ✅ | `domain.em` |
| Regime classifier (β, M_A, Mach, Jeans) | ✅ | `domain.regime` |
| Collision detection + merge with conservation | ✅ | `domain.physics.collision` |
| Chemistry (elemental abundance, malleability, differentiation) | ✅ | `domain.chemistry` |
| Adaptive pacing (bulk dynamical time) | ✅ | `domain.pacing` |

What's **missing** is the pathway from "a star has ignited with a disc around it" to "planets exist in that disc." The physics is well-defined; the code needs three new systems.

---

## 1. The honest resolution limit (reminder)

From `docs/notes/2026.06.26-authentic-phase0-formation-physics.md` §1:

> One parcel ≈ M_⊙/250 ≈ ~4 M_Jupiter. Planets are NOT resolvable from the gas. Real planet formation happens 10+ orders of magnitude in mass below a parcel: dust → mm pebbles → km planetesimals → protoplanets → planets.

This means planet formation MUST be a **sub-grid prescription** on the disk's local properties — the same approach real hydro simulations use. We do NOT pretend gas parcels merge into planets. We compute the disk state and seed planets from it.

---

## 2. Phase A — Disc Identification

### 2.1 Physical criterion

A region is a **disc** when it is:
1. **Rotationally supported:** v_φ dominates v_r (tangential velocity > 2× radial)
2. **Geometrically thin:** scale height h/r < 0.3 (oblateness > 0.7)
3. **Bound to a central mass:** gravitationally bound to a protostar/star

### 2.2 New component

```clojure
;; c/disc-tag — :component/disc-tag
;; One of: :disc (rotationally supported, thin), :envelope (infalling), :outflow, nil
```

### 2.3 Pure function

```clojure
(defn disc-classify
  "Classify a body's relationship to the central mass. Returns :disc, :envelope,
   :outflow, or nil (unbound / not near a star)."
  [{:keys [position velocity mass oblateness]} central-star]
  (let [r       (sp/dist position (:position central-star))
        v       velocity
        r-hat   (sp/v- position (:position central-star))
        r-hat   (sp/v* r-hat (/ 1.0 (sp/len r-hat)))
        ;; Decompose velocity into radial and tangential
        v-rad   (sp/dot v r-hat)
        v-tang  (sp/len (sp/v- v (sp/v* r-hat v-rad)))
        ;; Oblateness proxy for h/r
        h/r     (- 1.0 (double (or oblateness 1.0)))
        ;; Bound check: total energy < 0
        KE      (* 0.5 mass (sp/len2 v))
        PE      (- (/ (* law/G (:mass central-star) mass) r))
        bound?  (< (+ KE PE) 0.0)]
    (cond
      (not bound?) nil
      (and (> v-tang (* 2.0 (Math/abs v-rad)))
           (< h/r 0.3))
      :disc
      (and (< v-rad 0.0) (< h/r 0.5))
      :envelope
      (and (> v-rad 0.0) (> v-tang (Math/abs v-rad)))
      :outflow
      :else nil)))
```

### 2.4 ECS system

```clojure
(defn disc-identification-system
  "Tag each resolved body as :disc, :envelope, :outflow, or nil based on its
   velocity decomposition relative to the central star/protostar."
  [world]
  (let [stars   (->> (ecs/entities-with world c/matter-state c/mass c/position)
                     (filterv (fn [eid]
                                (#{:star :protostar} (ecs/get-component world eid c/matter-state)))))
        central (when (seq stars)
                  (apply max-key #(ecs/get-component world % c/mass) stars))]
    (if-not central
      world
      (let [star-region (stellar/entity->region world central)
            eids        (ecs/entities-with world c/matter-state c/position c/velocity c/mass)]
        (reduce (fn [w eid]
                  (let [region (stellar/entity->region w eid)
                        tag    (disc-classify region star-region)]
                    (ecs/put-component w eid c/disc-tag tag)))
                world
                (filterv #(not= % central) eids))))))
```

### 2.5 Tests

- `test-keplerian-orbit-classifies-as-disc`: body in circular orbit → `:disc`
- `test-radial-infall-classifies-as-envelope`: body falling straight in → `:envelope`
- `test-unbound-body-classifies-as-nil`: hyperbolic trajectory → `nil`
- `test-oblate-body-preferentially-disc`: flat, spinning body → `:disc`

### 2.6 Tick position

Insert after `regime-system` and before `collision-detection-system`:
```
... regime-system → disc-identification-system → collision-detection-system ...
```

---

## 3. Phase B — Toomre Q Disc Stability

### 3.1 Physical model

The Toomre parameter measures whether a disc region is gravitationally unstable:

```
Q = c_s · κ / (π · G · Σ)
```

Where:
- `c_s` = sound speed = √(γ k_B T / m_H)
- `κ` = epicyclic frequency ≈ Ω (orbital frequency) for nearly-Keplerian discs
- `Σ` = surface density = ρ · H (where H is the disc scale height)
- `G` = gravitational constant

**Q > 1:** disc is stable against self-gravity (Toomre 1964)
**Q < 1:** disc is unstable → spiral arms, fragmentation

But fragmentation also requires **fast cooling** (Gammie 2001):

```
t_cool ≲ 3 Ω⁻¹
```

If cooling is too slow, unstable regions form spiral arms but don't fragment into bound clumps.

### 3.2 Pure functions

```clojure
(defn toomre-q
  "Toomre Q parameter for a disc cell. Q > 1 → stable; Q < 1 → unstable."
  [{:keys [temperature density radius]} star-mass]
  (let [c-s    (stellar/sound-speed temperature)
        ;; Orbital frequency Ω = √(G M / r³)
        Omega  (if (pos? radius)
                 (Math/sqrt (/ (* law/G star-mass) (* radius radius radius)))
                 0.0)
        ;; Epicyclic frequency κ ≈ Ω for Keplerian disc
        kappa  Omega
        ;; Surface density Σ ≈ ρ · H, where H ≈ c_s / Ω (isothermal scale height)
        H      (if (pos? Omega) (/ c-s Omega) 0.0)
        Sigma  (* density H)]
    (if (and (pos? Sigma) (pos? c-s) (pos? kappa))
      (/ (* c-s kappa) (* Math/PI law/G Sigma))
      Double/POSITIVE_INFINITY)))

(defn cooling-time-ratio
  "Ratio t_cool / Ω⁻¹. Below ~3, fragmentation is possible (Gammie 2001)."
  [{:keys [temperature density radius]} star-mass dt]
  (let [Omega   (if (pos? radius)
                  (Math/sqrt (/ (* law/G star-mass) (* radius radius radius)))
                  0.0)
        ;; Cooling time: energy / luminosity (grey-body)
        energy  (* density (/ 4.0 3.0) Math/PI (Math/pow radius 3)
                   2.5 law/k-B temperature)
        ;; Luminosity: σ T⁴ × surface area × emissivity
        area    (* 4.0 Math/PI radius radius)
        L       (* law/stefan-boltzmann area (Math/pow temperature 4))
        t-cool  (if (pos? L) (/ energy L) Double/POSITIVE_INFINITY)
        Omega-inv (if (pos? Omega) (/ 1.0 Omega) Double/POSITIVE_INFINITY)]
    (if (pos? Omega-inv)
      (/ t-cool Omega-inv)
      Double/POSITIVE_INFINITY)))
```

### 3.3 Regime extension

Add to `domain.regime/classify`:

```clojure
;; New disc-regime tags:
:stable-disc           ;; Q > 1 — smooth, non-fragmenting
:gravitationally-unstable  ;; Q < 1 AND t_cool < 3 Ω⁻¹ — will fragment
:unstable-no-fragment  ;; Q < 1 BUT t_cool > 3 Ω⁻¹ — spiral arms, no clumps
```

### 3.4 Schema additions (`law.field`)

```clojure
(def toomre-q-schema
  "Toomre Q parameter. > 1 is stable; < 1 is unstable."
  (some-fn nil? #(and (number? %) (pos? %))))

(def cool-dyn-ratio-schema
  "Cooling time in units of dynamical time. < 3 enables fragmentation."
  (some-fn nil? #(and (number? %) (pos? %))))
```

### 3.5 Tests

- `test-keplerian-disc-has-high-q`: thin, hot disc → Q > 1
- `test-massive-cold-disc-is-unstable`: dense, cold disc → Q < 1
- `test-fast-cooling-enables-fragmentation`: Q < 1 AND t_cool < 3/Ω → `:gravitationally-unstable`
- `test-slow-cooling-prevents-fragmentation`: Q < 1 BUT t_cool > 3/Ω → `:unstable-no-fragment`

---

## 4. Phase C — Sub-Grid Planet Formation (Core Accretion)

### 4.1 Physical model

Within the disc, planets form by **core accretion** (Pollack et al. 1996):

1. **Dust settles** to the midplane and **coagulates** into planetesimals (km-scale)
2. **Planetesimals** grow by collisional accretion into **protoplanets** (~10 M⊕)
3. Inside the **snow line** (T ≈ 150–170 K): rocky/metal → **terrestrial** planets
4. Beyond the snow line: ice raises solid Σ → cores form fast enough to **runaway-accrete H/He** → **gas giants**
5. Ice giants: cores that grabbed only a little gas before the disc dispersed

The key parameter is the **solid surface density** Σ_solid, which jumps by ~3–4× beyond the snow line.

### 4.2 Sub-grid prescription

We do NOT resolve planetesimals. Instead, we compute **where in the disc** planets are likely to form and **seed them** as `:planet` entities. The prescription:

```clojure
(defn snow-line-radius
  "Radius where T = 170 K around a star of given luminosity. Beyond this,
   water ice condenses and solid surface density jumps ~3-4×."
  [luminosity]
  (let [;; T = (L / (16 π σ r²))^0.25, solve for r at T=170K
        T 170.0]
    (Math/sqrt (/ luminosity (* 16.0 Math/PI law/stefan-boltzmann T T T T)))))

(defn solid-surface-density
  "Solid surface density at radius r from the star. Jumps at the snow line."
  [r snow-line base-sigma]
  (if (> r snow-line)
    (* base-sigma 3.5)  ;; ice enhancement factor
    base-sigma))

(defn core-accretion-timescale
  "Timescale to build a ~10 M⊕ core at radius r, from Pollack et al. (1996)
   parameterization. Depends on Σ_solid and orbital period."
  [r sigma-solid star-mass]
  (let [P-yr   (/ (* 2.0 Math/PI
                     (Math/sqrt (/ (* r r r) (* law/G star-mass))))
                  3.156e7)  ;; orbital period in years
        ;; τ ∝ 1/Σ_solid, normalized to ~1 Myr at 5 AU for Jupiter-like Σ
        tau-yr (* 1.0e6 (Math/pow (/ 5.0e11 r) 0.5) (/ 100.0 (max sigma-solid 1.0)))]
    (* tau-yr 3.156e7)))  ;; convert to seconds
```

### 4.3 Planet seeding system

A new `planet-formation-system` runs **once** when the disc is identified and stable (after a few Myr of disc evolution). It:

1. Identifies disc-tagged regions
2. Computes local Σ_solid and snow-line position
3. Seeds `:planet` entities at radii where core-accretion timescale < disc age
4. Assigns material class (terrestrial / gas-giant / ice-giant) from Σ_solid and temperature

```clojure
(defn planet-formation-system
  "Sub-grid planet formation: seed planets in the disc where core accretion
   conditions are met. Runs once when the disc is stable and old enough."
  [world]
  (let [star-eid  (first (ecs/entities-with world c/matter-state c/luminosity))
        star-mass (ecs/get-component world star-eid c/mass)
        star-lum  (ecs/get-component world star-eid c/luminosity)
        snow-r    (snow-line-radius star-lum)
        disc-eids (->> (ecs/entities-with world c/disc-tag c/position c/mass c/density)
                       (filterv #(= :disc (ecs/get-component world % c/disc-tag))))
        ;; Compute disc surface density profile from disc bodies
        ;; (simplified: sum mass in annuli)
        disc-age  (:phase0/sim-time world)
        ;; Only form planets after disc has existed for > 1 Myr
        can-form? (> disc-age 3.156e13)]  ;; 1 Myr in seconds
    (if-not can-form?
      world
      ;; Seed planets at radii where accretion timescale < disc age
      ;; ... (detailed implementation in Phase C.4)
      )))
```

### 4.4 Planet type classification

```clojure
(defn planet-type
  "Classify a forming planet as :terrestrial, :gas-giant, or :ice-giant
   based on local conditions."
  [{:keys [temperature mass]} sigma-solid snow-line-r r]
  (cond
    ;; Beyond snow line, high solid Σ, massive core → gas giant
    (and (> r snow-line-r)
         (> sigma-solid 50.0)
         (> mass (* 10.0 5.97e24)))  ;; > 10 M⊕
    :gas-giant
    
    ;; Beyond snow line, moderate Σ → ice giant
    (and (> r snow-line-r)
         (> sigma-solid 20.0))
    :ice-giant
    
    ;; Inside snow line → terrestrial
    :else
    :terrestrial))
```

### 4.5 Composition assignment

```clojure
(defn planet-composition
  "Assign bulk composition based on planet type and formation location."
  [ptype snow-line-r r]
  (case ptype
    :terrestrial {:Si 0.30 :O 0.30 :Mg 0.15 :Fe 0.20 :metals 0.05}
    :ice-giant   {:H2O 0.40 :NH3 0.10 :CH4 0.10 :Si 0.15 :Fe 0.15 :metals 0.10}
    :gas-giant   {:H 0.70 :He 0.25 :metals 0.05}))
```

### 4.6 Tests

- `test-snow-line-at-expected-radius`: Sun-like luminosity → snow line at ~2.7 AU
- `test-sigma-jumps-beyond-snow-line`: solid Σ 3.5× higher beyond snow line
- `test-terrestrial-planet-seeded-inside-snow-line`: rocky planet inside snow line
- `test-gas-giant-seeded-beyond-snow-line`: gas giant beyond snow line
- `test-no-planets-form-too-close-to-star`: no planets inside 0.1 AU
- `test-planet-formation-conserves-disc-mass`: total planet mass < disc mass

---

## 5. Integration — The Complete Tick Pipeline

The final pipeline order:

```
1.  density-system           (SPH density from positions)
2.  hydro-system             (pressure-gradient acceleration)
3.  jeans-collapse-system    (promote Jeans-unstable gas → resolved bodies)
4.  classifier-system        (density-gated condensation + mass classification)
5.  accretion-zone-system    (set accretion radius for new sinks)
6.  orbital-system           (gravity + hydro + Lorentz → position/velocity)
7.  collision-detection-system (overlap detection → events)
8.  sink-formation-system    (absorb nearby gas into new sinks)
9.  collapse-system          (KH contraction of protostars)
10. fusion-system            (ignition check)
11. thermal-system           (virial heating + radiative cooling + irradiation)
12. structure-system         (radius/density/oblateness from mass + state)
13. eos-system               (pressure from ρ, T)
14. regime-system            (β, M_A, Mach, Jeans, Toomre Q)
15. disc-identification-system  ← NEW (Phase A)
16. em-system                (Lorentz, braking, flux-freeze, resistive decay)
17. recenter-system          (COM frame)
18. planet-formation-system  ← NEW (Phase C, runs once when disc stable)
```

Note: `disc-identification-system` (Phase A) runs after `regime-system` because it needs the regime tags. `planet-formation-system` (Phase C) runs at the end because it's a one-shot that reads the fully-evolved disc state.

---

## 6. What this produces

When complete, the simulation will:

1. **Collapse a nebula** into a central protostar + rotationally-supported disc (existing)
2. **Identify the disc** from velocity decomposition and oblateness (Phase A)
3. **Classify disc stability** via Toomre Q and Gammie cooling (Phase B)
4. **Seed planets** via core-accretion prescription on disc Σ_solid (Phase C)
5. **Assign planet types** (terrestrial / gas-giant / ice-giant) from location and mass (Phase C)
6. **Continue N-body evolution** of planets under mutual gravity + disc interactions (existing)

The output: a star system with 3–8 planets of physically-grounded types, at physically-grounded radii, with physically-grounded compositions. All from the same ECS substrate, all through the same physics pipeline.

---

## 7. What is explicitly out of scope

- **Pebble accretion / streaming instability** — the modern planetesimal formation pathway (Youdin & Goodman 2005, Johansen et al. 2007). This is a refinement of core accretion, not a replacement. Deferred until the basic pipeline works.
- **Planet migration** — Type I/II migration through the gas disc. Real, but requires disc–planet torque coupling. Deferred.
- **Disc photoevaporation** — stellar radiation dispersing the gas disc on ~10 Myr. Real, but a slow effect. Deferred.
- **Giant impacts** — Moon-forming collisions between protoplanets. Requires the voxel-body geometry (spec §7). Deferred.
- **Atmospheric escape** — thermal/Jeans escape stripping atmospheres. Requires `domain.atmosphere`. Deferred.

---

## 8. Implementation order

### Step 1: Disc identification (Phase A)
- Write `c/disc-tag` component schema in `law.stellar`
- Write failing tests in `test/domain/stellar_test.clj`
- Implement `disc-classify` pure function and `disc-identification-system` in `domain.stellar`
- Wire into `domain.phase0/physics-systems`
- All tests green

### Step 2: Toomre Q (Phase B)
- Write `toomre-q-schema` and `cool-dyn-ratio-schema` in `law.field`
- Write failing tests in `test/domain/regime_test.clj`
- Extend `domain.regime/classify` with disc-regime tags
- All tests green

### Step 3: Planet sub-grid (Phase C)
- Write `snow-line-radius`, `solid-surface-density`, `core-accretion-timescale` in `domain.stellar`
- Write failing tests in `test/domain/stellar_test.clj`
- Implement `planet-formation-system` in `domain.phase0`
- Wire into `domain.phase0/physics-systems`
- All tests green

### Step 4: Integration test
- Run full simulation: `clj -M:dev`
- Verify: star ignites, disc forms, planets seed, system stabilizes
- Verify: body count O(3–8), planet types match location

---

## 9. Key citations

- Toomre (1964) ApJ 139, 1217 — disc stability criterion Q
- Gammie (2001) ApJ 553, 174 — cooling criterion for fragmentation
- Pollack et al. (1996) Icarus 124, 62 — core accretion model
- Youdin & Goodman (2005) ApJ 620, 459 — streaming instability
- Johansen et al. (2007) Nature 448, 1022 — streaming instability → planetesimals
- Bate, Bonnell & Price (1995) MNRAS 277, 362 — sink particles (already implemented)
- Federrath et al. (2010) ApJ 713, 269 — sink formation criteria (already implemented)

---

## 10. First deliverable

**Phase A** (disc identification) is the smallest step. It needs no new physics — just velocity decomposition relative to the central star. It makes the disc visible to the rest of the simulation and enables Phase B (Toomre Q, which needs to know which bodies are disc members).

Next action: approve Phase A, then write schemas, failing tests, and implementation.
