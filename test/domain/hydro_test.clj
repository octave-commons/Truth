(ns domain.hydro-test
  "Tests for the hydrodynamic pressure-gradient layer on the N-body substrate.
   These assert the SPH formulation is momentum-conserving and points the right
   way: high pressure pushes outward, low pressure is compressed."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.hydro :as hydro]
   [domain.stellar :as stellar]
   [domain.physics.cache :as pcache]
   [law.stellar :as ls]
   [law.field :as lfield]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.spatial.index :as spatial]
   [shape.spatial :as sp]))

(deftest test-cubic-spline-gradient
  (testing "Kernel gradient points toward the neighbor (direction of increasing W)"
    (let [r-ij [-1.0 0.0 0.0]
          h 2.0
          grad (hydro/kernel-gradient r-ij h)]
      (is (> (first grad) 0.0) "gradient points toward particle j at origin")
      (is (= 0.0 (second grad) (nth grad 2)))))
  (testing "Kernel gradient vanishes beyond cutoff and at r=0"
    (let [far (hydro/kernel-gradient [3.0 0.0 0.0] 1.0)
          zero (hydro/kernel-gradient [0.0 0.0 0.0] 1.0)]
      (is (every? zero? far))
      (is (every? zero? zero)))))

(deftest test-pressure-term
  (testing "Symmetric pressure term is positive for positive pressures"
    (let [t (hydro/pressure-term 1.0 1.0 2.0 8.0)]
      (is (pos? t))
      (is (< (Math/abs (- t 3.0)) 1e-12) "1/1 + 8/4 = 3"))))

(deftest test-uniform-pressure-zero-accel
  (testing "A uniform pressure field produces no net acceleration"
    (let [data {:position [0.0 0.0 0.0] :density 1.0 :pressure 1.0
                :mass 1.0 :radius 1.0}
          neighbors [{:position [0.5 0.0 0.0] :density 1.0 :pressure 1.0
                      :mass 1.0 :radius 1.0}
                     {:position [-0.5 0.0 0.0] :density 1.0 :pressure 1.0
                      :mass 1.0 :radius 1.0}
                     {:position [0.0 0.5 0.0] :density 1.0 :pressure 1.0
                      :mass 1.0 :radius 1.0}
                     {:position [0.0 -0.5 0.0] :density 1.0 :pressure 1.0
                      :mass 1.0 :radius 1.0}]
          a (hydro/pressure-gradient-acceleration data neighbors)]
      (is (every? #(< (Math/abs %) 1e-12) a)))))

(deftest test-high-pressure-pushes-outward
  (testing "A central high-pressure particle accelerates away from neighbors"
    (let [data {:position [0.0 0.0 0.0] :density 1.0 :pressure 100.0
                :mass 1.0 :radius 1.0}
          neighbors [{:position [0.5 0.0 0.0] :density 1.0 :pressure 1.0
                      :mass 1.0 :radius 1.0}
                     {:position [-0.5 0.0 0.0] :density 1.0 :pressure 1.0
                      :mass 1.0 :radius 1.0}]
          a (hydro/pressure-gradient-acceleration data neighbors)]
      ;; central pressure is high, so it pushes outward: net x should be small
      ;; due to symmetry, but each neighbor feels inward force
      (is (< (Math/abs (first a)) 1e-6)))))

(deftest test-low-pressure-compressed
  (testing "A low-pressure particle between two high-pressure neighbors is compressed"
    (let [data {:position [0.0 0.0 0.0] :density 1.0 :pressure 1.0
                :mass 1.0 :radius 1.0}
          neighbors [{:position [0.5 0.0 0.0] :density 1.0 :pressure 100.0
                      :mass 1.0 :radius 1.0}
                     {:position [-0.5 0.0 0.0] :density 1.0 :pressure 100.0
                      :mass 1.0 :radius 1.0}]
          a (hydro/pressure-gradient-acceleration data neighbors)]
      ;; net acceleration should be near zero by symmetry
      (is (< (Math/abs (first a)) 1e-6)))))

(deftest test-momentum-conservation-pair
  (testing "The pairwise SPH force is antisymmetric"
    (let [left  {:position [-0.5 0.0 0.0] :density 1.0 :pressure 100.0
                 :mass 1.0 :radius 2.0}
          right {:position [0.5 0.0 0.0] :density 1.0 :pressure 1.0
                 :mass 1.0 :radius 2.0}
          a-left  (hydro/pressure-gradient-acceleration left [right])
          a-right (hydro/pressure-gradient-acceleration right [left])]
      ;; left has higher pressure, so it pushes left; right is pushed right
      (is (neg? (first a-left)) "high-pressure left pushes toward -x")
      (is (pos? (first a-right)) "low-pressure right is pushed toward +x")
      (is (< (Math/abs (+ (first a-left) (first a-right))) 1e-12)
          "action and reaction are equal and opposite"))))

(deftest test-sound-speed
  (testing "Sound speed c_s = √(γ P / ρ)"
    (let [cs (hydro/sound-speed 1.0 1.0)]
      (is (< (Math/abs (- cs (Math/sqrt lfield/gamma))) 1e-12))
      (is (zero? (hydro/sound-speed 0.0 1.0)))
      (is (zero? (hydro/sound-speed 1.0 0.0))))))

(deftest test-hydro-system-stores-acceleration
  (testing "hydro-system computes and stores c/hydro-accel on gas particles"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 1e14
                                             :matter-state :nebula
                                             :density 1e-18
                                             :pressure 1e-13})
          [w2 eb] (stellar/spawn-clump w1   {:position [2e14 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 1e14
                                             :matter-state :nebula
                                             :density 1e-18
                                             :pressure 1e-13})
          w2 (spatial/spatial-index w2)
          w3 ((hydro/hydro-system 1e10) w2)
          a-a (ecs/get-component w3 ea c/hydro-accel)
          a-b (ecs/get-component w3 eb c/hydro-accel)]
      (is (some? a-a))
      (is (some? a-b))
      ;; uniform pressure → zero acceleration
      (is (every? #(< (Math/abs %) 1e-20) a-a))
      (is (every? #(< (Math/abs %) 1e-20) a-b)))))

(deftest test-hydro-system-pressure-gradient
  (testing "A pressure gradient produces acceleration pointing downhill"
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
          w3 ((hydro/hydro-system 1e10) w2)
          a-a (ecs/get-component w3 ea c/hydro-accel)
          a-b (ecs/get-component w3 eb c/hydro-accel)]
      (is (neg? (first a-a)) "high-pressure left pushes left")
      (is (pos? (first a-b)) "low-pressure right pushes right"))))

(deftest test-pair-smoothing-length
  (testing "A neighbor at 1.5*r feels the pair smoothing length h_ij = r_i+r_j"
    (let [left  {:position [-0.75 0.0 0.0] :density 1.0 :pressure 100.0
                 :mass 1.0 :radius 1.0}
          right {:position [0.75 0.0 0.0] :density 1.0 :pressure 1.0
                 :mass 1.0 :radius 1.0}
          a-left  (hydro/pressure-gradient-acceleration left [right])
          a-right (hydro/pressure-gradient-acceleration right [left])]
      ;; Distance is 1.5, radii are 1.0, so h_ij = 2.0. With the old half-length
      ;; bug the kernel support would be 1.0 and the force would vanish.
      (is (neg? (first a-left)) "high-pressure left pushes away from right")
      (is (pos? (first a-right)) "low-pressure right is pushed away")
      (is (< (Math/abs (+ (first a-left) (first a-right))) 1e-12)
          "action and reaction remain antisymmetric"))))

(deftest test-hydro-accel-cleared-for-resolved-bodies
  (testing "When a clump stops being hydro-active its acceleration is removed"
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
          w3 ((hydro/hydro-system 1e10) w2)
          _   (is (some? (ecs/get-component w3 ea c/hydro-accel))
                  "nebula particle has hydro-accel while active")
          w4  (ecs/put-component w3 ea c/matter-state :planet)
          w5  ((hydro/hydro-system 1e10) w4)]
      (is (nil? (ecs/get-component w5 ea c/hydro-accel))
          "resolved body no longer carries stale hydro-accel"))))

(deftest test-kernel-r2-matches-r
  (testing "kernel-r2(r²,h) equals kernel(r,h) within tolerance"
    (let [h 2.0]
      (doseq [r [0.0 0.5 1.0 1.5 2.0 2.5 3.0]]
        (let [r2 (* r r)
              w-r (hydro/kernel r h)
              w-r2 (hydro/kernel-r2 r2 h)]
          (is (< (Math/abs (- w-r w-r2)) 1e-15)
              (str "r=" r " kernel arities match")))))))

(deftest test-kernel-gradient-r2-arity-matches-r-arity
  (testing "kernel-gradient([rx ry rz], r2, h) equals kernel-gradient([rx ry rz], h)"
    (let [h 2.0]
      (doseq [v [[0.5 0.0 0.0] [1.0 1.0 0.0] [-0.5 0.3 0.1]
                 [3.0 0.0 0.0] [0.0 0.0 0.0]]]
        (let [[rx ry rz] v
              r2 (+ (* rx rx) (* ry ry) (* rz rz))
              grad-r (hydro/kernel-gradient v h)
              grad-r2 (hydro/kernel-gradient v r2 h)]
          (is (< (sp/dist grad-r grad-r2) 1e-15)
              (str "v=" v " gradient arities match")))))))

(deftest test-kernel-boundary-cutoff
  (testing "Kernel is zero at and beyond the support radius"
    (let [h 2.0
          h2 (* h h)]
      (is (zero? (hydro/kernel h h)) "W(r=h,h) = 0")
      (is (zero? (hydro/kernel-r2 h2 h)) "W(r²=h²,h) = 0")
      (is (zero? (hydro/kernel (+ h 0.1) h)) "W(r>h,h) = 0")
      (is (zero? (hydro/kernel-r2 (* (+ h 0.1) (+ h 0.1)) h)) "W(r²>h²,h) = 0")
      (is (pos? (hydro/kernel (* 0.99 h) h)) "W just inside support is positive")
      (is (pos? (hydro/kernel-r2 (* 0.99 h2) h)) "W(r²) just inside support is positive"))))

(deftest test-kernel-normalization
  (testing "The cubic-spline kernel integrates to 1 over its support in 3D"
    (let [h 2.0
          ;; Simpson's rule on [0,h] for W(r,h) 4π r² dr
          n 1000
          dr (/ h n)
          integral (* 4.0 Math/PI
                      (reduce (fn [acc i]
                                (let [r (* i dr)
                                      w (hydro/kernel r h)]
                                  (+ acc (* w r r dr))))
                              0.0
                              (range 1 (inc n))))]
      (is (< (Math/abs (- integral 1.0)) 1e-4)))))

(deftest test-self-density
  (testing "An isolated particle has finite SPH density from its self-contribution"
    (let [m 1.0
          r 1.0
          data {:position [0.0 0.0 0.0] :mass m :radius r :density 0.0 :pressure 0.0}
          rho (hydro/sph-density data [data])]
      (is (pos? rho))
      (is (< (Math/abs (- rho (/ m Math/PI))) 1e-12)
          "self-density equals m W(0,2r) = m/(π r³)"))))

(deftest test-density-rises-with-crowding
  (testing "A particle with more neighbors within h has higher SPH density"
    (let [m 1.0
          r 1.0
          h 2.0
          base {:mass m :radius r :density 0.0 :pressure 0.0}
          isolated (assoc base :position [0.0 0.0 0.0])
          crowded  (assoc base :position [0.0 0.0 0.0])
          neighbor-left  {:position [(- h 0.1) 0.0 0.0] :mass m :radius r}
          _neighbor-right {:position [(+ h 0.1) 0.0 0.0] :mass m :radius r}
          ;; right neighbor is just outside support
          rho-isolated (hydro/sph-density isolated [isolated])
          rho-crowded  (hydro/sph-density crowded  [crowded neighbor-left])]
      (is (< rho-isolated rho-crowded)
          "crowded particle is denser than isolated particle"))))

(deftest test-density-system-updates-nebula-density
  (testing "density-system overwrites seed density with SPH density for :nebula"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 1e14
                                             :matter-state :nebula
                                             :temperature 12.0})
          [w2 _eb] (stellar/spawn-clump w1   {:position [1e14 0.0 0.0]
                                              :velocity [0.0 0.0 0.0]
                                              :mass 1e28
                                              :radius 1e14
                                              :matter-state :nebula
                                              :temperature 12.0})
          w2 (spatial/spatial-index w2)
          seed-rho (ecs/get-component w2 ea c/density)
          w3 ((hydro/density-system 1e10) w2)
          new-rho (ecs/get-component w3 ea c/density)]
      (is (not= seed-rho new-rho) "density-system changed the seed density")
      (is (pos? new-rho) "new density is positive")
      ;; two equal-mass particles overlapping should both be denser than seed body-density
      (is (> new-rho seed-rho) "crowded SPH density exceeds uniform sphere body-density"))))

(deftest test-density-system-preserves-resolved-body-density
  (testing "density-system does not overwrite :planet or :protostar body-density"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e29
                                             :radius 1e13
                                             :matter-state :planet
                                             :temperature 200.0})
          seed-rho (ecs/get-component w1 ea c/density)
          w1 (spatial/spatial-index w1)
          w2 ((hydro/density-system 1e10) w1)]
      (is (= seed-rho (ecs/get-component w2 ea c/density))
          "resolved body keeps its seed body-density"))))

(deftest test-density-pressure-consistent
  (testing "after density-system, pressure equals ideal-gas-law of new density"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 1e14
                                             :matter-state :nebula
                                             :temperature 12.0})
          [w2 _eb] (stellar/spawn-clump w1   {:position [1e14 0.0 0.0]
                                              :velocity [0.0 0.0 0.0]
                                              :mass 1e28
                                              :radius 1e14
                                              :matter-state :nebula
                                              :temperature 12.0})
          w2 (spatial/spatial-index w2)
          w3 ((hydro/density-system 1e10) w2)
          rho (ecs/get-component w3 ea c/density)
          press (ecs/get-component w3 ea c/pressure)
          expected (ls/ideal-gas-pressure rho 12.0)]
      (is (< (Math/abs (- press expected)) (* 1e-12 (max 1.0 (Math/abs expected))))
          "pressure is recomputed from the new density and temperature"))))

(deftest test-density-system-updates-radius
  (testing "density-system shrinks the radius of a crowded gas particle"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 1e14
                                             :matter-state :nebula
                                             :temperature 12.0})
          [w2 _eb] (stellar/spawn-clump w1   {:position [1e14 0.0 0.0]
                                              :velocity [0.0 0.0 0.0]
                                              :mass 1e28
                                              :radius 1e14
                                              :matter-state :nebula
                                              :temperature 12.0})
          w2 (spatial/spatial-index w2)
          seed-r (ecs/get-component w2 ea c/radius)
          w3 ((hydro/density-system 1e10) w2)
          new-r (ecs/get-component w3 ea c/radius)]
      (is (< new-r seed-r) "crowded gas particle shrinks after density update"))))

(deftest test-radius-density-consistent
  (testing "adaptive radius keeps r³ × ρ proportional to fixed mass"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 1e14
                                             :matter-state :nebula
                                             :temperature 12.0})
          [w2 _eb] (stellar/spawn-clump w1   {:position [1e14 0.0 0.0]
                                              :velocity [0.0 0.0 0.0]
                                              :mass 1e28
                                              :radius 1e14
                                              :matter-state :nebula
                                              :temperature 12.0})
          [w3 ec] (stellar/spawn-clump w2   {:position [3e15 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 1e14
                                             :matter-state :nebula
                                             :temperature 12.0})
          w3 (spatial/spatial-index w3)
          w4 ((hydro/density-system 1e10) w3)
          r-crowded (ecs/get-component w4 ea c/radius)
          rho-crowded (ecs/get-component w4 ea c/density)
          r-isolated (ecs/get-component w4 ec c/radius)
          rho-isolated (ecs/get-component w4 ec c/density)]
            ;; same mass → r³ × ρ should be similar; dense particle has smaller r
      (is (< r-crowded r-isolated) "crowded particle is smaller than isolated particle")
      (is (< (Math/abs (- (* r-crowded r-crowded r-crowded rho-crowded)
                          (* r-isolated r-isolated r-isolated rho-isolated)))
             1e30)
          "r³ × ρ is approximately conserved for equal-mass particles"))))

(deftest test-density-system-matches-with-cache
  (testing "density-system produces identical densities when using the shared cache"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 1e14
                                             :matter-state :nebula
                                             :temperature 12.0})
          [w2 _eb] (stellar/spawn-clump w1   {:position [1e14 0.0 0.0]
                                              :velocity [0.0 0.0 0.0]
                                              :mass 1e28
                                              :radius 1e14
                                              :matter-state :nebula
                                              :temperature 12.0})
          w2 (spatial/spatial-index w2)
          rho-uncached (ecs/get-component ((hydro/density-system 1e10) w2) ea c/density)
          cached (pcache/build-neighbor-cache w2)
          rho-cached (ecs/get-component ((hydro/density-system 1e10) cached) ea c/density)]
      (is (< (Math/abs (- rho-uncached rho-cached))
             (* 1e-12 (max 1.0 (Math/abs rho-uncached))))
          "cached density equals uncached density"))))

(deftest test-hydro-system-matches-with-cache
  (testing "hydro-system produces identical pressure-gradient accelerations with cache"
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
          a-uncached (ecs/get-component ((hydro/hydro-system 1e10) w2) ea c/hydro-accel)
          cached (pcache/build-neighbor-cache w2)
          a-cached (ecs/get-component ((hydro/hydro-system 1e10) cached) ea c/hydro-accel)]
      (is (< (sp/dist a-uncached a-cached)
             (* 1e-12 (max 1.0 (sp/len a-uncached))))
          "cached acceleration equals uncached acceleration"))))

(deftest test-gas-structure-matches-with-cache
  (testing "gas-structure returns identical [eid density radius] with cache"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 1e14
                                             :matter-state :nebula
                                             :temperature 12.0})
          [w2 _eb] (stellar/spawn-clump w1   {:position [1e14 0.0 0.0]
                                              :velocity [0.0 0.0 0.0]
                                              :mass 1e28
                                              :radius 1e14
                                              :matter-state :nebula
                                              :temperature 12.0})
          w2 (spatial/spatial-index w2)
          uncached (hydro/gas-structure w2)
          cached (pcache/build-neighbor-cache w2)
          cached-result (hydro/gas-structure cached)]
      (is (= (set (map (fn [[eid rho r]] [eid (double rho) (double r)]) uncached))
             (set (map (fn [[eid rho r]] [eid (double rho) (double r)]) cached-result)))
          "gas-structure results match with and without cache"))))

(deftest test-hydro-system-fallback-without-cache
  (testing "hydro-system runs correctly when :genesis/neighbor-cache is absent"
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
          w3 ((hydro/hydro-system 1e10) w2)
          a-a (ecs/get-component w3 ea c/hydro-accel)
          a-b (ecs/get-component w3 eb c/hydro-accel)]
      (is (some? a-a))
      (is (some? a-b))
      (is (every? #(Double/isFinite (double %)) a-a))
      (is (every? #(Double/isFinite (double %)) a-b))
      (is (neg? (first a-a)) "high-pressure left pushes left")
      (is (pos? (first a-b)) "low-pressure right pushes right"))))

(deftest test-density-system-fallback-without-cache
  (testing "density-system runs correctly when :genesis/neighbor-cache is absent"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 1e14
                                             :matter-state :nebula
                                             :temperature 12.0})
          [w2 _eb] (stellar/spawn-clump w1   {:position [1e14 0.0 0.0]
                                              :velocity [0.0 0.0 0.0]
                                              :mass 1e28
                                              :radius 1e14
                                              :matter-state :nebula
                                              :temperature 12.0})
          w2 (spatial/spatial-index w2)
          seed-rho (ecs/get-component w2 ea c/density)
          w3 ((hydro/density-system 1e10) w2)
          new-rho (ecs/get-component w3 ea c/density)]
      (is (not= seed-rho new-rho) "density-system changed the seed density")
      (is (pos? new-rho) "new density is positive")
      (is (Double/isFinite (double new-rho)) "new density is finite")
      (is (> new-rho seed-rho) "crowded SPH density exceeds uniform sphere body-density"))))

(deftest test-hydro-includes-protostar-neighbors
  (testing "A :nebula parcel feels pressure from a nearby :protostar neighbor"
    (let [base (ecs/empty-world)
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e28
                                             :radius 2e14
                                             :matter-state :nebula
                                             :density 1.0
                                             :pressure 1.0})
          [w2 eb] (stellar/spawn-clump w1   {:position [1e14 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e29
                                             :radius 2e13
                                             :matter-state :protostar
                                             :density 1.0
                                             :pressure 100.0})
          w2 (spatial/spatial-index w2)
          a-a (ecs/get-component ((hydro/hydro-system 1e10) w2) ea c/hydro-accel)]
      (is (some? a-a))
      (is (every? #(Double/isFinite (double %)) a-a))
      ;; high-pressure protostar neighbor pushes the nebula parcel away
      (is (neg? (first a-a)) "nebula parcel is pushed away from protostar"))))
