(ns infra.render.units
  "Pure coordinate transforms between physical, render, NDC and screen spaces
   for the Phase 0 renderer.

   All public functions are pure: no OpenGL, no atoms, no I/O. The
   `RenderContext` record carries the scale (physical metres per render unit),
   the camera value from `infra.camera`, and the viewport in pixels."
  (:require
   [clojure.math :as math]
   [infra.camera :as cam]
   [shape.spatial :as sp]))

;; ---------------------------------------------------------------------------
;; Context
;; ---------------------------------------------------------------------------

(defrecord RenderContext
           [scale camera viewport])

(defn make-context
  "Build a RenderContext from a camera and viewport.
   `scale` defaults to `infra.camera/phase0-view-scale`."
  ([camera viewport]
   (make-context cam/phase0-view-scale camera viewport))
  ([scale camera viewport]
   (->RenderContext (double scale) camera viewport)))

;; ---------------------------------------------------------------------------
;; Position / scale transforms
;; ---------------------------------------------------------------------------

(defn world->render
  "Physical point [m] → render point [ru] by dividing each component by
   `ctx.scale`."
  [ctx pos]
  (let [s (double (:scale ctx))]
    (mapv #(/ (double %) s) pos)))

(defn render->world
  "Render point [ru] → physical point [m] by multiplying each component by
   `ctx.scale`."
  [ctx pos]
  (let [s (double (:scale ctx))]
    (mapv #(* (double %) s) pos)))

;; ---------------------------------------------------------------------------
;; Radius transforms
;; ---------------------------------------------------------------------------

(def ^:private render-radius-ref
  "Physical radius [m] that maps to render-unit radius 1.0."
  3.0e13)

(defn phys->render-radius
  "Physical radius [m] → render-unit radius, log-compressed. Keeps a ~5-order
   span legible while preserving monotonicity."
  [_ctx r-phys]
  (let [r (double (or r-phys 0.0))]
    (if (pos? r)
      (let [linear (/ r render-radius-ref)
            log-r  (* 0.42 (math/log10 (max 1e-6 linear)))]
        (max (* 0.5 linear) (+ 0.01 log-r) 0.001))
      0.001)))

(defn ^:export render->phys-radius
  "Approximate inverse of `phys->render-radius` for debug/tooling only. Not for
   physics."
  [_ctx r-render]
  (let [r (double (or r-render 0.001))]
    (* r render-radius-ref)))

(def ^:const body-radius-floor-ru
  "Absolute floor on a body's render radius [ru] so a degenerate (zero/negative
   radius) body never vanishes from picking or produces a singular model
   matrix. 1e-9 ru = 1e6 m at the default scale — far below one pixel; the
   sprite LOD pass, not this floor, is what keeps distant bodies visible."
  1.0e-9)

(defn phys->body-render-radius
  "Physical radius [m] → render-unit radius at TRUE scale: the same linear
   `r / ctx.scale` mapping positions use, so a body's viewed size IS its
   physical size (the Sun is ~7e-7 ru across at the default 1e15 m/ru scale).

   Bodies too small to subtend a pixel are handed to the sprite-LOD pass
   (`classify-body-lod`), which renders them as clamped glints — discoverable
   at any distance without lying about scale. To SEE a body as a globe the
   camera must genuinely approach it (selection tether)."
  [ctx r-phys]
  (let [r (double (or r-phys 0.0))
        s (double (:scale ctx))]
    (if (pos? r)
      (max body-radius-floor-ru (/ r s))
      body-radius-floor-ru)))

;; ---------------------------------------------------------------------------
;; Camera basis shared by screen / ray transforms
;; ---------------------------------------------------------------------------

(def ^:private fov-deg 60.0)
(def ^:private near
  "Behind-camera cutoff for projection/picking. Small enough that a camera
   tethered a few body-radii from a true-scale planet (~1e-7 ru) still projects
   it; this is a guard against division blow-up, not the GL near plane."
  1.0e-8)

(defn camera-basis
  "Orthonormal view frame {:cam-pos :fwd :right :up :tan-half} for the context's
   camera, matching `cam/look-at` and the volume shader exactly: fwd =
   target−cam-pos, right = fwd×[0 0 1], up = right×fwd. World is z-up."
  [ctx]
  (let [camera (:camera ctx)
        cam-pos (vec (:position camera))
        fwd     (cam/normalize (sp/v- (:target camera) cam-pos))
        right   (cam/normalize (cam/cross fwd [0.0 0.0 1.0]))
        up      (cam/cross right fwd)]
    {:cam-pos cam-pos :fwd fwd :right right :up up
     :tan-half (math/tan (cam/deg->rad (/ fov-deg 2.0)))}))

;; ---------------------------------------------------------------------------
;; Screen / ray transforms
;; ---------------------------------------------------------------------------

(defn render->screen
  "Render-unit point → [sx sy depth] in framebuffer pixels, or nil when behind
   the camera. Inverse of `screen->render-ray` through the same basis."
  [ctx pos]
  (let [{:keys [cam-pos fwd right up tan-half]} (camera-basis ctx)
        w (:width (:viewport ctx))
        h (:height (:viewport ctx))
        aspect (/ (double w) (double h))
        rel (sp/v- (vec pos) cam-pos)
        zc  (sp/dot rel fwd)]
    (when (> zc near)
      (let [xc (sp/dot rel right)
            yc (sp/dot rel up)
            ndcx (/ xc (* zc aspect tan-half))
            ndcy (/ yc (* zc tan-half))
            sx (* (+ (* ndcx 0.5) 0.5) w)
            sy (* (- 1.0 (+ (* ndcy 0.5) 0.5)) h)]
        [sx sy zc]))))

(defn screen->render-ray
  "Framebuffer pixel coordinates → pick ray {:ro render-pos :rd
   normalized-dir} in render space. Uses the same projection path as the
   renderer so picking and rendering cannot drift."
  [ctx px py]
  (let [{:keys [cam-pos fwd right up tan-half]} (camera-basis ctx)
        w (:width (:viewport ctx))
        h (:height (:viewport ctx))
        aspect (/ (double w) (double h))
        ndcx (- (/ (* 2.0 px) w) 1.0)
        ndcy (- 1.0 (/ (* 2.0 py) h))
        rd (cam/normalize
            (sp/v+ fwd
                   (sp/v+ (sp/v* right (* ndcx aspect tan-half))
                          (sp/v* up (* ndcy tan-half)))))]
    {:ro cam-pos :rd rd}))
