(ns domain.phase0-test
  "Tests for Phase 0: Stellar Nebula — the ECS-based simulation.
   The world is a single ECS world; Phase 0 is a composition layer over it."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.phase0           :as phase0]
   [domain.pacing           :as pacing]
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

  (testing "Thermal progress climbs monotonically from cold gas toward ignition"
    (is (< (phase0/thermal-progress 10.0)
           (phase0/thermal-progress 1.0e4)
           (phase0/thermal-progress 1.0e7)))
    (is (<= 0.0 (phase0/thermal-progress 5.0) (phase0/thermal-progress 1.0e8) 1.0)))

  (testing "A fresh world starts at the bulk-cloud step derived from its dynamical time"
    (let [w        (phase0/create-world)
          ;; default nebula: radius 2.0e16, mass 4e30
          t-dyn    (Math/sqrt (/ (Math/pow 2.0e16 3) (* law/G 4.0e30)))
          expected (:dt (pacing/pacing-for t-dyn 2.0e16))]
      (is (== (:sim/dt w) expected)
          "fresh dt is cfl-factor × bulk dynamical time (clamped to the dt band)")
      (is (<= pacing/pacing-dt-min (:sim/dt w) pacing/pacing-dt-max)
          "fresh dt lies within the dt band")
      (is (pos? (:phase0/rate-yr w)))
      (is (== (:phase0/time-scale w) (* (:sim/dt w) pacing/ticks-per-second))
          "time-scale is the derived wall-clock rate: dt × ticks-per-second")))

  (testing "Parallel physics pipeline is a non-empty set of write-set systems"
    (let [systems (phase0/physics-systems-parallel (phase0/create-world))]
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
                  (ecs/put-component eid c/matter-state :debris))))
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
    (let [w0 (-> (phase0/create-world {:gas-count 50})
                 (assoc :phase0/adaptive-pacing? false :sim/dt 1.0e12))
          w1 (nth (iterate phase0/tick-world w0) 30)]
      (is (== 1.0e12 (:sim/dt w1))
          ":sim/dt is untouched when adaptive pacing is disabled")
      (is (pos? (:phase0/sim-time w1))
          "sim-time advances by the fixed step"))))

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
      (is (< (Math/abs (- (player/coherence-drain-from-focus 0.5)
                          (player/coherence-regen-rate 0.5)))
             0.001))))
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
  (testing "Overlapping resolved bodies merge, conserving mass into one entity"
    (let [base    (-> (ecs/empty-world)
                      (event/with-ledger)
                      (event/register-handler :event/collision
                                              stellar/stellar-merge-handler))
          [w1 _]  (stellar/spawn-clump base {:position [0 0 0]   :mass 2e30 :radius 1.0
                                             :matter-state :protostar})
          [w2 _]  (stellar/spawn-clump w1   {:position [0.5 0 0] :mass 1e30 :radius 1.0
                                             :matter-state :planet})
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

(deftest test-full-simulation-parallel
  (testing "The double-buffer path forms a star by accretion"
    ;; Go-live regression (design note §7c): density-gated condensation flips
    ;; parcels out of the gas, the accretion-zone owner latches each condensing
    ;; body a feeding zone on the SAME tick, and sink-formation grows the core by
    ;; accreting the surrounding gas until it ignites. Resolved bodies merge only
    ;; on literal collision; gas accretion is the dominant growth channel.
    (let [w0    (-> (phase0/create-world {:gas-count 50 :nebula-radius 1.2e16
                                          :contraction-time 2e12 :spin 0.55})
                    ;; fixed coarse step keeps the emergence regression fast and
                    ;; independent of the pacing curve (pacing covered elsewhere).
                    (assoc :phase0/adaptive-pacing? false :sim/dt 1.0e12))
          final (loop [w w0 i 0]
                  (if (or (> i 400) (:star? (phase0/system-summary w))
                          (not (:phase0/active w)))
                    w
                    (recur (phase0/tick-world w) (inc i))))
          summ  (phase0/system-summary final)]
      (is (:star? summ) "a star should ignite on the parallel path too")
      (is (>= (:resolved-count summ) 1)
          "bodies should condense and assemble rather than stall as gas")
      ;; the fix's mechanism: condensed bodies carry a feeding zone
      (is (seq (ecs/entities-with final c/accretion-radius))
          "condensed bodies must latch a gravitational feeding zone"))))

(deftest test-accretion-zone-tracks-condensation
  (testing "The feeding zone is latched exactly when the classifier condenses"
    ;; A dense, Jeans-unstable, star-forming parcel: the classifier promotes it
    ;; out of :nebula, and accretion-zone-system must write its feeding zone on
    ;; the same frozen snapshot — keyed off the same classify-next-state decision.
    (let [gas-mass law/deuterium-burning-mass
          ;; a fat, dense gas parcel above the deuterium limit, larger than its
          ;; Jeans length so it is unstable and will condense to a protostar
          region {:matter-state :nebula
                  :mass    (* 4.0 law/deuterium-burning-mass)
                  :radius  1.0e14
                  :density (* 10.0 stellar/core-condensation-density)
                  :temperature 12.0}]
      (is (not= :nebula (stellar/classify-next-state region gas-mass))
          "precondition: this parcel condenses")
      (let [base (-> (phase0/create-world {:gas-count 4})
                     (assoc :phase0/gas-particle-mass gas-mass
                            :phase0/feeding-zone-factor stellar/feeding-zone-factor))
            [w eid] (ecs/spawn base)
            w  (ecs/put-components w eid
                 {c/matter-state :nebula c/mass (:mass region)
                  c/radius (:radius region) c/density (:density region)
                  c/temperature (:temperature region) c/position [0.0 0.0 0.0]})
            ws ((:run (stellar/accretion-zone-system)) w)
            zone (get-in ws [c/accretion-radius eid])]
        (is (some? zone) "a condensing parcel is given a feeding zone")
        (is (= zone (* stellar/feeding-zone-factor (:radius region)))
            "the zone is feeding-zone-factor × the GAS smoothing radius")))))

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
