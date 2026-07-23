(ns infra.render.scene.voxel
  "Voxel band render path: the missing renderer for `c/voxel-band`
   (kanban/tasks/voxel-band-render-path.md). Before this namespace, nothing
   in `src/infra/` read a voxel component, so a focused planet's resolved
   surface patch was invisible — the player saw only the plain body sphere.

   COORDINATE FRAME (load-bearing, resolved by reading `domain.voxel.band`
   carefully — see its own docstring and `domain.voxel.focus`'s
   `f-rel = focus - position` translation): `c/voxel-band` lives on the
   COMMITTED WORLD entity, keyed by BODY-CENTRIC canonical-grid offsets —
   `domain.voxel.band/voxel-center` maps `[i j k]` to a body-centric metre
   position with NO rotation applied anywhere upstream (the whole voxel
   ladder is translation-only relative to the body's `c/position`; nothing
   in `domain.voxel.*` reads a body orientation component). World-space
   centre is therefore exactly `body-position + voxel-center(offset)` — z-up
   throughout, since `voxel-center`'s z axis is the same z the body position
   and every other body-space quantity in this renderer already uses.

   RENDER PATH: voxel cubes are true-scale world-space geometry with a
   caller-supplied centre and a fixed physical edge length
   (`law.voxel/canonical-voxel-edge-m`), exactly the shape `:body` shapes
   already have (`:position` + `:radius`, model-matrix positioned) — NOT the
   raw-position `:particle`/`:line` path, which never model-matrix-scales
   its geometry. Each cube is therefore emitted as `:render-mode :voxel-cube`
   (a body-shaped record; `infra.render.scene.setup` gives it its own pass so
   it draws with `infra.render.mesh/make-cube-mesh` instead of the sphere
   mesh and never falls through the sprite-LOD reclassification bodies get)."
  (:require
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.voxel.band :as band]
   [law.voxel :as voxel]
   [infra.render.color :as rcolor]
   [infra.render.units :as units]))

(def ^:private half-edge-m
  "Half of `law.voxel/canonical-voxel-edge-m` — the model-matrix `radius`
   (distance centre → face) that reproduces the full canonical edge length,
   matching `infra.render.mesh/make-cube-mesh`'s unit-cube (half-extent 1.0)
   convention."
  (/ voxel/canonical-voxel-edge-m 2.0))

(defn- committed-band-eid
  "The eid of the `c/commitment-state :committed` world carrying a
   `c/voxel-band`, or nil. Read-only; mirrors the same private helper
   duplicated per-namespace elsewhere in the renderer/domain (`infra.render.
   hud/committed-world-eid`, `domain.narrowing`'s) rather than adding a
   cross-namespace dependency for four lines."
  [world]
  (some (fn [[eid m]]
          (when (and (= :committed (get m c/commitment-state))
                     (some? (get m c/voxel-band)))
            eid))
        (ecs/all-of world c/commitment-state c/voxel-band)))

(defn voxel-cube-shapes
  "Render-space cube shapes for the committed world's resolved `c/voxel-band`,
   or `[]` when no committed world carries a band or the band is empty.

   Carved/absent cells (`domain.voxel.band/materialize`'s nil `:after` for a
   replayed carve) are SKIPPED — they are the visible cavity the design calls
   for (kanban card 'Done when': depth where the band has been carved), not a
   voxel to draw.

   Each shape is `{:entity eid+offset :position [x y z] :radius half-edge-ru
   :color [r g b] :material kw :kind :voxel :render-mode :voxel-cube}` —
   `:position`/`:radius` in the SAME render-space units and true-scale
   convention `:body` shapes use (`infra.render.units/world->render`,
   `phys->body-render-radius`), so the shared model-matrix path
   (`infra.render.math/model-matrix`) places them correctly without any new
   transform math."
  [ctx world]
  (if-let [eid (committed-band-eid world)]
    (let [body-pos (ecs/get-component world eid c/position)
          voxels   (get-in (ecs/get-component world eid c/voxel-band) [:voxels])
          half-ru  (units/phys->body-render-radius ctx half-edge-m)]
      (if (and body-pos (seq voxels))
        (into []
              (keep (fn [[offset v]]
                      (when v
                        (let [center-body (band/voxel-center offset)
                              world-pos   (mapv + body-pos center-body)]
                          {:entity      [eid offset]
                           :position    (units/world->render ctx world-pos)
                           :radius      half-ru
                           :color       (rcolor/voxel-material-color (:material v))
                           :material    (:material v)
                           :kind        :voxel
                           :render-mode :voxel-cube}))))
              voxels)
        []))
    []))
