(ns infra.inspect-test
  "Tests for inspection / picking transforms in infra.inspect."
  (:require
    [clojure.test :refer [deftest testing is]]
    [domain.ecs.core :as ecs]
    [domain.stellar :as stellar]
    [infra.camera :as cam]
    [infra.inspect :as inspect]
    [infra.render :as render]
    [infra.render.units :as units]
    [shape.spatial :as sp]))

(deftest test-screen-ray-projects-through-center
  (testing "Center screen ray points along the camera forward axis"
    (let [camera (cam/make-camera 50.0)
          ctx    (units/make-context camera {:width 1280 :height 720})
          {:keys [ro rd]} (inspect/screen->ray ctx 640.0 360.0)]
      (is (= (:position camera) ro) "ray origin is the camera position")
      (is (< (Math/abs (- (sp/len rd) 1.0)) 1e-6) "ray direction is normalized"))))

(deftest test-project-point-round-trip
  (testing "A render point projects to screen and the ray through that pixel lands near it"
    (let [camera (-> (cam/make-camera 50.0) (assoc :pitch 0.0) cam/update-camera-position)
          ctx    (units/make-context camera {:width 1280 :height 720})
          p      [0.0 0.0 20.0]
          [sx sy depth] (inspect/project-point ctx p)]
      (is (number? sx))
      (is (number? sy))
      (is (pos? depth) "depth is positive in front of the camera")
      (let [{:keys [ro rd]} (inspect/screen->ray ctx sx sy)
            t       (/ depth (sp/dot rd [0.0 0.0 1.0]))
            closest (sp/v+ ro (sp/v* rd t))]
        (is (< (sp/dist p closest) 1e-3) "ray at projected depth lands near original point")))))

(deftest test-cursor-world-on-target-plane
  (testing "Cursor at screen center returns a world point on the target depth plane"
    (let [camera (cam/make-camera 50.0)
          ctx    (units/make-context camera {:width 1280 :height 720})
          wp     (inspect/cursor->world ctx 640.0 360.0)]
      (is (every? number? wp) "world point has three numeric components")
      (is (every? #(not (Double/isNaN %)) wp) "no NaNs"))))

(deftest test-pick-entity-selects-under-cursor
  (testing "A body directly under the screen center is picked"
    (let [camera (-> (cam/make-camera 50.0) (assoc :pitch 0.0) cam/update-camera-position)
          ctx    (units/make-context camera {:width 1280 :height 720})
          body   {:entity 1 :render-mode :body :position [0.0 0.0 20.0] :radius 5.0}
          picked (inspect/pick-entity ctx [body] 640.0 360.0)]
      (is (= 1 picked) "center-screen body is picked")))
  (testing "A body far from the cursor is not picked"
    (let [camera (-> (cam/make-camera 50.0) (assoc :pitch 0.0) cam/update-camera-position)
          ctx    (units/make-context camera {:width 1280 :height 720})
          body   {:entity 2 :render-mode :body :position [0.0 0.0 20.0] :radius 1.0}
          picked (inspect/pick-entity ctx [body] 10.0 10.0)]
      (is (nil? picked) "off-cursor body is not picked"))))

(deftest test-selected-shape
  (testing "selected-shape finds the body shape for an entity"
    (let [bodies [{:entity 7 :render-mode :body :position [1 2 3]}
                  {:entity 8 :render-mode :particle :position [4 5 6]}]]
      (is (= 7 (:entity (inspect/selected-shape bodies 7))))
      (is (nil? (inspect/selected-shape bodies 8)) "particles are not selectable"))))

(deftest test-halo-shapes
  (testing "Halo is a closed ring of :line segments around the center"
    (let [camera (cam/make-camera 50.0)
          ctx    (units/make-context camera {:width 1280 :height 720})
          halo   (inspect/halo-shapes [0.0 0.0 0.0] 2.0 ctx [1.0 1.0 1.0] 16)]
      (is (= 32 (count halo)) "16 segments → 32 vertices")
      (is (every? #(= :line (:render-mode %)) halo))
      (is (every? #(= [1.0 1.0 1.0] (:color %)) halo)))))

(deftest test-inspector-card
  (testing "Inspector card is anchored to the body's screen position"
    (let [[w eid] (stellar/spawn-clump (ecs/empty-world)
                     {:position [0.0 0.0 -3.0e15]
                      :mass 2e30 :radius 1e9
                      :matter-state :star
                      :temperature 5800.0})
          camera (cam/make-camera 50.0)
          ctx    (units/make-context camera {:width 1280 :height 720})
          bodies (render/phase0-bodies-from-world w)
          card   (inspect/inspector-card ctx w eid bodies)]
      (is (map? card))
      (is (seq (:rects card)) "card has background rectangles")
      (is (seq (:text card)) "card has text lines")
      (is (some #(re-find #"Msun" (:text %)) (:text card)) "mass is shown in solar units"))))
