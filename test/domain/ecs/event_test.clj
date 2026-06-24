(ns domain.ecs.event-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [domain.ecs.core :as ecs]
    [domain.ecs.event :as evt]))

(deftest event-construction
  (testing "Events require tick, kind, and entities set"
    (let [e (evt/->event {:tick 5 :kind :event/boom :entities #{1 2}})]
      (is (= 5 (:tick e)))
      (is (= :event/boom (:kind e)))
      (is (= #{1 2} (:entities e)))
      (is (uuid? (:id e))))))

(deftest dispatch-appends-and-handles
  (testing "dispatch appends event and runs handler"
    (let [[w eid] (ecs/spawn (-> (ecs/empty-world)
                                  evt/with-ledger
                                  evt/with-handlers))
          w       (evt/register-handler w :event/boom
                                        (fn [w event]
                                          (ecs/put-component w eid :boomed true)))
          w'      (evt/dispatch w (evt/->event {:tick 0
                                                 :kind :event/boom
                                                 :entities #{eid}}))]
      (is (= true (ecs/get-component w' eid :boomed)))
      (is (= 1 (count (get-in w' [:ledger :events])))))))

(deftest events-of-kind-filter
  (testing "events-of-kind returns only matching events"
    (let [w (-> (ecs/empty-world)
                 evt/with-ledger)
          w (evt/emit w (evt/->event {:tick 1 :kind :a :entities #{1}}))
          w (evt/emit w (evt/->event {:tick 2 :kind :b :entities #{1}}))
          w (evt/emit w (evt/->event {:tick 3 :kind :a :entities #{1}}))]
      (is (= 2 (count (evt/events-of-kind w :a))))
      (is (= 1 (count (evt/events-of-kind w :b)))))))
