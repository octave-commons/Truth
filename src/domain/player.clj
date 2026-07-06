(ns domain.player
  "The player as a quantum oscillation — a coherent spark whose attention is the
   resource. Coherence is sustained against vacuum noise: focusing costs it,
   witnessing threshold events restores it, and a dying region drains it.

   The spark is a singleton ECS entity carrying the :component/observer map.
   Pure helpers operate on that map; `observer-system` drives it from the
   world's event ledger so coherence responds to what actually happened."
  (:require
   [law.stellar           :as law]
   [shape.spatial         :as sp]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [domain.ecs.tick       :as tick]
   [domain.ecs.event      :as event]
   [domain.physics.cache  :as pcache]))

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
   ;; `resonance` is the PROGRESSION currency: the amplitude of the world that is
   ;; in phase with you. It is awarded the FIRST time a given threshold category
   ;; is witnessed in this world-line and spent to unlock/intensify ability slots.
   ;; Distinct from agency (spendable quanta earned every tick).
   :resonance       0.0
   :resonance-thresholds #{}
   :focus-position  position
   ;; The focus radius doubles as the halo's Plummer scale radius, so it starts
   ;; genuinely nebula-scale (25% of the default cloud): a wide, diffuse
   ;; shepherd. Narrowing it concentrates the same mass into a stronger pull.
   :focus-radius    5e15
   :focus-intensity 0.5
   :resolution      0.0             ;; local simulation detail [0,1]
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
               :nebula-collapse    0.10
               :protostar-formation 0.15
               :stellar-ignition   0.3
               :planet-formation   0.2
               :collision          0.1
               :phase-transition   0.15
               :life-emergence     0.5
               :gate-discovery     1.0
               0.05)]
    (* base (- 1.0 current-coherence))))

(defn agency-gain-from-event
  "Influence quanta granted for witnessing a threshold event. Rarer, more
   dramatic transitions pay more — a star igniting is worth far more than a
   routine phase tick. These are the player's earned capacity to act."
  [event-type]
  (case event-type
    :nebula-collapse       3.0
    :planetesimal-formation 2.0
    :gas-giant-formation    4.0
    :brown-dwarf-formation  8.0
    :protostar-formation   12.0
    :stellar-ignition      25.0
    :planet-formation      10.0
    :phase-transition       5.0
    :collision              1.0
    :life-emergence        50.0
    :gate-discovery       100.0
    0.0))

(defn resonance-gain-from-event
  "Resonance awarded the FIRST time a given threshold is crossed in a world-line.
   Unlike agency (which pays every tick), resonance is legacy — it unlocks and
   intensifies ability slots."
  [event-type]
  (case event-type
    :nebula-collapse       1
    :planetesimal-formation 1
    :gas-giant-formation    1
    :brown-dwarf-formation  1
    :protostar-formation    1
    :stellar-ignition       2
    :planet-formation       1
    :phase-transition       1
    :life-emergence         4
    :gate-discovery         8
    0))

(defn accrue-agency
  "Add the quanta earned from a seq of witnessed event categories to `observer`."
  [observer witnessed-events]
  (update observer :agency
          (fnil + 0.0)
          (reduce + 0.0 (map agency-gain-from-event witnessed-events))))

(defn accrue-resonance
  "Add resonance for threshold event categories the observer has not yet resonated
   with in this world-line. Returns updated observer with `:resonance` and
   `:resonance-thresholds` updated."
  [observer witnessed-events]
  (let [seen (:resonance-thresholds observer)
        new-categories (remove seen (distinct witnessed-events))
        gain (reduce + 0 (map resonance-gain-from-event new-categories))]
    (if (pos? gain)
      (-> observer
          (update :resonance (fnil + 0.0) gain)
          (update :resonance-thresholds into new-categories))
      observer)))

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

;; --- Influence: the dark halo -------------------------------------------------
;; The spark influences matter as a LARGE, DIFFUSE body of mass — a dark-matter-
;; like Plummer halo centred on the focus, not a point kick. Zero force at the
;; centre, peak pull at ~0.7× the focus radius, Keplerian fade beyond. A static
;; halo is a conservative field: it can only deepen the local potential well and
;; gather matter. Ejecting matter requires deliberately dragging or narrowing a
;; strong halo — possible, never free — and is the seam where destructive play
;; will later drain coherence (the ledger already records the transitions).

(def default-halo-mass-factor
  "Halo mass at FULL coherence and focus intensity, as a multiple of the seeded
   cloud's mass. Halo mass = factor · coherence · focus-intensity · cloud-mass,
   so at spawn defaults (0.8, 0.5) the spark weighs ~0.8 cloud masses — felt
   everywhere, dominant nowhere. Live knob: :genesis/observer-halo-mass-factor
   (Spark menu panel); 0.0 disables the halo entirely."
  2.0)

(def default-influence-dv-cap
  "Per-tick Δv ceiling for influence fields, as a multiple of the cloud's virial
   speed. A dt-robustness BACKSTOP (a Myr-scale step across a concentrated halo
   must not teleport parcels), not the design lever — at sane knob values the
   halo field stays far below it. Live knob: :genesis/influence-dv-cap."
  1.0)

(def ^:const halo-reach-factor
  "Influence cutoff in scale radii. Beyond 3a the Plummer pull is under 10% of
   peak; cutting there keeps the write-set sparse and auto-clearing."
  3.0)

(defn influence-reference
  "Reference scales every influence field (observer halo, warp wells) shares,
   read off the world with the seeded-cloud defaults: `:ref-mass`, the cloud
   mass that halo mass factors multiply, and `:dv-cap` (m/s), the per-tick Δv
   ceiling — the cloud's virial speed × :genesis/influence-dv-cap."
  [world]
  (let [m   (double (or (:genesis/nebula-mass world) 4.0e30))
        r   (double (or (:genesis/nebula-radius world) 2.0e16))
        cap (double (or (:genesis/influence-dv-cap world) default-influence-dv-cap))]
    {:ref-mass m
     :dv-cap   (* cap (law/virial-speed m r))}))

(defn halo-mass
  "The observer halo's gravitating mass (kg): mass-factor · coherence ·
   focus-intensity · ref-mass. Coherence is the live scaling — the spark's
   gravitational presence grows and fades with its clarity."
  [{:keys [coherence focus-intensity]} mass-factor ref-mass]
  (* (double mass-factor)
     (double (or coherence 0.0))
     (double (or focus-intensity 0.0))
     (double ref-mass)))

(defn observer-acceleration
  "Acceleration the observer's halo exerts on a body at `body-pos`: a Plummer
   pull toward the focus (law.stellar/plummer-acceleration) with scale radius
   :focus-radius and mass from `halo-mass`, capped so |Δv| = |a|·dt never
   exceeds `:dv-cap` — the dt backstop. Nil outside `halo-reach-factor` scale
   radii, at the exact centre, or when the halo mass is zero."
  [obs body-pos dt {:keys [ref-mass mass-factor dv-cap]}]
  (let [scale (double (or (:focus-radius obs) 0.0))
        M     (halo-mass obs mass-factor ref-mass)
        d     (sp/v- (:focus-position obs) body-pos)
        dist  (sp/len d)]
    (when (and (pos? M) (pos? scale) (pos? dist)
               (< dist (* halo-reach-factor scale)))
      (let [g (-> (law/plummer-acceleration M scale dist)
                  (min (/ (double dv-cap) (max 1.0 (double dt)))))]
        (when (pos? g)
          (sp/v* d (/ g dist)))))))

(defn observer-acceleration-system
  "Write-set system (sole writer of :component/accel.observer): the halo pull
   toward the focus for every body within reach — the spark as a large, diffuse
   centre of gravity, 'reality condenses where you look.' A pure snapshot-
   reading fan-out emitter; the integrator sums accel.observer like any other
   force. Reads the observer focus from the snapshot (set by last tick's
   observer-system / player input — one-tick lag, accepted) and auto-clears the
   contribution from bodies that have drifted out of reach. Set
   :genesis/observer-halo-mass-factor to 0.0 to disable."
  []
  {:id     :observer-accel
   :writes #{c/accel-observer}
   :run    (fn [world]
             (let [obs (get-observer world)
                   kf  (double (or (:genesis/observer-halo-mass-factor world)
                                   default-halo-mass-factor))]
               (if (or (nil? obs) (not (pos? kf)))
                 {c/accel-observer {}}
                 (let [dt     (double (or (:sim/dt world) 1.0e12))
                       ref    (assoc (influence-reference world) :mass-factor kf)
                       ;; Evaluate the pull at drift-predicted positions: the
                       ;; kick lands next tick, and a restoring force applied
                       ;; one drift stale pumps the very oscillation it should
                       ;; damp (see pcache/predicted-position-fn).
                       pos-of (pcache/predicted-position-fn world)
                       cell   (into {}
                                    (keep (fn [eid]
                                            (when-let [a (observer-acceleration
                                                          obs (pos-of eid) dt ref)]
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

;; Player-facing arc text (quest / observation note / event notification) lives
;; in `domain.arc`: it is story state, not player mechanics, and `domain.arc`
;; already depends on this namespace, so it cannot live here without a cycle.

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
  {:event/nebula-collapse       :nebula-collapse
   :event/planetesimal-formation :planetesimal-formation
   :event/gas-giant-formation    :gas-giant-formation
   :event/brown-dwarf-formation  :brown-dwarf-formation
   :event/protostar-formation    :protostar-formation
   :event/stellar-ignition       :stellar-ignition
   :event/planet-formation       :planet-formation
   :event/collision              :collision
   :event/phase-transition       :phase-transition
   :event/life-emergence         :life-emergence
   :event/gate-discovery         :gate-discovery})

(defn observer-system
  "ECS system: drains/restores the observer's coherence based on the events that
   landed in the ledger since it last looked, and the world's current observable
   complexity (read from :genesis/complexity), accrues agency from those events,
   and caches the resolved observation verbs (observation-effect / collapse-radius)
   for the renderer. Player-facing arc TEXT (quest / observation note / event
   notification) is produced by `domain.arc/advance-arc`, not here — that keeps
   the observer/coherence loop free of any dependency on the narrative layer."
  [dt]
  (fn [world]
    (if-let [obs (get-observer world)]
      (let [complexity (get world :genesis/complexity 0)
            this-tick  (:tick world)
            new-events (->> (event/events-since world this-tick)
                            (filter #(= (:tick %) this-tick))
                            (keep #(event-kind->coherence (:kind %))))
            obs1 (-> (apply-coherence obs dt complexity new-events)
                     (accrue-agency new-events)
                     (accrue-resonance new-events)
                     (assoc :last-tick this-tick)
                     (update :time-witnessed + dt))
            obs' (assoc obs1
                        :observation-effect (observation-effect obs1)
                        :collapse-radius    (probability-collapse-radius obs1))]
        ;; Sole writer of :component/observer — coherence/agency/observation state.
        ;; The pull-toward-focus force is a separate fan-out emitter
        ;; (`observer-acceleration-system`), so this never touches physical state.
        (put-observer world obs'))
      world)))
