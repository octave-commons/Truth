(ns domain.stellar.seeder
  "Seed-and-grow condensation: :nebula parcels that would become :planetesimal
   spawn a small physical seed instead of promoting the whole parcel. Also owns
   the generic `spawn-clump` / `seed-clump` helpers used by world construction and
   lifecycle materialization."
  (:require
   [law.stellar                   :as law]
   [law.composition               :as lcomp]
   [domain.em                     :as em]
   [domain.ecs.core               :as ecs]
   [domain.ecs.components         :as c]
   [domain.stellar.thermodynamics :as thermo]
   [domain.stellar.structure      :as structure]
   [domain.stellar.collapse       :as collapse]
   [domain.stellar.classifier.state :as cls-state]
   [domain.stellar.sink           :as sink]
   [domain.stellar.disc           :as disc]
   [domain.spatial.index          :as spatial]
   [shape.spatial                 :as sp]
   [domain.planet-formation       :as pf]))

(def ^:private zero3 [0.0 0.0 0.0])

(def default-composition
  "Fallback composition for a spawned parcel that inherits none from its source.
   Population-I (solar) so the explicit element set carries metals — a metal-free
   fallback would zero out solid surface density and block planet seeding. Bodies
   normally carry their accreted composition; this is only the last-resort default."
  lcomp/solar-composition)

(defn seed-clump
  "Return the component map for one nebular clump entity. Carries a magnetic
   field vector (defaulting to the coherent large-scale nebular field) so the
   EM layer and regime classifier have field state from the first tick."
  [{:keys [position velocity mass radius temperature composition matter-state
           body-kind b-field angular-momentum]
    :or   {velocity [0.0 0.0 0.0]
           temperature 10.0
           composition default-composition
           matter-state :nebula
           body-kind :body/gas}}]
  (let [density (thermo/body-density mass radius)
        L (or angular-momentum (thermo/orbital-angular-momentum mass position velocity))
        spin (thermo/spin-from-angular-momentum L mass radius)
        resolved? (not= matter-state :nebula)]
    (cond-> {c/position     position
             c/velocity     velocity
             c/mass         mass
             c/radius       radius
             c/body-kind    body-kind
             c/temperature  temperature
             c/density      density
             c/pressure     (law/ideal-gas-pressure density temperature)
             c/composition  composition
             c/luminosity   0.0
             c/b-field      (or b-field (em/seed-field))
             c/matter-state matter-state
             c/angular-momentum L
             c/spin         spin
             c/oblateness   1.0
             c/rotation-axis (thermo/rotation-axis L)}
      resolved? (assoc c/accretion-radius radius))))

(defn spawn-clump
  "Spawn one nebular clump entity from a seed spec. Returns [world eid]."
  [world spec]
  (let [[w eid] (ecs/spawn world)]
    [(ecs/put-components w eid (seed-clump spec)) eid]))

(defn- condensation-candidate?
  "True when a :nebula parcel in the rotationally-supported disk is
   Jeans-unstable but not dense enough to form a hydrostatic core. These
   parcels form planetesimals via seed-and-grow instead of collapsing to a
   :condensed-core."
  [world eid _gas-mass zones]
  (and (= :nebula (ecs/get-component world eid c/matter-state))
       (not (ecs/get-component world eid c/condensation-seeded))
       (disc/in-disc? world eid)
       (< (double (or (ecs/get-component world eid c/mass) 0.0)) law/opacity-limit-mass)
       (let [region (thermo/entity->region world eid)]
         (and (collapse/jeans-unstable? region)
              (< (double (or (:density region) 0.0)) cls-state/core-condensation-density)
              (not (sink/within-existing-sink? (:position region) zones))))))

(defn- local-density-max?
  "True when `eid` is at least as dense as all other :nebula parcels within
   `radius-factor * gas-r`."
  [world eid gas-r radius-factor]
  (let [rho (double (or (ecs/get-component world eid c/density) 0.0))
        pos (ecs/get-component world eid c/position)
        r (* radius-factor gas-r)
        nbrs (spatial/query-within-radius world pos r #(= :nebula (:matter-state %)))]
    (every? #(>= rho (double (or (:density %) 0.0)))
            (remove #(= (:id %) eid) nbrs))))

(defn- seed-spec
  "Build the spawn spec for a condensation seed displaced from `eid`."
  [world eid seed-mass seed-r]
  (let [pos (or (ecs/get-component world eid c/position) zero3)
        v (or (ecs/get-component world eid c/velocity) zero3)
        parent-r (double (or (ecs/get-component world eid c/radius) 0.0))
        composition (or (ecs/get-component world eid c/composition) default-composition)
        temp (double (or (ecs/get-component world eid c/temperature) 10.0))
        dir-raw [(double (mod (* (long eid) 2654435761) 1000003))
                 (double (mod (* (long eid) 2654435761 7) 1000003))
                 (double (mod (* (long eid) 2654435761 13) 1000003))]
        dir (let [l (sp/len dir-raw)]
              (if (pos? l) (sp/v* dir-raw (/ 1.0 l)) [1.0 0.0 0.0]))
        offset (* 1.1 (+ parent-r seed-r))]
    {:position (sp/v+ pos (sp/v* dir offset))
     :velocity v
     :mass seed-mass
     :radius seed-r
     :matter-state :planetesimal
     :body-kind :body/rocky
     :composition composition
     :temperature temp}))

(defn- seeder-write-set
  "Fold selected candidates into a write-set with spawn requests, mass debits
   and one-shot markers."
  [world selected seed-mass seed-r]
  (reduce (fn [ws eid]
            (let [spec (seed-spec world eid seed-mass seed-r)]
              (-> ws
                  (update-in [c/spawn-request-condense eid] (fnil conj []) spec)
                  (assoc-in [c/mass-flux-condense eid] (- (double seed-mass)))
                  (assoc-in [c/condensation-seeded eid] true))))
          {} selected))

(defn condensation-seeder-system
  "Fan-out emitter: when a :nebula parcel would condense to :planetesimal,
   instead spawn a small physical seed and debit that mass from the parent parcel.
   Gated by `cls-state/condense-tick?`, a one-shot `c/condensation-seeded` marker per parcel,
   a local-density-maximum filter, and a per-tick seed cap. The parent parcel stays
   :nebula; the seed materializes next tick and becomes a resolved sink. Growth
   after seeding is collisional / rare BHL capture, not a runaway channel."
  []
  {:id     :condensation-seeder
   :ns     'domain.stellar.seeder
   :reads  #{c/matter-state c/mass c/density c/position c/velocity
             c/radius c/composition c/temperature c/condensation-seeded}
   :writes #{c/spawn-request-condense c/mass-flux-condense c/condensation-seeded}
   :run
   (fn [world]
     (when (cls-state/condense-tick? world)
       (let [gas-mass      (:genesis/gas-particle-mass world)
             zones         (sink/sink-exclusion-zones world)
             gas-r         (double (or (:genesis/gas-smoothing-radius world) 0.0))
             radius-factor (double (or (:genesis/condensation-local-radius-factor world) 2.0))
             max-seeds     (long (or (:genesis/max-condensation-seeds-per-tick world) 1))
             seed-mass     (pf/condensation-seed-mass world)
             seed-r        (structure/sphere-radius seed-mass structure/debris-material-density)
             eids          (ecs/entities-with world c/matter-state c/mass c/density
                                              c/position c/velocity c/radius c/composition c/temperature)
             candidates    (->> eids
                                (filter #(condensation-candidate? world % gas-mass zones))
                                (filterv #(local-density-max? world % gas-r radius-factor)))
             selected      (->> (sort-by #(double (or (ecs/get-component world % c/density) 0.0)) > candidates)
                                (take max-seeds))]
         (seeder-write-set world selected seed-mass seed-r))))})

