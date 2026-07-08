(ns domain.atmosphere
  "Planetary atmosphere physics over the ECS substrate. Currently: atmospheric
   escape under stellar XUV irradiation. This is ongoing planetary physics — it
   runs whenever a planet sits in a star's radiation field, at any point in the
   game — so it lives here rather than in the genesis formation loop.

   Pure data transformation; a snapshot-reading fan-out emitter (no IO)."
  (:require
   [domain.ecs.core       :as ecs]
   [domain.ecs.components :as c]
   [law.sed               :as lsed]
   [law.plasma            :as lplasma]
   [shape.spatial         :as sp]))

(def ^:private regime-mdot-factors
  "Mass-loss multipliers by escape regime."
  {:energy-limited 1.0
   :recombination-limited 0.6
   :blow-off 2.0})

(defn- star-data
  "Build a vector of star data maps for XUV escape calculations."
  [world]
  (->> (ecs/entities-with world c/matter-state c/luminosity c/position c/sed-bands)
       (mapv (fn [eid]
               {:eid eid
                :pos (ecs/get-component world eid c/position)
                :sed (ecs/get-component world eid c/sed-bands)}))))

(defn- nearest-star
  "Return the nearest star data map to `pos` from `stars`."
  [pos stars]
  (when (seq stars)
    (apply min-key #(sp/dist pos (:pos %)) stars)))

(defn- mdot-for-regime
  "Compute mass-loss rate for the given escape regime."
  [regime F-xuv R M]
  (let [base (lplasma/energy-limited-escape F-xuv R M 0.15)
        factor (get regime-mdot-factors regime 0.0)]
    (* base factor)))

(defn- xuv-escape-for
  "Compute `[eid dm escape-info]` for one planet, or nil if no valid star."
  [world dt stars eid]
  (let [pos (ecs/get-component world eid c/position)
        R (double (or (ecs/get-component world eid c/radius) 0.0))
        M (double (or (ecs/get-component world eid c/mass) 0.0))
        nearest (nearest-star pos stars)
        dist (when nearest (sp/dist pos (:pos nearest)))
        bands (when nearest (get-in nearest [:sed :bands]))]
    (when (and bands dist (pos? dist) (pos? R) (pos? M))
      (let [L-xuv (lsed/xuv-luminosity bands)
            F-xuv (lplasma/xuv-flux-at L-xuv dist)
            regime (lplasma/escape-regime F-xuv R)
            mdot (mdot-for-regime regime F-xuv R M)
            dm (min (* mdot dt) (* 0.01 M) (max 0.0 (- M 1.0e20)))]
        [eid dm {:regime regime :xuv-flux F-xuv :mass-loss-rate mdot}]))))

(defn- escape-result-maps
  "Build the mass-flux and atmosphere-escape component maps from `results`."
  [results]
  {c/mass-flux-xuv (into {} (keep (fn [[eid dm _]] (when (pos? dm) [eid (- dm)]))) results)
   c/atmosphere-escape (into {} (map (fn [[eid _ esc]] [eid esc])) results)})

(defn xuv-atmospheric-escape-system
  "Write-set emitter: planetary atmospheric escape under stellar XUV. For each
   :planet, find the nearest star's SED bands, compute the XUV flux at the
   planet's distance, the escape regime, and the mass-loss rate; emit the mass
   loss as :component/mass-flux.xuv (negative, the integrator owns mass) and the
   diagnostic :component/atmosphere-escape (its own column). A pure snapshot-
   reading fan-out emitter. Mass loss is clamped to ≤1% of M per tick and never
   below a 1e20 kg rocky core."
  []
  {:id     :xuv-atmospheric-escape
   :writes #{c/mass-flux-xuv c/atmosphere-escape}
   :run
   (fn [world]
     (let [dt (double (or (:sim/dt world) 1.0e12))
           stars (star-data world)
           planets (filterv #(= :planet (ecs/get-component world % c/matter-state))
                            (ecs/entities-with world c/matter-state c/mass c/radius c/position))
           results (keep #(xuv-escape-for world dt stars %) planets)]
       (escape-result-maps results)))})
