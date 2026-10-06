(ns domain.rotation-test
  "Spark attitude contracts: observable state first, then integrated dynamics."
  (:require
   [clojure.test :refer [deftest is testing]]
   [malli.core :as m]
   [domain.ecs.core :as ecs]
   [domain.player.state :as player]
   [law.rotation :as law]))

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
