(ns shape.spatial-test
  (:require
   [clojure.math :as math] [clojure.test :refer [deftest is testing]]
   [shape.spatial :as spatial]))

(deftest vec3-ops
  (testing "Basic vec3 arithmetic and norms"
    (let [a (spatial/vec3 1.0 2.0 3.0)
          b (spatial/vec3 -1.0 0.0 4.0)]
      (is (= [1.0 2.0 3.0] a))
      (is (= [0.0 2.0 7.0] (spatial/v+ a b)))
      (is (= [2.0 4.0 6.0] (spatial/v* a 2.0)))
      (is (<= (abs (- (spatial/len a)
                      (math/sqrt 14.0)))
              1.0e-9))
      (is (= 0.0 (spatial/len (spatial/vec3 0.0 0.0 0.0)))))))

(deftest aabb-contains-and-expand
  (testing "AABB containment and expansion around points"
    (let [p1 (spatial/vec3 -1.0 0.0 2.0)
          p2 (spatial/vec3  3.0 5.0 -2.0)
          bb (spatial/aabb-from-points [p1 p2])]
      (is (spatial/contains? bb p1))
      (is (spatial/contains? bb p2))
      (is (spatial/contains? bb (spatial/center bb)))
      (let [bb2 (spatial/aabb-include bb (spatial/vec3 10.0 0.0 0.0))]
        (is (spatial/contains? bb2 (spatial/vec3 10.0 0.0 0.0)))
        (is (spatial/contains? bb2 p1))
        (is (spatial/contains? bb2 p2))))))

(deftest octant-classification
  (testing "Points are correctly assigned to AABB octants"
    (let [bb     (spatial/aabb (spatial/vec3 -1.0 -1.0 -1.0)
                               (spatial/vec3  1.0  1.0  1.0))
          center (spatial/center bb)]
      ;; The centre lies exactly on all three dividing planes, which `octant`'s
      ;; docstring resolves with `>=` — positive on every axis. Asserting that
      ;; explicitly, because the previous form here was `(is (spatial/octant bb
      ;; center))`: `octant` always returns one of the eight keywords, all truthy,
      ;; so it could never fail (clj-kondo `:condition-always-true`).
      (is (= :octant/ppp (spatial/octant bb center))
          "a point on all three planes goes positive on each axis, deterministically")
      (is (= :octant/ppp (spatial/octant bb (spatial/vec3 0.5 0.5 0.5))))
      (is (= :octant/ppm (spatial/octant bb (spatial/vec3 0.5 0.5 -0.5))))
      (is (= :octant/mpm (spatial/octant bb (spatial/vec3 -0.5 0.5 -0.5))))
      (is (= :octant/mmm (spatial/octant bb (spatial/vec3 -0.5 -0.5 -0.5)))))))

(deftest body-shape
  (testing "Bodies have mass, radius, position, velocity, and kind"
    (let [b (spatial/->body
             {:id       1
              :mass     5.972e24
              :radius   6.371e6
              :kind     :body/planet
              :position (spatial/vec3 0.0 0.0 0.0)
              :velocity (spatial/vec3 0.0 0.0 0.0)})]
      (is (= 1 (:id b)))
      (is (= :body/planet (:kind b)))
      (is (= 5.972e24 (:mass b)))
      (is (= 6.371e6 (:radius b)))
      (is (= [0.0 0.0 0.0] (:position b)))
      (is (= [0.0 0.0 0.0] (:velocity b))))))
