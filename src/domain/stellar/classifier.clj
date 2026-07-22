(ns domain.stellar.classifier
  "Matter-state classification and the authentic formation state machine."
  (:require
   [clojure.math :as math] [law.stellar                  :as law]
   [domain.stellar.thermodynamics :as thermo]
   [domain.stellar.collapse      :as collapse]
   [domain.stellar.sink          :as sink]
   [domain.chemistry             :as chemistry]
   [domain.ecs.core              :as ecs]
   [domain.ecs.components        :as c]
   [domain.orbital.stability      :as stability]
   [domain.profile               :as profile]
   [shape.spatial                :as sp]))

;; --- Complexity / time scale ------------------------------------------------

(defn complexity-score
  "Observable complexity from a tally of the system. Higher complexity slows
   simulation time — the universe becomes more articulate as it cools.

   Note: only *collapsed* bodies (stars and planets) count as complex.
   Diffuse nebula clumps are not yet resolved into distinct objects, so they
   should not compress time to real-time."
  [{:keys [_body-count star? fusion? planet-count]}]
  (+ (if star? 5 0)
     (if fusion? 20 0)
     (* 10 planet-count)))

(defn- mass-class-update
  [world gas-mass eid]
  (let [state (ecs/get-component world eid c/matter-state)]
    [eid state
     (if (= :star state)
       state
       (law/mass-class (ecs/get-component world eid c/mass) gas-mass))]))

(defn- mass-class-updates
  [world]
  (let [gas-mass (:genesis/gas-particle-mass world)]
    (mapv #(mass-class-update world gas-mass %)
          (ecs/entities-with world c/matter-state c/mass))))

(defn- apply-classify-update
  "Apply a single matter-state promotion, latching an accretion radius when
   a body first condenses out of the nebula."
  [w [eid old nw]]
  (if (= old nw)
    w
    (let [w (ecs/put-component w eid c/matter-state nw)
          old-gas-radius (when (= old :nebula)
                           (double (or (ecs/get-component w eid c/radius) 0.0)))]
      (cond
        (and (= nw :protostar)
             (nil? (ecs/get-component w eid c/accretion-radius)))
        (ecs/put-component w eid c/accretion-radius
                           (* 10.0 (double (or old-gas-radius
                                               (ecs/get-component w eid c/radius) 0.0))))

        (and (= old :nebula)
             (not= nw :nebula)
             (nil? (ecs/get-component w eid c/accretion-radius)))
        (ecs/put-component w eid c/accretion-radius
                           (* 100.0 (double (or old-gas-radius
                                                (ecs/get-component w eid c/radius) 0.0))))

        :else w))))

(defn classify-system
  "Set each clump's matter-state from the mass it has accreted from the cloud
   (law/mass-class): gas -> debris -> planet -> protostar. Formation is emergent —
   a clump becomes a planet or a star-forming core because it ATE enough gas, not
   because it was seeded that way. Stars never declassify; ignition (protostar ->
   star) is left to the fusion system once contraction makes the core hot enough."
  [world]
  (reduce apply-classify-update world (mass-class-updates world)))

;; --- The classifier: authentic matter-state state machine -------------------
;; See docs/notes/2026.06.26-authentic-phase0-formation-physics.md §3. The two
;; physical axes are kept separate: Jeans instability gates whether diffuse gas
;; CONDENSES; accreted mass gates WHAT a condensed core becomes; temperature +
;; mass gate IGNITION. No axis stands in for another (the old bug, where mass
;; alone turned diffuse gas straight into solid bodies).

(def ^:const core-condensation-density
  "Central density (kg/m³) at which collapsing gas becomes optically thick and a
   self-gravitating hydrostatic core forms — the first-core threshold. Diffuse
   cloud gas sits at ~1e-16; crossing this (while Jeans-unstable) is the authentic
   nebula→resolved condensation trigger. It also caps the SPH density: once gas is
   this dense it stops being a fluid sample and becomes a body."
  1.0e-10)

(defn contraction-stalled?
  "True when a contracting core has reached its main-sequence/degenerate radius
   floor while still below the hydrogen-ignition temperature — i.e. it will never
   ignite hydrogen. This is the brown-dwarf outcome."
  [radius mass temperature]
  (and radius mass temperature
       (<= (double radius) (* 1.05 (law/main-sequence-radius mass)))
       (< (double temperature) law/fusion-temp-threshold)))

(defn- substellar-up-ladder
  "Promote a collapsed body up the substellar mass ladder."
  [m]
  (cond
    (>= m law/hydrogen-burning-mass)  :protostar
    (>= m law/deuterium-burning-mass) :brown-dwarf
    :else                             (law/substellar-mass-class m)))

(defn- star-next-state
  "Hysteresis: a star stays a star while fusion is self-sustaining or its mass
   remains above the H-burning limit; otherwise it collapses to a degenerate
   :stellar-remnant — never back to :nebula."
  [m region]
  (cond
    (law/fusion-sustaining? region)    :star
    (>= m law/hydrogen-burning-mass) :star
    :else                              :stellar-remnant))

(defn- protostar-next-state
  "Protostar: ignite, stall as a brown dwarf, or collapse to a remnant if
   stripped below the deuterium-burning limit. Never returns to :nebula."
  [{:keys [mass radius temperature] :as region}]
  (let [m (double (or mass 0.0))]
    (cond
      (and (>= m law/hydrogen-burning-mass)
           (law/fusion-possible? region))
      :star

      (and (>= m law/deuterium-burning-mass)
           (<  m law/hydrogen-burning-mass)
           (contraction-stalled? radius m temperature))
      :brown-dwarf

      (< m law/deuterium-burning-mass)
      :stellar-remnant

      :else :protostar)))

(defn- brown-dwarf-next-state
  "Brown dwarf: climb to protostar if massive enough, or collapse to a remnant
   if stripped below the deuterium-burning limit. Never returns to :nebula."
  [m]
  (cond
    (>= m law/hydrogen-burning-mass) :protostar
    (>= m law/deuterium-burning-mass) :brown-dwarf
    :else                              :stellar-remnant))

(defn- condense-next-state
  "Diffuse gas condenses to a :condensed-core when Jeans-unstable and dense
   enough to be optically thick, and outside existing sink zones. Mass is
   irrelevant at the moment of condensation; the core climbs the substellar
   ladder as it accretes."
  [{:keys [matter-state density position] :as region}
   _gas-particle-mass sink-zones]
  (if (and (collapse/jeans-unstable? region)
           (>= (double (or density 0.0)) core-condensation-density)
           (not (sink/within-existing-sink? position sink-zones)))
    :condensed-core
    (or matter-state :nebula)))

(defn- condensed-core-up-ladder
  "Promote a condensed gas core up the mass ladder. It stays a core until it
   reaches the opacity limit, then becomes a gas giant, brown dwarf, or
   protostar."
  [m]
  (cond
    (>= m law/hydrogen-burning-mass)  :protostar
    (>= m law/deuterium-burning-mass) :brown-dwarf
    (>= m law/opacity-limit-mass)     :gas-giant
    :else                             :condensed-core))

(def ^:private substellar-state-map
  "States that climb the substellar mass ladder share the same transition fn.
   :brown-dwarf is handled separately because it can collapse to :stellar-remnant."
  {:gas-giant   substellar-up-ladder
   :planetesimal substellar-up-ladder
   :condensed-core condensed-core-up-ladder})

(defn classify-next-state
  "Pure transition function for one body's matter-state, given its physical
   region and the cloud's fixed gas-particle mass. Authentic formation beats:

      :nebula  --Jeans-unstable & accreted past one parcel-->  condensed core
                    condensed & mass ≥ H-burning limit      -> :protostar
                    condensed & mass ≥ deuterium limit      -> :brown-dwarf
                    condensed & sub-stellar                 -> :gas-giant / :planetesimal
      :planetesimal/:gas-giant --accreted to ≥ deuterium-->    :brown-dwarf
      :brown-dwarf --accreted to ≥ H-burning-->                :protostar
      :protostar  --T≥1e7 & M≥0.08 M⊙ & H-->                  :star
                  --contraction stalled & 0.013–0.08 M⊙-->    :brown-dwarf
      :star / :brown-dwarf / :gas-giant / :planetesimal       terminal, down-ladder, or :stellar-remnant
      :stellar-remnant                                         terminal (degenerate remnant)
      :planet                                                 owned by the disk
                                                               sub-grid (beat 6)

    The sub-stellar mass ladder is literature-grounded:
      :planetesimal  < opacity limit          (< ~3 M_J)
      :gas-giant     opacity limit to desert   (~3–30 M_J)
      :brown-dwarf   desert to H-burning       (~30–80 M_J)

    See docs/research/physics/stellar-nebula-mass-hierarchy.md.

    `sink-zones` is an optional seq of {:position :radius} maps for existing
    sinks (from `sink/sink-exclusion-zones`). When provided, a :nebula parcel can
    only condense if it is outside all existing sinks' accretion radii — the
    isolation criterion (Federrath et al. 2010)."
  ([region gas-particle-mass]
   (classify-next-state region gas-particle-mass nil))
  ([{:keys [matter-state mass] :as region}
    gas-particle-mass sink-zones]
   (let [m (double (or mass 0.0))]
     (or (when-let [f (substellar-state-map matter-state)] (f m))
         (case matter-state
           :star             (star-next-state m region)
           :protostar        (protostar-next-state region)
           :brown-dwarf      (brown-dwarf-next-state m)
           :stellar-remnant  :stellar-remnant
           :planet           :planet
           (condense-next-state region gas-particle-mass sink-zones))))))

;; --- Feeding-zone constants -------------------------------------------------
;; `feeding-zone-factor` lives in `domain.stellar.structure` so that both
;; `structure` and `sink` can reference it without creating a circular dependency.

(def ^:const condense-interval
  "Minimum sim-time (seconds) between successive nebula→body condensations. The
   nebula→resolved transition is the only thing that turns smoothly-orbiting gas
   into a collidable sink, so pacing it by PHYSICS (sim-time) rather than by tick
   count is what keeps formation watchable at a fixed 60 Hz tick rate: between
   condensations the cloud runs many ticks of pure self-gravity (visible infall
   and rotation) instead of condensing wholesale in a handful of ticks the
   instant the homologous collapse crosses the density threshold." 3.0e11)

(defn condense-tick?
  "True when a new condensation is permitted this tick: either the timestep
   already spans a full `condense-interval`, or sim-time crosses an interval
   boundary during this step. Stateless — derived from `:genesis/sim-time` and
   `:sim/dt` on the frozen snapshot, so it holds across the parallel fan-out."
  [world]
  (let [t  (double (or (:genesis/sim-time world) 0.0))
        dt (double (or (:sim/dt world) 0.0))]
    (or (not (pos? condense-interval))
        (>= dt condense-interval)
        (not= (math/floor (/ t condense-interval))
              (math/floor (/ (+ t dt) condense-interval))))))

(defn- classifier-scan
  [world]
  {:world world
   :gas-mass (:genesis/gas-particle-mass world)
   :eids (ecs/entities-with world c/matter-state c/mass)
   :zones (sink/sink-exclusion-zones world)
   :promotions (get-in world [:components c/promotion-signal] {})})

(defn- classifier-transitions
  [{:keys [world gas-mass eids zones promotions] :as state}]
  (assoc state :transitions
         (into {} (keep (fn [eid]
                          (let [region (thermo/entity->region world eid)
                                cur (:matter-state region)
                                sig (get promotions eid)
                                nxt (if sig
                                      (:promotion sig)
                                      (classify-next-state region gas-mass zones))]
                            (when (not= cur nxt)
                              [eid {:old cur :nxt nxt :region region}]))))
               eids)))

(defn- classifier-select
  [{:keys [transitions world] :as state}]
  (let [big-condenses (filterv (fn [[_ {:keys [old nxt]}]]
                                 (and (= old :nebula) (not= nxt :nebula) (not= nxt :planetesimal)))
                               transitions)
        best-big-condense (when (seq big-condenses)
                            (key (apply max-key
                                        (fn [[eid _]]
                                          (double (or (:density (:region (get transitions eid))) 0.0)))
                                        big-condenses)))
        applied (into {} (keep (fn [[eid {:keys [old nxt]}]]
                                 (cond
                                   (and (= old :nebula) (= nxt :planetesimal)) nil
                                   (and (= old :nebula) (not= nxt :nebula))
                                   (when (= eid best-big-condense) [eid nxt])
                                   :else [eid nxt])))
                      transitions)
        acc-radius-map (when best-big-condense
                         (let [gas-r (double (or (:genesis/gas-smoothing-radius world) 0.0))
                               factor (double (or (:genesis/feeding-zone-factor world)
                                                  sink/feeding-zone-factor))]
                           (when (pos? gas-r)
                             {best-big-condense (* factor gas-r)})))]
    (assoc state
           :best-big-condense best-big-condense
           :applied applied
           :acc-radius-map acc-radius-map)))

(defn- classifier-write-set
  [{:keys [applied acc-radius-map]}]
  (cond-> {c/matter-state applied}
    acc-radius-map (assoc c/accretion-radius acc-radius-map)))

(defn classifier-system
  "Double-buffer write-set system: SOLE writer of matter-state AND accretion-radius.
   Applies `classify-next-state` to every body. :nebula → :planetesimal
   condensation is deferred to `condensation-seeder-system` (seed-and-grow). All
   other condense transitions (:gas-giant, :brown-dwarf, :protostar) still promote
   the whole gas parcel and latch an accretion-radius so the big sink can feed.
   Non-condense transitions (up/down the substellar ladder, ignition) are applied
   directly. Condensation pacing lives in the seeder."
  []
  {:id     :classifier
   :writes #{c/matter-state c/accretion-radius}
   :reads  #{c/promotion-signal}
   :run
   (fn [world]
     (profile/profile-sections
      world
      [[:classifier/scan classifier-scan]
       [:classifier/transitions classifier-transitions]
       [:classifier/select classifier-select]
       [:classifier/write-set classifier-write-set]]))})

;; --- M5 handoff Phase 1: material + thermal classification ------------------
;; See kanban/tasks/ecology-m5-phase1-planet-classification.md and parent
;; kanban/tasks/ecology-water-gate-snowline.md §3.1-3.2. Pure classification
;; from composition/mass and two-body equilibrium temperature only — no orbit
;; integration, no atmosphere physics. This is the real material/thermal gate
;; that replaces the trivially-satisfied `habitability-score > 0.2` scalar.

(def ^:const rocky-max-mass
  "Upper mass bound (kg) for the :rocky material class (parent §3.1)." 1.0e25)

(def ^:const icy-max-mass
  "Upper mass bound (kg) for the :icy material class (parent §3.1)." 5.0e25)

(def ^:const gas-giant-min-mass
  "Lower mass bound (kg) for the :gaseous material class (parent §3.1)." 1.0e25)

(defn material-class
  "Bulk material class of a body from its element-resolved `composition` map
   (domain.chemistry/bulk-categories at `temperature`), plus `mass` (kg), per
   parent §3.1:

     :rocky    metal+rock > 50%, H+He < 25%, mass < 1e25 kg
     :icy      ice/volatiles > 50%, mass < 5e25 kg
     :gaseous  H+He > 50%, mass > 1e25 kg
     :mixed    none of the above strongly

   Uses the DERIVED bulk categories (metal/rock/ice fractions from Lodders
   condensation temperatures), never a stored `:metals` key — composition is
   the real element map (H, He, O, Si, Fe, ...)."
  [composition mass temperature]
  (let [m (double mass)
        {:keys [rock metal ice]} (chemistry/bulk-categories composition temperature)
        rock-metal (+ (double rock) (double metal))
        h-he (+ (double (get composition :H 0.0))
                (double (get composition :He 0.0)))]
    (cond
      (and (> h-he 0.5) (> m gas-giant-min-mass))            :gaseous
      (and (> rock-metal 0.5) (< h-he 0.25) (< m rocky-max-mass)) :rocky
      (and (> (double ice) 0.5) (< m icy-max-mass))           :icy
      :else                                                   :mixed)))

(def ^:private material-albedo
  "Coarse Bond albedo by material class — ice and cloud tops reflect more
   sunlight than bare rock/metal. A single rough number per class, not a
   wavelength-resolved model (parent §3.2)."
  {:rocky   0.3
   :icy     0.5
   :gaseous 0.5
   :mixed   0.3})

(defn equilibrium-temperature
  "Two-body radiative equilibrium temperature (K):
     T_eff = (L (1 - A) / (16 π σ a²))^0.25
   for a star of luminosity `L` (W), orbital separation `a` (m), and Bond
   albedo `albedo` (parent §3.2)."
  [L a albedo]
  (let [l (double L) aa (double a) alb (double albedo)]
    (math/pow (/ (* l (- 1.0 alb))
                 (* 16.0 math/PI law/stefan-boltzmann aa aa))
              0.25)))

(defn thermal-band
  "Coarse thermal band for a body of `material-class` at orbital separation
   `a` (m) from a star of luminosity `L` (W). Computes the two-body
   equilibrium temperature (`equilibrium-temperature`) with a coarse
   composition-based Bond albedo, then buckets it per parent §3.2:
     :frozen < 150 K, :cold 150-250 K, :temperate 250-350 K,
     :warm 350-450 K, :hot > 450 K."
  [L a material-class]
  (let [albedo (get material-albedo material-class 0.3)
        t-eff (equilibrium-temperature L a albedo)]
    (cond
      (< t-eff 150.0) :frozen
      (< t-eff 250.0) :cold
      (< t-eff 350.0) :temperate
      (< t-eff 450.0) :warm
      :else            :hot)))

(def ^:private non-classifiable-states
  "Matter states that are not planet-candidate bodies and are excluded from
   material/thermal classification: diffuse gas and the central star itself."
  #{:nebula :star :protostar :stellar-remnant})

(defn- central-star
  "The most massive :star or :protostar in `world`, as
   `{:position :velocity :mass :radius :luminosity}`, or nil if none exists
   yet. Mirrors `domain.stellar.disc/disc-identification-system`'s
   central-body lookup. Mass/radius/velocity feed the M5 Phase 2 orbit-
   stability proxy (`domain.orbital.stability/orbit-stability`) alongside the
   luminosity Phase 1 already reads for thermal-band."
  [world]
  (let [candidates (filterv #(contains? #{:star :protostar}
                                        (ecs/get-component world % c/matter-state))
                            (ecs/entities-with world c/matter-state c/mass))]
    (when (seq candidates)
      (let [eid (apply max-key #(ecs/get-component world % c/mass) candidates)]
        {:position   (ecs/get-component world eid c/position)
         :velocity   (or (ecs/get-component world eid c/velocity) [0.0 0.0 0.0])
         :mass       (double (or (ecs/get-component world eid c/mass) 0.0))
         :radius     (double (or (ecs/get-component world eid c/radius) 0.0))
         :luminosity (double (or (ecs/get-component world eid c/luminosity) 0.0))}))))

(defn- classify-body-material
  [world eid]
  (when-let [composition (ecs/get-component world eid c/composition)]
    (when-let [mass (ecs/get-component world eid c/mass)]
      (let [temperature (double (or (ecs/get-component world eid c/temperature) 0.0))]
        (material-class composition mass temperature)))))

(defn- classify-body-thermal
  [world star eid mclass]
  (when star
    (when-let [pos (ecs/get-component world eid c/position)]
      (when-let [star-pos (:position star)]
        (let [a (sp/dist pos star-pos)]
          (when (pos? a)
            (thermal-band (:luminosity star) a mclass)))))))

;; --- M5 handoff Phase 2: orbit stability (analytic proxy) --------------------
;; See kanban/tasks/ecology-m5-phase2-orbit-stability.md and parent
;; kanban/tasks/ecology-water-gate-snowline.md §3.3. Folded into
;; `classification-system` rather than a separate `:stability` system: it needs
;; the exact same candidate-body scan and the same central-star lookup this
;; system already does for material/thermal classification, so extending the
;; one write-set keeps reads minimal and `reg/write-conflicts` empty without a
;; second fan-out emitter duplicating the scan.

(defn- candidate-snapshot
  "`{:position :mass}` for a candidate body, or nil if either is missing."
  [world eid]
  (when-let [pos (ecs/get-component world eid c/position)]
    (when-let [mass (ecs/get-component world eid c/mass)]
      {:position pos :mass mass})))

(defn- classify-body-stability
  "Run the analytic orbit-stability proxy for one candidate against the
   central `star` and every OTHER candidate in `candidates` (a map of
   eid -> `{:position :mass}`). nil (omitted from the write-set) when the star
   or this body's own velocity/mass/position are not yet resolvable."
  [world star eid candidates]
  (when star
    (when-let [pos (ecs/get-component world eid c/position)]
      (when-let [mass (ecs/get-component world eid c/mass)]
        (let [vel (or (ecs/get-component world eid c/velocity) [0.0 0.0 0.0])
              others (keep (fn [[oid data]] (when (not= oid eid) data)) candidates)]
          (stability/orbit-stability {:position pos :velocity vel :mass mass}
                                     star others))))))

(defn classification-system
  "Double-buffer write-set system: SOLE writer of `c/material-class`,
   `c/thermal-band`, AND `c/orbit-stable` (M5 handoff Phases 1 and 2). Jacobi
   fan-out emitter — reads the frozen snapshot only, writes all three
   component types for every planet-candidate body (any matter-state other
   than nebula/protostar/star/stellar-remnant) that has composition, mass, and
   a resolvable position relative to the central star. Orbit stability is an
   ANALYTIC PROXY (`domain.orbital.stability/orbit-stability`) — periapsis/
   apoapsis bounds plus Hill-radius separation from sibling candidates — NOT a
   10 Myr two-body integration. Bodies missing required data (or with no
   central star yet) are simply omitted from the write-set this tick, not
   defaulted."
  []
  {:id     :classification
   :writes #{c/material-class c/thermal-band c/orbit-stable}
   :reads  #{c/matter-state c/mass c/composition c/temperature c/position
             c/velocity c/radius c/luminosity}
   :run
   (fn [world]
     (let [star (central-star world)
           eids (filterv #(not (contains? non-classifiable-states
                                          (ecs/get-component world % c/matter-state)))
                         (ecs/entities-with world c/matter-state c/mass))
           materials (into {} (keep (fn [eid]
                                      (when-let [mclass (classify-body-material world eid)]
                                        [eid mclass])))
                           eids)
           thermals (into {} (keep (fn [eid]
                                     (when-let [band (classify-body-thermal
                                                      world star eid (get materials eid :mixed))]
                                       [eid band])))
                          eids)
           candidates (into {} (keep (fn [eid]
                                       (when-let [snap (candidate-snapshot world eid)]
                                         [eid snap])))
                            eids)
           stabilities (into {} (keep (fn [eid]
                                        (when-some [ok (classify-body-stability world star eid candidates)]
                                          [eid ok])))
                             eids)]
       {c/material-class materials
        c/thermal-band   thermals
        c/orbit-stable   stabilities}))})
