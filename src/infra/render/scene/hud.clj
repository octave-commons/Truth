(ns infra.render.scene.hud
  "Player HUD and overlay render helpers.

   Observer spark, focus reticle, and coherence bars are built here as pure
   render-shape maps. No GL calls."
  (:require
   [clojure.math :as math]
   [domain.player :as player]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [infra.render.color :as rcolor]
   [infra.render.units :as units]))

(defn- ring-segments
  "Line-segment endpoints approximating a circle of `radius` render units in the
   xy-plane at `center`, as :line render shapes."
  [center radius color n]
  (let [[cx cy cz] center]
    (vec (mapcat
          (fn [i]
            (let [a0 (* 2.0 math/PI (/ (double i) n))
                  a1 (* 2.0 math/PI (/ (double (inc i)) n))]
              [{:position [(+ cx (* radius (math/cos a0))) (+ cy (* radius (math/sin a0))) cz]
                :color color :size 1.0 :render-mode :line}
               {:position [(+ cx (* radius (math/cos a1))) (+ cy (* radius (math/sin a1))) cz]
                :color color :size 1.0 :render-mode :line}]))
          (range n)))))

(defn player-overlay-shapes
  "Render shapes for the player's spark and focus volume: a bright point at the
   observer position and a reticle ring at the focus, tinted by coherence."
  [ctx world]
  (if-let [obs (player/get-observer world)]
    (let [fpos  (units/world->render ctx (:focus-position obs))
          fr    (units/world->render ctx [(:focus-radius obs) 0.0 0.0])
          spark (units/world->render ctx (:position obs))
          col   (rcolor/coherence-color (player/decoherence-state obs))]
      (into [{:position spark :color [0.85 0.96 1.0]
              :size (+ 28.0 (* 44.0 (double (:focus-intensity obs 0.5))))
              :render-mode :particle}]
            (ring-segments fpos (max 0.5 (first fr)) col 48)))
    []))

(defn- mood-color
  "Subtle tint colour for a narrative mood, as [r g b a] in NDC."
  [mood]
  (case mood
    :wonder        [0.25 0.32 0.55 0.12]
    :dread         [0.45 0.12 0.12 0.14]
    :tenderness    [0.42 0.25 0.42 0.10]
    :sterility     [0.55 0.55 0.55 0.10]
    :anticipation  [0.20 0.30 0.35 0.08]
    [0.10 0.12 0.16 0.06]))

(defn hud-rects-from-world
  "HUD rectangles (NDC) for the observer: a coherence track + fill, a thin
   focus-intensity bar, and a subtle mood tint. Empty when there is no observer."
  [world]
  (if-let [eid (player/observer-entity world)]
    (let [obs  (player/get-observer world)
          nstate (ecs/get-component world eid c/narrative-state)
          mood (:mood nstate :anticipation)
          coh  (double (or (:coherence obs) 0.0))
          mx   (double (or (:max-coherence obs) 1.0))
          frac (max 0.0 (min 1.0 (/ coh (max 1e-9 mx))))
          fi   (double (or (:focus-intensity obs) 0.5))
          col  (conj (rcolor/coherence-color (player/decoherence-state obs)) 0.92)
          x0 -0.96 x1 -0.46 y0 -0.93 y1 -0.89]
      [{:x0 -1.0 :y0 -1.0 :x1 1.0 :y1 1.0 :color (mood-color mood)}
       {:x0 x0 :y0 y0 :x1 x1 :y1 y1 :color [0.10 0.10 0.16 0.65]}
       {:x0 x0 :y0 y0 :x1 (+ x0 (* (- x1 x0) frac)) :y1 y1 :color col}
       {:x0 x0 :y0 -0.875 :x1 (+ x0 (* (- x1 x0) fi)) :y1 -0.86
        :color [0.70 0.86 1.0 0.85]}])
    []))
