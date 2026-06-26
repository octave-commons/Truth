(ns domain.orbital.system
  "Orbital physics system for Gates of Truth.
   Queries position + velocity + mass components via ECS.
   Returns world with updated position + velocity components."
  (:require
    [domain.ecs.core        :as ecs]
    [domain.ecs.components  :as c]
    [domain.ecs.parallel    :as par]
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

(defn- total-acceleration
  "Gravitational acceleration from the Barnes–Hut tree plus any pre-computed
   hydrodynamic pressure-gradient acceleration stored on `c/hydro-accel`."
  [G theta softening world tree body]
  (let [grav (bh/acceleration G theta softening tree body)
        hydro (or (ecs/get-component world (:id body) c/hydro-accel)
                  [0.0 0.0 0.0])]
    (sp/v+ grav hydro)))

(defn orbital-system
  "ECS system: advances all entities with position+velocity+mass by one Leapfrog
   step under mutual gravitational attraction plus any stored hydrodynamic
   acceleration.

   The Barnes–Hut tree is immutable once built, so per-body accelerations are
   computed in parallel (pmap) across cores — the single most expensive part of
   the tick — and the results applied sequentially. `softening` is the Plummer
   length passed to the gravity kernel."
  ([G theta dt] (orbital-system G theta dt bh/default-softening))
  ([G theta dt softening]
   (fn [world]
     (let [bodies  (world->bodies world)
           tree    (bh/build-tree bodies)
           updated (par/par-mapv
                    (fn [body]
                      (integrator/leapfrog-step
                       body
                       (fn [b] (total-acceleration G theta softening world tree b))
                       dt))
                    bodies)]
       (reduce (fn [w body] (apply-body-back w (:id body) body))
               world
               updated)))))
