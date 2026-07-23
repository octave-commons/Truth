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
   [domain.narrowing :as narrowing]
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

(def ^:const frame-margin
  "Desired orbit distance at full binding-tether engagement, in world render
   radii — shared by the binding tether (`infra.camera.navigation.tether`)
   and the auto camera modes' bind-blend below, so both agree on how close
   'fully bound' frames the world."
  4.0)

;; ---------------------------------------------------------------------------
;; Binding tether reconciliation (narrowing-tether-default-camera-modes)
;;
;; The auto camera modes (:fit-all, :follow-selection) compute their own
;; target/distance every frame from the live bodies, independent of the
;; binding tether — which would otherwise erase any tether pull the instant
;; the next auto-frame overwrites :target/:distance. `blend-toward-binding`
;; is the single reconciliation point: once bound, it lerps the mode's own
;; computed target/distance toward the bound world's frame by
;; `tether-strength`, continuous in binding depth exactly like
;; `infra.camera.navigation.tether/tether-step` — so the tether pulls in
;; :fit-all and :follow-selection without a manual mode switch, and there is
;; no jump-cut at any binding depth including capture.
;; ---------------------------------------------------------------------------

(defn- bound-world-frame
  "The [target-ru distance-ru strength] the binding tether would produce for
   the observer's deepest-bound world, or nil when unbound (no binding, zero
   strength, or the bound world has no position). Mirrors
   `infra.camera.navigation.tether/tether-step`'s target/distance math so the
   auto modes can blend toward the same frame instead of a separate one."
  [world]
  (when-let [[eid b] (narrowing/deepest-binding world)]
    (let [s (narrowing/tether-strength b)]
      (when (pos? s)
        (when-let [pos (ecs/get-component world eid c/position)]
          (let [target (mapv #(/ (double %) p/phase0-view-scale) pos)
                r-ru (/ (double (or (ecs/get-component world eid c/radius) 0.0))
                        p/phase0-view-scale)
                desired (max (* frame-margin r-ru) (min-approach-distance r-ru))]
            [target desired s]))))))

(defn- blend-toward-binding
  "Blend an auto-mode's computed `target`/`dist` toward the bound world's
   frame, weighted DIRECTLY by tether strength (not an additional per-frame
   rate): at strength 0 the auto target/distance pass through unchanged; at
   strength 1.0 (capture) the result IS the bound world's frame — 'the
   camera should track the bound world', literally, once fully engaged.
   Strength itself only ever changes gradually tick to tick (accrual is slow,
   `domain.narrowing/binding-step`), so the blended target is continuous
   across ticks with no jump-cut — the same guarantee
   `infra.camera.navigation.tether/tether-step` makes, applied to a lerp
   weight instead of a lerp rate because the auto modes' own target math is
   an independent, competing pull every frame that a small additional rate
   would not reliably win against. Returns [target dist] unchanged when
   unbound."
  [world target dist]
  (if-let [[b-target b-dist s] (bound-world-frame world)]
    [(vlerp target b-target s) (lerp dist b-dist s)]
    [target dist]))

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
   of radii. Falls back to manual behaviour when the selection is gone. Once
   the observer is bound to a world (`domain.narrowing/deepest-binding`), the
   computed target/distance blend toward the bound world's frame by tether
   strength (`blend-toward-binding`) so the binding tether pulls this mode
   too, without a manual mode switch."
  [camera world settings]
  (let [eid (:follow-eid settings)]
    (if-not (and eid (ecs/get-component world eid c/position))
      (update-camera-manual camera world)
      (let [r-ru (/ (double (or (ecs/get-component world eid c/radius) 0.0))
                    p/phase0-view-scale)
            target' (follow-selection-target camera world settings eid)
            dist' (max (:distance camera) (min-approach-distance r-ru))
            [target'' dist''] (blend-toward-binding world target' dist')]
        (-> camera
            (assoc :target target'' :distance dist'')
            input/update-camera-position)))))

(defn- tracking-camera-update
  "Shared body for tracking modes: compute target/distance from bodies, lerp
   toward them, and reset yaw/pitch to the manual defaults. When `bind-blend?`
   is true and the observer is bound to a world, the result additionally
   blends toward that world's frame by tether strength
   (`blend-toward-binding`) so the binding tether pulls this mode too."
  [camera world settings target-fn radius-floor & {:keys [bind-blend?]}]
  (let [bodies (bodies->render world p/phase0-view-scale)
        target (target-fn bodies)
        radius (max radius-floor (:radius target))
        desired-dist (distance-for-radius radius 60.0 (:fit-margin settings 1.6))
        t (double (:smoothing settings 0.06))
        target' (vlerp (:target camera [0.0 0.0 0.0]) (:center target) t)
        dist' (lerp (:distance camera) desired-dist t)
        [target'' dist''] (if bind-blend?
                            (blend-toward-binding world target' dist')
                            [target' dist'])]
    (-> camera
        (assoc :target target''
               :distance dist''
               :yaw (double (:manual-yaw settings -90.0))
               :pitch (double (:manual-pitch settings -20.0)))
        input/update-camera-position)))

(defn update-camera-track-cluster
  "Frame the densest mass cluster.

   Does NOT reconcile with the binding tether (`bind-blend?` false): the
   cluster-of-mass target and a single bound world are different framings by
   design, so this mode degrades gracefully by simply continuing its own
   framing rather than fighting the tether for the target."
  [camera world settings]
  (tracking-camera-update
   camera world settings
   #(let [cluster (largest-mass-cluster % 8.0)]
      {:center (:center cluster [0.0 0.0 0.0])
       :radius (:radius cluster)})
   5.0))

(defn update-camera-fit-all
  "Frame the whole system. Once the observer is bound to a world, blends
   toward that world's frame by tether strength (`bind-blend?` true) — the
   default startup mode (`:fit-all`) therefore feels the tether without a
   manual mode switch."
  [camera world settings]
  (tracking-camera-update
   camera world settings
   #(fit-all-bounds % (:fit-percentile settings 0.90))
   10.0
   :bind-blend? true))
