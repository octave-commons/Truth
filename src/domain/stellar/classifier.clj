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
   [law.atmosphere                :as atmosphere]
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

(def material-albedo
  "Coarse Bond albedo by material class — ice and cloud tops reflect more
   sunlight than bare rock/metal. A single rough number per class, not a
   wavelength-resolved model (parent §3.2). Public: reused by the M5 Phase 4
   handoff gate (`eligible-candidate?`/`build-candidate-record`) so the
   equilibrium temperature used to admit a `:planet-candidate` is computed
   with the exact same albedo `classify-body-equilibrium-temp` already
   uses — one source of truth, not a re-derived constant."
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

(defn- star-record
  "The `{:id :matter-state :position :velocity :mass :radius :luminosity}`
   map for one star entity, or nil when data is incomplete."
  [world eid]
  (when-let [pos (ecs/get-component world eid c/position)]
    (when-let [mass (ecs/get-component world eid c/mass)]
      {:id         eid
       :matter-state (ecs/get-component world eid c/matter-state)
       :position   pos
       :velocity   (or (ecs/get-component world eid c/velocity) [0.0 0.0 0.0])
       :mass       (double mass)
       :radius     (double (or (ecs/get-component world eid c/radius) 0.0))
       :luminosity (double (or (ecs/get-component world eid c/luminosity) 0.0))})))

(defn stellar-bodies
  "Every `:star`/`:protostar` entity in `world` (the candidate parent
   population), as eids. One scan per tick, shared by all per-body parent
   lookups — never a per-body re-scan."
  [world]
  (filterv #(contains? #{:star :protostar}
                       (ecs/get-component world % c/matter-state))
           (ecs/entities-with world c/matter-state c/mass)))

(defn central-star
  "The most massive :star or :protostar in `world`, as
   `{:id :matter-state :position :velocity :mass :radius :luminosity}`, or
   nil if none exists yet. Mirrors `domain.stellar.disc/disc-identification-
   system`'s central-body lookup. Mass/radius/velocity feed the M5 Phase 2
   orbit-stability proxy (`domain.orbital.stability/orbit-stability`)
   alongside the luminosity Phase 1 already reads for thermal-band; `:id`
   and `:matter-state` feed the M5 Phase 4 handoff gate
   (`domain.stellar.classifier/handoff-system`), which needs to know the
   star's own entity id (for `:planet-candidate`'s `:star-id`) and whether
   it has actually reached `:star` (not merely `:protostar`).

   NOTE (multi-timescale card 4): this is the SYSTEM-level primary only —
   retained for the system-level handoff criterion (a :star exists) and
   legacy callers. Per-body orbit/thermal/eligibility evaluation uses
   `dominant-attractor`: in a multi-star field a planet is governed by its
   NEAREST BOUND star, not the biggest star across the cloud."
  [world]
  (let [candidates (stellar-bodies world)]
    (when (seq candidates)
      (let [eid (apply max-key #(ecs/get-component world % c/mass) candidates)]
        (star-record world eid)))))

(defn dominant-attractor
  "The star `eid` is governed by: the `:star`/`:protostar` with the lowest
   bound two-body specific energy relative to the body (ε = u²/2 − μ/r < 0,
   unsoftened μ — the velocity pairing rule, design §3.5: classified planets
   are the sub-stepped population, which lives under the integrator's exact
   Newtonian drift). Ties broken by distance. Returns the `star-record` map,
   or nil when the body is bound to no star (a hyperbolic interloper is
   legitimately not a planet-candidate — multi-timescale card 4)."
  [world eid stars]
  (when-let [pos (ecs/get-component world eid c/position)]
    (let [vel (or (ecs/get-component world eid c/velocity) [0.0 0.0 0.0])]
      (->> stars
           (remove #(= % eid))
           (keep #(star-record world %))
           (keep (fn [star]
                   (let [r-vec (sp/v- pos (:position star))
                         v-vec (sp/v- vel (:velocity star))
                         r (sp/len r-vec)
                         mu (* law/G (:mass star))]
                     (when (pos? r)
                       (let [energy (- (/ (sp/len2 v-vec) 2.0) (/ mu r))]
                         (when (neg? energy)
                           {:star star :energy energy :r r}))))))
           (sort-by (juxt :energy :r))
           first
           :star))))

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
  "Run the analytic orbit-stability proxy for one candidate against its
   dominant-attractor `star` (per-body parent, card 4) and every OTHER
   candidate in `candidates` (a map of eid -> `{:position :mass}`). nil
   (omitted from the write-set) when the star or this body's own
   velocity/mass/position are not yet resolvable."
  [world star eid candidates]
  (when star
    (when-let [pos (ecs/get-component world eid c/position)]
      (when-let [mass (ecs/get-component world eid c/mass)]
        (let [vel (or (ecs/get-component world eid c/velocity) [0.0 0.0 0.0])
              others (keep (fn [[oid data]] (when (not= oid eid) data)) candidates)]
          (stability/orbit-stability {:position pos :velocity vel :mass mass}
                                     star others))))))

;; --- M5 handoff Phase 3: atmosphere retention --------------------------------
;; See kanban/tasks/ecology-m5-phase3-atmosphere-retention.md, parent
;; kanban/tasks/ecology-water-gate-snowline.md §4, and the grounding research
;; note docs/research/atmosphere/planetary-atmosphere-retention-classifier.md
;; (which supersedes the parent card's rougher formulas — most-probable-speed
;; v_th and the literal moon-like test — where they conflict; see that note's
;; §3.4 and §6.1). Folded into `classification-system` for the same reason
;; Phase 2 was: it is a pure downstream consumer of material-class/thermal-
;; band computed in the very same fan-out, so extending one write-set keeps
;; `reg/write-conflicts` empty without a second emitter re-scanning candidates.

(defn- candidate-species
  "Chemically plausible atmospheric volatiles for a body of `material-class`
   at `thermal-band` (research note §3.3): gaseous bodies retain a primordial
   H2/He envelope; rocky/icy/mixed bodies are gated to secondary volatiles
   (N2, CO2), with H2O added only when the thermal band is warm enough that
   water is not locked up as surface/subsurface ice (:temperate/:warm/:hot;
   the same snowline boundary Phase 1 already uses for thermal-band)."
  [material-class thermal-band]
  (if (= material-class :gaseous)
    #{:H2 :He}
    (cond-> #{:N2 :CO2}
      (contains? #{:temperate :warm :hot} thermal-band) (conj :H2O))))

(defn- representative-species-mass
  "Single dominant-species molecular mass (kg) used for the overall
   atmosphere-class bucket (research note §4.2): the H/He mean mass for
   gaseous bodies, otherwise CO2 for :hot bodies (Venus-like, secondary CO2
   atmosphere dominant) or N2 for cooler bodies (Earth/Titan-like default)."
  [material-class thermal-band]
  (if (= material-class :gaseous)
    atmosphere/h2-he-mean-mass
    (if (= thermal-band :hot)
      (:CO2 atmosphere/species-mass)
      (:N2 atmosphere/species-mass))))

(defn- species-retention-threshold
  "Retention-ratio threshold for `species`: the higher H2/He bar (early-XUV
   exposure) or the lower heavy-secondary-volatile bar (research note §3.4)."
  [species]
  (if (contains? #{:H2 :He} species)
    atmosphere/h-he-retention-ratio
    atmosphere/heavy-retention-ratio))

(defn- atmosphere-bucket
  "Bucket a representative retention ratio into a coarse atmosphere-class
   (research note §3.4): `:none` r<3, `:thin` 3-6, `:substantial` 6-10,
   `:thick` r>=10."
  [ratio]
  (cond
    (< ratio atmosphere/thin-ratio-floor)         :none
    (< ratio atmosphere/substantial-ratio-floor)  :thin
    (< ratio atmosphere/thick-ratio-floor)        :substantial
    :else                                         :thick))

(defn atmosphere-class
  "Coarse Phase-0 atmosphere-retention classifier (M5 handoff Phase 3), pure
   function of quantities already resolved by handoff time:

     `{:mass M :radius R :temperature T :material-class mc :thermal-band tb}`
     => `{:atmosphere-class :none|:thin|:substantial|:thick
          :retained-species #{:H2 :He :H2O :N2 :CO2}}`

   Uses the classical Jeans escape-parameter ratio
   `r = v_esc/v_th = sqrt(2GM/R) / sqrt(3 k_B T / m)` (RMS thermal speed,
   `law.atmosphere/retention-ratio` — the same v_th convention as
   `domain.chemistry/can-retain-gas?`, see that fn's docstring and the
   research note §3.4 for the reconciliation). The composition gate
   (`candidate-species`) runs FIRST: a species must be chemically plausible
   for this material-class/thermal-band before its retention ratio is even
   checked, so a volatile-poor rocky body cannot be credited with a thick
   CO2 atmosphere just because its gravity is high enough in principle.

   This is a one-shot formation-time verdict against THERMAL escape only —
   it does not model non-thermal loss (solar-wind sputtering, no-
   magnetosphere pickup), which is what actually strips real ambiguous
   bodies like the Moon or Mercury (research note §6.1, §8.2); do not read
   `:thin`/`:substantial` as \"confirmed has a bound atmosphere.\""
  [{:keys [mass radius temperature material-class thermal-band]}]
  (let [candidates (candidate-species material-class thermal-band)
        retained   (into #{}
                         (filter #(> (atmosphere/retention-ratio
                                      mass radius temperature
                                      (get atmosphere/species-mass %))
                                     (species-retention-threshold %)))
                         candidates)
        mu         (representative-species-mass material-class thermal-band)
        ratio      (atmosphere/retention-ratio mass radius temperature mu)]
    {:atmosphere-class (atmosphere-bucket ratio)
     :retained-species retained}))

(defn classify-body-equilibrium-temp
  "Two-body equilibrium temperature (K) for a candidate body, mirroring
   `classify-body-thermal` but returning the raw temperature instead of its
   bucketed thermal-band — the input `atmosphere-class` needs (research note
   §4.2), not the coarse label. nil when the star or this body's position
   is not yet resolvable. Public: also the M5 Phase 4 handoff gate's source
   for the §2 planet-candidate table's 150-400 K temperature test."
  [world star eid mclass]
  (when star
    (when-let [pos (ecs/get-component world eid c/position)]
      (when-let [star-pos (:position star)]
        (let [a (sp/dist pos star-pos)]
          (when (pos? a)
            (equilibrium-temperature (:luminosity star) a
                                     (get material-albedo mclass 0.3))))))))

(defn- classify-body-atmosphere
  "Run `atmosphere-class` for one candidate body, or nil (omitted from the
   write-set) when mass/radius/material-class/thermal-band/temperature are
   not all resolvable yet."
  [world star eid mclass tband]
  (when (and mclass tband)
    (when-let [mass (ecs/get-component world eid c/mass)]
      (when-let [radius (ecs/get-component world eid c/radius)]
        (when-let [t-eff (classify-body-equilibrium-temp world star eid mclass)]
          (atmosphere-class {:mass mass :radius radius :temperature t-eff
                             :material-class mclass :thermal-band tband}))))))

(defn classification-system
  "Double-buffer write-set system: SOLE writer of `c/material-class`,
   `c/thermal-band`, `c/orbit-stable`, `c/atmosphere-class`, AND
   `c/retained-species` (M5 handoff Phases 1-3). Jacobi fan-out emitter —
   reads the frozen snapshot only, writes all five component types for every
   planet-candidate body (any matter-state other than nebula/protostar/
   star/stellar-remnant) that has composition, mass, and a resolvable
   position relative to the central star. Orbit stability is an ANALYTIC
   PROXY (`domain.orbital.stability/orbit-stability`) — periapsis/apoapsis
   bounds plus Hill-radius separation from sibling candidates — NOT a 10 Myr
   two-body integration. Atmosphere retention (`atmosphere-class`) is a
   one-shot Jeans-escape-ratio verdict, not an ongoing mass-loss simulation
   (that is `domain.atmosphere`'s xuv-atmospheric-escape-system) — see that
   fn's docstring. Bodies missing required data (or with no central star
   yet) are simply omitted from the write-set this tick, not defaulted."
  []
  {:id     :classification
   :writes #{c/material-class c/thermal-band c/orbit-stable
             c/atmosphere-class c/retained-species}
   :reads  #{c/matter-state c/mass c/composition c/temperature c/position
             c/velocity c/radius c/luminosity}
   :run
   (fn [world]
     (let [stars (stellar-bodies world)
           eids (filterv #(not (contains? non-classifiable-states
                                          (ecs/get-component world % c/matter-state)))
                         (ecs/entities-with world c/matter-state c/mass))
           ;; Multi-timescale card 4: every per-body verdict (thermal band,
           ;; orbit stability, equilibrium temperature) is evaluated against
           ;; the body's OWN dominant attractor, not the system-primary
           ;; `central-star` — in a multi-star field the primary is the wrong
           ;; parent for most bodies.
           body-parents (into {} (keep (fn [eid]
                                    (when-let [p (dominant-attractor world eid stars)]
                                      [eid p])))
                         eids)
           materials (into {} (keep (fn [eid]
                                      (when-let [mclass (classify-body-material world eid)]
                                        [eid mclass])))
                           eids)
           thermals (into {} (keep (fn [eid]
                                     (when-let [band (classify-body-thermal
                                                      world (get body-parents eid) eid
                                                      (get materials eid :mixed))]
                                       [eid band])))
                           eids)
           candidates (into {} (keep (fn [eid]
                                       (when-let [snap (candidate-snapshot world eid)]
                                         [eid snap])))
                            eids)
           stabilities (into {} (keep (fn [eid]
                                        (when-some [ok (classify-body-stability
                                                        world (get body-parents eid) eid candidates)]
                                          [eid ok])))
                             eids)
           atmospheres (into {} (keep (fn [eid]
                                        (when-let [verdict (classify-body-atmosphere
                                                            world (get body-parents eid) eid
                                                            (get materials eid)
                                                            (get thermals eid))]
                                          [eid verdict])))
                             eids)
           atmosphere-classes (into {} (keep (fn [[eid v]] [eid (:atmosphere-class v)])) atmospheres)
           retained-species-map (into {} (keep (fn [[eid v]] [eid (:retained-species v)])) atmospheres)]
       {c/material-class materials
        c/thermal-band   thermals
        c/orbit-stable   stabilities
        c/atmosphere-class atmosphere-classes
        c/retained-species retained-species-map}))})

;; --- M5 handoff Phase 4: planet-candidate record + handoff gate -------------
;; See kanban/tasks/ecology-m5-phase4-handoff-event.md and parent
;; kanban/tasks/ecology-water-gate-snowline.md §2, §5. This is the FINAL M5
;; stage: it does not re-derive anything `classification-system` (Phases 1-3)
;; already wrote — it reads material-class/thermal-band/orbit-stable/
;; atmosphere-class/retained-species off the frozen snapshot (one tick
;; Jacobi-stale, same as every other cross-system read in this fan-out) and
;; assembles the full `:planet-candidate` contract (parent §5) for every body
;; that ALSO clears the stricter per-planet §2 table (mass, bound low-
;; eccentricity orbit, 150-400 K equilibrium temperature, <95% H/He by mass,
;; at least :thin atmosphere retention) — a planet-candidate is a STRICT
;; subset of a merely material-classified body. Emission of the whole batch
;; is additionally gated on the SYSTEM-level §2 criteria (a :star exists, at
;; least one eligible candidate exists, and no collision merge is currently
;; in flight); while that gate is false the write-set is simply `{}` for this
;; tick, so any previously-recorded candidates are left exactly as they were
;; (persist, never retracted) rather than being erased. The ledger
;; `:event/phase0-handoff` append itself is NOT done from inside this fan-out
;; — like every other real ledger append in this codebase (see
;; `domain.genesis.tick/emit-promotion-events`), dispatching an event from
;; inside a write-set `:run` only mutates a scratch snapshot that is diffed
;; away (see `domain.physics.collision/collision-detection-system`'s 0-arity
;; form for the same pattern with `:event/collision`); the ledger append is
;; therefore `domain.genesis.tick/emit-handoff-event`, a serial post-fold
;; step that reacts to this system's `c/planet-candidate` output, so there is
;; exactly one place that decides "has the gate fired" (here) and one place
;; that reacts to it (the ledger step).

(def ^:const candidate-max-eccentricity
  "Eccentricity ceiling for a planet-candidate's orbit (parent §2 table:
   \"Bound to the star; eccentricity < 0.4\")." 0.4)

(def ^:const candidate-min-temperature
  "Lower equilibrium-temperature bound (K) for a planet-candidate (parent §2
   table: \"between 150 K and 400 K\")." 150.0)

(def ^:const candidate-max-temperature
  "Upper equilibrium-temperature bound (K) for a planet-candidate (parent §2
   table)." 400.0)

(def ^:const candidate-max-h-he-fraction
  "H+He mass-fraction ceiling for a planet-candidate (parent §2 table:
   \"Not > 95% H/He by mass\")." 0.95)

(def ^:const core-dynamo-min-omega
  "Coarse angular-speed floor (rad/s) above which a convective interior is
   assumed to spin fast enough to sustain a magnetic dynamo (parent §5
   `:core-dynamo?`). Set below Earth's ~7.29e-5 rad/s sidereal rate so both
   Earth-like rocky rotators and faster gas giants qualify. This is a
   deliberately coarse Phase-0 proxy — NOT a Christensen-scaling-law dynamo
   number — matching the rest of this namespace's one-shot formation-time
   verdicts." 5.0e-5)

(defn- stable-star
  "`central-star` restricted to a body that has actually reached `:star`
   (parent §2 criterion 1) — a protostar does not satisfy the handoff gate,
   even though `central-star` itself (shared with Phases 1-3) also accepts
   protostars for thermal-band purposes."
  [world]
  (let [star (central-star world)]
    (when (= :star (:matter-state star))
      star)))

(defn- h-he-fraction
  "H+He mass fraction of a `composition` map (missing species default 0)."
  [composition]
  (+ (double (get composition :H 0.0))
     (double (get composition :He 0.0))))

(defn- candidate-orbit-elements
  "Two-body orbital elements of candidate `eid` relative to `star`, or nil
   if position/velocity are not resolvable or the orbit is unbound."
  [world star eid]
  (when-let [pos (ecs/get-component world eid c/position)]
    (let [vel (or (ecs/get-component world eid c/velocity) [0.0 0.0 0.0])
          mu  (* law/G (double (:mass star)))
          r-vec (sp/v- pos (:position star))
          v-vec (sp/v- vel (:velocity star))]
      (stability/two-body-elements r-vec v-vec mu))))

(defn- eligible-candidate?
  "True when candidate `eid` clears every per-planet handoff test in the
   parent §2 table: mass above `law/rounding-mass-threshold`, a bound orbit
   with eccentricity < `candidate-max-eccentricity`, an equilibrium
   temperature in [`candidate-min-temperature` `candidate-max-temperature`],
   composition under `candidate-max-h-he-fraction` H+He by mass, and at
   least `:thin` atmosphere retention. Reads Phase 1-3's already-written
   material-class/orbit-stable/atmosphere-class rather than re-deriving
   them."
  [world star eid]
  (boolean
   (when-let [mass (ecs/get-component world eid c/mass)]
     (when-let [mclass (ecs/get-component world eid c/material-class)]
       (when (and (some? (ecs/get-component world eid c/orbit-stable))
                  (ecs/get-component world eid c/orbit-stable)
                  (not= :none (ecs/get-component world eid c/atmosphere-class))
                  (> (double mass) law/rounding-mass-threshold)
                  (<= (h-he-fraction (or (ecs/get-component world eid c/composition) {}))
                      candidate-max-h-he-fraction))
         (when-let [elements (candidate-orbit-elements world star eid)]
           (when-let [t-eff (classify-body-equilibrium-temp world star eid mclass)]
             (and (< (:eccentricity elements) candidate-max-eccentricity)
                  (<= candidate-min-temperature t-eff candidate-max-temperature)))))))))

(defn- convective-interior?
  "Coarse proxy for a differentiated, convective interior: any resolved
   material class except `:mixed` (an undifferentiated, no-strong-category
   rubble pile)."
  [material-class]
  (contains? #{:rocky :icy :gaseous} material-class))

(defn core-dynamo?
  "Coarse Phase-0 estimate of `:core-dynamo?` (parent §5): true when the body
   has a plausibly convective interior (`convective-interior?`) AND is
   rotating faster than `core-dynamo-min-omega`. `spin` is the body-fixed
   angular-velocity vector (`c/spin`, rad/s), possibly nil."
  [material-class spin]
  (and (convective-interior? material-class)
       (some? spin)
       (>= (sp/len spin) core-dynamo-min-omega)))

(defn surface-gravity
  "Surface gravity g = GM/R² (m/s²) for `mass` (kg) and `radius` (m). 0.0 when
   `radius` is missing or non-positive (avoids a division by zero for a body
   whose structure hasn't resolved yet)."
  [mass radius]
  (let [r (double (or radius 0.0))]
    (if (pos? r)
      (/ (* law/G (double (or mass 0.0))) (* r r))
      0.0)))

(defn- formation-events-for
  "Every ledger event (by `:id`) whose `:entities` set includes `eid` — the
   threshold events that shaped this body (parent §5 `:formation-events`).
   Reads the ledger directly off `world` (a top-level world key, like
   `:genesis/spatial-tree`, not a component — see
   `domain.ecs.registry`'s docstring on what `:reads` covers)."
  [world eid]
  (mapv :id
        (filter #(contains? (:entities %) eid)
                (get-in world [:ledger :events] []))))

(defn build-candidate-record
  "Assemble the full `:planet-candidate` record (parent §5) for candidate
   `eid` relative to `star`. Every field is either a component Phases 1-3
   already wrote, or a direct pure derivation from mass/radius/composition/
   angular-momentum/spin/b-field already carried by the body — nothing here
   is invented data.

   Forward-compat note (planetary-voxel phase, not implemented by this
   card): `:bulk-composition`, `:thermal-band`, `:atmosphere-class`/
   `:retained-species`, `:rotation-axis`, and `:surface-gravity` are exactly
   the fields a future per-planet voxel-world phase would seed geography/
   chemistry/atmosphere generation from."
  [world star eid]
  (let [mclass (ecs/get-component world eid c/material-class)
        elements (candidate-orbit-elements world star eid)
        mass   (ecs/get-component world eid c/mass)
        radius (ecs/get-component world eid c/radius)
        spin   (ecs/get-component world eid c/spin)]
    {:planet-id              eid
     :star-id                (:id star)
     :material-class          mclass
     :thermal-band            (ecs/get-component world eid c/thermal-band)
     :equilibrium-temperature (classify-body-equilibrium-temp world star eid mclass)
     :semi-major-axis         (:semi-major-axis elements)
     :eccentricity            (:eccentricity elements)
     :orbit-stable?           (boolean (ecs/get-component world eid c/orbit-stable))
     :atmosphere-class        (ecs/get-component world eid c/atmosphere-class)
     :retained-species        (or (ecs/get-component world eid c/retained-species) #{})
     :volatile-budget-kg      (ecs/get-component world eid c/volatile-budget)
     :differentiated-layers   (ecs/get-component world eid c/differentiated-layers)
     :bulk-composition        (or (ecs/get-component world eid c/composition) {})
     :angular-momentum        (or (ecs/get-component world eid c/angular-momentum) [0.0 0.0 0.0])
     :rotation-axis           (or (ecs/get-component world eid c/rotation-axis) [0.0 0.0 1.0])
     :oblateness              (ecs/get-component world eid c/oblateness)
     :surface-gravity         (surface-gravity mass radius)
     :core-dynamo?            (core-dynamo? mclass spin)
     :magnetic-field          (or (ecs/get-component world eid c/b-field) [0.0 0.0 0.0])
     :formation-events        (formation-events-for world eid)}))

(defn- system-settled?
  "Proxy for parent §2 criterion 3 (\"no unresolved catastrophic collisions
   pending\"): true when no entity currently carries `c/absorb-merge` — an
   in-flight, not-yet-folded collision merge. This is a snapshot proxy, like
   the Phase 2 orbit-stability check, not a 10 Myr forward lookahead."
  [world]
  (empty? (ecs/entities-with world c/absorb-merge)))

(defn handoff-system
  "Double-buffer write-set system: SOLE writer of `c/planet-candidate` (M5
   handoff Phase 4). Jacobi fan-out emitter — reads the frozen snapshot only.

   Emission is gated on the FULL parent §2 criteria: a `:star` (not merely a
   `:protostar`) exists (`stable-star`), at least one body clears every
   per-planet test in the §2 table (`eligible-candidate?`), and the system
   is not mid-collision (`system-settled?`). When all three hold, every
   currently-eligible candidate's full `:planet-candidate` record
   (`build-candidate-record`) is written this tick. When any one is false,
   the write-set is `{}` — candidates already recorded on a prior tick are
   left untouched (persist), never retracted.

   The ledger `:event/phase0-handoff` append is a SEPARATE serial step
   (`domain.genesis.tick/emit-handoff-event`) that reacts to this system's
   output after the fold — see this namespace's Phase 4 section docstring
   for why events are never dispatched from inside a write-set `:run`."
  []
    {:id     :handoff
    :writes #{c/planet-candidate}
    :reads  #{c/matter-state c/mass c/composition c/position c/velocity
              c/radius c/luminosity c/material-class c/thermal-band
              c/orbit-stable c/atmosphere-class c/retained-species
              c/angular-momentum c/rotation-axis c/oblateness c/b-field
              c/spin c/absorb-merge c/volatile-budget c/differentiated-layers}
   :run
   (fn [world]
     (if-let [_primary (stable-star world)]
       (let [stars (stellar-bodies world)
             eids  (ecs/entities-with world c/material-class)
             ;; Multi-timescale card 4: eligibility and the candidate record
             ;; are evaluated against each body's OWN dominant attractor
             ;; (restricted to bodies whose parent has actually reached :star
             ;; — parent §2 criterion 1 reads "bound to the star"); the
             ;; system-level `stable-star` above only gates "a :star exists".
             body-parents (into {} (keep (fn [eid]
                                      (when-let [p (dominant-attractor world eid stars)]
                                        (when (= :star (:matter-state p))
                                          [eid p]))))
                           eids)
             eligible (filterv #(and (contains? body-parents %)
                                     (eligible-candidate? world (get body-parents %) %))
                               eids)]
         (if (and (seq eligible) (system-settled? world))
           {c/planet-candidate
            (into {} (map (fn [eid] [eid (build-candidate-record
                                          world (get body-parents eid) eid)])) eligible)}
           {}))
       {}))})
