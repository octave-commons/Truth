(ns infra.input
  "Player input dispatch: translate a control action into an update of the
   world's observer entity. Input handling lives in infra/ (it is driven by the
   window/renderer event loop); the observer mechanics it delegates to live in
   domain.player. Pure: world -> world'."
  (:require
   [domain.player :as player]))

(defn handle-input
  "Apply a player control to the world's observer."
  [world input-type & args]
  (case input-type
    :move-focus  (let [[pos] args]
                   (player/update-observer world
                                           #(player/set-focus % pos (:focus-radius %) (:focus-intensity %))))
    :narrow-focus (player/update-observer world #(player/narrow-focus % 2.0))
    :widen-focus  (player/update-observer world #(player/widen-focus % 2.0))
    ;; :release was deleted with the spark spring (spark-redesign card 4):
    ;; the free spark is owned by gravity now — "release" is simply the
    ;; absence of flight input.
    world))
