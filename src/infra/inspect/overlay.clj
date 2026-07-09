(ns infra.inspect.overlay
  "Selection overlay shapes: camera-facing halos and velocity arrows.

   These are rendered as :line segments on top of the Phase 0 scene. The overlay
   namespace depends on infra.inspect.picking for selected-shape and on
   infra.render.units for the camera basis and world/render transforms."
  (:require
   [clojure.math :as math]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [infra.inspect.picking :as picking]
   [infra.render.units :as units]
   [shape.spatial :as sp]))

;; ---------------------------------------------------------------------------
;; Selection overlay — camera-facing halo + velocity arrow (as :line shapes).
;; ---------------------------------------------------------------------------

(defn- line-seg [a b color]
  [{:position (vec a) :color color :size 1.0 :render-mode :line}
   {:position (vec b) :color color :size 1.0 :render-mode :line}])

(defn- adaptive-segments
  "Compute a smooth segment count for a halo of render radius `r` centered at
   `center`, given the current camera context. The ring is subdivided so each
   segment is roughly four pixels on screen, capped between 32 and 256."
  [r center ctx]
  (let [{:keys [cam-pos tan-half]} (units/camera-basis ctx)
        h (double (get-in ctx [:viewport :height]))
        dist (max 1.0e-12 (sp/len (sp/v- (vec center) cam-pos)))
        screen-r (* (double r) (/ h (* 2.0 dist tan-half)))
        n (int (/ (* 2.0 math/PI screen-r) 4.0))]
    (max 32 (min 256 n))))

(defn- normalize [v]
  (let [l (sp/len v)] (if (pos? l) (sp/v* v (/ 1.0 l)) [0.0 0.0 1.0])))

(defn halo-shapes
  "A camera-facing ring of render radius `r` around `center`, as :line segments —
   the selection marker. Lives in the camera's right/up plane so it reads as a
   ring from any angle. `n` is a minimum segment count; if the halo is large on
   screen the count is raised so the ring stays smooth when zoomed in."
  [{:keys [center r ctx color n]}]
  (let [segs (max (int (or n 32)) (adaptive-segments r center ctx))
        {:keys [right up]} (units/camera-basis ctx)
        pt (fn [a]
             (sp/v+ (vec center)
                    (sp/v+ (sp/v* right (* r (math/cos a)))
                           (sp/v* up    (* r (math/sin a))))))
        pts (mapv (fn [i] (pt (* 2.0 math/PI (/ (double i) segs)))) (range segs))]
    (vec (mapcat (fn [i]
                   (line-seg (nth pts i)
                             (nth pts (mod (inc i) segs))
                             color))
                 (range segs)))))

(defn- speed-color
  "Cool→hot ramp by speed (km/s): slow teal → fast amber."
  [kms]
  (let [f (max 0.0 (min 1.0 (/ (math/log10 (+ 1.0 (double kms))) 2.6)))]
    [(+ 0.30 (* 0.70 f)) (+ 0.85 (* -0.20 f)) (- 1.0 (* 0.7 f))]))

(defn velocity-arrow-shapes
  "An arrow from `center` along the body's world velocity, as :line segments.
   Direction is scale-invariant (render space is a uniform shrink of world), so we
   reuse the world velocity vector directly. Length scales with the passed
   `body-r` (the caller supplies a screen-floored radius, so the arrow stays
   proportionate whether framing the nebula or hovering over a true-scale
   planet) and stretches with log speed; colour ramps with speed. nil for a
   motionless body."
  [center vel-world body-r ctx]
  (when (and vel-world (pos? (sp/len vel-world)))
    (let [speed-ms (sp/len vel-world)
          kms      (/ speed-ms 1000.0)
          dir      (normalize vel-world)
          len      (* (double body-r)
                      (+ 1.5 (min 4.0 (* 1.1 (math/log10 (+ 1.0 kms))))))
          tip      (sp/v+ (vec center) (sp/v* dir len))
          col      (speed-color kms)
          {:keys [fwd]} (units/camera-basis ctx)
          ;; arrowhead in the plane facing the camera
          perp     (let [p (sp/cross dir fwd)]
                     (if (pos? (sp/len p)) (normalize p)
                         (normalize (sp/cross dir [0.0 1.0 0.0]))))
          hl       (* 0.28 len)
          hw       (* 0.16 len)
          back     (sp/v- tip (sp/v* dir hl))]
      (into (line-seg center tip col)
            (concat (line-seg tip (sp/v+ back (sp/v* perp hw)) col)
                    (line-seg tip (sp/v- back (sp/v* perp hw)) col))))))

(defn overlay-radius
  "Halo radius for a body of render radius `r` at `center`: `k`× the body, but
   never smaller than `min-frac` of the view height at the body's depth. At true
   scale most bodies are sub-pixel — the floor is what keeps the selection ring
   readable; it shrinks naturally as the tethered camera closes in."
  [{:keys [ctx center r k min-frac]}]
  (let [{:keys [cam-pos tan-half]} (units/camera-basis ctx)
        dist (sp/len (sp/v- (vec center) cam-pos))]
    (max (* (double (or r 0.0)) (double k))
         (* (double min-frac) dist (double tan-half)))))

(defn hover-overlay-shapes
  "A faint, thin halo around the body the cursor is over — the passive 'this is
   resolvable' cue, shown before a click commits to selection. Empty when nothing
   is hovered or the hovered body is already the selection."
  [ctx bodies hover-eid sel-eid]
  (if (and hover-eid (not= hover-eid sel-eid))
    (if-let [shape (picking/selected-shape bodies hover-eid)]
      (halo-shapes {:center (:position shape)
                    :r (overlay-radius {:ctx ctx
                                        :center (:position shape)
                                        :r (double (or (:radius shape) 0.6))
                                        :k 1.3
                                        :min-frac 0.014})
                    :ctx ctx
                    :color [0.35 0.55 0.7]
                    :n 40})
      [])
    []))

(defn intervention-overlay-shapes
  "Camera-facing rings for the player's active warps (`:genesis/interventions`):
   a well reads cyan, a repulsor warm-orange; the ring sized to the warp's reach
   and dimmed as it decays, so a placed warp is visible and you watch it fade."
  [ctx world]
  (let [tick (long (or (:tick world) 0))]
    (vec
     (mapcat
      (fn [{:keys [kind position radius born-tick ttl]}]
        (let [center (units/world->render ctx position)
              r      (units/world->render ctx [radius 0.0 0.0])
              age    (- tick (long (or born-tick 0)))
              fade   (max 0.15 (- 1.0 (/ (double age) (double (or ttl 1)))))
              col    (case kind
                       :warp/repulsor [(* 1.0 fade) (* 0.55 fade) (* 0.25 fade)] ;; warm orange
                       :warp/well     [(* 0.30 fade) (* 0.75 fade) (* 1.0 fade)] ;; cyan
                       :heat/source   [(* 1.0 fade) (* 0.35 fade) (* 0.12 fade)] ;; hot red
                       :heat/sink     [(* 0.55 fade) (* 0.85 fade) (* 1.0 fade)] ;; cold blue-white
                       [(* 0.30 fade) (* 0.75 fade) (* 1.0 fade)])]
          (into (halo-shapes {:center center :r (first r) :ctx ctx :color col :n 64})
                (halo-shapes {:center center :r (* (first r) 0.62) :ctx ctx :color col :n 48}))))
      (:genesis/interventions world)))))

(defn selection-overlay-shapes
  "Halo + velocity arrow for the selected entity, riding on its already-projected
   render shape so they align with the drawn body. Empty when the entity has no
   shape this frame (merged/destroyed → caller should clear the selection)."
  [ctx world eid bodies]
  (if-let [shape (picking/selected-shape bodies eid)]
    (let [center (:position shape)
          r      (overlay-radius {:ctx ctx :center center
                                  :r (double (or (:radius shape) 0.6))
                                  :k 1.45
                                  :min-frac 0.02})
          vel    (ecs/get-component world eid c/velocity)]
      (into (halo-shapes {:center center :r r :ctx ctx :color [0.55 0.95 1.0] :n 56})
            (or (velocity-arrow-shapes center vel (* 0.7 r) ctx) [])))
    []))
