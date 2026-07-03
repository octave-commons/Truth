(ns infra.camera
  "Orbital camera for the Gates of Truth renderer.

   Computes view/projection matrices and drives the camera from the live ECS
   world. Supports three tracking modes — :manual (third-person follow),
   :track-largest-cluster and :fit-all — and owns the Phase 0 view scale that
   maps simulation metres to render units."
  (:require
   [clojure.math :as math]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.player :as player]
   [shape.spatial :as sp]))

;; ---------------------------------------------------------------------------
;; View scale
;; ---------------------------------------------------------------------------

(def ^:const phase0-view-scale
  "World metres per render unit for the Phase 0 view."
  1.0e15)

;; ---------------------------------------------------------------------------
;; Math helpers
;; ---------------------------------------------------------------------------

(defn deg->rad
  "Convert degrees to radians."
  [d]
  (* d (/ math/PI 180.0)))

(defn normalize
  "Return a unit vector in the direction of `v`; default to +z for the zero
   vector."
  [v]
  (let [l (sp/len v)]
    (if (pos? l)
      (sp/v* v (/ 1.0 l))
      [0.0 0.0 1.0])))

(defn cross
  "Cross product of two 3-vectors."
  [a b]
  (sp/cross a b))

(defn perspective
  "Build a column-major 4x4 perspective projection matrix."
  [fov-deg aspect near far]
  (let [f (/ 1.0 (math/tan (/ (deg->rad fov-deg) 2.0)))
        nf (/ 1.0 (- near far))]
    (float-array [(/ f aspect) 0.0 0.0 0.0
                  0.0 f 0.0 0.0
                  0.0 0.0 (* (+ far near) nf) -1.0
                  0.0 0.0 (* 2.0 far near nf) 0.0])))

(defn look-at
  "Build a column-major 4x4 view matrix that looks from `eye` toward `center`
   with `up` as the world up direction."
  [eye center up]
  (let [f (normalize (sp/v- center eye))
        s (normalize (cross f up))
        u (cross s f)]
    (float-array [(nth s 0) (nth u 0) (- (nth f 0)) 0.0
                  (nth s 1) (nth u 1) (- (nth f 1)) 0.0
                  (nth s 2) (nth u 2) (- (nth f 2)) 0.0
                  (- (sp/dot s eye)) (- (sp/dot u eye)) (sp/dot f eye) 1.0])))

;; ---------------------------------------------------------------------------
;; Camera record and local motion
;; ---------------------------------------------------------------------------

(defrecord Camera
           [position yaw pitch distance target])

(defn make-camera
  "Create an orbital camera. `distance` is the initial orbit radius in render
   units."
  ([] (make-camera 50.0))
  ([distance]
   (->Camera (sp/vec3 0.0 (* distance 0.33) distance) -90.0 -20.0 distance (sp/vec3 0.0 0.0 0.0))))

(defn camera-forward
  "World-space forward direction of `camera` (from camera toward target)."
  [camera]
  (let [yaw-rad   (deg->rad (:yaw camera))
        pitch-rad (deg->rad (:pitch camera))
        cp        (math/cos pitch-rad)]
    [(- (* cp (math/cos yaw-rad)))
     (- (math/sin pitch-rad))
     (- (* cp (math/sin yaw-rad)))]))

(defn observer-render-position
  "The observer (player spark/mote) position in render units, or the origin when
   there is no observer."
  [world]
  (if-let [obs (player/get-observer world)]
    (mapv #(/ (double %) phase0-view-scale) (:position obs))
    [0.0 0.0 0.0]))

(defn camera-horizontal-basis
  "Horizontal forward and right vectors for `camera`, both normalized.

   Forward is the projection of the camera→target direction onto the xz-plane.
   Right is forward × world-up. Used for movement in :manual mode so WASD moves
   the mote relative to the camera's horizontal facing."
  [camera]
  (let [[fx _ fz] (camera-forward camera)
        fwd-h [fx 0.0 fz]
        len   (sp/len fwd-h)]
    (if (pos? len)
      (let [forward (sp/v* fwd-h (/ 1.0 len))
            right   (normalize (cross forward [0.0 1.0 0.0]))]
        {:forward forward :right right})
      {:forward [0.0 0.0 1.0] :right [1.0 0.0 0.0]})))

(defn update-camera-position
  "Recompute :position from :target, :distance, :yaw and :pitch."
  [camera]
  (let [yaw-rad   (deg->rad (:yaw camera))
        pitch-rad (deg->rad (:pitch camera))
        d         (:distance camera)
        [tx ty tz] (:target camera)
        x (+ tx (* d (math/cos pitch-rad) (math/cos yaw-rad)))
        y (+ ty (* d (math/sin pitch-rad)))
        z (+ tz (* d (math/cos pitch-rad) (math/sin yaw-rad)))]
    (assoc camera :position (sp/vec3 x y z))))

(defn flight-move
  "Apply one frame of flight translation to `camera`.

   `input` is a map {:forward signed :right signed} where +1 means the positive
   key is held (W or D) and -1 means the negative key (S or A). `dt` is elapsed
   wall-clock seconds. Speed is `(:flight-speed settings)` orbit radii per
   second, so flying scales naturally with the current orbit distance.

   Only meaningful in :manual mode; tracking modes overwrite the target each
   frame. Returns `camera` unchanged when no flight key is held."
  [camera input dt settings]
  (let [fwd-input (double (:forward input 0.0))
        rgt-input (double (:right input 0.0))]
    (if (and (zero? fwd-input) (zero? rgt-input))
      camera
      (let [{:keys [forward right]} (camera-horizontal-basis camera)
            speed (* (:distance camera)
                     (double (:flight-speed settings 0.5))
                     (double dt))
            dx (sp/v+ (sp/v* forward (* speed fwd-input))
                      (sp/v* right (* speed rgt-input)))]
        (-> camera
            (update :target sp/v+ dx)
            update-camera-position)))))

(defn observer-move-velocity
  "Physical velocity [m/s] for the observer from camera-relative input.

   `input` is a map {:forward signed :right signed} where +1 means the positive
   key is held (W or D) and -1 means the negative key (S or A). `settings`
   provides :move-speed in m/s. Multiply by `dt` to obtain displacement."
  [camera input settings]
  (let [fwd-input (double (:forward input 0.0))
        rgt-input (double (:right input 0.0))]
    (if (and (zero? fwd-input) (zero? rgt-input))
      [0.0 0.0 0.0]
      (let [{:keys [forward right]} (camera-horizontal-basis camera)
            speed (double (:move-speed settings 3.0e15))]
        (sp/v+ (sp/v* forward (* speed fwd-input))
               (sp/v* right (* speed rgt-input)))))))

;; ---------------------------------------------------------------------------
;; Camera modes and settings
;; ---------------------------------------------------------------------------

(def ^:private camera-modes [:manual :track-largest-cluster :fit-all])

(defn default-camera-settings
  "Default in-game camera configuration. Mutate the window config's
   :camera-settings entry from the REPL, or use the key bindings in the dev
   window."
  []
  {:mode :manual
   :fit-margin 1.6
   :smoothing 0.06
   :fit-percentile 0.90
   :manual-yaw -90.0
   :manual-pitch -20.0
   :move-speed 3.0e15})

(defn cycle-camera-mode
  "Advance to the next camera mode."
  [settings]
  (let [i (.indexOf camera-modes (:mode settings))
        n (count camera-modes)
        next-i (mod (inc i) n)]
    (assoc settings :mode (nth camera-modes next-i))))

(defn adjust-fit-margin
  "Scale the fit margin by `factor`, clamped to a sensible range."
  [settings factor]
  (update settings :fit-margin #(max 1.0 (min 4.0 (* % factor)))))

;; ---------------------------------------------------------------------------
;; World tracking
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
                 [(long (Math/floor (/ (double x) cell-size)))
                  (long (Math/floor (/ (double y) cell-size)))
                  (long (Math/floor (/ (double z) cell-size)))])
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
  (let [fov (deg->rad fov-deg)]
    (* radius margin (/ 1.0 (Math/tan (/ fov 2.0))))))

(defn update-camera-for-world
  "Update `camera` based on the world and camera settings. Pure: returns a new
   Camera. In :manual mode the camera is unchanged except for position recalc."
  [camera world settings]
  (case (:mode settings :fit-all)
    :manual
    (-> camera
        (assoc :target (observer-render-position world))
        update-camera-position)

    :track-largest-cluster
    (let [bodies (bodies->render world phase0-view-scale)
          ;; cell size ~ a few render units; with view-scale 1e15 this frames
          ;; the local star-forming region rather than the whole cloud.
          cluster (largest-mass-cluster bodies 8.0)
          target  (:center cluster [0.0 0.0 0.0])
          radius  (max 5.0 (:radius cluster))
          desired-dist (distance-for-radius radius 60.0 (:fit-margin settings 1.6))
          t       (double (:smoothing settings 0.06))
          target' (vlerp (:target camera [0.0 0.0 0.0]) target t)
          dist'   (lerp (:distance camera) desired-dist t)]
      (-> camera
          (assoc :target target'
                 :distance dist'
                 :yaw (double (:manual-yaw settings -90.0))
                 :pitch (double (:manual-pitch settings -20.0)))
          update-camera-position))

    :fit-all
    (let [bodies (bodies->render world phase0-view-scale)
          bounds (fit-all-bounds bodies (:fit-percentile settings 0.90))
          target (:center bounds [0.0 0.0 0.0])
          radius (max 10.0 (:radius bounds))
          desired-dist (distance-for-radius radius 60.0 (:fit-margin settings 1.6))
          t      (double (:smoothing settings 0.06))
          target' (vlerp (:target camera [0.0 0.0 0.0]) target t)
          dist'   (lerp (:distance camera) desired-dist t)]
      (-> camera
          (assoc :target target'
                 :distance dist'
                 :yaw (double (:manual-yaw settings -90.0))
                 :pitch (double (:manual-pitch settings -20.0)))
          update-camera-position))))
