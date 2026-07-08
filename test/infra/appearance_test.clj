(ns infra.appearance-test
  "Pins the pure body-appearance mapping: every body kind gets the surface it
   IS, deterministically, and a living world is visibly alive."
  (:require
   [clojure.test :refer [deftest testing is]]
   [infra.render :as render]
   [infra.camera :as cam]))

(deftest appearance-by-state-and-type
  (testing "stars granulate, protostars run molten"
    (is (= render/surface-star (:surface (render/body-appearance {:state :star :planet-type nil :temp 5800.0 :living? false :eid 1}))))
    (is (= render/surface-molten (:surface (render/body-appearance {:state :protostar :planet-type nil :temp 900.0 :living? false :eid 1})))))
  (testing "planets surface by planet-type"
    (is (= render/surface-gas-giant
           (:surface (render/body-appearance {:state :planet :planet-type :gas-giant :temp 120.0 :living? false :eid 2}))))
    (is (= render/surface-ice-giant
           (:surface (render/body-appearance {:state :planet :planet-type :ice-giant :temp 60.0 :living? false :eid 3}))))
    (is (= render/surface-terrestrial
           (:surface (render/body-appearance {:state :planet :planet-type :terrestrial :temp 290.0 :living? false :eid 4})))))
  (testing "debris is rocky when cold, molten when hot"
    (is (= render/surface-rocky (:surface (render/body-appearance {:state :planetesimal :planet-type nil :temp 200.0 :living? false :eid 5}))))
    (is (= render/surface-molten (:surface (render/body-appearance {:state :planetesimal :planet-type nil :temp 2000.0 :living? false :eid 5})))))
  (testing "unknown states render flat"
    (is (= render/surface-flat (:surface (render/body-appearance {:state :nebula :planet-type nil :temp 10.0 :living? false :eid 6}))))))

(deftest temperate-and-living-worlds-read-alive
  (testing "a temperate terrestrial gets a blue ocean base"
    (let [app (render/body-appearance {:state :planet :planet-type :terrestrial :temp 290.0 :living? false :eid 7})]
      (is (some? (:base app)))
      (let [[r g b] (:base app)] (is (> b r)) (is (> b g)))))
  (testing "a frozen or scorched terrestrial keeps its material base"
    (is (nil? (:base (render/body-appearance {:state :planet :planet-type :terrestrial :temp 150.0 :living? false :eid 7}))))
    (is (nil? (:base (render/body-appearance {:state :planet :planet-type :terrestrial :temp 500.0 :living? false :eid 7})))))
  (testing "life turns the land green"
    (let [dead  (:accent (render/body-appearance {:state :planet :planet-type :terrestrial :temp 290.0 :living? false :eid 8}))
          alive (:accent (render/body-appearance {:state :planet :planet-type :terrestrial :temp 290.0 :living? true :eid 8}))]
      (is (not= dead alive))
      (let [[r g b] alive] (is (> g r)) (is (> g b))))))

(deftest appearance-is-deterministic-per-eid
  (let [a (render/body-appearance {:state :planet :planet-type :terrestrial :temp 290.0 :living? false :eid 42})
        b (render/body-appearance {:state :planet :planet-type :terrestrial :temp 290.0 :living? false :eid 42})
        c (render/body-appearance {:state :planet :planet-type :terrestrial :temp 290.0 :living? false :eid 43})]
    (is (= a b) "same eid → same face")
    (is (not= (:seed a) (:seed c)) "different eids get different faces")))

(deftest min-approach-and-overlay-scale
  (testing "camera approach floor scales with body size but never degenerates"
    (is (= 1.0e-7 (cam/min-approach-distance 0.0)))
    (is (= 1.0e-7 (cam/min-approach-distance nil)))
    (is (< (cam/min-approach-distance 1.0e-8) 1.0e-6)
        "a true-scale planet can be approached to within sight of its globe")
    (is (= 25.0 (cam/min-approach-distance 10.0)))))
