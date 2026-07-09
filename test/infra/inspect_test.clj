(ns infra.inspect-test
  "Tests for inspection / picking transforms in infra.inspect."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecology :as ecology]
   [domain.ecs.components :as c]
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
      (is (< (abs (- (sp/len rd) 1.0)) 1e-6) "ray direction is normalized"))))

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
            fwd     (:fwd (units/camera-basis ctx))
            t       (/ depth (sp/dot rd fwd))
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
          ;; z-up camera at pitch 0 looks along +y, so a center-screen body sits
          ;; on the +y axis in front of the camera (not +z).
          body   {:entity 1 :render-mode :body :position [0.0 20.0 0.0] :radius 5.0}
          picked (inspect/pick-entity ctx [body] 640.0 360.0)]
      (is (= 1 picked) "center-screen body is picked")))
  (testing "A body far from the cursor is not picked"
    (let [camera (-> (cam/make-camera 50.0) (assoc :pitch 0.0) cam/update-camera-position)
          ctx    (units/make-context camera {:width 1280 :height 720})
          body   {:entity 2 :render-mode :body :position [0.0 20.0 0.0] :radius 1.0}
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
          halo   (inspect/halo-shapes {:center [0.0 0.0 0.0]
                                       :r 2.0
                                       :ctx ctx
                                       :color [1.0 1.0 1.0]
                                       :n 16})]
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

(deftest test-inspector-shows-ecology-stats
  (testing "body-facts includes ecology rows when the body is alive"
    (let [[w eid] (ecs/spawn (ecs/empty-world))
          w (-> (ecs/put-components w eid
                                    {c/mass 6e24 c/radius 6.4e6 c/position [1e16 0 0]
                                     c/velocity [0 0 0] c/body-kind :body/planet
                                     c/matter-state :planet c/temperature 300.0
                                     c/density 5500.0 c/pressure 1e5
                                     c/composition {:H2O 0.1 :C 0.01 :N 0.001}
                                     c/ecology (ecology/make-ecology {:phase :prokaryotic
                                                                      :biomass 0.25
                                                                      :complexity 0.1
                                                                      :stability 0.6
                                                                      :moisture 0.5})})
                (assoc :next-id 1))
          facts (inspect/body-facts w eid)
          labels (set (map first facts))]
      (is (contains? labels "life"))
      (is (contains? labels "biomass"))
      (is (contains? labels "complexity"))
      (is (contains? labels "stability"))
      (is (contains? labels "moisture"))
      (is (some #(re-find #"25%" (second %)) facts) "biomass shown as percent"))))
