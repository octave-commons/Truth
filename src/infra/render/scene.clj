(ns infra.render.scene
  "Phase 0 scene orchestration: ECS world → render shapes, LOD, field lines,
   fog particles, and the final draw call sequence.

   This namespace is a thin facade over the split implementation under
   `infra.render.scene.*`; prefer requiring the specific sub-namespace for new
   code. The public API is preserved for existing callers and tests."
  (:require
   [infra.render.scene.setup :as setup]
   [infra.render.scene.bodies :as bodies]
   [infra.render.scene.particles :as particles]
   [infra.render.scene.hud :as hud]
   [infra.render.scene.voxel :as voxel]))

;; --- LOD constants ----------------------------------------------------------

(def default-sprite-lod-threshold-pixels
  bodies/default-sprite-lod-threshold-pixels)
(def sprite-min-pixels bodies/sprite-min-pixels)
(def sprite-max-pixels bodies/sprite-max-pixels)

;; --- Particle / field line helpers -----------------------------------------

(def nebula-fog particles/nebula-fog)
(def field-line particles/field-line)
(def field-line-shapes particles/field-line-shapes)

;; --- Player HUD / overlay ---------------------------------------------------

(def player-overlay-shapes hud/player-overlay-shapes)
(def hud-rects-from-world hud/hud-rects-from-world)

;; --- Body projection and LOD ------------------------------------------------

(def body-screen-diameter bodies/body-screen-diameter)
(def classify-body-lod bodies/classify-body-lod)
(def clear-phase0-render-cache! bodies/clear-phase0-render-cache!)
(def phase0-bodies-from-world bodies/phase0-bodies-from-world)
(def phase0-bodies+fields bodies/phase0-bodies+fields)
(def bodies-from-world bodies/bodies-from-world)

;; --- Voxel band render path --------------------------------------------------

(def voxel-cube-shapes voxel/voxel-cube-shapes)

;; --- Scene drawing ----------------------------------------------------------

(def scene-far-plane setup/scene-far-plane)
(def render-scene setup/render-scene)
(def render-bodies setup/render-bodies)
