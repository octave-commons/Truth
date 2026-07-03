(ns infra.input
  "Player input dispatch: translate a control action into an update of the
   world's observer entity. Input handling lives in infra/ (it is driven by the
   window/renderer event loop); the observer mechanics it delegates to live in
   domain.player. Pure: world -> world'."
  (:require
   [domain.player :as player]
   [shape.spatial :as sp]))

(defn handle-input
  "Apply a player control to the world's observer."
  [world input-type & args]
  (case input-type
    :move-focus  (let [[pos] args]
                   (player/update-observer world
                                           #(player/set-focus % pos (:focus-radius %) (:focus-intensity %))))
    :narrow-focus (player/update-observer world #(player/narrow-focus % 2.0))
    :widen-focus  (player/update-observer world #(player/widen-focus % 2.0))
    :release      (player/update-observer world
                                          #(player/release-focus %
                                                                 (fn [pos]
                                                                   (let [dir (sp/v- (sp/vec3 0 0 0) pos)
                                                                         l   (sp/len dir)]
                                                                     (if (pos? l) (sp/v* dir (/ 1.0 l)) dir)))))
    world))
