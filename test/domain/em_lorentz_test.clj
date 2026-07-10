(ns domain.em-lorentz-test
  "Tests for the Lorentz force and magnetic braking additions to domain.em.
   These verify that magnetic fields now exert real forces and torques rather
   than only diagnostics."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.em     :as em]
   [domain.physics.cache :as pcache]
   [domain.stellar.seeder :as seeder]
   [domain.ecs.core :as ecs]
   [domain.ecs.tick :as tick]
   [domain.ecs.components :as c]
   [domain.spatial.index :as spatial]
   [shape.spatial :as sp]))

(deftest test-curl-estimate-zero-for-uniform-field
  (testing "A uniform B-field has zero curl"
    (let [b [0.0 0.0 1.0e-9]
          __data-a {:position [0.0 0.0 0.0] :b-field b :mass 1.0 :density 1.0 :radius 1.0}
          data-b {:position [0.5 0.0 0.0] :b-field b :mass 1.0 :density 1.0 :radius 1.0}
          curl (em/curl-estimate {:b-field b :density 1.0 :position [0.0 0.0 0.0] :neighbors [data-b]})]
      (is (every? #(< (abs %) 1e-20) curl)))))

(deftest test-lorentz-force-perpendicular-to-b
  (testing "f · B = 0"
    (let [curl-b [1.0e-12 0.0 0.0]
          b      [0.0 0.0 1.0e-9]
          f      (em/lorentz-force-density b curl-b)]
      (is (< (abs (sp/dot f b)) 1e-30)
          "Lorentz force is perpendicular to B"))))

(deftest test-lorentz-acceleration-positive
  (testing "A non-zero curl and field produce acceleration"
    (let [b [0.0 0.0 1.0e-9]
          curl-b [1.0e-12 0.0 0.0]
          a (em/lorentz-acceleration b curl-b 1.0)]
      (is (pos? (sp/len a))))))

(deftest test-magnetic-braking-opposes-spin
  (testing "Braking torque points opposite to angular momentum"
    (let [cell {:mass 2e30
                :radius 1e15
                :density 1e-15
                :b-field [0.0 0.0 1.0e-5]
                :angular-momentum [0.0 0.0 1e40]
                :rotation-axis [0.0 0.0 1.0]}
          tau (em/magnetic-braking-torque cell 1.0e10)]
      (is (neg? (nth tau 2)) "torque in -z opposes +z angular momentum")
      (is (zero? (first tau))
          "torque is aligned with rotation axis")
      (is (zero? (second tau))))))

(deftest test-em-system-applies-lorentz-acceleration
  (testing "em-system adds Lorentz acceleration to c/hydro-accel"
    (let [base (ecs/empty-world)
          ;; uniform field → zero curl; add a gradient by tilting one neighbor
          [w1 ea] (seeder/spawn-clump base {:position [0.0 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e28
                                            :radius 2e14
                                            :matter-state :nebula
                                            :density 1.0
                                            :pressure 1.0
                                            :b-field [0.0 0.0 1.0]
                                            :angular-momentum [0.0 0.0 1e30]})
          [w2 eb] (seeder/spawn-clump w1   {:position [1e14 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e28
                                            :radius 2e14
                                            :matter-state :nebula
                                            :density 1.0
                                            :pressure 1.0
                                            :b-field [0.0 0.0 0.5]
                                            :angular-momentum [0.0 0.0 0.0]})
          w2 (spatial/spatial-index w2)
          w3 ((em/em-system 1e10) w2)
          a-a (ecs/get-component w3 ea c/hydro-accel)
          a-b (ecs/get-component w3 eb c/hydro-accel)]
      (is (some? a-a))
      (is (some? a-b))
      ;; non-uniform B → non-zero Lorentz acceleration
      (is (> (sp/len a-a) 1e-20))
      (is (> (sp/len a-b) 1e-20)))))

(deftest test-em-system-brakes-spin
  (testing "em-system reduces the magnitude of angular momentum over one tick"
    (let [base (ecs/empty-world)
          [w eid] (seeder/spawn-clump base {:position [0.0 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 2e30
                                            :radius 1e15
                                            :matter-state :protostar
                                            :density 1e-15
                                            :pressure 1e-10
                                            :b-field [0.0 0.0 1.0e-4]
                                            :angular-momentum [0.0 0.0 1e45]})
          L0   (ecs/get-component w eid c/angular-momentum)
          spin0 (ecs/get-component w eid c/spin)
          w    (spatial/spatial-index w)
          w2   ((em/em-system 1e10) w)
          L1   (ecs/get-component w2 eid c/angular-momentum)
          spin1 (ecs/get-component w2 eid c/spin)]
      (is (< (sp/len L1) (sp/len L0))
          "angular momentum magnitude decreases due to magnetic braking")
      (is (< (sp/len spin1) (sp/len spin0))
          "spin magnitude decreases"))))

(deftest test-em-system-conserves-b-field-bounds
  (testing "Resistive decay keeps B finite"
    (let [base (ecs/empty-world)
          [w eid] (seeder/spawn-clump base {:position [0.0 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e28
                                            :radius 1e14
                                            :matter-state :nebula
                                            :density 1e-18
                                            :pressure 1e-13
                                            :b-field [0.0 0.0 1.0e-9]
                                            :angular-momentum [0.0 0.0 0.0]})
          w    (spatial/spatial-index w)
          w2   ((em/em-system 1e10) w)
          b    (ecs/get-component w2 eid c/b-field)]
      (is (some? b))
      (is (< (sp/len b) 1.0)))))

(deftest test-curl-estimate-matches-with-cache
  (testing "Cached curl equals on-the-fly curl for the same neighbors"
    (let [b [0.0 0.0 1.0]
          data-b {:position [0.5 0.0 0.0] :b-field [0.0 0.0 0.5] :mass 1.0 :density 1.0 :radius 1.0}
          curl-uncached (em/curl-estimate {:b-field b :density 1.0 :position [0.0 0.0 0.0] :radius 1.0 :neighbors [data-b]})
          grads [(:gradient-curl (pcache/neighbor-with-gradients [0.0 0.0 0.0] 1.0 data-b))]
          curl-cached (em/curl-estimate {:b-field b :density 1.0 :position [0.0 0.0 0.0] :radius 1.0 :neighbors [data-b] :gradients grads})]
      (is (< (sp/dist curl-uncached curl-cached)
             (* 1e-12 (max 1.0 (sp/len curl-uncached))))
          "cached curl matches on-the-fly curl"))))

(deftest test-em-system-matches-with-cache
  (testing "em-system applies the same Lorentz acceleration with and without cache"
    (let [base (ecs/empty-world)
          [w1 ea] (seeder/spawn-clump base {:position [0.0 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e28
                                            :radius 2e14
                                            :matter-state :nebula
                                            :density 1.0
                                            :pressure 1.0
                                            :b-field [0.0 0.0 1.0]
                                            :angular-momentum [0.0 0.0 1e30]})
          [w2 _eb] (seeder/spawn-clump w1   {:position [1e14 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 2e14
                                             :matter-state :nebula
                                             :density 1.0
                                             :pressure 1.0
                                             :b-field [0.0 0.0 0.5]
                                             :angular-momentum [0.0 0.0 0.0]})
          w2 (spatial/spatial-index w2)
          a-uncached (ecs/get-component ((em/em-system 1e10) w2) ea c/hydro-accel)
          cached (pcache/build-neighbor-cache w2)
          a-cached (ecs/get-component ((em/em-system 1e10) cached) ea c/hydro-accel)]
      (is (< (sp/dist a-uncached a-cached)
             (* 1e-12 (max 1.0 (sp/len a-uncached))))
          "cached em-system acceleration matches uncached"))))

(deftest test-mhd-gate-suppresses-weak-field
  (testing "When magnetic pressure is negligible, capped Lorentz returns zero"
    (let [data {:b-field [0.0 0.0 1.0e-12]
                :density 1.0
                :pressure 1.0
                :radius 1.0
                :velocity [1.0e4 0.0 0.0]}
          curl-b [1.0e-15 0.0 0.0]
          a (em/capped-lorentz-acceleration data curl-b)]
      (is (zero? (sp/len a))))))

(deftest test-em-system-fallback-without-cache
  (testing "em-system runs correctly when c/neighbor-cache is absent"
    (let [base (ecs/empty-world)
          [w1 ea] (seeder/spawn-clump base {:position [0.0 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e28
                                            :radius 2e14
                                            :matter-state :nebula
                                            :density 1.0
                                            :pressure 1.0
                                            :b-field [0.0 0.0 1.0]
                                            :angular-momentum [0.0 0.0 1e30]})
          [w2 eb] (seeder/spawn-clump w1   {:position [1e14 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e28
                                            :radius 2e14
                                            :matter-state :nebula
                                            :density 1.0
                                            :pressure 1.0
                                            :b-field [0.0 0.0 0.5]
                                            :angular-momentum [0.0 0.0 0.0]})
          w2 (spatial/spatial-index w2)
          w3 ((em/em-system 1e10) w2)
          a-a (ecs/get-component w3 ea c/hydro-accel)
          a-b (ecs/get-component w3 eb c/hydro-accel)]
      (is (some? a-a))
      (is (some? a-b))
      (is (every? #(Double/isFinite (double %)) a-a))
      (is (every? #(Double/isFinite (double %)) a-b))
      (is (> (sp/len a-a) 1e-20))
      (is (> (sp/len a-b) 1e-20)))))

(deftest test-lorentz-system-fallback-without-cache
  (testing "lorentz-acceleration-system runs correctly without neighbor cache"
    (let [base (ecs/empty-world)
          [w1 ea] (seeder/spawn-clump base {:position [0.0 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e28
                                            :radius 2e14
                                            :matter-state :nebula
                                            :density 1.0
                                            :pressure 1.0
                                            :b-field [0.0 0.0 1.0]
                                            :angular-momentum [0.0 0.0 1e30]})
          [w2 eb] (seeder/spawn-clump w1   {:position [1e14 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e28
                                            :radius 2e14
                                            :matter-state :nebula
                                            :density 1.0
                                            :pressure 1.0
                                            :b-field [0.0 0.0 0.5]
                                            :angular-momentum [0.0 0.0 0.0]})
          w2 (spatial/spatial-index w2)
          ws ((:run (em/lorentz-acceleration-system 1e10)) w2)
          w3 (tick/apply-write-set w2 ws)
          a-a (ecs/get-component w3 ea c/accel-lorentz)
          a-b (ecs/get-component w3 eb c/accel-lorentz)]
      (is (some? a-a))
      (is (some? a-b))
      (is (every? #(Double/isFinite (double %)) a-a))
      (is (every? #(Double/isFinite (double %)) a-b))
      (is (> (sp/len a-a) 1e-20))
      (is (> (sp/len a-b) 1e-20)))))

(deftest test-neutral-parcel-feels-no-lorentz-force
  (testing "A parcel with ionization-fraction zero experiences zero Lorentz acceleration."
    (let [base (ecs/empty-world)
          [w1 ea] (seeder/spawn-clump base {:position [0.0 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e28
                                            :radius 2e14
                                            :matter-state :nebula
                                            :density 1.0
                                            :pressure 1.0
                                            :b-field [0.0 0.0 1.0]
                                            :angular-momentum [0.0 0.0 1e30]})
          [w2 eb] (seeder/spawn-clump w1   {:position [1e14 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e28
                                            :radius 2e14
                                            :matter-state :nebula
                                            :density 1.0
                                            :pressure 1.0
                                            :b-field [0.0 0.0 0.5]
                                            :angular-momentum [0.0 0.0 0.0]})
          w2 (-> w2
                 (ecs/put-component ea c/ionization-fraction 0.0)
                 (ecs/put-component eb c/ionization-fraction 0.0)
                 (spatial/spatial-index))
          ws ((:run (em/lorentz-acceleration-system 1e10)) w2)
          w3 (tick/apply-write-set w2 ws)]
      (is (zero? (sp/len (ecs/get-component w3 ea c/accel-lorentz))))
      (is (zero? (sp/len (ecs/get-component w3 eb c/accel-lorentz)))))))

(deftest test-curl-estimate-skips-nil-b-field
  (testing "curl-estimate ignores neighbors with missing or nil b-field"
    (let [b [0.0 0.0 1.0]
          _data-a {:position [0.0 0.0 0.0] :b-field b :mass 1.0 :density 1.0 :radius 1.0}
          data-b {:position [0.5 0.0 0.0] :b-field nil :mass 1.0 :density 1.0 :radius 1.0}
          curl (em/curl-estimate {:b-field b :density 1.0 :position [0.0 0.0 0.0] :neighbors [data-b]})]
      (is (every? zero? curl)))))
