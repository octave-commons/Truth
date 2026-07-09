(ns infra.render
  "Thin public facade for the Phase 0 renderer.

   The implementation lives in layered sub-modules under `infra.render.*`;
   this namespace re-exports the public API used by `infra.dev.window`,
   `infra.dev.server`, and tests so existing call sites keep working."
  (:require
   [infra.render.math :as math]
   [infra.render.mesh :as mesh]
   [infra.render.color :as color]
   [infra.render.hud :as hud]
   [infra.render.volume :as volume]
   [infra.render.input :as input]
   [infra.render.scene :as scene]
   [infra.render.window :as window]))

;; ---------------------------------------------------------------------------
;; Window / context lifecycle
;; ---------------------------------------------------------------------------

(def init-glfw "Initialize GLFW; required before creating windows." window/init-glfw)
(def create-window "Create an OpenGL window and context." window/create-window)
(def render-to-file "Render the current frame to an image file." window/render-to-file)
(def run-window "Enter the main render loop for the given window." window/run-window)

;; ---------------------------------------------------------------------------
;; Shader program constructors (backward-compatible wrappers)
;; ---------------------------------------------------------------------------

(def create-program "Compile and link a shader program." window/create-program)
(def create-particle-program "Create the particle shader program." window/create-particle-program)
(def create-sprite-program "Create the sprite shader program." window/create-sprite-program)
(def create-line-program "Create the line shader program." window/create-line-program)
(def create-hud-program "Create the HUD shader program." window/create-hud-program)
(def create-volume-program "Create the volume shader program." volume/create-volume-program)

;; ---------------------------------------------------------------------------
;; Mesh
;; ---------------------------------------------------------------------------

(def make-sphere-mesh "Build an indexed sphere mesh." mesh/make-sphere-mesh)
(def upload-mesh "Upload a mesh to GPU buffers." mesh/upload-mesh)
(def subdivisions-for-screen-size "Adaptive sphere subdivisions from on-screen diameter." mesh/subdivisions-for-screen-size)

;; ---------------------------------------------------------------------------
;; Input
;; ---------------------------------------------------------------------------

(def action-palette "Default key-to-action mapping." input/action-palette)
(def action-for-key "Return the action bound to a key, if any." input/action-for-key)
(def setup-input "Attach input callbacks to a window." input/setup-input)

;; ---------------------------------------------------------------------------
;; Color / material / appearance
;; ---------------------------------------------------------------------------

(def tint-color "Apply a tint color to a base color." color/tint-color)
(def temp-color "Map a temperature (K) to a blackbody color." color/temp-color)
(def disk-temp-color "Map a disk temperature (K) to a color." color/disk-temp-color)
(def body-brightness "Compute a body's apparent brightness." color/body-brightness)
(def composition->material-color "Map a material composition to a base color." color/composition->material-color)
(def body-render-color "Choose the final rendered color for a body." color/body-render-color)
(def body-appearance "Compute the full appearance descriptor for a body." color/body-appearance)
(def coherence-color "Map observer coherence to a color." color/coherence-color)

(def surface-flat "Flat shading material." color/surface-flat)
(def surface-star "Star surface material." color/surface-star)
(def surface-gas-giant "Gas-giant surface material." color/surface-gas-giant)
(def surface-ice-giant "Ice-giant surface material." color/surface-ice-giant)
(def surface-terrestrial "Terrestrial surface material." color/surface-terrestrial)
(def surface-rocky "Rocky surface material." color/surface-rocky)
(def surface-molten "Molten surface material." color/surface-molten)

;; ---------------------------------------------------------------------------
;; HUD
;; ---------------------------------------------------------------------------

(def render-hud "Render the HUD overlay for the current frame." hud/render-hud)
(def render-text "Render a text string to the HUD." hud/render-text)
(def hud-text-from-world "Build the main HUD text from world state." hud/hud-text-from-world)
(def observer-hud-text "Build observer-specific HUD text." hud/observer-hud-text)
(def controls-hud "Build the controls HUD text." hud/controls-hud)
(def view-bar-hud "Build the view-mode bar HUD text." hud/view-bar-hud)

;; ---------------------------------------------------------------------------
;; Volume
;; ---------------------------------------------------------------------------

(def reset-volume-cache! "Reset the volume renderer's cached state." volume/reset-volume-cache!)
(def build-volume-texture "Build the 3D volume texture for the nebula." volume/build-volume-texture)
(def volume-lights "Return the active light sources for volume shading." volume/volume-lights)
(def render-volume "Render the nebula volume." volume/render-volume)
(def froxel-resolution-for "Choose an appropriate froxel resolution for a view." volume/froxel-resolution-for)
(def frame-volume "Prepare the volume for the current frame." volume/frame-volume)
(def delete-volume "Release GPU resources owned by a volume." volume/delete-volume)

;; ---------------------------------------------------------------------------
;; Scene / projection
;; ---------------------------------------------------------------------------

(def bodies-from-world "Extract renderable bodies from the world." scene/bodies-from-world)
(def phase0-bodies-from-world "Extract Phase 0 bodies from the world." scene/phase0-bodies-from-world)
(def phase0-bodies+fields "Extract Phase 0 bodies plus their field lines." scene/phase0-bodies+fields)
(def render-scene "Render the full scene for a frame." scene/render-scene)
(def render-bodies "Render the body set produced by scene queries." scene/render-bodies)
(def clear-phase0-render-cache! "Invalidate cached Phase 0 render data." scene/clear-phase0-render-cache!)
(def scene-far-plane "Far plane distance for the scene projection." scene/scene-far-plane)
(def nebula-fog "Return the fog parameters for the nebula." scene/nebula-fog)
(def field-line "Build a single field-line shape." scene/field-line)
(def field-line-shapes "Build all field-line shapes for a body." scene/field-line-shapes)
(def player-overlay-shapes "Build shapes for the player overlay." scene/player-overlay-shapes)
(def hud-rects-from-world "Build HUD rectangles from world state." scene/hud-rects-from-world)
(def classify-body-lod "Classify the level-of-detail for a body." scene/classify-body-lod)
(def body-screen-diameter "Estimate a body's on-screen diameter in pixels." scene/body-screen-diameter)

;; ---------------------------------------------------------------------------
;; Math
;; ---------------------------------------------------------------------------

(def model-matrix "Build a model matrix for a body transform." math/model-matrix)