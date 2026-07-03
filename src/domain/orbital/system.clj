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
   length passed to the gravity kernel.

   LEGACY: this fuses gravity computation and Leapfrog integration into one
   system, so the expensive tree-walk blocks the rest of the tick. The
   double-buffer pipeline replaces it with `gravity-acceleration` (own thread)
   + `motion-integration` (see below). Kept for the sequential path until the
   parallel tick goes live."
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

;; --- Double-buffer split: gravity emitter + motion integrator ----------------

(def ^:private zero3 [0.0 0.0 0.0])

;; The single-owner acceleration contributions the motion integrator sums. Each
;; is written by exactly one fan-out system (gravity / hydro / em). New force
;; sources join this list; the integrator stays unchanged.
(def ^:private accel-sources
  [c/accel-gravity c/accel-pressure c/accel-lorentz c/accel-observer c/accel-warp])

(defn gravity-acceleration
  "Write-set system: per-body Barnes–Hut self-gravity → `accel.gravity`.

   Reads the shared spatial tree from :genesis/spatial-tree (built once per tick
   by domain.spatial.index/spatial-index) instead of constructing its own.
   The tree contains ALL entities; gravity computes acceleration for every body
   in the tree. Self-gravity is skipped by the Barnes–Hut walker at leaf nodes
   via the body's `:id`.

   When `:genesis/physics-soa` is present, the Barnes-Hut tree is walked directly
   against the primitive arrays via `bh/acceleration-for-soa`; otherwise the
   already projected `:genesis/spatial-items` are used as a fallback."
  [G theta softening]
  {:id     :gravity
   :writes #{c/accel-gravity}
   :run    (fn [world]
             (let [tree (:genesis/spatial-tree world)]
               (if-let [soa (:genesis/physics-soa world)]
                 {c/accel-gravity (bh/acceleration-for-soa G theta softening tree soa nil)}
                 (let [bodies (:genesis/spatial-items world (world->bodies world))]
                   {c/accel-gravity
                    (into {}
                          (par/par-mapv
                           (fn [body]
                             [(:id body) (bh/acceleration G theta softening tree body)])
                           bodies))}))))})

(defn motion-integration
  "Write-set system: sum all acceleration contributions and advance the body by
   one symplectic step — `v' = v + a·dt`, `x' = x + v'·dt` (symplectic Euler).

   Single-evaluation form: `a` is the acceleration at the snapshot position,
   already computed by the contribution emitters, so the integrator never
   evaluates the force field itself. Sole writer of position and velocity."
  [dt]
  {:id     :motion
   :writes #{c/position c/velocity}
   :run    (fn [world]
             (let [eids (ecs/entities-with world c/position c/velocity
                                           c/mass c/radius c/body-kind)]
               (reduce
                (fn [ws eid]
                  (let [a  (reduce (fn [acc src]
                                     (sp/v+ acc (or (ecs/get-component world eid src)
                                                    zero3)))
                                   zero3 accel-sources)
                        v  (ecs/get-component world eid c/velocity)
                        x  (ecs/get-component world eid c/position)
                        v' (sp/v+ v (sp/v* a dt))
                        x' (sp/v+ x (sp/v* v' dt))]
                    (-> ws
                        (assoc-in [c/velocity eid] v')
                        (assoc-in [c/position eid] x'))))
                {}
                eids)))})
