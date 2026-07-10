(ns law.field
  "Contracts, constants, and regime thresholds for the electromagnetic / MHD
   field layer of Phase 0.

   Units: SI throughout (see the design doc
   docs/designs/phase0-coupled-physics-and-regime-classifier.md). The research
   note is written in Gaussian/CGS — every formula here is the SI form, so the
   magnetic-pressure factor is 1/(2μ₀), NOT 1/8π, and the Alfvén speed uses
   √(μ₀ρ), NOT √(4πρ). This single convention is what keeps the numbers right."
  (:require
   [clojure.math :as math]
   [law.field.schema :as schema]))

;; --- Re-exports from schema -------------------------------------------------

(def max-b-field schema/max-b-field)
(def finite-number? schema/finite-number?)
(def finite-vec3? schema/finite-vec3?)
(def bounded-b-field? schema/bounded-b-field?)
(def regime-tags schema/regime-tags)
(def disc-regime-tags schema/disc-regime-tags)
(def regime-tag? schema/regime-tag?)
(def hydro-em-active? schema/hydro-em-active?)
(def field-cell-schema schema/field-cell-schema)
(def hydro-accel-schema schema/hydro-accel-schema)
(def magnetic-torque-schema schema/magnetic-torque-schema)
(def neighbor-cache-entry-schema schema/neighbor-cache-entry-schema)
(def neighbor-cache-entry? schema/neighbor-cache-entry?)
(def physics-soa-schema schema/physics-soa-schema)
(def physics-soa? schema/physics-soa?)
(def toomre-q-schema schema/toomre-q-schema)
(def cool-dyn-ratio-schema schema/cool-dyn-ratio-schema)
(def gas-sample-schema schema/gas-sample-schema)
(def gas-sample? schema/gas-sample?)
(def field-cell-contract schema/field-cell-contract)

;; --- Dual-representation / focus zones (Phase 1) ----------------------------

(def field-zone-schema schema/field-zone-schema)
(def statistical-cell-schema schema/statistical-cell-schema)
(def attention-shell-schema schema/attention-shell-schema)
(def promotion-invariant? schema/promotion-invariant?)

;; --- Physical constants -----------------------------------------------------

(def ^:const mu-0 1.25663706212e-6) ;; vacuum permeability, T·m/A (SI)
(def ^:const gamma 1.6666666666666667) ;; adiabatic index, 5/3 monatomic gas

;; --- Regime thresholds ------------------------------------------------------
;; The dimensionless boundaries the classifier uses. Tunable for play; asserted
;; in-range by tests rather than hard-coded inside loops.

(def ^:const beta-magnetized 1.0)
;; Plasma β below this → magnetic pressure dominates gas pressure.

(def ^:const alfven-mach-magnetized 1.0)
;; Alfvén-Mach below this → magnetic tension/pressure constrain the flow.

(def ^:const min-neighbors-for-curl 5)
;; Minimum neighbor count required to attempt an SPH curl estimate. Isolated
;; particles have too noisy a curl for the Lorentz force to be meaningful.

(def ^:const mach-supersonic 1.0)
;; Flow Mach above this → shocks, compressible turbulence.

(def ^:const jeans-unstable 1.0)
;; L/λ_J at or above this → gravitationally unstable, tends to collapse.

;; --- Plasma / MHD helpers ---------------------------------------------------

(defn- vec3-len2
  "Squared magnitude of a 3-vector of doubles."
  [v]
  (let [[x y z] (map double v)]
    (+ (* x x) (* y y) (* z z))))

(defn magnetic-pressure
  "Magnetic pressure P_B = |B|² / (2μ₀)  (SI). Pascals."
  [b-field]
  (if (finite-vec3? b-field)
    (/ (vec3-len2 b-field) (* 2.0 mu-0))
    0.0))

(defn plasma-beta
  "Plasma beta β = P_thermal / P_B. β < 1 means magnetic pressure dominates
   the thermal pressure. Returns +∞ when the field is zero."
  [pressure b-field]
  (let [pb (magnetic-pressure b-field)]
    (if (pos? pb)
      (/ (double pressure) pb)
      Double/POSITIVE_INFINITY)))

(defn alfven-speed
  "Alfvén speed v_A = |B| / √(μ₀ρ)  (SI). m/s. Zero field or zero density →
   zero speed."
  [b-field density]
  (if (and (finite-vec3? b-field) (pos? (double density)))
    (/ (math/sqrt (vec3-len2 b-field))
       (math/sqrt (* mu-0 (double density))))
    0.0))

(defn alfven-mach
  "Alfvén Mach number ℳ_A = v / v_A. ℳ_A < 1 means magnetic tension/pressure
   constrains the flow. Returns +∞ when v_A is zero."
  [velocity b-field density]
  (let [va (alfven-speed b-field density)
        v  (double (or velocity 0.0))]
    (if (pos? va)
      (/ v va)
      Double/POSITIVE_INFINITY)))

(defn mhd-regime?
  "True when magnetic pressure or tension is dynamically significant: either
   plasma beta is below `beta-magnetized` or the Alfvén Mach is below
   `alfven-mach-magnetized`."
  [pressure b-field velocity density]
  (or (< (plasma-beta pressure b-field) beta-magnetized)
      (< (alfven-mach velocity b-field density) alfven-mach-magnetized)))

(defn lorentz-acceleration-cap
  "Cap on Lorentz acceleration magnitude: |a_L| ≤ v_A² / R  (SI). m/s².
   Returns the scalar cap, or 0.0 when the radius is non-positive."
  [b-field density radius]
  (let [va (alfven-speed b-field density)
        r  (double (or radius 1.0))]
    (if (pos? r)
      (/ (* va va) r)
      0.0)))

;; --- Disc-regime thresholds (Part 3) ----------------------------------------

(def ^:const toomre-q-stable 1.0)
;; Toomre Q = c_s Ω / (π G Σ). Q > 1 ⇒ gravitationally stable against axisymmetric
;; perturbations; Q < 1 ⇒ the disc is unstable and can fragment.

(def ^:const cooling-dynamical-ratio-fast 3.0)
;; Gammie (2001): fragmentation requires t_cool < 3 Ω⁻¹. If cooling is slower,
;; the disc heats up and stabilizes even when Q < 1.
