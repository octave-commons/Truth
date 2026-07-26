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
    :event/phase-transition
    :event/world-commitment})

(def ^:const commitment-line
  "The ONE ambient narrator line spoken at world commitment (The First
   Narrowing, child C — kanban/tasks/narrowing-frame-handoff.md; design
   docs/designs/the-first-narrowing-star-to-planet.md §6 step 5-6). Ambient,
   never addressed: no 'you', no imperative, no announcement of mechanics
   (ux-architecture.md hard rule: felt, never announced)."
  "the sky narrows to a single hearth")

(defn commitment-utterance
  "The ambient utterance for a `:event/world-commitment` at `tick`: one line,
   `:attribution :ambient`, per law.narrative/utterance-schema. Pure."
  [tick]
  {:text        commitment-line
   :attribution :ambient
   :topic       :collapse
   :tick        (long (or tick 0))
   :context     {}})

(defn mood-from-events
  "Pure function: choose a new mood from recent events, observer coherence,
   and the current story arc.

   Rules:
   - A `:stellar-ignition` event (or life-emergence / gate-discovery) -> :wonder.
   - A `:world-commitment` event -> :tenderness (the captured world is held,
     not marvelled at).
   - An arc ending in dispersal / sterility -> :sterility.
   - Coherence < 0.2 with no recent threshold events -> :dread.
   - Otherwise the current mood persists."
  [events current-mood coherence arc ending]
  (let [has-threshold? (some #(threshold-event-kinds (:kind %)) events)
        has-wonder?    (some #(#{:event/stellar-ignition
                                 :event/life-emergence
                                 :event/gate-discovery} (:kind %)) events)
        has-commitment? (some #(= :event/world-commitment (:kind %)) events)]
    (cond
      has-wonder?                         :wonder
      has-commitment?                     :tenderness
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
   narrative-state map.

   On the tick a `:event/world-commitment` lands it also stamps the ONE
   ambient commitment line (`commitment-utterance`) as `:last-line` and sets
   `:last-utterance-tick`. Exactly once per world-line: the commitment event
   itself is emitted at most once (domain.genesis.tick/emit-commitment-event
   is idempotent), and the line is only written when no `:last-line` exists,
   so a replayed event cannot re-speak it. The line is ambient narration
   data; surfacing it (viewport float, Narrator menu 'Last line') is the
   render/menu layer's job — nothing here addresses the player."
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
            committed?  (boolean (some #(= :event/world-commitment (:kind %)) events))
            new-state   (cond-> (assoc old-state :mood new-mood)
                          (and committed? (nil? (:last-line old-state)))
                          (assoc :last-line (commitment-utterance (:tick world))
                                 :last-utterance-tick (:tick world)))]
        (ecs/put-component world eid c/narrative-state new-state))
      world)))
