(ns infra.inspect
  "Body inspection: ray-picking, screen projection, and the selection overlay.

   The resolved bodies (stars, protostars, planets, debris, the player spark) are
   the *characters* of a Phase 0 scene — the gas and field lines are weather around
   them. This namespace makes the characters interactable: click one and the
   renderer draws a camera-facing halo + a velocity arrow around it and a HUD card
   reading its live ECS state.

   Everything here is pure render-space math. It reconstructs the SAME ray/basis
   the renderer and the volume shader use (`infra.render/render-scene`,
   `perspective 60°`, `look-at … up=[0 1 0]`) so a click lands where the body is
   drawn, and the halo/arrow ride on the body's already-projected render shape
   (never a recomputed position — see the 'two coordinate paths' hazard)."
  (:require
    [clojure.string :as str]
    [domain.ecs.core :as ecs]
    [domain.ecs.components :as c]
    [shape.spatial :as sp]))

(def ^:const fov-deg 60.0)
(def ^:const near 0.1)

(defn- normalize
  "Unit vector along v; +z for the zero vector (shape.spatial has no normalize)."
  [v]
  (let [l (sp/len v)] (if (pos? l) (sp/v* v (/ 1.0 l)) [0.0 0.0 1.0])))

;; Physical reference scales for human-readable readouts.
(def ^:const solar-mass   1.989e30)  ;; kg
(def ^:const solar-radius 6.957e8)   ;; m
(def ^:const solar-lum    3.828e26)  ;; W
(def ^:const earth-mass   5.972e24)  ;; kg
(def ^:const earth-radius 6.371e6)   ;; m
(def ^:const au           1.496e11)  ;; m

;; ---------------------------------------------------------------------------
;; Camera basis — the orthonormal frame the renderer draws through.
;; ---------------------------------------------------------------------------

(defn camera-basis
  "Orthonormal view frame {:pos :fwd :right :up :tan-half} for `camera`, matching
   `look-at`/the volume shader exactly: fwd = target−pos, right = fwd×[0 1 0],
   up = right×fwd."
  [camera]
  (let [pos   (vec (:position camera))
        fwd   (normalize (sp/v- (:target camera) pos))
        right (normalize (sp/cross fwd [0.0 1.0 0.0]))
        up    (sp/cross right fwd)]
    {:pos pos :fwd fwd :right right :up up
     :tan-half (Math/tan (Math/toRadians (/ fov-deg 2.0)))}))

;; ---------------------------------------------------------------------------
;; Screen → ray and world → screen (mutual inverses through the same basis).
;; ---------------------------------------------------------------------------

(defn screen->ray
  "World-space pick ray {:ro :rd} through pixel (px,py) on a w×h surface.
   (px,py) and (w,h) must be in the SAME pixel space (top-left origin)."
  [camera px py w h]
  (let [{:keys [pos fwd right up tan-half]} (camera-basis camera)
        aspect (/ (double w) (double h))
        ndcx (- (/ (* 2.0 px) w) 1.0)
        ndcy (- 1.0 (/ (* 2.0 py) h))
        rd   (normalize
               (sp/v+ fwd
                      (sp/v+ (sp/v* right (* ndcx aspect tan-half))
                             (sp/v* up    (* ndcy tan-half)))))]
    {:ro pos :rd rd}))

(defn project-point
  "Project render-space point `p` to pixel coords on a w×h surface.
   Returns [sx sy depth] (depth = metres along view fwd) or nil when behind the
   camera. Inverse of `screen->ray`, so the card anchors where the body draws."
  [camera p w h]
  (let [{:keys [pos fwd right up tan-half]} (camera-basis camera)
        rel (sp/v- (vec p) pos)
        zc  (sp/dot rel fwd)]
    (when (> zc near)
      (let [aspect (/ (double w) (double h))
            xc (sp/dot rel right)
            yc (sp/dot rel up)
            ndcx (/ xc (* zc aspect tan-half))
            ndcy (/ yc (* zc tan-half))
            sx (* (+ (* ndcx 0.5) 0.5) w)
            sy (* (- 1.0 (+ (* ndcy 0.5) 0.5)) h)]
        [sx sy zc]))))

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
  [bodies camera px py w h]
  (let [{:keys [ro rd tan-half]} (assoc (screen->ray camera px py w h)
                                        :tan-half (:tan-half (camera-basis camera)))]
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
   camera target (so the spark's attention rides the cluster the camera frames).
   `scale` converts render units → metres (render/phase0-view-scale)."
  [camera px py w h scale]
  (let [{:keys [ro rd]} (screen->ray camera px py w h)
        t  (max 0.0 (sp/dot (sp/v- (vec (:target camera)) ro) rd))
        pt (sp/v+ ro (sp/v* rd t))]
    (sp/v* pt (double scale))))

;; ---------------------------------------------------------------------------
;; Selection overlay — camera-facing halo + velocity arrow (as :line shapes).
;; ---------------------------------------------------------------------------

(defn- line-seg [a b color]
  [{:position (vec a) :color color :size 1.0 :render-mode :line}
   {:position (vec b) :color color :size 1.0 :render-mode :line}])

(defn halo-shapes
  "A camera-facing ring of render radius `r` around `center`, as :line segments —
   the selection marker. Lives in the camera's right/up plane so it reads as a
   ring from any angle."
  [center r camera color n]
  (let [{:keys [right up]} (camera-basis camera)
        pt (fn [a]
             (sp/v+ (vec center)
                    (sp/v+ (sp/v* right (* r (Math/cos a)))
                           (sp/v* up    (* r (Math/sin a))))))]
    (vec (mapcat (fn [i]
                   (let [a0 (* 2.0 Math/PI (/ (double i) n))
                         a1 (* 2.0 Math/PI (/ (double (inc i)) n))]
                     (line-seg (pt a0) (pt a1) color)))
                 (range n)))))

(defn- speed-color
  "Cool→hot ramp by speed (km/s): slow teal → fast amber."
  [kms]
  (let [f (max 0.0 (min 1.0 (/ (Math/log10 (+ 1.0 (double kms))) 2.6)))]
    [(+ 0.30 (* 0.70 f)) (+ 0.85 (* -0.20 f)) (- 1.0 (* 0.7 f))]))

(defn velocity-arrow-shapes
  "An arrow from `center` along the body's world velocity, as :line segments.
   Direction is scale-invariant (render space is a uniform shrink of world), so we
   reuse the world velocity vector directly. Length is log-scaled by speed and
   floored to the body radius so even a slow body shows a stub; colour ramps with
   speed. nil for a motionless body."
  [center vel-world body-r camera]
  (when (and vel-world (pos? (sp/len vel-world)))
    (let [speed-ms (sp/len vel-world)
          kms      (/ speed-ms 1000.0)
          dir      (normalize vel-world)
          len      (+ (double body-r)
                      (max 0.6 (min 6.0 (* 1.1 (Math/log10 (+ 1.0 kms))))))
          tip      (sp/v+ (vec center) (sp/v* dir len))
          col      (speed-color kms)
          {:keys [fwd]} (camera-basis camera)
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

(defn hover-overlay-shapes
  "A faint, thin halo around the body the cursor is over — the passive 'this is
   resolvable' cue, shown before a click commits to selection. Empty when nothing
   is hovered or the hovered body is already the selection."
  [bodies hover-eid sel-eid camera]
  (if (and hover-eid (not= hover-eid sel-eid))
    (if-let [shape (selected-shape bodies hover-eid)]
      (halo-shapes (:position shape) (* (double (or (:radius shape) 0.6)) 1.3)
                   camera [0.35 0.55 0.7] 40)
      [])
    []))

(defn intervention-overlay-shapes
  "Camera-facing rings for the player's active warps (`:phase0/interventions`):
   a well reads cyan, a repulsor warm-orange; the ring sized to the warp's reach
   and dimmed as it decays, so a placed warp is visible and you watch it fade.
   `scale` converts world metres → render units."
  [world camera scale]
  (let [tick (long (or (:tick world) 0))]
    (vec
      (mapcat
        (fn [{:keys [kind position radius born-tick ttl]}]
          (let [center (mapv #(/ (double %) (double scale)) position)
                r      (/ (double radius) (double scale))
                age    (- tick (long (or born-tick 0)))
                fade   (max 0.15 (- 1.0 (/ (double age) (double (or ttl 1)))))
                col    (case kind
                         :warp/repulsor [(* 1.0 fade) (* 0.55 fade) (* 0.25 fade)] ;; warm orange
                         :warp/well     [(* 0.30 fade) (* 0.75 fade) (* 1.0 fade)] ;; cyan
                         :heat/source   [(* 1.0 fade) (* 0.35 fade) (* 0.12 fade)] ;; hot red
                         :heat/sink     [(* 0.55 fade) (* 0.85 fade) (* 1.0 fade)] ;; cold blue-white
                         [(* 0.30 fade) (* 0.75 fade) (* 1.0 fade)])]
            (into (halo-shapes center r camera col 64)
                  (halo-shapes center (* r 0.62) camera col 48))))
        (:phase0/interventions world)))))

(defn selection-overlay-shapes
  "Halo + velocity arrow for the selected entity, riding on its already-projected
   render shape so they align with the drawn body. Empty when the entity has no
   shape this frame (merged/destroyed → caller should clear the selection)."
  [world eid bodies camera]
  (if-let [shape (selected-shape bodies eid)]
    (let [center (:position shape)
          r      (double (or (:radius shape) 0.6))
          vel    (ecs/get-component world eid c/velocity)]
      (into (halo-shapes center (* r 1.45) camera [0.55 0.95 1.0] 56)
            (or (velocity-arrow-shapes center vel r camera) [])))
    []))

;; ---------------------------------------------------------------------------
;; Inspector card — live ECS readout, drawn via the HUD text/rect programs.
;; ---------------------------------------------------------------------------

(defn- fmt-mass [kg stellar?]
  (let [kg (double (or kg 0.0))]
    (cond
      (or stellar? (>= kg (* 0.05 solar-mass))) (format "%.3f Msun" (/ kg solar-mass))
      (>= kg (* 0.05 earth-mass)) (format "%.2f Mearth" (/ kg earth-mass))
      :else                       (format "%.2e kg" kg))))

(defn- fmt-radius [m star?]
  (let [m (double (or m 0.0))]
    (cond
      star?            (format "%.2f Rsun" (/ m solar-radius))
      (>= m earth-radius) (format "%.2f Rearth" (/ m earth-radius))
      (>= m 1.0e3)     (format "%.0f km" (/ m 1.0e3))
      :else            (format "%.2e m" m))))

(defn- fmt-comp
  "Top two composition fractions, e.g. \"H 0.74  He 0.24\"."
  [cm]
  (when (seq cm)
    (->> cm
         (sort-by (fn [[_ v]] (- (double v))))
         (take 2)
         (map (fn [[k v]] (format "%s %.2f" (name k) (double v))))
         (str/join "  "))))

(defn- state-label [state]
  (case state
    :nebula "Nebula gas"
    :protostar "Protostar"
    :star "Star"
    :planet "Planet"
    :debris "Debris"
    (some-> state name)))

(defn- state-color [state]
  (case state
    :star      [1.0 0.92 0.55 1.0]
    :protostar [1.0 0.72 0.45 1.0]
    :planet    [0.55 0.78 1.0 1.0]
    :debris    [0.75 0.75 0.8 1.0]
    :nebula    [0.7 0.6 0.9 1.0]
    [0.85 0.9 1.0 1.0]))

(defn body-facts
  "Ordered [label value] readout lines for entity `eid` from its live ECS state."
  [world eid]
  (let [g     (fn [k] (ecs/get-component world eid k))
        state (g c/matter-state)
        stellar? (boolean (#{:star :protostar} state))
        vel   (g c/velocity)
        speed (when vel (/ (sp/len vel) 1000.0))
        temp  (g c/temperature)
        lum   (g c/luminosity)
        regime (g c/regime)
        comp  (fmt-comp (g c/composition))]
    (cond-> [["mass"  (fmt-mass (g c/mass) stellar?)]
             ["radius" (fmt-radius (g c/radius) stellar?)]]
      temp        (conj ["temp"  (format "%.0f K" (double temp))])
      speed       (conj ["speed" (format "%.2f km/s" (double speed))])
      (and lum (pos? (double lum)))
      (conj ["lum"   (format "%.3g Lsun" (/ (double lum) solar-lum))])
      comp        (conj ["comp"  comp])
      regime      (conj ["regime" (name regime)])
      true        (conj ["eid"   (str eid)]))))

(defn inspector-card
  "HUD content for the selected body: a titled card of live facts, anchored beside
   the body's screen position (clamped on-screen). Returns
   {:rects [...] :text [...]} ready for `render/render-hud` and `render/render-text`,
   or nil when the entity has no render shape this frame."
  [world eid bodies camera w h]
  (when-let [shape (selected-shape bodies eid)]
    (let [state   (ecs/get-component world eid c/matter-state)
          title   (or (state-label state) "Body")
          tcol    (state-color state)
          facts   (body-facts world eid)
          lines   (into [title] (map (fn [[k v]] (format "%-7s%s" k v)) facts))
          scale   2.0
          line-h  20.0
          pad     12.0
          char-w  (* scale 6.2)
          card-w  (+ (* 2 pad) (* char-w (double (apply max (map count lines)))))
          card-h  (+ (* 2 pad) (* line-h (count lines)))
          anchor  (project-point camera (:position shape) w h)
          [bx by] (if anchor anchor [(* 0.5 w) (* 0.5 h)])
          ;; place beside the body, clamped on-screen and BELOW the top-left
          ;; stats panel (its IMF line spans nearly the full width) so the card
          ;; never collides with the world HUD text drawn in the same pass.
          stats-floor 252.0
          ;; sit to the body's right normally, but flip left when the body is in
          ;; the right third so the card stays clear of the action palette there.
          x0 (if (> bx (* 0.62 w))
               (max 12.0 (- bx card-w 28.0))
               (max 12.0 (min (- w card-w 12.0) (+ bx 28.0))))
          y0 (max stats-floor (min (- h card-h 12.0) (- by (* 0.5 card-h))))
          px->ndcx (fn [px] (- (/ (* 2.0 px) w) 1.0))
          px->ndcy (fn [py] (- 1.0 (/ (* 2.0 py) h)))
          rects [{:x0 (px->ndcx x0) :y0 (px->ndcy (+ y0 card-h))
                  :x1 (px->ndcx (+ x0 card-w)) :y1 (px->ndcy y0)
                  :color [0.04 0.06 0.12 0.82]}
                 ;; accent bar under the title
                 {:x0 (px->ndcx x0) :y0 (px->ndcy (+ y0 line-h pad -2.0))
                  :x1 (px->ndcx (+ x0 card-w)) :y1 (px->ndcy (+ y0 line-h pad))
                  :color tcol}]
          text  (map-indexed
                  (fn [i s]
                    {:text s
                     :x (+ x0 pad)
                     :y (+ y0 pad (* i line-h))
                     :scale scale
                     :color (if (zero? i) tcol [0.86 0.94 1.0 0.96])})
                  lines)]
      {:rects rects :text text})))
