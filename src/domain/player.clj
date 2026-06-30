(ns domain.player
  "The player as a quantum oscillation — a coherent spark whose attention is the
   resource. Coherence is sustained against vacuum noise: focusing costs it,
   witnessing threshold events restores it, and a dying region drains it.

   The spark is a singleton ECS entity carrying the :component/observer map.
   Pure helpers operate on that map; `observer-system` drives it from the
   world's event ledger so coherence responds to what actually happened."
  (:require
   [shape.spatial         :as sp]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [domain.ecs.tick       :as tick]
   [domain.ecs.event      :as event]))

(declare get-observer)

;; --- Construction -----------------------------------------------------------

(defn create-observer
  "A fresh observer map at the given position."
  [position]
  {:id              (java.util.UUID/randomUUID)
   :position        position
   :coherence       0.8
   :max-coherence   1.0
   ;; `agency` is the SPENDABLE action currency (influence quanta), distinct from
   ;; coherence (the spark's life/clarity). Witnessing reality crystallize — every
   ;; classifier transition — releases quanta the observer banks; paid actions
   ;; (warp/heat/transmute) spend them. Passive observation (looking, hovering,
   ;; moving) costs nothing and earns nothing. Starts empty: you must witness to act.
   :agency          0.0
   :focus-position  position
   :focus-radius    1e15            ;; nebula-scale focus to start
   :focus-intensity 0.5
   :drift-velocity  (sp/vec3 0 0 0)
   :resonance-events []
   :time-witnessed  0.0
   :narrative-seeds {}
   :last-tick       0})             ;; ledger cursor for the observer system

;; --- Coherence mechanics ----------------------------------------------------

(defn coherence-drain-from-focus
  "Per-frame coherence drain based on focus-intensity. At default intensity
   (0.5), drain roughly equals regen — the bar holds steady. At max (1.0),
   the bar drains in ~7 seconds. At min (0.1), regen dominates."
  [focus-intensity]
  (* 0.003 (double focus-intensity)))

(defn coherence-regen-rate
  "Per-frame passive coherence regeneration. At default intensity (0.5), regen
   roughly equals drain — the bar holds steady. At max focus (1.0), regen is
   zero. At min focus (0.1), regen refills the bar in ~6 seconds."
  [focus-intensity]
  (* 0.003 (- 1.0 (double focus-intensity))))

(defn coherence-gain-from-event
  "Coherence restored by witnessing a threshold event, with diminishing returns
   as coherence approaches its maximum."
  [event-type current-coherence]
  (let [base (case event-type
               :stellar-ignition 0.3
               :planet-formation 0.2
               :collision        0.1
               :phase-transition 0.15
               :life-emergence   0.5
               :gate-discovery   1.0
               0.05)]
    (* base (- 1.0 current-coherence))))

(defn agency-gain-from-event
  "Influence quanta granted for witnessing a threshold event. Rarer, more
   dramatic transitions pay more — a star igniting is worth far more than a
   routine phase tick. These are the player's earned capacity to act."
  [event-type]
  (case event-type
    :stellar-ignition 25.0
    :planet-formation 10.0
    :phase-transition 5.0
    :collision        1.0
    :life-emergence   50.0
    :gate-discovery   100.0
    0.0))

(defn accrue-agency
  "Add the quanta earned from a seq of witnessed event categories to `observer`."
  [observer witnessed-events]
  (update observer :agency
          (fnil + 0.0)
          (reduce + 0.0 (map agency-gain-from-event witnessed-events))))

(defn can-afford? [observer cost]
  (>= (double (or (:agency observer) 0.0)) (double cost)))

(defn spend-agency
  "Deduct `cost` quanta (clamped at zero). Caller should `can-afford?` first."
  [observer cost]
  (update observer :agency #(max 0.0 (- (double (or % 0.0)) (double cost)))))

(defn apply-coherence
  "Pure update of an observer's coherence. Drain and regen are per-frame
   (not sim-time dependent), so the bar moves at a consistent wall-clock rate
   regardless of simulation speed. Focus-intensity is the lever: high focus
   drains fast, low focus lets coherence recover. Witnessing events gives bursts."
  [observer _dt _environmental-complexity witnessed-events]
  (let [fi       (double (:focus-intensity observer 0.5))
        drain    (coherence-drain-from-focus fi)
        regen    (coherence-regen-rate fi)
        gains    (reduce + 0.0 (map #(coherence-gain-from-event % (:coherence observer))
                                    witnessed-events))
        coherence' (-> (:coherence observer)
                       (- drain) (+ regen) (+ gains)
                       (max 0.0) (min (:max-coherence observer)))]
    (-> observer
        (assoc :coherence coherence')
        (update :resonance-events #(into [] (take 100 (concat witnessed-events %)))))))

;; --- Observation / focus ----------------------------------------------------

(defn observation-effect
  "How strongly the observer's attention resolves reality."
  [{:keys [coherence focus-intensity]}]
  (* coherence focus-intensity))

(defn probability-collapse-radius
  "Radius within which the observer's attention collapses probability into
   resolved matter."
  [{:keys [coherence focus-radius]}]
  (* focus-radius coherence))

(defn set-focus
  [observer position radius intensity]
  (assoc observer
         :focus-position position
         :focus-radius radius
         :focus-intensity (max 0.1 (min 1.0 intensity))))

(defn narrow-focus
  [{:keys [focus-radius focus-intensity] :as o} factor]
  (set-focus o (:focus-position o) (/ focus-radius factor)
             (min 1.0 (* focus-intensity factor))))

(defn widen-focus
  [{:keys [focus-radius focus-intensity] :as o} factor]
  (set-focus o (:focus-position o) (* focus-radius factor)
             (max 0.1 (/ focus-intensity factor))))

;; --- Movement ---------------------------------------------------------------

(defn drift
  [observer velocity dt]
  (-> observer
      (assoc :drift-velocity velocity)
      (update :position #(sp/v+ % (sp/v* velocity dt)))))

(defn- normalize-vec [v]
  (let [l (sp/len v)] (if (> l 0) (sp/v* v (/ 1.0 l)) v)))

(defn approach-focus
  [{:keys [position focus-position] :as o} speed dt]
  (drift o (sp/v* (normalize-vec (sp/v- focus-position position)) speed) dt))

(defn release-focus
  "Let the spark drift along a gradient toward interesting regions, faster when
   coherence is low."
  [observer gradient-field]
  (let [g (gradient-field (:position observer))
        speed (* 1e12 (- 1.0 (:coherence observer)))]
    (assoc observer :drift-velocity (sp/v* g speed))))

;; --- Influence --------------------------------------------------------------

(defn influence-strength
  "How strongly the spark can bias local physics — it nudges, never commands."
  [{:keys [coherence focus-intensity]}]
  (* coherence focus-intensity 0.1))

(defn influence-vector
  [observer target-position desired-direction]
  (let [in-focus? (< (sp/dist (:focus-position observer) target-position)
                     (:focus-radius observer))]
    (sp/v* desired-direction (if in-focus? (influence-strength observer) 0.0))))

(def ^:private default-influence-speed
  "Reference speed (m/s) for the observer's pull-toward-focus nudge. The per-tick
   Δv it imparts is influence-strength × this, BOUNDED regardless of dt — the same
   dt-robust cap the wind/flare/flux systems use, because a raw acceleration
   integrated over a Myr-scale step would blow up (Δv ≫ c). Tuned to be
   perceptible: bodies inside the collapse radius visibly drift toward your focus.
   Set :phase0/observer-influence-speed 0.0 to disable."
  1.0e5)

(defn observer-acceleration
  "Acceleration to write on a body so the motion integrator applies a BOUNDED
   per-tick velocity nudge toward the focus — 'reality condenses where you look.'
   accel = pull·(ref-speed/dt) ⇒ v += pull·ref-speed, independent of dt. Returns
   nil outside the probability-collapse radius. Composes the wired verbs
   `influence-vector` (focus-gated strength) and `probability-collapse-radius`."
  [observer body-pos dt ref-speed]
  (let [d    (sp/v- (:focus-position observer) body-pos)
        dist (sp/len d)]
    (when (and (pos? dist) (pos? (double ref-speed))
               (< dist (probability-collapse-radius observer)))
      (let [dir  (sp/v* d (/ 1.0 dist))                   ;; unit pull toward focus
            pull (influence-vector observer body-pos dir)] ;; dir × influence-strength
        (when (pos? (sp/len pull))
          (sp/v* pull (/ (double ref-speed) (max 1.0 (double dt)))))))))

(defn observer-acceleration-system
  "Write-set system (sole writer of :component/accel.observer): the pull-toward-
   focus nudge for every body inside the collapse radius — 'reality condenses
   where you look.' A pure snapshot-reading fan-out emitter (spec §6: the observer
   was the half-done case); the integrator sums accel.observer like any other
   force. Reads the observer focus from the snapshot (set by last tick's
   observer-system / player input — one-tick lag, accepted) and auto-clears the
   contribution from bodies that have drifted out of the zone."
  []
  {:id     :observer-accel
   :writes #{c/accel-observer}
   :run    (fn [world]
             (let [obs (get-observer world)]
               (if-not obs
                 {c/accel-observer {}}
                 (let [dt        (double (or (:sim/dt world) 1.0e12))
                       ref-speed (get world :phase0/observer-influence-speed
                                      default-influence-speed)
                       cell      (into {}
                                       (keep (fn [eid]
                                               (when-let [a (observer-acceleration
                                                              obs (ecs/get-component world eid c/position)
                                                              dt ref-speed)]
                                                 [eid a])))
                                       (ecs/entities-with world c/position c/mass))]
                   (tick/contribution-write-set
                     c/accel-observer cell
                     (keys (get-in world [:components c/accel-observer])))))))})

;; --- Decoherence / endings --------------------------------------------------

(defn decoherence-state
  [{:keys [coherence]}]
  (cond
    (> coherence 0.8)  :highly-coherent
    (> coherence 0.5)  :coherent
    (> coherence 0.2)  :wavering
    (> coherence 0.05) :fading
    :else              :dissolved))

(defn can-interact?
  [{:keys [coherence]}]
  (> coherence 0.05))

(defn time-slip-threshold?
  "Time slips (jumps forward) when coherence is low and there is little
   observable complexity left to hold attention."
  [{:keys [coherence]} system-complexity]
  (and (< coherence 0.3) (< system-complexity 5)))

(defn phase-quest
  "Player-facing objective for each formation phase."
  [phase]
  (case phase
    :phase-0/nebula-collapse "Watch the cloud condense. Witness what ignites."
    :phase-0/protostar       "A core is forming. Wait for ignition."
    :phase-0/ignition        "A star is born. Watch it settle."
    :phase-0/accretion       "Matter gathers. Watch for planets."
    :phase-0/planets-formed  "Worlds exist. The gates may reveal themselves."
    :phase-0/dispersed       "The cloud has scattered. A new nebula will form."
    :initializing            "The cosmos is waking."
    nil))

(defn phase-description
  "Short flavour text for the current phase."
  [phase]
  (case phase
    :phase-0/nebula-collapse "A cold cloud of gas and dust collapses under its own gravity."
    :phase-0/protostar       "A hot core forms at the centre. Not yet a star."
    :phase-0/ignition        "Nuclear fusion ignites. A star is born."
    :phase-0/accretion       "A disk of matter swirls. Bodies collide and grow."
    :phase-0/planets-formed  "Planets orbit the star. The system is stable."
    :phase-0/dispersed       "Gravity has scattered the cloud."
    :initializing            ""
    nil))

(defn observation-note
  "A narrative note reflecting the observer's current state, focus, and phase.
   Priority: low-coherence warnings > high-focus drain notice > resonance > phase flavour."
  [{:keys [coherence focus-intensity resonance-events]} phase]
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
      (= phase :phase-0/nebula-collapse)
        "You drift through a forming cosmos. Watch closely."
      (= phase :phase-0/protostar)
        "Heat builds at the centre. Something is waking."
      (= phase :phase-0/ignition)
        "Light. For the first time, light."
      (= phase :phase-0/accretion)
        "Matter finds matter. The dance of accretion."
      (= phase :phase-0/planets-formed)
        "Worlds turn in silence. The gates may be watching."
      :else
        "You drift through the forming cosmos, a mote of awareness.")))

(defn event-notification
  "Short text for a witnessed event, or nil."
  [event-type]
  (case event-type
    :stellar-ignition "A star ignites! +25 quanta"
    :planet-formation "A planet forms! +10 quanta"
    :collision        "A collision! +1 quanta"
    :phase-transition "The phase shifts. +5 quanta"
    :life-emergence   "Life emerges! +50 quanta"
    :gate-discovery   "A gate is discovered! +100 quanta"
    nil))

;; --- ECS integration --------------------------------------------------------

(defn observer-entity
  "The singleton observer entity id, or nil."
  [world]
  (first (ecs/entities-with world c/observer)))

(defn get-observer
  "The observer map from the world."
  [world]
  (when-let [eid (observer-entity world)]
    (ecs/get-component world eid c/observer)))

(defn put-observer
  [world observer]
  (if-let [eid (observer-entity world)]
    (ecs/put-component world eid c/observer observer)
    world))

(defn update-observer
  "Apply f to the observer map in the world."
  [world f & args]
  (if-let [eid (observer-entity world)]
    (ecs/update-component world eid c/observer #(apply f % args))
    world))

(defn spawn-observer
  "Spawn the singleton observer entity. Returns [world eid]."
  [world position]
  (let [[w eid] (ecs/spawn world)]
    [(ecs/put-component w eid c/observer (create-observer position)) eid]))

(def ^:private event-kind->coherence
  "Map ledger event kinds to the coherence-gain categories."
  {:event/stellar-ignition :stellar-ignition
   :event/planet-formation :planet-formation
   :event/collision        :collision
   :event/phase-transition :phase-transition
   :event/life-emergence   :life-emergence
   :event/gate-discovery   :gate-discovery})

(defn observer-system
  "ECS system: drains/restores the observer's coherence based on the events that
   landed in the ledger since it last looked, and the world's current observable
   complexity (read from :phase0/complexity). Then resolves the observation verbs
   — caching observation-effect / collapse-radius / observation-note on the
   observer for the renderer — and applies the pull-toward-focus influence to
   nearby bodies (`apply-observer-influence`). Also tracks event notifications
   and phase-aware quest/objective text for the player HUD."
  [dt]
  (fn [world]
    (if-let [obs (get-observer world)]
      (let [complexity (get world :phase0/complexity 0)
            this-tick  (:tick world)
            phase      (:phase0/phase world)
            new-events (->> (event/events-since world this-tick)
                            (filter #(= (:tick %) this-tick))
                            (keep #(event-kind->coherence (:kind %))))
            obs1 (-> (apply-coherence obs dt complexity new-events)
                     (accrue-agency new-events)
                     (assoc :last-tick this-tick)
                     (update :time-witnessed + dt))
            ;; notification from the most recent event this tick
            last-event-type (last (vec new-events))
            notification    (when last-event-type
                              {:text (event-notification last-event-type)
                               :tick this-tick})
            ;; resolve + cache the observation verbs (renderer/HUD read these)
            obs' (cond-> (assoc obs1
                                :observation-effect (observation-effect obs1)
                                :collapse-radius    (probability-collapse-radius obs1)
                                :observation-note   (observation-note obs1 phase)
                                :phase-quest        (phase-quest phase)
                                :phase-description  (phase-description phase)
                                :current-phase      phase)
                   notification (assoc :notification notification))]
        ;; Sole writer of :component/observer — coherence/agency/HUD state. The
        ;; pull-toward-focus force is now a separate fan-out emitter
        ;; (`observer-acceleration-system`), so this never touches physical state.
        (put-observer world obs'))
      world)))
