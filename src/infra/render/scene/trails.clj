(ns infra.render.scene.trails
  "Project body histories through the existing raw-line render path."
  (:require
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.trail :as trail]
   [infra.render.units :as units]
   [law.trail :as law]
   [shape.trail :as shape]))

(defn- body-segments [world eid]
  (let [position (ecs/get-component world eid c/position)
        body-kind (ecs/get-component world eid c/body-kind)
        matter-state (ecs/get-component world eid c/matter-state)]
    (when (trail/eligible? {:position position :body-kind body-kind :matter-state matter-state})
      {:entity eid :spark? (= :spark body-kind)
       :segments (shape/segments (ecs/get-component world eid c/motion-trail)
                                 {:time (:genesis/sim-time world 0.0) :position position})})))

(defn- trail-color [world eid]
  (cond
    (= :spark (ecs/get-component world eid c/body-kind)) [0.35 0.85 1.0]
    (= :star (ecs/get-component world eid c/matter-state)) [1.0 0.75 0.35]
    :else [0.5 0.7 1.0]))

(defn project-trails
  "Return bounded line vertices and explicit coverage counts as pure data.

   Shape history/head contracts are enforced by shape.trail/segments. World
   coordinates are scaled here; scene setup applies the raw render origin."
  [ctx world]
  (let [bodies (keep #(body-segments world %)
                     (ecs/entities-with world c/motion-trail c/position))
        {:keys [segments summary]} (shape/budget-segments bodies law/segment-budget)]
    {:shapes (into []
                   (mapcat (fn [{:keys [entity vertices]}]
                             (let [color (trail-color world entity)]
                               (map (fn [vertex]
                                      (-> vertex
                                          (update :position #(units/world->render ctx %))
                                          (assoc :color color :render-mode :line :trail/entity entity)))
                                    vertices))))
                   segments)
     :summary summary}))

(def ^:private reported-budget (atom nil))

(defn report-budget!
  "Report changed cap losses to runtime diagnostics, without identical spam."
  [{:keys [dropped] :as summary}]
  (when (not= summary @reported-budget)
    (reset! reported-budget summary)
    (when (pos? dropped)
      (binding [*out* *err*]
        (println "Motion trail segment budget:" (pr-str summary))))))
