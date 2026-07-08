(ns infra.camera.navigation.input
  "Local camera motion, flight controls, and camera settings for the Gates of
   Truth renderer.

   Defines the Camera record, orbital position math, and FPS-style free-flight
   input. Every public function is pure: given a camera and settings it returns a
   new camera value.

   Depends on infra.camera.projection for the Phase 0 view scale and vector math
   helpers."
  (:require
   [clojure.math :as math]
   [infra.camera.projection :as p]
   [shape.spatial :as sp]))

;; ---------------------------------------------------------------------------
;; Camera record and local motion
;; ---------------------------------------------------------------------------

(defrecord Camera
           [position yaw pitch distance target])

(declare update-camera-position)

(defn make-camera
  "Create an orbital camera. `distance` is the initial orbit radius in render
   units. Position is derived from yaw/pitch/distance so it is consistent with
   the z-up orbit geometry (see `update-camera-position`)."
  ([] (make-camera 50.0))
  ([distance]
   (update-camera-position
    (->Camera (sp/vec3 0.0 0.0 0.0) -90.0 -20.0 distance (sp/vec3 0.0 0.0 0.0)))))

(defn camera-forward
  "World-space forward direction of `camera` (from camera toward target).

   The world is z-up (the disk lies in the xy-plane, spin axis +z): `yaw` sweeps
   the xy-plane and `pitch` tilts toward ±z."
  [camera]
  (let [yaw-rad   (p/deg->rad (:yaw camera))
        pitch-rad (p/deg->rad (:pitch camera))
        cp        (math/cos pitch-rad)]
    [(- (* cp (math/cos yaw-rad)))
     (- (* cp (math/sin yaw-rad)))
     (- (math/sin pitch-rad))]))

(defn camera-move-basis
  "Forward and right movement vectors for `camera`, both normalized — FPS /
   free-flight style.

   `:forward` is the FULL camera→target look direction (into the screen,
   including any pitch), so W/S fly the mote toward / away from exactly where the
   camera is pointed — like moving forward in any 3D game. `:right` is
   forward × world-up ([0 0 1]), kept level so A/D strafe horizontally regardless
   of pitch. Looking straight up/down degenerates the strafe axis; we fall back to
   a stable one."
  [camera]
  (let [forward (p/normalize (camera-forward camera))
        r       (p/cross forward [0.0 0.0 1.0])
        rlen    (sp/len r)]
    (if (pos? rlen)
      {:forward forward :right (sp/v* r (/ 1.0 rlen))}
      {:forward forward :right [1.0 0.0 0.0]})))

(defn update-camera-position
  "Recompute :position from :target, :distance, :yaw and :pitch."
  [camera]
  (let [yaw-rad   (p/deg->rad (:yaw camera))
        pitch-rad (p/deg->rad (:pitch camera))
        d         (:distance camera)
        [tx ty tz] (:target camera)
        x (+ tx (* d (math/cos pitch-rad) (math/cos yaw-rad)))
        y (+ ty (* d (math/cos pitch-rad) (math/sin yaw-rad)))
        z (+ tz (* d (math/sin pitch-rad)))]
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
      (let [{:keys [forward right]} (camera-move-basis camera)
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
   provides :move-speed in m/s. Multiply by `dt` to obtain displacement.

   Forward flies along the camera's full look direction (FPS-style), so W/S move
   the mote toward / away from wherever the camera is aimed."
  [camera input settings]
  (let [fwd-input (double (:forward input 0.0))
        rgt-input (double (:right input 0.0))]
    (if (and (zero? fwd-input) (zero? rgt-input))
      [0.0 0.0 0.0]
      (let [{:keys [forward right]} (camera-move-basis camera)
            speed (double (:move-speed settings 3.0e15))]
        (sp/v+ (sp/v* forward (* speed fwd-input))
               (sp/v* right (* speed rgt-input)))))))

;; ---------------------------------------------------------------------------
;; Camera modes and settings
;; ---------------------------------------------------------------------------

(def ^:private camera-modes [:manual :follow-selection :track-largest-cluster :fit-all])

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
   :move-speed 3.0e15
   ;; Mouse look degrees-per-pixel and scroll-zoom render-units-per-notch.
   ;; Adjustable live from the View panel (infra.menu) or the REPL.
   :look-sensitivity 0.01
   :zoom-sensitivity 5.0})

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

(defn min-approach-distance
  "Closest orbit distance [ru] the camera may take to a body of render radius
   `r-ru`: a couple of radii out so the globe fills the view without clipping,
   floored so a degenerate radius can't pin the camera to a point."
  [r-ru]
  (max (* 2.5 (double (or r-ru 0.0))) 1.0e-7))
