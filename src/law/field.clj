(ns law.field
  "Contracts, constants, and regime thresholds for the electromagnetic / MHD
   field layer of Phase 0.

   Units: SI throughout (see the design doc
   docs/designs/phase0-coupled-physics-and-regime-classifier.md). The research
   note is written in Gaussian/CGS — every formula here is the SI form, so the
   magnetic-pressure factor is 1/(2μ₀), NOT 1/8π, and the Alfvén speed uses
   √(μ₀ρ), NOT √(4πρ). This single convention is what keeps the numbers right."
  (:require
   [law.contract :as contract]))

;; --- Physical constants -----------------------------------------------------

(def ^:const mu-0 1.25663706212e-6) ;; vacuum permeability, T·m/A (SI)
(def ^:const gamma 1.6666666666666667) ;; adiabatic index, 5/3 monatomic gas

;; --- Numerical bounds -------------------------------------------------------

(def ^:const max-b-field 1.0e3)
;; Tesla. Far above any nebular/stellar-core field; a value past this is a
;; numerical blow-up (the "no unbounded amplification per tick" contract), not
;; physics. Interstellar fields are ~1e-9 T; even amplified cores stay ≪ 1 T.

;; --- Regime thresholds ------------------------------------------------------
;; The dimensionless boundaries the classifier uses. Tunable for play; asserted
;; in-range by tests rather than hard-coded inside loops.

(def ^:const beta-magnetized 1.0)
;; Plasma β below this → magnetic pressure dominates gas pressure.

(def ^:const alfven-mach-magnetized 1.0)
;; Alfvén-Mach below this → magnetic tension/pressure constrain the flow.

(def ^:const mach-supersonic 1.0)
;; Flow Mach above this → shocks, compressible turbulence.

(def ^:const jeans-unstable 1.0)
;; L/λ_J at or above this → gravitationally unstable, tends to collapse.

;; --- Predicates -------------------------------------------------------------

(defn finite-number?
  [x]
  (and (number? x) (Double/isFinite (double x))))

(defn finite-vec3?
  "A 3-vector of finite numbers — the shape every field vector must satisfy."
  [v]
  (and (sequential? v)
       (= 3 (count v))
       (every? finite-number? v)))

(defn bounded-b-field?
  "True if every component is finite and the magnitude is within bounds — the
   invariant that no induction/flux-freezing step has run the field away."
  [v]
  (and (finite-vec3? v)
       (let [[x y z] (map double v)]
         (<= (Math/sqrt (+ (* x x) (* y y) (* z z))) max-b-field))))

(def regime-tags
  "The closed set of regime tags the classifier may emit for Phase 0."
  #{:gravity-hydro :mhd-dominated :gravitationally-unstable
    :radiation-dominated :convective :stable-disc :tectonically-dead})

(defn regime-tag? [k] (contains? regime-tags k))

;; --- Schemas ----------------------------------------------------------------

(def field-cell-schema
  "Field state carried by a resolved cell/clump alongside its matter state."
  {:b-field bounded-b-field?
   :regime  regime-tag?})

(def hydro-accel-schema
  "Pressure-gradient acceleration vector a = -∇p/ρ in m/s²."
  finite-vec3?)

(def magnetic-torque-schema
  "Torque density vector τ = r × f from the Lorentz force, in N/m²."
  finite-vec3?)

;; --- Contracts --------------------------------------------------------------

(def field-cell-contract
  (contract/->contract
   {:id          ::field-cell
    :shape-id    ::field-cell
    :kind        :quality
    :schema      field-cell-schema
    :name        "Field Cell"
    :description "Magnetic field and dominant-physics regime of a resolved cell."}))
