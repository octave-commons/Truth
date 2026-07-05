(ns domain.ecs.dsl-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.event :as evt]
   [domain.ecs.dsl :refer [defcomponent defevent defsystem defreaction
                           install-reaction]]))

(defcomponent position
  "Cartesian position."
  [:vector {:min 3 :max 3} :double])

(defcomponent velocity
  "Cartesian velocity."
  [:vector {:min 3 :max 3} :double])

(defevent collision
  "Discrete collision event between two entities."
  [:map
   [:normal [:vector {:min 3 :max 3} :double]]
   [:depth :double]]
  {:entity-count 2
   :reversible? true})

(defreaction mark-collided
  "Marks all participating entities as collided."
  collision
  [world event]
  (reduce (fn [w eid]
            (ecs/put-component w eid :component/collided? true))
          world
          (:entities event)))

(defsystem zero-velocity
  "Set queried velocities to zero."
  {:query [velocity]}
  [world rows]
  (reduce (fn [w [eid _components]]
            (ecs/put-component w eid velocity [0.0 0.0 0.0]))
          world
          rows))

(deftest component-dsl-defines-key-and-validator
  (testing "defcomponent creates a keyword var and schema validator"
    (is (= :component/position position))
    (is (position? [1.0 2.0 3.0]))
    (is (not (position? [1.0 2.0])))))

(deftest event-dsl-defines-constructor
  (testing "defevent creates a checked constructor"
    (let [event (->collision 7 #{:a :b} {:normal [1.0 0.0 0.0]
                                         :depth  0.25})]
      (is (= :event/collision (:kind event)))
      (is (= 7 (:tick event)))
      (is (= #{:a :b} (:entities event)))
      (is (= 0.25 (get-in event [:payload :depth]))))))

(deftest system-dsl-queries-by-component
  (testing "defsystem receives only matching rows"
    (let [[world e1] (ecs/spawn (ecs/empty-world))
          [world e2] (ecs/spawn world)
          world      (ecs/put-component world e1 velocity [4.0 5.0 6.0])
          world'     (zero-velocity world)]
      (is (= [0.0 0.0 0.0] (ecs/get-component world' e1 velocity)))
      (is (nil? (ecs/get-component world' e2 velocity))))))

(deftest reaction-dsl-installs-on-ledger-path
  (testing "reaction is installable from var metadata"
    (let [[world e1] (ecs/spawn (-> (ecs/empty-world)
                                    evt/with-ledger
                                    evt/with-handlers))
          [world e2] (ecs/spawn world)
          world      (install-reaction world #'mark-collided)
          world'     (evt/dispatch world
                                   (->collision 0 #{e1 e2}
                                                {:normal [1.0 0.0 0.0]
                                                 :depth 0.5}))]
      (is (true? (ecs/get-component world' e1 :component/collided?)))
      (is (true? (ecs/get-component world' e2 :component/collided?)))
      (is (= 1 (count (get-in world' [:ledger :events])))))))
