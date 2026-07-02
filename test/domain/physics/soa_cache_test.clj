(ns domain.physics.soa-cache-test
  "Tests for the transient SoA primitive-array physics cache."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.tick :as tick]
   [domain.integrator :as integ]
   [domain.orbital.system :as orbital]
   [domain.physics.cache :as cache]
   [domain.spatial.index :as spatial]
   [domain.stellar :as stellar]
   [law.field :as lfield]
   [shape.spatial :as sp]))

(defn- seeded-world
  "A small deterministic world of gas particles for cache/parity tests."
  ([] (seeded-world 20))
  ([n]
   (let [base (ecs/empty-world)]
     (reduce (fn [w i]
               (first (stellar/spawn-clump
                       w {:position [(double (* i 1e14)) 0.0 0.0]
                          :velocity [0.0 0.0 0.0]
                          :mass 1e28
                          :radius 2e14
                          :matter-state :nebula
                          :density 1e-18
                          :pressure 1e-13
                          :temperature 12.0
                          :b-field [0.0 0.0 1.0e-9]})))
             base (range n)))))

(deftest test-soa-cache-validated
  (testing "The SoA cache satisfies law.field/physics-soa-schema"
    (let [w (-> (seeded-world 20) spatial/spatial-index cache/build-physics-soa)
          soa (:phase0/physics-soa w)]
      (is (map? soa))
      (is (lfield/physics-soa? soa))
      (is (pos? (:n soa)))
      (is (= (:n soa) (count (:eids soa)))))))

(deftest test-soa-arrays-match-ecs
  (testing "Primitive arrays mirror ECS component values"
    (let [w (-> (seeded-world 20) spatial/spatial-index cache/build-physics-soa)
          soa (:phase0/physics-soa w)]
      (doseq [[idx eid] (map-indexed vector (:eids soa))]
        (let [pos (ecs/get-component w eid c/position)
              vel (ecs/get-component w eid c/velocity)
              m   (double (or (ecs/get-component w eid c/mass) 0.0))
              r   (double (or (ecs/get-component w eid c/radius) 0.0))]
          (is (= m (aget ^doubles (:mass soa) idx)))
          (is (= r (aget ^doubles (:radius soa) idx)))
          (is (= (double (nth pos 0)) (aget ^doubles (:px soa) idx)))
          (is (= (double (nth pos 1)) (aget ^doubles (:py soa) idx)))
          (is (= (double (nth pos 2)) (aget ^doubles (:pz soa) idx)))
          (is (= (double (nth vel 0)) (aget ^doubles (:vx soa) idx)))
          (is (= (double (nth vel 1)) (aget ^doubles (:vy soa) idx)))
          (is (= (double (nth vel 2)) (aget ^doubles (:vz soa) idx))))))))

(deftest test-soa-stripped-after-build
  (testing "strip-physics-soa removes the cache without touching components"
    (let [w0 (-> (seeded-world 10) spatial/spatial-index cache/build-physics-soa)
          w1 (cache/strip-physics-soa w0)]
      (is (contains? w0 :phase0/physics-soa))
      (is (not (contains? w1 :phase0/physics-soa)))
      (is (= (:components w0) (:components w1))))))

(deftest test-soa-empty-world
  (testing "SoA construction and strip on an empty world"
    (let [w0 (-> (ecs/empty-world) spatial/spatial-index cache/build-physics-soa)
          soa (:phase0/physics-soa w0)]
      (is (zero? (:n soa)))
      (is (empty? (:eids soa)))
      (is (lfield/physics-soa? soa))
      (let [w1 (cache/strip-physics-soa w0)]
        (is (not (contains? w1 :phase0/physics-soa)))))))

(deftest test-soa-missing-optional-components
  (testing "Entities missing density/pressure/b-field still produce a valid cache"
    (let [w (first (stellar/spawn-clump
                    (ecs/empty-world)
                    {:position [1e14 0.0 0.0]
                     :velocity [0.0 1e3 0.0]
                     :mass 1e25
                     :radius 1e13
                     :matter-state :nebula}))
          w (-> w spatial/spatial-index cache/build-physics-soa)
          soa (:phase0/physics-soa w)]
      (is (= 1 (:n soa)))
      (is (lfield/physics-soa? soa))
      (is (= 1e25 (aget ^doubles (:mass soa) 0)))
      (is (= 1e13 (aget ^doubles (:radius soa) 0))))))

(defn- step-with-soa-flag
  "Run gravity + integrator on `world` with optional SoA cache. Returns the
   updated world."
  [world with-soa?]
  (let [G       6.6743e-11
        theta   0.5
        dt      1.0e12
        soften  1e14
        gravity (orbital/gravity-acceleration G theta soften)
        integrator (integ/integrator-system dt)
        w       (-> world
                    spatial/spatial-index
                    cache/build-neighbor-cache
                    (cond-> with-soa? cache/build-physics-soa))
        w1      (tick/apply-write-set w ((:run gravity) w))
        w2      (tick/apply-write-set w1 ((:run integrator) w1))]
    (cond-> w2
      with-soa? cache/strip-physics-soa
      true      cache/strip-neighbor-cache)))

(defn- close-vec-absolute?
  "Absolute tolerance comparison for 3-vectors."
  [a b tol]
  (let [a (mapv double a)
        b (mapv double b)]
    (every? #(< (Math/abs (double %)) tol)
            (map - a b))))

(deftest test-gravity-integrator-parity
  (testing "SoA and ECS gravity+integrator paths produce identical physics"
    (let [w0 (seeded-world 30)
          w-soa (step-with-soa-flag w0 true)
          w-no-soa (step-with-soa-flag w0 false)
          eids (ecs/entities-with w-soa c/position c/velocity c/mass)]
      (is (= (set eids) (set (ecs/entities-with w-no-soa c/position c/velocity c/mass))))
      (doseq [eid eids]
        (let [pos-soa (ecs/get-component w-soa eid c/position)
              pos-ref (ecs/get-component w-no-soa eid c/position)
              vel-soa (ecs/get-component w-soa eid c/velocity)
              vel-ref (ecs/get-component w-no-soa eid c/velocity)
              m-soa   (ecs/get-component w-soa eid c/mass)
              m-ref   (ecs/get-component w-no-soa eid c/mass)]
          (is (close-vec-absolute? pos-soa pos-ref 1e-6)
              (str "position mismatch for eid " eid))
          (is (close-vec-absolute? vel-soa vel-ref 1e-6)
              (str "velocity mismatch for eid " eid))
          (is (< (Math/abs (- (double m-soa) (double m-ref))) 1e-6)
              (str "mass mismatch for eid " eid)))))))

(deftest test-multi-tick-parity
  (testing "Several ticks with SoA match the ECS-only path within tolerance"
    (let [w0 (seeded-world 20)
          ticks 5
          [w-soa w-no-soa]
          (reduce (fn [[ws wn] _]
                    [(step-with-soa-flag ws true)
                     (step-with-soa-flag wn false)])
                  [w0 w0]
                  (range ticks))
          eids (ecs/entities-with w-soa c/position c/velocity)]
      (is (= (set eids) (set (ecs/entities-with w-no-soa c/position c/velocity))))
      (doseq [eid eids]
        (is (close-vec-absolute? (ecs/get-component w-soa eid c/position)
                                 (ecs/get-component w-no-soa eid c/position)
                                 1e-3))
        (is (close-vec-absolute? (ecs/get-component w-soa eid c/velocity)
                                 (ecs/get-component w-no-soa eid c/velocity)
                                 1e-3))))))

(deftest test-soa-frame-offset-parity
  (testing "SoA path respects :phase0/frame-offset exactly like the ECS path"
    (let [w0 (-> (seeded-world 15)
                 (assoc :phase0/frame-offset [1e12 2e12 3e12]))
          w-soa (step-with-soa-flag w0 true)
          w-no-soa (step-with-soa-flag w0 false)
          eids (ecs/entities-with w-soa c/position)]
      (doseq [eid eids]
        (is (close-vec-absolute? (ecs/get-component w-soa eid c/position)
                                 (ecs/get-component w-no-soa eid c/position)
                                 1e-6))))))

(deftest test-soa-absorb-merge-parity
  (testing "SoA path blends absorb-merge packets identically to ECS path"
    (let [eid-a (second (stellar/spawn-clump
                         (ecs/empty-world)
                         {:position [0.0 0.0 0.0]
                          :velocity [0.0 0.0 0.0]
                          :mass 1e28
                          :radius 2e14
                          :matter-state :nebula}))
          eid-b (second (stellar/spawn-clump
                         (ecs/empty-world)
                         {:position [1e13 0.0 0.0]
                          :velocity [1e3 0.0 0.0]
                          :mass 1e26
                          :radius 1e13
                          :matter-state :nebula}))
          w0    (-> (ecs/empty-world)
                    (ecs/put-component eid-a c/position [0.0 0.0 0.0])
                    (ecs/put-component eid-a c/velocity [0.0 0.0 0.0])
                    (ecs/put-component eid-a c/mass 1e28)
                    (ecs/put-component eid-a c/radius 2e14)
                    (ecs/put-component eid-a c/body-kind :body/gas)
                    (ecs/put-component eid-b c/position [1e13 0.0 0.0])
                    (ecs/put-component eid-b c/velocity [1e3 0.0 0.0])
                    (ecs/put-component eid-b c/mass 1e26)
                    (ecs/put-component eid-b c/radius 1e13)
                    (ecs/put-component eid-b c/body-kind :body/gas)
                    (ecs/put-component eid-a c/absorb-merge
                                       [{:mass 1e26
                                         :velocity [1e3 0.0 0.0]
                                         :position [1e13 0.0 0.0]
                                         :angular-momentum [0.0 0.0 0.0]}]))
          w-soa (step-with-soa-flag w0 true)
          w-no-soa (step-with-soa-flag w0 false)]
      (is (close-vec-absolute? (ecs/get-component w-soa eid-a c/velocity)
                               (ecs/get-component w-no-soa eid-a c/velocity)
                               1e-6))
      (is (close-vec-absolute? (ecs/get-component w-soa eid-a c/position)
                               (ecs/get-component w-no-soa eid-a c/position)
                               1e-6))
      (is (< (Math/abs (- (double (ecs/get-component w-soa eid-a c/mass))
                          (double (ecs/get-component w-no-soa eid-a c/mass))))
             1e-6)))))

(deftest test-soa-validation-rejects-malformed-cache
  (testing "law.field/physics-soa? rejects a malformed SoA cache"
    (is (not (lfield/physics-soa? {:n 1 :eids [1]
                                   :mass (double-array [1.0])
                                   :radius (double-array [1.0])
                                   :px (double-array [1.0])
                                   :py (double-array [1.0])
                                   :pz (double-array [1.0])
                                   :vx (double-array [1.0])
                                   :vy (double-array [1.0])})))
    (is (not (lfield/physics-soa? {:n 1 :eids [1]
                                   :mass (double-array [1.0])
                                   :radius (double-array [1.0])
                                   :px (double-array [1.0])
                                   :py (double-array [1.0])
                                   :pz (double-array [1.0])
                                   :vx (double-array [1.0])
                                   :vy (double-array [1.0])
                                   :vz "not-an-array"})))))

(deftest test-soa-validation-can-be-disabled
  (testing "Validation can be disabled for release runs"
    (let [w (-> (seeded-world 5)
                (assoc :phase0/validate-soa? false)
                spatial/spatial-index
                cache/build-physics-soa)]
      (is (contains? w :phase0/physics-soa))
      (is (pos? (:n (:phase0/physics-soa w)))))))

(deftest test-soa-fallback-chain
  (testing "gravity-acceleration falls back through soa -> spatial-items -> world->bodies"
    (let [w0 (seeded-world 10)
          G 6.6743e-11 theta 0.5 soft 1e14
          gravity (orbital/gravity-acceleration G theta soft)
          ;; soa path
          w-soa (-> w0 spatial/spatial-index cache/build-physics-soa)
          ;; spatial-items path (no soa)
          w-items (spatial/spatial-index w0)
          ;; raw world path (no spatial-items, no soa)
          w-raw w0
          run (:run gravity)]
      (is (map? (run w-soa)))
      (is (map? (run w-items)))
      (is (map? (run w-raw))))))
