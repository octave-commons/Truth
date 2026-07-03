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
   [malli.core :as m]
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
         (<= (math/sqrt (+ (* x x) (* y y) (* z z))) max-b-field))))

(defn- vec3-len2
  "Squared magnitude of a 3-vector of doubles."
  [v]
  (let [[x y z] (map double v)]
    (+ (* x x) (* y y) (* z z))))

(def regime-tags
  "The closed set of regime tags the classifier may emit for Phase 0."
  #{:gravity-hydro :mhd-dominated :gravitationally-unstable
    :radiation-dominated :convective :stable-disc :unstable-no-fragment :tectonically-dead})

(def disc-regime-tags
  "Regime tags specific to rotationally-supported discs (Part 3)."
  #{:stable-disc :gravitationally-unstable :unstable-no-fragment})

(defn regime-tag? [k] (contains? regime-tags k))

;; --- Physics participation predicates ----------------------------------------

(defn hydro-em-active?
  "Matter states that participate in SPH hydro and MHD-lite EM pair loops:
   diffuse nebular gas and contracting protostars."
  [state]
  (contains? #{:nebula :protostar} state))

;; --- Plasma / MHD helpers ---------------------------------------------------

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

(def neighbor-cache-entry-schema
  "Malli schema for one entry of the transient :genesis/neighbor-cache shared by
   SPH hydro and MHD-lite EM. Neighbor maps must include :r2, the squared
   distance from the central particle, so consumers can filter without
   recomputing distance."
  [:map
   [:position [:tuple :double :double :double]]
   [:h [:and :double [:> 0]]]
   [:neighbors [:vector [:map]]]
   [:gradients [:vector [:tuple :double :double :double]]]
   [:curl-gradients [:vector [:tuple :double :double :double]]]])

(def neighbor-cache-entry?
  "Predicate: does `value` satisfy the neighbor-cache entry schema?"
  (m/validator neighbor-cache-entry-schema))

(defn- double-array?
  "True if `x` is a primitive double array."
  [x]
  (= (class x) (class (double-array 0))))

(def physics-soa-schema
  "Malli schema for the transient `:genesis/physics-soa` Structure-of-Arrays
   cache. The cache holds only the fields the gravity and kinematics hot paths
   read: entity ids, count, and primitive double arrays for mass, radius,
   position, and velocity. Missing optional values are stored as 0.0."
  [:map
   [:eids [:vector :int]]
   [:n :int]
   [:mass [:fn double-array?]]
   [:radius [:fn double-array?]]
   [:px [:fn double-array?]]
   [:py [:fn double-array?]]
   [:pz [:fn double-array?]]
   [:vx [:fn double-array?]]
   [:vy [:fn double-array?]]
   [:vz [:fn double-array?]]])

(def physics-soa?
  "Predicate: does `value` satisfy `law.field/physics-soa-schema`?"
  (m/validator physics-soa-schema))

(def toomre-q-schema
  "Toomre Q parameter for a disc annulus: a positive finite number."
  finite-number?)

(def cool-dyn-ratio-schema
  "Cooling-time to dynamical-time ratio t_cool / Ω⁻¹: a positive finite number."
  finite-number?)

;; --- Contracts --------------------------------------------------------------

(def field-cell-contract
  (contract/->contract
   {:id          ::field-cell
    :shape-id    ::field-cell
    :kind        :quality
    :schema      field-cell-schema
    :name        "Field Cell"
    :description "Magnetic field and dominant-physics regime of a resolved cell."}))
