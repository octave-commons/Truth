(ns domain.physics.cache.neighbor
  "Persistent neighbor cache shared by SPH hydro and MHD-lite EM.

   See `domain.physics.cache` for the high-level design note."
  (:require
   [clojure.math :as math]
   [law.field :as lf]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.parallel :as par]
   [domain.ecs.tick :as tick]
   [domain.profile :as profile]
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

;; --- Staleness-budgeted density estimate ------------------------------------
;; The shared pair walk also maintains the SPH density estimate the Structure
;; owner reads for gas parcels. Recomputing the estimate is lazy within the
;; documented budget (law.field/density-stale-*): due on the first build, on
;; any fresh spatial query (the neighbor identities changed), on accumulated
;; displacement past fraction·h from the estimate's anchor, or on age reaching
;; the max-ticks cap. The budget knobs resolve from world keys with law/
;; defaults so benches and tests can force fresh mode (max-ticks 1).

(defn- density-budget
  "Resolve `[displacement-fraction max-ticks]` for the density staleness
   budget: world-key overrides first, `law.field` defaults otherwise."
  [world]
  [(double (or (:genesis/density-stale-displacement-fraction world)
               lf/density-stale-displacement-fraction))
   (long (or (:genesis/density-stale-max-ticks world)
             lf/density-stale-max-ticks))])

(defn- density-refresh-due?
  "True when a gas parcel's density estimate must be recomputed this tick.

    `fresh-query?` marks entries whose neighbor identities were just re-queried
   (a rebuilt estimate is mandatory — the sum's inputs changed identity, not
   just value). Otherwise the estimate goes stale within budget: recomputed
   when the parcel drifted more than `frac`·h_prev from the estimate's anchor
   (h_prev is the smoothing length the stale estimate was computed at), when h
   itself moved more than `frac` relative since the estimate — the kernel
   self-term is ∝ h⁻³, so a few % of h-drift is the dominant estimate error
   for quiet parcels, and it is also the signal that a neighbor is approaching
   (interaction about to matter) — when the parcel's MASS moved more than
   `frac` relative (the self-term is ∝ m; mass-transfer/ablation changes the
   estimate    without moving the parcel), or when the estimate is `max-ticks` old. The
   displacement threshold scales with `:density-h` — the kernel support the
   stale estimate was actually summed over — not the identity skin's h: once
   the parcel has moved a fraction of THAT support, the geometry the estimate
   describes has honestly shifted."
  [prev-entry pos h mass tick fresh-query? frac max-ticks]
  (boolean
   (or fresh-query?
       (nil? (:density-estimate prev-entry))
       (nil? (:density-h prev-entry))
       (nil? (:density-m prev-entry))
       (>= (- (long tick) (long (:density-tick prev-entry 0))) max-ticks)
       (let [h-prev (double (:density-h prev-entry))]
         (>= (Math/abs (- (double h) h-prev)) (* frac h-prev)))
       (let [m-prev (double (:density-m prev-entry))]
         (and (pos? m-prev)
              (>= (Math/abs (- (double mass) m-prev)) (* frac m-prev))))
       (let [h-est (double (:density-h prev-entry))
             d     (* frac h-est)]
         (>= (sp/len2 (sp/v- pos (:density-anchor prev-entry)))
             (* d d))))))

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
   (~3 µs × 2 × N) dominates cache rebuild time (kanban/tasks/perf-60fps-parallel-tick.md)."
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
  "Project an ECS entity into the map the cache builder needs.

   Only the fields actually required by the cache entry and reuse checks are
   fetched from the pre-built `item-by-id` map over `:genesis/spatial-items`,
   avoiding all per-entity component lookups on the hot path. :mass feeds the
   density estimate's mass-drift staleness trigger."
  [item-by-id eid]
  (let [item (item-by-id eid)]
    {:eid      eid
     :position (:position item)
     :radius   (:radius item)
     :mass     (:mass item)
     :state    (:matter-state item)}))

(defn- attach-r2
  "Attach only the squared distance to a spatial-index item in the central
   particle frame. Used by the cache builder; gradients are computed on demand
   in the consumer systems to avoid storing two gradient vectors per neighbor."
  [pos-c item]
  (let [pos-n (:position item)
        rx    (- (double (nth pos-c 0)) (double (nth pos-n 0)))
        ry    (- (double (nth pos-c 1)) (double (nth pos-n 1)))
        rz    (- (double (nth pos-c 2)) (double (nth pos-n 2)))
        r2    (+ (* rx rx) (* ry ry) (* rz rz))]
    (assoc item :r2 r2)))

(defn- attach-pair-terms
  "Attach the shared pair products to a spatial-index item in the central
   particle frame: `:r2` (squared distance, same arithmetic as `attach-r2`)
   and — for hydro/EM-active neighbors with the fields the merged force
   consumer needs, inside the pair kernel h_ij = r_c + r_n — `:grad`, the
   kernel gradient ∇W_ij computed ONCE here so neither consumer re-walks the
   kernel (receipts 2026-07-09: cache-side gradients beat consumer-side
   recompute by ~11 ms). Pairs outside the kernel or missing fields carry no
   `:grad`; consumers fall back to on-demand computation, which is bit-equal
   for the same inputs."
  [pos-c r-c item]
  (let [pos-n (:position item)
        rx    (- (double (nth pos-c 0)) (double (nth pos-n 0)))
        ry    (- (double (nth pos-c 1)) (double (nth pos-n 1)))
        rz    (- (double (nth pos-c 2)) (double (nth pos-n 2)))
        r2    (+ (* rx rx) (* ry ry) (* rz rz))
        h     (+ (double r-c) (double (or (:radius item) 1.0)))]
    (if (and (< r2 (* h h))
             (lf/hydro-em-active? (:matter-state item))
             (:density item)
             (:pressure item)
             (:mass item))
      (assoc item :r2 r2 :grad (hydro/kernel-gradient [rx ry rz] r2 h))
      (assoc item :r2 r2))))

(defn neighbor-with-gradients
  "Attach pressure and curl gradients to a spatial-index item, both computed in
   the central-particle frame. Public so tests can construct matching gradients
   and legacy callers can build hand-rolled cache entries; the production cache
   builder stores a single shared `:grad` per in-kernel pair instead (see
   `attach-pair-terms`)."
  [pos-c r-c item]
  (let [pos-n (:position item)
        r-n   (double (or (:radius item) 1.0))
        r-c   (double r-c)
        rx    (- (double (nth pos-c 0)) (double (nth pos-n 0)))
        ry    (- (double (nth pos-c 1)) (double (nth pos-n 1)))
        rz    (- (double (nth pos-c 2)) (double (nth pos-n 2)))
        r2    (+ (* rx rx) (* ry ry) (* rz rz))
        h-pressure (+ r-c r-n)
        h-curl     (+ r-c r-n)
        grad-pressure (hydro/kernel-gradient [rx ry rz] r2 h-pressure)
        grad-curl     (hydro/kernel-gradient [rx ry rz] r2 h-curl)]
    (assoc item
           :r2 r2
           :gradient-pressure grad-pressure
           :gradient-curl     grad-curl)))

(defn- assemble-cache-entry
  "Assemble a cache entry for `data` from raw spatial-index `items`, computing
   the shared pair products (r2 and, for in-kernel hydro/EM pairs, the kernel
   gradient) in ONE walk. Consumers read `:grad` instead of re-evaluating the
   kernel; the standard pair smoothing length is h_ij = r_i + r_j.

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
        nbrs (mapv #(attach-pair-terms pos r-c %)
                   (sort-by :id items))]
    {:position         pos
     :anchor-position  anchor
     :query-r          query-r
     :h                (double h)
     :radius           r-c
     :state            (:state data)
     :neighbors        nbrs}))

(defn- build-cache-entry
  "Build one cache entry for `data` using the uniform grid for radius queries
   and the Barnes–Hut tree for the nearest-neighbor distance that sets h. The
   nearest neighbor's IDENTITY is stored as `:nn-id` so the refresh path can
   recompute the distance to it in O(1) instead of re-descending the tree.
   A fresh spatial query always recomputes the gas density estimate (the
   neighbor identities changed); see `build-or-refresh-cache-entry`."
  [world data]
  (let [pos     (:position data)
        r-c     (double (or (:radius data) 1.0))
        [d nn-id] (idx/query-nearest world pos (:eid data))
        h       (hydro/smoothing-length-from-dist data d)
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
   tick's `:genesis/spatial-items`), and squared distances. The
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
      (let [[nn-d2 nn-id] (reduce (fn [[best-d2 _best-id :as best] item]
                                    (if (= (:id item) eid)
                                      best
                                      (let [d2 (item-dist2 pos item)]
                                        (if (< d2 (double best-d2))
                                          [d2 (:id item)]
                                          best))))
                                  [(item-dist2 pos nn-item) (:id nn-item)]
                                  items)
            h       (hydro/smoothing-length-from-dist data (math/sqrt (double nn-d2)))
            r-c     (double (or (:radius data) 1.0))
            query-r (double (:query-r prev-entry))]
        (when (<= (max h (* 2.0 r-c)) query-r)
          (assoc (assemble-cache-entry data h
                                       (or (:anchor-position prev-entry)
                                           (:position prev-entry))
                                       query-r
                                       items)
                 :nn-id nn-id))))))

(defn- cache-full-rebuild?
  "True when the neighbor cache must be fully rebuilt this tick.

   A full rebuild is forced when `:genesis/invalidate-neighbor-cache?` is set or
   when `tick` is a multiple of the configured interval. Otherwise the per-entity
   refresh path reuses previous neighbor identities when they are still valid."
  [world tick]
  (let [interval (long (or (:genesis/neighbor-cache-full-rebuild-interval world) 10))]
    (or (:genesis/invalidate-neighbor-cache? world)
        (zero? (mod (long tick) interval)))))

(defn- item-by-id-map
  "Build an id->item lookup from `:genesis/spatial-items`."
  [world]
  (into {} (map (juxt :id identity)) (:genesis/spatial-items world)))

(defn- carry-density-estimate
  "Carry the previous entry's staleness-budgeted density estimate forward onto
   a refreshed entry whose budget did NOT come due this tick."
  [prev-entry entry]
  (assoc entry
         :density-estimate (:density-estimate prev-entry)
         :density-anchor   (:density-anchor prev-entry)
         :density-tick     (:density-tick prev-entry)
         :density-h        (:density-h prev-entry)
         :density-m        (:density-m prev-entry)))

(defn- with-density-estimate
  "Attach a FRESH density estimate to `entry`, summed from the entry's own
   neighbor vector via `domain.hydro/sph-density-from-cache` — bit-equal to
   the sum the Structure consumer would have computed — stamped with the
   current position, tick, smoothing length, and parcel mass (the staleness
   triggers' reference values)."
  [entry mass tick]
  (assoc entry
         :density-estimate (hydro/sph-density-from-cache (:neighbors entry)
                                                         (:h entry))
         :density-anchor   (:position entry)
         :density-tick     (long tick)
         :density-h        (:h entry)
         :density-m        (double (or mass 0.0))))

(defn- build-or-refresh-cache-entry
  "Return `[eid entry]` for `eid`, reusing the previous entry when valid.
   `prev-fn` returns the previous cache entry for an eid (or nil).

   This is the ONE shared pair/neighbor walk of the tick: the entry it emits
   carries the pair products every consumer reads — r2 and the kernel gradient
   per in-kernel pair for the merged hydro/EM force, plus the staleness-
   budgeted SPH density estimate for the Structure owner. The density budget
   (`density-refresh-due?`, law.field/density-stale-* knobs) is decided here:
   fresh queries always recompute; refreshed entries recompute on displacement,
   h-drift, or age and otherwise carry the previous estimate forward unchanged
   (skipping the density sum entirely on quiet ticks)."
  [world full-rebuild? item-by-id prev-fn tick eid]
  (let [prev-entry (prev-fn eid)
        reusable?  (and (not full-rebuild?) (cache-entry-valid? world prev-entry eid))
        data       (entity->cache-data item-by-id eid)]
    (when (cache-active? (:state data))
      (let [gas?             (= :nebula (:state data))
            [frac max-ticks] (density-budget world)
            refreshed        (when reusable?
                               (refresh-cache-entry data prev-entry item-by-id))
            entry            (or refreshed (build-cache-entry world data))
            entry            (if gas?
                               (if (density-refresh-due? prev-entry
                                                         (:position entry)
                                                         (:h entry)
                                                         (:mass data)
                                                         tick
                                                         (nil? refreshed)
                                                         frac max-ticks)
                                 (with-density-estimate entry (:mass data) tick)
                                 (carry-density-estimate prev-entry entry))
                               entry)]
        (when (or (false? (:genesis/validate-neighbor-cache? world))
                  (neighbor-cache-entry? entry))
          [eid entry])))))

(defn rebuild-neighbor-cache
  "Build or refresh per-entity `c/neighbor-cache` entries — the ONE shared
   pair/neighbor walk of the tick.

   Returns a write-set `{c/neighbor-cache {eid entry}}` suitable for the
   double-buffer fan-out. Reuses previous neighbor identities when valid,
   otherwise rebuilds with a real spatial query. A full rebuild is forced when
   `:genesis/invalidate-neighbor-cache?` is set or `tick` is a multiple of the
   configured interval. Entities no longer alive or hydro/EM-active are evicted
   (absent from the write-set).

   Each entry carries the pair products all consumers read: per-neighbor `:r2`
   and `:grad` (kernel gradient for in-kernel hydro/EM pairs), and for gas
   parcels the staleness-budgeted `:density-estimate` (see
   `density-refresh-due?`). Consumers never re-walk the neighbor set."
  [world tick]
  (if (:genesis/profile-subsystems? world)
    (let [t0 (System/nanoTime)
          full? (cache-full-rebuild? world tick)
          t1 (System/nanoTime)
          item-by-id (item-by-id-map world)
          t2 (System/nanoTime)
          prior (get-in world [:components c/neighbor-cache])
          prev-fn #(ecs/get-component world % c/neighbor-cache)
          eids (ecs/entities-with world c/matter-state c/position c/radius c/mass)
          t3 (System/nanoTime)
          entries (par/par-mapv #(build-or-refresh-cache-entry world full? item-by-id prev-fn tick %) eids)
          t4 (System/nanoTime)
          new-cache (into {} (keep identity) entries)
          t5 (System/nanoTime)
          removed (into {} (comp (remove #(contains? new-cache (key %)))
                                 (map (fn [[eid _]] [eid tick/removed])))
                        prior)
          t6 (System/nanoTime)
          ws {c/neighbor-cache (if (seq removed) (into new-cache removed) new-cache)}]
      (assoc ws :genesis/_profile
             {:neighbor-cache/full-check (- t1 t0)
              :neighbor-cache/item-map (- t2 t1)
              :neighbor-cache/scan-prior (- t3 t2)
              :neighbor-cache/build-entries (- t4 t3)
              :neighbor-cache/build-map (- t5 t4)
              :neighbor-cache/evict (- t6 t5)
              :neighbor-cache/rebuild (- t6 t0)}))
    (let [full? (cache-full-rebuild? world tick)
          item-by-id (item-by-id-map world)
          prev-fn #(ecs/get-component world % c/neighbor-cache)
          eids (ecs/entities-with world c/matter-state c/position c/radius c/mass)
          entries (par/par-mapv #(build-or-refresh-cache-entry world full? item-by-id prev-fn tick %) eids)
          new-cache (into {} (keep identity) entries)
          prior (get-in world [:components c/neighbor-cache])
          removed (into {} (comp (remove #(contains? new-cache (key %)))
                                 (map (fn [[eid _]] [eid tick/removed])))
                        prior)]
      (if (seq removed)
        {c/neighbor-cache (into new-cache removed)}
        {c/neighbor-cache new-cache}))))

(defn build-neighbor-cache
  "Build a fresh neighbor cache onto `world` as `c/neighbor-cache` components.

   Convenience wrapper for tests and legacy callers that applies the write-set
   produced by `rebuild-neighbor-cache` at tick 0 directly to the world."
  [world]
  (let [ws (rebuild-neighbor-cache world 0)]
    (reduce-kv (fn [w eid entry]
                 (ecs/put-component w eid c/neighbor-cache entry))
               world
               (get ws c/neighbor-cache {}))))

(defn strip-neighbor-cache
  "Remove `c/neighbor-cache` from every entity in `world`.

   Mostly useful for tests or callers that need to discard the persistent cache
   before comparing worlds byte-for-byte."
  [world]
  (reduce (fn [w eid]
            (ecs/remove-component w eid c/neighbor-cache))
          world
          (ecs/entities-with world c/neighbor-cache)))

(defn neighbor-cache-system
  "Fan-out system: build or refresh per-entity `c/neighbor-cache` entries —
   the tick's ONE shared pair/neighbor walk.

   Reads the one-tick-stale `c/neighbor-cache` components from the frozen
   snapshot to decide reuse, and emits the current tick's entries as a
   write-set. Hydro-EM, the Structure gas branch, and EM-Lorentz read the same
   stale snapshot entries, so all consumers see the same one-tick Jacobi lag.
   Sole writer of `c/neighbor-cache`; the entry's `:grad` pair terms and
   staleness-budgeted `:density-estimate` ride the same component, so no
   consumer re-walks the neighbor set and no second writer exists."
  []
  {:id     :neighbor-cache
   :ns     'domain.physics.cache.neighbor
   :reads  #{c/matter-state c/position c/mass c/radius
             c/neighbor-cache}
   :writes #{c/neighbor-cache}
   :run    (fn [world]
             (let [[ws dt] (profile/timing #(rebuild-neighbor-cache world (long (or (:tick world) 0))))]
               (if (:genesis/profile-subsystems? world)
                 (assoc ws :genesis/_profile
                        (merge-with + (or (:genesis/_profile ws) {})
                                    {:neighbor-cache/rebuild (double dt)}))
                 ws)))})
