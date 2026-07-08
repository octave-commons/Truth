(ns infra.render.scene.hud
  "Player HUD and overlay render helpers.

   Observer spark, focus reticle, and coherence bars are built here as pure
   render-shape maps. No GL calls."
  (:require
   [clojure.math :as math]
   [domain.player :as player]
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

(defn hud-rects-from-world
  "HUD rectangles (NDC) for the observer: a coherence track + fill and a thin
   focus-intensity bar. Empty when there is no observer."
  [world]
  (if-let [obs (player/get-observer world)]
    (let [coh  (double (or (:coherence obs) 0.0))
          mx   (double (or (:max-coherence obs) 1.0))
          frac (max 0.0 (min 1.0 (/ coh (max 1e-9 mx))))
          fi   (double (or (:focus-intensity obs) 0.5))
          col  (conj (rcolor/coherence-color (player/decoherence-state obs)) 0.92)
          x0 -0.96 x1 -0.46 y0 -0.93 y1 -0.89]
      [{:x0 x0 :y0 y0 :x1 x1 :y1 y1 :color [0.10 0.10 0.16 0.65]}
       {:x0 x0 :y0 y0 :x1 (+ x0 (* (- x1 x0) frac)) :y1 y1 :color col}
       {:x0 x0 :y0 -0.875 :x1 (+ x0 (* (- x1 x0) fi)) :y1 -0.86
        :color [0.70 0.86 1.0 0.85]}])
    []))
