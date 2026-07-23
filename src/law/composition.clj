(ns law.composition
  "Contracts and schemas for chemical composition, from primordial BBN yields
   through stellar-processed material.

   Derived from: docs/research/physics/nebular-chemistry-metal-enrichment.md
   and docs/research/cosmology/primordial-nucleosynthesis-yields.md

   Units: mass fractions unless noted. Sums to 1.0.
   The `:metals` lump is retired; composition is element-resolved.",
  (:require
   [law.contract :as contract]))

;; --- Element inventory ------------------------------------------------------

(def ^:const element-set
  "The explicit element set tracked by mass fraction in Phase 0.
   Molecules (H2O, CO2, NH3, CH4, volatiles, ices, silicates) are derived
   categories, not independent composition entries."
  #{:H :He :D :He3 :Li7 :C :N :O :Ne :Na :Mg :Al :Si :S :Ca :Fe :Ni})

(def ^:const gas-giants
  "Elements that remain predominantly gaseous at nebular temperatures."
  #{:H :He :D :He3 :Ne})

(def ^:const rock-formers
  "Elements that condense into rocky/silicate material."
  #{:Mg :Al :Si :Ca :Fe :Ni :Na :S})

(def ^:const ice-formers
  "Elements that condense into water, ammonia, methane ices."
  #{:C :N :O})

;; --- Primordial BBN yields (PDG 2025, Yeh+2026) -----------------------------

(def ^:const primordial-H
  "Primordial hydrogen mass fraction X_p."
  0.754)

(def ^:const primordial-He
  "Primordial helium-4 mass fraction Y_p."
  0.246)

(def ^:const primordial-D
  "Primordial deuterium mass fraction. D/H ≈ 2.53e-5 by number."
  5.0e-6)

(def ^:const primordial-He3
  "Primordial helium-3 mass fraction. He3/H ≤ 1.1e-5 by number."
  1.5e-6)

(def ^:const primordial-Li7
  "Primordial lithium-7 mass fraction (observed, not BBN prediction)."
  8.3e-10)

;; --- Population-I / solar composition (Asplund+2009) -------------------------

(def ^:const solar-composition
  "Solar (Population-I) mass fractions from Asplund+2009.
   Metallicity Z ≈ 0.0167. H and He sum to ~0.9833."
  {:H   0.7346
   :He  0.2485
   :O   5.92e-3
   :C   2.40e-3
   :Ne  1.76e-3
   :Fe  1.30e-3
   :N   6.96e-4
   :Si  6.51e-4
   :Mg  5.78e-4
   :S   4.42e-4
   :Al  4.90e-5
   :Ca  6.20e-5
   :Na  3.30e-5
   :Ni  2.70e-5})

(def ^:const solar-metallicity
  "Total metal mass fraction Z for the tracked solar composition.
   Slightly below the canonical Z≈0.0167 because the explicit table omits
   trace species such as P, Ti, Cr, Mn."
  (- 1.0 (:H solar-composition) (:He solar-composition)))

;; --- Baryon density (PDG 2025 + Yeh+2026) -----------------------------------

(def ^:const eta-10
  "Baryon-to-photon ratio × 10¹⁰. Combined BBN+CMB constraint."
  6.12)

(def ^:const omega-b-h2
  "Baryon density parameter Ω_b h². From Planck CMB + BBN."
  0.02236)

;; --- Canonical composition maps ---------------------------------------------

(def primordial-composition
  "The canonical primordial BBN composition for a gas parcel at the start
   of Phase 0. Metals are absent; heavy elements appear only through enrichment."
  {:H   primordial-H
   :He  primordial-He
   :D   primordial-D
   :He3 primordial-He3
   :Li7 primordial-Li7})

(def metallicity-presets
  "World-creation `:genesis/metallicity` presets → cloud-floor composition map.
   `:population-i` is an enriched present-day cloud (solar, Z≈0.0167);
   `:primordial` is a first-generation, metal-free cloud (BBN yields)."
  {:population-i solar-composition
   :primordial   primordial-composition})

(defn metallicity-preset->composition
  "Resolve a `:genesis/metallicity` preset keyword to a cloud-floor composition
   map. Defaults to Population-I (solar) — a present-day star-forming cloud is
   already enriched by prior stellar generations, so metals must exist from tick
   0 for solids (and therefore planets) to condense."
  [preset]
  (get metallicity-presets (or preset :population-i) solar-composition))

;; --- Validation predicates --------------------------------------------------

(defn mass-fraction?
  "True if x is a valid mass fraction: finite, non-negative, ≤ 1.0."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (>= (double x) 0.0)
       (<= (double x) 1.0)))

(defn positive-mass-fraction?
  "True if x is a positive mass fraction (> 0, ≤ 1)."
  [x]
  (and (mass-fraction? x) (pos? (double x))))

(defn trace-mass-fraction?
  "True if x is a valid trace isotope mass fraction: non-negative, finite."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (>= (double x) 0.0)))

;; --- Composition schemas ----------------------------------------------------

(def composition-schema
  "Chemical composition of a gas parcel or body. Values are mass fractions.
   :H and :He are required; all other elements in `element-set` are optional
   trace/metal fields. Mass fractions must sum to ≈ 1.0 (validator below)."
  (into {:H  positive-mass-fraction?
         :He positive-mass-fraction?}
        (for [e element-set] [e mass-fraction?])))

(def primordial-composition-schema
  "Composition schema for primordial gas: no heavy elements beyond Li7."
  (into {:H   positive-mass-fraction?
         :He  positive-mass-fraction?
         :D   trace-mass-fraction?
         :He3 trace-mass-fraction?
         :Li7 trace-mass-fraction?}
        (for [e (disj element-set :H :He :D :He3 :Li7)]
          [e trace-mass-fraction?])))

;; --- Derived composition helpers --------------------------------------------

(defn normalize
  "Normalize a composition map so its values sum to 1.0.
   Preserves zero entries. Safe for empty maps (returns empty)."
  [composition]
  (let [total (double (reduce + 0.0 (vals composition)))]
    (if (pos? total)
      (reduce-kv (fn [m k v] (assoc m k (/ (double v) total))) {} composition)
      {})))

(defn metallicity
  "Compute Z (total metal mass fraction) from an explicit composition map.
   Z = 1 - X(H) - Y(He)."
  [composition]
  (- 1.0
     (double (:H composition 0.0))
     (double (:He composition 0.0))))

(defn composition-sums-to-unity?
  "True if the composition's mass fractions sum to approximately 1.0.
   Allows 1% tolerance for floating-point accumulation."
  [c]
  (let [sum (double (reduce + 0.0 (vals c)))]
    (< (abs (- sum 1.0)) 0.01)))

(defn primordial-composition?
  "True if comp matches primordial BBN values within 10% tolerance for H/He
   and has no significant metals (Z < 1e-4)."
  [c]
  (and (< (abs (- (double (:H c 0.0)) primordial-H)) (* 0.1 primordial-H))
       (< (abs (- (double (:He c 0.0)) primordial-He)) (* 0.1 primordial-He))
       (< (double (metallicity c)) 1e-4)))

;; --- Condensation sequence (Lodders 2003) -----------------------------------

(def ^:const condensation-width
  "Sigmoid width ΔT (K) of the condensation transition used by
   `domain.chemistry/solid-fraction` (nebular-chemistry spec §6.1, decision
   §10.3). Condensation is grain nucleation and growth spread over a
   temperature interval, not a cliff at Tc: laboratory and nebular-analog
   condensates appear over tens of kelvin around the 50% point
   (docs/research/physics/nebular-chemistry-metal-enrichment.md §4.3). A
   constant absolute width (not Tc-relative) keeps the transition sharp for
   refractories and correctly wide for the most volatile species, whose low
   Tc would otherwise compress the interval to nothing."
  30.0)

(def condensation-temperatures
  "50% condensation temperatures (K) for elements from a solar-composition gas
   at 10⁻⁴ bar, following Lodders (2003). Used to derive `c/comp-condensed`.
   Elements with no reliable entry default to 50 K (volatile)."
  {:H   20.0
   :He  4.0
   :D   20.0
   :He3 4.0
   :Li7 1200.0
   :C   90.0
   :N   90.0
   :O   170.0
   :Ne  20.0
   :Na  950.0
   :Mg  1340.0
   :Al  1650.0
   :Si  1310.0
   :S   650.0
   :Ca  1518.0
   :Fe  1357.0
   :Ni  1353.0})

;; --- Contracts --------------------------------------------------------------

(def composition-contract
  (contract/->contract
   {:id       ::composition
    :shape-id ::chemical-state
    :kind     :type
    :schema   composition-schema
    :name     "Chemical Composition"
    :description "Mass-fraction chemical composition of a gas parcel or body."}))

(def primordial-composition-contract
  (contract/->contract
   {:id       ::primordial-composition
    :shape-id ::chemical-state
    :kind     :quality
    :schema   primordial-composition-schema
    :name     "Primordial BBN Composition"
    :description "Gas parcel with primordial Big Bang nucleosynthesis yields."}))
