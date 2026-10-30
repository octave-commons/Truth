(ns shape.rigid-test
  (:require [clojure.test :refer [deftest is]] [shape.rigid :as rigid]
            [shape.spatial :as sp]))

(deftest quaternion-rotation-and-inertia
  (let [q [(Math/cos (/ Math/PI 4)) 0.0 0.0 (Math/sin (/ Math/PI 4))]]
    (is (< (sp/dist [0.0 1.0 0.0] (rigid/rotate q [1.0 0.0 0.0])) 1e-12)))
  (let [i [[2.0 0.2 0.1] [0.2 3.0 0.3] [0.1 0.3 4.0]] v [1.0 2.0 3.0]]
    (is (< (sp/dist v (rigid/matvec (rigid/inverse i) (rigid/matvec i v))) 1e-12))))

(deftest torque-free-spin-conserves-energy
  (let [inverse [[0.5 0.0 0.0] [0.0 (/ 1.0 3.0) 0.0] [0.0 0.0 0.25]]
        angular [0.3 0.5 0.7]
        energy (fn [q] (let [body-l (rigid/rotate (rigid/conjugate q) angular)]
                           (* 0.5 (sp/dot body-l (rigid/matvec inverse body-l)))))
        q (nth (iterate #(rigid/step % angular inverse 0.01) [1.0 0.0 0.0 0.0]) 1000)]
    (is (< (abs (- 1.0 (reduce + (map #(* % %) q)))) 1e-12))
    (is (< (abs (- (energy q) (energy [1.0 0.0 0.0 0.0]))) 1e-9))))
