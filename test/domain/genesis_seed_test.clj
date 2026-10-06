(ns domain.genesis-seed-test
  "Public bootstrap seed forwarding and reproducible initial conditions."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.genesis :as genesis]))

(defn- initial-gas
  "Physical component maps for four initial gas parcels, excluding the observer."
  [opts]
  (let [world (genesis/create-world (assoc opts :gas-count 4))
        eids (ecs/entities-with world c/matter-state)]
    (into {}
          (map (fn [component]
                 [component (select-keys (get-in world [:components component]) eids)]))
          [c/position c/velocity c/mass c/matter-state c/composition])))

(deftest explicit-seeds-control-reproducible-initial-conditions
  (let [seed-41 (initial-gas {:seed 41})
        seed-43 (initial-gas {:seed 43})]
    (testing "repeating an explicit seed reproduces the same physical state"
      (is (= seed-41 (initial-gas {:seed 41}))))
    (testing "different requested seeds vary cloud geometry and motion"
      (is (not= (get seed-41 c/position) (get seed-43 c/position)))
      (is (not= (get seed-41 c/velocity) (get seed-43 c/velocity))))
    (testing "seed variation preserves the configured matter budget"
      (is (= 4 (count (get seed-41 c/matter-state))
             (count (get seed-43 c/matter-state))))
      (is (= 4e30 (reduce + (vals (get seed-41 c/mass)))
             (reduce + (vals (get seed-43 c/mass)))))
      (is (= (select-keys seed-41 [c/mass c/matter-state c/composition])
             (select-keys seed-43 [c/mass c/matter-state c/composition]))))))

(deftest omitted-seed-preserves-the-existing-default-world
  (is (= (initial-gas {}) (initial-gas {:seed 42}))))
