(ns law.field.schema
  "Field/regime schemas and contracts."
  (:require
   [clojure.math :as math]
   [malli.core :as m]
   [law.contract :as contract]))

;; --- Numerical bounds -------------------------------------------------------

(def ^:const max-b-field 1.0e3)
;; Tesla. Far above any nebular/stellar-core field; a value past this is a
;; numerical blow-up (the "no unbounded amplification per tick" contract), not
;; physics. Interstellar fields are ~1e-9 T; even amplified cores stay ≪ 1 T.

;; --- Predicates -------------------------------------------------------------

(defn finite-number?
  "True if x is a finite double."
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

(def regime-tags
  "The closed set of regime tags the classifier may emit for Phase 0."
  #{:gravity-hydro :mhd-dominated :gravitationally-unstable
    :radiation-dominated :convective :stable-disc :unstable-no-fragment :tectonically-dead})

(def disc-regime-tags
  "Regime tags specific to rotationally-supported discs (Part 3)."
  #{:stable-disc :gravitationally-unstable :unstable-no-fragment})

(defn regime-tag?
  "True if k is a recognised Phase 0 regime tag."
  [k] (contains? regime-tags k))

(defn hydro-em-active?
  "Matter states that participate in SPH hydro and MHD-lite EM pair loops:
   diffuse nebular gas and contracting protostars."
  [state]
  (contains? #{:nebula :protostar} state))

(defn- double-array?
  "True if `x` is a primitive double array."
  [x]
  (= (class x) (class (double-array 0))))

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
  "Malli schema for one entry of the persistent `c/neighbor-cache` component
   shared by SPH hydro and MHD-lite EM. Neighbor maps must include :r2, the squared
   distance from the central particle, so consumers can filter without
   recomputing distance. :anchor-position is where the neighbor set was last
   actually queried and :query-r the radius that query covered — the reuse
   skin is measured against them. :nn-id (optional) remembers the nearest
   neighbor's identity so the refresh path can rederive the smoothing length
   without a tree descent."
  [:map
   [:position [:tuple :double :double :double]]
   [:anchor-position [:tuple :double :double :double]]
   [:query-r [:and :double [:> 0]]]
   [:h [:and :double [:> 0]]]
   [:neighbors [:vector [:map]]]
   [:gradients {:optional true} [:vector [:tuple :double :double :double]]]
   [:curl-gradients {:optional true} [:vector [:tuple :double :double :double]]]])

(def neighbor-cache-entry?
  "Predicate: does `value` satisfy the neighbor-cache entry schema?"
  (m/validator neighbor-cache-entry-schema))

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
   [:vz [:fn double-array?]]
   ;; Drift-predicted positions x̂ = x + (v + Σa·dt + Σdv)·dt — where the force
   ;; emitted this tick will actually land next tick (Jacobi force alignment).
   [:px-pred {:optional true} [:fn double-array?]]
   [:py-pred {:optional true} [:fn double-array?]]
   [:pz-pred {:optional true} [:fn double-array?]]])

(def physics-soa?
  "Predicate: does `value` satisfy `law.field/physics-soa-schema`?"
  (m/validator physics-soa-schema))

(def toomre-q-schema
  "Toomre Q parameter for a disc annulus: a positive finite number."
  finite-number?)

(def cool-dyn-ratio-schema
  "Cooling-time to dynamical-time ratio t_cool / Ω⁻¹: a positive finite number."
  finite-number?)

(def gas-sample-schema
  "Malli schema for one render-facing SPH gas sample as produced by
   domain.hydro/gas-samples (SI units). :smoothing-h is the full kernel
   support h (= 2 × particle radius, the SPH convention used throughout
   domain.hydro); :density is the SPH density the tick's density pass wrote,
   in kg/m³. :temperature may be absent for entities that never received one."
  [:map
   [:eid :int]
   [:position [:tuple :double :double :double]]
   [:smoothing-h [:and :double [:> 0]]]
   [:density [:and :double [:> 0]]]
   [:temperature {:optional true} [:maybe :double]]
   [:ionization [:and :double [:>= 0.0] [:<= 1.0]]]
   [:matter-state [:fn hydro-em-active?]]
   [:disc-tag {:optional true} [:maybe [:enum :disc :envelope :outflow]]]
   [:composition {:optional true} [:maybe :map]]
   [:solid-fraction {:optional true} [:and :double [:>= 0.0] [:<= 1.0]]]])

(def gas-sample?
  "Predicate: does `value` satisfy `law.field/gas-sample-schema`?"
  (m/validator gas-sample-schema))

;; --- Contract ---------------------------------------------------------------

(def field-cell-contract
  (contract/->contract
   {:id          :law.field/field-cell
    :shape-id    :law.field/field-cell
    :kind        :quality
    :schema      field-cell-schema
    :name        "Field Cell"
    :description "Magnetic field and dominant-physics regime of a resolved cell."}))
