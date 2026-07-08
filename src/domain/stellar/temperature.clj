(ns domain.stellar.temperature
  "Temperature equilibrium and evolution for stellar bodies.
   Radiative heating from nearby stars, SED-aware heating of planets, and the
   temperature-system that owns the temperature component."
  (:require
   [clojure.math :as math] [law.stellar                  :as law]
   [law.sed                      :as lsed]
   [domain.stellar.thermodynamics :as thermo]
   [domain.ecs.core              :as ecs]
   [domain.ecs.components        :as c]
   [domain.ecs.parallel          :as par]
   [shape.spatial                :as sp]))

(defn irradiance-at
  "Radiative flux (W/m²) from a star of given luminosity at distance r."
  [luminosity r]
  (if (pos? r)
    (/ (double luminosity) (* 4.0 math/PI r r))
    0.0))

(defn radiation-equilibrium-temperature
  "Equilibrium temperature (K) of a grey-body at distance r from a star with
   the given luminosity, assuming a moderate albedo."
  [luminosity r]
  (let [S (irradiance-at luminosity r)]
    (if (pos? S)
      (math/pow (/ (* 0.7 S) (* 4.0 law/stefan-boltzmann)) 0.25)
      0.0)))

(defn radiation-heating-delta
  "Temperature rise (K) over dt for a body heated by a nearby star."
  [{:keys [mass radius density]} luminosity r dt]
  (let [absorbed (* 0.7 (irradiance-at luminosity r) math/PI radius radius)
        specific-heat (* 2.5 law/k-B (/ 1.0 law/m-H))
        body-mass (or mass (* density (/ 4.0 3.0) math/PI (math/pow radius 3)))]
    (if (pos? body-mass)
      (/ (* absorbed dt) (* body-mass specific-heat))
      0.0)))

(defn sed-heating-delta
  "Temperature rise (K) over dt for a body heated by a star's SED bands.
   Uses vis+NIR for surface heating (climate) and XUV for upper-atmosphere
   heating. More physically accurate than bolometric heating for planets
   with atmospheres. Falls back to bolometric if bands are nil."
  [{:keys [mass radius density]} bands r dt]
  (let [body-mass (or mass (* density (/ 4.0 3.0) math/PI (math/pow radius 3)))
        specific-heat (* 2.5 law/k-B (/ 1.0 law/m-H))]
    (if-not (pos? body-mass)
      0.0
      (if (seq bands)
        ;; Band-specific: vis+NIR for surface, XUV for atmosphere
        (let [L-climate (lsed/climate-luminosity bands)
              L-xuv     (lsed/xuv-luminosity bands)
              ;; Climate heating: 70% absorbed by surface
              S-climate  (irradiance-at L-climate r)
              absorbed   (* 0.7 S-climate math/PI radius radius)
              ;; XUV heating: 90% absorbed by upper atmosphere (if any)
              S-xuv      (irradiance-at L-xuv r)
              xuv-absorbed (* 0.9 S-xuv math/PI radius radius)]
          (/ (* (+ absorbed xuv-absorbed) dt) (* body-mass specific-heat)))
        ;; Fallback: bolometric
        (radiation-heating-delta {:mass mass :radius radius :density density}
                                 (reduce + 0.0 (map (fn [[_ v]] (double v)) bands))
                                 r dt)))))

(defn- radiative-heat
  "Total temperature rise from all luminous stars for a single body."
  [region star-lums star-poss star-bands dt]
  (reduce (fn [acc [lum spos bands]]
            (if (pos? (double (or lum 0.0)))
              (let [dist (sp/dist (:position region) spos)]
                (+ acc (if bands
                         (sed-heating-delta region bands dist dt)
                         (radiation-heating-delta region lum dist dt))))
              acc))
          0.0 (map vector star-lums star-poss star-bands)))

(defn- body-temperature
  "Temperature for one body given its region and the star heating tables."
  [region state dt star-lums star-poss star-bands]
  (cond
    (and (#{:protostar :star} state) (:mass region) (:radius region))
    (thermo/virial-temperature (:mass region) (:radius region))

    (#{:planetesimal :gas-giant :brown-dwarf :planet} state)
    (let [star-heat (radiative-heat region star-lums star-poss star-bands dt)
          t (double (or (:temperature region) 3.0))
          drp (thermo/radiative-cooling-delta region dt)]
      (max 3.0 (- (+ t star-heat) drp)))

    (= :nebula state)
    (let [t (double (or (:temperature region) 3.0))
          drp (thermo/radiative-cooling-delta region dt)]
      (max 3.0 (- t drp)))

    :else nil))

(defn temperature-system
  "Double-buffer write-set system: SOLE writer of temperature.
     :protostar / :star  T = virial temperature G M m_H / (k_B R) — compression
                         (Kelvin–Helmholtz) heating that RISES as Structure
                         contracts the radius, carrying the core to ignition. A
                         pure derivation from mass + radius (no frozen reference).
      :planetesimal / :gas-giant / :brown-dwarf / :planet   radiative: cool toward the CMB, warmed by nearby stars.

     :nebula             skipped — diffuse gas stays at its seeded background.
   Replaces collapse's compression heating and the legacy thermal-system."
  [dt]
  {:id     :thermal
   :writes #{c/temperature}
   :run    (fn [world]
             (let [stars     (ecs/entities-with world c/matter-state c/luminosity c/position)
                   star-lums (mapv #(ecs/get-component world % c/luminosity) stars)
                   star-poss (mapv #(ecs/get-component world % c/position) stars)
                   star-bands (mapv #(some-> (ecs/get-component world % c/sed-bands)
                                             :bands)
                                    stars)
                   eids      (ecs/entities-with world c/matter-state c/temperature
                                                c/density c/radius c/mass c/position)
                   cells (par/par-mapv
                          (fn [eid]
                            (let [region (thermo/entity->region world eid)
                                  state  (:matter-state region)]
                              (when-let [t (body-temperature region state dt
                                                             star-lums star-poss star-bands)]
                                [eid t])))
                          eids)]
               {c/temperature (into {} (keep identity) cells)}))})
