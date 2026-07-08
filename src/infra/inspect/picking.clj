(ns infra.inspect.picking
  "Picking and projection helpers for body inspection.

   Screen → ray, render → screen, and forgiving ray-vs-body picking against the
   same shapes the renderer drew this frame so picking and visuals cannot drift."
  (:require
   [shape.spatial :as sp]
   [infra.render.units :as units]))

;; ---------------------------------------------------------------------------
;; Screen → ray and render → screen (mutual inverses through units).
;; ---------------------------------------------------------------------------

(defn screen->ray
  "Render-space pick ray {:ro :rd} through pixel (px,py)."
  [ctx px py]
  (units/screen->render-ray ctx px py))

(defn project-point
  "Project render-space point `p` to framebuffer pixels [sx sy depth], or nil
   when behind the camera. Delegates to `infra.render.units/render->screen`."
  [ctx p]
  (units/render->screen ctx p))

;; ---------------------------------------------------------------------------
;; Picking — forgiving ray-vs-body test.
;; ---------------------------------------------------------------------------

(defn- body-shapes
  "The selectable render shapes: drawn spheres that carry an :entity id."
  [bodies]
  (filter #(and (= :body (:render-mode % :body)) (:entity %)) bodies))

(defn selected-shape
  "The already-projected render shape for entity `eid`, or nil."
  [bodies eid]
  (first (filter #(= eid (:entity %)) (body-shapes bodies))))

(defn pick-entity
  "Entity id of the body the ray through (px,py) hits, nearest-first, or nil.

   Forgiving: a body counts as hit when the ray passes within its (slightly
   inflated) render radius OR within a constant screen-space tolerance, so tiny
   distant bodies stay clickable without precise aim. Picks against the SAME
   shapes the renderer drew this frame, so it can't drift from the visuals."
  [ctx bodies px py]
  (let [{:keys [ro rd] tan-half :tan-half} (assoc (units/screen->render-ray ctx px py)
                                                  :tan-half (:tan-half (units/camera-basis ctx)))]
    (->> (body-shapes bodies)
         (keep (fn [{:keys [entity position radius]}]
                 (let [center (vec position)
                       rad    (double (or radius 0.5))
                       t*     (sp/dot (sp/v- center ro) rd)]
                   (when (pos? t*)
                     (let [closest (sp/v+ ro (sp/v* rd t*))
                           perp    (sp/len (sp/v- closest center))
                           ;; screen-space slack: ≥ this many render units at depth t*
                           slack   (* 0.018 t* tan-half)
                           thresh  (max (* rad 1.25) slack)]
                       (when (<= perp thresh)
                         [entity t*]))))))
         (sort-by second)
         ffirst)))

(defn cursor->world
  "World-metre point under pixel (px,py), placed on the depth plane through the
   camera target (so the spark's attention rides the cluster the camera frames)."
  [ctx px py]
  (let [{:keys [ro rd]} (units/screen->render-ray ctx px py)
        t  (max 0.0 (sp/dot (sp/v- (vec (:target (:camera ctx))) ro) rd))
        pt (sp/v+ ro (sp/v* rd t))]
    (units/render->world ctx pt)))
