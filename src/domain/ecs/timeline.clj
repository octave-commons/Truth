(ns domain.ecs.timeline
  "Rewindable world implementation.
   Wraps an ECS world + Ledger + snapshot cache.
   Physics reversal via negated dt (symplectic integrator time-reversal).
   Event reversal via :event/reverse-* handlers or explicit undo-fn on event payload."
  (:require
   [domain.ecs.core       :as ecs]
   [domain.ecs.rewindable :refer [Rewindable current-tick restore seek step-backward step-forward]]
   [law.ledger            :as ledger]))

;; ---------------------------------------------------------------------------
;; Snapshot interval — how often we checkpoint
;; ---------------------------------------------------------------------------

(def ^:long snapshot-every 60)

;; ---------------------------------------------------------------------------
;; Timeline record
;; ---------------------------------------------------------------------------

(defrecord Timeline
           [world          ;; current ECS world map
            ledger         ;; law.ledger/Ledger
            systems-fwd    ;; [system-fn] for forward tick
            systems-bwd    ;; [system-fn] for backward tick
            snap-every ;; long
            ])

(defn- take-snapshot
  "Store current world as snapshot in ledger at current tick."
  [^Timeline tl]
  (let [tick (get-in tl [:world :tick])]
    (update tl :ledger ledger/store-snapshot tick (:world tl))))

(defn- maybe-snapshot
  "Snapshot if we've hit the interval."
  [^Timeline tl]
  (if (zero? (mod (get-in tl [:world :tick]) (:snap-every tl)))
    (take-snapshot tl)
    tl))

;; ---------------------------------------------------------------------------
;; Event collection (from the world's event ledger into the hash-chained ledger)
;; ---------------------------------------------------------------------------

(defn- world-events
  "All events currently in the world's inline event ledger."
  [world]
  (get-in world [:ledger :events] []))

(defn- new-world-events
  "Return events appended to `world-after` since `world-before`.
   Uses a cursor difference so it does not depend on event tick tagging."
  [world-before world-after]
  (let [old (count (world-events world-before))
        new-count (count (world-events world-after))]
    (subvec (world-events world-after) old new-count)))

;; ---------------------------------------------------------------------------
;; Event un-application (for rewind)
;; ---------------------------------------------------------------------------

(defn- unapply-events-at-tick
  "Reverse all events that occurred at `tick` in the ledger.
   Uses :undo-fn in event payload if present."
  [world ledger tick]
  (let [entries  (ledger/entries-between ledger tick tick)
        reversed (reverse entries)]
    (reduce (fn [w {:keys [event]}]
              (if-let [undo (get-in event [:payload :undo-fn])]
                (undo w event)
                w))
            world
            reversed)))

;; ---------------------------------------------------------------------------
;; Rewindable implementation
;; ---------------------------------------------------------------------------

(extend-type Timeline
  Rewindable

  (step-forward [tl]
    (let [world     (:world tl)
          world'    (ecs/tick world (:systems-fwd tl))
          new-events (new-world-events world world')
          ledger'   (reduce ledger/append (:ledger tl) new-events)
          tl'       (assoc tl :world world' :ledger ledger')]
      (maybe-snapshot tl')))

  (step-backward [tl]
    (let [tick    (get-in tl [:world :tick])
          world'  (unapply-events-at-tick (:world tl) (:ledger tl) tick)
          world'' (-> (ecs/run-systems world' (:systems-bwd tl))
                      (assoc :tick (dec tick)))]
      (assoc tl :world world'')))

  (snapshot [tl]
    {:tick  (get-in tl [:world :tick])
     :world (:world tl)})

  (restore [tl snap]
    (assoc tl :world (:world snap)))

  (current-tick [tl]
    (get-in tl [:world :tick]))

  (seek [tl target-tick]
    (let [current (get-in tl [:world :tick])]
      (cond
        (= current target-tick) tl

        (> target-tick current)
        (let [[snap-tick snap] (or (ledger/nearest-snapshot (:ledger tl) target-tick)
                                   [0 nil])
              tl' (if snap
                    (restore tl {:tick snap-tick :world snap})
                    tl)]
          (loop [t tl']
            (if (>= (current-tick t) target-tick)
              t
              (recur (step-forward t)))))

        :else
        (let [[snap-tick snap] (ledger/nearest-snapshot (:ledger tl) target-tick)]
          (if snap
            (-> (restore tl {:tick snap-tick :world snap})
                (seek target-tick))
            (loop [t tl]
              (if (<= (current-tick t) target-tick)
                t
                (recur (step-backward t))))))))))

;; ---------------------------------------------------------------------------
;; Constructor
;; ---------------------------------------------------------------------------

(defn ->timeline
  "Create a Timeline from a bootstrapped world and system fns.
   systems-fwd: normal system pipeline
   systems-bwd: same systems but with negated dt (caller's responsibility)"
  [world systems-fwd systems-bwd]
  (-> (->Timeline world
                  (ledger/empty-ledger)
                  systems-fwd
                  systems-bwd
                  snapshot-every)
      take-snapshot))
