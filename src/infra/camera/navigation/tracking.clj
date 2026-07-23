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
   there is no observer. Reads the spark's `c/position` column — the single
   source of truth since spark-redesign card 4."
  [world]
  (if-let [pos (player/observer-position world)]
    (mapv #(/ (double %) p/phase0-view-scale) pos)
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

(def ^:const frame-margin
  "Desired orbit distance at full binding-tether engagement, in world render
   radii — shared with the binding tether (`infra.camera.navigation.tether`)
   so both agree on how close 'fully bound' frames the world."
  4.0)

;; ---------------------------------------------------------------------------
;; Camera mode updates
;; ---------------------------------------------------------------------------

(defn- lerp-toward
  "Lerp `camera`'s :target toward `pos`, matching
   `follow-selection-target`'s smoothing pattern — fast but continuous, never
   a hard snap. Used to damp position jitter (e.g. the observer's spark
   position) before it reaches the camera."
  [camera pos t]
  (vlerp (:target camera [0.0 0.0 0.0]) pos t))

(defn update-camera-manual
  "Re-centre the manual camera on the observer render position.

   Lerps toward the observer position rather than snapping — a hard `assoc`
   here let spark-position jitter (physically integrated under the spark
   redesign) pass straight through to the camera, producing visible bounce."
  [camera world]
  (-> camera
      (assoc :target (lerp-toward camera (observer-render-position world) 0.35))
      input/update-camera-position))

(defn- follow-selection-target
  "Compute the target for :follow-selection: a continuous lerp toward the
   body, faster when close, never a hard center-snap.

   The old behaviour SNAPPED the target to the body's exact center once
   within 1000 render units or 4 body radii — that instantaneous jump-then-
   overlap is what made a bound spark flap in/out of the body sphere every
   frame (spark-planet-binding's root cause). Close range now uses a fast
   but still-continuous lerp rate instead of `target` itself, so the camera
   settles smoothly onto the body without ever teleporting exactly onto it."
  [camera world settings eid]
  (let [pos (ecs/get-component world eid c/position)
        target (mapv #(/ (double %) p/phase0-view-scale) pos)
        r-ru (/ (double (or (ecs/get-component world eid c/radius) 0.0))
                p/phase0-view-scale)
        far-t (double (:smoothing settings 0.06))
        cur (:target camera [0.0 0.0 0.0])
        close? (or (< (:distance camera) 1000.0)
                   (< (sp/dist cur target) (* 4.0 (max r-ru 1.0e-7))))
        t (if close? (max far-t 0.35) (max far-t 0.15))]
    (vlerp cur target t)))

(defn update-camera-follow-selection
  "Ride on the selected body, clamping orbit distance no closer than a couple
   of radii — the player's own scroll-set distance is authoritative; this
   only floors it so the camera cannot clip inside the body. Falls back to
   manual behaviour when the selection is gone."
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
