(ns infra.camera-test
  "Tests for the orbital camera and world-tracking functions in infra.camera.
   These are pure geometry/ECS fns; no OpenGL is exercised."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.stellar :as stellar]
   [infra.camera :as cam]))

(deftest test-update-camera-track-largest-cluster
  (testing "Camera target moves toward the largest mass cluster"
    (let [[w _] (stellar/spawn-clump (ecs/empty-world)
                   {:position [3e15 0.0 0.0] :mass 2e30 :radius 1e14
                    :matter-state :star})
          cam0 (cam/make-camera)
          cam1 (cam/update-camera-for-world cam0 w (cam/default-camera-settings))]
      (is (not= (:target cam0) (:target cam1)))
      (is (> (first (:target cam1)) 0.0) "target shifts toward positive x")
      (is (pos? (:distance cam1)) "distance remains positive"))))

(deftest test-largest-mass-cluster
  (testing "Finds the densest cluster and ignores isolated bodies"
    (let [bodies [[[0.0 0.0 0.0] 100.0]
                  [[1.0 0.0 0.0] 100.0]
                  [[2.0 0.0 0.0] 100.0]
                  [[100.0 0.0 0.0] 1.0]]
          cluster (cam/largest-mass-cluster bodies 2.0)]
      (is (> (:mass cluster) 250.0))
      (is (< (:radius cluster) 5.0))
      (is (every? #(< -0.1 % 3.0) (:center cluster)))))
  (testing "Empty body list returns zeroed bounds"
    (let [cluster (cam/largest-mass-cluster [] 2.0)]
      (is (= 0.0 (:mass cluster) (:radius cluster)))
      (is (= [0.0 0.0 0.0] (:center cluster))))))

(deftest test-fit-all-bounds
  (testing "Bounding sphere contains the requested percentile of bodies"
    (let [bodies (mapv (fn [i] [[(double i) 0.0 0.0] 1.0]) (range 100))
          bounds (cam/fit-all-bounds bodies 0.90)]
      (is (>= (:radius bounds) 40.0))
      (is (<= (:radius bounds) 50.0))
      (is (= [49.5 0.0 0.0] (:center bounds)))))
  (testing "Empty body list returns zeroed bounds"
    (let [bounds (cam/fit-all-bounds [] 0.90)]
      (is (= 0.0 (:radius bounds)))
      (is (= [0.0 0.0 0.0] (:center bounds))))))

(deftest test-distance-for-radius
  (testing "Distance scales with radius and margin, inversely with FOV"
    (let [d60 (cam/distance-for-radius 10.0 60.0 1.0)
          d90 (cam/distance-for-radius 10.0 90.0 1.0)
          d2x (cam/distance-for-radius 10.0 60.0 2.0)]
      (is (pos? d60))
      (is (< d90 d60) "wider FOV needs closer camera")
      (is (> d2x d60) "larger margin needs farther camera"))))

(deftest test-camera-settings-cycle
  (testing "Camera mode cycles through the three modes"
    (let [s0 (cam/default-camera-settings)
          s1 (cam/cycle-camera-mode s0)
          s2 (cam/cycle-camera-mode s1)
          s3 (cam/cycle-camera-mode s2)]
      (is (= :track-largest-cluster (:mode s0)))
      (is (= :fit-all (:mode s1)))
      (is (= :manual (:mode s2)))
      (is (= :track-largest-cluster (:mode s3)))))
  (testing "Fit margin is clamped"
    (let [s (cam/default-camera-settings)]
      (is (>= (:fit-margin (cam/adjust-fit-margin s 0.1)) 1.0))
      (is (<= (:fit-margin (cam/adjust-fit-margin s 10.0)) 4.0)))))

(deftest test-perspective-look-at
  (testing "Perspective and look-at produce sane column-major matrices"
    (let [p (cam/perspective 60.0 1.0 0.1 100.0)
          v (cam/look-at [0.0 0.0 5.0] [0.0 0.0 0.0] [0.0 1.0 0.0])]
      (is (= 16 (count p)))
      (is (= 16 (count v)))
      ;; last column of a view matrix should be the translation/eye term
      (is (not-every? zero? (take 3 (drop 12 v)))))))

(deftest test-camera-forward
  (testing "Forward vector points from camera toward target"
    (let [c (cam/make-camera 10.0)
          f (cam/camera-forward c)]
      (is (> (last f) 0.0) "default camera looks toward +z")
      (is (pos? (reduce + (map * f f))) "forward is non-zero"))))
