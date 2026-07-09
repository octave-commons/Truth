(ns domain.integrator.kinematics
  "Kinematics write-sets for the unified integrator."
  (:require
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.parallel :as par]
   [domain.profile :as profile]
   [domain.integrator.base :as base]
   [shape.spatial :as sp]))

(defn- com-blend
  "Mass-weighted centroid of survivor `[v0 x0]` with mass `m0` and absorbed
   packets `pkts`. Returns `[v-blend x-blend total-mass]`."
  [v0 x0 m0 pkts]
  (let [v0m (sp/v* v0 m0)
        x0m (sp/v* x0 m0)
        {:keys [vn xn total-m]}
        (reduce (fn [acc p]
                  (let [m (double (:mass p 0.0))
                        v (or (:velocity p) base/zero3)
                        x (or (:position p) base/zero3)]
                    (-> acc
                        (update :vn sp/v+ (sp/v* v m))
                        (update :xn sp/v+ (sp/v* x m))
                        (update :total-m + m))))
                {:vn v0m :xn x0m :total-m m0}
                pkts)]
    (if (pos? total-m)
      (let [inv (/ 1.0 total-m)]
        [(sp/v* vn inv) (sp/v* xn inv) total-m])
      [v0 x0 m0])))

(defn- compute-forces
  "Σ acceleration influences for every entity in `eids`."
  [world eids]
  (into {} (par/par-mapv
            (fn [eid] [eid (base/sum-vec-influences world eid base/accel-sources)])
            eids)))

(defn- kinematics-cell
  "Compute [eid velocity' position'] for one entity, blending in absorbed packets."
  [world dt foff absorbs eid forces]
  (let [a    (get forces eid base/zero3)
        dv   (base/sum-vec-influences world eid base/dv-sources)
        v    (ecs/get-component world eid c/velocity)
        x    (ecs/get-component world eid c/position)
        m0   (double (or (ecs/get-component world eid c/mass) 0.0))
        v1   (sp/v+ (sp/v+ v (sp/v* a dt)) dv)
        x1   (sp/v- (sp/v+ x (sp/v* v1 dt)) foff)]
    (if-let [pkts (get absorbs eid)]
      (let [[v-blend x-blend _] (com-blend v1 x1 m0 pkts)]
        [eid v-blend x-blend])
      [eid v1 x1])))

(defn- build-kinematics-ws
  "Fold per-entity kinematics cells into the position/velocity write-set."
  [eids world dt foff absorbs forces]
  (reduce (fn [ws [eid v x]]
            (-> ws
                (assoc-in [c/velocity eid] v)
                (assoc-in [c/position eid] x)))
          {}
          (par/par-mapv #(kinematics-cell world dt foff absorbs % forces) eids)))

(defn kinematics-ws
  "Position + velocity. v' = v + (Σ accel.*)·dt + Σ dv.*; x' = x + v'·dt
   (symplectic Euler), then the one-tick-stale COM frame-offset is subtracted from
   position (a pure Galilean shift, §6). Absorb-accrete/merge packets are blended
   for COM preservation — the absorbed mass's momentum shifts the survivor.
   Gradual mass-transfer recoil rides the c/dv-transfer velocity-delta channel.

   When `:lod/throttle-ticks?` is true, only entities whose `c/lod-tick-phase`
   schedule are due this tick are advanced."
  [world dt]
  (let [foff (or (:genesis/frame-offset world) base/zero3)
        eids (base/due-entities
              world
              (ecs/entities-with world c/position c/velocity
                                      c/mass c/radius c/body-kind))
        absorbs (merge (get-in world [:components c/absorb-accrete] {})
                       (get-in world [:components c/absorb-merge] {}))
        profiling? (:genesis/profile-subsystems? world)
        force-fn #(compute-forces world eids)
        leapfrog-fn #(build-kinematics-ws eids world dt foff absorbs %)
        [forces dt-force] (if profiling?
                            (profile/timing force-fn)
                            [(force-fn) nil])
        [ws dt-leap] (if profiling?
                       (profile/timing #(leapfrog-fn forces))
                       [(leapfrog-fn forces) nil])]
    (if profiling?
      (assoc ws :genesis/_profile
             (merge-with + (or (:genesis/_profile ws) {})
                         {:integrator/force-accum (double dt-force)
                          :integrator/leapfrog (double dt-leap)}))
      ws)))

(defn- sum-vec-soa
  "Sum a vector influence from component cells for the SoA path."
  [cells eid]
  (reduce (fn [[ax ay az] cell]
            (if-let [v (get cell eid)]
              [(+ ax (double (nth v 0)))
               (+ ay (double (nth v 1)))
               (+ az (double (nth v 2)))]
              [ax ay az]))
          [0.0 0.0 0.0]
          cells))

(defn- soa-kinematics-cell
  "Compute [eid velocity' position'] for one SoA index, blending absorbed packets."
  [soa dt foff absorbs idx forces dv-cells]
  (let [eid (nth (:eids soa) idx)
        [ax ay az] (get forces eid [0.0 0.0 0.0])
        [dvx dvy dvz] (sum-vec-soa dv-cells eid)
        [fox foy foz] foff
        vx0 (aget ^doubles (:vx soa) idx)
        vy0 (aget ^doubles (:vy soa) idx)
        vz0 (aget ^doubles (:vz soa) idx)
        px0 (aget ^doubles (:px soa) idx)
        py0 (aget ^doubles (:py soa) idx)
        pz0 (aget ^doubles (:pz soa) idx)
        m0 (aget ^doubles (:mass soa) idx)
        vx1 (+ vx0 (* ax dt) dvx)
        vy1 (+ vy0 (* ay dt) dvy)
        vz1 (+ vz0 (* az dt) dvz)
        px1 (- (+ px0 (* vx1 dt)) fox)
        py1 (- (+ py0 (* vy1 dt)) foy)
        pz1 (- (+ pz0 (* vz1 dt)) foz)]
    (if-let [pkts (get absorbs eid)]
      (let [[v-blend x-blend _] (com-blend [vx1 vy1 vz1] [px1 py1 pz1] m0 pkts)]
        [eid v-blend x-blend])
      [eid [vx1 vy1 vz1] [px1 py1 pz1]])))

(defn- build-soa-kinematics-ws
  "Fold per-index SoA kinematics cells into the position/velocity write-set."
  [soa dt foff absorbs forces dv-cells]
  (reduce (fn [ws [eid v x]]
            (-> ws
                (assoc-in [c/velocity eid] v)
                (assoc-in [c/position eid] x)))
          {}
          (par/par-mapv
           #(soa-kinematics-cell soa dt foff absorbs % forces dv-cells)
           (range (:n soa)))))

(defn kinematics-ws-soa
  "SoA-aware position + velocity updater. Reads positions/velocities/masses from
   the `:genesis/physics-soa` primitive arrays, sums acceleration contributions
   directly from their component cell maps, and produces the standard write-set
   for position and velocity. Falls back to the ECS path when the cache is absent.

   When `:lod/throttle-ticks?` is true, only due entities (by `c/lod-tick-phase`)
   are advanced."
  [world dt soa]
  (let [foff (or (:genesis/frame-offset world) base/zero3)
        {:keys [eids n]} soa
        tick (long (or (:tick world) 0))
        due-idxs (if (:lod/throttle-ticks? world)
                   (filterv #(base/due-entity? world tick (nth eids %)) (range n))
                   (range n))
        absorbs (merge (get-in world [:components c/absorb-accrete] {})
                       (get-in world [:components c/absorb-merge] {}))
        dt (double dt)
        accel-cells (mapv #(get-in world [:components %]) base/accel-sources)
        dv-cells (mapv #(get-in world [:components %]) base/dv-sources)
        profiling? (:genesis/profile-subsystems? world)
        force-fn #(into {} (par/par-mapv
                            (fn [idx] [(nth eids idx) (sum-vec-soa accel-cells (nth eids idx))])
                            due-idxs))
        leapfrog-fn #(build-soa-kinematics-ws soa dt foff absorbs % dv-cells)
        [forces dt-force] (if profiling?
                            (profile/timing force-fn)
                            [(force-fn) nil])
        [ws dt-leap] (if profiling?
                       (profile/timing #(leapfrog-fn forces))
                       [(leapfrog-fn forces) nil])]
    (if profiling?
      (assoc ws :genesis/_profile
             (merge-with + (or (:genesis/_profile ws) {})
                         {:integrator/force-accum (double dt-force)
                          :integrator/leapfrog (double dt-leap)}))
      ws)))
