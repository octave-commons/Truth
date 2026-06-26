(ns domain.em
  "Electromagnetic / MHD-lite layer for Phase 0.

   The substrate is N-body: each resolved clump carries a single magnetic field
   vector (component `c/b-field`) rather than a grid of cells. So the field
   operations here are the per-body reductions of the full MHD equations:

     - flux freezing      : ideal induction under spherical compression,
                            B ∝ ρ^(2/3)  (equivalently B ∝ 1/r²).
     - magnetic pressure  : P_B = |B|² / (2μ₀), the support term that opposes
                            gravity in the momentum equation.
     - resistive decay    : the non-ideal η∇²B hook, reduced to dB/dt = -ηB/L².
                            Negligible in diffuse gas, real only in dense cores —
                            which is exactly where non-ideal MHD matters.

   All formulas are SI (see law.field). Pure data transformation; no IO."
  (:require
   [law.field         :as lf]
   [law.stellar       :as ls]
   [shape.spatial     :as sp]
   [domain.ecs.core   :as ecs]
   [domain.ecs.parallel :as par]
   [domain.ecs.components :as c]))

;; --- Pure field physics -----------------------------------------------------

(defn magnetic-pressure
  "Magnetic pressure of a field, P_B = |B|² / (2μ₀)  (SI). Pascals."
  [b-field]
  (if (lf/finite-vec3? b-field)
    (/ (sp/len2 b-field) (* 2.0 lf/mu-0))
    0.0))

(defn alfven-speed
  "Alfvén speed v_A = |B| / √(μ₀ρ)  (SI). m/s. Zero field → zero speed."
  [b-field density]
  (if (and (lf/finite-vec3? b-field) (pos? (double density)))
    (/ (sp/len b-field) (Math/sqrt (* lf/mu-0 (double density))))
    0.0))

(defn flux-freeze
  "Ideal induction under spherical compression: as a clump's density rises from
   `old-density` to `new-density`, frozen-in flux amplifies the field by
   (ρ'/ρ)^(2/3), preserving direction. Returns the new B vector.

   This is what makes a collapsing core's field grow — the visible EM signature
   of contraction. Capped at law.field/max-b-field so a runaway can't blow up."
  [b-field old-density new-density]
  (if (and (lf/finite-vec3? b-field)
           (pos? (double old-density))
           (pos? (double new-density)))
    (let [ratio  (Math/pow (/ (double new-density) (double old-density)) (/ 2.0 3.0))
          scaled (sp/v* b-field ratio)
          mag    (sp/len scaled)]
      (if (> mag lf/max-b-field)
        ;; clamp magnitude, keep direction; the (1 - ε) factor absorbs the
        ;; rounding of the rescale so the bounded-b-field? invariant holds.
        (sp/v* scaled (* (/ lf/max-b-field mag) (- 1.0 1e-12)))
        scaled))
    b-field))

(defn self-gravity-pressure
  "Central pressure of a self-gravitating uniform sphere, P ≈ GM²/((4/3π)R⁴).
   Duplicated from domain.stellar's formula to keep this namespace free of a
   dependency on the stellar domain (em is upstream of stellar)."
  [mass radius]
  (if (and (pos? (double mass)) (pos? (double radius)))
    (/ (* ls/G (double mass) (double mass))
       (* (/ 4.0 3.0) Math/PI (Math/pow (double radius) 4)))
    0.0))

(defn magnetically-supported?
  "True if magnetic pressure can hold a clump against its own self-gravity,
   i.e. P_B ≥ P_grav. Under flux freezing both scale as 1/r⁴, so this ratio is
   set at seeding by the clump's mass-to-flux — the magnetic critical-mass idea.
   A supported (sub-critical) clump resists collapse; an unsupported
   (super-critical) one falls in. Massive cores are strongly super-critical, so
   this correctly does NOT stop them — magnetic support matters for small,
   strongly-magnetized clumps, not for the central core."
  [{:keys [b-field mass radius]}]
  (and b-field mass radius
       (>= (magnetic-pressure b-field)
           (self-gravity-pressure mass radius))))

(defn resistive-decay
  "Non-ideal flux loss over dt: dB/dt = -ηB/L², with magnetic diffusivity η and
   length scale L≈radius. Reduced proxy for Ohmic dissipation / ambipolar
   diffusion. Effect ~ r²/η; enormous (→ no decay) in diffuse gas, finite only
   in dense compact cores. `eta` defaults to a small magnetic diffusivity."
  ([b-field radius dt] (resistive-decay b-field radius dt 1.0e8))
  ([b-field radius dt eta]
   (if (and (lf/finite-vec3? b-field) (pos? (double radius)))
     (let [r      (double radius)
           rate   (/ (double eta) (* r r))           ;; 1/s
           factor (Math/exp (- (* rate (double dt)))) ;; ∈ (0,1]
           factor (max 0.0 (min 1.0 factor))]
       (sp/v* b-field factor))
     b-field)))

;; --- Seeding ----------------------------------------------------------------

(def ^:const default-nebula-field 1.0e-9)
;; Tesla. Coherent large-scale nebular field, ~nT — the molecular-cloud range.

(defn seed-field
  "An initial magnetic field vector for a clump: a coherent large-scale field
   aligned with the nebula's rotation axis (z), the configuration observed in
   real molecular clouds where polarization maps show ordered fields roughly
   along the spin axis."
  ([] (seed-field default-nebula-field))
  ([magnitude] (sp/vec3 0.0 0.0 (double magnitude))))

;; --- ECS system -------------------------------------------------------------

(defn em-system
  "The EM tick step (non-ideal induction). Applies resistive flux decay to every
   field-bearing clump. Diffuse clumps keep their field essentially unchanged;
   dense cores slowly shed flux — the design's non-ideal hook. Flux-freezing
   amplification on contraction lives in domain.stellar/collapse-system, where
   the density change is known in the same step."
  [dt]
  (fn [world]
    (let [eids    (ecs/entities-with world c/b-field c/radius)
          updates (par/par-mapv
                   (fn [eid]
                     [eid (resistive-decay (ecs/get-component world eid c/b-field)
                                           (ecs/get-component world eid c/radius)
                                           dt)])
                   eids)]
      (reduce (fn [w [eid b]] (ecs/put-component w eid c/b-field b))
              world
              updates))))
