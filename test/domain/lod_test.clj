(ns domain.lod-test
  "Tests for observer-centric LOD scheduling and its integration with the
   integrator."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.player :as player]
   [domain.lod :as lod]
   [domain.integrator.base :as base]
   [domain.integrator.kinematics :as kinematics]
   [domain.stellar :as stellar]))

(defn- world-with-star-planet-observer
  "A world with a star at the origin, a planet at 1e12 m, and an observer at the
   origin."
  []
  (let [[w star] (stellar/spawn-clump (ecs/empty-world)
                                      {:position [0.0 0.0 0.0]
                                       :mass 2e30 :radius 6.957e8
                                       :matter-state :star
                                       :temperature 5800.0})
        [w planet] (stellar/spawn-clump w
                                        {:position [1.0e12 0.0 0.0]
                                         :mass 6e24 :radius 6.4e6
                                         :matter-state :planet
                                         :temperature 300.0})
        [w obs] (player/spawn-observer w [0.0 0.0 0.0])]
    (assoc w :next-id 10 :tick 0)))

(deftest lod-scheduler-writes-tick-phase-on-level-change
  (testing "When a body changes LOD level, the scheduler also writes a tick phase"
    (let [w (-> (world-with-star-planet-observer)
                (assoc :tick 5))
          sys (lod/lod-scheduler)
          ws ((:run sys) w)]
      (is (contains? ws c/lod-level))
      (is (contains? ws c/lod-tick-phase))
      (is (every? (fn [[eid phase]]
                    (and (#{:local :system :galaxy} (:level phase))
                         (#{1 2 4} (:period phase))
                         (= 5 (:phase phase))))
                  (get ws c/lod-tick-phase)))))
  (testing "When the level is unchanged, no tick-phase write is emitted"
    (let [w (-> (world-with-star-planet-observer)
                (assoc :tick 5)
                (ecs/put-component 0 c/lod-level :local)
                (ecs/put-component 1 c/lod-level :local))
          sys (lod/lod-scheduler)
          ws ((:run sys) w)]
      (is (empty? ws))))
  (testing "Without an observer the scheduler emits nothing"
    (let [w (ecs/empty-world)
          sys (lod/lod-scheduler)]
      (is (empty? ((:run sys) w))))))

(deftest integrator-due-entity-filter
  (testing "Entities without a tick-phase are always due"
    (let [w (ecs/empty-world)]
      (is (base/due-entity? w 0 999))))
  (testing "An entity is due exactly when (tick - phase) mod period == 0"
    (let [w (-> (ecs/empty-world)
                (ecs/put-component 0 c/lod-tick-phase {:level :system :period 2 :phase 3}))]
      (is (base/due-entity? w 3 0))
      (is (base/due-entity? w 5 0))
      (is (not (base/due-entity? w 4 0)))))
  (testing "When throttling is disabled, every entity is due regardless of phase"
    (let [w (-> (ecs/empty-world)
                (assoc :lod/throttle-ticks? false)
                (ecs/put-component 0 c/lod-tick-phase {:level :galaxy :period 4 :phase 0}))]
      (is (base/due-entity? w 1 0))))
  (testing "When throttling is enabled, galaxy entities skip most ticks"
    (let [w (-> (ecs/empty-world)
                (assoc :lod/throttle-ticks? true :tick 1)
                (ecs/put-component 0 c/lod-tick-phase {:level :galaxy :period 4 :phase 0}))]
      (is (not (base/due-entity? w 1 0))))))

(deftest kinematics-ws-skips-non-due-entities
  (testing "With throttling enabled, only due entities advance in position"
    (let [w (-> (ecs/empty-world)
                (assoc :lod/throttle-ticks? true :tick 0)
                (ecs/put-component 0 c/position [0.0 0.0 0.0])
                (ecs/put-component 0 c/velocity [1.0 0.0 0.0])
                (ecs/put-component 0 c/mass 1.0)
                (ecs/put-component 0 c/radius 1.0)
                (ecs/put-component 0 c/body-kind :body/test)
                (ecs/put-component 0 c/lod-tick-phase {:level :galaxy :period 2 :phase 1})
                (ecs/put-component 1 c/position [0.0 0.0 0.0])
                (ecs/put-component 1 c/velocity [1.0 0.0 0.0])
                (ecs/put-component 1 c/mass 1.0)
                (ecs/put-component 1 c/radius 1.0)
                (ecs/put-component 1 c/body-kind :body/test)
                (ecs/put-component 1 c/lod-tick-phase {:level :local :period 1 :phase 0}))
          ws (kinematics/kinematics-ws w 1.0)]
      (is (contains? (get ws c/position) 1) "local entity advances")
      (is (not (contains? (get ws c/position) 0)) "galaxy entity is skipped at tick 0")))
  (testing "With throttling disabled, every entity advances"
    (let [w (-> (ecs/empty-world)
                (assoc :lod/throttle-ticks? false :tick 0)
                (ecs/put-component 0 c/position [0.0 0.0 0.0])
                (ecs/put-component 0 c/velocity [1.0 0.0 0.0])
                (ecs/put-component 0 c/mass 1.0)
                (ecs/put-component 0 c/radius 1.0)
                (ecs/put-component 0 c/body-kind :body/test)
                (ecs/put-component 0 c/lod-tick-phase {:level :galaxy :period 2 :phase 1})
                (ecs/put-component 1 c/position [0.0 0.0 0.0])
                (ecs/put-component 1 c/velocity [1.0 0.0 0.0])
                (ecs/put-component 1 c/mass 1.0)
                (ecs/put-component 1 c/radius 1.0)
                (ecs/put-component 1 c/body-kind :body/test)
                (ecs/put-component 1 c/lod-tick-phase {:level :local :period 1 :phase 0}))
          ws (kinematics/kinematics-ws w 1.0)]
      (is (contains? (get ws c/position) 0))
      (is (contains? (get ws c/position) 1)))))
