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
   [infra.render.scene.hud :as hud]))

;; --- LOD constants ----------------------------------------------------------

;; --- Particle / field line helpers -----------------------------------------

(def nebula-fog particles/nebula-fog)
(def field-line particles/field-line)
;; --- Player HUD / overlay ---------------------------------------------------

(def player-overlay-shapes hud/player-overlay-shapes)
(def hud-rects-from-world hud/hud-rects-from-world)

;; --- Body projection and LOD ------------------------------------------------

(def body-screen-diameter bodies/body-screen-diameter)
(def clear-phase0-render-cache! bodies/clear-phase0-render-cache!)
(def phase0-bodies-from-world bodies/phase0-bodies-from-world)
(def phase0-bodies+fields bodies/phase0-bodies+fields)
(def bodies-from-world bodies/bodies-from-world)

;; --- Voxel band render path --------------------------------------------------

;; --- Scene drawing ----------------------------------------------------------

(def scene-far-plane setup/scene-far-plane)
(def render-scene setup/render-scene)
