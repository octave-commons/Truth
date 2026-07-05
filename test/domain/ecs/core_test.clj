(ns domain.ecs.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]))

(deftest spawn-returns-unique-ids
  (testing "each spawn call produces a distinct entity id"
    (let [[w e1] (ecs/spawn (ecs/empty-world))
          [w e2] (ecs/spawn w)
          [_w e3] (ecs/spawn w)]
      (is (distinct? e1 e2 e3)))))

(deftest despawn-removes-all-components
  (testing "despawned entity has no components and is not alive"
    (let [[w e1] (ecs/spawn (ecs/empty-world))
          w      (ecs/put-component w e1 :pos [0.0 0.0 0.0])
          w      (ecs/despawn w e1)]
      (is (nil? (ecs/get-component w e1 :pos)))
      (is (not (ecs/alive? w e1))))))

(deftest alive?-tracks-spawn-despawn
  (testing "alive? true after spawn, false after despawn"
    (let [[w e1] (ecs/spawn (ecs/empty-world))]
      (is (ecs/alive? w e1))
      (is (not (ecs/alive? (ecs/despawn w e1) e1))))))

(deftest put-get-roundtrip
  (testing "get-component returns the exact value passed to put-component"
    (let [[w e1] (ecs/spawn (ecs/empty-world))
          w      (ecs/put-component w e1 :vel [1.0 2.0 3.0])]
      (is (= [1.0 2.0 3.0] (ecs/get-component w e1 :vel))))))

(deftest put-component-is-idempotent-on-same-value
  (testing "putting the same value twice yields same world value"
    (let [[w e1] (ecs/spawn (ecs/empty-world))
          w1     (ecs/put-component w e1 :hp 100)
          w2     (ecs/put-component w1 e1 :hp 100)]
      (is (= (ecs/get-component w1 e1 :hp)
             (ecs/get-component w2 e1 :hp))))))

(deftest remove-component-clears-key
  (testing "remove-component makes get-component return nil"
    (let [[w e1] (ecs/spawn (ecs/empty-world))
          w      (-> w
                     (ecs/put-component e1 :hp 100)
                     (ecs/remove-component e1 :hp))]
      (is (nil? (ecs/get-component w e1 :hp))))))

(deftest get-all-components
  (testing "get-components returns a map of all keys for an entity"
    (let [[w e1] (ecs/spawn (ecs/empty-world))
          w      (-> w
                     (ecs/put-component e1 :hp 50)
                     (ecs/put-component e1 :pos [1.0 2.0 3.0]))]
      (is (= {:hp 50 :pos [1.0 2.0 3.0]}
             (ecs/get-components w e1))))))

(deftest entities-with-single-component
  (testing "entities-with returns only entities having all requested keys"
    (let [[w e1] (ecs/spawn (ecs/empty-world))
          [w e2] (ecs/spawn w)
          [w e3] (ecs/spawn w)
          w      (-> w
                     (ecs/put-component e1 :pos [0.0 0.0 0.0])
                     (ecs/put-component e2 :pos [1.0 1.0 1.0])
                     (ecs/put-component e3 :vel [0.0 0.0 0.0]))]
      (is (= #{e1 e2} (set (ecs/entities-with w :pos))))
      (is (= #{e3}    (set (ecs/entities-with w :vel)))))))

(deftest entities-with-multiple-components
  (testing "entities-with intersection across multiple keys"
    (let [[w e1] (ecs/spawn (ecs/empty-world))
          [w e2] (ecs/spawn w)
          [w e3] (ecs/spawn w)
          w      (-> w
                     (ecs/put-component e1 :pos [0.0 0.0 0.0])
                     (ecs/put-component e1 :vel [0.0 0.0 0.0])
                     (ecs/put-component e2 :pos [1.0 1.0 1.0])
                     (ecs/put-component e3 :vel [0.0 0.0 0.0]))]
      (is (= #{e1} (set (ecs/entities-with w :pos :vel)))))))

(deftest entities-with-no-match-returns-empty
  (testing "entities-with returns empty seq when no entity has all keys"
    (let [[w e1] (ecs/spawn (ecs/empty-world))
          w      (ecs/put-component w e1 :hp 10)]
      (is (empty? (ecs/entities-with w :pos :vel))))))

(deftest archetype-index-stays-consistent
  (testing "archetype index reflects live component topology"
    (let [[w e1] (ecs/spawn (ecs/empty-world))
          w      (-> w
                     (ecs/put-component e1 :a 1)
                     (ecs/put-component e1 :b 2))
          arch   (ecs/archetype w e1)]
      (is (= #{:a :b} arch))
      (let [w' (ecs/remove-component w e1 :a)]
        (is (= #{:b} (ecs/archetype w' e1)))))))

(deftest tick-increments
  (testing "tick increments :tick counter"
    (let [w  (ecs/empty-world)
          w' (ecs/tick w [])]
      (is (= 1 (:tick w'))))))

(deftest systems-compose-in-order
  (testing "systems run in order and compose"
    (let [[w eid] (ecs/spawn (ecs/empty-world))
          w       (ecs/put-component w eid :test/counter 0)
          inc-sys (fn [world]
                    (ecs/update-component world eid :test/counter
                                          (fnil inc 0)))
          w'      (ecs/tick w [inc-sys inc-sys inc-sys])]
      (is (= 3 (ecs/get-component w' eid :test/counter))))))
