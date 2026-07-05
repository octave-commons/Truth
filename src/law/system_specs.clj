(ns law.system-specs
  "System function contracts for Phase 0 → Phase 1 transition.

   Derived from research notebooks in docs/research/. Each spec defines:
   - What components a system READS (from the frozen snapshot)
   - What components a system WRITES (its write-set)
   - Precondition: what state must exist before the system runs
   - Postcondition: what invariants hold after the system runs

   These are NOT executable specs — they are documentation contracts that
   guide implementation and testing. A system that violates its contract
   is a bug.

   See: docs/research/cosmology/primordial-nucleosynthesis-yields.md §7
   See: docs/research/phase1-radiation-plasma-truth.md §5-7")

;; ============================================================================
;; Phase 0: BBN Initial Composition
;; Source: cosmology/primordial-nucleosynthesis-yields.md §7
;; ============================================================================

(def primordial-composition-system-spec
  "System: primordial-composition-system
   Phase: 0 (world initialization, runs ONCE)
   Namespace: domain.stellar

   READS:  c/matter-state, c/composition
   WRITES: c/composition

   Precondition:
     - World exists with :nebula entities carrying arbitrary composition defaults
       like {:H 0.70 :He 0.28 :metals 0.02}

   Postcondition:
     - Every :nebula entity's :composition is merged with primordial-composition
       from law.composition (existing keys preserved, missing keys filled)
     - H + He ≈ 1.0 (within 1% tolerance)
     - metals = 0.0 (no stellar processing yet)
     - D, He3, Li7 set to BBN yields (Li7 uses observed value, not BBN prediction)

   Test (kaocha):
     (deftest primordial-composition-sums-to-unity
       (let [comp (law.composition/primordial-composition)]
         (is (≈ 1.0 (+ (:H comp) (:He comp)) 1e-3))
          (is (< (:metals comp) 1e-6))))")

;; ---------------------------------------------------------------------------
;; Phase 0: Deuterium Depletion (separate system — user decision)
;; Source: cosmology/primordial-nucleosynthesis-yields.md §8 (open question #2)
;; ---------------------------------------------------------------------------

(def deuterium-depletion-system-spec
  "System: deuterium-depletion-system
   Phase: 0 (runs every tick for :protostar and :star entities)
   Namespace: domain.stellar

   READS:  c/matter-state, c/composition, c/temperature
   WRITES: c/composition (:D key zeroed)

   Precondition:
     - Entity is :protostar or :star (has contracted past Jeans collapse)
     - Composition carries a :D mass fraction (from primordial seeding)

   Postcondition:
     - Any entity with temperature > 1e6 K has :D set to 0.0
     - D is destroyed in stellar interiors — every star that forms destroys its D
     - This is a ONE-WAY gate: D never re-appears once destroyed
     - Sub-stellar bodies (debris, planets) retain primordial D

   Rationale:
     D is the most fragile isotope — destroyed at T > 10⁶ K, well below
     fusion temperatures. Tracking it separately keeps composition honest
     without coupling D-depletion to the fusion system.

   Test (kaocha):
     (deftest deuterium-depleted-in-hot-bodies
       (let [world  (seed-entity {:matter-state :star :temperature 1e7
                                   :composition {:H 0.75 :He 0.24 :D 5e-6}})
             world' (deuterium-depletion-system world)
             eid    (first (ecs/entities-with world' c/matter-state))]
         (is (zero? (:D (ecs/get-component world' eid c/composition))))))

     (deftest deuterium-retained-in-cold-bodies
       (let [world  (seed-entity {:matter-state :debris :temperature 300.0
                                   :composition {:H 0.75 :He 0.24 :D 5e-6}})
             world' (deuterium-depletion-system world)
             eid    (first (ecs/entities-with world' c/matter-state))]
          (is (= 5e-6 (:D (ecs/get-component world' eid c/composition))))))")

;; ============================================================================
;; Phase 1: Stellar SED
;; Source: phase1-radiation-plasma-truth.md §2
;; ============================================================================

(def stellar-sed-system-spec
  "System: stellar-sed-system
   Phase: 1 (runs every tick for :star entities)
   Namespace: domain.stellar

   READS:  c/mass, c/radius, c/composition, c/matter-state, c/luminosity
   WRITES: c/sed-bands, c/luminosity (revised)

   Precondition:
     - Entity is :star with positive mass, radius, and luminosity
     - Composition map exists (at minimum {:H :He :metals})

   Postcondition:
     - c/sed-bands is a map of band-keyword → Watts
     - Sum of band luminosities = c/luminosity (within 1%)
     - All band values are non-negative finite numbers
     - T_eff derived from L and R via Stefan-Boltzmann
     - SED shape matches T_eff (hot stars peak in UV, cool in IR)

   Integration notes:
     - REPLACES star-luminosity as the source of c/luminosity for :star entities.
       star-luminosity is still used by stellar-wind-system for mass-loss
       (Ṁ ∝ L/(v_esc·c)) — SED system writes c/luminosity BEFORE wind reads it.
     - radiation-heating-delta stays bolometric for Phase 1. Band-specific
       heating is a LATER extension.
     - radiation-equilibrium-temperature stays bolometric. Same rationale.

   Test (kaocha):
     (deftest stellar-sed-system-computes-bands
       (let [world  (seed-test-star {:mass solar-mass :radius solar-radius})
             world' (stellar-sed-system world)
             eid    (first (ecs/entities-with world' c/matter-state))
             sed    (ecs/get-component world' eid c/sed-bands)]
         (is (some? sed))
         (is (> (reduce + (vals sed)) 0.0))
         (is (≈ (reduce + (vals sed))
                (ecs/get-component world' eid c/luminosity) 0.01))))")

;; ============================================================================
;; Phase 1: Atmosphere Shells
;; Source: phase1-radiation-plasma-truth.md §3
;; ============================================================================

(def atmosphere-shells-system-spec
  "System: atmosphere-shells-system
   Phase: 1 (runs every tick for :star entities)
   Namespace: domain.plasma.atmosphere

   READS:  c/sed-bands, c/mass, c/radius, c/b-field, c/matter-state
   WRITES: c/atmosphere-shells

   Precondition:
     - Entity is :star with valid c/sed-bands (stellar-sed-system has run)
     - B-field vector exists (may be zero)

   Postcondition:
     - c/atmosphere-shells contains exactly 4 layers:
       :photosphere, :chromosphere, :transition, :corona
     - Each layer has :temperature, :electron-density, :ionization-fraction
     - Photosphere T ≈ T_eff (from Stefan-Boltzmann)
     - Corona T ≫ photosphere T (10⁶–10⁷ K)
     - Ionization fraction increases from photosphere to corona
     - Heights are monotonically increasing

   Test (kaocha):
     (deftest atmosphere-shells-system-creates-layers
       (let [world  (seed-test-star-with-sed)
             world' (atmosphere-shells-system world)
             eid    (first (ecs/entities-with world' c/matter-state))
             shells (ecs/get-component world' eid c/atmosphere-shells)]
         (is (= 4 (count shells)))
         (is (every? #(contains? % :temperature) shells))
         (is (> (-> shells last :temperature)
                (-> shells first :temperature)))))")

;; ============================================================================
;; Phase 1: Plasma Winds
;; Source: phase1-radiation-plasma-truth.md §5
;; ============================================================================

(def stellar-wind-system-plasma-spec
  "System: stellar-wind-system (Phase 1 revision)
   Phase: 1 (serial barrier, runs after sink-formation-system)
   Namespace: domain.stellar

   READS:  c/matter-state, c/mass, c/radius, c/position, c/velocity,
           c/atmosphere-shells, c/sed-bands, c/b-field, c/wind-reservoir
    WRITES: c/mass, c/velocity, c/wind-reservoir (on star)
            c/position, c/velocity, c/mass, c/density, c/temperature,
            c/ionization-fraction, c/b-field, c/ram-pressure (on wind parcels)

    Precondition:
      - Entity is :star with valid atmosphere-shells (corona layer exists)
      - SED bands computed (for XUV/EUV contribution to wind driving)
      - Note: c/ionization-fraction and c/ram-pressure are Phase 1 component
        keywords added to domain.ecs.components

   Postcondition:
     - Wind parcels are spawned as :nebula with ionization-fraction > 0.5
     - Each parcel carries :ram-pressure derived from Ṁ and v_wind
     - Each parcel carries the star's B-field at the launch point
     - Mass is conserved: star mass decreases by parcel mass
     - Momentum is conserved: star recoils opposite to ejection
     - Parcels NOT within the star's accretion radius (no emit→absorb flicker)

   Test (kaocha):
     (deftest stellar-wind-system-produces-plasma-parcels
       (let [world  (seed-test-star-with-atmosphere-and-sed)
             world' (stellar-wind-system world)
             parcels (ecs/entities-with world' c/matter-state c/ionization-fraction)]
         (is (seq parcels))
         (let [eid (first parcels)]
           (is (> (ecs/get-component world' eid c/ionization-fraction) 0.5))
           (is (> (ecs/get-component world' eid c/ram-pressure) 0.0)))))")

;; ============================================================================
;; Phase 1: XUV Atmospheric Escape
;; Source: phase1-radiation-plasma-truth.md §4.2, §5.3
;; ============================================================================

(def xuv-atmospheric-escape-system-spec
  "System: xuv-atmospheric-escape-system
   Phase: 1 (runs every tick for :planet entities near stars)
   Namespace: domain.atmosphere

   READS:  c/mass, c/radius, c/atmos-cell, c/sed-bands (from host star),
           c/position (planet and star)
   WRITES: c/atmosphere-escape

   Precondition:
     - Planet entity with :atmos-cell or :atmosphere component
     - Host star has valid c/sed-bands (XUV luminosity available)

   Postcondition:
     - c/atmosphere-escape has :regime set to :energy-limited or
       :recombination-limited based on incident XUV flux
     - :xuv-flux is the XUV band luminosity at the planet's distance
     - :mass-loss-rate is non-negative (kg/s)
     - Energy-limited: Ṁ ∝ F_xuv · R_p³ / M_p
     - Recombination-limited: Ṁ saturates at high F_xuv

   Test (kaocha):
     (deftest xuv-atmospheric-escape-system-selects-regime
       (let [world  (seed-star-planet-system-with-sed)
             world' (xuv-atmospheric-escape-system world)
             planet (first (ecs/entities-with world' c/atmosphere-escape))
             esc    (ecs/get-component world' planet c/atmosphere-escape)]
         (is (#{:energy-limited :recombination-limited} (:regime esc)))
         (is (> (:mass-loss-rate esc) 0.0))))")

;; ============================================================================
;; Phase 1: Flare XUV Boost
;; Source: phase1-radiation-plasma-truth.md §6
;; ============================================================================

(def stellar-flare-xuv-spec
  "System: stellar-flare-system (Phase 1 revision)
   Phase: 1 (runs when flare triggers)
   Namespace: domain.stellar

   READS:  c/sed-bands, c/matter-state
   WRITES: c/sed-bands (transient XUV boost), c/event-source

   Precondition:
     - Entity is :star with valid c/sed-bands
     - Flare event triggers (stochastic or periodic)

   Postcondition:
     - c/sed-bands :xray and :euv values multiplied by boost factor
     - c/event-source set to {:kind :flare :payload {...}}
     - Boost decays exponentially over flare duration
     - Non-XUV bands unchanged

   Test (kaocha):
     (deftest stellar-flare-boosts-xuv-band
       (let [world   (seed-flaring-star-with-sed)
             world'  (stellar-flare-system world)
             eid     (first (ecs/entities-with world' c/matter-state))
             sed     (ecs/get-component world' eid c/sed-bands)]
         (is (> (:xray sed) 0.0))
         (is (some? (ecs/get-component world' eid c/event-source)))))")

;; ============================================================================
;; Phase 1: LOD Scheduler
;; Source: phase1-radiation-plasma-truth.md §7
;; ============================================================================

(def lod-scheduler-spec
  "System: lod-scheduler
   Phase: 1 (runs every tick, before radiation systems)
   Namespace: domain.genesis

   READS:  c/position (player observer), c/position (all stars/planets),
           c/lod-level (if set)
   WRITES: c/lod-level

   Precondition:
     - Player observer entity exists with c/position
     - Stars and planets exist with c/position

   Postcondition:
     - Each entity has c/lod-level set to :galaxy, :system, or :local
     - :galaxy — too far for detailed radiation (coarse SED only)
     - :system — band luminosities and steady winds resolved
     - :local — full atmosphere shells, XUV escape, CME shocks
     - LOD is observer-centric: moves with the player's focus

   Test (kaocha):
     (deftest lod-scheduler-disables-distant-radiation-detail
       (let [world   (seed-multi-system-world-with-player)
             world'  (lod-scheduler world)
             distant (first (ecs/entities-with world' c/lod-level))]
         (is (some? (ecs/get-component world' distant c/lod-level)))))")
