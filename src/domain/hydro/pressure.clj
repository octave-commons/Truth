(ns domain.hydro.pressure
  "SPH pressure-gradient acceleration and hydro systems."
  (:require
   [law.field :as lf]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.parallel :as par]
   [domain.ecs.tick :as tick]
   [domain.profile :as profile]
   [domain.hydro.common :as common]
   [domain.hydro.kernel :as kernel]
   [domain.spatial.index :as idx]
   [shape.spatial :as sp]))

(defn- pressure-gradient-step
  "Compute the contribution of one neighbor to the pressure-gradient acceleration."
  [density pressure r-self [px py pz] gradients idx n]
  (let [np (:position n)
        nx (double (nth np 0))
        ny (double (nth np 1))
        nz (double (nth np 2))
        rx (- px nx)
        ry (- py ny)
        rz (- pz nz)
        h (+ r-self (double (or (:radius n) 1.0)))
        h2 (* h h)
        r2 (+ (* rx rx) (* ry ry) (* rz rz))]
    (if (or (>= r2 h2)
            (not (and (:density n) (:pressure n) (:mass n))))
      [0.0 0.0 0.0]
      (let [[gx gy gz] (if gradients
                         (nth gradients idx)
                         (kernel/kernel-gradient [rx ry rz] r2 h))
            term (kernel/pressure-term density pressure
                                       (double (:density n)) (double (:pressure n)))
            scale (* (double (:mass n)) term -1.0)]
        [(* gx scale) (* gy scale) (* gz scale)]))))

(defn pressure-gradient-acceleration
  "SPH pressure-gradient acceleration for one particle. `neighbors` is a seq of
   maps with :mass :position :density :pressure :radius. The self-particle may
   be included; its contribution is zero because the kernel gradient vanishes at
   r = 0.

   The density pass uses a geometric smoothing length h = sph-h-factor · d_nn
   (see `smoothing-length` and `sph-h-factor`). The pressure-gradient pass uses
   the pair smoothing length h_ij = r_i + r_j, the sum of the two particle radii.

   If `gradients` is supplied it must be the same length as `neighbors` and
   contain the pre-computed ∇_i W(r_ij, h_ij) vectors; otherwise the gradient is
   recomputed for each neighbor."
  ([data neighbors]
   (pressure-gradient-acceleration data neighbors nil))
  ([data neighbors gradients]
   (let [pos (mapv double (:position data))
         density (double (:density data))
         pressure (double (:pressure data))
         r-self (double (or (:radius data) 1.0))]
     (reduce-kv
      (fn [acc idx n]
        (sp/v+ acc (pressure-gradient-step density pressure r-self pos gradients idx n)))
      [0.0 0.0 0.0]
      (vec neighbors)))))

(defn- pressure-gradient-acceleration-from-cache
  "SPH pressure-gradient acceleration computed directly from a neighbor-cache
   entry's neighbor vector. Each neighbor's cached `:r2` is reused, but the kernel
   gradient is recomputed on demand with the pair smoothing length h_ij = r_i + r_j
   and accumulated into scalar doubles to avoid vector allocation per neighbor.

   Identical to filtering the entry and calling `pressure-gradient-acceleration`,
   without allocating the filtered neighbor vector or a separate gradients vector."
  [data neighbors]
  (let [density  (double (:density data))
        pressure (double (:pressure data))
        [px py pz] (:position data)
        px (double px)
        py (double py)
        pz (double pz)
        r-self (double (or (:radius data) 1.0))]
    (loop [i 0
           ax 0.0
           ay 0.0
           az 0.0]
      (if (>= i (count neighbors))
        [ax ay az]
        (let [n (nth neighbors i)]
          (if-not (and (lf/hydro-em-active? (:matter-state n))
                       (:density n)
                       (:pressure n)
                       (:mass n))
            (recur (inc i) ax ay az)
            (let [r-n (double (or (:radius n) 1.0))
                  h   (+ r-self r-n)
                  h2  (* h h)
                  r2  (double (:r2 n))]
              (if (>= r2 h2)
                (recur (inc i) ax ay az)
                (let [np  (:position n)
                      rx  (- px (double (nth np 0)))
                      ry  (- py (double (nth np 1)))
                      rz  (- pz (double (nth np 2)))
                      [gx gy gz] (kernel/kernel-gradient [rx ry rz] r2 h)
                      term  (kernel/pressure-term density pressure
                                                  (double (:density n))
                                                  (double (:pressure n)))
                      scale (* (double (:mass n)) term -1.0)]
                  (recur (inc i)
                         (+ ax (* gx scale))
                         (+ ay (* gy scale))
                         (+ az (* gz scale))))))))))))

(defn- clear-stale-hydro-accel
  "Remove c/hydro-accel from entities that are no longer hydro-active."
  [world active]
  (let [active-eids (set (map :eid active))
        stale (ecs/entities-with world c/hydro-accel)]
    (reduce (fn [w eid]
              (if (active-eids eid)
                w
                (ecs/remove-component w eid c/hydro-accel)))
            world
            stale)))

(defn- compute-hydro-updates
  "Compute [eid c/hydro-accel] updates for every hydro-active entity."
  [world active]
  (par/par-mapv
   (fn [data]
     (let [radius-fn #(* 2.0 (double (or (:radius %) 1.0)))
           [nbrs grads] (common/cache-neighbors-and-gradients
                         {:world world :data data
                          :radius-fn radius-fn
                          :state-pred common/hydro-active?
                          :gradient-key :gradient-pressure})]
       [(:eid data)
        (pressure-gradient-acceleration data nbrs grads)]))
   active))

(defn hydro-system
  "Compute the pressure-gradient acceleration a = −∇p/ρ for every hydro-active
   clump and store it on `c/hydro-accel`. This acceleration is consumed by
   `domain.orbital.system` during the same tick. Any entity that currently
   carries `c/hydro-accel` but is no longer hydro-active has its acceleration
   removed, so stale pressure forces do not leak into resolved bodies."
  [_dt]
  (fn [world]
    (let [eids     (ecs/entities-with world c/matter-state c/position c/density
                                      c/pressure c/mass c/radius)
          all-data (mapv #(common/entity->hydro-data world %) eids)
          active   (filterv #(common/hydro-active? (:state %)) all-data)
          cleared  (clear-stale-hydro-accel world active)
          updates  (compute-hydro-updates world active)]
      (reduce (fn [w [eid a]]
                (if (lf/finite-vec3? a)
                  (ecs/put-component w eid c/hydro-accel a)
                  w))
              cleared
              updates))))

(defn pressure-acceleration
  "Double-buffer write-set system: SPH pressure-gradient acceleration a = −∇p/ρ
   for every hydro-active clump → `accel.pressure`. Reads the shared spatial tree
   from :genesis/spatial-tree (built once per tick by domain.spatial.index),
   filters query results to hydro-active neighbors (`:nebula` and `:protostar`).
   Writes ONLY accel.pressure."
  []
  {:id     :hydro
   :writes #{c/accel-pressure}
   :run    (fn [world]
             (let [eids     (ecs/entities-with world c/matter-state c/position
                                               c/density c/pressure c/mass c/radius)
                   all-data (mapv #(common/entity->hydro-data world %) eids)
                   active   (filterv #(common/hydro-active? (:state %)) all-data)
                   computed (profile/profile-section
                             world :hydro/compute
                             (fn [_world]
                               (par/par-mapv
                                (fn [data]
                                  (let [h (* 2.0 (double (or (:radius data) 1.0)))]
                                    (if-let [entry (ecs/get-component world (:eid data) c/neighbor-cache)]
                                      [(:eid data)
                                       (pressure-gradient-acceleration-from-cache
                                        data (:neighbors entry))]
                                      (let [nbrs (idx/within-radius
                                                  (:genesis/spatial-tree world) (:position data) h
                                                  #(common/hydro-active? (:matter-state %)))]
                                        [(:eid data)
                                         (pressure-gradient-acceleration data nbrs nil)]))))
                                active)))
                   cell     (reduce (fn [m [eid a]]
                                      (if (lf/finite-vec3? a) (assoc m eid a) m))
                                    {} computed)]
               (tick/contribution-write-set
                c/accel-pressure cell
                (keys (get-in world [:components c/accel-pressure])))))})
