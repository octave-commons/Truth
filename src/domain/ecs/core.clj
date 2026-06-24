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

(defn entities-with
  "Return a seq of entity ids that have ALL of the requested component keys.
   Uses archetype index — O(n entities matching first key)."
  [world & component-keys]
  (when (seq component-keys)
    (let [ks (set component-keys)
          ;; start from the smallest population for efficiency
          pivot (->> ks
                     (map (fn [k] [k (count (get-in world [:components k] {}))]))
                     (sort-by second)
                     ffirst)
          candidates (keys (get-in world [:components pivot] {}))]
      (filterv (fn [eid]
                 (every? #(contains? (archetype world eid) %) ks))
               candidates))))

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
