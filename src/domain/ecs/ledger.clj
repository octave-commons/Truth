(ns domain.ecs.ledger
  "Append-only event ledger with named projection and aggregate support.
   All operations are pure — no atoms, no side effects.")

;; ---- Data shapes -----------------------------------------------------------

(defrecord Ledger
  [events    ;; vector of event maps, append-only
   cursor])  ;; integer: index of next unread position (monotone)

(defn empty-ledger
  []
  (->Ledger [] 0))

(defrecord Checkpoint
  [cursor    ;; ledger index at the point of checkpointing
   value])   ;; materialized aggregate value at cursor

(defn append
  "Append a single event to the ledger. Returns a new Ledger."
  [^Ledger ledger event]
  (->Ledger (conj (:events ledger) event)
            (:cursor ledger)))

(defn events-since
  "All events at or after `cursor`."
  [^Ledger ledger cursor]
  (subvec (:events ledger) cursor))

(defn events-of-kind
  "Filter events matching one or more `kinds` (set of keywords)."
  [events kinds]
  (filterv #(contains? kinds (:kind %)) events))

;; ---- Projection runtime ----------------------------------------------------

(defn project
  "Fold a named projection var over every matching event in the ledger.
   Uses :projection/init, :projection/event-kind, and the function body."
  [world projection-var]
  (let [{:keys [projection/event-kind
                projection/init]} (meta projection-var)
        f                         @projection-var
        ledger                    (:ledger world)
        events                    (events-of-kind (:events ledger)
                                                  #{event-kind})]
    (reduce f (init) events)))

(defn project-all
  "Same as project but for aggregates that track multiple event kinds."
  [world aggregate-var]
  (let [{:keys [aggregate/tracked
                aggregate/init]}  (meta aggregate-var)
        f                         @aggregate-var
        ledger                    (:ledger world)
        events                    (events-of-kind (:events ledger)
                                                  (set tracked))]
    (reduce f (init) events)))

;; ---- Checkpoint / resume ---------------------------------------------------

(defn checkpoint
  "Materialize an aggregate up to the current ledger cursor.
   Returns a Checkpoint."
  [world aggregate-var]
  (let [value  (project-all world aggregate-var)
        cursor (count (:events (:ledger world)))]
    (->Checkpoint cursor value)))

(defn resume
  "Fold an aggregate from a Checkpoint forward to the current ledger tail.
   Only events appended *after* the checkpoint cursor are folded."
  [world ^Checkpoint snap aggregate-var]
  (let [{:keys [aggregate/tracked]} (meta aggregate-var)
        f      @aggregate-var
        ledger (:ledger world)
        delta  (-> (events-since ledger (:cursor snap))
                   (events-of-kind (set tracked)))]
    (reduce f (:value snap) delta)))

;; ---- Rewind ----------------------------------------------------------------

(defn rewind
  "Undo the last `n` events (default 1) using registered rewind handlers.
   Throws if any event in the range is not reversible."
  ([world] (rewind world 1))
  ([world n]
   (let [events  (:events (:ledger world))
         total   (count events)
         drop-n  (min n total)
         to-undo (subvec events (- total drop-n))]
     ;; Validate reversibility before mutating anything
     (doseq [e to-undo]
       (when-not (get-in world [:rewind-handlers (:kind e)])
         (throw (ex-info "Event is not reversible — no rewind handler registered"
                         {:kind    ::not-reversible
                          :event-kind (:kind e)
                          :event   e}))))
     ;; Apply rewind handlers in reverse chronological order
     (-> (reduce (fn [w e]
                   (let [handler (get-in w [:rewind-handlers (:kind e)])]
                     (handler w e)))
                 world
                 (rseq (vec to-undo)))
         ;; Trim ledger
         (update-in [:ledger :events] subvec 0 (- total drop-n))))))
