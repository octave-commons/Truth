(ns domain.world-bootstrap-test
  "Coverage tests for the world bootstrap entry point."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.event :as event]
   [domain.ecs.rewindable :refer [current-tick]]
   [domain.ecs.timeline :as timeline]
   [domain.world-bootstrap :as boot]
   [shape.spatial :as sp]))

(deftest bootstrap-creates-world-with-defaults
  (let [w (boot/bootstrap {})]
    (testing "world has simulation constants"
      (is (= 6.674e-11 (:sim/G w)))
      (is (= 0.5 (:sim/theta w)))
      (is (= 1.0 (:sim/dt w))))
    (testing "world has an event ledger and handler registry"
      (is (map? (:ledger w)))
      (is (map? (:handlers w)))
      (is (map? (:rewind-handlers w))))
    (testing "world starts at tick zero"
      (is (zero? (:tick w))))))

(deftest bootstrap-accepts-overrides
  (let [w (boot/bootstrap {:G 1.0 :theta 0.75 :dt 0.5 :merge? true})]
    (is (= 1.0 (:sim/G w)))
    (is (= 0.75 (:sim/theta w)))
    (is (= 0.5 (:sim/dt w)))))

(deftest make-systems-returns-forward-pipeline
  (let [w (boot/bootstrap {:G 1.0 :theta 0.5 :dt 0.1})
        systems (boot/make-systems w)]
    (testing "pipeline has orbital physics then collision detection"
      (is (= 2 (count systems)))
      (is (ifn? (first systems)))
      (is (ifn? (second systems))))))

(deftest make-timeline-wraps-world
  (let [w (boot/bootstrap {})
        tl (boot/make-timeline w)]
    (testing "timeline is a Timeline record"
      (is (instance? domain.ecs.timeline.Timeline tl)))
    (testing "timeline starts at tick 0 with a snapshot"
      (is (zero? (current-tick tl)))
      (is (contains? (:snapshots (:ledger tl)) 0)))))

(deftest bootstrapped-handler-resolves-elastic-collision
  (let [w (boot/bootstrap {:merge? false})
        [w a] (ecs/spawn w)
        [w b] (ecs/spawn w)
        w (-> w
              (ecs/put-components a {c/mass 10.0 c/radius 1.0
                                     c/position (sp/vec3 0.0 0.0 0.0)
                                     c/velocity (sp/vec3 1.0 0.0 0.0)})
              (ecs/put-components b {c/mass 10.0 c/radius 1.0
                                     c/position (sp/vec3 1.5 0.0 0.0)
                                     c/velocity (sp/vec3 -1.0 0.0 0.0)}))
        e (event/->event {:tick 0 :kind :event/collision
                          :entities #{a b}
                          :payload {:eid-a a :eid-b b
                                    :normal (sp/vec3 -1.0 0.0 0.0)
                                    :depth 0.5}})
        w' (event/dispatch w e)
        va (ecs/get-component w' a c/velocity)
        vb (ecs/get-component w' b c/velocity)]
    (testing "equal-mass elastic collision swaps velocities along normal"
      (is (< (Math/abs (- (first va) -1.0)) 1e-9))
      (is (< (Math/abs (- (first vb) 1.0)) 1e-9)))))

(deftest bootstrapped-handler-resolves-merge-collision
  (let [w (boot/bootstrap {:merge? true})
        [w a] (ecs/spawn w)
        [w b] (ecs/spawn w)
        w (-> w
              (ecs/put-components a {c/mass 10.0 c/radius 2.0
                                     c/position (sp/vec3 0.0 0.0 0.0)
                                     c/velocity (sp/vec3 1.0 0.0 0.0)})
              (ecs/put-components b {c/mass 5.0 c/radius 1.0
                                     c/position (sp/vec3 1.0 0.0 0.0)
                                     c/velocity (sp/vec3 0.0 0.0 0.0)}))
        e (event/->event {:tick 0 :kind :event/collision
                          :entities #{a b}
                          :payload {:eid-a a :eid-b b}})
        w' (event/dispatch w e)]
    (testing "merge absorbs smaller body into larger"
      (is (ecs/alive? w' a))
      (is (not (ecs/alive? w' b)))
      (is (= 15.0 (ecs/get-component w' a c/mass)))
      (is (< (Math/abs (- (first (ecs/get-component w' a c/velocity))
                          (/ 10.0 15.0)))
             1e-9)))))
