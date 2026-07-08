(ns domain.planet-formation.composition
  "Planet composition, material density, and type classification for the
   core-accretion seeder."
  (:require
   [law.composition :as lcomp]))

(defn planet-type
  "Classify a seeded planet by location and solid surface density.
     :terrestrial  — inside snow line, rocky
     :ice-giant    — beyond snow line, moderate mass
     :gas-giant    — beyond snow line, runaway gas capture possible"
  [r _sigma-solid snow-line mass-solar]
  (let [beyond? (> (double r) (double snow-line))]
    (cond
      (and beyond? (> mass-solar 0.3)) :gas-giant
      beyond?                          :ice-giant
      :else                            :terrestrial)))

(defn planet-composition
  "Return a plausible explicit-element composition map for a planet type.
   Mass fractions are normalized to sum to 1.0."
  [ptype]
  (lcomp/normalize
   (case ptype
     :terrestrial {:H 0.05 :He 0.001 :O 0.25 :C 0.005 :N 0.005
                   :Mg 0.15 :Si 0.16 :Al 0.02 :Ca 0.03 :Na 0.01
                   :S 0.005 :Fe 0.30 :Ni 0.02}
     :ice-giant   {:H 0.15 :He 0.05 :O 0.40 :C 0.05 :N 0.05
                   :Mg 0.06 :Si 0.08 :Al 0.01 :Ca 0.01 :Na 0.005
                   :S 0.005 :Fe 0.05 :Ni 0.005}
     :gas-giant   {:H 0.70 :He 0.28 :O 0.005 :C 0.005
                   :Fe 0.005 :Ni 0.005}
     lcomp/primordial-composition)))

(defn planet-material-density-by-type
  "Mean material density (kg/m³) for a planet type."
  [ptype]
  (case ptype
    :terrestrial 5.0e3
    :ice-giant   1.6e3
    :gas-giant   1.0e3
    1.0e3))
