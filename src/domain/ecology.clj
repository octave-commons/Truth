(ns domain.ecology
  "Toy ecology model: a five-variable phase-driven biosphere for Phase 0 planets.

   Pure functions only. The ecology ticks deterministically from its current
   state and from player abilities that modify it. All values live in [0,1].

   Design source: docs/designs/player-abilities-and-ecology.md §5–§7.
   The ECS wiring (`ecology-system`, `emit-phase-events`) lives at the bottom:
   habitable planets adopt an abiotic ecology, self-seed when warm and wet
   (spontaneous prebiotic chemistry — the world's own agency), and tick toward
   life; the player's Seed/Spark abilities remain the fast path."
  (:require
   [law.ecology :as le]
   [domain.chemistry :as chemistry]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.event :as event]))

;; --- Construction -----------------------------------------------------------

(defn make-ecology
  "Create a fresh ecology state. `phase` defaults to :abiotic; `seeded` to false."
  ([]
   (make-ecology {}))
  ([overrides]
   (merge {:moisture   0.0
           :temp       0.5
           :biomass    0.0
           :complexity 0.0
           :stability  0.5
           :phase      le/phase-abiotic
           :seeded     false
           :record     []}
          overrides)))

;; --- Predicates -------------------------------------------------------------

(defn habitable?
  "Temperature inside the habitable band (0.25, 0.75)."
  [{:keys [temp]}]
  (and (> (double temp) le/habitability-low)
       (< (double temp) le/habitability-high)))

(defn living?
  "Phase is beyond prebiotic — biomass growth and complexity gain apply."
  [{:keys [phase]}]
  (not (#{le/phase-abiotic le/phase-prebiotic} phase)))

(defn abiotic?
  [{:keys [phase]}]
  (= phase le/phase-abiotic))

(defn prebiotic?
  [{:keys [phase]}]
  (= phase le/phase-prebiotic))

(defn prokaryotic?
  [{:keys [phase]}]
  (= phase le/phase-prokaryotic))

(defn eukaryotic?
  [{:keys [phase]}]
  (= phase le/phase-eukaryotic))

(defn multicellular?
  [{:keys [phase]}]
  (= phase le/phase-multicellular))

(defn complex?
  [{:keys [phase]}]
  (= phase le/phase-complex))

;; --- Passive tick -----------------------------------------------------------

(defn- clamp01 [x]
  (max 0.0 (min 1.0 (double x))))

(defn- +c [x delta]
  (clamp01 (+ (double x) (double delta))))

(defn- -c [x delta]
  (clamp01 (- (double x) (double delta))))

(defn tick-moisture
  "Moisture gains when temp < 0.6, loses when temp ≥ 0.6."
  [{:keys [moisture temp]}]
  (clamp01 (if (< (double temp) 0.6)
             (+ moisture le/moisture-gain-rate)
             (- moisture le/moisture-loss-rate))))

(defn tick-biomass
  "Biomass grows only in a living phase under habitable, moist conditions."
  [{:keys [biomass] :as ecology}]
  (clamp01 (if (and (living? ecology) (habitable? ecology) (> (:moisture ecology) 0.1))
             (+ biomass le/biomass-gain-rate)
             biomass)))

(defn tick-complexity
  "Complexity grows under the same conditions as biomass."
  [{:keys [complexity] :as ecology}]
  (clamp01 (if (and (living? ecology) (habitable? ecology) (> (:moisture ecology) 0.1))
             (+ complexity le/complexity-gain-rate)
             complexity)))

(defn tick-stability
  "Stability drains when conditions are hostile, recovers when habitable."
  [{:keys [stability] :as ecology}]
  (clamp01 (if (habitable? ecology)
             (+ stability le/stability-gain-rate)
             (- stability le/stability-loss-rate))))

(defn tick-temperature
  "Temperature mean-reverts toward 0.5 (climate regulation)."
  [{:keys [temp]}]
  (clamp01 (+ temp (* (- 0.5 temp) le/temperature-reversion-rate))))

(defn collapse?
  "True if stability is critically low and there is meaningful biomass."
  [{:keys [stability biomass]}]
  (and (< (double stability) le/collapse-stability-threshold)
       (> (double biomass) le/collapse-biomass-threshold)))

(defn tick-collapse
  "During collapse, biomass drops by 2% per tick."
  [{:keys [biomass] :as ecology}]
  (if (collapse? ecology)
    (clamp01 (- biomass le/collapse-biomass-loss-rate))
    biomass))

(defn passive-tick
  "Apply one passive ecology tick (design doc §5). Returns the updated ecology
   map. Does NOT evaluate phase transitions — call `check-phase-transition`
   separately so the caller can emit events and record the transition."
  [ecology]
  (let [m'  (tick-moisture ecology)
        t'  (tick-temperature ecology)
        s'  (tick-stability ecology)
        e1  (assoc ecology :moisture m' :temp t' :stability s')
        b'  (tick-biomass e1)
        e2  (assoc e1 :biomass b')
        b'' (tick-collapse e2)
        e3  (assoc e2 :biomass b'')
        c'  (tick-complexity e3)]
    (assoc e3 :complexity c')))

;; --- Phase transitions ------------------------------------------------------

(defn- next-phase
  "Compute the next phase from current ecology state, or nil if no transition."
  [{:keys [phase seeded moisture biomass complexity stability temp]}]
  (case phase
    :abiotic
    (when (and seeded (> (double moisture) le/moisture-prebiotic-threshold))
      le/phase-prebiotic)

    :prebiotic
    (when (and (> (double biomass) le/biomass-prokaryotic-threshold)
               (< (double temp) le/temp-prokaryotic-max)) ;; temp captured from ecology
      le/phase-prokaryotic)

    :prokaryotic
    (when (and (> (double biomass) le/biomass-eukaryotic-threshold)
               (> (double complexity) le/complexity-eukaryotic-threshold))
      le/phase-eukaryotic)

    :eukaryotic
    (when (and (> (double complexity) le/complexity-multicellular-threshold)
               (> (double stability) le/stability-multicellular-threshold))
      le/phase-multicellular)

    :multicellular
    (when (and (> (double complexity) le/complexity-complex-threshold)
               (> (double biomass) le/biomass-complex-threshold))
      le/phase-complex)

    :complex
    nil))

(defn check-phase-transition
  "Return the next phase keyword if a transition should occur, else nil."
  [ecology]
  (next-phase ecology))

(defn record-transition
  "Append a transition record for `new-phase` at `tick` with current biomass
   and complexity snapshot."
  [ecology tick new-phase]
  (update ecology :record conj
          {:tick      tick
           :phase     new-phase
           :biomass   (:biomass ecology)
           :complexity (:complexity ecology)}))

(defn advance-phase
  "Move ecology to `new-phase`, recording the transition at `tick`."
  [ecology tick new-phase]
  (-> ecology
      (assoc :phase new-phase)
      (record-transition tick new-phase)))

(defn maybe-advance-phase
  "If a phase transition is warranted, apply it and return [ecology' event],
   else [ecology nil]. `tick` is the current simulation tick; `body-eid` is the
   planetary entity id for the event."
  [ecology tick body-eid]
  (if-let [new-phase (check-phase-transition ecology)]
    (let [ecology' (advance-phase ecology tick new-phase)
          event    (event/->event {:tick     tick
                                   :kind     :event/ecology-phase-transition
                                   :entities #{body-eid}
                                   :payload  {:from (:phase ecology)
                                              :to   new-phase
                                              :biomass    (:biomass ecology')
                                              :complexity (:complexity ecology')}})]
      [ecology' event])
    [ecology nil]))

;; --- Extinction -------------------------------------------------------------

(defn extinction?
  "True if biomass dropped below the extinction floor while in a living phase."
  [{:keys [phase biomass] :as ecology}]
  (and (living? ecology)
       (< (double biomass) le/extinction-biomass-floor)
       (not= phase le/phase-abiotic)))

(defn extinguish
  "Collapse the ecology back to abiotic: biomass/complexity zero, seeded false,
   record preserved."
  [ecology]
  (assoc ecology
         :phase      le/phase-abiotic
         :seeded     false
         :biomass    0.0
         :complexity 0.0))

(defn maybe-extinguish
  "If extinction conditions are met, return [ecology' event], else [ecology nil]."
  [ecology tick body-eid]
  (if (extinction? ecology)
    (let [ecology' (extinguish ecology)
          event    (event/->event {:tick     tick
                                   :kind     :event/ecology-extinction
                                   :entities #{body-eid}
                                   :payload  {:previous-phase (:phase ecology)
                                              :stability      (:stability ecology)
                                              :biomass        (:biomass ecology)}})]
      [ecology' event])
    [ecology nil]))

;; --- Ability effects --------------------------------------------------------

(defn apply-seed
  "Prebiotic seeding. Requires surface mode (enforced by caller) and moisture
   ≥ 20%. Sets seeded=true and adds +4% biomass seed stock. Returns [ecology'
   ok? reason]."
  ([ecology] (apply-seed ecology {}))
  ([ecology {:keys [ignore-surface?]}]
   (cond
     (and (not ignore-surface?) (< (:moisture ecology) 0.20))
     [ecology false :seed-failed-moisture]

     (not= (:phase ecology) le/phase-abiotic)
     [ecology false :seed-failed-not-abiotic]

     :else
     [(-> ecology
          (assoc :seeded true)
          (update :biomass +c 0.04))
      true
      :seed-ok])))

(defn apply-heat
  "Raise local temperature by 8% (clamped)."
  [ecology]
  [(update ecology :temp +c 0.08) true :heat-ok])

(defn apply-cool
  "Lower local temperature by 8% (clamped)."
  [ecology]
  [(update ecology :temp -c 0.08) true :cool-ok])

(defn apply-spark
  "Excite chemistry: +6% biomass, +3% complexity. Requires seeded=true and temp
   in habitable band (30–75%). Returns [ecology' ok? reason]."
  [ecology]
  (cond
    (not (:seeded ecology))
    [ecology false :spark-failed-not-seeded]

    (not (habitable? ecology))
    [ecology false :spark-failed-not-habitable]

    :else
    [(-> ecology
         (update :biomass +c 0.06)
         (update :complexity +c 0.03))
     true
     :spark-ok]))

(defn apply-grow
  "Stimulate replication: +12% biomass, +4% complexity. Requires prokaryotic+."
  [ecology]
  (if (< (le/phase-index (:phase ecology)) (le/phase-index le/phase-prokaryotic))
    [ecology false :grow-locked]
    [(-> ecology
         (update :biomass +c 0.12)
         (update :complexity +c 0.04))
     true
     :grow-ok]))

(defn apply-evolve
  "Selection pressure: +15% complexity, −5% stability. Requires eukaryotic+."
  [ecology]
  (if (< (le/phase-index (:phase ecology)) (le/phase-index le/phase-eukaryotic))
    [ecology false :evolve-locked]
    [(-> ecology
         (update :complexity +c 0.15)
         (update :stability -c 0.05))
     true
     :evolve-ok]))

;; --- Autonomous prebiotic chemistry -------------------------------------------
;; The design's biomass growth applies only to LIVING phases; the player's Seed
;; and Spark abilities push a world across the prebiotic gap. But the world has
;; agency of its own (design pillar: the world's internal agency GROWS): a warm,
;; wet prebiotic world slowly assembles biomass by spontaneous chemistry, at a
;; fraction of the living rate, so life eventually emerges even unwatched — the
;; player's abilities accelerate rather than gate the arc.

(def ^:const prebiotic-drift-rate
  "Biomass accrual per ecology tick from spontaneous prebiotic chemistry —
   1/6 of the living growth rate; crossing the prokaryotic threshold unaided
   takes ~300 ecology ticks of sustained warm-wet conditions."
  (/ le/biomass-gain-rate 6.0))

(defn prebiotic-drift
  "Apply spontaneous prebiotic biomass accrual: only in :prebiotic phase, only
   while habitable and moist."
  [ecology]
  (if (and (prebiotic? ecology)
           (habitable? ecology)
           (> (double (:moisture ecology)) 0.25))
    (update ecology :biomass +c prebiotic-drift-rate)
    ecology))

(def ^:const self-seed-moisture
  "Moisture above which an abiotic habitable world seeds its own prebiotic
   chemistry (shallow warm seas). Drier worlds wait for the player's Seed."
  0.30)

(defn maybe-self-seed
  "Spontaneous seeding: an abiotic, habitable, wet world becomes seeded."
  [ecology]
  (if (and (abiotic? ecology)
           (not (:seeded ecology))
           (habitable? ecology)
           (>= (double (:moisture ecology)) self-seed-moisture))
    (assoc ecology :seeded true)
    ecology))

;; --- ECS convenience --------------------------------------------------------

(defn tick-ecology
  "Run one full ecology update on `ecology` for entity `body-eid` at `tick`:
   passive tick, autonomous chemistry, phase transition, extinction.
   Returns [ecology' events]."
  [ecology tick body-eid]
  (let [ecology' (-> ecology passive-tick maybe-self-seed prebiotic-drift)
        [ecology'' evt1] (maybe-advance-phase ecology' tick body-eid)
        [ecology''' evt2] (maybe-extinguish ecology'' tick body-eid)]
    [ecology''' (vec (keep identity [evt1 evt2]))]))

;; --- ECS wiring: the ecology system + event emission -------------------------

(defn- temp->01
  "Map a surface temperature (K) into the ecology's [0,1] temp scale: 150 K → 0,
   300 K → 0.5, 450 K → 1. The habitable band (0.25, 0.75) then spans 225–375 K,
   bracketing liquid water."
  [t-kelvin]
  (max 0.0 (min 1.0 (/ (- (double (or t-kelvin 150.0)) 150.0) 300.0))))

(defn- moisture-from-composition
  "Initial moisture from a body's volatile inventory."
  [composition]
  (let [w (+ (double (get composition :H2O 0.0))
             (double (get composition :volatiles 0.0))
             (double (get composition :ices 0.0)))]
    (max 0.0 (min 1.0 (+ 0.05 (* 2.5 w))))))

(defn adopt-ecology
  "A fresh abiotic ecology for a planet, initialised from its physical state."
  [world eid]
  (make-ecology
   {:temp     (temp->01 (ecs/get-component world eid c/temperature))
    :moisture (moisture-from-composition
               (or (ecs/get-component world eid c/composition) {}))}))

(defn- planet-habitable?
  "Chemistry-model habitability gate for adopting an ecology, from the body's
   own components (kept independent of domain.habitability to avoid a require
   cycle through domain.genesis)."
  [world eid]
  (> (chemistry/habitability-score
      {:temperature (double (or (ecs/get-component world eid c/temperature) 0.0))
       :pressure    (double (or (ecs/get-component world eid c/pressure) 0.0))
       :composition (or (ecs/get-component world eid c/composition) {})})
     0.2))

(def ^:const ecology-interval-ticks
  "Physics ticks between ecology updates — the biosphere breathes on a slower
   cadence than the integrator (~0.4 s of real time at the fixed 60 Hz rate,
   per player-abilities-and-ecology.md §5)."
  24)

(defn ecology-system
  "Write-set system: SOLE writer of c/ecology.

   Every `ecology-interval-ticks`: habitable :planet bodies without an ecology
   adopt a fresh abiotic one (initialised from their temperature/volatiles);
   bodies with an ecology advance it one tick (passive dynamics, autonomous
   chemistry, phase transitions, extinction). Transition EVENTS are emitted
   separately by `emit-phase-events` in the tick driver — a fan-out system
   only writes its component."
  []
  {:id     :ecology
   :writes #{c/ecology}
   :run
   (fn [world]
     (let [tick (long (or (:tick world) 0))]
       (if-not (zero? (mod tick ecology-interval-ticks))
         {}
         (let [planets (ecs/entities-with world c/matter-state c/mass c/temperature)
               cell
               (into {}
                     (keep
                      (fn [eid]
                        (let [eco (ecs/get-component world eid c/ecology)]
                          (cond
                            eco
                            [eid (first (tick-ecology eco tick eid))]

                            (and (= :planet (ecs/get-component world eid c/matter-state))
                                 (planet-habitable? world eid))
                            [eid (adopt-ecology world eid)]

                            :else nil))))
                     planets)]
           (if (empty? cell) {} {c/ecology cell})))))})

(defn emit-phase-events
  "Diff ecology phases between `prev-world` and `world` and dispatch the
   corresponding ledger events: crossing into a living phase for the FIRST time
   fires :event/life-emergence (the witnessing reward the arc/player layer
   already prices); other advances fire :event/ecology-phase-transition; a
   collapse back to :abiotic fires :event/ecology-extinction. Runs post-physics
   in the tick driver, where the ledger is writable. Pure: world → world'."
  [world prev-world]
  (let [cur  (get-in world      [:components c/ecology] {})
        prev (get-in prev-world [:components c/ecology] {})
        tick (:tick world)]
    (reduce-kv
     (fn [w eid eco]
       (let [p-phase (:phase (get prev eid))
             c-phase (:phase eco)]
         (if (or (nil? p-phase) (= p-phase c-phase))
           w
           (let [was-living?  (not (#{le/phase-abiotic le/phase-prebiotic} p-phase))
                 now-living?  (living? eco)
                 ever-lived?  (some #(not (#{le/phase-abiotic le/phase-prebiotic} (:phase %)))
                                    (butlast (:record eco)))
                 kind (cond
                        (and now-living? (not was-living?) (not ever-lived?))
                        :event/life-emergence

                        (= c-phase le/phase-abiotic)
                        :event/ecology-extinction

                        :else :event/ecology-phase-transition)]
             (event/dispatch w (event/->event {:tick     tick
                                               :kind     kind
                                               :entities #{eid}
                                               :payload  {:from p-phase :to c-phase}}))))))
     world
     cur)))
