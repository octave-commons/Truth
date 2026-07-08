(ns domain.intervention
  "Paid player interventions — the spend side of the agency economy.

   An intervention is a PLACED, DECAYING transient the player drops into the world
   by spending agency quanta (earned from witnessing classifier transitions, see
   domain.player). Phase 1 is warp-space: a gravity WELL (pull) or REPULSOR (push)
   — a placed, decaying Plummer halo (law.stellar/plummer-acceleration), the same
   large-diffuse-mass field the observer's own influence is. Zero force at its
   centre, peak pull at ~0.7× its radius, Keplerian fade beyond; being a
   conservative field, a static well can only bind and gather — it cannot
   slingshot matter out (the old fixed per-tick Δv kick accumulated without
   bound and ejected anything that lingered near the core).

   Interventions live as a plain vector on the world at `:genesis/interventions`.
   The warp system reads them and emits the single-writer `:component/accel.warp`
   channel; the motion integrator sums it like any other force. Nothing here adds
   or removes REAL mass — warp is pure force (the conservation invariant the
   player economy is built on)."
  (:require
   [law.stellar           :as law]
   [shape.spatial         :as sp]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [domain.ecs.tick       :as tick]
   [domain.physics.cache  :as pcache]
   [domain.player         :as player]))

(def ^:const zero3 [0.0 0.0 0.0])

;; --- Defaults / costs -------------------------------------------------------

(def action-cost
  "Agency quanta to place one intervention, by kind. Fixed cost for now —
   affordable after a couple of witnessed transitions (a stellar ignition is 25)."
  {:warp/well 15.0 :warp/repulsor 15.0 :heat/source 15.0 :heat/sink 15.0})

(defn cost-of
  "Return the agency cost to place an intervention of `kind`."
  [kind] (double (get action-cost kind 15.0)))

(def default-radius
  "m — a placed well's Plummer scale radius (~4 render units): where its pull
   peaks (~0.7×) and the ring the inspector overlay draws. Force reaches
   `player/halo-reach-factor` × this. Live knob: :genesis/well-radius."
  4.0e15)

(def default-ttl
  "Ticks an intervention persists before fading. Live knob: :genesis/well-ttl."
  600)

(def default-well-mass-factor
  "A fresh, full-strength well's gravitating mass as a multiple of the seeded
   cloud's mass — SIZED TO THE CLOUD, like the observer halo, so it deepens the
   local potential at the scale self-gravity works at rather than kicking
   matter. Decays to zero over the ttl. Live knob: :genesis/well-mass-factor."
  0.5)

;; Thermal targets the intervention eases matter toward (Kelvin). A source pumps
;; toward hot, a sink drains toward the CMB floor. Easing (not a fixed ΔT) keeps
;; it bounded and self-limiting regardless of how long the source lingers.
(def ^:const heat-target-hot 1.0e5)
(def ^:const heat-target-cold 3.0)
;; Fraction of the gap to the target closed per tick at the core.
;; Live knob: :genesis/heat-approach.
(def default-heat-approach 0.03)
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
       :heat/source (assoc base :target-temp heat-target-hot)
       :heat/sink   (assoc base :target-temp heat-target-cold)
       base)
     opts)))

;; --- Acceleration -----------------------------------------------------------

(def ^:private warp-kinds #{:warp/well :warp/repulsor})

(defn decay-fraction
  "Remaining strength of a warp in [0,1], easing linearly to zero at its ttl."
  [{:keys [born-tick ttl]} tick]
  (max 0.0 (- 1.0 (/ (double (- (long tick) (long born-tick))) (double ttl)))))

(defn warp-accel-on
  "Acceleration a single warp exerts on a body at `body-pos`: a placed Plummer
   halo of mass well-mass-factor × strength × decay × ref-mass with scale
   radius :radius — pull toward the centre for a well, push away for a
   repulsor. Nil beyond `player/halo-reach-factor` scale radii, at the exact
   centre, or once fully decayed. The per-tick Δv cap is applied by the SYSTEM
   on the summed field, not per warp."
  [{:keys [kind position radius strength] :as iv} body-pos tick
   {:keys [ref-mass well-mass-factor]}]
  (let [d    (sp/v- position body-pos)        ;; vector toward the warp centre
        dist (sp/len d)
        scale (double radius)
        M    (* (double well-mass-factor) (double (or strength 1.0))
                (decay-fraction iv tick) (double ref-mass))]
    (when (and (pos? M) (pos? dist) (< dist (* player/halo-reach-factor scale)))
      (let [g    (law/plummer-acceleration M scale dist)
            sign (if (= kind :warp/repulsor) -1.0 1.0)]
        (when (pos? g)
          (sp/v* d (* sign (/ g dist))))))))

(defn warp-acceleration-system
  "Write-set system (sole writer of accel.warp): sum every active warp's halo
   field onto each body, then cap the summed per-tick Δv at the influence
   ceiling (`player/influence-reference` — the dt backstop; a sane field never
   hits it). Returns a full replacement map each tick, so a body that has
   drifted out of every warp simply has no entry → zero force (auto-clearing,
   no stale push). No-op map when there are no interventions."
  []
  {:id     :warp
   :writes #{c/accel-warp}
   :run    (fn [world]
             (let [ivs  (filterv #(warp-kinds (:kind %)) (:genesis/interventions world))
                   tick (:tick world)]
               (if (empty? ivs)
                 {c/accel-warp {}}
                 ;; Evaluate at drift-predicted positions: the kick lands next
                 ;; tick, and a point-attractor evaluated one drift stale is a
                 ;; negatively-damped spring (see pcache/predicted-position-fn).
                 (let [dt     (double (or (:sim/dt world) 1.0e12))
                       {:keys [ref-mass dv-cap]} (player/influence-reference world)
                       ctx    {:ref-mass ref-mass
                               :well-mass-factor
                               (double (or (:genesis/well-mass-factor world)
                                           default-well-mass-factor))}
                       a-max  (/ (double dv-cap) (max 1.0 dt))
                       pos-of (pcache/predicted-position-fn world)]
                   {c/accel-warp
                    (into {}
                          (keep (fn [eid]
                                  (let [pos (pos-of eid)
                                        a   (reduce (fn [acc iv]
                                                      (sp/v+ acc (or (warp-accel-on iv pos tick ctx) zero3)))
                                                    zero3 ivs)
                                        l   (sp/len a)]
                                    (when (pos? l)
                                      [eid (if (> l a-max) (sp/v* a (/ a-max l)) a)]))))
                          (ecs/entities-with world c/position c/mass))}))))})

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
   `cost-of` the kind — so the caller never has to pre-check. Radius and ttl
   default to the world's live knobs (:genesis/well-radius, :genesis/well-ttl);
   `opts` overrides any intervention field (e.g. :radius :strength :ttl
   :target-temp)."
  ([world kind position] (place world kind position {}))
  ([world kind position opts]
   (let [obs  (player/get-observer world)
         cost (cost-of kind)
         knobs {:radius (double (or (:genesis/well-radius world) default-radius))
                :ttl    (double (or (:genesis/well-ttl world) default-ttl))}]
     (if (and obs (player/can-afford? obs cost))
       (-> world
           (player/update-observer player/spend-agency cost)
           (update :genesis/interventions (fnil conj [])
                   (make-intervention kind position (:tick world) (merge knobs opts))))
       world))))

;; --- Thermal (heat source / sink) -------------------------------------------

(def ^:private thermal-kinds #{:heat/source :heat/sink})

;; States whose temperature PERSISTS tick-to-tick, so an ease actually sticks:
;; nebula (temperature-system skips it) and substellar bodies / planets
;; (incremental). Stars and protostars re-derive T from the virial relation every
;; tick, so a thermal push on them would be washed out — heat acts on gas and
;; worlds, not on stellar cores.
(def ^:private thermal-states #{:nebula :planetesimal :gas-giant :brown-dwarf :planet})

(defn thermal-step
  "New temperature for a body at `body-pos`/`temp` after one tick of every active
   thermal intervention: ease toward each source/sink's target, shaped by
   proximity² and decay, clamped to [min-temp, max-temp]."
  [{:keys [ivs body-pos temp tick approach]}]
  (let [approach (or approach default-heat-approach)
        t' (reduce
            (fn [t {:keys [position radius target-temp strength] :as iv}]
              (let [d (sp/dist body-pos position)
                    R (double radius)]
                (if (< d R)
                  (let [prox (let [u (- 1.0 (/ d R))] (* u u))
                        ease (* (double approach) (double (or strength 1.0))
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
  ([ivs body-pos tick] (thermal-contributions ivs body-pos tick default-heat-approach))
  ([ivs body-pos tick approach]
   (into []
         (keep (fn [{:keys [position radius target-temp strength] :as iv}]
                 (let [d (sp/dist body-pos position)
                       R (double radius)]
                   (when (< d R)
                     (let [prox (let [u (- 1.0 (/ d R))] (* u u))]
                       {:target-temp (double target-temp)
                        :ease        (* (double approach) (double (or strength 1.0))
                                        prox (decay-fraction iv tick))})))))
         ivs)))

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
                       approach (double (or (:genesis/heat-approach world)
                                            default-heat-approach))
                       cell (into {}
                                  (keep (fn [eid]
                                          (when (thermal-states (ecs/get-component world eid c/matter-state))
                                            (let [cs (thermal-contributions
                                                      ivs (ecs/get-component world eid c/position) tick approach)]
                                              (when (seq cs) [eid cs])))))
                                  (ecs/entities-with world c/position c/matter-state c/temperature))]
                   (tick/contribution-write-set
                    c/heat-intervention cell
                    (keys (get-in world [:components c/heat-intervention])))))))})
