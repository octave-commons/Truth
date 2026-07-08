(ns infra.camera
  "Orbital camera for the Gates of Truth renderer.

   Computes view/projection matrices and drives the camera from the live ECS
   world. Supports three tracking modes — :manual (third-person follow),
   :track-largest-cluster and :fit-all — and owns the Phase 0 view scale that
   maps simulation metres to render units.

   This namespace is a thin facade over infra.camera.projection (matrix / view
   math) and infra.camera.navigation (camera movement and world tracking). New
   code may require the sub-namespace directly; this facade preserves the legacy
   public API."
  (:require
   [infra.camera.navigation :as nav]
   [infra.camera.projection :as p]))

;; ---------------------------------------------------------------------------
;; Projection / view math
;; ---------------------------------------------------------------------------

(def phase0-view-scale
  "World metres per render unit for the Phase 0 view."
  p/phase0-view-scale)

(defn deg->rad
  "Convert degrees to radians."
  [d]
  (p/deg->rad d))

(defn normalize
  "Return a unit vector in the direction of `v`; default to +z for the zero
   vector."
  [v]
  (p/normalize v))

(defn cross
  "Cross product of two 3-vectors."
  [a b]
  (p/cross a b))

(defn perspective
  "Build a column-major 4x4 perspective projection matrix."
  [fov-deg aspect near far]
  (p/perspective fov-deg aspect near far))

(defn look-at
  "Build a column-major 4x4 view matrix that looks from `eye` toward `center`
   with `up` as the world up direction."
  [eye center up]
  (p/look-at eye center up))

;; ---------------------------------------------------------------------------
;; Camera record constructors
;; ---------------------------------------------------------------------------

(def ->Camera
  "Positional constructor for the Camera record."
  nav/->Camera)

(def map->Camera
  "Map constructor for the Camera record."
  nav/map->Camera)

;; ---------------------------------------------------------------------------
;; Camera movement and world tracking
;; ---------------------------------------------------------------------------

(defn make-camera
  "Create an orbital camera. `distance` is the initial orbit radius in render
   units. Position is derived from yaw/pitch/distance so it is consistent with
   the z-up orbit geometry."
  ([] (nav/make-camera))
  ([distance] (nav/make-camera distance)))

(defn camera-forward
  "World-space forward direction of `camera` (from camera toward target).

   The world is z-up (the disk lies in the xy-plane, spin axis +z): `yaw` sweeps
   the xy-plane and `pitch` tilts toward ±z."
  [camera]
  (nav/camera-forward camera))

(defn observer-render-position
  "The observer (player spark/mote) position in render units, or the origin when
   there is no observer."
  [world]
  (nav/observer-render-position world))

(defn camera-move-basis
  "Forward and right movement vectors for `camera`, both normalized — FPS /
   free-flight style."
  [camera]
  (nav/camera-move-basis camera))

(defn update-camera-position
  "Recompute :position from :target, :distance, :yaw and :pitch."
  [camera]
  (nav/update-camera-position camera))

(defn flight-move
  "Apply one frame of flight translation to `camera`."
  [camera input dt settings]
  (nav/flight-move camera input dt settings))

(defn observer-move-velocity
  "Physical velocity [m/s] for the observer from camera-relative input."
  [camera input settings]
  (nav/observer-move-velocity camera input settings))

(defn min-approach-distance
  "Closest orbit distance [ru] the camera may take to a body of render radius
   `r-ru`."
  [r-ru]
  (nav/min-approach-distance r-ru))

(defn default-camera-settings
  "Default in-game camera configuration."
  []
  (nav/default-camera-settings))

(defn cycle-camera-mode
  "Advance to the next camera mode."
  [settings]
  (nav/cycle-camera-mode settings))

(defn adjust-fit-margin
  "Scale the fit margin by `factor`, clamped to a sensible range."
  [settings factor]
  (nav/adjust-fit-margin settings factor))

(defn bodies->render
  "Project ECS bodies into [[render-position mass] ...]."
  [world scale]
  (nav/bodies->render world scale))

(defn largest-mass-cluster
  "Find the densest mass cluster using a uniform grid."
  [bodies cell-size]
  (nav/largest-mass-cluster bodies cell-size))

(defn fit-all-bounds
  "Bounding sphere containing `percentile` of bodies by distance from the
   overall centroid."
  [bodies percentile]
  (nav/fit-all-bounds bodies percentile))

(defn distance-for-radius
  "Orbit distance (render units) needed to fit a sphere of `radius` with the
   given field-of-view and margin."
  [radius fov-deg margin]
  (nav/distance-for-radius radius fov-deg margin))

(defn update-camera-for-world
  "Update `camera` based on the world and camera settings. Pure: returns a new
   Camera."
  [camera world settings]
  (nav/update-camera-for-world camera world settings))
