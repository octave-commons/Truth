(ns infra.camera.navigation.tracking
  "World-aware camera tracking and framing for the Gates of Truth renderer.

   Computes targets for the automated camera modes — selected-body follow,
   largest-mass cluster track, and fit-all framing — from the live ECS world.
   Every public function is pure: given a camera, world, and settings it returns
   a new camera value.

   Depends on infra.camera.navigation.input for the Camera record and orbital
   position math, and on infra.camera.projection for the Phase 0 view scale."
  (:require
   [clojure.math :as math]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.player :as player]
   [infra.camera.navigation.input :as input]
   [infra.camera.projection :as p]
   [shape.spatial :as sp]))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- lerp
  "Linear interpolation between scalars a and b by t."
  [a b t]
  (+ a (* (- b a) t)))

(defn- vlerp
  "Component-wise lerp between 3-vectors a and b by t."
  [a b t]
  (mapv #(lerp %1 %2 t) a b))

(defn- weighted-centroid
  "Mass-weighted centroid of [[position mass] ...] in render units."
  [bodies]
  (let [[sx sy sz m]
        (reduce (fn [[ax ay az am] [[x y z] m]]
                  [(+ ax (* x m)) (+ ay (* y m)) (+ az (* z m)) (+ am m)])
                [0.0 0.0 0.0 0.0] bodies)]
    (if (pos? m)
      [(/ sx m) (/ sy m) (/ sz m)]
      [0.0 0.0 0.0])))

(defn- bounding-radius
  "Radius of a sphere centered at `center` that contains all bodies."
  [center bodies]
  (if (seq bodies)
    (reduce max 0.0 (map #(sp/dist center (first %)) bodies))
    0.0))

;; ---------------------------------------------------------------------------
;; World query and framing
;; ---------------------------------------------------------------------------

(defn observer-render-position
  "The observer (player spark/mote) position in render units, or the origin when
   there is no observer."
  [world]
  (if-let [obs (player/get-observer world)]
    (mapv #(/ (double %) p/phase0-view-scale) (:position obs))
    [0.0 0.0 0.0]))

(defn bodies->render
  "Project ECS bodies into [[render-position mass] ...]."
  [world scale]
  (->> (ecs/all-of world c/position c/mass)
       (mapv (fn [[_ comps]]
               [(mapv #(/ (double %) scale) (comps c/position))
                (double (comps c/mass))]))))

(defn largest-mass-cluster
  "Find the densest mass cluster using a uniform grid. Returns
   {:center [x y z] :radius r :mass m} in render units.

   `cell-size` controls the clustering scale; pass a value comparable to the
   desired cluster radius (e.g. a few times the typical body separation)."
  [bodies cell-size]
  (if (empty? bodies)
    {:center [0.0 0.0 0.0] :radius 0.0 :mass 0.0}
    (let [cell (fn [[x y z]]
                 [(long (math/floor (/ (double x) cell-size)))
                  (long (math/floor (/ (double y) cell-size)))
                  (long (math/floor (/ (double z) cell-size)))])
          grid (group-by (fn [[pos _]] (cell pos)) bodies)
          ;; include a body in the winning cell and its 26 neighbours so the
          ;; cluster does not get sliced at grid boundaries
          [win-cell _] (apply max-key #(reduce + 0.0 (map second (val %))) grid)
          [wx wy wz] win-cell
          neighbours (for [dx [-1 0 1] dy [-1 0 1] dz [-1 0 1]]
                       [(+ wx dx) (+ wy dy) (+ wz dz)])
          cluster-bodies (mapcat #(get grid %) neighbours)
          center (weighted-centroid cluster-bodies)
          radius (bounding-radius center cluster-bodies)
          total-mass (reduce + 0.0 (map second cluster-bodies))]
      {:center center :radius radius :mass total-mass})))

(defn fit-all-bounds
  "Bounding sphere containing `percentile` of bodies by distance from the
   overall centroid. Returns {:center [x y z] :radius r} in render units."
  [bodies percentile]
  (if (empty? bodies)
    {:center [0.0 0.0 0.0] :radius 0.0}
    (let [center (weighted-centroid bodies)
          rs     (sort (map #(sp/dist center (first %)) bodies))
          idx    (int (* (count rs) percentile))
          radius (nth rs (min idx (dec (count rs))))]
      {:center center :radius radius})))

(defn distance-for-radius
  "Orbit distance (render units) needed to fit a sphere of `radius` with the
   given field-of-view and margin."
  [radius fov-deg margin]
  (let [fov (p/deg->rad fov-deg)]
    (* radius margin (/ 1.0 (math/tan (/ fov 2.0))))))

(defn min-approach-distance
  "Closest orbit distance [ru] the camera may take to a body of render radius
   `r-ru`: a couple of radii out so the globe fills the view without clipping,
   floored so a degenerate radius can't pin the camera to a point."
  [r-ru]
  (max (* 2.5 (double (or r-ru 0.0))) 1.0e-7))

;; ---------------------------------------------------------------------------
;; Camera mode updates
;; ---------------------------------------------------------------------------

(defn update-camera-manual
  "Re-centre the manual camera on the observer render position."
  [camera world]
  (-> camera
      (assoc :target (observer-render-position world))
      input/update-camera-position))

(defn- follow-selection-target
  "Compute the target for :follow-selection. Snaps when close, lerps otherwise."
  [camera world settings eid]
  (let [pos (ecs/get-component world eid c/position)
        target (mapv #(/ (double %) p/phase0-view-scale) pos)
        r-ru (/ (double (or (ecs/get-component world eid c/radius) 0.0))
                p/phase0-view-scale)
        t (double (:smoothing settings 0.06))
        close? (< (:distance camera) 1000.0)]
    (if (or close?
            (< (sp/dist (:target camera [0.0 0.0 0.0]) target)
               (* 4.0 (max r-ru 1.0e-7))))
      target
      (vlerp (:target camera [0.0 0.0 0.0]) target (max t 0.15)))))

(defn update-camera-follow-selection
  "Ride on the selected body, clamping orbit distance no closer than a couple of
   radii. Falls back to manual behaviour when the selection is gone."
  [camera world settings]
  (let [eid (:follow-eid settings)]
    (if-not (and eid (ecs/get-component world eid c/position))
      (update-camera-manual camera world)
      (let [r-ru (/ (double (or (ecs/get-component world eid c/radius) 0.0))
                    p/phase0-view-scale)
            target' (follow-selection-target camera world settings eid)
            dist' (max (:distance camera) (min-approach-distance r-ru))]
        (-> camera
            (assoc :target target' :distance dist')
            input/update-camera-position)))))

(defn- tracking-camera-update
  "Shared body for tracking modes: compute target/distance from bodies, lerp
   toward them, and reset yaw/pitch to the manual defaults."
  [camera world settings target-fn radius-floor]
  (let [bodies (bodies->render world p/phase0-view-scale)
        target (target-fn bodies)
        radius (max radius-floor (:radius target))
        desired-dist (distance-for-radius radius 60.0 (:fit-margin settings 1.6))
        t (double (:smoothing settings 0.06))
        target' (vlerp (:target camera [0.0 0.0 0.0]) (:center target) t)
        dist' (lerp (:distance camera) desired-dist t)]
    (-> camera
        (assoc :target target'
               :distance dist'
               :yaw (double (:manual-yaw settings -90.0))
               :pitch (double (:manual-pitch settings -20.0)))
        input/update-camera-position)))

(defn update-camera-track-cluster
  "Frame the densest mass cluster."
  [camera world settings]
  (tracking-camera-update
   camera world settings
   #(let [cluster (largest-mass-cluster % 8.0)]
      {:center (:center cluster [0.0 0.0 0.0])
       :radius (:radius cluster)})
   5.0))

(defn update-camera-fit-all
  "Frame the whole system."
  [camera world settings]
  (tracking-camera-update
   camera world settings
   #(fit-all-bounds % (:fit-percentile settings 0.90))
   10.0))
