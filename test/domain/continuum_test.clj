(ns domain.continuum-test
  (:require [clojure.test :refer [deftest is]]
            [domain.continuum :as fe]
            [domain.ecs.core] [domain.ecs.components] [domain.ecs.tick] [domain.integrator]))

(deftest refinement-preserves-affine-physical-state
  (let [s (fe/create-region :coarse)
        s (fe/affine-state s [[0.97 0.04] [-0.02 1.03]] [4.0 -3.0]
                           [[0.0002 -0.001] [0.001 0.0001]] [0.3 -0.2])
        before (fe/observe s) refined (fe/refine s) after (fe/observe refined)]
    (is (= 16 (:elements before)))
    (is (= 64 (:elements after)))
    (doseq [k [:mass-kg :kinetic-J :elastic-J :angular-momentum]]
      (is (< (abs (- (k after) (k before))) (* 1e-10 (max 1.0 (abs (k before))))) (str k)))
    (is (< (fe/norm (mapv - (:momentum before) (:momentum after))) 1e-5))
    (is (= (:stocks-kg before) (:stocks-kg after)))))

(deftest energy-gradient-is-the-elastic-force
  (let [s (fe/affine-state (fe/create-region :coarse) [[0.98 0.02] [0.01 1.02]] [0.0 0.0]
                           [[0.0 0.0] [0.0 0.0]] [0.0 0.0])
        epsilon 1e-4 node 0 axis 0
        plus (update-in s [:x node axis] + epsilon)
        minus (update-in s [:x node axis] - epsilon)
        gradient (/ (- (:elastic-J (fe/observe plus)) (:elastic-J (fe/observe minus))) (* 2 epsilon))
        force (get-in (fe/elastic s) [:forces node axis])]
    (is (< (abs (+ gradient force)) (* 1e-6 (max 1.0 (abs force)))))))

(deftest contact-is-an-energy-gradient-and-reciprocal
  (let [s (assoc-in (fe/create-region :coarse) [:projectile :p] [-22.0 64.0])
        contact (fe/contact s)
        target (reduce #(mapv + %1 %2) [0.0 0.0] (:forces contact))]
    (is (pos? (:energy contact)))
    (is (< (fe/norm (mapv + target (:projectile-force contact))) 1e-8))))

(deftest closed-region-step-conserves-momentum-and-accounts-heat
  (let [s (fe/affine-state (fe/create-region :coarse) [[1.0 0.0] [0.0 1.0]] [0.0 0.0]
                           [[0.0001 -0.0004] [0.0004 -0.0001]] [0.01 -0.02])
        a (fe/observe s) b (fe/observe (fe/step s 0.01))]
    (is (pos? (:heat-J b)))
    (is (< (fe/norm (mapv - (:momentum a) (:momentum b))) 1e-5))
    (is (< (abs (- (:angular-momentum a) (:angular-momentum b))) 1e-3))))

(deftest integrator-is-the-only-owner-and-advances-region
  (let [w (domain.ecs.core/empty-world)
        [w id] (domain.ecs.core/spawn w)
        w (domain.ecs.core/put-component w id domain.ecs.components/continuum-region (fe/create-region :coarse))
        after (domain.ecs.tick/apply-write-set w ((:run (domain.integrator/integrator-system 0.5)) w))]
    (is (= 0.5 (:time (domain.ecs.core/get-component after id domain.ecs.components/continuum-region))))))

(deftest contact-remapping-preserves-potential
  (let [s (assoc-in (fe/create-region :coarse) [:projectile :p] [-22.0 64.0])
        a (fe/observe s) b (fe/observe (fe/refine s)) epsilon 1e-5
        plus (update-in s [:projectile :p 0] + epsilon)
        minus (update-in s [:projectile :p 0] - epsilon)
        derivative (/ (- (:energy (fe/contact plus)) (:energy (fe/contact minus))) (* 2.0 epsilon))
        force (first (:projectile-force (fe/contact s)))]
    (is (< (abs (- (:contact-J a) (:contact-J b))) 1e-5))
    (is (< (abs (+ derivative force)) (* 1e-7 (abs force))))))

(deftest adaptive-quiet-guard-does-not-thrash-before-impact
  (let [s (nth (iterate #(fe/step % 0.5) (fe/create-region :adaptive)) 150)
        ops (map :op (:transfers s))]
    (is (= [:refine] (vec ops)))
    (is (= 1 (:level s)))))

(deftest coarsening-rejects-unresolved-distortion
  (let [fine (-> (fe/create-region :fine) (assoc :mode :adaptive)
                 (update-in [:x 20 0] + 1.0))
        candidate (fe/try-coarsen fine)]
    (is (= 1 (:level candidate)))
    (is (= 1 (:coarsen-rejections candidate)))
    (is (pos? (abs (get-in candidate [:last-coarsen-error :energy-residual-J]))))))

(deftest compatible-coarsening-and-rigid-rotation
  (let [s (fe/affine-state (fe/create-region :coarse) [[1.01 0.02] [-0.01 0.99]] [3.0 -2.0]
                           [[0.0001 -0.0002] [0.0002 0.0001]] [0.1 0.2])
        a (fe/observe s) coarse (fe/try-coarsen (fe/refine s)) b (fe/observe coarse)]
    (is (zero? (:level coarse)))
    (is (< (abs (- (:energy-J a) (:energy-J b))) 0.1))
    (is (< (fe/norm (mapv - (:momentum a) (:momentum b))) 1e-5)))
  (let [angle 0.4 ca (Math/cos angle) sa (Math/sin angle)
        s (fe/affine-state (fe/create-region :coarse) [[ca (- sa)] [sa ca]] [7.0 2.0]
                           [[0.0 0.0] [0.0 0.0]] [0.0 0.0])]
    (is (< (abs (:elastic-J (fe/observe s))) 0.01))))
