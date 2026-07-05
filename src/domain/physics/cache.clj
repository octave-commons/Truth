(ns domain.physics.cache
  "Persistent neighbor cache shared by SPH hydro and MHD-lite EM.

   The cache survives across ticks as `:genesis/neighbor-cache` on the world.
   Each tick `rebuild-neighbor-cache` reuses ONLY the expensive spatial-query
   products of the previous tick's entry — the neighbor identity list and the
   nearest-neighbor identity — for any particle that has moved less than
   `displacement-tolerance` of its smoothing length since its last real query;
   all field data, the smoothing length, and the kernel gradients are recomputed
   from the current snapshot every tick, so nothing physically stale ever flows
   out of a reused entry. A full rebuild is forced every
   `:genesis/neighbor-cache-full-rebuild-interval` ticks (default 10) and
   whenever `:genesis/invalidate-neighbor-cache?` is set — this bounds the age
   of the identity lists (a particle that sneaks into another's kernel without
   the central particle moving is picked up at the next full rebuild).

   It is pure world plumbing, not an ECS component, so the single-substrate
   invariant is preserved.

    Each entry stores, for one hydro/EM-active particle:
      :position  - central particle position [x y z]
      :anchor-position - position of the last real spatial query; reuse
                   displacement is measured from it
      :query-r   - radius the neighbor set was queried at (with skin headroom)
      :h         - geometric SPH smoothing length (used by density/structure)
      :nn-id     - identity of the nearest neighbor that set h
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

(def ^:const displacement-tolerance
  "Fraction of smoothing length a particle may move — measured from the
   position of its last SPATIAL QUERY (the entry's `:anchor-position`), not the
   last tick — before its neighbor set must be requeried. The reuse path
   refreshes all field data, the smoothing length, and gradients every tick
   regardless; only the neighbor IDENTITIES are trusted across ticks, so this
   is the classic SPH neighbor-list skin criterion."
  0.1)

(defn max-displacement-squared
  "Return the squared displacement threshold for smoothing length `h` and
   `tolerance`. A cache entry is reused while |x_now − x_anchor|² < threshold."
  [h tolerance]
  (let [d (* tolerance (double h))]
    (* d d)))

(defn cache-entry-valid?
  "True when `prev-entry`'s neighbor and nearest-neighbor identities can be
   reused for `eid` in `world`.

   An entry is valid when the entity is alive, still carries `c/matter-state`,
   is hydro/EM-active, the entry satisfies the neighbor-cache schema, it
   records the `:query-r` its neighbor set was collected at, and the current
   position is within `displacement-tolerance` of the cached smoothing length
   from the entry's `:anchor-position` — the position of the last actual
   spatial query, so a slowly drifting particle still requeries once its total
   drift exceeds the skin. Kernel growth past the recorded coverage is checked
   in `refresh-cache-entry`, where the fresh smoothing length is known.

   The Malli schema check is skipped when `:genesis/validate-neighbor-cache?`
   is false; on the hot tick path the builder is trusted and validation cost
   (~3 µs × 2 × N) dominates cache rebuild time (docs/specs/perf-60fps-parallel-tick.md)."
  [world prev-entry eid]
  (boolean
   (when prev-entry
     (and (ecs/alive? world eid)
          (some-> (ecs/get-component world eid c/matter-state) cache-active?)
          (or (false? (:genesis/validate-neighbor-cache? world))
              (neighbor-cache-entry? prev-entry))
          (number? (:query-r prev-entry))
          (let [pos    (ecs/get-component world eid c/position)
                anchor (or (:anchor-position prev-entry) (:position prev-entry))
                h      (:h prev-entry)]
            (and (pos? h)
                 (< (sp/len2 (sp/v- pos anchor))
                    (max-displacement-squared h displacement-tolerance))))))))

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

(defn- assemble-cache-entry
  "Assemble a cache entry for `data` from raw spatial-index `items`, computing
   r2 and both kernel gradients per neighbor in the central-particle frame.
   `anchor` is the position at which the neighbor set was last actually
   queried and `query-r` the radius it covered; displacement and kernel growth
   for reuse are measured against them.

   Neighbors are CANONICALLY ORDERED by `:id` so that a refreshed entry and a
   freshly queried one walk consumers' floating-point reductions in the same
   order — spatial-query iteration order is an artifact of grid layout and must
   never leak into physics."
  [data h anchor query-r items]
  (let [pos  (:position data)
        r-c  (double (or (:radius data) 1.0))
        nbrs (mapv #(neighbor-with-gradients pos r-c %)
                   (sort-by :id items))]
    {:position         pos
     :anchor-position  anchor
     :query-r          query-r
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

(defn- build-cache-entry
  "Build one cache entry for `data` using the uniform grid for radius queries
   and the Barnes–Hut tree for the nearest-neighbor distance that sets h. The
   nearest neighbor's IDENTITY is stored as `:nn-id` so the refresh path can
   recompute the distance to it in O(1) instead of re-descending the tree."
  [world data]
  (let [pos     (:position data)
        r-c     (double (or (:radius data) 1.0))
        [d nn-id] (idx/query-nearest world pos (:eid data))
        h       (hydro/smoothing-length-from-dist data d)
        ;; Density/pressure/EM all need neighbors inside max(h, 2r); the skin
        ;; factor buys headroom so sub-tolerance drift and small kernel growth
        ;; stay inside the queried coverage instead of forcing a requery.
        query-r (* (+ 1.0 displacement-tolerance) (max h (* 2.0 r-c)))
        grid    (:genesis/spatial-grid world)
        tree    (:genesis/spatial-tree world)
        raw     (if (and grid (pos? (:cell-size grid)))
                  (idx/grid-within-radius grid pos query-r (constantly true))
                  (idx/within-radius tree pos query-r (constantly true)))]
    (assoc (assemble-cache-entry data h pos query-r raw)
           :nn-id nn-id)))

(defn- item-dist2
  "Squared distance from `pos` to `item`'s position, with the same arithmetic
   as the spatial index's nearest walk so a recomputed distance is bit-equal to
   a fresh query's."
  [pos item]
  (let [[px py pz] pos
        bp (:position item)
        dx (- (double px) (double (nth bp 0)))
        dy (- (double py) (double (nth bp 1)))
        dz (- (double pz) (double (nth bp 2)))]
    (+ (* dx dx) (* dy dy) (* dz dz))))

(defn- refresh-cache-entry
  "Refresh `prev-entry` for the current snapshot WITHOUT any spatial query:
   the neighbor identity list and the nearest-neighbor identity `:nn-id` (the
   expensive query products) are reused. Everything derived from them is
   recomputed at the current positions — the smoothing length from the distance
   to the remembered nearest neighbor (min'd against the cached set in case a
   set member drifted closer), every neighbor's fields from `item-by-id` (this
   tick's `:genesis/spatial-items`), and r2 and kernel gradients. The
   `:anchor-position` and `:query-r` are carried over unchanged so displacement
   keeps accumulating against the last real query.

   Returns nil — the caller falls back to a full rebuild — when the entry has
   no nearest-neighbor identity, the nearest neighbor or any cached neighbor is
   missing from the current items (despawned/merged), or the consumers' filter
   radius max(h, 2·radius) has outgrown the coverage the neighbor set was
   queried at."
  [data prev-entry item-by-id]
  (let [eid     (:eid data)
        pos     (:position data)
        nn-item (some-> (:nn-id prev-entry) item-by-id)
        items   (when nn-item
                  (reduce (fn [acc n]
                            (if-let [item (item-by-id (:id n))]
                              (conj acc item)
                              (reduced nil)))
                          []
                          (:neighbors prev-entry)))]
    (when items
      (let [[nn-d2 nn-id] (reduce (fn [[best-d2 best-id :as best] item]
                                    (if (= (:id item) eid)
                                      best
                                      (let [d2 (item-dist2 pos item)]
                                        (if (< d2 (double best-d2))
                                          [d2 (:id item)]
                                          best))))
                                  [(item-dist2 pos nn-item) (:id nn-item)]
                                  items)
            h       (hydro/smoothing-length-from-dist data (Math/sqrt (double nn-d2)))
            r-c     (double (or (:radius data) 1.0))
            query-r (double (:query-r prev-entry))]
        (when (<= (max h (* 2.0 r-c)) query-r)
          (assoc (assemble-cache-entry data h
                                       (or (:anchor-position prev-entry)
                                           (:position prev-entry))
                                       query-r
                                       items)
                 :nn-id nn-id))))))

(defn rebuild-neighbor-cache
  "Build or refresh a persistent `:genesis/neighbor-cache` onto `world`.

   For each currently hydro/EM-active particle, reuse the previous tick's
   neighbor IDENTITY LIST from `prev-cache` when `cache-entry-valid?` passes
   and this is not a forced full-rebuild tick — skipping the expensive radius
   query — while recomputing the smoothing length and kernel gradients and
   re-reading every field from the current snapshot, so no stale physics ever
   flows out of a reused entry. Particles that moved past the skin tolerance,
   whose kernel outgrew its queried coverage, that lost a neighbor to despawn,
   or that were never cached get a fresh entry from a real radius query.

   A full rebuild is forced when `prev-cache` is nil, the world carries
   `:genesis/invalidate-neighbor-cache?`, or `tick` is a multiple of
   `:genesis/neighbor-cache-full-rebuild-interval` (default 10). Entities that
   are no longer alive or no longer hydro/EM-active are evicted.

   The cache survives the fold and is returned as part of `world` so the next
   tick can reuse it."
  [world prev-cache tick]
  (let [interval      (:genesis/neighbor-cache-full-rebuild-interval world 10)
        full-rebuild? (or (nil? prev-cache)
                          (:genesis/invalidate-neighbor-cache? world)
                          (zero? (mod tick interval)))
        prev          (or prev-cache {})
        item-by-id    (when-not full-rebuild?
                        (into {} (map (juxt :id identity))
                              (:genesis/spatial-items world)))
        eids          (ecs/entities-with world c/matter-state c/position c/radius c/mass)
        entries       (par/par-mapv
                       (fn [eid]
                         (let [prev-entry (prev eid)
                               reusable?  (and (not full-rebuild?)
                                               (cache-entry-valid? world prev-entry eid))
                               data       (entity->cache-data world eid)]
                           (when (cache-active? (:state data))
                             (let [entry (or (when reusable?
                                               (refresh-cache-entry data prev-entry item-by-id))
                                             (build-cache-entry world data))]
                               (when (or (false? (:genesis/validate-neighbor-cache? world))
                                         (neighbor-cache-entry? entry))
                                 [eid entry])))))
                       eids)]
    (assoc world :genesis/neighbor-cache (into {} (keep identity) entries))))

(defn build-neighbor-cache
  "Build a fresh `:genesis/neighbor-cache` onto `world`.

   Convenience wrapper around `rebuild-neighbor-cache` with no previous cache,
   forcing a full rebuild on tick 0. Preserves the original one-shot API used
   by tests and legacy callers."
  [world]
  (rebuild-neighbor-cache world nil 0))

(defn strip-neighbor-cache
  "Remove `:genesis/neighbor-cache` from `world`.

   Mostly useful for tests or callers that need to discard the persistent cache
   before comparing worlds byte-for-byte."
  [world]
  (dissoc world :genesis/neighbor-cache))

(def ^:private pred-accel-sources
  "Mirror of the integrator's :velocity accumulate list
   (domain.integrator/influence-registry) for the drift prediction below.
   Kept literal here to avoid a require cycle; a new accel channel joins BOTH."
  [c/accel-gravity c/accel-pressure c/accel-lorentz c/accel-observer c/accel-warp])

(def ^:private pred-dv-sources
  "Mirror of the integrator's :velocity-delta accumulate list (impulses, raw)."
  [c/dv-wind c/dv-flare])

(defn build-physics-soa
  "Build and assoc a fresh `:genesis/physics-soa` SoA cache onto `world`.

   The cache covers every entity with position, velocity, mass, and radius,
   packing the dominant physics fields into primitive double arrays for the hot
   gravity and motion-integration kernels. It is rebuilt every tick from a
   single `ecs/all-of` projection so the builder itself issues one ECS lookup
   per entity, not one per component.

   Also carries DRIFT-PREDICTED positions (:px-pred/:py-pred/:pz-pred):
   x̂ = x + (v + Σaccel·dt + Σdv)·dt — the integrator's own deterministic
   update re-derived from the same snapshot. Force emitters evaluate at x̂ so
   the kick they emit lands (next tick) on exactly the position it was
   computed for, restoring symplectic force/position alignment WITHOUT any
   post-fold ordering (spec: docs/specs/perf-60fps-parallel-tick.md, Fix 5).
   The COM frame-offset is deliberately absent: it is a uniform Galilean
   shift, invisible to pairwise forces.

   Validation runs by default but is skipped when `:genesis/validate-soa?` is
   explicitly false, avoiding the per-tick Malli cost on release runs. The
   cache is transient world plumbing, not an ECS component."
  [world]
  (let [all  (vec (ecs/all-of world c/position c/velocity c/mass c/radius))
        n    (count all)
        dt   (double (or (:sim/dt world) 0.0))
        accel-maps (mapv #(get-in world [:components %] {}) pred-accel-sources)
        dv-maps    (mapv #(get-in world [:components %] {}) pred-dv-sources)
        eids (object-array n)
        mass (double-array n)
        radius (double-array n)
        px   (double-array n)
        py   (double-array n)
        pz   (double-array n)
        vx   (double-array n)
        vy   (double-array n)
        vz   (double-array n)
        pxp  (double-array n)
        pyp  (double-array n)
        pzp  (double-array n)]
    (let [fill (fn [^long start ^long end]
                 (loop [i start]
                   (when (< i end)
                     (let [[eid comps] (nth all i)
                           [x y z]     (comps c/position)
                           [vx0 vy0 vz0] (comps c/velocity)
                           [ax ay az]  (reduce (fn [acc m]
                                                 (if-let [v (get m eid)]
                                                   (sp/v+ acc v)
                                                   acc))
                                               [0.0 0.0 0.0]
                                               accel-maps)
                           [dvx dvy dvz] (reduce (fn [acc m]
                                                   (if-let [v (get m eid)]
                                                     (sp/v+ acc v)
                                                     acc))
                                                 [0.0 0.0 0.0]
                                                 dv-maps)
                           vpx (+ (double vx0) (* (double ax) dt) (double dvx))
                           vpy (+ (double vy0) (* (double ay) dt) (double dvy))
                           vpz (+ (double vz0) (* (double az) dt) (double dvz))]
                       (aset ^objects eids i eid)
                       (aset ^doubles mass i (double (or (comps c/mass) 0.0)))
                       (aset ^doubles radius i (double (or (comps c/radius) 0.0)))
                       (aset ^doubles px i (double x))
                       (aset ^doubles py i (double y))
                       (aset ^doubles pz i (double z))
                       (aset ^doubles vx i (double vx0))
                       (aset ^doubles vy i (double vy0))
                       (aset ^doubles vz i (double vz0))
                       (aset ^doubles pxp i (+ (double x) (* vpx dt)))
                       (aset ^doubles pyp i (+ (double y) (* vpy dt)))
                       (aset ^doubles pzp i (+ (double z) (* vpz dt))))
                     (recur (inc i)))))]
      ;; Chunked parallel fill: each chunk writes a DISJOINT index range of the
      ;; primitive arrays, and every cell is derived only from the frozen
      ;; snapshot — identical to the serial loop.
      (if (< n 512)
        (fill 0 n)
        (let [chunk 256
              futs  (mapv (fn [start] (future (fill start (min n (+ start chunk)))))
                          (range 0 n chunk))]
          (run! deref futs))))
    (let [soa {:eids   (vec eids)
               :n      n
               :mass   mass
               :radius radius
               :px     px
               :py     py
               :pz     pz
               :vx     vx
               :vy     vy
               :vz     vz
               :px-pred pxp
               :py-pred pyp
               :pz-pred pzp}]
      (when (and (not (false? (:genesis/validate-soa? world)))
                 (not (lf/physics-soa? soa)))
        (throw (ex-info "Physics SoA cache failed validation" {})))
      (assoc world :genesis/physics-soa soa))))

(defn strip-physics-soa
  "Remove the transient `:genesis/physics-soa` from `world`."
  [world]
  (dissoc world :genesis/physics-soa))

(defn predicted-position-fn
  "Return `(fn [eid] position)` reading the drift-predicted position x̂ from
   the SoA when present, else the snapshot position.

   Force emitters MUST evaluate at x̂: their kick is applied by the integrator
   NEXT tick, to the drifted positions. Evaluating a position-dependent force
   at the snapshot position instead makes it one drift stale — harmless for
   slowly-varying fields, catastrophic for restoring forces whose oscillation
   period is a few ticks (observer pull, warp wells): a lagged spring is a
   spring with NEGATIVE damping, and it pumps the system instead of holding it
   (the dispersing-clump bug, spec Fix 5)."
  [world]
  (let [soa (:genesis/physics-soa world)]
    (if-let [^doubles pxs (and soa (:px-pred soa))]
      (let [^doubles pys (:py-pred soa)
            ^doubles pzs (:pz-pred soa)
            idx (into {} (map-indexed (fn [i e] [e i])) (:eids soa))]
        (fn [eid]
          (if-let [i (idx eid)]
            [(aget pxs (int i)) (aget pys (int i)) (aget pzs (int i))]
            (ecs/get-component world eid c/position))))
      (fn [eid] (ecs/get-component world eid c/position)))))
