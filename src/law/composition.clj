(ns law.composition
  "Contracts and schemas for chemical composition, from primordial BBN yields
   through stellar-processed material.

   Derived from: docs/research/cosmology/primordial-nucleosynthesis-yields.md
   The primordial composition of the universe is set by Big Bang Nucleosynthesis
   in the first ~180 seconds. These values are the INITIAL CONDITIONS for every
   gas parcel in Phase 0 — not arbitrary defaults.

   Units: mass fractions (X, Y, Z) unless noted. Sums to 1.0."
  (:require
   [law.contract :as contract]))

;; --- Primordial BBN yields (PDG 2025, Yeh+2026) ---
;; These are the validated values from the research notebook.
;; η₁₀ = 6.12 ± 0.038 (baryon-to-photon ratio × 10¹⁰)

(def ^:const primordial-H
  "Primordial hydrogen mass fraction X_p. H is the dominant baryonic species."
  0.754)

(def ^:const primordial-He
  "Primordial helium-4 mass fraction Y_p. Set by n/p freeze-out ratio.
   Observed: 0.2458 ± 0.0013 (Yeh+2026). Predicted: 0.247."
  0.246)

(def ^:const primordial-D
  "Primordial deuterium mass fraction. D/H ≈ 2.53e-5 by number.
   Mass fraction ≈ 2 × D/H × (m_H/m_He) correction ≈ 5.0e-6.
   D is destroyed at T > 10⁶ K — every star that forms destroys its D."
  5.0e-6)

(def ^:const primordial-He3
  "Primordial helium-3 mass fraction. He3/H ≤ 1.1e-5 by number.
   Mass fraction ≈ 1.5e-6."
  1.5e-6)

(def ^:const primordial-Li7
  "Primordial lithium-7 mass fraction. Li7/H ≈ 1.58e-10 by number (observed).
   BBN predicts ~5.0e-10 — the factor-of-3 'lithium problem'.
   We use the OBSERVED value: the simulation matches reality, not the
   theoretical prediction. The deviation is an open question in physics,
   not something we need to resolve here.

   Conversion: Y_Li7 = (n_Li7/n_H) × (m_Li7/m_H) × X_H
             = 1.58e-10 × 7 × 0.754 ≈ 8.3e-10"
  8.3e-10)

(def ^:const primordial-metals
  "Primordial metal mass fraction. BBN produces no metals (Z > 2).
   All metals are stellar-processed."
  0.0)

;; --- Baryon density (PDG 2025 + Yeh+2026) ---

(def ^:const eta-10
  "Baryon-to-photon ratio × 10¹⁰. Combined BBN+CMB constraint."
  6.12)

(def ^:const omega-b-h2
  "Baryon density parameter Ω_b h². From Planck CMB + BBN."
  0.02236)

;; --- Validation predicates ---

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
  "True if x is a valid trace isotope mass fraction: non-negative, finite.
   No upper bound — trace species can be arbitrarily small."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (>= (double x) 0.0)))

;; --- Composition schemas ---

(def composition-schema
  "Chemical composition of a gas parcel or body. Values are mass fractions.
   Required: :H, :He, :metals. Optional trace isotopes: :D, :He3, :Li7.
   Constraint: H + He + metals + traces ≤ 1.0 (enforced by validator, not schema)."
  {:H      positive-mass-fraction?
   :He     positive-mass-fraction?
   :metals mass-fraction?})

(def composition-trace-schema
  "Extended composition including trace isotopes from BBN.
   Same as composition-schema plus optional :D, :He3, :Li7."
  (assoc composition-schema
         :D   trace-mass-fraction?
         :He3 trace-mass-fraction?
         :Li7 trace-mass-fraction?))

(def primordial-composition
  "The canonical primordial BBN composition for a gas parcel at the start
   of Phase 0. Replaces arbitrary defaults like {:H 0.70 :He 0.28}.
   Li7 uses observed value (not BBN prediction) — see primordial-Li7 docstring."
  {:H       primordial-H
   :He      primordial-He
   :D       primordial-D
   :He3     primordial-He3
   :Li7     primordial-Li7
   :metals  primordial-metals})

;; --- Composition validators ---

(defn composition-sums-to-unity?
  "True if the composition's mass fractions sum to approximately 1.0.
   Allows 1% tolerance for floating-point accumulation."
  [c]
  (let [sum (+ (double (:H c 0.0))
               (double (:He comp 0.0))
               (double (:metals comp 0.0))
               (double (:D comp 0.0))
               (double (:He3 comp 0.0))
               (double (:Li7 comp 0.0)))]
    (< (Math/abs (- sum 1.0)) 0.01)))

(defn primordial-composition?
  "True if comp matches primordial BBN values within 10% tolerance.
   Useful for detecting whether a parcel has been stellar-processed."
  [c]
  (and (< (Math/abs (- (double (:H c 0.0)) primordial-H)) (* 0.1 primordial-H))
       (< (Math/abs (- (double (:He c 0.0)) primordial-He)) (* 0.1 primordial-He))
       (< (double (:metals c 0.0)) 1e-4)))

(defn metallicity
  "Compute Z (total metal mass fraction) from a composition map.
   Z = 1 - X(H) - Y(He) for standard astrophysical notation."
  [composition]
  (- 1.0
     (double (:H composition 0.0))
     (double (:He composition 0.0))))

;; --- Contracts ---

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
    :schema   (assoc composition-trace-schema
                     ;; Override validators for primordial-specific checks
                     :metals #(and (mass-fraction? %) (< (double %) 1e-4)))
    :name     "Primordial BBN Composition"
    :description "Gas parcel with primordial Big Bang nucleosynthesis yields."}))
