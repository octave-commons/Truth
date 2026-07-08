(ns domain.chemistry-system-test
  "μ for the live nucleosynthesis system: composition must actually evolve in the
   tick (stars burn H→He), conserve mass-fraction, and stay bounded under the
   Myr-scale dilating timestep. See kanban/tasks/phase-0-chemistry-differentiation-spec.md
   and docs/research/cosmology/primordial-nucleosynthesis-yields.md."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core         :as ecs]
   [domain.ecs.components    :as c]
   [domain.ecs.registry      :as reg]
   [domain.ecs.tick          :as tick]
   [domain.chemistry         :as chem]
   [domain.integrator        :as integ]
   [law.stellar              :as law]))

(def ^:private solar law/solar-mass)
(def ^:private primordial {:H 0.753 :He 0.247 :metals 0.0})

(defn- spawn-body
  "Spawn one body with the given matter-state/temperature/mass/composition."
  [world {:keys [state temp mass composition]
          :or   {state :star temp 1.5e7 mass solar composition primordial}}]
  (let [[w eid] (ecs/spawn world)]
    [(ecs/put-components w eid
                         {c/matter-state state c/temperature temp c/mass mass c/composition composition})
     eid]))

(defn- comp-of [world eid] (ecs/get-component world eid c/composition))
(defn- sum [composition] (reduce + (vals composition)))

(defn- burn-tick
  "One composition tick through the unified pipeline: the nucleosynthesis emitter
   writes comp.burn, then the integrator (sole owner of composition) folds the
   burn (and deuterium gate) into composition — exactly as the fan-out + fold
   does at runtime."
  [dt world]
  (let [w1 (tick/apply-write-set world ((:run (chem/nucleosynthesis-system dt)) world))]
    (tick/apply-write-set w1 (integ/composition-ws w1))))

;; --- single-writer guard ----------------------------------------------------

(deftest composition-has-exactly-one-fan-out-writer
  (testing "the integrator is the sole declared writer of :component/composition"
    (is (= [:integrator]
           (get (reg/writers-by-component (reg/fan-out-systems reg/systems))
                c/composition))))
  (testing "nucleosynthesis is the sole writer of the comp.burn influence"
    (is (= [:nucleosynthesis]
           (get (reg/writers-by-component (reg/fan-out-systems reg/systems))
                c/comp-burn))))
  (testing "adding it introduced no single-writer conflict"
    (is (empty? (reg/write-conflicts reg/systems)))))

;; --- burning behaviour ------------------------------------------------------

(deftest burning-star-h-monotonically-decreases
  (let [[w eid] (spawn-body (ecs/empty-world) {})
        hs      (reductions (fn [world _] (burn-tick 1.0e14 world)) w (range 5))
        h-vals  (map #(:H (comp-of % eid)) hs)]
    (testing "hydrogen strictly decreases each tick"
      (is (apply > h-vals)))
    (testing "and never goes below the seed"
      (is (< (last h-vals) (:H primordial))))))

(deftest he-gains-exactly-what-h-loses
  (let [[w eid] (spawn-body (ecs/empty-world) {})
        w'      (burn-tick 1.0e14 w)
        before  (comp-of w eid)
        after   (comp-of w' eid)]
    (is (< (abs (+ (- (:He after) (:He before))
                   (- (:H after) (:H before))))
           1.0e-12)
        "ΔHe == −ΔH")))

(deftest composition-stays-normalized
  (let [[w eid] (spawn-body (ecs/empty-world) {})]
    (doseq [world (take 10 (iterate #(burn-tick 1.0e14 %) w))]
      (is (< (abs (- 1.0 (sum (comp-of world eid)))) 1.0e-9)))))

(deftest metals-unchanged-by-fusion
  (let [[w eid] (spawn-body (ecs/empty-world) {})
        after   (comp-of (burn-tick 1.0e14 w) eid)]
    (is (= 0.0 (:metals after)) "H→He burn leaves metals alone")))

;; --- who burns and who does not ---------------------------------------------

(deftest nebula-composition-unchanged
  (let [[w eid] (spawn-body (ecs/empty-world) {:state :nebula :temp 15.0})
        after   (comp-of (burn-tick 1.0e14 w) eid)]
    (is (= primordial after) "cold nebula gas does not fuse")))

(deftest cold-protostar-below-ignition-does-not-burn
  (let [[w eid] (spawn-body (ecs/empty-world) {:state :protostar :temp 5.0e6})
        after   (comp-of (burn-tick 1.0e14 w) eid)]
    (is (= primordial after) "a protostar below fusion-temp-threshold is inert")))

;; --- the large-dt hazard ----------------------------------------------------

(deftest burn-bounded-under-huge-dt
  (testing "even with dt ≫ τ_MS, at most max-burn-fraction of current H burns"
    (let [composition  primordial
          after (chem/burn-step composition solar 1.0e30)] ;; dt vastly exceeds any τ_MS
      (is (>= (:H after) (* (:H composition) (- 1.0 0.0100001)))
          "H drops by at most ~1% of current value in one step")
      (is (pos? (:H after)) "H never lurches negative"))))

(deftest h-floored-at-zero-over-many-ticks
  (let [[w eid] (spawn-body (ecs/empty-world) {:mass (* 30 solar)}) ;; fast burner
        final   (nth (iterate #(burn-tick 1.0e16 %) w) 500)]
    (is (>= (:H (comp-of final eid)) 0.0) "H stays non-negative across 500 ticks")
    (is (< (abs (- 1.0 (sum (comp-of final eid)))) 1.0e-9)
        "composition still normalized after heavy burning")))
