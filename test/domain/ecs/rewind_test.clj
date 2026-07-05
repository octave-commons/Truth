(ns domain.ecs.rewind-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.event :as evt]
   [domain.ecs.dsl :refer [defevent defreaction defrewind]]
   [domain.ecs.ledger :as ledger]))

(defevent healed
  "Entity received healing."
  [:map [:amount :int]]
  {:entity-count 1
   :reversible?  true})

(defevent permanent-death
  "Entity permanently removed."
  [:map [:cause :keyword]]
  {:entity-count 1
   :reversible?  false})

(defreaction apply-heal
  "Increment hp by heal amount."
  healed
  [world event]
  (let [eid    (first (:entities event))
        amount (get-in event [:payload :amount])]
    (update-in world [:components :hp eid] (fnil + 0) amount)))

(defrewind undo-heal
  "Decrement hp by the same amount to reverse the heal."
  healed
  [world event]
  (let [eid    (first (:entities event))
        amount (get-in event [:payload :amount])]
    (update-in world [:components :hp eid] - amount)))

(deftest rewind-reverses-heal
  (testing "rewind undoes a reversible event"
    (let [[world e1] (ecs/spawn (-> (ecs/empty-world) evt/with-ledger))
          world      (ecs/put-component world e1 :hp 50)
          world      (evt/install-reaction world #'apply-heal)
          world      (evt/install-rewind   world #'undo-heal)
          world      (evt/dispatch world (->healed 1 #{e1} {:amount 30}))
          _          (is (= 80 (ecs/get-component world e1 :hp)))
          world'     (ledger/rewind world 1)]
      (is (= 50 (ecs/get-component world' e1 :hp))))))

(deftest rewind-refuses-irreversible-event
  (testing "rewind throws when event is not reversible"
    (let [[world e1] (ecs/spawn (-> (ecs/empty-world) evt/with-ledger))
          world      (evt/dispatch world (->permanent-death 1 #{e1} {:cause :old-age}))]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"not reversible"
           (ledger/rewind world 1))))))

(deftest rewind-n-ticks
  (testing "rewind N steps undoes N events in reverse order"
    (let [[world e1] (ecs/spawn (-> (ecs/empty-world) evt/with-ledger))
          world      (ecs/put-component world e1 :hp 0)
          world      (evt/install-reaction world #'apply-heal)
          world      (evt/install-rewind   world #'undo-heal)
          world      (evt/dispatch world (->healed 1 #{e1} {:amount 10}))
          world      (evt/dispatch world (->healed 2 #{e1} {:amount 10}))
          world      (evt/dispatch world (->healed 3 #{e1} {:amount 10}))
          world'     (ledger/rewind world 2)]
      (is (= 10 (ecs/get-component world' e1 :hp))))))
