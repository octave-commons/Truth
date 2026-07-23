(ns law.chemistry
  "Contracts, schemas, and physical constants for gravitational differentiation
   and the volatile budget (kanban/tasks/phase-0-chemistry-differentiation-spec.md
   §5-§6, adapted to the element-resolved composition model — the spec's
   `:metals` lump is retired; bulk groups are derived from the element map via
   `domain.chemistry/material-groups`).

   Element vocabulary and condensation temperatures live in `law.composition`;
   this namespace adds only what differentiation/volatile-loss needs."
  (:require
   [malli.core :as m]
   [law.contract :as contract]))

;; --- Differentiation thresholds ----------------------------------------------

(def ^:const differentiation-malleability-min
  "Malleability above which a body is molten enough to differentiate by density
   (spec §3 table: m > 0.8 ⇒ molten). With law.stellar/malleability =
   clamp(T/1500 K) this is T > 1200 K."
  0.8)

(def ^:const differentiation-timescale
  "e-folding timescale (s) for density differentiation of a molten body,
   ≈ 1 Myr. Magma-ocean separation on planetesimal-to-planet bodies proceeds
   in ~10⁵–10⁶ yr (Elkins-Tanton 2012, ARAA 40:113 — magma-ocean cooling and
   cumulate overturn timescales); the Phase-0 Myr-scale dt resolves it as a
   bounded per-tick advance, never a one-tick snap."
  3.0e13)

;; --- Volatile blow-off thresholds (impact heating) ----------------------------
;; Justification: a collision's impact heating raises the survivor's bulk
;; temperature; past material-dependent thresholds the corresponding volatile
;; inventory is no longer gravitationally/thermally bound to a molten body and
;; is driven off (Ahrens 1993, Icarus 104:291 — impact-induced volatile loss;
;; Genda & Abe 2005, Nature 433:842 — atmospheric erosion scales with impact
;; energy past melting).

(def ^:const ice-volatile-loss-temperature
  "Post-impact temperature (K) above which the ice-volatile inventory (C, N,
   and oxygen not bound into silicate rock) is driven off a merged body.
   Water ice sublimates near 200 K in vacuum and refractory organics pyrolyze
   by ~700–900 K (Hayatsu & Anders 1981; Lodders 2003 puts S, the last
   moderately-volatile condensate, at Tc ≈ 650 K) — 700 K is the point where
   an ice/organic inventory cannot persist on an impact-heated body."
  700.0)

(def ^:const hhe-volatile-loss-temperature
  "Post-impact temperature (K) above which primordial H/He (H, He, D, He3, Ne)
   is blown off. Set at the silicate melt point (law.stellar/melt-temperature,
   1500 K): a body impact-heated into a magma ocean has a Jeans escape
   parameter ≪ 1 for H₂/He at planetesimal masses, so no primordial envelope
   survives (spec §3: H/He 'T_melt' ≈ 0 K — they are never bound to molten
   rock)."
  1500.0)

;; --- Schemas ------------------------------------------------------------------

(def malleability-schema
  "Malleability m ∈ [0,1]: 0 brittle/cold, 1 molten (spec §3)."
  [:double {:min 0.0 :max 1.0}])

(def material-group-schema
  "Disjoint bulk material groups derived from an element composition map
   (`domain.chemistry/material-groups`): each mass fraction in [0,1], groups
   summing to ≈ 1. `:organics` is C plus its CO/CO₂-bound oxygen; `:volatiles`
   is H/He/N/free oxygen (gas + ice inventory)."
  [:map
   [:volatiles [:double {:min 0.0 :max 1.0}]]
   [:silicates [:double {:min 0.0 :max 1.0}]]
   [:metals    [:double {:min 0.0 :max 1.0}]]
   [:organics  [:double {:min 0.0 :max 1.0}]]])

(def differentiated-layers-schema
  "Density-separated layer fractions of a differentiated body (spec §5,
   extended with `:degree`): `:core-fraction` + `:mantle-fraction` +
   `:volatile-fraction` always sum to 1.0 — the layers are a PARTITION of the
   body's mass, so total layer mass equals body mass exactly. `:degree` ∈ [0,1]
   is how far segregation has proceeded (0 = freshly molten/uniform,
   1 = fully differentiated); `:surface-composition` is the element map a
   viewer/handoff sees at the surface, interpolated from the bulk composition
   (degree 0) toward the volatile-layer composition (degree 1)."
  [:and
   [:map
    [:core-fraction      [:double {:min 0.0 :max 1.0}]]
    [:mantle-fraction    [:double {:min 0.0 :max 1.0}]]
    [:volatile-fraction  [:double {:min 0.0 :max 1.0}]]
    [:degree             [:double {:min 0.0 :max 1.0}]]
    [:surface-composition :map]]
   [:fn (fn [{:keys [core-fraction mantle-fraction volatile-fraction]}]
          (< (abs (- 1.0 (+ core-fraction mantle-fraction volatile-fraction)))
             1.0e-6))]])

(def volatile-budget-schema
  "Volatile inventory of a body in kg: the volatile-group mass fraction of the
   composition (H/He + ices + free oxygen + organics, single-counted) times the
   body mass. Feeds the M5 habitability handoff as `:volatile-budget-kg`."
  [:double {:min 0.0}])

(def differentiated-layers?
  "Predicate: does `value` satisfy `differentiated-layers-schema`?"
  (m/validator differentiated-layers-schema))

(def volatile-budget?
  "Predicate: does `value` satisfy `volatile-budget-schema`?"
  (m/validator volatile-budget-schema))

;; --- Contracts -----------------------------------------------------------------

(def differentiated-layers-contract
  (contract/->contract
   {:id       ::differentiated-layers
    :shape-id ::chemical-state
    :kind     :type
    :schema   differentiated-layers-schema
    :name     "Differentiated Layers"
    :description "Density-separated core/mantle/volatile layer partition of a molten body."}))

(def volatile-budget-contract
  (contract/->contract
   {:id       ::volatile-budget
    :shape-id ::chemical-state
    :kind     :type
    :schema   volatile-budget-schema
    :name     "Volatile Budget"
    :description "Volatile inventory (kg) a body carries toward the habitability handoff."}))
