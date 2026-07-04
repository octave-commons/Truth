(ns domain.ecs.core
  "Entity-Component store.
   Pure data — no atoms, no IO, no side effects.

   Storage layout:
     :components  {component-key {entity-id value}}
     :archetypes  {entity-id     #{component-key ...}}
     :alive       #{entity-id}
     :next-id     long")

;; ---- World -----------------------------------------------------------------

(defn empty-world
  "Return a fresh empty world map."
  []
  {:components {}
   :archetypes {}
   :alive      #{}
   :next-id    0
   :tick       0})

;; ---- Entity lifecycle ------------------------------------------------------

(defn spawn
  "Allocate a new entity. Returns [world' entity-id]."
  [world]
  (let [eid (:next-id world)]
    [(-> world
         (update :next-id inc)
         (update :alive conj eid)
         (assoc-in [:archetypes eid] #{}))
     eid]))

(defn alive?
  "True if entity exists and has not been despawned."
  [world eid]
  (contains? (:alive world) eid))

(defn despawn
  "Remove all components and mark entity dead."
  [world eid]
  (let [component-keys (get-in world [:archetypes eid] #{})]
    (-> (reduce (fn [w k]
                  (update-in w [:components k] dissoc eid))
                world
                component-keys)
        (update :archetypes dissoc eid)
        (update :alive disj eid))))

;; ---- Components ------------------------------------------------------------

(defn put-component
  "Associate `value` with `component-key` for entity `eid`."
  [world eid component-key value]
  (-> world
      (assoc-in [:components component-key eid] value)
      (update-in [:archetypes eid] (fnil conj #{}) component-key)))

(defn get-component
  "Retrieve the value of `component-key` for entity `eid`. Returns nil if absent."
  [world eid component-key]
  (get-in world [:components component-key eid]))

(defn get-components
  "Return a map of all component keys and values for entity `eid`."
  [world eid]
  (let [ks (get-in world [:archetypes eid] #{})]
    (into {}
          (map (fn [k] [k (get-component world eid k)]))
          ks)))

(defn remove-component
  "Dissoc `component-key` from entity `eid`."
  [world eid component-key]
  (-> world
      (update-in [:components component-key] dissoc eid)
      (update-in [:archetypes eid] disj component-key)))

(defn has-component?
  "True if entity eid has component ctype."
  [world eid ctype]
  (contains? (get-in world [:components ctype] {}) eid))

;; ---- Archetype query -------------------------------------------------------

(defn archetype
  "Return the set of component keys currently on entity `eid`."
  [world eid]
  (get-in world [:archetypes eid] #{}))

(defn- entities-with*
  "Uncached scan: pivot on the smallest component population, then check the
   other component maps directly (no per-candidate archetype materialization)."
  [world ks]
  (let [cmps  (:components world)
        maps  (mapv (fn [k] (get cmps k {})) ks)
        pivot (apply min-key count maps)
        rest-maps (filterv #(not (identical? pivot %)) maps)]
    (persistent!
     (reduce-kv (fn [acc eid _]
                  (if (every? (fn [m] (contains? m eid)) rest-maps)
                    (conj! acc eid)
                    acc))
                (transient [])
                pivot))))

(defn entities-with
  "Return a vector of entity ids that have ALL of the requested component keys.

   When the world carries a `:ecs/_query-cache` (attached to the frozen
   per-tick snapshot by `step-physics`), results are memoized per ctype-set:
   the fan-out systems all query the SAME immutable snapshot, so one scan
   serves every system. The cache remembers the snapshot's `:components`
   identity and is bypassed the moment a world's components differ — a system
   that mutates its own working world mid-run falls back to a live scan
   instead of reading stale snapshot results. The compute is pure and the
   snapshot frozen, so a racing computeIfAbsent is benign."
  [world & component-keys]
  (when (seq component-keys)
    (let [ks (vec (distinct component-keys))
          {:keys [^java.util.concurrent.ConcurrentHashMap chm components]}
          (:ecs/_query-cache world)]
      (if (and chm (identical? components (:components world)))
        (.computeIfAbsent chm (set ks)
                          (reify java.util.function.Function
                            (apply [_ _] (entities-with* world ks))))
        (entities-with* world ks)))))

(defn with-query-cache
  "Attach a fresh per-snapshot query cache for `entities-with` memoization.
   Attach ONLY to a frozen snapshot (the fan-out input); the cache is keyed by
   ctype set and never invalidated, so it MUST be stripped before any world
   whose :components differ from the snapshot becomes visible (a stale cache is
   a correctness bug, not a slowdown). Transient plumbing, not EDN."
  [world]
  (assoc world :ecs/_query-cache
         {:chm        (java.util.concurrent.ConcurrentHashMap.)
          :components (:components world)}))

(defn strip-query-cache
  "Remove the transient query cache (see `with-query-cache`)."
  [world]
  (dissoc world :ecs/_query-cache))

(defn all-entities
  "Return all currently alive entity ids."
  [world]
  (:alive world))

(defn all-of
  "Return a seq of [eid {ctype value ...}] for all entities that have
   ALL of the given ctypes. The map contains only the requested ctypes."
  [world & ctypes]
  (when-let [eids (apply entities-with world ctypes)]
    (map (fn [eid]
           [eid (into {} (map (fn [ct] [ct (get-component world eid ct)])) ctypes)])
         eids)))

;; ---- Batch update ----------------------------------------------------------

(defn update-component
  "Apply f to the current value of ctype on eid. f receives current value
   (or nil if absent)."
  [world eid ctype f]
  (update-in world [:components ctype eid] f))

(defn put-components
  "Associate multiple components at once from a map {ctype value}."
  [world eid component-map]
  (reduce-kv (fn [w ct v] (put-component w eid ct v))
             world
             component-map))

;; ---- System runner ---------------------------------------------------------

(defn run-system
  "Run a single system (fn [world] world') over the world."
  [world system-fn]
  (system-fn world))

(defn run-systems
  "Run a seq of systems in order over the world."
  [world system-fns]
  (reduce run-system world system-fns))

(defn advance-tick
  "Increment the world's logical tick counter."
  [world]
  (update world :tick inc))

(defn tick
  "Advance the world by one tick: run all systems, increment :tick."
  [world system-fns]
  (-> (run-systems world system-fns)
      (advance-tick)))
