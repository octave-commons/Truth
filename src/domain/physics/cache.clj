(ns domain.physics.cache
  "Transient per-tick neighbor cache shared by SPH hydro and MHD-lite EM.

   The cache is rebuilt every physics tick after `domain.spatial.index` and
   stripped before the tick returns. It is pure world plumbing, not an ECS
   component, so the single-substrate invariant is preserved.

    Each entry stores, for one hydro/EM-active particle:
      :position  - central particle position [x y z]
      :h         - geometric SPH smoothing length (used by density/structure)
      :neighbors - vector of neighbor maps drawn from the spatial index; each map
                   includes :r2, the squared distance from the central particle
      :gradients - pre-computed pressure-gradient kernel gradients ∇_i W(r_ij,h_ij)
      :curl-gradients - pre-computed curl kernel gradients (matches legacy EM h)

   All functions are pure; no I/O."
  (:require
   [law.field :as lf]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.parallel :as par]
   [domain.hydro :as hydro]
   [domain.spatial.index :as idx]
   [shape.spatial :as sp]))

(defn cache-active?
  "Particles that participate in the shared SPH density/pressure or EM Lorentz
   pair loops: diffuse nebular gas and contracting protostars. Kept as a local
   alias of `law.field/hydro-em-active?` for discoverability."
  [state]
  (lf/hydro-em-active? state))

(defn neighbor-cache-entry?
  "Predicate: does `value` satisfy `law.field/neighbor-cache-entry-schema`?"
  [value]
  (lf/neighbor-cache-entry? value))

(defn- entity->cache-data
  "Project an ECS entity into the map the cache builder needs."
  [world eid]
  {:eid         eid
   :position    (ecs/get-component world eid c/position)
   :velocity    (ecs/get-component world eid c/velocity)
   :mass        (ecs/get-component world eid c/mass)
   :radius      (ecs/get-component world eid c/radius)
   :density     (ecs/get-component world eid c/density)
   :pressure    (ecs/get-component world eid c/pressure)
   :temperature (ecs/get-component world eid c/temperature)
   :b-field     (ecs/get-component world eid c/b-field)
   :state       (ecs/get-component world eid c/matter-state)})

(defn neighbor-with-gradients
  "Attach pressure and curl gradients to a spatial-index item, both computed in
   the central-particle frame. Public so tests can construct matching gradients."
  [pos-c r-c item]
  (let [pos-n (:position item)
        r-n   (double (or (:radius item) 1.0))
        r-c   (double r-c)
        rx    (- (double (nth pos-c 0)) (double (nth pos-n 0)))
        ry    (- (double (nth pos-c 1)) (double (nth pos-n 1)))
        rz    (- (double (nth pos-c 2)) (double (nth pos-n 2)))
        r2    (+ (* rx rx) (* ry ry) (* rz rz))
        h-pressure (+ r-c r-n)
        h-curl     (* 0.5 (+ r-n 1.0))
        grad-pressure (hydro/kernel-gradient [rx ry rz] r2 h-pressure)
        grad-curl     (hydro/kernel-gradient [rx ry rz] r2 h-curl)]
    (assoc item
           :r2 r2
           :gradient-pressure grad-pressure
           :gradient-curl     grad-curl)))

(defn- build-cache-entry
  "Build one cache entry for `data` using the uniform grid for radius queries
   and the Barnes–Hut tree for the nearest-neighbor distance that sets h."
  [world data]
  (let [pos     (:position data)
        r-c     (double (or (:radius data) 1.0))
        h       (hydro/smoothing-length data world)
        ;; Density/pressure/EM all need neighbors inside this radius.
        query-r (max h (* 2.0 r-c))
        grid    (:genesis/spatial-grid world)
        tree    (:genesis/spatial-tree world)
        raw     (if (and grid (pos? (:cell-size grid)))
                  (idx/grid-within-radius grid pos query-r (constantly true))
                  (idx/within-radius tree pos query-r (constantly true)))
        nbrs    (mapv #(neighbor-with-gradients pos r-c %) raw)]
    {:position         pos
     :h                h
     :radius           r-c
     :mass             (:mass data)
     :density          (:density data)
     :pressure         (:pressure data)
     :b-field          (:b-field data)
     :velocity         (:velocity data)
     :state            (:state data)
     :neighbors        nbrs
     :gradients        (mapv :gradient-pressure nbrs)
     :curl-gradients   (mapv :gradient-curl nbrs)}))

(defn build-neighbor-cache
  "Build and assoc a fresh `:genesis/neighbor-cache` onto `world`.

   The cache covers every hydro/EM-active particle with its smoothing length,
   neighbor list, and both pressure and curl gradients. Consumers fall back to
   direct spatial queries when the cache is absent."
  [world]
  (let [eids    (ecs/entities-with world c/matter-state c/position c/radius c/mass)
        entries (par/par-mapv
                 (fn [eid]
                   (let [data (entity->cache-data world eid)]
                     (when (cache-active? (:state data))
                       (let [entry (build-cache-entry world data)]
                         (when (neighbor-cache-entry? entry)
                           [(:eid data) entry])))))
                 eids)]
    (assoc world :genesis/neighbor-cache (into {} (keep identity) entries))))

(defn strip-neighbor-cache
  "Remove the transient `:genesis/neighbor-cache` from `world`."
  [world]
  (dissoc world :genesis/neighbor-cache))

(defn build-physics-soa
  "Build and assoc a fresh `:genesis/physics-soa` SoA cache onto `world`.

   The cache covers every entity with position, velocity, mass, and radius,
   packing the dominant physics fields into primitive double arrays for the hot
   gravity and motion-integration kernels. It is rebuilt every tick from a
   single `ecs/all-of` projection so the builder itself issues one ECS lookup
   per entity, not one per component.

   Validation runs by default but is skipped when `:genesis/validate-soa?` is
   explicitly false, avoiding the per-tick Malli cost on release runs. The
   cache is transient world plumbing, not an ECS component."
  [world]
  (let [all  (ecs/all-of world c/position c/velocity c/mass c/radius)
        n    (count all)
        eids (object-array n)
        mass (double-array n)
        radius (double-array n)
        px   (double-array n)
        py   (double-array n)
        pz   (double-array n)
        vx   (double-array n)
        vy   (double-array n)
        vz   (double-array n)]
    (loop [i 0]
      (when (< i n)
        (let [[eid comps] (nth all i)
              [x y z]     (comps c/position)
              [vx0 vy0 vz0] (comps c/velocity)]
          (aset ^objects eids i eid)
          (aset ^doubles mass i (double (or (comps c/mass) 0.0)))
          (aset ^doubles radius i (double (or (comps c/radius) 0.0)))
          (aset ^doubles px i (double x))
          (aset ^doubles py i (double y))
          (aset ^doubles pz i (double z))
          (aset ^doubles vx i (double vx0))
          (aset ^doubles vy i (double vy0))
          (aset ^doubles vz i (double vz0)))
        (recur (inc i))))
    (let [soa {:eids   (vec eids)
               :n      n
               :mass   mass
               :radius radius
               :px     px
               :py     py
               :pz     pz
               :vx     vx
               :vy     vy
               :vz     vz}]
      (when (and (not (false? (:genesis/validate-soa? world)))
                 (not (lf/physics-soa? soa)))
        (throw (ex-info "Physics SoA cache failed validation" {})))
      (assoc world :genesis/physics-soa soa))))

(defn strip-physics-soa
  "Remove the transient `:genesis/physics-soa` from `world`."
  [world]
  (dissoc world :genesis/physics-soa))
