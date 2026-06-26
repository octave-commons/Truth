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
   [domain.physics.collision :as collision]))

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

(deftest test-time-compression
  (testing "Higher observable complexity slows simulation time"
    (is (> (stellar/time-scale-from-complexity 1)
           (stellar/time-scale-from-complexity 50)))))

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
  (testing "A fresh world has nebular bodies and exactly one observer"
    (let [w (phase0/create-world)]
      (is (= 7 (count (ecs/entities-with w c/matter-state))))
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
             1e25)))))

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
  (testing "A run produces a star and planets, advancing through phases"
    (let [w0 (phase0/create-world)
          ;; run until a star ignites or we exhaust a generous budget
          final (loop [w w0 i 0]
                  (if (or (> i 80) (:star? (phase0/system-summary w))
                          (not (:phase0/active w)))
                    w
                    (recur (phase0/tick-world w) (inc i))))
          summ (phase0/system-summary final)]
      (is (:star? summ) "a star should ignite")
      (is (pos? (:planet-count summ)) "planets should form")
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
