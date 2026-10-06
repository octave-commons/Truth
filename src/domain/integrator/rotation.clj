(ns domain.integrator.rotation
  "Single-writer attitude fold over the same frozen ECS snapshot as motion."
  (:require
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.registry :as registry]
   [domain.integrator.base :as base]
   [law.rotation :as law]
   [shape.quaternion :as quaternion]
   [shape.spatial :as sp]))

(defn- limit-speed
  "Apply the always-on angular speed ceiling without changing the spin axis."
  [omega]
  (when-not (law/angular-velocity? omega)
    (throw (ex-info "Torque fold produced non-finite angular velocity" {:angular-velocity omega})))
  (let [scale (reduce max 0.0 (map abs omega))]
    (if (zero? scale)
      omega
      (let [scaled (mapv #(/ % scale) omega)
            norm (sp/len scaled)]
        (if (> scale (/ law/max-angular-speed norm))
          (sp/v* scaled (/ law/max-angular-speed norm))
          omega)))))

(defn- entity-rotation
  "Read one complete rotational state and integrate registered torque sources."
  [world eid sources dt]
  (let [orientation (ecs/get-component world eid c/orientation)
        omega (ecs/get-component world eid c/angular-velocity)]
    (when-not (law/rotation-input? {:orientation orientation :angular-velocity omega :dt dt})
      (throw (ex-info "Incomplete or invalid ECS rotation state"
                      {:entity eid :orientation orientation :angular-velocity omega :dt dt})))
    (let [torque (base/sum-vec-influences world eid sources)
          next-omega (if (zero? dt)
                       omega
                       (limit-speed (sp/v+ omega (sp/v* torque (/ dt law/moment-of-inertia)))))]
      [(quaternion/advance orientation next-omega dt) next-omega])))

(defn rotation-integrator-system
  "Integrate orientation and angular velocity, consuming previous-tick torques.

   dt is the physics pipeline's simulation seconds, including time dilation.
   Only entities carrying either rotation column are visited. A partial pair
   fails loudly; explicit observer load repair handles older saved worlds.
   No damping or torque emitter is introduced by this substrate slice."
  [dt]
  (when-not (law/rotation-input? {:orientation law/identity-orientation
                                  :angular-velocity base/zero3 :dt dt})
    (throw (ex-info "Rotation requires a finite nonnegative simulation dt" {:dt dt})))
  (let [sources (get-in base/influence-registry [:angular-velocity :accumulate])]
    {:id :rotation-integrator
     :reads (into #{c/orientation c/angular-velocity} sources)
     :writes (registry/registry-writes :rotation-integrator)
     :run (fn [world]
            (reduce
             (fn [ws eid]
               (let [[orientation omega] (entity-rotation world eid sources dt)]
                 (-> ws
                     (assoc-in [c/orientation eid] orientation)
                     (assoc-in [c/angular-velocity eid] omega))))
             {c/orientation {} c/angular-velocity {}}
             (into (set (keys (get-in world [:components c/orientation])))
                   (keys (get-in world [:components c/angular-velocity])))))}))
