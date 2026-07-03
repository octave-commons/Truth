(ns domain.intervention
  "Paid player interventions — the spend side of the agency economy.

   An intervention is a PLACED, DECAYING transient the player drops into the world
   by spending agency quanta (earned from witnessing classifier transitions, see
   domain.player). Phase 1 is warp-space: a gravity WELL (pull) or REPULSOR (push)
   that bends nearby bodies for a while, then fades.

   Interventions live as a plain vector on the world at `:genesis/interventions`.
   The warp system reads them and emits the single-writer `:component/accel.warp`
   channel; the motion integrator sums it like any other force. Expiry runs at the
   serial barrier. Nothing here adds or removes mass — warp is pure force (the
   conservation invariant the player economy is built on)."
  (:require
   [shape.spatial         :as sp]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [domain.ecs.tick       :as tick]
   [domain.player         :as player]))

(def ^:const zero3 [0.0 0.0 0.0])

;; --- Defaults / costs -------------------------------------------------------

(def action-cost
  "Agency quanta to place one intervention, by kind. Fixed cost for now —
   affordable after a couple of witnessed transitions (a stellar ignition is 25)."
  {:warp/well 15.0 :warp/repulsor 15.0 :heat/source 15.0 :heat/sink 15.0})

(defn cost-of [kind] (double (get action-cost kind 15.0)))

(def ^:const default-radius   4.0e15)  ;; m — influence reach (~4 render units)
(def ^:const default-ttl      600)     ;; ticks an intervention persists before fading
;; Peak per-tick warp Δv at the core. SIZED TO THE CLOUD: the nebula's own
;; velocities are ~40–115 m/s (virial speed √(GM/R) ≈ 115 m/s for the default
;; cloud), so a warp must nudge at that scale to gather matter — not eject it. The
;; old 2e4 m/s was 173× the virial speed and blew the cloud apart on contact. At
;; ~1.3× virial it pulls gently, and over the (now longer) ttl it still draws a
;; visible inflow. Weaker, for longer.
(def ^:const default-base-speed 1.5e2) ;; m/s

;; Thermal targets the intervention eases matter toward (Kelvin). A source pumps
;; toward hot, a sink drains toward the CMB floor. Easing (not a fixed ΔT) keeps
;; it bounded and self-limiting regardless of how long the source lingers.
(def ^:const heat-target-hot 1.0e5)
(def ^:const heat-target-cold 3.0)
(def ^:const heat-approach   0.03)     ;; fraction of the gap closed per tick at the core
(def ^:const min-temp        3.0)
(def ^:const max-temp        1.0e7)

(defn make-intervention
  "Construct an intervention map for `kind` at world-metre `position`.
   :warp/well :warp/repulsor   — placed force (see `warp-acceleration-system`)
   :heat/source :heat/sink     — placed thermal ease (see `apply-thermal-interventions`)"
  [kind position tick opts]
  (let [base {:kind kind :position (vec position) :radius default-radius
              :strength 1.0 :born-tick tick :ttl default-ttl}]
    (merge
      (case kind
        (:warp/well :warp/repulsor) (assoc base :base-speed default-base-speed)
        :heat/source                (assoc base :target-temp heat-target-hot)
        :heat/sink                  (assoc base :target-temp heat-target-cold)
        base)
      opts)))

;; --- Acceleration -----------------------------------------------------------

(def ^:private warp-kinds #{:warp/well :warp/repulsor})

(defn decay-fraction
  "Remaining strength of a warp in [0,1], easing linearly to zero at its ttl."
  [{:keys [born-tick ttl]} tick]
  (max 0.0 (- 1.0 (/ (double (- (long tick) (long born-tick))) (double ttl)))))

(defn warp-accel-on
  "Bounded per-tick acceleration a single warp imparts on a body at `body-pos`,
   or nil outside its radius. dt-ROBUST like the observer nudge: it targets a
   bounded Δv (base-speed × strength × proximity² × decay) toward the centre
   (well) or away (repulsor), then divides by dt so `v += Δv` regardless of the
   Myr-scale step — a raw GM/r² integrated over such a dt would blow past c."
  [{:keys [kind position radius strength base-speed] :as iv} body-pos tick dt]
  (let [d    (sp/v- position body-pos)        ;; vector toward the warp centre
        dist (sp/len d)]
    (when (and (pos? dist) (< dist (double radius)))
      (let [prox  (let [u (- 1.0 (/ dist (double radius)))] (* u u)) ;; smooth falloff
            sign  (if (= kind :warp/repulsor) -1.0 1.0)
            dv    (* (double base-speed) (double strength) prox
                     (decay-fraction iv tick) sign)
            dir   (sp/v* d (/ 1.0 dist))]       ;; unit toward centre
        (sp/v* dir (/ dv (max 1.0 (double dt))))))))

(defn warp-acceleration-system
  "Write-set system (sole writer of accel.warp): sum every active warp's
   contribution onto each body. Returns a full replacement map each tick, so a
   body that has drifted out of every warp simply has no entry → zero force
   (auto-clearing, no stale push). No-op map when there are no interventions."
  []
  {:id     :warp
   :writes #{c/accel-warp}
   :run    (fn [world]
             (let [ivs  (filterv #(warp-kinds (:kind %)) (:genesis/interventions world))
                   tick (:tick world)
                   dt   (:sim/dt world)]
               (if (empty? ivs)
                 {c/accel-warp {}}
                 {c/accel-warp
                  (into {}
                        (keep (fn [eid]
                                (let [pos (ecs/get-component world eid c/position)
                                      a   (reduce (fn [acc iv]
                                                    (sp/v+ acc (or (warp-accel-on iv pos tick dt) zero3)))
                                                  zero3 ivs)]
                                  (when (pos? (sp/len a)) [eid a]))))
                        (ecs/entities-with world c/position c/mass))})))})

;; --- Lifecycle --------------------------------------------------------------

(defn expire-interventions
  "Barrier step: drop interventions whose ttl has elapsed. Pure: world → world'."
  [world]
  (let [tick (:tick world)]
    (cond-> world
      (seq (:genesis/interventions world))
      (update :genesis/interventions
              (fn [ivs] (filterv #(pos? (decay-fraction % tick)) ivs))))))

(defn place
  "Spend agency to place an intervention of `kind` at world-metre `position`.
   No-op (returns world unchanged) when there is no observer or it can't afford
   `cost-of` the kind — so the caller never has to pre-check. `opts` overrides
   intervention fields (e.g. :radius :strength :ttl :target-temp)."
  ([world kind position] (place world kind position {}))
  ([world kind position opts]
   (let [obs  (player/get-observer world)
         cost (cost-of kind)]
     (if (and obs (player/can-afford? obs cost))
       (-> world
           (player/update-observer player/spend-agency cost)
           (update :genesis/interventions (fnil conj [])
                   (make-intervention kind position (:tick world) opts)))
       world))))

;; --- Thermal (heat source / sink) -------------------------------------------

(def ^:private thermal-kinds #{:heat/source :heat/sink})

;; States whose temperature PERSISTS tick-to-tick, so an ease actually sticks:
;; nebula (temperature-system skips it) and debris/planet (incremental). Stars and
;; protostars re-derive T from the virial relation every tick, so a thermal push
;; on them would be washed out — heat acts on gas and worlds, not on stellar cores.
(def ^:private thermal-states #{:nebula :debris :planet})

(defn thermal-step
  "New temperature for a body at `body-pos`/`temp` after one tick of every active
   thermal intervention: ease toward each source/sink's target, shaped by
   proximity² and decay, clamped to [min-temp, max-temp]."
  [ivs body-pos temp tick]
  (let [t' (reduce
             (fn [t {:keys [position radius target-temp strength] :as iv}]
               (let [d (sp/dist body-pos position)
                     R (double radius)]
                 (if (< d R)
                   (let [prox (let [u (- 1.0 (/ d R))] (* u u))
                         ease (* heat-approach (double (or strength 1.0))
                                 prox (decay-fraction iv tick))]
                     (+ t (* (- (double target-temp) t) ease)))
                   t)))
             (double temp) ivs)]
    (max min-temp (min max-temp t'))))

(defn thermal-contributions
  "The list of {:target-temp :ease} eases an in-range body at `body-pos` receives
   from the active thermal interventions `ivs` this tick (empty when none reach
   it). `ease` already folds in proximity², decay, strength and the approach
   fraction; the integrator applies T += (target − T)·ease per contribution and
   clamps to [min-temp, max-temp]. The single-influence form of `thermal-step`."
  [ivs body-pos tick]
  (into []
        (keep (fn [{:keys [position radius target-temp strength] :as iv}]
                (let [d (sp/dist body-pos position)
                      R (double radius)]
                  (when (< d R)
                    (let [prox (let [u (- 1.0 (/ d R))] (* u u))]
                      {:target-temp (double target-temp)
                       :ease        (* heat-approach (double (or strength 1.0))
                                       prox (decay-fraction iv tick))})))))
        ivs))

(defn apply-thermal-contributions
  "Apply a body's `heat.intervention` contributions (from `thermal-contributions`)
   to a base temperature, clamped to [min-temp, max-temp]. The integrator's
   temperature updater calls this AFTER deriving the body's base temperature, so
   the player's heat source/sink eases the freshly-derived value (as the old
   serial `apply-thermal-interventions` eased the post-fold value)."
  [base-temp contributions]
  (let [t' (reduce (fn [t {:keys [target-temp ease]}]
                     (+ t (* (- (double target-temp) t) (double ease))))
                   (double base-temp) contributions)]
    (max min-temp (min max-temp t'))))

(defn thermal-intervention-system
  "Write-set system (sole writer of :component/heat.intervention): for every gas
   parcel / world in range of an active heat source/sink, the list of ease
   contributions it receives this tick. A pure snapshot-reading fan-out emitter;
   the integrator owns temperature and applies these eases after deriving the
   base temperature (spec §6: thermal interventions become a heat influence).
   Auto-clears the influence from bodies no longer in range."
  []
  {:id     :thermal-intervention
   :writes #{c/heat-intervention}
   :run    (fn [world]
             (let [ivs (filterv #(thermal-kinds (:kind %)) (:genesis/interventions world))]
               (if (empty? ivs)
                 {c/heat-intervention {}}
                 (let [tick (:tick world)
                       cell (into {}
                                  (keep (fn [eid]
                                          (when (thermal-states (ecs/get-component world eid c/matter-state))
                                            (let [cs (thermal-contributions
                                                       ivs (ecs/get-component world eid c/position) tick)]
                                              (when (seq cs) [eid cs])))))
                                  (ecs/entities-with world c/position c/matter-state c/temperature))]
                   (tick/contribution-write-set
                     c/heat-intervention cell
                     (keys (get-in world [:components c/heat-intervention])))))))})
