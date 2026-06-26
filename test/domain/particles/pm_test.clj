(ns domain.particles.pm-test
  "Tests for the particle-mesh gravity solver."
  (:require [clojure.test :refer [deftest testing is]]
            [domain.particles.pm :as pm]))

(deftest test-pm-momentum-conservation
  (testing "Self-gravity of a symmetric pair gives equal and opposite accelerations"
    (let [mesh (pm/make-mesh 16 40.0 1.0)
          n 2
          px (double-array [-2.0 2.0])
          py (double-array [0.0 0.0])
          pz (double-array [0.0 0.0])
          mass (double-array [1.0 1.0])
          ax (double-array n) ay (double-array n) az (double-array n)]
      (pm/solve! mesh px py pz mass n ax ay az)
      ;; accelerations should be anti-parallel along x (point toward each other)
      (is (neg? (* (aget ax 0) (aget ax 1))) "x accelerations should point toward each other")
      ;; total momentum change should be near zero
      (is (< (Math/abs (+ (* (aget mass 0) (aget ax 0)) (* (aget mass 1) (aget ax 1)))) 0.01)))))

(deftest test-pm-attracts-toward-mass
  (testing "A light particle is accelerated toward a heavy one"
    (let [mesh (pm/make-mesh 16 40.0 1.0)
          n 2
          px (double-array [-5.0 5.0])
          py (double-array [0.0 0.0])
          pz (double-array [0.0 0.0])
          mass (double-array [0.01 10.0])
          ax (double-array n) ay (double-array n) az (double-array n)]
      (pm/solve! mesh px py pz mass n ax ay az)
      (is (pos? (aget ax 0)) "light particle should accelerate toward heavy")
      (is (neg? (aget ax 1)) "heavy particle should be pulled back toward light"))))
