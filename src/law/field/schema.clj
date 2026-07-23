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
   without a tree descent. Optional :density-estimate/:density-anchor/
   :density-tick carry the staleness-budgeted SPH density the shared pair walk
   computes (law.field/density-stale-* budget knobs); per-neighbor maps may
   carry :grad, the pair kernel gradient ∇W_ij at h_ij = r_i + r_j, precomputed
   once per in-kernel pair for the merged hydro/EM consumer."
  [:map
   [:position [:tuple :double :double :double]]
   [:anchor-position [:tuple :double :double :double]]
   [:query-r [:and :double [:> 0]]]
   [:h [:and :double [:> 0]]]
   [:neighbors [:vector [:map]]]
   [:gradients {:optional true} [:vector [:tuple :double :double :double]]]
   [:curl-gradients {:optional true} [:vector [:tuple :double :double :double]]]
   [:density-estimate {:optional true} [:and :double [:>= 0]]]
   [:density-anchor {:optional true} [:tuple :double :double :double]]
   [:density-tick {:optional true} :int]
   [:density-h {:optional true} [:and :double [:> 0]]]
   [:density-m {:optional true} [:and :double [:>= 0]]]])

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

;; --- Dual-representation / focus zones (Phase 1) ----------------------------

(def field-zone-schema
  "Zone tag for the dual-representation fidelity of an entity."
  #{:immediate :regional :global})

(def statistical-cell-schema
  "Mass budget of a regional statistical cell: total mass, centre-of-mass
   velocity, specific angular momentum, mean thermodynamic state, and
   composition."
  [:map
   [:mass [:and :double [:> 0]]]
   [:velocity [:tuple :double :double :double]]
   [:angular-momentum [:tuple :double :double :double]]
   [:mean-b [:tuple :double :double :double]]
   [:temperature [:and :double [:>= 0]]]
   [:composition [:map-of :keyword :double]]])

(def attention-shell-schema
  "Observer focus radii: immediate and regional attention shells."
  [:map
   [:immediate-r [:and :double [:> 0]]]
   [:regional-r [:and :double [:> 0]]]])

;; --- Promotion/demotion lifecycle markers (Player Focus, child A) -----------
;; The regional-cell substrate the focus-zone system (child B) will consume:
;; `spawn-request-promotion` seed specs, the `consumed-demote` reap marker, and
;; `promoted-from-cell` back-pointer. Schemas only — nothing ticks yet.

(def promotion-spawn-spec-schema
  "One seed spec carried by `c/spawn-request-promotion`, as
   `domain.stellar.seeder/spawn-clump` expects (spec §5), plus an optional
   `:extra-components` map applied to the spawned entity after materialization
   — used to stamp `c/promoted-from-cell` with the source cell's entity id."
  [:map
   [:position [:tuple :double :double :double]]
   [:velocity {:optional true} [:tuple :double :double :double]]
   [:mass [:and :double [:> 0]]]
   [:radius [:and :double [:> 0]]]
   [:temperature {:optional true} [:and :double [:>= 0]]]
   [:composition {:optional true} [:map-of :keyword :double]]
   [:matter-state {:optional true} :keyword]
   [:body-kind {:optional true} :keyword]
   [:angular-momentum {:optional true} [:tuple :double :double :double]]
   [:extra-components {:optional true} [:map-of :keyword :any]]])

(def promotion-spawn-spec?
  "Predicate: does `value` satisfy `law.field/promotion-spawn-spec-schema`?"
  (m/validator promotion-spawn-spec-schema))

(def consumed-demote-schema
  "The `c/consumed.demote` marker: a resolved body flagged for aggregation into
   its source cell and despawn at world-construction. A bare boolean flag, like
   the other `consumed.*` markers."
  :boolean)

(def consumed-demote?
  "Predicate: does `value` satisfy `law.field/consumed-demote-schema`?"
  (m/validator consumed-demote-schema))

(def promoted-from-cell-schema
  "The `c/promoted-from-cell` back-pointer: a non-negative entity id (int) of
   the regional cell a promoted clump was sampled from."
  [:and :int [:>= 0]])

(def promoted-from-cell?
  "Predicate: does `value` satisfy `law.field/promoted-from-cell-schema`?"
  (m/validator promoted-from-cell-schema))

(def regional-cell-schema
  "An ECS regional cell entity: the statistical-mass ledger, the :regional
   field-zone tag, and a position — and, by construction, no `c/matter-state`
   key, so it stays structurally invisible to gravity/hydro/classifier/
   integrator (all of which filter on `c/matter-state`)."
  [:map
   [:statistical-mass statistical-cell-schema]
   [:field-zone [:= :regional]]
   [:position [:tuple :double :double :double]]])

(def regional-cell?
  "Predicate: does `value` satisfy `law.field/regional-cell-schema`?"
  (m/validator regional-cell-schema))

(defn promotion-invariant?
  "Return true if the promoted/demoted set conserves total mass, linear momentum,
   and angular momentum within relative `tol` (default 1e-6). `before` and `after`
   are collections of maps with :mass, :velocity, and :angular-momentum."
  ([before after] (promotion-invariant? before after 1e-6))
  ([before after tol]
   (letfn [(mass [coll] (reduce + 0.0 (map :mass coll)))
           (momentum [coll]
             (reduce (fn [v item] (mapv + v (mapv * (repeat (:mass item)) (:velocity item))))
                     [0.0 0.0 0.0] coll))
           (angmom [coll] (reduce (fn [v item] (mapv + v (:angular-momentum item)))
                                  [0.0 0.0 0.0] coll))
           (rel-close? [a b]
             (or (= a b)
                 (< (Math/abs (- a b))
                    (* (max (Math/abs a) (Math/abs b) 1.0) tol))))]
     (and (rel-close? (mass before) (mass after))
           (every? (fn [[a b]] (rel-close? a b)) (map vector (momentum before) (momentum after)))
           (every? (fn [[a b]] (rel-close? a b)) (map vector (angmom before) (angmom after)))))))

;; --- Contract ---------------------------------------------------------------

(def field-cell-contract
  (contract/->contract
   {:id          :law.field/field-cell
    :shape-id    :law.field/field-cell
    :kind        :quality
    :schema      field-cell-schema
    :name        "Field Cell"
    :description "Magnetic field and dominant-physics regime of a resolved cell."}))
