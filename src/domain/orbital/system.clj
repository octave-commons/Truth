(ns domain.orbital.system
  "Orbital physics system for Gates of Truth.
   Queries position + velocity + mass components via ECS.
   Returns world with updated position + velocity components."
  (:require
    [domain.ecs.core        :as ecs]
    [domain.ecs.components  :as c]
    [domain.gravity.barnes-hut :as bh]
    [domain.orbital.integrator :as integrator]
    [shape.spatial          :as sp]))

(defn- world->bodies
  "Project ECS world into a seq of body maps for the Barnes-Hut tree."
  [world]
  (map (fn [[eid comps]]
         {:id       eid
          :mass     (comps c/mass)
          :radius   (comps c/radius)
          :kind     (comps c/body-kind)
          :position (comps c/position)
          :velocity (comps c/velocity)})
       (ecs/all-of world c/position c/velocity c/mass c/radius c/body-kind)))

(defn- apply-body-back
  "Write updated position and velocity for eid back into world."
  [world eid body]
  (-> world
      (ecs/put-component eid c/position (:position body))
      (ecs/put-component eid c/velocity (:velocity body))))

(defn orbital-system
  "ECS system: advances all entities with position+velocity+mass
   by one Leapfrog step under mutual gravitational attraction."
  [G theta dt]
  (fn [world]
    (let [bodies (world->bodies world)
          tree   (bh/build-tree bodies)]
      (reduce (fn [w body]
                (let [updated (integrator/leapfrog-step
                                body
                                (fn [b] (bh/acceleration G theta tree b))
                                dt)]
                  (apply-body-back w (:id body) updated)))
              world
              bodies))))
