(ns infra.render.units
  "Pure coordinate transforms between physical, render, NDC and screen spaces
   for the Phase 0 renderer.

   All public functions are pure: no OpenGL, no atoms, no I/O. The
   `RenderContext` record carries the scale (physical metres per render unit),
   the camera value from `infra.camera`, and the viewport in pixels."
  (:require
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

(defn valid-context?
  "True when `ctx` is a RenderContext with positive scale and a viewport."
  [ctx]
  (and (instance? RenderContext ctx)
       (pos? (:scale ctx))
       (map? (:viewport ctx))
       (pos? (:width (:viewport ctx)))
       (pos? (:height (:viewport ctx)))))

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
  [ctx r-phys]
  (let [r (double (or r-phys 0.0))]
    (if (pos? r)
      (let [linear (/ r render-radius-ref)
            log-r  (* 0.42 (Math/log10 (max 1e-6 linear)))]
        (max (* 0.5 linear) (+ 0.01 log-r) 0.001))
      0.001)))

(defn render->phys-radius
  "Approximate inverse of `phys->render-radius` for debug/tooling only. Not for
   physics."
  [ctx r-render]
  (let [r (double (or r-render 0.001))]
    (* r render-radius-ref)))

;; ---------------------------------------------------------------------------
;; Camera basis shared by screen / ray transforms
;; ---------------------------------------------------------------------------

(def ^:private fov-deg 60.0)
(def ^:private near 0.1)

(defn camera-basis
  "Orthonormal view frame {:cam-pos :fwd :right :up :tan-half} for the context's
   camera, matching `cam/look-at` and the volume shader exactly: fwd =
   target−cam-pos, right = fwd×[0 1 0], up = right×fwd."
  [ctx]
  (let [camera (:camera ctx)
        cam-pos (vec (:position camera))
        fwd     (cam/normalize (sp/v- (:target camera) cam-pos))
        right   (cam/normalize (cam/cross fwd [0.0 1.0 0.0]))
        up      (cam/cross right fwd)]
    {:cam-pos cam-pos :fwd fwd :right right :up up
     :tan-half (Math/tan (cam/deg->rad (/ fov-deg 2.0)))}))

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
