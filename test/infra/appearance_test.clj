(ns infra.appearance-test
  "Pins the pure body-appearance mapping: every body kind gets the surface it
   IS, deterministically, and a living world is visibly alive."
  (:require
   [clojure.test :refer [deftest testing is]]
   [infra.render :as render]
   [infra.camera :as cam]
   [infra.render.units :as units]))

(deftest appearance-by-state-and-type
  (testing "stars granulate, protostars run molten"
    (is (= render/surface-star (:surface (render/body-appearance :star nil 5800.0 false 1))))
    (is (= render/surface-molten (:surface (render/body-appearance :protostar nil 900.0 false 1)))))
  (testing "planets surface by planet-type"
    (is (= render/surface-gas-giant
           (:surface (render/body-appearance :planet :gas-giant 120.0 false 2))))
    (is (= render/surface-ice-giant
           (:surface (render/body-appearance :planet :ice-giant 60.0 false 3))))
    (is (= render/surface-terrestrial
           (:surface (render/body-appearance :planet :terrestrial 290.0 false 4)))))
  (testing "debris is rocky when cold, molten when hot"
    (is (= render/surface-rocky (:surface (render/body-appearance :planetesimal nil 200.0 false 5))))
    (is (= render/surface-molten (:surface (render/body-appearance :planetesimal nil 2000.0 false 5)))))
  (testing "unknown states render flat"
    (is (= render/surface-flat (:surface (render/body-appearance :nebula nil 10.0 false 6))))))

(deftest temperate-and-living-worlds-read-alive
  (testing "a temperate terrestrial gets a blue ocean base"
    (let [app (render/body-appearance :planet :terrestrial 290.0 false 7)]
      (is (some? (:base app)))
      (let [[r g b] (:base app)] (is (> b r)) (is (> b g)))))
  (testing "a frozen or scorched terrestrial keeps its material base"
    (is (nil? (:base (render/body-appearance :planet :terrestrial 150.0 false 7))))
    (is (nil? (:base (render/body-appearance :planet :terrestrial 500.0 false 7)))))
  (testing "life turns the land green"
    (let [dead  (:accent (render/body-appearance :planet :terrestrial 290.0 false 8))
          alive (:accent (render/body-appearance :planet :terrestrial 290.0 true 8))]
      (is (not= dead alive))
      (let [[r g b] alive] (is (> g r)) (is (> g b))))))

(deftest appearance-is-deterministic-per-eid
  (let [a (render/body-appearance :planet :terrestrial 290.0 false 42)
        b (render/body-appearance :planet :terrestrial 290.0 false 42)
        c (render/body-appearance :planet :terrestrial 290.0 false 43)]
    (is (= a b) "same eid → same face")
    (is (not= (:seed a) (:seed c)) "different eids get different faces")))

(deftest min-approach-and-overlay-scale
  (testing "camera approach floor scales with body size but never degenerates"
    (is (= 1.0e-7 (cam/min-approach-distance 0.0)))
    (is (= 1.0e-7 (cam/min-approach-distance nil)))
    (is (< (cam/min-approach-distance 1.0e-8) 1.0e-6)
        "a true-scale planet can be approached to within sight of its globe")
    (is (= 25.0 (cam/min-approach-distance 10.0)))))
