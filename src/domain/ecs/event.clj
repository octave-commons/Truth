(ns domain.ecs.event
  "Minimal event dispatch and handler registry.
   All state lives in the world map. No global atoms.
   Events are discrete, meaningful occurrences — not continuous state updates.
   Position changes are NOT events. Collisions, deaths, interactions are.

   Ledger is an append-only vector on the world at :ledger.
   Event handlers are pure (fn [world event] world') registered at :handlers.
   Rewind handlers are registered at :rewind-handlers.")

(defrecord Event
           [id       ;; UUID — unique per event
            tick     ;; long — tick on which this occurred
            kind     ;; keyword — e.g. :event/collision :event/death :event/trade
            entities ;; #{entity-id} — all entities involved
            payload  ;; map — event-specific data
            cause])  ;; nil | event-id — causal chain

(defn new-event-id [] (java.util.UUID/randomUUID))

(defn ->event
  "Construct a raw event map. Required keys: :tick :kind :entities.
   Optional: :payload :cause :id"
  [{:keys [tick kind entities payload cause id] :as m}]
  (when-not (and tick kind (set? entities))
    (throw (ex-info "Event requires :tick, :kind, :entities (set)"
                    {:kind ::invalid-event :data m})))
  (->Event (or id (new-event-id))
           tick
           kind
           entities
           (or payload {})
           cause))

(defn with-ledger
  "Add an empty ledger to a world map."
  [world]
  (assoc world
         :ledger   {:events [] :cursor 0}
         :handlers {}
         :rewind-handlers {}))

(defn with-handlers
  "Alias — no-op if ledger already present; used for test legibility."
  [world]
  (update world :handlers (fnil identity {})))

(defn register-handler
  "Register a pure handler fn for a given event kind.
   Only one handler per kind in this minimal runtime."
  [world kind f]
  (update-in world [:handlers kind] (fnil (constantly f) f)))

(defn install-reaction
  "Install a reaction var using its :event/kind metadata."
  [world reaction-var]
  (let [kind (:event/kind (meta reaction-var))]
    (when-not kind
      (throw (ex-info "Reaction var is missing :event/kind metadata"
                      {:kind ::missing-event-kind
                       :var reaction-var})))
    (register-handler world kind @reaction-var)))

(defn install-rewind
  "Register a pure rewind (compensation) handler for a given event kind."
  [world rewind-var]
  (let [kind (:event/kind (meta rewind-var))]
    (when-not kind
      (throw (ex-info "Rewind var missing :event/kind metadata"
                      {:var rewind-var})))
    (update world :rewind-handlers assoc kind @rewind-var)))

(defn emit
  "Append an event to the world's ledger.
   Does NOT dispatch handlers — use dispatch for that."
  [world event]
  (update-in world [:ledger :events] conj event))

(defn dispatch
  "Append an event to the world ledger, then run the registered handler for
   its kind. Returns final world state."
  [world event]
  (let [world'   (emit world event)
        handler  (get-in world' [:handlers (:kind event)])]
    (if handler
      (handler world' event)
      world')))

(defn dispatch-all
  "Dispatch a seq of events in order."
  [world events]
  (reduce dispatch world events))

(defn events-since
  "All raw events in the world ledger at or after tick t."
  [world t]
  (filter #(>= (:tick %) (long t)) (get-in world [:ledger :events])))

(defn events-of-kind
  "All raw events in the world ledger of a given kind."
  [world kind]
  (filter #(= (:kind %) kind) (get-in world [:ledger :events])))
