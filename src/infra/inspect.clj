(ns infra.inspect
  "Body inspection: ray-picking, screen projection, and the selection overlay.

   The resolved bodies (stars, protostars, planets, debris, the player spark) are
   the *characters* of a Phase 0 scene — the gas and field lines are weather around
   them. This namespace makes the characters interactable: click one and the
   renderer draws a camera-facing halo + a velocity arrow around it and a HUD card
   reading its live ECS state.

   This namespace is a thin facade over infra.inspect.picking, infra.inspect.overlay,
   infra.inspect.format, and infra.inspect.card. New code may require the
   sub-namespace directly; this facade preserves the legacy public API.

   Everything here uses `infra.render.units` for coordinate transforms so that
   picking, projection, and rendering share one transform chain and cannot drift."
  (:require
   [infra.inspect.card :as card]
   [infra.inspect.format :as fmt]
   [infra.inspect.overlay :as overlay]
   [infra.inspect.picking :as picking]))

;; ---------------------------------------------------------------------------
;; Picking and projection
;; ---------------------------------------------------------------------------

(defn screen->ray
  "Render-space pick ray {:ro :rd} through pixel (px,py)."
  [ctx px py]
  (picking/screen->ray ctx px py))

(defn project-point
  "Project render-space point `p` to framebuffer pixels [sx sy depth], or nil
   when behind the camera."
  [ctx p]
  (picking/project-point ctx p))

(defn selected-shape
  "The already-projected render shape for entity `eid`, or nil."
  [bodies eid]
  (picking/selected-shape bodies eid))

(defn pick-entity
  "Entity id of the body the ray through (px,py) hits, nearest-first, or nil."
  [ctx bodies px py]
  (picking/pick-entity ctx bodies px py))

(defn cursor->world
  "World-metre point under pixel (px,py), placed on the depth plane through the
   camera target."
  [ctx px py]
  (picking/cursor->world ctx px py))

;; ---------------------------------------------------------------------------
;; Overlay shapes
;; ---------------------------------------------------------------------------

(defn halo-shapes
  "A camera-facing ring of render radius `r` around `center`, as :line segments."
  [{:keys [center r ctx color n]}]
  (overlay/halo-shapes {:center center :r r :ctx ctx :color color :n n}))

(defn velocity-arrow-shapes
  "An arrow from `center` along the body's world velocity, as :line segments."
  [center vel-world body-r ctx]
  (overlay/velocity-arrow-shapes center vel-world body-r ctx))

(defn overlay-radius
  "Halo radius for a body of render radius `r` at `center`."
  [{:keys [ctx center r k min-frac]}]
  (overlay/overlay-radius {:ctx ctx :center center :r r :k k :min-frac min-frac}))

(defn hover-overlay-shapes
  "A faint, thin halo around the body the cursor is over."
  [ctx bodies hover-eid sel-eid]
  (overlay/hover-overlay-shapes ctx bodies hover-eid sel-eid))

(defn intervention-overlay-shapes
  "Camera-facing rings for the player's active warps."
  [ctx world]
  (overlay/intervention-overlay-shapes ctx world))

(defn selection-overlay-shapes
  "Halo + velocity arrow for the selected entity."
  [ctx world eid bodies]
  (overlay/selection-overlay-shapes ctx world eid bodies))

;; ---------------------------------------------------------------------------
;; Formatting helpers
;; ---------------------------------------------------------------------------

(def solar-mass
  "Solar mass reference constant [kg]."
  fmt/solar-mass)

(def solar-radius
  "Solar radius reference constant [m]."
  fmt/solar-radius)

(def solar-lum
  "Solar luminosity reference constant [W]."
  fmt/solar-lum)

(def earth-mass
  "Earth mass reference constant [kg]."
  fmt/earth-mass)

(def earth-radius
  "Earth radius reference constant [m]."
  fmt/earth-radius)

(def au
  "Astronomical unit reference constant [m]."
  fmt/au)

(defn fmt-mass
  "Format a mass in kg as a human-readable string."
  [kg stellar?]
  (fmt/fmt-mass kg stellar?))

(defn fmt-radius
  "Format a radius in metres as a human-readable string."
  [m star?]
  (fmt/fmt-radius m star?))

(defn state-label
  "Return a human-readable label for a matter state keyword."
  [state]
  (fmt/state-label state))

(defn state-color
  "RGBA colour for a matter-state keyword, used by the inspector card title."
  [state]
  (fmt/state-color state))

(defn body-facts
  "Ordered [label value] readout lines for entity `eid` from its live ECS state."
  [world eid]
  (fmt/body-facts world eid))

;; ---------------------------------------------------------------------------
;; Inspector card
;; ---------------------------------------------------------------------------

(defn inspector-card
  "HUD content for the selected body: a titled card of live facts, anchored beside
   the body's screen position (clamped on-screen). Returns
   {:rects [...] :text [...]} or nil when the entity has no render shape this frame."
  [ctx world eid bodies]
  (card/inspector-card ctx world eid bodies))
