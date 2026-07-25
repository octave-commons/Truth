(ns infra.camera.navigation
  "Camera movement, tracking, and world framing facade for the Gates of Truth
   renderer.

   Delegates to infra.camera.navigation.input for local flight motion and
   settings, and to infra.camera.navigation.tracking for world-aware follow,
   cluster tracking, and fit-all framing. This namespace preserves the legacy
   public API of the original monolithic navigation namespace."
  (:require
   [infra.camera.navigation.input :as input]
   [infra.camera.navigation.tether :as tether]
   [infra.camera.navigation.tracking :as tracking]))

;; ---------------------------------------------------------------------------
;; Camera record constructors
;; ---------------------------------------------------------------------------

;; ---------------------------------------------------------------------------
;; Local motion and input
;; ---------------------------------------------------------------------------

(defn make-camera
  "Create an orbital camera. `distance` is the initial orbit radius in render
   units. Position is derived from yaw/pitch/distance so it is consistent with
   the z-up orbit geometry."
  ([] (input/make-camera))
  ([distance] (input/make-camera distance)))

(defn camera-forward
  "World-space forward direction of `camera` (from camera toward target).

   The world is z-up (the disk lies in the xy-plane, spin axis +z): `yaw` sweeps
   the xy-plane and `pitch` tilts toward ±z."
  [camera]
  (input/camera-forward camera))

(defn camera-move-basis
  "Forward and right movement vectors for `camera`, both normalized — FPS /
   free-flight style."
  [camera]
  (input/camera-move-basis camera))

(defn update-camera-position
  "Recompute :position from :target, :distance, :yaw and :pitch."
  [camera]
  (input/update-camera-position camera))

(defn flight-move
  "Apply one frame of flight translation to `camera`."
  [camera input dt settings]
  (input/flight-move camera input dt settings))

(defn thrust-direction
  "Unit thrust direction (world axes) for the spark from camera-relative
   input, or nil when no flight key is held."
  [camera input]
  (input/thrust-direction camera input))

;; ---------------------------------------------------------------------------
;; Settings
;; ---------------------------------------------------------------------------

(defn default-camera-settings
  "Default in-game camera configuration."
  []
  (input/default-camera-settings))

(defn cycle-camera-mode
  "Advance to the next camera mode."
  [settings]
  (input/cycle-camera-mode settings))

(defn adjust-fit-margin
  "Scale the fit margin by `factor`, clamped to a sensible range."
  [settings factor]
  (input/adjust-fit-margin settings factor))

;; ---------------------------------------------------------------------------
;; World tracking and framing
;; ---------------------------------------------------------------------------

(defn observer-render-position
  "The observer (player spark/mote) position in render units, or the origin when
   there is no observer."
  [world]
  (tracking/observer-render-position world))

(defn bodies->render
  "Project ECS bodies into [[render-position mass] ...]."
  [world scale]
  (tracking/bodies->render world scale))

(defn largest-mass-cluster
  "Find the densest mass cluster using a uniform grid."
  [bodies cell-size]
  (tracking/largest-mass-cluster bodies cell-size))

(defn fit-all-bounds
  "Bounding sphere containing `percentile` of bodies by distance from the
   overall centroid."
  [bodies percentile]
  (tracking/fit-all-bounds bodies percentile))

(defn distance-for-radius
  "Orbit distance (render units) needed to fit a sphere of `radius` with the
   given field-of-view and margin."
  [radius fov-deg margin]
  (tracking/distance-for-radius radius fov-deg margin))

(defn min-approach-distance
  "Closest orbit distance [ru] the camera may take to a body of render radius
   `r-ru`."
  [r-ru]
  (tracking/min-approach-distance r-ru))

(defn update-camera-for-world
  "Update `camera` based on the world and camera settings. Pure: returns a new
   Camera. In :manual mode the camera is unchanged except for position recalc."
  [camera world settings]
  (case (:mode settings :fit-all)
    :manual (tracking/update-camera-manual camera world)
    :follow-selection (tracking/update-camera-follow-selection camera world settings)
    :track-largest-cluster (tracking/update-camera-track-cluster camera world settings)
    :fit-all (tracking/update-camera-fit-all camera world settings)))

;; ---------------------------------------------------------------------------
;; Binding tether (The First Narrowing, child C)
;; ---------------------------------------------------------------------------

(defn tether-step
  "One frame of the binding camera tether. Pure: returns a new Camera. Player
   input (`:input-active?`) wins outright; otherwise the frame tightens toward
   the deepest-bound world at a rate continuous in binding depth."
  [camera world opts]
  (tether/tether-step camera world opts))
