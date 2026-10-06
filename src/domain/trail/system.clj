(ns domain.trail.system
  "The sole motion-history writer in the existing frozen-snapshot fan-out."
  (:require
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.registry :as registry]
   [domain.ecs.tick :as tick]
   [domain.integrator.base :as base]
   [domain.trail :as trail]
   [law.trail :as law]))

(defn- significant-body [world eid]
  {:position (ecs/get-component world eid c/position)
   :body-kind (ecs/get-component world eid c/body-kind)
   :matter-state (ecs/get-component world eid c/matter-state)})

(defn trail-system
  "Record significant bodies and clear only this owner's stale histories.

   Filter before bounded history work; diffuse parcels never allocate samples.
   History receives the frame offset actually applied to the body's position;
   the existing opt-in LOD throttle freezes non-due positions without shifting."
  []
  {:id :motion-trail
   :reads #{c/position c/body-kind c/matter-state c/lod-tick-phase c/motion-trail}
   :writes (registry/registry-writes :motion-trail)
   :run (fn [world]
          (let [eligible (filter #(trail/eligible? (significant-body world %))
                                 (ecs/entities-with world c/position))
                observation {:time (:genesis/sim-time world 0.0)
                             :dt (:sim/dt world 0.0)}
                offset (:genesis/frame-offset world base/zero3)
                histories (into {}
                                (map (fn [eid]
                                       [eid (trail/advance-history
                                             (ecs/get-component world eid c/motion-trail)
                                             (assoc observation :position
                                                    (ecs/get-component world eid c/position)
                                                    :frame-offset
                                                    (if (base/due-entity? world (:tick world 0) eid)
                                                      offset base/zero3))
                                             law/default-options)]))
                                eligible)]
            (tick/contribution-write-set c/motion-trail histories
                                         (ecs/entities-with world c/motion-trail))))})
