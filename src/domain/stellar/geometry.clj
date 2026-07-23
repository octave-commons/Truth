(ns domain.stellar.geometry
  "Shape, compactness, and equation of state for stellar bodies.
   Owns the body's radius, density, oblateness, and rotation-axis as a single
   geometric fact (the Structure owner), plus the ideal-gas pressure derivation
   that follows from density and temperature."
  (:require
   [clojure.math :as math] [law.stellar                  :as law]
   [law.field                    :as lf]
   [domain.stellar.thermodynamics :as thermo]
   [domain.ecs.core              :as ecs]
   [domain.ecs.components        :as c]
   [domain.ecs.parallel          :as par]
   [domain.hydro                 :as hydro]
   [domain.profile               :as profile]))

(def ^:const debris-material-density 2.0e3) ;; kg/m³ — rocky planetesimal

(def ^:const planet-material-density 1.0e3) ;; kg/m³ — mixed rock/ice/volatile

(defn sphere-radius
  "Radius of a uniform sphere of `mass` at material `density`: r = (3m/4πρ)^(1/3)."
  [mass density]
  (math/pow (/ (* 3.0 (double mass)) (* 4.0 math/PI (double density))) (/ 1.0 3.0)))

(defn resolved-shape
  "Shape + compactness for a RESOLVED body, by matter-state. Solids are
   incompressible (fixed material density → radius follows mass); cores contract
   on the Kelvin–Helmholtz timescale toward the main-sequence radius floor,
   flattening under their own angular momentum. Returns a map of the components
   to write (a subset of radius/density/oblateness/rotation-axis)."
  [{:keys [matter-state mass radius oblateness angular-momentum]}
   collapse-fraction contraction-time dt]
  (let [m (double (or mass 0.0))]
    (case matter-state
      :planetesimal (let [r (sphere-radius m debris-material-density)]
                      {:radius r :density debris-material-density})
      (:gas-giant :brown-dwarf :planet)
      (let [r (sphere-radius m planet-material-density)]
        {:radius r :density planet-material-density})
      :stellar-remnant
      (let [r (law/white-dwarf-radius m)
            rho (/ m (* (/ 4.0 3.0) math/PI (math/pow r 3)))]
        {:radius r :density rho})
      (:protostar :star :condensed-core)
      (let [L     (or angular-momentum [0.0 0.0 0.0])
            o     (double (or oblateness 1.0))
            a     (double (or radius (sphere-radius m planet-material-density)))
            frac  (min (double collapse-fraction)
                       (- 1.0 (math/exp (- (/ (double dt) (double contraction-time))))))
            floor (law/main-sequence-radius m)
            {:keys [equatorial-radius polar-radius] :as shape}
            (thermo/oblate-collapse-shape {:mass m
                                           :angular-momentum L
                                           :equatorial-radius a
                                           :oblateness o
                                           :collapse-fraction frac
                                           :floor floor})]
        {:radius        equatorial-radius
         :density       (thermo/oblate-density m equatorial-radius polar-radius)
         :oblateness    (:oblateness shape)
         :rotation-axis (:rotation-axis shape)})
      nil)))

(defn- gas-structure-ws
  "Compute the gas-branch structure write-set: SPH density + adaptive radius."
  [world]
  (let [profile? (:genesis/profile-subsystems? world)
        [gas-results dt-query]
        (if profile?
          (profile/timing #(hydro/gas-structure world))
          [(hydro/gas-structure world) 0])]
    (profile/profile-section
     world :structure/gas-reduce
     (fn [_world]
       (reduce (fn [ws [eid rho r]]
                 (if (and (lf/finite-number? rho) (pos? rho)
                          (lf/finite-number? r) (pos? r))
                   (-> ws (assoc-in [c/density eid] rho)
                       (assoc-in [c/radius eid] r))
                   ws))
               (if profile?
                 {:genesis/_profile {:structure/gas-query (double dt-query)}}
                 {})
               gas-results)))))

(defn- resolved-eids
  "Collect resolved body ids from the matter-state component map."
  [world]
  (let [ms-map (get-in world [:components c/matter-state] {})
        mass-map (get-in world [:components c/mass] {})
        rad-map (get-in world [:components c/radius] {})]
    (persistent!
     (reduce-kv (fn [acc eid st]
                  (if (and (#{:planetesimal :gas-giant :brown-dwarf :planet :condensed-core :protostar :star :stellar-remnant} st)
                           (contains? mass-map eid)
                           (contains? rad-map eid))
                    (conj! acc eid)
                    acc))
                (transient [])
                ms-map))))

(defn- resolved-shape-entry
  "Project a resolved body's new shape into a write-set entry."
  [world eid cf ct dt]
  [eid (resolved-shape (thermo/entity->region world eid) cf ct dt)])

(defn- apply-shape
  "Merge one resolved shape into a write-set."
  [ws [eid s]]
  (if s
    (cond-> ws
      (:radius s)        (assoc-in [c/radius eid] (:radius s))
      (:density s)       (assoc-in [c/density eid] (:density s))
      (:oblateness s)    (assoc-in [c/oblateness eid] (:oblateness s))
      (:rotation-axis s) (assoc-in [c/rotation-axis eid] (:rotation-axis s)))
    ws))

(defn- resolved-structure-ws
  "Compute the resolved-body structure write-set and merge with `gas-ws`."
  [world gas-ws cf ct dt]
  (profile/profile-section
   world :structure/resolved
   (fn [_world]
     (let [eids (resolved-eids world)
           shapes (par/par-mapv #(resolved-shape-entry world % cf ct dt) eids)]
       (reduce apply-shape gas-ws shapes)))))

(defn structure-system
  "Double-buffer write-set system: SOLE writer of the body's shape and the
   compactness it implies — radius, density, and (for cores) oblateness +
   thermo/rotation-axis. Computed per matter-state (design note §7b):
     :nebula           SPH density + adaptive smoothing radius (fluid sample)
     :planetesimal / :gas-giant / :brown-dwarf / :planet fixed material density → radius from mass (solid)
     :stellar-remnant  degenerate white-dwarf scale radius, no contraction
     :protostar/:star  KH oblate contraction toward the main-sequence floor
    Replaces the radius/density writes of density-system, jeans-collapse, and
    collapse. The future home of the voxel shape representation.

    The gas branch reads the shared pair walk's staleness-budgeted
    `:density-estimate` from `c/neighbor-cache` (law.field/density-stale-*
    knobs) instead of re-walking the neighbor set."
  []
  {:id     :structure
   :reads  #{c/matter-state c/mass c/radius c/density c/position c/temperature
             c/pressure c/oblateness c/angular-momentum c/neighbor-cache}
   :writes #{c/radius c/density c/oblateness c/rotation-axis}
   :run    (fn [world]
             (let [cf (:genesis/collapse-fraction world 0.5)
                   ct (:genesis/contraction-time world 9.5e14)
                   dt (:sim/dt world 1.0e12)]
               (resolved-structure-ws world (gas-structure-ws world) cf ct dt)))})

(defn eos-system
  "Double-buffer write-set system: pressure as the pure equation of state
   P = ρ k_B T / m_H (`law/ideal-gas-pressure`) for every body carrying density
   and temperature. Sole writer of pressure — the single-writer replacement for
   the four legacy systems (density / jeans-collapse / collapse / thermal) that
   each recomputed this identical ideal-gas pressure. Reads ρ and T from the
   frozen snapshot (one-tick latency, negligible for a derived quantity)."
  []
  {:id     :eos
   :writes #{c/pressure}
   :run    (fn [world]
             (let [eids (ecs/entities-with world c/density c/temperature)]
               {c/pressure
                (into {}
                      (keep (fn [eid]
                              (let [rho (ecs/get-component world eid c/density)
                                    t   (ecs/get-component world eid c/temperature)]
                                (when (and rho t)
                                  [eid (law/ideal-gas-pressure rho t)]))))
                      eids)}))})
