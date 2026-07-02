(ns infra.render.units-test
  "Tests for the pure coordinate-transform layer in infra.render.units."
  (:require
    [clojure.test :refer [deftest testing is]]
    [infra.camera :as cam]
    [infra.render.units :as units]
    [shape.spatial :as sp]))

(deftest test-make-context
  (testing "Context carries scale, camera and viewport"
    (let [cam (cam/make-camera)
          ctx (units/make-context cam {:width 1280 :height 720})]
      (is (= cam/phase0-view-scale (:scale ctx)))
      (is (= cam (:camera ctx)))
      (is (= {:width 1280 :height 720} (:viewport ctx)))))
  (testing "Custom scale overrides the default"
    (let [ctx (units/make-context 1e10 (cam/make-camera) {:width 1 :height 1})]
      (is (= 1e10 (:scale ctx))))))

(deftest test-world-render-round-trip
  (testing "world → render → world round-trips within floating-point tolerance"
    (let [ctx (units/make-context (cam/make-camera) {:width 1 :height 1})
          p   [3.0e15 1.5e15 -2.0e15]]
      (is (every? #(< % 1.0) (map - p (units/render->world ctx (units/world->render ctx p))))
          "round-trip stays within 1 m"))))

(deftest test-phys-render-radius
  (testing "Radius mapping is monotonic and reference-sized"
    (let [ctx (units/make-context (cam/make-camera) {:width 1 :height 1})
          a   (units/phys->render-radius ctx 1.0e13)
          b   (units/phys->render-radius ctx 3.0e13)
          c   (units/phys->render-radius ctx 1.0e15)]
      (is (< a b c) "larger physical radius → larger render radius")
      (is (> b 0.0) "reference radius produces a positive render radius")))
  (testing "Non-positive radii clamp to the visible minimum"
    (let [ctx (units/make-context (cam/make-camera) {:width 1 :height 1})]
      (is (= 0.001 (units/phys->render-radius ctx 0.0)))
      (is (= 0.001 (units/phys->render-radius ctx nil))))))

(deftest test-screen-render-ray-normalized
  (testing "screen → render ray returns a normalized direction"
    (let [ctx (units/make-context (cam/make-camera) {:width 1280 :height 720})
          {:keys [rd]} (units/screen->render-ray ctx 640.0 360.0)]
      (is (number? (sp/len rd)))
      (is (< (Math/abs (- (sp/len rd) 1.0)) 1e-6) "ray direction is unit length"))))

(deftest test-render-screen-identity
  (testing "A point on the screen's center ray projects back through the ray"
    (let [ctx (units/make-context (cam/make-camera) {:width 1280 :height 720})
          {:keys [ro rd]} (units/screen->render-ray ctx 640.0 360.0)
          p   (sp/v+ ro (sp/v* rd 10.0))
          [sx sy _depth] (units/render->screen ctx p)]
      (is (< (Math/abs (- sx 640.0)) 0.1) "center ray projects to center x")
      (is (< (Math/abs (- sy 360.0)) 0.1) "center ray projects to center y"))))

(deftest test-camera-basis
  (testing "camera-basis yields an orthonormal right-handed frame"
    (let [ctx (units/make-context (cam/make-camera) {:width 1 :height 1})
          {:keys [fwd right up]} (units/camera-basis ctx)]
      (is (< (Math/abs (- (sp/len fwd) 1.0)) 1e-6))
      (is (< (Math/abs (- (sp/len right) 1.0)) 1e-6))
      (is (< (Math/abs (- (sp/len up) 1.0)) 1e-6))
      (is (< (Math/abs (sp/dot fwd right)) 1e-6) "fwd ⟂ right")
      (is (< (Math/abs (sp/dot fwd up)) 1e-6) "fwd ⟂ up")
      (is (pos? (sp/dot (sp/cross right fwd) up)) "right × fwd ≈ up"))))
