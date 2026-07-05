(ns domain.ecs.ledger-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.event :as evt]
   [domain.ecs.dsl :refer [defcomponent defevent
                           defprojection defaggregate]]
   [domain.ecs.ledger :as ledger]))

(defcomponent hp     "Hit points."  :int)
(defcomponent alive? "Alive flag."  :boolean)

(defevent took-damage
  "Entity received damage."
  [:map [:amount :int]]
  {:entity-count 1})

(defevent died
  "Entity transitioned to dead."
  [:map [:cause :keyword]]
  {:entity-count 1})

(defprojection total-damage-dealt
  "Running sum of damage dealt across all took-damage events."
  took-damage
  {:init 0}
  [acc event]
  (+ acc (get-in event [:payload :amount])))

(defaggregate damage-log
  "Per-entity damage history."
  {:tracked [took-damage died]
   :init    (fn [] {})}
  [acc event]
  (let [eid (first (:entities event))]
    (update acc eid (fnil conj []) event)))

(deftest projection-folds-events
  (testing "projection accumulates incrementally"
    (let [world  (-> (ecs/empty-world)
                     evt/with-ledger)
          [world e1] (ecs/spawn world)
          world  (evt/dispatch world (->took-damage 1 #{e1} {:amount 30}))
          world  (evt/dispatch world (->took-damage 2 #{e1} {:amount 20}))
          result (ledger/project world #'total-damage-dealt)]
      (is (= 50 result)))))

(deftest projection-skips-non-matching-events
  (testing "projection only folds its declared event-kind"
    (let [world      (-> (ecs/empty-world) evt/with-ledger)
          [world e1] (ecs/spawn world)
          world      (evt/dispatch world (->took-damage 1 #{e1} {:amount 10}))
          world      (evt/dispatch world (->died 2 #{e1} {:cause :poison}))
          world      (evt/dispatch world (->took-damage 3 #{e1} {:amount 5}))
          result     (ledger/project world #'total-damage-dealt)]
      (is (= 15 result)))))

(deftest aggregate-snapshots-and-resumes
  (testing "checkpoint then replay from cursor is identical to full fold"
    (let [world      (-> (ecs/empty-world) evt/with-ledger)
          [world e1] (ecs/spawn world)
          world      (evt/dispatch world (->took-damage 1 #{e1} {:amount 10}))
          world      (evt/dispatch world (->took-damage 2 #{e1} {:amount 20}))
          snap       (ledger/checkpoint world #'damage-log)
          world      (evt/dispatch world (->took-damage 3 #{e1} {:amount 5}))
          resumed    (ledger/resume world snap #'damage-log)
          full       (ledger/project-all world #'damage-log)]
      (is (= full resumed))
      (is (= 3 (count (get resumed e1)))))))

(deftest aggregate-fast-forward
  (testing "fast-forward accumulates delta ticks only"
    (let [world      (-> (ecs/empty-world) evt/with-ledger)
          [world e1] (ecs/spawn world)
          world      (evt/dispatch world (->took-damage 1 #{e1} {:amount 5}))
          snap       (ledger/checkpoint world #'damage-log)
          world      (evt/dispatch world (->took-damage 2 #{e1} {:amount 5}))
          world      (evt/dispatch world (->took-damage 3 #{e1} {:amount 5}))
          world      (evt/dispatch world (->took-damage 4 #{e1} {:amount 5}))
          resumed    (ledger/resume world snap #'damage-log)]
      (is (= 4 (count (get resumed e1)))))))
