(ns domain.narrative
  "Narrative presence layer: mood and ambience.
   Reads the event ledger and observer state, writes the observer's
   `:component/narrative-state`. Phase 1 (this slice) is mood only; Phase 2+
   adds embedded phrasing and addressed utterances.
   Pure: every function here is a data transformation."
  (:require
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.event :as event]
   [domain.player :as player]))

(def ^:private threshold-event-kinds
  "Ledger event kinds that can drive a mood change."
  #{:event/stellar-ignition
    :event/protostar-formation
    :event/planet-formation
    :event/life-emergence
    :event/gate-discovery
    :event/collision
    :event/phase-transition})

(defn mood-from-events
  "Pure function: choose a new mood from recent events, observer coherence,
   and the current story arc.

   Rules:
   - A `:stellar-ignition` event (or life-emergence / gate-discovery) -> :wonder.
   - An arc ending in dispersal / sterility -> :sterility.
   - Coherence < 0.2 with no recent threshold events -> :dread.
   - Otherwise the current mood persists."
  [events current-mood coherence arc ending]
  (let [has-threshold? (some #(threshold-event-kinds (:kind %)) events)
        has-wonder?    (some #(#{:event/stellar-ignition
                                 :event/life-emergence
                                 :event/gate-discovery} (:kind %)) events)]
    (cond
      has-wonder?                         :wonder
      (or (= ending :sterile)
          (= ending :dispersal)
          (= arc :arc/genesis-dispersed)) :sterility
      (and (not has-threshold?)
           (< (double (or coherence 1.0)) 0.2)) :dread
      :else                               current-mood)))

(defn- recent-threshold-events
  "Events on the current tick that are considered threshold events."
  [world]
  (let [this-tick (:tick world)]
    (->> (event/events-since world this-tick)
         (filter #(= (:tick %) this-tick))
         (filter #(threshold-event-kinds (:kind %))))))

(defn narrative-system
  "ECS system: SOLE writer of `c/narrative-state` on the observer singleton.
   Returns a world -> world transformer. Runs after `observer-system` so it reads
   the updated coherence. Computes the new mood and writes a fresh
   narrative-state map."
  []
  (fn [world]
    (if-let [eid (player/observer-entity world)]
      (let [obs         (player/get-observer world)
            old-state   (or (ecs/get-component world eid c/narrative-state)
                            {:mood :anticipation
                             :last-utterance-tick nil
                             :topics #{}})
            coherence   (:coherence obs 1.0)
            arc         (:arc/current world)
            ending      (get-in world [:genesis/ending :type])
            events      (recent-threshold-events world)
            new-mood    (mood-from-events events (:mood old-state) coherence arc ending)
            new-state   (assoc old-state :mood new-mood)]
        (ecs/put-component world eid c/narrative-state new-state))
      world)))
