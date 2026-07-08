(ns domain.em.magnetosphere
  "Magnetosphere coupling for Phase 0.

   Stellar wind and CME parcels carry ram pressure and B-field. When they reach
   a planet, they compress its magnetosphere. The standoff distance r_mp is where
   the planet's magnetic pressure equals the wind ram pressure: B²/(2μ₀) = P_ram.

   All formulas are SI (see law.field). Pure data transformation; no IO."
  (:require
   [clojure.math :as math]
   [law.field :as lf]
   [domain.ecs.core :as ecs]
   [domain.ecs.registry :as reg]
   [domain.ecs.components :as c]
   [domain.profile :as profile]
   [shape.spatial :as sp]))

(defn- magnetopause-distance
  "Standoff distance (m) where planetary magnetic pressure balances wind ram
   pressure: r_mp = R_p × (B_p² / (2μ₀ P_ram))^(1/6). Returns R_p when no wind."
  [planet-radius planet-b-field ram-pressure]
  (let [Rp   (double (or planet-radius 0.0))
        Bp   (double (or planet-b-field 0.0))
        Pram (double (or ram-pressure 0.0))]
    (if (and (pos? Rp) (pos? Bp) (pos? Pram))
      (* Rp (math/pow (/ (* Bp Bp) (* 2.0 lf/mu-0 Pram)) (/ 1.0 6.0)))
      Rp)))

(defn- wind-parcel?
  "A wind/CME parcel is an ionized nebula body."
  [world eid]
  (and (= :nebula (ecs/get-component world eid c/matter-state))
       (pos? (double (or (ecs/get-component world eid c/ionization-fraction) 0.0)))))

(defn- build-wind-data
  "Project wind-parcel eids into {:pos :ram} for magnetopause computations."
  [world eids]
  (mapv (fn [eid]
          {:pos (ecs/get-component world eid c/position)
           :ram (double (or (ecs/get-component world eid c/ram-pressure) 0.0))})
        eids))

(defn- magnetosphere-cell
  "Compute the new c/magnetosphere value for one planet, or nil if unchanged."
  [world eid wind-data]
  (let [pos    (ecs/get-component world eid c/position)
        Rp     (double (or (ecs/get-component world eid c/radius) 0.0))
        Bp     (double (or (some-> (ecs/get-component world eid c/b-field) sp/len) 0.0))
        cutoff (* 10.0 Rp)
        nearby-ram (reduce (fn [acc wd]
                             (if (< (sp/dist pos (:pos wd)) cutoff)
                               (+ acc (:ram wd))
                               acc))
                           0.0 wind-data)
        r-mp       (magnetopause-distance Rp Bp nearby-ram)
        compression (if (pos? Rp) (min 10.0 (/ Rp (max 1.0e3 r-mp))) 1.0)
        value {:standoff-distance r-mp
               :compression compression}]
    (when (not= value (ecs/get-component world eid c/magnetosphere))
      [eid value])))

(defn magnetosphere-coupling-system
  "Double-buffer write-set system: SOLE writer of c/magnetosphere. For each
   :planet, finds nearby ionized wind/CME parcels and computes magnetosphere
   compression — standoff distance + compression factor from the parcels'
   one-tick-stale ram pressure. A compressed magnetosphere (small standoff)
   means more atmospheric exposure. Emits only the cells that CHANGED. Each
   phase is profiled when `:genesis/profile-subsystems?` is enabled."
  []
  {:id     :magnetosphere-coupling
   :writes (reg/registry-writes :magnetosphere-coupling)
   :run
   (fn [world]
     (profile/profile-sections
      world
      [[:magnetosphere/filter-winds
        (fn [w]
          (filterv #(wind-parcel? w %)
                   (ecs/entities-with w c/matter-state c/position c/mass c/radius)))]
       [:magnetosphere/build-wind-data
        (fn [wind-parcels]
          (build-wind-data world wind-parcels))]
       [:magnetosphere/compute
        (fn [wind-data]
          (let [planets (filterv #(= :planet (ecs/get-component world % c/matter-state))
                                 (ecs/entities-with world c/matter-state c/position c/radius))]
            {c/magnetosphere
             (into {} (keep #(magnetosphere-cell world % wind-data)) planets)}))]]))})
