(ns domain.genesis-test
  "Tests for Phase 0: Stellar Nebula — the ECS-based simulation.
   The world is a single ECS world; Phase 0 is a composition layer over it."
  (:require
   [clojure.math :as math] [clojure.test :refer [deftest testing is]]
   [domain.genesis           :as genesis]
   [domain.arc              :as arc]
   [domain.pacing           :as pacing]
   [domain.stellar          :as stellar]
   [domain.stellar.thermodynamics :as thermo]
   [domain.stellar.classifier :as classifier]
   [domain.stellar.collapse :as collapse]
   [domain.stellar.structure :as structure]
   [domain.chemistry        :as chemistry]
   [domain.player           :as player]
   [law.stellar             :as law]
   [law.composition         :as lcomp]
   [domain.ecs.core         :as ecs]
   [domain.ecs.event        :as event]
   [domain.ecs.components    :as c]
   [domain.physics.collision :as collision]
   [domain.spatial.index     :as spatial]
   [shape.spatial           :as sp]))

;; --- Pure physics -----------------------------------------------------------

(deftest test-gravitational-collapse
  (testing "A diffuse, massive, cold region is Jeans-unstable"
    (let [region {:density 1e-18 :temperature 10 :radius 1e17}]
      (is (pos? (collapse/gravitational-collapse-rate region)))
      (is (collapse/jeans-unstable? region))))
  (testing "A small dense warm region is stable against collapse"
    (let [region {:density 5500 :temperature 300 :radius 1e5}]
      (is (not (collapse/jeans-unstable? region))))))

(defn- first-parcel-composition
  "Composition of the first matter parcel in a freshly seeded world."
  [world]
  (let [eid (first (ecs/entities-with world c/matter-state c/composition))]
    (ecs/get-component world eid c/composition)))

(deftest test-metallicity-seeding
  (testing "Default world seeds the Population-I floor so metals exist from tick 0"
    ;; This is the unlock for planet formation: planet-seeds derives solid
    ;; surface density from Z = metallicity(star composition); a metal-free
    ;; nebula gives Z≈0, sigma-solid≈0, and NO planets ever seed.
    (let [w (genesis/create-world {:gas-count 20})
          composition (first-parcel-composition w)]
      (is (= :population-i (:genesis/metallicity w)))
      (is (> (lcomp/metallicity composition) 0.01) "cloud carries solar metals (Z≈0.0167)")
      (is (> (double (get composition :Fe 0.0)) 0.0) "iron is present for rocky cores")
      (is (> (double (get composition :Si 0.0)) 0.0) "silicon is present for silicates")
      (is (lcomp/composition-sums-to-unity? composition))))
  (testing ":primordial preset seeds a metal-free first-generation cloud"
    (let [w (genesis/create-world {:gas-count 20 :metallicity :primordial})
          composition (first-parcel-composition w)]
      (is (< (lcomp/metallicity composition) 1e-4) "no metals in a primordial cloud")
      (is (lcomp/composition-sums-to-unity? composition)))))

(deftest test-virial-collapse-drives-ignition
  (testing "Virial temperature and self-gravity pressure rise as a core contracts"
    (let [m 2e30]
      (is (> (thermo/virial-temperature m 1e9)
             (thermo/virial-temperature m 1e10)))
      (is (> (thermo/self-gravity-pressure m 1e9)
             (thermo/self-gravity-pressure m 1e10))))))

(deftest test-fusion-ignition
  (testing "Fusion needs temperature, pressure, and hydrogen above threshold"
    (is (not (law/fusion-possible? {:temperature 1e6 :pressure 1e24 :composition {:H 0.75}})))
    (is (not (law/fusion-possible? {:temperature 2e7 :pressure 1e8  :composition {:H 0.75}})))
    (is (not (law/fusion-possible? {:temperature 2e7 :pressure 1e14 :composition {:H 0.02}})))
    (is (law/fusion-possible? {:temperature 2e7 :pressure 1e14 :composition {:H 0.75}}))))

(deftest test-hydrostatic-equilibrium
  (testing "Self-gravity rounds bodies above the mass threshold"
    (is (law/hydrostatic-equilibrium? {:mass 6e24}))
    (is (not (law/hydrostatic-equilibrium? {:mass 1e20})))
    (is (not (law/hydrostatic-equilibrium? {:mass nil})))))

(deftest test-pacing
  (testing "Fixed tick rate, bulk-collapse-driven timestep: dt shrinks as the cloud contracts"
    ;; t-dyn/radius chosen in the unclamped band (dt ∈ [min,max], soft ∈ [min,max])
    (let [diffuse   (pacing/pacing-for 3.0e13 8.0e15)
          midway    (pacing/pacing-for 3.0e12 3.0e15)
          collapsed (pacing/pacing-for 3.0e11 3.0e14)]
      (is (> (:dt diffuse) (:dt midway) (:dt collapsed))
          "in-game seconds per tick shrink as the bulk dynamical time shrinks")
      (is (> (:rate diffuse) (:rate midway) (:rate collapsed))
          "wall-clock rate dilates as the cloud collapses (tick count fixed at 60 Hz)")
      (is (every? #(== (:dt %) (/ (:rate %) pacing/ticks-per-second)) [diffuse midway collapsed])
          "dt and the displayed rate are consistent at the fixed tick rate")
      (is (> (:softening diffuse) (:softening collapsed))
          "softening tracks (shrinks with) the bulk radius")
      (is (== (:dt (pacing/pacing-for 1.0e30 1.0e30)) pacing/pacing-dt-max)
          "dt is clamped to the ceiling for a huge diffuse cloud")
      (is (== (:dt (pacing/pacing-for 0.0 0.0)) pacing/pacing-dt-min)
          "dt is clamped to the floor for a degenerate/tiny cloud")))

  (testing "Observable complexity slows the clock independently of bulk collapse"
    ;; Same bulk dynamical time and radius, chosen so the physics-bound dt is
    ;; above the complexity cap for the tested complexities.
    (let [simple       (pacing/pacing-for 1.0e14 1.0e16 0.0)
          with-star    (pacing/pacing-for 1.0e14 1.0e16 5.0)
          with-planets (pacing/pacing-for 1.0e14 1.0e16 25.0)]
      (is (== (:dt simple) (:dt (pacing/pacing-for 1.0e14 1.0e16)))
          "zero complexity is the default and does not change dt")
      (is (> (:dt simple) (:dt with-star) (:dt with-planets))
          "higher complexity yields smaller per-tick steps")
      (is (> (:rate simple) (:rate with-star) (:rate with-planets))
          "higher complexity yields a slower wall-clock rate")))

  (testing "Complexity cap never breaks the dt floor/ceiling"
    (let [huge-complexity (pacing/pacing-for 1.0e30 1.0e30 1.0e6)]
      (is (== (:dt huge-complexity) pacing/pacing-dt-min)
          "extreme complexity is clamped to the dt floor")))

  (testing "Thermal progress climbs monotonically from cold gas toward ignition"
    (is (< (genesis/thermal-progress 10.0)
           (genesis/thermal-progress 1.0e4)
           (genesis/thermal-progress 1.0e7)))
    (is (<= 0.0 (genesis/thermal-progress 5.0) (genesis/thermal-progress 1.0e8) 1.0)))

  (testing "A fresh world starts at the bulk-cloud step derived from its dynamical time"
    (let [w        (genesis/create-world)
          ;; default nebula: radius 2.0e16, mass 4e30
          t-dyn    (math/sqrt (/ (math/pow 2.0e16 3) (* law/G 4.0e30)))
          expected (:dt (pacing/pacing-for t-dyn 2.0e16))]
      (is (== (:sim/dt w) expected)
          "fresh dt is cfl-factor × bulk dynamical time (clamped to the dt band)")
      (is (<= pacing/pacing-dt-min (:sim/dt w) pacing/pacing-dt-max)
          "fresh dt lies within the dt band")
      (is (pos? (:genesis/rate-yr w)))
      (is (== (:genesis/time-scale w) (* (:sim/dt w) pacing/ticks-per-second))
          "time-scale is the derived wall-clock rate: dt × ticks-per-second")))

  (testing "Parallel physics pipeline is a non-empty set of write-set systems"
    (let [systems (genesis/physics-systems-parallel (genesis/create-world))]
      (is (pos? (count systems)))
      (is (every? :writes systems)))))

(defn- world-of-bodies
  "Build a bare world with the given [position mass radius] bodies (resolved
   debris), for cloud-scale tests."
  [bodies]
  (reduce (fn [w [pos m r]]
            (let [[w' eid] (ecs/spawn w)]
              (-> w'
                  (ecs/put-component eid c/position pos)
                  (ecs/put-component eid c/mass (double m))
                  (ecs/put-component eid c/radius (double r))
                  (ecs/put-component eid c/matter-state :planetesimal))))
          (ecs/empty-world)
          bodies))

(deftest test-time-dilation
  (testing "Bulk collapse drives dilation: a more-collapsed cloud gets a smaller timestep"
    (let [diffuse   (pacing/pacing-for 3.0e13 8.0e15)
          collapsed (pacing/pacing-for 3.0e11 3.0e14)]
      (is (< (:dt collapsed) (:dt diffuse))
          "as the bulk dynamical time shrinks, in-game seconds per tick shrink")
      (is (< (:rate collapsed) (:rate diffuse))
          "and the wall-clock rate dilates toward real time")))

  (testing "A single dominant central mass does NOT collapse the bulk scale (no freeze)"
    ;; 200 gas parcels spread over ~1e16 m, plus one body holding HALF the total
    ;; mass at the centre. The 90%-mass radius must still reflect the spread-out
    ;; cloud, not the central pinpoint — otherwise one sink would freeze the rest.
    (let [spread (for [i (range 200)]
                   [[(+ 1.0e16 (* i 1.0e13)) 0.0 0.0] 1.0e28 1.0e13])
          w      (world-of-bodies (cons [[0.0 0.0 0.0] 2.0e30 1.0e9] spread))
          {:keys [radius]} (pacing/cloud-scale w)]
      (is (> radius 5.0e15)
          "bulk scale tracks the spread-out cloud, not the tiny dominant core")))

  (testing "Fixed pacing holds the timestep constant for pace-independent runs"
    (let [w0 (-> (genesis/create-world {:gas-count 50})
                 (assoc :genesis/adaptive-pacing? false :sim/dt 1.0e12))
          w1 (nth (iterate genesis/tick-world w0) 30)]
      (is (== 1.0e12 (:sim/dt w1))
          ":sim/dt is untouched when adaptive pacing is disabled")
      (is (pos? (:genesis/sim-time w1))
          "sim-time advances by the fixed step"))))

(deftest test-stats
  (testing "Per-tick stats tally mass, temperature, and counts"
    (let [w1 (genesis/tick-world (genesis/create-world {:gas-count 50}))
          st (:genesis/stats w1)]
      (is (pos? (:total-mass-kg st)))
      (is (pos? (:total-mass-msun st)))
      (is (<= 0.0 (:avg-temp st) (:peak-temp st)) "mean within [0, peak]")
      (is (= (:body-count st) (:body-count (genesis/system-summary w1)))))))

(deftest test-orbital-motion-advances
  (testing "Ring clumps move when the world ticks"
    (let [w0 (genesis/create-world)
          eids (ecs/entities-with w0 c/matter-state c/position)
          before (into {} (map (juxt identity #(ecs/get-component w0 % c/position))) eids)
          w1 (genesis/tick-world w0)
          ;; some particles merge (accrete) and despawn in a tick; compare only
          ;; survivors that still have a position
          after (into {} (keep (fn [eid]
                                 (when-let [p (ecs/get-component w1 eid c/position)]
                                   [eid p]))
                               eids))
          moved (filter (fn [eid]
                          (when-let [p (get after eid)]
                            (> (sp/dist (get before eid) p) 1e3)))
                        eids)]
      (is (seq moved) "at least one body should have moved by more than 1 km per tick"))))

(deftest test-chemistry-composition
  (testing "Cooling gas forms water"
    (let [cold (chemistry/molecular-composition {:H 0.7 :O 0.1 :C 0.05} 500 1e5)]
      (is (contains? cold :H2O))
      (is (pos? (get cold :H2O 0))))))

(deftest test-habitability
  (testing "Habitability scoring distinguishes living and sterile worlds"
    (is (> (chemistry/habitability-score
            {:temperature 300 :pressure 1e5
             :composition {:H2O 0.1 :C 0.01 :N 0.001}}) 0.5))
    (is (< (chemistry/habitability-score
            {:temperature 500 :pressure 1e8 :composition {:Fe 0.9}}) 0.2))))

;; --- Player / observer ------------------------------------------------------

(deftest test-player-coherence
  (testing "Higher focus-intensity drains more coherence per frame"
    (is (< (player/coherence-drain-from-focus 0.1)
           (player/coherence-drain-from-focus 0.5)))
    (is (< (player/coherence-drain-from-focus 0.5)
           (player/coherence-drain-from-focus 1.0))))
  (testing "Lower focus-intensity regenerates more coherence per frame"
    (is (> (player/coherence-regen-rate 0.1)
           (player/coherence-regen-rate 0.5)))
    (is (> (player/coherence-regen-rate 0.5)
           (player/coherence-regen-rate 0.9))))
  (testing "At high focus, drain exceeds regen; at low focus, regen wins"
    (is (> (player/coherence-drain-from-focus 0.8)
           (player/coherence-regen-rate 0.8)))
    (is (< (player/coherence-drain-from-focus 0.2)
           (player/coherence-regen-rate 0.2)))
    (testing "At default focus (0.5), drain and regen roughly balance"
      (is (< (abs (- (player/coherence-drain-from-focus 0.5)
                     (player/coherence-regen-rate 0.5)))
             0.001))))
  (testing "Witnessing events restores coherence with diminishing returns"
    (let [gain (player/coherence-gain-from-event :stellar-ignition 0.5)]
      (is (pos? gain))
      (is (< gain 0.3)))))

;; --- World construction -----------------------------------------------------

(deftest test-world-construction
  (testing "A fresh world is a cloud of equal-mass gas particles plus one observer"
    (let [w (genesis/create-world {:gas-count 50})]
      ;; nothing is pre-formed: just the seeded gas particles
      (is (= 50 (count (ecs/entities-with w c/matter-state))))
      (is (every? #(= :nebula (ecs/get-component w % c/matter-state))
                  (ecs/entities-with w c/matter-state)))
      ;; every particle carries a magnetic field for the EM/regime layer
      (is (= 50 (count (ecs/entities-with w c/b-field))))
      (is (= 1 (count (ecs/entities-with w c/observer))))
      (is (some? (player/get-observer w)))
      (is (true? (:genesis/active w))))))

(deftest per-body-promotion-events-fire-for-each-transition
  (testing "Every body that promotes out of nebula or ignites emits its own event"
    (let [before (ecs/empty-world)
          [before gas0] (stellar/spawn-clump before {:position [0 0 0] :mass 1e29 :radius 1e14
                                                     :matter-state :nebula})
          [before gas1] (stellar/spawn-clump before {:position [1e15 0 0] :mass 1e29 :radius 1e14
                                                     :matter-state :nebula})
          [before debris] (stellar/spawn-clump before {:position [2e15 0 0] :mass 1e25 :radius 1e10
                                                       :matter-state :nebula})
          after  (-> before
                     (ecs/put-component gas0 c/matter-state :protostar)
                     (ecs/put-component gas1 c/matter-state :planetesimal)
                     (ecs/put-component debris c/matter-state :planetesimal)
                     (assoc :tick 7)
                     (genesis/emit-promotion-events before))
          kinds (->> (event/events-since after 7)
                     (filter #(= (:tick %) 7))
                     (map :kind)
                     frequencies)]
      (is (= 1 (get kinds :event/protostar-formation 0))
          "nebula->protostar emits exactly one protostar-formation event")
      (is (= 2 (get kinds :event/planetesimal-formation 0))
          "nebula->debris emits one body-resolved event per resolved parcel"))))

(deftest per-body-promotion-events-pay-agency-for-every-body
  (testing "Each promotion event pays agency; resonance only pays the first time per category"
    (let [w0     (genesis/create-world {:gas-count 20 :nebula-radius 1.2e16
                                        :contraction-time 2e12 :spin 0.55})
          w0     (assoc w0 :genesis/adaptive-pacing? false :sim/dt 1.0e12)
          obs0   (player/get-observer w0)
          ;; Tick through the full genesis+arc+observer pipeline until multiple
          ;; bodies have condensed.
          final  (loop [w w0 i 0]
                   (if (or (>= i 60) (>= (:resolved-count (genesis/system-summary w)) 2)
                           (not (:genesis/active w)))
                     w
                     (recur (arc/tick-genesis w) (inc i))))
          events (->> (event/events-since final 0) (map :kind) frequencies)
          promotions (+ (get events :event/planetesimal-formation 0)
                        (get events :event/gas-giant-formation 0)
                        (get events :event/brown-dwarf-formation 0)
                        (get events :event/protostar-formation 0)
                        (get events :event/stellar-ignition 0)
                        (get events :event/planet-formation 0))
          obs    (player/get-observer final)]
      (is (>= promotions 2)
          "at least two distinct per-body promotions should fire")
      (is (> (:agency obs) (:agency obs0))
          "observer gains agency from the per-body promotions")
      (is (>= (:resonance obs) 0.0)
          "resonance is non-negative"))))

;; --- Accretion / merge handler ----------------------------------------------

(deftest test-stellar-merge
  (testing "Overlapping resolved bodies merge, conserving mass into one entity"
    (let [base    (-> (ecs/empty-world)
                      (event/with-ledger)
                      (event/register-handler :event/collision
                                              structure/stellar-merge-handler))
          [w1 _]  (stellar/spawn-clump base {:position [0 0 0]   :mass 2e30 :radius 1.0
                                             :matter-state :protostar})
          [w2 _]  (stellar/spawn-clump w1   {:position [0.5 0 0] :mass 1e30 :radius 1.0
                                             :matter-state :planet})
          w2      (spatial/spatial-index w2)
          w3      (collision/collision-detection-system w2)
          w3      (genesis/materialize-lifecycle w3)
          remaining (ecs/entities-with w3 c/mass)]
      (is (= 1 (count remaining)))
      (testing "absorb-merge packet carries the absorbed mass"
        (let [pkts (ecs/get-component w3 (first remaining) c/absorb-merge)]
          (is (some? pkts))
          (is (< (abs (- 1e30 (reduce + (map :mass pkts)))) 1e25)
              "packet carries the smaller body's mass")))
      ;; The merged body sits at the survivor's position. The integrator will
      ;; blend to the mass-weighted centroid next tick.
      (let [[x] (ecs/get-component w3 (first remaining) c/position)]
        (is (= 0.0 (double x))
            "survivor stays at its position (centroid blended by integrator)")))))

;; Arc detection moved to domain.arc — see test/domain/arc_test.clj.

;; --- Full arc ---------------------------------------------------------------

(deftest test-full-simulation-parallel
  (testing "The double-buffer path forms a star by accretion"
    ;; Go-live regression (design note §7c): density-gated condensation flips
    ;; parcels out of the gas, the accretion-zone owner latches each condensing
    ;; body a feeding zone on the SAME tick, and sink-formation grows the core by
    ;; accreting the surrounding gas until it ignites. Resolved bodies merge only
    ;; on literal collision; gas accretion is the dominant growth channel.
    (let [w0    (-> (genesis/create-world {:gas-count 50 :nebula-radius 1.2e16
                                           :contraction-time 2e12 :spin 0.55})
                    ;; fixed coarse step keeps the emergence regression fast and
                    ;; independent of the pacing curve (pacing covered elsewhere).
                    (assoc :genesis/adaptive-pacing? false :sim/dt 1.0e12))
          final (loop [w w0 i 0]
                  (if (or (> i 200) (:star? (genesis/system-summary w))
                          (not (:genesis/active w)))
                    w
                    (recur (genesis/tick-world w) (inc i))))
          summ  (genesis/system-summary final)]
      (is (:star? summ) "a star should ignite on the parallel path too")
      (is (>= (:resolved-count summ) 1)
          "bodies should condense and assemble rather than stall as gas")
      ;; Big gas-collapse condensations still latch a feeding zone in the classifier.
      (is (seq (ecs/entities-with final c/accretion-radius))
          "condensed bodies must latch a gravitational feeding zone"))))

(deftest test-classifier-latches-feeding-zone-for-big-condensations
  (testing "The feeding zone is latched when the classifier whole-parcel promotes"
    ;; A dense, Jeans-unstable parcel above the planetesimal floor: the classifier
    ;; promotes it out of :nebula and writes c/accretion-radius on the same tick.
    (let [gas-mass law/deuterium-burning-mass
          ;; a fat, dense gas parcel above the deuterium limit, larger than its
          ;; Jeans length so it is unstable and will condense to a brown dwarf
          region {:matter-state :nebula
                  :mass    (* 4.0 law/deuterium-burning-mass)
                  :radius  1.0e14
                  :density (* 10.0 classifier/core-condensation-density)
                  :temperature 12.0}]
      (is (not= :nebula (classifier/classify-next-state region gas-mass))
          "precondition: this parcel condenses")
      (let [base (-> (genesis/create-world {:gas-count 4})
                     (assoc :genesis/gas-particle-mass gas-mass
                            :genesis/feeding-zone-factor structure/feeding-zone-factor))
            [w eid] (ecs/spawn base)
            w  (ecs/put-components w eid
                                   {c/matter-state :nebula c/mass (:mass region)
                                    c/radius (:radius region) c/density (:density region)
                                    c/temperature (:temperature region) c/position [0.0 0.0 0.0]})
            ws ((:run (classifier/classifier-system)) w)
            new-state (get-in ws [c/matter-state eid])
            zone (get-in ws [c/accretion-radius eid])
            expected-zone (* structure/feeding-zone-factor
                             (:genesis/gas-smoothing-radius base))]
        (is (not= :nebula new-state) "a big condensing parcel is promoted out of nebula")
        (is (some? zone) "a condensing parcel is given a feeding zone")
        (is (= zone expected-zone)
            "the zone is feeding-zone-factor × the global gas smoothing radius")))))

;; Endings moved to domain.arc/genesis-ending — see test/domain/arc_test.clj.

;; Input dispatch moved to infra.input — see test/infra/input_test.clj.
