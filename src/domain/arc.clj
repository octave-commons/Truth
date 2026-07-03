(ns domain.arc
  "The narrative arc / quest layer over the physical genesis substrate.

   The simulation (`domain.genesis`) does not know it is 'Phase 0': a nebula can
   collapse at any point in the game. The ARC is the player's *story state* — a
   quest, an observation note, and a progressive narrowing of perspective — that
   interprets physical threshold events into player-facing meaning.

   `advance-arc` reads the post-physics world summary and this tick's threshold
   events, then updates the `:arc/*` world keys and emits an
   `:event/phase-transition` when the arc advances. Pure data transformation;
   rendering and IO live in infra. This namespace depends on `domain.genesis`
   (for the world summary and habitability handoff) and `domain.player` (for the
   observer that colours the observation note); nothing depends back on it, so
   the physics loop stays portable and arc-agnostic."
  (:require
   [domain.genesis      :as genesis]
   [domain.habitability :as habitability]
   [domain.ecology      :as ecology]
   [domain.player       :as player]
   [domain.ecs.components :as c]
   [domain.ecs.event    :as event]))

;; --- Arc detection ----------------------------------------------------------

(defn living-worlds
  "Entity ids of bodies whose ecology has crossed into a living phase — the
   worlds the perspective will narrow toward."
  [world]
  (->> (get-in world [:components c/ecology] {})
       (filter (fn [[_ eco]] (ecology/living? eco)))
       (mapv first)))

(defn detect-arc
  "Detect the current arc from the resolved-matter summary (and, in the
   3-arity, whether any world is alive). Returns an `:arc/*` keyword — the
   player's story state, not a physics gate. Life continues the narrowing:
   the awe of a god collapsing, world by world, toward a single being."
  ([summ sim-time] (detect-arc summ sim-time false))
  ([{:keys [star? planet-count body-count regions]} sim-time life?]
   (let [nebula?    (some #(= :nebula (:matter-state %)) regions)
         protostar? (some #(= :protostar (:matter-state %)) regions)
         planet?    (some #(= :planet (:matter-state %)) regions)
         debris?    (some #(= :debris (:matter-state %)) regions)]
     (cond
       (and life? (pos? planet-count)) :arc/life-emergence
       (and star? (pos? planet-count)) :arc/genesis-planets-formed
       (and star? (>= body-count 3))   :arc/genesis-accretion
       star?                           :arc/genesis-ignition
       protostar?                      :arc/genesis-protostar
       (or planet? debris?)            :arc/genesis-accretion
       (zero? body-count)              :arc/genesis-dispersed
       (and nebula? (< sim-time 1e18)) :arc/genesis-nebula-collapse
       :else                           :arc/genesis-dispersed))))

;; --- Player-facing text -----------------------------------------------------

(defn quest-for
  "Player-facing objective for the given genesis arc."
  [arc]
  (case arc
    :arc/genesis-nebula-collapse "Watch the cloud condense. Witness what ignites."
    :arc/genesis-protostar       "A core is forming. Wait for ignition."
    :arc/genesis-ignition        "A star is born. Watch it settle."
    :arc/genesis-accretion       "Matter gathers. Watch for planets."
    :arc/genesis-planets-formed  "Worlds exist. The gates may reveal themselves."
    :arc/life-emergence          "Something stirs on a world below. Draw close."
    :arc/genesis-dispersed       "The cloud has scattered. A new nebula will form."
    "The cosmos is waking."))

(defn description-for
  "Short flavour text for the given genesis arc."
  [arc]
  (case arc
    :arc/genesis-nebula-collapse "A cold cloud of gas and dust collapses under its own gravity."
    :arc/genesis-protostar       "A hot core forms at the centre. Not yet a star."
    :arc/genesis-ignition        "Nuclear fusion ignites. A star is born."
    :arc/genesis-accretion       "A disk of matter swirls. Bodies collide and grow."
    :arc/genesis-planets-formed  "Planets orbit the star. The system is stable."
    :arc/life-emergence          "On one world, chemistry has learned to remember itself."
    :arc/genesis-dispersed       "Gravity has scattered the cloud."
    ""))

(defn observation-note
  "A narrative note reflecting the observer's current state, focus, and arc.
   Priority: low-coherence warnings > high-focus drain notice > resonance > arc flavour."
  [{:keys [coherence focus-intensity resonance-events]} arc]
  (let [fi (double (or focus-intensity 0.5))]
    (cond
      (< coherence 0.15)
        "You are fading. The universe recedes into statistics. Ease your focus."
      (< coherence 0.3)
        "Your coherence wavers. Relax your focus to recover."
      (and (> fi 0.7) (< coherence 0.6))
        "Intense focus drains coherence. Ease off to recover."
      (> fi 0.8)
        "Your attention burns bright. Coherence drains fast."
      (> (count resonance-events) 10)
        "Patterns emerge from the chaos. You have seen this before."
      (= arc :arc/genesis-nebula-collapse)
        "You drift through a forming cosmos. Watch closely."
      (= arc :arc/genesis-protostar)
        "Heat builds at the centre. Something is waking."
      (= arc :arc/genesis-ignition)
        "Light. For the first time, light."
      (= arc :arc/genesis-accretion)
        "Matter finds matter. The dance of accretion."
      (= arc :arc/genesis-planets-formed)
        "Worlds turn in silence. The gates may be watching."
      (= arc :arc/life-emergence)
        "You were the whole sky once. Now one small world holds your gaze."
      :else
        "You drift through the forming cosmos, a mote of awareness.")))

(defn event-notification
  "Short text for a witnessed event category, or nil."
  [event-category]
  (case event-category
    :stellar-ignition "A star ignites! +25 quanta"
    :planet-formation "A planet forms! +10 quanta"
    :collision        "A collision! +1 quanta"
    :phase-transition "The phase shifts. +5 quanta"
    :life-emergence   "Life emerges! +50 quanta"
    :gate-discovery   "A gate is discovered! +100 quanta"
    nil))

(def ^:private event-kind->category
  "Map ledger event kinds to the player-facing notification categories."
  {:event/stellar-ignition :stellar-ignition
   :event/planet-formation :planet-formation
   :event/collision        :collision
   :event/phase-transition :phase-transition
   :event/life-emergence   :life-emergence
   :event/gate-discovery   :gate-discovery})

;; --- Handoff / endings ------------------------------------------------------

(defn ready-to-narrow?
  "True when the arc has reached planet formation and at least one habitable
   candidate world exists — the soft handoff from cosmic witness toward a
   narrower perspective."
  [world]
  (and (#{:arc/genesis-planets-formed :arc/life-emergence} (:arc/current world))
       (seq (habitability/habitable-worlds world))))

(defn genesis-ending
  "If the genesis arc has reached a terminal outcome, describe it; else nil."
  [world]
  (let [arc (:arc/current world)
        obs (player/get-observer world)]
    (cond
      (ready-to-narrow? world)
      {:type    :ready-to-narrow
       :worlds  (habitability/habitable-worlds world)
       :time    (:genesis/sim-time world)
       :message "A world capable of harboring life has formed."}

      (and obs (not (player/can-interact? obs)))
      {:type    :fadeout
       :message "You dissolve back into the quantum foam."}

      (= arc :arc/genesis-dispersed)
      {:type    :dispersal
       :message "The nebula disperses. No stars form here."}

      (and (= arc :arc/genesis-planets-formed)
           (empty? (habitability/habitable-worlds world)))
      {:type    :sterile
       :message "Beautiful, but sterile. Life will not arise here."}

      :else nil)))

;; --- Tick integration -------------------------------------------------------

(defn advance-arc
  "Read the post-physics world and this tick's threshold events, then update the
   `:arc/*` story state: current/previous arc, quest + description text, the
   observer-coloured observation note, and the most recent event notification
   (kept until it ages out). Emits an `:event/phase-transition` when the arc
   advances. Pure: world -> world'. Run AFTER `genesis/tick-world`.

   Reuses the post-physics summary `genesis/tick-world` already cached on
   `:genesis/_prev-summary`, so it adds no extra entity walk."
  [world]
  (let [summ      (or (:genesis/_prev-summary world) (genesis/system-summary world))
        sim-time  (:genesis/sim-time world 0.0)
        prev      (:arc/current world)
        cur       (detect-arc summ sim-time (boolean (seq (living-worlds world))))
        obs       (player/get-observer world)
        this-tick (:tick world)
        new-cats  (->> (event/events-since world this-tick)
                       (filter #(= (:tick %) this-tick))
                       (keep #(event-kind->category (:kind %))))
        last-cat  (last (vec new-cats))
        notif     (when last-cat {:text (event-notification last-cat) :tick this-tick})
        world1    (cond-> world
                    (and prev (not= cur prev))
                    (genesis/emit-threshold :event/phase-transition {:from prev :to cur}))]
    (assoc world1
           :arc/previous         prev
           :arc/current          cur
           :arc/quest            (quest-for cur)
           :arc/description      (description-for cur)
           :arc/observation-note (when obs (observation-note obs cur))
           :arc/notification     (or notif (:arc/notification world))
           :arc/recent-events    (vec new-cats))))

(defn tick-genesis
  "One combined tick: advance the physical world (`genesis/tick-world`) then the
   narrative arc (`advance-arc`). The single entry point for consumers that want
   both physics and story state; `genesis/tick-world` alone remains available for
   pure-physics callers (tests, headless runs)."
  [world]
  (-> world genesis/tick-world advance-arc))
