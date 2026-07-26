(ns domain.planet-formation.composition
  "Planet composition, material density, and type classification for the
   core-accretion seeder."
  (:require
   [domain.chemistry :as chem]
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
  "Bulk composition of a planet seed formed from LOCAL disk material
   (nebular-chemistry spec §6.5, decision §9.1) — never a per-type lookup.

   The seed's refractory core is the disk's CONDENSED inventory at the local
   midplane temperature `disk-temperature` (the same `partition-solids`
   derivation that backs `c/comp-condensed`), restricted to ACCRETABLE
   condensate: the gas-formers H/He/D/He3/Ne never freeze into grains in a
   protoplanetary disk, and the sigmoid's finite ΔT would otherwise leak
   percent-level nebular hydrogen (~6.5% of H mass at 100 K) into every
   core — so they are
   excluded from the solid inventory (a captured envelope still carries them
   as gas). What remains is rock+metal inside the snow line, rock+metal+ice
   beyond it. A runaway giant additionally captures `gas-m` kg of the local
   nebular gas. `core-m`/`gas-m` (kg) mass-weight the blend, so the seeded
   planet's element budget is exactly the disk material it formed from —
   conservation holds by construction (`blend-compositions` normalizes).
   Seeds form at a single annulus, so no cross-radius weighting is needed;
   the split is core vs captured envelope.

   Degenerate case: a disk with zero condensables at `disk-temperature` and
   zero `gas-m` yields an empty map — unreachable through `planet-seeds`,
   whose viability guard requires positive solid surface density."
  [disk-composition disk-temperature core-m gas-m]
  (let [{:keys [solid gas]} (chem/partition-solids disk-composition disk-temperature)
        grains (lcomp/normalize
                (into {}
                      (remove (fn [[k _]] (contains? lcomp/gas-giants k)))
                      solid))]
    (chem/blend-compositions grains core-m gas gas-m)))

(defn planet-material-density-by-type
  "Mean material density (kg/m³) for a planet type."
  [ptype]
  (case ptype
    :terrestrial 5.0e3
    :ice-giant   1.6e3
    :gas-giant   1.0e3
    1.0e3))
