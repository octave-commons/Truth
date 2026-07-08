(ns domain.ecs.timeline-test
  "Coverage tests for the rewindable Timeline implementation."
  (:require
   [clojure.test :refer [deftest is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.event :as event]
   [domain.ecs.rewindable :refer [current-tick restore seek step-backward step-forward]]
   [domain.ecs.timeline :as timeline]
   [law.ledger :as ledger]))

(defn- counter-world
  "World with a single entity carrying a :counter component."
  []
  (let [[w e] (ecs/spawn (-> (ecs/empty-world)
                             (event/with-ledger)))]
    [(ecs/put-component w e :counter 0) e]))

(defn- inc-system
  "Forward system: increment every :counter by 1."
  [world]
  (update-in world [:components :counter]
             #(reduce-kv (fn [m k v] (assoc m k (inc v))) {} %)))

(defn- dec-system
  "Backward system: decrement every :counter by 1."
  [world]
  (update-in world [:components :counter]
             #(reduce-kv (fn [m k v] (assoc m k (dec v))) {} %)))

(defn- make-test-timeline
  ([]
   (let [[w _e] (counter-world)]
     (timeline/->timeline w [inc-system] [dec-system])))
  ([world]
   (timeline/->timeline world [inc-system] [dec-system])))

(deftest timeline-starts-at-tick-zero-with-snapshot
  (let [tl (make-test-timeline)]
    (is (zero? (current-tick tl)))
    (is (= [0 (:world tl)] (ledger/nearest-snapshot (:ledger tl) 0)))
    (is (ledger/valid-chain? (:ledger tl)))))

(deftest step-forward-advances-tick-and-world
  (let [tl (make-test-timeline)
        tl' (step-forward tl)]
    (is (= 1 (current-tick tl')))
    (is (zero? (count (get-in tl' [:ledger :entries]))))
    (is (= 1 (get-in tl' [:world :components :counter 0])))))

(deftest step-backward-reverses-tick-and-world
  (let [tl (-> (make-test-timeline) step-forward step-backward)]
    (is (zero? (current-tick tl)))
    (is (zero? (get-in tl [:world :components :counter 0])))))

(deftest forward-then-backward-is-identity
  (let [tl (make-test-timeline)
        tl' (-> tl step-forward step-backward)]
    (is (= (:world tl) (:world tl')))
    (is (= (current-tick tl) (current-tick tl')))))

(deftest seek-forward-uses-snapshots
  (let [tl (make-test-timeline)
        tl' (seek tl 100)]
    (is (= 100 (current-tick tl')))
    (is (= 100 (get-in tl' [:world :components :counter 0])))))

(deftest seek-backward-uses-snapshots
  (let [tl (-> (make-test-timeline) (seek 100) (seek 50))]
    (is (= 50 (current-tick tl)))
    (is (= 50 (get-in tl [:world :components :counter 0])))))

(deftest seek-to-current-tick-is-noop
  (let [tl (-> (make-test-timeline) (seek 42))
        tl' (seek tl 42)]
    (is (= tl tl'))))

(deftest seek-backward-past-origin-clamps
  (let [tl (-> (make-test-timeline) (seek 10) (seek 0))]
    (is (zero? (current-tick tl)))
    (is (zero? (get-in tl [:world :components :counter 0])))))

(deftest step-forward-collects-world-events-into-ledger
  (let [[w e] (counter-world)
        event-system (fn [world]
                       (event/emit world
                                   (event/->event {:tick (inc (:tick world))
                                                   :kind :event/test
                                                   :entities #{e}
                                                   :payload {:value 7}})))
        tl (make-test-timeline w)
        tl' (step-forward (assoc tl :systems-fwd [inc-system event-system]))]
    (is (= 1 (count (get-in tl' [:ledger :entries]))))
    (is (= :event/test (get-in tl' [:ledger :entries 0 :event :kind])))
    (is (ledger/valid-chain? (:ledger tl')))))

(deftest step-backward-unapplies-events-with-undo-fn
  (let [[w e] (counter-world)
        undo-marker (fn [world _event]
                      (ecs/put-component world e :undoed? true))
        event-system (fn [world]
                       (event/emit world
                                   (event/->event {:tick (inc (:tick world))
                                                   :kind :event/test
                                                   :entities #{e}
                                                   :payload {:undo-fn undo-marker}})))
        tl (-> (make-test-timeline w)
               (assoc :systems-fwd [inc-system event-system])
               step-forward)
        tl' (step-backward tl)]
    (is (= 1 (get-in tl [:world :components :counter e])) "forward tick increments counter")
    (is (get-in tl' [:world :components :undoed? e]) "backward invokes event undo")))

(deftest snapshots-are-taken-at-intervals
  (let [tl (seek (make-test-timeline) (* 2 timeline/snapshot-every))]
    (is (contains? (:snapshots (:ledger tl)) 0))
    (is (contains? (:snapshots (:ledger tl)) timeline/snapshot-every))
    (is (contains? (:snapshots (:ledger tl)) (* 2 timeline/snapshot-every)))))

(deftest restore-returns-timeline-to-snapshot
  (let [tl (seek (make-test-timeline) 10)
        world-at-3 (assoc (:world tl) :tick 3 :components {:counter {0 99}})
        snap {:tick 3 :world world-at-3}
        tl' (restore tl snap)]
    (is (= 99 (get-in tl' [:world :components :counter 0])))
    (is (= 3 (current-tick tl')))))
