(ns domain.mhd-force-test
  "Tests for the merged hydro/EM force system."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.tick :as tick]
   [domain.stellar :as stellar]
   [domain.spatial.index :as spatial]
   [domain.physics.cache :as pcache]
   [domain.hydro :as hydro]
   [domain.em :as em]
   [domain.mhd.force :as mhd]
   [shape.spatial :as sp]))

(deftest test-merged-system-matches-pressure-acceleration
  (testing "Merged system pressure channel equals the standalone hydro pressure system"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 2e14
                                             :matter-state :nebula
                                             :density 1.0
                                             :pressure 100.0})
          [w2 eb] (stellar/spawn-clump w1   {:position [1e14 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 2e14
                                             :matter-state :nebula
                                             :density 1.0
                                             :pressure 1.0})
          w2 (spatial/spatial-index w2)
          cached (pcache/build-neighbor-cache w2)
          dt 1e10
          ;; standalone pressure system
          ws-p ((:run (hydro/pressure-acceleration)) cached)
          w-p  (tick/apply-write-set cached ws-p)
          a-p-a (ecs/get-component w-p ea c/accel-pressure)
          a-p-b (ecs/get-component w-p eb c/accel-pressure)
          ;; merged system
          ws-m ((:run (mhd/merged-hydro-em-system dt)) cached)
          w-m  (tick/apply-write-set cached ws-m)
          a-m-a (ecs/get-component w-m ea c/accel-pressure)
          a-m-b (ecs/get-component w-m eb c/accel-pressure)]
      (is (some? a-m-a))
      (is (some? a-m-b))
      (is (< (sp/dist a-p-a a-m-a) 1e-6))
      (is (< (sp/dist a-p-b a-m-b) 1e-6))
      (is (neg? (first a-m-a)) "high-pressure left pushes left")
      (is (pos? (first a-m-b)) "low-pressure right pushes right"))))

(deftest test-merged-system-matches-lorentz-acceleration
  (testing "Merged system Lorentz channel equals the standalone Lorentz system"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 2e14
                                             :matter-state :nebula
                                             :density 1.0
                                             :pressure 1.0
                                             :b-field [0.0 0.0 1.0]
                                             :angular-momentum [0.0 0.0 1e30]})
          [w2 eb] (stellar/spawn-clump w1   {:position [1e14 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 2e14
                                             :matter-state :nebula
                                             :density 1.0
                                             :pressure 1.0
                                             :b-field [0.0 0.0 0.5]
                                             :angular-momentum [0.0 0.0 0.0]})
          w2 (spatial/spatial-index w2)
          cached (pcache/build-neighbor-cache w2)
          dt 1e10
          ;; standalone Lorentz system
          ws-l ((:run (em/lorentz-acceleration-system dt)) cached)
          w-l  (tick/apply-write-set cached ws-l)
          a-l-a (ecs/get-component w-l ea c/accel-lorentz)
          a-l-b (ecs/get-component w-l eb c/accel-lorentz)
          ;; merged system
          ws-m ((:run (mhd/merged-hydro-em-system dt)) cached)
          w-m  (tick/apply-write-set cached ws-m)
          a-m-a (ecs/get-component w-m ea c/accel-lorentz)
          a-m-b (ecs/get-component w-m eb c/accel-lorentz)]
      (is (some? a-m-a))
      (is (some? a-m-b))
      (is (< (sp/dist a-l-a a-m-a) 1e-9))
      (is (< (sp/dist a-l-b a-m-b) 1e-9))
      (is (> (sp/len a-m-a) 1e-20)))))

(deftest test-merged-system-computes-magnetic-braking
  (testing "Merged system emits magnetic-braking torque for rotating protostars"
    (let [base (ecs/empty-world)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e30
                                             :radius 1e15
                                             :matter-state :protostar
                                             :density 1e-15
                                             :pressure 1e-10
                                             :b-field [0.0 0.0 1.0e-4]
                                             :angular-momentum [0.0 0.0 1e45]})
          w (spatial/spatial-index w)
          cached (pcache/build-neighbor-cache w)
          dt 1e10
          ws ((:run (mhd/merged-hydro-em-system dt)) cached)
          w' (tick/apply-write-set cached ws)
          torque (ecs/get-component w' eid c/torque-em)]
      (is (some? torque))
      (is (every? #(Double/isFinite (double %)) torque))
      (is (neg? (nth torque 2)) "braking torque opposes +z angular momentum"))))

(deftest test-merged-system-clears-stale-for-resolved-bodies
  (testing "When a clump stops being hydro/EM-active its acceleration channels are removed"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 2e14
                                             :matter-state :nebula
                                             :density 1.0
                                             :pressure 100.0})
          [w2 _eb] (stellar/spawn-clump w1   {:position [1e14 0.0 0.0]
                                              :velocity [0.0 0.0 0.0]
                                              :mass 1e28
                                              :radius 2e14
                                              :matter-state :nebula
                                              :density 1.0
                                              :pressure 1.0})
          w2 (spatial/spatial-index w2)
          cached (pcache/build-neighbor-cache w2)
          dt 1e10
          ws1 ((:run (mhd/merged-hydro-em-system dt)) cached)
          w3  (tick/apply-write-set cached ws1)
          _   (is (some? (ecs/get-component w3 ea c/accel-pressure)))
          w4  (ecs/put-component w3 ea c/matter-state :planet)
          ws2 ((:run (mhd/merged-hydro-em-system dt)) w4)
          w5  (tick/apply-write-set w4 ws2)]
      (is (nil? (ecs/get-component w5 ea c/accel-pressure)))
      (is (nil? (ecs/get-component w5 ea c/accel-lorentz)))
      (is (nil? (ecs/get-component w5 ea c/torque-em))))))