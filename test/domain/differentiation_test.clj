(ns domain.differentiation-test
  "μ for differentiation + the volatile budget (chemistry spec §5-§7 Phase 3-4):
   molten bodies gain core/mantle/volatile layer fractions over sustained ticks,
   cold bodies stay undifferentiated, layers always partition the body mass
   exactly, the volatile budget is derived from the element composition in kg,
   and hot merges drive off volatiles while cold ones just blend."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core        :as ecs]
   [domain.ecs.components   :as c]
   [domain.ecs.registry     :as reg]
   [domain.ecs.tick         :as tick]
   [domain.chemistry        :as chem]
   [domain.integrator       :as integ]
   [law.chemistry           :as lchem]
   [law.stellar             :as law]))

(def ^:private rocky-comp
  "A differentiated-terrestrial composition: silicate mantle + metal core +
   some free oxygen/water. Sums to 1.0."
  {:Si 0.3 :O 0.45 :Fe 0.2 :Mg 0.05})

(def ^:private volatile-rich-comp
  "A gas-giant-ish composition dominated by H/He. Sums to 1.0."
  {:H 0.7 :He 0.25 :C 0.02 :O 0.02 :Si 0.005 :Fe 0.005})

(defn- spawn-body
  "Spawn one body with the given matter-state/temperature/mass/composition."
  [world {:keys [state temp mass composition velocity]
          :or   {state :planetesimal temp 300.0 mass 1.0e24
                 composition rocky-comp velocity [0.0 0.0 0.0]}}]
  (let [[w eid] (ecs/spawn world)]
    [(ecs/put-components w eid
                         {c/matter-state state c/temperature temp c/mass mass
                          c/composition composition c/velocity velocity})
     eid]))

(defn- differentiate-tick
  "One differentiation tick through the fan-out discipline: run the emitter on
   the frozen world, fold its write-set."
  [dt world]
  (tick/apply-write-set world ((:run (chem/differentiation-system dt)) world)))

;; --- single-writer / declared reads+writes -----------------------------------

(deftest differentiation-single-writer-and-declared-io
  (testing "the differentiation system is the sole writer of both new components"
    (is (= [:differentiation]
           (get (reg/writers-by-component (reg/fan-out-systems reg/systems))
                c/differentiated-layers)))
    (is (= [:differentiation]
           (get (reg/writers-by-component (reg/fan-out-systems reg/systems))
                c/volatile-budget))))
  (testing "adding it introduced no single-writer conflict"
    (is (empty? (reg/write-conflicts reg/systems))))
  (testing "it never writes matter-state (classification is the classifier's)"
    (let [writes (reg/registry-writes :differentiation)]
      (is (not (contains? writes c/matter-state)))))
  (testing "everything it reads is declared"
    (let [reads (get (some #(when (= :differentiation (:id %)) %) reg/systems) :reads)]
      (is (every? #(contains? reads %)
                  [c/matter-state c/composition c/mass c/temperature
                   c/differentiated-layers])))))

;; --- differentiation behaviour (spec §7 Phase 3) ------------------------------

(deftest molten-body-differentiates
  (testing "a body above the molten malleability threshold gains layer fractions over time"
    (let [[w eid] (spawn-body (ecs/empty-world) {:temp 2000.0})
          worlds  (take 4 (iterate #(differentiate-tick 1.0e13 %) w))
          degrees (map #(some-> (ecs/get-component % eid c/differentiated-layers)
                                :degree)
                       worlds)]
      (is (nil? (first degrees)) "no layers before the first tick")
      (is (every? some? (rest degrees)) "layers appear once molten")
      (is (apply < (rest degrees)) "degree strictly increases while molten")
      (let [layers (ecs/get-component (last worlds) eid c/differentiated-layers)]
        (is (= 1.0 (:degree layers)) "fully differentiated after a few Myr-scale ticks")
        (is (pos? (:core-fraction layers)) "metal core fraction present")
        (is (< (abs (- (:core-fraction layers) 0.2)) 1.0e-9)
            "core fraction is the Fe+Ni share of the composition")
        (is (lchem/differentiated-layers? layers)
            "satisfies law.chemistry/differentiated-layers-schema")))))

(deftest cold-body-stays-undifferentiated
  (testing "a body below the threshold never gains layers, but keeps a volatile budget"
    (let [[w eid] (spawn-body (ecs/empty-world) {:temp 500.0})
          w'      (nth (iterate #(differentiate-tick 1.0e13 %) w) 5)]
      (is (nil? (ecs/get-component w' eid c/differentiated-layers))
          "cold body retains uniform composition — no layer component")
      (is (some? (ecs/get-component w' eid c/volatile-budget))
          "volatile budget is maintained for cold future planets too"))))

(deftest differentiation-conserves-mass
  (testing "layer fractions always sum to 1.0 — total layer mass equals body mass"
    (doseq [composition [rocky-comp volatile-rich-comp
                         {:Fe 0.9 :Ni 0.1} {:H 0.75 :He 0.25}]]
      (let [layers (chem/differentiate-layers composition nil 1.0e13)
            total  (+ (:core-fraction layers)
                      (:mantle-fraction layers)
                      (:volatile-fraction layers))]
        (is (< (abs (- 1.0 total)) 1.0e-9)
            (str "mass conserved for " composition))))))

;; --- volatile budget (spec §7 Phase 4) ----------------------------------------

(deftest volatile-budget-from-composition
  (testing "a high H/He/organics composition has a large volatile budget"
    (let [gas-rich (chem/volatile-budget volatile-rich-comp 1.0e27)
          rocky    (chem/volatile-budget rocky-comp 1.0e27)]
      (is (> gas-rich (* 0.9 1.0e27)) "H/He-dominated body is almost all volatile")
      (is (< rocky (* 0.2 1.0e27)) "rocky body carries a small volatile fraction")
      (is (> gas-rich (* 5.0 rocky)) "orders of magnitude apart"))
    (testing "oxygen bound into silicate rock is NOT volatile; free oxygen is"
      (let [bound (chem/volatile-fraction {:Si 0.3 :O 0.3 :Fe 0.4})
            free  (chem/volatile-fraction {:Si 0.1 :O 0.5 :Fe 0.4})]
        (is (< bound free) "more oxygen than silicon can bind reads as water/volatiles")))
    (testing "budget scales with mass and satisfies the law schema"
      (is (lchem/volatile-budget? (chem/volatile-budget rocky-comp 1.0e24)))
      (is (< (abs (- (chem/volatile-budget rocky-comp 2.0e24)
                     (* 2.0 (chem/volatile-budget rocky-comp 1.0e24))))
             1.0e-9)))))

(deftest volatile-budget-ticked-on-every-body
  (testing "the system refreshes c/volatile-budget in kg from composition × mass"
    (let [[w eid] (spawn-body (ecs/empty-world)
                              {:composition volatile-rich-comp :mass 2.0e24})
          w'      (differentiate-tick 1.0e13 w)]
      (is (< (abs (- (ecs/get-component w' eid c/volatile-budget)
                     (chem/volatile-budget volatile-rich-comp 2.0e24)))
             1.0e15)))))

;; --- volatile loss on hot merges (spec §7 Phase 4) -----------------------------

(deftest volatiles-lost-in-hot-collision
  (let [cmp {:H 0.5 :He 0.3 :C 0.02 :O 0.05 :Si 0.08 :Fe 0.05}
        [w eid] (spawn-body (ecs/empty-world) {:composition cmp :mass 1.0e24})
        ;; impactor at 50 km/s → impact heating lifts the survivor past both
        ;; blow-off thresholds (~5000 K post-merge)
        pkt  {:mass 1.0e23 :velocity [5.0e4 0.0 0.0] :temperature 300.0
              :composition cmp}
        w    (ecs/put-component w eid c/absorb-merge [pkt])
        comp-ws (integ/composition-ws w)
        mass-ws (integ/mass-ws w)
        merged (get-in comp-ws [c/composition eid])
        m1     (get-in mass-ws [c/mass eid])]
    (testing "H/He and the ice-volatile inventory are driven off"
      (is (< (double (:H merged 0.0)) 1.0e-9))
      (is (< (double (:He merged 0.0)) 1.0e-9))
      (is (< (double (:C merged 0.0)) 1.0e-9)))
    (testing "rock-bound oxygen and metals stay"
      (is (pos? (double (:Si merged))))
      (is (pos? (double (:Fe merged))))
      (is (pos? (double (:O merged))) "silicate-bound oxygen survives"))
    (testing "the surviving composition is renormalized"
      (is (< (abs (- 1.0 (reduce + (vals merged)))) 1.0e-9)))
    (testing "the escaped volatile mass is debited from the survivor"
      (let [naive-merge-mass 1.1e24]
        (is (< m1 naive-merge-mass) "mass loss beyond a plain inelastic merge")
        (is (< (abs (- m1 (* naive-merge-mass
                             (- 1.0 (:lost-fraction (chem/strip-volatiles cmp 5000.0))))))
               (* 1.0e-6 naive-merge-mass))
            "lost kg = lost-fraction × total merged mass")))
    (testing "the volatile budget of the merged body collapses"
      (is (< (chem/volatile-budget merged m1)
             (* 0.05 (chem/volatile-budget cmp 1.1e24)))))))

(deftest gentle-collision-keeps-volatiles
  (let [comp-a {:H 0.5 :He 0.3 :C 0.02 :O 0.05 :Si 0.08 :Fe 0.05}
        comp-b {:Fe 0.6 :Si 0.3 :O 0.1}
        [w eid] (spawn-body (ecs/empty-world) {:composition comp-a :mass 1.0e24})
        ;; 1 km/s → a couple of kelvin of impact heating: below every threshold
        pkt  {:mass 1.0e23 :velocity [1.0e3 0.0 0.0] :temperature 300.0
              :composition comp-b}
        w    (ecs/put-component w eid c/absorb-merge [pkt])
        merged (get-in (integ/composition-ws w) [c/composition eid])
        m1     (get-in (integ/mass-ws w) [c/mass eid])
        plain  (chem/blend-compositions comp-a 1.0e24 comp-b 1.0e23)]
    (testing "a cold/gentle merge is the plain mass-weighted blend — nothing stripped"
      (is (< (abs (- (double (:H merged)) (double (:H plain)))) 1.0e-12))
      (is (< (abs (- (double (:Fe merged)) (double (:Fe plain)))) 1.0e-12)))
    (testing "and mass is the full inelastic-merge sum"
      (is (< (abs (- m1 1.1e24)) 1.0e18)))))

;; --- pure-helper edge cases -----------------------------------------------------

(deftest strip-volatiles-thresholds
  (testing "below both thresholds nothing is lost"
    (is (= {:composition rocky-comp :lost-fraction 0.0}
           (chem/strip-volatiles rocky-comp 300.0))))
  (testing "above the ice threshold only the ice-volatile inventory leaves"
    (let [{:keys [composition lost-fraction]}
          (chem/strip-volatiles {:H 0.5 :He 0.3 :C 0.05 :O 0.05 :Fe 0.1}
                                (inc lchem/ice-volatile-loss-temperature))]
      (is (pos? (double (:H composition))) "H/He survives a merely hot merge")
      (is (nil? (:C composition)) "organics are driven off")
      (is (pos? lost-fraction)))))

(deftest malleability-threshold-agrees-with-law
  (testing "differentiation-malleability-min is the spec §3 molten band"
    (is (> (law/malleability 2000.0) lchem/differentiation-malleability-min))
    (is (< (law/malleability 500.0) lchem/differentiation-malleability-min))))
