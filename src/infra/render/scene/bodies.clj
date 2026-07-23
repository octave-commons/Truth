(ns infra.render.scene.bodies
  "Phase 0 body projection and LOD.

   Converts ECS matter entities into stylized, view-scaled render shapes. Also
   includes the legacy non-Phase 0 body list and sprite LOD classification."
  (:require
   [clojure.math :as math]
   [domain.ecology :as ecology]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.player :as player]
   [shape.spatial :as sp]
   [infra.camera :as cam]
   [infra.render.color :as rcolor]
   [infra.render.units :as units]
   [infra.render.scene.particles :as particles]
   [infra.render.scene.hud :as hud]
   [infra.render.scene.voxel :as voxel]))

;; --- Level-of-detail: distant bodies fall back to screen-space sprites ------

(def ^:const default-sprite-lod-threshold-pixels
  "Screen-space diameter below which a body is drawn as a point sprite."
  4.0)

(def ^:const sprite-min-pixels
  "Minimum sprite diameter in pixels so distant bodies remain visible."
  2.5)

(def ^:const sprite-max-pixels
  "Maximum sprite diameter in pixels so nearby proxies don't dominate."
  24.0)

(defn- pixels-per-radian
  "Framebuffer height in pixels per radian of vertical view angle."
  [height fov-deg]
  (/ (double height) 2.0 (math/tan (/ (cam/deg->rad fov-deg) 2.0))))

(defn body-screen-diameter
  "Approximate on-screen diameter in pixels for a render-space body."
  [body camera height fov-deg]
  (let [dist (sp/dist (:position camera) (:position body))
        angular-diam (* 2.0 (/ (double (:radius body)) (max dist 1.0e-12)))]
    (* angular-diam (pixels-per-radian height fov-deg))))

(defn classify-body-lod
  "Split render shapes into solid bodies and sprite proxies.

   Any shape with :render-mode :body whose screen-space diameter falls below
   `threshold-pixels` is converted to a :sprite shape with a clamped pixel size.
   Stars use their :brightness to boost sprite size, so luminous bodies stay
   visible as point sources even when their physical sphere is sub-pixel.
   Shapes ALREADY classified :sprite (e.g. regional-cell probability clouds)
   pass straight to the sprite list; other shapes are passed through unchanged.
   Returns [solid-bodies sprites]."
  [shapes camera height threshold-pixels]
  (let [threshold (double (or threshold-pixels default-sprite-lod-threshold-pixels))
        ppr (pixels-per-radian height 60.0)]
    (reduce (fn [[solids sprites] shape]
              (cond
                (= :sprite (:render-mode shape))
                [solids (conj sprites shape)]

                (= :body (:render-mode shape))
                (let [dist       (sp/dist (:position camera) (:position shape))
                      angular-diam (* 2.0 (/ (double (:radius shape)) (max dist 1.0e-12)))
                      pixel-diam (* angular-diam ppr)
                      brightness (double (or (:brightness shape) 0.3))
                      star?      (= :star (:kind shape))
                      min-size   (if star?
                                   (max sprite-min-pixels (* 2.0 brightness))
                                   sprite-min-pixels)]
                  (if (< pixel-diam threshold)
                    (let [size (max min-size
                                    (min sprite-max-pixels
                                         (if star?
                                           (* pixel-diam brightness)
                                           pixel-diam)))]
                      [solids (conj sprites (assoc shape
                                                   :render-mode :sprite
                                                   :size size))])
                    [(conj solids shape) sprites]))

                :else
                [(conj solids shape) sprites]))
            [[] []]
            shapes)))

;; --- Projection far plane -------------------------------------------------

;; Moved to infra.render.scene.setup so the setup namespace can compute it
;; directly; re-exported from the scene facade for compatibility.

;; --- Sky simplification: regional statistical cells render as dimmed clouds --

(def ^:const cell-cloud-dim
  "Brightness factor applied to a regional cell's thermal/composition colour.
   Demoted matter must read as a probability haze next to resolved bodies, so
   the cloud keeps the matter's hue but most of its light is gone."
  0.30)

(def ^:const cell-cloud-size
  "Sprite size (pixels) of a regional-cell probability cloud. Deliberately
   mid-range in the sprite clamp band (see classify-body-lod): present in the
   sky, never dominant."
  14.0)

(defn- cell-cloud-shapes
  "Dimmed probability-cloud sprites for every regional statistical cell
   (`c/field-zone :regional`, i.e. `c/position` + `c/statistical-mass` and no
   `c/matter-state`). This is the demotion path made visible: when a promoted
   clump folds back into its cell, the resolved body shape vanishes from the
   projection above and the cell's dim cloud remains — the sky simplifies in
   frame instead of the mass blinking out.

   Colour is the cell ledger's own thermal/composition colour scaled by
   cell-cloud-dim (sprites carry RGB, no alpha, so 'dimmed' is darker light,
   matching how classify-body-lod dims distant proxies). Position projects
   through units/world->render like every body: z-up, true-scale intact.

   GAP: cells do not feed the volumetric froxel field
   (infra.render.volume/render-samples reads domain.hydro/gas-samples, which
   is matter-state-filtered), so a fully demoted region has no ray-marched
   haze — only these sprites. Splatting cell ledgers into the volume is a
   later card."
  [ctx world]
  (into []
        (keep (fn [eid]
                (when-let [ledger (ecs/get-component world eid c/statistical-mass)]
                  (let [pos  (ecs/get-component world eid c/position)
                        base (rcolor/body-render-color (:temperature ledger)
                                                       (:composition ledger))]
                    {:entity      eid
                     :position    (units/world->render ctx pos)
                     :color       (mapv #(* cell-cloud-dim (double %)) base)
                     :size        cell-cloud-size
                     :kind        :statistical-cell
                     :render-mode :sprite}))))
        (ecs/entities-with world c/position c/statistical-mass)))

;; --- Phase 0 projection ---------------------------------------------------

(defn- _hash01
  "Deterministic [0,1) value from an integer key for stable per-entity jitter."
  [n]
  (/ (double (mod (* (inc (long n)) 2654435761) 1000003)) 1000003.0))

(defn- player-focus-level
  "Observer attention in 0..1, used to scale the fog sample budget."
  [world]
  (if-let [obs (player/get-observer world)]
    (max 0.0 (min 1.0 (* (:coherence obs 0.5) (:focus-intensity obs 0.5))))
    0.5))

(def ^:private phase0-bodies-cache
  "Per-frame cache for `phase0-bodies-from-world`."
  (atom {}))

(defn clear-phase0-render-cache!
  "Reset the per-frame Phase 0 render projection cache."
  []
  (reset! phase0-bodies-cache {}))

(defn- phase0-render-cache-key
  "Cache key: world identity + tick + view scale."
  [world scale]
  [(System/identityHashCode world) (:tick world) scale])

(defn- phase0-bodies-from-world*
  "Uncached render projection; see `phase0-bodies-from-world`."
  [ctx world]
  (let [_focus (player-focus-level world)]
    (into
     (into (into (hud/player-overlay-shapes ctx world)
                 (cell-cloud-shapes ctx world))
           (voxel/voxel-cube-shapes ctx world))
     (mapcat
      (fn [eid]
        (let [state   (ecs/get-component world eid c/matter-state)
              [x y z] (ecs/get-component world eid c/position)
              center  (units/world->render ctx [x y z])
              temp    (ecs/get-component world eid c/temperature)
              compose (ecs/get-component world eid c/composition)
              r-phys  (ecs/get-component world eid c/radius)
              color   (rcolor/body-render-color temp compose)
              ob      (or (ecs/get-component world eid c/oblateness) 1.0)
              axis    (or (ecs/get-component world eid c/rotation-axis) [0.0 0.0 1.0])]
          (case state
            :nebula []

            :star
            (let [core-r     (units/phys->body-render-radius ctx r-phys)
                  teff       (double (or (ecs/get-component world eid c/temperature) 5800.0))
                  s-col      (rcolor/stellar-spectral-color teff)
                  brightness (rcolor/body-brightness world eid state)
                  app        (rcolor/body-appearance {:state :star :planet-type nil :temp teff :living? false :eid eid})
                  body       {:entity        eid
                              :position      center
                              :radius        core-r
                              :color         s-col
                              :kind          state
                              :oblateness    ob
                              :rotation-axis axis
                              :render-mode   :body
                              :glow          (* 1.5 brightness)
                              :brightness    brightness
                              :surface       (:surface app)
                              :accent        (:accent app)
                              :seed          (:seed app)}]
              (concat
               [body]
               (particles/field-line center core-r (ecs/get-component world eid c/b-field))))

            :protostar
            (let [render-r   (units/phys->body-render-radius ctx r-phys)
                  brightness (rcolor/body-brightness world eid state)
                  app        (rcolor/body-appearance {:state :protostar :planet-type nil :temp temp :living? false :eid eid})]
              (concat
               [{:entity        eid
                 :position      center
                 :radius        (* render-r (math/pow ob (/ 1.0 3.0)))
                 :color         color
                 :kind          state
                 :oblateness    ob
                 :rotation-axis axis
                 :render-mode   :body
                 :glow          (* 1.2 brightness)
                 :brightness    brightness
                 :surface       (:surface app)
                 :accent        (:accent app)
                 :seed          (:seed app)}]
               (particles/field-line center render-r (ecs/get-component world eid c/b-field))))

            (let [render-r (units/phys->body-render-radius ctx r-phys)
                  ptype    (ecs/get-component world eid c/planet-type)
                  eco      (ecs/get-component world eid c/ecology)
                  living?  (boolean (and eco (ecology/living? eco)))
                  app      (rcolor/body-appearance {:state state :planet-type ptype :temp temp :living? living? :eid eid})]
              [{:entity      eid
                :position    center
                :radius      render-r
                :color       (or (:base app) color)
                :kind        state
                :oblateness  ob
                :rotation-axis axis
                :render-mode :body
                :glow        0.15
                :brightness  0.3
                :surface     (:surface app)
                :accent      (:accent app)
                :seed        (:seed app)}]))))
      (ecs/entities-with world c/position c/matter-state)))))

(defn phase0-bodies-from-world
  "Project Phase 0 ECS matter entities into stylized, view-scaled render shapes.
   This is the ONLY Phase 0 render projection. The projection is cached per
   [tick scale] so consecutive render frames that see the same world do not
   rebuild hundreds of fog particles."
  ([world] (phase0-bodies-from-world world cam/phase0-view-scale))
  ([world scale]
   (let [ckey (phase0-render-cache-key world scale)]
     (if-let [cached (get @phase0-bodies-cache ckey)]
       cached
       (let [ctx (units/make-context scale (cam/make-camera) {:width 1 :height 1})
             bodies (phase0-bodies-from-world* ctx world)]
         (reset! phase0-bodies-cache {ckey bodies})
         bodies)))))

(defn phase0-bodies+fields
  "Phase 0 render bodies plus the magnetic field rendered as dipole field-line
   loops. Kept for compatibility with callers that expect explicit field-line
   shapes."
  ([world]
   (phase0-bodies+fields world cam/phase0-view-scale))
  ([world scale]
   (let [ctx (units/make-context scale (cam/make-camera) {:width 1 :height 1})]
     (into (vec (phase0-bodies-from-world world scale))
           (particles/field-line-shapes ctx world)))))

(defn bodies-from-world
  "Legacy non-Phase 0 body list from the ECS world. The spark
   (`c/body-kind :spark`) is EXCLUDED: it is a gravity-bound observer body
   (spark-redesign card 4) with its own overlay (player-overlay-shapes), not
   a scene body — including it would render a diffuse 1e12 m sphere."
  [world]
  (into []
        (comp
         (remove (fn [[_ comps]] (= :spark (comps c/body-kind))))
         (map (fn [[eid comps]]
                {:entity eid
                 :position (comps c/position)
                 :radius   (comps c/radius)
                 :kind     (comps c/body-kind)})))
        (ecs/all-of world c/position c/radius c/body-kind)))
