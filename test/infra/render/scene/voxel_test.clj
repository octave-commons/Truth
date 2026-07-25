(ns infra.render.scene.voxel-test
  "Tests for the voxel band render path (kanban/tasks/voxel-band-render-path.md):
   the pure band -> cube-shape projection this card adds. The band only
   materializes live after the commitment horizon (slow to reach in a real
   run), so every test here hand-builds a synthetic committed world with a
   `c/voxel-band` via the ECS API directly, per the card's testing
   instruction."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.voxel.band :as band]
   [law.voxel :as voxel]
   [infra.camera :as cam]
   [infra.render.units :as units]
   [infra.render.color :as rcolor]
   [infra.render.scene.voxel :as sv]))

(def ^:private ctx
  "Scale-1.0 render context so world-space and render-space positions are
   identical and the assertions can compare against `band/voxel-center`
   directly, with no scale-factor arithmetic in the test itself."
  (units/make-context 1.0 (cam/make-camera) {:width 1 :height 1}))

(defn- voxel [material]
  {:material material :density 3000.0 :temperature 500.0 :state :solid :cohesion 1.0e7})

(def ^:private body-pos [1000.0 2000.0 3000.0])

(defn- committed-world
  "A world with one entity: `c/position` body-pos, `c/commitment-state
   :committed`, and a hand-built `c/voxel-band` with three resolved cells
   (basalt/ice/ore) and one carved cell (offset [2 2 2] -> nil, per
   `domain.voxel.band/materialize`'s carve representation)."
  []
  (let [[world eid] (ecs/spawn (ecs/empty-world))]
    [eid
     (-> world
         (ecs/put-component eid c/position body-pos)
         (ecs/put-component eid c/commitment-state :committed)
         (ecs/put-component eid c/voxel-band
                            {:spec {}
                             :voxels {[0 0 0] (voxel :basalt)
                                      [1 0 0] (voxel :ice)
                                      [0 1 0] (voxel :ore)
                                      [2 2 2] nil}
                             :touched {}}))]))

(deftest voxel-cube-shapes-empty-cases
  (testing "no committed world at all -> no shapes"
    (is (= [] (sv/voxel-cube-shapes ctx (ecs/empty-world)))))
  (testing "committed world with no c/voxel-band -> no shapes"
    (let [[world eid] (ecs/spawn (ecs/empty-world))
          world' (-> world
                     (ecs/put-component eid c/position [0.0 0.0 0.0])
                     (ecs/put-component eid c/commitment-state :committed))]
      (is (= [] (sv/voxel-cube-shapes ctx world')))))
  (testing "committed world with an EMPTY c/voxel-band -> no shapes"
    (let [[world eid] (ecs/spawn (ecs/empty-world))
          world' (-> world
                     (ecs/put-component eid c/position [0.0 0.0 0.0])
                     (ecs/put-component eid c/commitment-state :committed)
                     (ecs/put-component eid c/voxel-band {:spec {} :voxels {} :touched {}}))]
      (is (= [] (sv/voxel-cube-shapes ctx world'))))))

(deftest voxel-cube-shapes-non-empty-band
  (let [[_eid world] (committed-world)
        shapes (sv/voxel-cube-shapes ctx world)]
    (testing "one cube per resolved cell, carved cells emit nothing"
      (is (= 3 (count shapes))))
    (testing "each cube's world-space centre is body-position + voxel-center(offset)"
      (doseq [[offset _v] [[[0 0 0] :basalt] [[1 0 0] :ice] [[0 1 0] :ore]]]
        (let [expected (mapv + body-pos (band/voxel-center offset))
              hit       (some #(when (= expected (:position %)) %) shapes)]
          (is (some? hit) (str "no cube at expected centre for offset " offset)))))
    (testing "materials map to distinguishable, correct colors"
      (let [by-material (into {} (map (fn [s] [(:material s) (:color s)])) shapes)]
        (is (= (rcolor/voxel-material-color :basalt) (get by-material :basalt)))
        (is (= (rcolor/voxel-material-color :ice) (get by-material :ice)))
        (is (= (rcolor/voxel-material-color :ore) (get by-material :ore)))
        (is (apply distinct? (vals by-material))
            "three different materials read as three different colors")))
    (testing "every shape is a positioned, radius-carrying voxel-cube body shape"
      (is (every? #(= :voxel-cube (:render-mode %)) shapes))
      (is (every? #(pos? (double (:radius %))) shapes))
      (is (every? #(= 3 (count (:position %))) shapes)))))

(deftest voxel-cube-shapes-scale
  (testing "cube radius is half the canonical voxel edge, true-scale (no exaggeration)"
    (let [[_eid world] (committed-world)
          shapes (sv/voxel-cube-shapes ctx world)
          expected-r (units/phys->body-render-radius ctx (/ voxel/canonical-voxel-edge-m 2.0))]
      (is (every? #(= expected-r (:radius %)) shapes)))))
