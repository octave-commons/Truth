(ns domain.phase0-test
  "Tests for Phase 0: Stellar Nebula — the ECS-based simulation.
   The world is a single ECS world; Phase 0 is a composition layer over it."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.phase0           :as phase0]
   [domain.stellar          :as stellar]
   [domain.chemistry        :as chemistry]
   [domain.player           :as player]
   [law.stellar             :as law]
   [domain.ecs.core         :as ecs]
   [domain.ecs.event        :as event]
   [domain.ecs.components    :as c]
   [domain.physics.collision :as collision]
   [shape.spatial           :as sp]))

;; --- Pure physics -----------------------------------------------------------

(deftest test-gravitational-collapse
  (testing "A diffuse, massive, cold region is Jeans-unstable"
    (let [region {:density 1e-18 :temperature 10 :radius 1e17}]
      (is (> (stellar/gravitational-collapse-rate region) 0))
      (is (stellar/jeans-unstable? region))))
  (testing "A small dense warm region is stable against collapse"
    (let [region {:density 5500 :temperature 300 :radius 1e5}]
      (is (not (stellar/jeans-unstable? region))))))

(deftest test-virial-collapse-drives-ignition
  (testing "Virial temperature and self-gravity pressure rise as a core contracts"
    (let [m 2e30]
      (is (> (stellar/virial-temperature m 1e9)
             (stellar/virial-temperature m 1e10)))
      (is (> (stellar/self-gravity-pressure m 1e9)
             (stellar/self-gravity-pressure m 1e10))))))

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
  (testing "Pacing is continuous: rate dilates with thermal progress, dt/softening with orbits"
    (let [cold   (phase0/pacing-for 0.0 0.0)
          warm   (phase0/pacing-for 0.5 0.0)
          hot    (phase0/pacing-for 1.0 0.0)
          orbits (phase0/pacing-for 1.0 1.0)]
      (is (> (:rate cold) (:rate warm) (:rate hot))
          "wall-clock rate dilates smoothly as the core heats")
      (is (= (:dt cold) (:dt hot))
          "dt stays large through the Myr-scale collapse/contraction (no orbits yet)")
      (is (> (:dt hot) (:dt orbits))
          "dt refines only once tight planetary orbits exist")
      (is (> (:softening hot) (:softening orbits)))))

  (testing "Thermal progress climbs monotonically from cold gas toward ignition"
    (is (< (phase0/thermal-progress 10.0)
           (phase0/thermal-progress 1.0e4)
           (phase0/thermal-progress 1.0e7)))
    (is (<= 0.0 (phase0/thermal-progress 5.0) (phase0/thermal-progress 1.0e8) 1.0)))

  (testing "A fresh world starts cold, at the nebular rate and step"
    (let [w   (phase0/create-world)
          neb (phase0/pacing-for 0.0 0.0)]
      (is (= 1.0e12 (:sim/dt w)) "nebular integration step")
      (is (pos? (:phase0/rate-yr w)))
      (is (= (:rate neb) (:phase0/time-scale w))
          "time-scale is the clock rate in sim-seconds per real second")))

  (testing "Physics pipeline has eleven ordered systems, density first"
    (let [systems (phase0/physics-systems (phase0/create-world))]
      (is (= 11 (count systems)))
      (is (fn? (first systems))))))

(deftest test-stats
  (testing "Per-tick stats tally mass, temperature, and counts"
    (let [w1 (phase0/tick-world (phase0/create-world {:gas-count 50}))
          st (:phase0/stats w1)]
      (is (pos? (:total-mass-kg st)))
      (is (pos? (:total-mass-msun st)))
      (is (<= 0.0 (:avg-temp st) (:peak-temp st)) "mean within [0, peak]")
      (is (= (:body-count st) (:body-count (phase0/system-summary w1)))))))

(deftest test-orbital-motion-advances
  (testing "Ring clumps move when the world ticks"
    (let [w0 (phase0/create-world)
          eids (ecs/entities-with w0 c/matter-state c/position)
          before (into {} (map (juxt identity #(ecs/get-component w0 % c/position))) eids)
          w1 (phase0/tick-world w0)
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
      (is (> (get cold :H2O 0) 0)))))

(deftest test-habitability
  (testing "Habitability scoring distinguishes living and sterile worlds"
    (is (> (chemistry/habitability-score
            {:temperature 300 :pressure 1e5
             :composition {:H2O 0.1 :C 0.01 :N 0.001}}) 0.5))
    (is (< (chemistry/habitability-score
            {:temperature 500 :pressure 1e8 :composition {:Fe 0.9}}) 0.2))))

;; --- Player / observer ------------------------------------------------------

(deftest test-player-coherence
  (testing "Coherence drains faster in complex regions"
    (let [obs (player/create-observer [0 0 0])]
      (is (< (player/coherence-drain-rate obs 1.0)
             (player/coherence-drain-rate obs 100.0)))))
  (testing "Witnessing events restores coherence with diminishing returns"
    (let [gain (player/coherence-gain-from-event :stellar-ignition 0.5)]
      (is (> gain 0))
      (is (< gain 0.3)))))

;; --- World construction -----------------------------------------------------

(deftest test-world-construction
  (testing "A fresh world is a cloud of equal-mass gas particles plus one observer"
    (let [w (phase0/create-world {:gas-count 50})]
      ;; nothing is pre-formed: just the seeded gas particles
      (is (= 50 (count (ecs/entities-with w c/matter-state))))
      (is (every? #(= :nebula (ecs/get-component w % c/matter-state))
                  (ecs/entities-with w c/matter-state)))
      ;; every particle carries a magnetic field for the EM/regime layer
      (is (= 50 (count (ecs/entities-with w c/b-field))))
      (is (= 1 (count (ecs/entities-with w c/observer))))
      (is (some? (player/get-observer w)))
      (is (true? (:phase0/active w))))))

;; --- Accretion / merge handler ----------------------------------------------

(deftest test-stellar-merge
  (testing "Overlapping bodies merge, conserving mass into one entity"
    (let [base    (-> (ecs/empty-world)
                      (event/with-ledger)
                      (event/register-handler :event/collision
                                              stellar/stellar-merge-handler))
          [w1 _]  (stellar/spawn-clump base {:position [0 0 0]   :mass 2e30 :radius 1.0})
          [w2 _]  (stellar/spawn-clump w1   {:position [0.5 0 0] :mass 1e30 :radius 1.0})
          w3      (collision/collision-detection-system w2)
          remaining (ecs/entities-with w3 c/mass)]
      (is (= 1 (count remaining)))
      (is (< (Math/abs (- 3e30 (ecs/get-component w3 (first remaining) c/mass)))
             1e25))
      ;; The merged body sits at the MASS-WEIGHTED CENTROID, conserving the
      ;; system centre of mass. If it snapped to the larger body instead, the
      ;; COM would jump and recenter-system would teleport the whole cloud.
      (let [[x] (ecs/get-component w3 (first remaining) c/position)
            expected (/ (* 1e30 0.5) 3e30)] ; (2e30·0 + 1e30·0.5)/3e30
        (is (< (Math/abs (- (double x) expected)) 1e-9)
            "merged position is the mass-weighted centroid, not the larger body")))))

;; --- Phase detection --------------------------------------------------------

(deftest test-phase-detection
  (testing "Phase follows the state of the resolved matter"
    (is (= :phase-0/nebula-collapse
           (phase0/detect-phase {:star? false :planet-count 0 :body-count 3
                                 :regions [{:matter-state :nebula}]} 0.0)))
    (is (= :phase-0/protostar
           (phase0/detect-phase {:star? false :planet-count 0 :body-count 1
                                 :regions [{:matter-state :protostar}]} 0.0)))
    (is (= :phase-0/planets-formed
           (phase0/detect-phase {:star? true :planet-count 2 :body-count 3
                                 :regions [{:matter-state :star}]} 0.0)))
    (is (= :phase-0/dispersed
           (phase0/detect-phase {:star? false :planet-count 0 :body-count 0
                                 :regions []} 1e20)))))

;; --- Full arc ---------------------------------------------------------------

(deftest test-full-simulation
  (testing "A gas cloud collapses and a star + other bodies emerge by accretion"
    ;; A compact, fast-forming cloud: dense (small radius → short free-fall) and
    ;; quick contraction (τ small), so a star ignites within a bounded tick
    ;; budget. The production defaults deliberately stretch this to ~tens of Myr
    ;; (see `create-world`); this test pins the EMERGENCE, not the pace.
    (let [w0 (phase0/create-world {:gas-count 400 :nebula-radius 1.2e16
                                   :contraction-time 2e12 :spin 0.55})
          ;; run until a star ignites from the gas or we exhaust the budget
          final (loop [w w0 i 0]
                  (if (or (> i 400) (:star? (phase0/system-summary w))
                          (not (:phase0/active w)))
                    w
                    (recur (phase0/tick-world w) (inc i))))
          summ (phase0/system-summary final)]
      (is (:star? summ) "a star should ignite from the collapsing cloud")
      (is (> (:resolved-count summ) 1)
          "other bodies (planets/debris) should condense alongside the star")
      (is (> (:phase0/sim-time final) 0.0))
      (is (not= :initializing (:phase0/phase final)))
      (let [coh (:coherence (player/get-observer final))]
        (is (<= 0.0 coh 1.0))))))

;; --- Endings ----------------------------------------------------------------

(deftest test-ending-conditions
  (testing "A habitable planet at planets-formed yields success"
    (let [base    (phase0/create-world)
          [w eid] (ecs/spawn base)
          w       (-> (ecs/put-components w eid
                        {c/mass 6e24 c/radius 6.4e6 c/position [1e16 0 0]
                         c/velocity [0 0 0] c/body-kind :body/planet
                         c/matter-state :planet c/temperature 300.0
                         c/density 5500.0 c/pressure 1e5
                         c/composition {:H2O 0.1 :C 0.01 :N 0.001}
                         c/luminosity 0.0})
                      (assoc :phase0/phase :phase-0/planets-formed))]
      (is (= :success (:type (phase0/world-ending w))))))

  (testing "Exhausted coherence yields a graceful fadeout"
    (let [w (-> (phase0/create-world)
                (player/update-observer #(assoc % :coherence 0.01)))]
      (is (= :fadeout (:type (phase0/world-ending w)))))))

;; --- Input ------------------------------------------------------------------

(deftest test-input-handling
  (testing "Controls operate on the observer in the world"
    (let [w        (phase0/create-world)
          before   (:focus-radius (player/get-observer w))
          narrowed (phase0/handle-input w :narrow-focus)
          moved    (phase0/handle-input w :move-focus [1e15 1e15 0])]
      (is (< (:focus-radius (player/get-observer narrowed)) before))
      (is (= [1e15 1e15 0] (:focus-position (player/get-observer moved)))))))
