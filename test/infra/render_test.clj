(ns infra.render-test
  "Tests for the single Phase 0 render projection (infra.render). These cover the
   pure geometry/colour fns that turn the ECS world into render shapes — regime
   tinting, volumetric fog, and magnetic field lines. GL calls are not exercised."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.stellar :as stellar]
   [infra.render :as r]))

(deftest test-tint-color
  (testing "Tinting keeps colours in [0,1] and shifts by regime"
    (is (every? #(<= 0.0 % 1.0) (r/tint-color [0.8 0.6 0.9] :mhd-dominated)))
    (is (= [0.55 0.45 0.75] (r/tint-color [0.55 0.45 0.75] :gravity-hydro))
        "gravity-hydro is the neutral tint")
    (let [warm (r/tint-color [0.5 0.5 0.5] :gravitationally-unstable)]
      (is (> (first warm) (nth warm 2)) "collapsing clumps read warmer (red > blue)"))))

(deftest test-field-line
  (testing "A clump with a field yields two endpoints straddling its centre"
    (let [seg (r/field-line [0.0 0.0 0.0] 1.0 [0.0 0.0 1.0e-9])]
      (is (= 2 (count seg)))
      (is (every? #(= :line (:render-mode %)) seg))
      (is (neg? (nth (:position (first seg)) 2)))
      (is (pos? (nth (:position (second seg)) 2)))))
  (testing "A stronger (amplified) field draws a brighter line"
    (let [weak   (r/field-line [0.0 0.0 0.0] 1.0 [0.0 0.0 1.0e-9])
          strong (r/field-line [0.0 0.0 0.0] 1.0 [0.0 0.0 1.0e-3])]
      (is (> (nth (:color (first strong)) 2) (nth (:color (first weak)) 2)))))
  (testing "No field means no line"
    (is (nil? (r/field-line [0.0 0.0 0.0] 1.0 [0.0 0.0 0.0])))))

(deftest test-nebula-fog
  (testing "Fog puffs are tagged :particle and lie within the extent"
    (let [fog (r/nebula-fog {:center [0.0 0.0 0.0] :extent 5.0
                             :color [0.5 0.4 0.7] :count 50})]
      (is (= 50 (count fog)))
      (is (every? #(= :particle (:render-mode %)) fog))
      (is (every? #(pos? (:size %)) fog))
      (is (every? #(<= (Math/sqrt (apply + (map * (:position %) (:position %)))) 5.0001) fog)))))

(deftest test-phase0-projection
  (testing "Gas → fog, protostar → fog + field line, star → shaded body"
    (let [[w1 _] (stellar/spawn-clump (ecs/empty-world)
                   {:position [0.0 0.0 0.0] :mass 1e28 :radius 1e13
                    :matter-state :nebula})
          [w2 _] (stellar/spawn-clump w1
                   {:position [2e15 0.0 0.0] :mass 2e30 :radius 1e14
                    :matter-state :protostar})
          [w3 _] (stellar/spawn-clump w2
                   {:position [4e16 0.0 0.0] :mass 2e30 :radius 1e9
                    :matter-state :star})
          shapes (r/phase0-bodies-from-world w3)
          modes  (frequencies (map :render-mode shapes))]
      (is (pos? (get modes :particle 0)) "gas/protostar produce volumetric fog")
      (is (pos? (get modes :line 0))     "the protostar produces magnetic field lines")
      (is (pos? (get modes :body 0))     "the star produces a shaded body"))))


(deftest test-largest-mass-cluster
  (testing "Finds the densest cluster and ignores isolated bodies"
    (let [bodies [[[0.0 0.0 0.0] 100.0]
                  [[1.0 0.0 0.0] 100.0]
                  [[2.0 0.0 0.0] 100.0]
                  [[100.0 0.0 0.0] 1.0]]
          cluster (r/largest-mass-cluster bodies 2.0)]
      (is (> (:mass cluster) 250.0))
      (is (< (:radius cluster) 5.0))
      (is (every? #(< -0.1 % 3.0) (:center cluster)))))
  (testing "Empty body list returns zeroed bounds"
    (let [cluster (r/largest-mass-cluster [] 2.0)]
      (is (= 0.0 (:mass cluster) (:radius cluster)))
      (is (= [0.0 0.0 0.0] (:center cluster))))))

(deftest test-fit-all-bounds
  (testing "Bounding sphere contains the requested percentile of bodies"
    (let [bodies (mapv (fn [i] [[(double i) 0.0 0.0] 1.0]) (range 100))
          bounds (r/fit-all-bounds bodies 0.90)]
      (is (>= (:radius bounds) 40.0))
      (is (<= (:radius bounds) 50.0))
      (is (= [49.5 0.0 0.0] (:center bounds)))))
  (testing "Empty body list returns zeroed bounds"
    (let [bounds (r/fit-all-bounds [] 0.90)]
      (is (= 0.0 (:radius bounds)))
      (is (= [0.0 0.0 0.0] (:center bounds))))))

(deftest test-distance-for-radius
  (testing "Distance scales with radius and margin, inversely with FOV"
    (let [d60 (r/distance-for-radius 10.0 60.0 1.0)
          d90 (r/distance-for-radius 10.0 90.0 1.0)
          d2x (r/distance-for-radius 10.0 60.0 2.0)]
      (is (pos? d60))
      (is (< d90 d60) "wider FOV needs closer camera")
      (is (> d2x d60) "larger margin needs farther camera"))))

(deftest test-camera-settings-cycle
  (testing "Camera mode cycles through the three modes"
    (let [s0 (r/default-camera-settings)
          s1 (r/cycle-camera-mode s0)
          s2 (r/cycle-camera-mode s1)
          s3 (r/cycle-camera-mode s2)]
      (is (= :track-largest-cluster (:mode s0)))
      (is (= :fit-all (:mode s1)))
      (is (= :manual (:mode s2)))
      (is (= :track-largest-cluster (:mode s3)))))
  (testing "Fit margin is clamped"
    (let [s (r/default-camera-settings)]
      (is (>= (:fit-margin (r/adjust-fit-margin s 0.1)) 1.0))
      (is (<= (:fit-margin (r/adjust-fit-margin s 10.0)) 4.0)))))

(deftest test-update-camera-track-largest-cluster
  (testing "Camera target moves toward the largest mass cluster"
    (let [[w _] (stellar/spawn-clump (ecs/empty-world)
                   {:position [3e15 0.0 0.0] :mass 2e30 :radius 1e14
                    :matter-state :star})
          cam0 (r/make-camera)
          cam1 (r/update-camera-for-world cam0 w (r/default-camera-settings))]
      (is (not= (:target cam0) (:target cam1)))
      (is (> (first (:target cam1)) 0.0) "target shifts toward positive x")
      (is (pos? (:distance cam1)) "distance remains positive"))))
