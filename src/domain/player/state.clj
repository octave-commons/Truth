(ns domain.player.state
  "Observer entity construction and ECS access."
  (:require
   [shape.spatial :as sp]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]))

(defn create-observer "A fresh observer map at the given position." [position] {:agency 0.0, :focus-radius 5.0E15, :time-witnessed 0.0, :last-tick 0, :focus-intensity 0.5, :drift-velocity (sp/vec3 0 0 0), :coherence 0.8, :resolution 0.0, :id (java.util.UUID/randomUUID), :resonance-thresholds #{}, :position position, :focus-position position, :narrative-seeds {}, :resonance 0.0, :max-coherence 1.0, :resonance-events []})

(defn observer-entity "The singleton observer entity id, or nil." [world] (first (ecs/entities-with world c/observer)))

(defn get-observer "The observer map from the world." [world] (when-let [eid (observer-entity world)] (ecs/get-component world eid c/observer)))

(defn put-observer "Replace the observer component in the world, returning the updated world." [world observer] (if-let [eid (observer-entity world)] (ecs/put-component world eid c/observer observer) world))

(defn update-observer "Apply f to the observer map in the world." [world f & args] (if-let [eid (observer-entity world)] (ecs/update-component world eid c/observer (fn* [p1__247#] (apply f p1__247# args))) world))

(defn spawn-observer "Spawn the singleton observer entity. Returns [world eid]." [world position] (let [[w eid] (ecs/spawn world)] [(ecs/put-component w eid c/observer (create-observer position)) eid]))
