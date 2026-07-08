(ns infra.inspect.card
  "Inspector card layout and rendering for the selected body.

   Builds a titled HUD card of live ECS facts, anchored beside the body's screen
   position and clamped on-screen. Depends on infra.inspect.format for the fact
   and colour formatting, and on infra.inspect.picking for selected-shape."
  (:require
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.naming :as naming]
   [infra.inspect.format :as fmt]
   [infra.inspect.picking :as picking]
   [infra.render.units :as units]))

;; ---------------------------------------------------------------------------
;; Inspector card — live ECS readout, drawn via the HUD text/rect programs.
;; ---------------------------------------------------------------------------

(defn- card-lines
  "The title + fact lines for the inspector card."
  [world eid state]
  (let [title (naming/display-label eid state)
        facts (fmt/body-facts world eid)]
    (into [title] (map (fn [[k v]] (format "%-7s%s" k v)) facts))))

(defn- card-bounds
  "Card dimensions from its line count."
  [lines]
  (let [scale   2.0
        line-h  20.0
        pad     12.0
        char-w  (* scale 6.2)
        card-w  (+ (* 2 pad) (* char-w (double (apply max (map count lines)))))
        card-h  (+ (* 2 pad) (* line-h (count lines)))]
    [scale line-h pad card-w card-h]))

(defn- card-position
  "Pixel origin [x0 y0] for the card beside the body, clamped on-screen."
  [ctx shape viewport card-w card-h]
  (let [w       (:width viewport)
        h       (:height viewport)
        [bx by] (or (units/render->screen ctx (:position shape))
                    [(* 0.5 w) (* 0.5 h)])
        stats-floor 252.0
        x0      (if (> bx (* 0.62 w))
                  (max 12.0 (- bx card-w 28.0))
                  (max 12.0 (min (- w card-w 12.0) (+ bx 28.0))))
        y0      (max stats-floor (min (- h card-h 12.0) (- by (* 0.5 card-h))))]
    [x0 y0]))

(defn- card-rects
  "Background + accent rectangles for the inspector card."
  [x0 y0 card-w card-h line-h pad tcol w h]
  (let [px->ndcx (fn [px] (- (/ (* 2.0 px) w) 1.0))
        px->ndcy (fn [py] (- 1.0 (/ (* 2.0 py) h)))]
    [{:x0 (px->ndcx x0) :y0 (px->ndcy (+ y0 card-h))
      :x1 (px->ndcx (+ x0 card-w)) :y1 (px->ndcy y0)
      :color [0.04 0.06 0.12 0.82]}
     {:x0 (px->ndcx x0) :y0 (px->ndcy (+ y0 line-h pad -2.0))
      :x1 (px->ndcx (+ x0 card-w)) :y1 (px->ndcy (+ y0 line-h pad))
      :color tcol}]))

(defn- card-text
  "Text line maps for the inspector card."
  [lines x0 y0 pad line-h scale tcol]
  (map-indexed
   (fn [i s]
     {:text s
      :x (+ x0 pad)
      :y (+ y0 pad (* i line-h))
      :scale scale
      :color (if (zero? i) tcol [0.86 0.94 1.0 0.96])})
   lines))

(defn inspector-card
  "HUD content for the selected body: a titled card of live facts, anchored beside
   the body's screen position (clamped on-screen). Returns
   {:rects [...] :text [...]} ready for `render/render-hud` and `render/render-text`,
   or nil when the entity has no render shape this frame."
  [ctx world eid bodies]
  (when-let [shape (picking/selected-shape bodies eid)]
    (let [state     (ecs/get-component world eid c/matter-state)
          tcol      (fmt/state-color state)
          lines     (card-lines world eid state)
          [scale line-h pad card-w card-h] (card-bounds lines)
          [x0 y0]   (card-position ctx shape (:viewport ctx) card-w card-h)
          w         (:width (:viewport ctx))
          h         (:height (:viewport ctx))]
      {:rects (card-rects x0 y0 card-w card-h line-h pad tcol w h)
       :text  (card-text lines x0 y0 pad line-h scale tcol)})))
