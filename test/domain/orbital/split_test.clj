(ns domain.orbital.split-test
  "Step 3: gravity split out of the integrator. The Barnes–Hut tree-walk becomes
   its own write-set system (accel.gravity); a thin motion integrator sums every
   acceleration contribution and advances position/velocity symplectically."
  (:require
    [clojure.test :refer [deftest is testing]]
    [domain.ecs.core :as ecs]
    [domain.ecs.components :as c]
    [domain.orbital.system :as orbital]
    [domain.spatial.index  :as spatial]
    [law.stellar :as law]))

(defn- body [w eid pos vel]
  (ecs/put-components w eid {c/position pos c/velocity vel
                             c/mass 1.0e30 c/radius 1.0e9
                             c/body-kind :body/gas}))

(deftest motion-sums-all-acceleration-contributions
  (let [[w e0] (ecs/spawn (ecs/empty-world))
        w (-> (body w e0 [0.0 0.0 0.0] [1.0 0.0 0.0])
              ;; two separate contributions on the same body
              (ecs/put-component e0 c/accel-gravity  [1.0 0.0 0.0])
              (ecs/put-component e0 c/accel-pressure [0.0 2.0 0.0]))
        ws ((:run (orbital/motion-integration 2.0)) w)]
    (testing "net a = Σ contributions = [1 2 0]; v' = v + a·dt; x' = x + v'·dt"
      ;; a=[1 2 0], dt=2 → v'=[1 0 0]+[2 4 0]=[3 4 0]; x'=[0 0 0]+[6 8 0]=[6 8 0]
      (is (= [3.0 4.0 0.0] (get-in ws [c/velocity e0])))
      (is (= [6.0 8.0 0.0] (get-in ws [c/position e0]))))
    (testing "motion writes ONLY position and velocity"
      (is (= #{c/position c/velocity} (set (keys ws)))))))

(deftest motion-treats-missing-contributions-as-zero
  (let [[w e0] (ecs/spawn (ecs/empty-world))
        w  (body w e0 [5.0 0.0 0.0] [0.0 0.0 0.0])   ;; no accel components at all
        ws ((:run (orbital/motion-integration 10.0)) w)]
    (testing "zero net acceleration ⇒ body coasts (v unchanged, x drifts by v·dt)"
      (is (= [0.0 0.0 0.0] (get-in ws [c/velocity e0])))
      (is (= [5.0 0.0 0.0] (get-in ws [c/position e0]))))))

(deftest gravity-emits-finite-accel-for-every-body
  (let [[w e0] (ecs/spawn (ecs/empty-world))
        [w e1] (ecs/spawn w)
        w  (-> (body w e0 [-1.0e12 0.0 0.0] [0.0 0.0 0.0])
               (body e1 [ 1.0e12 0.0 0.0] [0.0 0.0 0.0]))
        w  (spatial/spatial-index w)
        sys (orbital/gravity-acceleration law/G 0.5 5.0e14)
        ws  ((:run sys) w)
        ag  (get ws c/accel-gravity)]
    (testing "gravity writes only accel.gravity, one vector per body"
      (is (= #{c/accel-gravity} (set (keys ws))))
      (is (= #{e0 e1} (set (keys ag)))))
    (testing "the two masses attract — accelerations point toward each other"
      (let [[ax0 _ _] (get ag e0)
            [ax1 _ _] (get ag e1)]
        (is (pos? ax0) "left body accelerates +x (rightward, toward e1)")
        (is (neg? ax1) "right body accelerates -x (leftward, toward e0)")
        (is (every? #(Double/isFinite (double %)) (concat (get ag e0) (get ag e1))))))))
