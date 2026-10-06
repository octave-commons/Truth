(ns domain.rotation-test
  "Spark attitude contracts: observable state first, then integrated dynamics."
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.math :as math]
   [malli.core :as m]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.registry :as registry]
   [domain.ecs.tick :as tick]
   [domain.genesis.systems :as systems]
   [domain.integrator.base :as base]
   [domain.integrator.rotation :as rotation]
   [domain.player.state :as player]
   [law.rotation :as law]
   [shape.quaternion :as quaternion]
   [shape.spatial :as sp]))

(defn- close-vector?
  [expected actual]
  (and (= (count expected) (count actual))
       (every? #(< (abs %) 1.0e-10) (map - expected actual))))

(defn- spinning-world
  [omega]
  (let [[world eid] (player/spawn-observer (ecs/empty-world) [0 0 0])]
    [(ecs/put-component world eid c/angular-velocity omega) eid]))

(deftest rotation-contracts-reject-invalid-state
  (testing "only finite unit quaternions and complete finite angular vectors"
    (is (m/validate law/orientation-schema [1.0 0.0 0.0 0.0]))
    (doseq [q [[0 0 0 0] [2 0 0 0] [1 0 0] [1 0 0 ##NaN] [1 0 0 ##Inf]]]
      (is (not (m/validate law/orientation-schema q))))
    (is (m/validate law/angular-velocity-schema [0 0 0]))
    (doseq [omega [[0 0] [0 0 ##NaN] [0 0 ##Inf]]]
      (is (not (m/validate law/angular-velocity-schema omega))))
    (doseq [dt [-1 ##NaN ##Inf nil]]
      (is (not (law/rotation-input? {:orientation law/identity-orientation
                                     :angular-velocity [0 0 0] :dt dt}))))))

(deftest spark-spawns-with-complete-rotation-state
  (let [[world eid] (player/spawn-observer (ecs/empty-world) [0 0 0])]
    (is (= law/identity-orientation (ecs/get-component world eid :component/orientation)))
    (is (= [0.0 0.0 0.0] (ecs/get-component world eid :component/angular-velocity)))))

(deftest legacy-repair-restores-only-missing-rotation-columns
  (let [[world eid] (player/spawn-observer (ecs/empty-world) [0 0 0])
        legacy (-> world
                   (ecs/remove-component eid :component/orientation)
                   (ecs/remove-component eid :component/angular-velocity))
        repaired (player/repair-observer-columns legacy)]
    (is (= law/identity-orientation (ecs/get-component repaired eid :component/orientation)))
    (is (= [0.0 0.0 0.0] (ecs/get-component repaired eid :component/angular-velocity)))
    (is (= repaired (player/repair-observer-columns repaired)))
    (let [existing (-> repaired
                       (ecs/put-component eid :component/orientation [0.0 0.0 1.0 0.0])
                       (ecs/put-component eid :component/angular-velocity [0.0 0.25 0.0]))]
      (is (= existing (player/repair-observer-columns existing))))))

(deftest analytic-turns-have-the-declared-world-axis-convention
  (let [half-root (math/sqrt 0.5)]
    (is (close-vector? [half-root 0 0 half-root]
                       (quaternion/advance law/identity-orientation [0 0 (/ math/PI 2)] 1.0)))
    (testing "world Z quarter-turn after body X quarter-turn must premultiply"
      (is (close-vector? [0.5 0.5 0.5 0.5]
                         (quaternion/advance [half-root half-root 0 0]
                                             [0 0 (/ math/PI 2)] 1.0))))))

(deftest free-spin-is-independent-of-step-partition-and-normalized
  (let [omega [0.1 0.2 -0.3]
        one (quaternion/advance law/identity-orientation omega 1.0)
        two (-> law/identity-orientation
                (quaternion/advance omega 0.5)
                (quaternion/advance omega 0.5))
        many (nth (iterate #(quaternion/advance % omega 0.001)
                           law/identity-orientation) 1000)]
    (is (close-vector? one two))
    (is (close-vector? one many))
    (is (m/validate law/orientation-schema many))
    (is (= one (quaternion/advance one [0 0 0] 4.1e9)))
    (is (= one (quaternion/advance one omega 0.0)))))

(deftest physics-dt-is-used-at-fractional-and-cosmological-scales
  (doseq [dt [0.125 1.0 4.1e9]]
    (let [[world eid] (spinning-world [0 0 (/ 0.25 dt)])
          out (tick/run-sequential world [(rotation/rotation-integrator-system dt)])]
      (is (close-vector? [(math/cos 0.125) 0 0 (math/sin 0.125)]
                         (ecs/get-component out eid c/orientation))
          (str "same angular displacement with explicit simulation dt=" dt))
      (is (close-vector? (ecs/get-component world eid c/angular-velocity)
                         (ecs/get-component out eid c/angular-velocity))
          "free spin is not damped"))))

(deftest invalid-time-and-partial-state-fail-loudly
  (doseq [dt [nil -1 ##NaN ##Inf]]
    (is (thrown? clojure.lang.ExceptionInfo (rotation/rotation-integrator-system dt))))
  (let [[world eid] (spinning-world [0 0 1])
        run (:run (rotation/rotation-integrator-system 1.0))]
    (doseq [component [c/orientation c/angular-velocity]]
      (is (thrown? clojure.lang.ExceptionInfo
                   (run (ecs/remove-component world eid component)))))))

(deftest angular-limit-preserves-axis-without-touching-other-state
  (let [[world eid] (spinning-world [0 0 20])
        out (tick/run-sequential world [(rotation/rotation-integrator-system 0.25)])]
    (is (close-vector? [0 0 law/max-angular-speed]
                       (ecs/get-component out eid c/angular-velocity)))
    (is (= (ecs/get-component world eid c/position) (ecs/get-component out eid c/position)))
    (is (= (ecs/get-component world eid c/velocity) (ecs/get-component out eid c/velocity)))
    (is (= (ecs/get-component world eid c/mass) (ecs/get-component out eid c/mass)))))

(deftest torque-sum-obeys-jacobi-lag-then-free-coast
  (let [source-a :component/torque.test-a
        source-b :component/torque.test-b
        [world eid] (spinning-world [0 0 0])
        emitter (fn [torque] {:id :test-torque
                              :writes #{source-a source-b}
                              :run (fn [_] {source-a {eid torque} source-b {eid torque}})})]
    (with-redefs [base/influence-registry
                  (assoc-in base/influence-registry [:angular-velocity :accumulate]
                            [source-a source-b])]
      (let [system (rotation/rotation-integrator-system 0.25)
            pulse [system (emitter [0 0 1])]
            coast [system (emitter [0 0 0])]
            first-step (tick/run-parallel world pulse)
            second-step (tick/run-parallel first-step coast)
            third-step (tick/run-parallel second-step coast)]
        (is (= #{c/orientation c/angular-velocity source-a source-b} (:reads system)))
        (is (= first-step (tick/run-sequential world pulse)))
        (is (= law/identity-orientation (ecs/get-component first-step eid c/orientation))
            "a torque emitted in this fan-out cannot affect the same snapshot")
        (is (close-vector? [0 0 0.5] (ecs/get-component second-step eid c/angular-velocity)))
        (is (= (ecs/get-component second-step eid c/angular-velocity)
               (ecs/get-component third-step eid c/angular-velocity)))))))

(deftest rotational-state-has-one-writer-in-the-live-physics-table
  (let [system (rotation/rotation-integrator-system 0.5)
        entries (filter #(= :rotation-integrator (:id %))
                        (systems/physics-systems-parallel {:sim/G 0.0 :sim/theta 0.5 :sim/dt 0.5}))
        declaration (first (filter #(= :rotation-integrator (:id %)) registry/systems))]
    (is (= 1 (count entries)))
    (is (= #{c/orientation c/angular-velocity} (:writes system) (:writes declaration)))
    (is (= (:reads system) (:reads declaration)))
    (is (= {} (registry/write-conflicts registry/systems)))
    (is (= {c/orientation {} c/angular-velocity {}}
           ((:run system) (ecs/empty-world))))))

(deftest live-physics-entry-preserves-many-steps-of-free-spin
  (let [[world eid] (spinning-world [0.1 0.2 0.3])
        system (first (filter #(= :rotation-integrator (:id %))
                              (systems/physics-systems-parallel
                               {:sim/G 0.0 :sim/theta 0.5 :sim/dt 0.1})))
        out (nth (iterate #(tick/run-sequential % [system]) world) 1000)]
    (is (= [0.1 0.2 0.3] (ecs/get-component out eid c/angular-velocity)))
    (is (m/validate law/orientation-schema (ecs/get-component out eid c/orientation)))
    (is (< (sp/len (ecs/get-component out eid c/angular-velocity)) law/max-angular-speed))))

(deftest extreme-finite-inputs-do-not-collapse-the-spin-axis
  (let [[world eid] (spinning-world [1.0e300 1.0e300 0.0])
        out (tick/run-sequential world [(rotation/rotation-integrator-system Double/MAX_VALUE)])
        omega (ecs/get-component out eid c/angular-velocity)]
    (is (close-vector? [(/ law/max-angular-speed (math/sqrt 2.0))
                        (/ law/max-angular-speed (math/sqrt 2.0)) 0]
                       omega))
    (is (m/validate law/orientation-schema (ecs/get-component out eid c/orientation))))
  (is (m/validate law/orientation-schema
                  (quaternion/advance law/identity-orientation [0 0 1.0e-300] 1.0e300))))
