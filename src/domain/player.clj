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
   [domain.ecs.event      :as event]))

;; --- Construction -----------------------------------------------------------

(defn create-observer
  "A fresh observer map at the given position."
  [position]
  {:id              (java.util.UUID/randomUUID)
   :position        position
   :coherence       0.8
   :max-coherence   1.0
   :focus-position  position
   :focus-radius    1e15            ;; nebula-scale focus to start
   :focus-intensity 0.5
   :drift-velocity  (sp/vec3 0 0 0)
   :resonance-events []
   :time-witnessed  0.0
   :narrative-seeds {}
   :last-tick       0})             ;; ledger cursor for the observer system

;; --- Coherence mechanics ----------------------------------------------------

(defn coherence-drain-rate
  "Coherence lost per unit simulated time.  Wide, intense focus in a complex
   region costs more, but the rate is calibrated so that even a cosmological
   tick does not instantly dissolve the observer."
  [{:keys [focus-intensity focus-radius]} environmental-complexity]
  (let [focus-cost      (* focus-intensity (Math/log10 (+ 1 focus-radius)))
        complexity-cost (* 1e-30 environmental-complexity)]
    (+ (* focus-cost 1e-42) complexity-cost)))

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

(defn apply-coherence
  "Pure update of an observer's coherence given drain over dt and a seq of
   witnessed event types."
  [observer dt environmental-complexity witnessed-events]
  (let [drain (* (coherence-drain-rate observer environmental-complexity) dt)
        gains (reduce + 0.0 (map #(coherence-gain-from-event % (:coherence observer))
                                 witnessed-events))
        coherence' (-> (:coherence observer)
                       (- drain) (+ gains)
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
   integrated over a Myr-scale step would blow up (Δv ≫ c). Gentle next to escape
   speeds (~1e5 m/s). Set :phase0/observer-influence-speed 0.0 to disable."
  1.0e3)

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

(defn apply-observer-influence
  "Sole writer of :component/accel.observer. Writes the pull-toward-focus
   acceleration onto bodies inside the collapse radius and clears it from those
   that have left. Runs serially in tick-world (a barrier-stage write), so it is
   exempt from the fan-out single-writer rule; `motion` sums accel.observer."
  [world observer dt ref-speed]
  (let [cleared (reduce (fn [w eid] (ecs/remove-component w eid c/accel-observer))
                        world
                        (ecs/entities-with world c/accel-observer))]
    (reduce
     (fn [w eid]
       (if-let [a (observer-acceleration observer
                                         (ecs/get-component w eid c/position)
                                         dt ref-speed)]
         (ecs/put-component w eid c/accel-observer a)
         w))
     cleared
     (ecs/entities-with world c/position c/mass))))

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

(defn observation-note
  "A narrative note reflecting the observer's current state."
  [{:keys [coherence focus-intensity resonance-events]}]
  (cond
    (< coherence 0.2)             "Your coherence wavers. The universe becomes distant, statistical."
    (> (count resonance-events) 10) "Patterns emerge from the chaos. You've seen this before."
    (> focus-intensity 0.8)       "Your attention narrows. Details crystallize from probability."
    :else                         "You drift through the forming cosmos, a mote of awareness."))

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
   nearby bodies (`apply-observer-influence`)."
  [dt]
  (fn [world]
    (if-let [obs (get-observer world)]
      (let [complexity (get world :phase0/complexity 0)
            this-tick  (:tick world)
            new-events (->> (event/events-since world this-tick)
                            (filter #(= (:tick %) this-tick))
                            (keep #(event-kind->coherence (:kind %))))
            obs1 (-> (apply-coherence obs dt complexity new-events)
                     (assoc :last-tick this-tick)
                     (update :time-witnessed + dt))
            ;; resolve + cache the observation verbs (renderer/HUD read these)
            obs' (assoc obs1
                        :observation-effect (observation-effect obs1)
                        :collapse-radius    (probability-collapse-radius obs1)
                        :observation-note   (observation-note obs1))
            ref-speed (get world :phase0/observer-influence-speed default-influence-speed)]
        (-> world
            (put-observer obs')
            (apply-observer-influence obs' dt ref-speed)))
      world)))
