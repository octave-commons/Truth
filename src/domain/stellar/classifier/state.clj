(ns domain.stellar.classifier.state
  "The authentic matter-state formation state machine and the `:classifier`
   write-set system.

   Split out of the former `domain.stellar.classifier` on 2026-07-24 (see
   `kanban/tasks/static-analysis-split-classifier.md`): that namespace carried
   three ECS systems and 62 vars. This one owns the matter-state axis —
   complexity scoring, the Jeans/accretion/ignition ladder, and the
   `:classifier` emitter that is the sole writer of `c/matter-state` and
   `c/accretion-radius`. It depends on neither sibling."
  (:require
   [clojure.math                  :as math]
   [law.stellar                   :as law]
   [domain.stellar.thermodynamics :as thermo]
   [domain.stellar.collapse       :as collapse]
   [domain.stellar.sink           :as sink]
   [domain.ecs.core               :as ecs]
   [domain.ecs.components         :as c]
   [domain.profile                :as profile]))

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

;; Intentional: the two `:star` branches are two INDEPENDENT physical criteria
;; for stardom (self-sustaining fusion OR mass above the H-burning limit), which
;; is what the hysteresis docstring states. Collapsing them into one `or` would
;; hide that either criterion alone suffices. Splint is right that the branches
;; are redundant and wrong that the redundancy is a defect.
#_{:splint/disable [lint/identical-branches]}
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
