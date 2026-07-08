(ns domain.em.field
  "Magnetic field as a substrate for Phase 0.

   The substrate is N-body: each resolved clump carries a single magnetic field
   vector (component `c/b-field`) rather than a grid of cells. So the field
   operations here are the per-body reductions of the full MHD equations:

     - flux freezing      : ideal induction under spherical compression,
                             B ∝ ρ^(2/3)  (equivalently B ∝ 1/r²).
     - magnetic pressure  : P_B = |B|² / (2μ₀), the support term that opposes
                            gravity in the momentum equation.
     - resistive decay    : the non-ideal η∇²B hook, reduced to dB/dt = -ηB/L².
                            Negligible in diffuse gas, real only in dense cores.
     - dipole superposition : the field at any point in space is the sum of every
                              body's dipole field, so star-star/planet-star
                              interactions emerge from the sum rather than being
                              asserted.

   All formulas are SI (see law.field). Pure data transformation; no IO."
  (:require
   [clojure.math :as math]
   [law.field :as lf]
   [law.stellar :as ls]
   [domain.ecs.core :as ecs]
   [domain.ecs.parallel :as par]
   [domain.ecs.components :as c]
   [domain.profile :as profile]
   [shape.spatial :as sp]))

;; --- Pure field diagnostics -------------------------------------------------

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
    (/ (sp/len b-field) (math/sqrt (* lf/mu-0 (double density))))
    0.0))

;; --- The field as a FIELD: dipole superposition -----------------------------

(def ^:const mu0-over-4pi 1.0e-7) ;; μ₀/4π exactly (T·m/A)

(defn dipole-moment
  "Magnetic dipole moment m (A·m²) of a body whose surface field magnitude is
   |b-field| at radius `radius`, aligned with `b-field`. From the on-axis dipole
   relation B_pole = μ₀|m|/(2π R³) ⇒ m = (2π R³/μ₀)·B."
  [b-field radius]
  (let [r (double (or radius 0.0))]
    (if (and (lf/finite-vec3? b-field) (pos? r))
      (sp/v* b-field (/ (* 2.0 math/PI r r r) lf/mu-0))
      [0.0 0.0 0.0])))

(defn dipole-field-at
  "Field (T) produced at world point `p` by a dipole of moment `m` at `src`:
   B = (μ₀/4π)[3(m·d̂)d̂ − m]/|d|³, d = p − src. [0 0 0] at the singularity."
  [m src p]
  (let [d (sp/v- p src)
        r (sp/len d)]
    (if (pos? r)
      (let [rhat (sp/v* d (/ 1.0 r))
            mdot (sp/dot m rhat)
            term (sp/v- (sp/v* rhat (* 3.0 mdot)) m)]
        (sp/v* term (/ mu0-over-4pi (* r r r))))
      [0.0 0.0 0.0])))

(defn net-field-at
  "Superposed magnetic field at world point `p`: Σ over `sources` of each dipole's
   field, plus a uniform `background`. `sources` is a seq of {:moment :position}.
   THIS is the field bodies feel and that field-line tracing follows — the
   interactions between bodies' fields are this sum, emergent, not hard-coded."
  [p sources background]
  (reduce (fn [acc {:keys [moment position]}]
            (sp/v+ acc (dipole-field-at moment position p)))
          (or background [0.0 0.0 0.0])
          sources))

(defn external-field-at
  "Like `net-field-at` but EXCLUDING the source whose position equals `self-pos`
   (a body does not torque on its own field) — the field a body sees from all the
   OTHERS. Background is omitted (a uniform field exerts no net force, only torque
   handled separately)."
  [self-pos sources]
  (reduce (fn [acc {:keys [moment position]}]
            (if (= position self-pos)
              acc
              (sp/v+ acc (dipole-field-at moment position self-pos))))
          [0.0 0.0 0.0]
          sources))

(defn field-sources
  "Dipole sources from every resolved body (their fields are amplified enough to
   matter). Each → {:moment :position :eid}. Diffuse :nebula gas is left out as a
   source (its weak seeded field is the background), though it still carries B."
  [world]
  (->> (ecs/entities-with world c/b-field c/radius c/position c/matter-state)
       (filter #(not= :nebula (ecs/get-component world % c/matter-state)))
       (mapv (fn [eid]
               {:eid      eid
                :position (ecs/get-component world eid c/position)
                :radius   (double (or (ecs/get-component world eid c/radius) 0.0))
                :moment   (dipole-moment (ecs/get-component world eid c/b-field)
                                         (ecs/get-component world eid c/radius))}))))

;; --- Flux freezing and support ----------------------------------------------

(defn flux-freeze
  "Ideal induction under compression: as a clump's density rises from
   `old-density` to `new-density`, frozen-in flux amplifies the field.

   For isotropic spherical contraction the scaling is B ∝ ρ^(2/3). For collapse
   along field lines (flux-conserving in the perpendicular plane) B ∝ ρ. The
   `anisotropy` parameter interpolates: 0 = isotropic, 1 = fully along B.
   Direction is preserved. Capped at law.field/max-b-field."
  ([b-field old-density new-density]
   (flux-freeze b-field old-density new-density 0.0))
  ([b-field old-density new-density anisotropy]
   (if (and (lf/finite-vec3? b-field)
            (pos? (double old-density))
            (pos? (double new-density)))
     (let [a     (double (max 0.0 (min 1.0 (or anisotropy 0.0))))
           exp   (+ (* (- 1.0 a) (/ 2.0 3.0)) (* a 1.0))
           ratio (math/pow (/ (double new-density) (double old-density)) exp)
           scaled (sp/v* b-field ratio)
           mag    (sp/len scaled)]
       (if (> mag lf/max-b-field)
         (sp/v* scaled (* (/ lf/max-b-field mag) (- 1.0 1e-12)))
         scaled))
     b-field)))

(defn self-gravity-pressure
  "Central pressure of a self-gravitating uniform sphere, P ≈ GM²/((4/3π)R⁴).
   Duplicated from domain.stellar's formula to keep this namespace free of a
   dependency on the stellar domain (em is upstream of stellar)."
  [mass radius]
  (if (and (pos? (double mass)) (pos? (double radius)))
    (/ (* ls/G (double mass) (double mass))
       (* (/ 4.0 3.0) math/PI (math/pow (double radius) 4)))
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

(def ^:const min-flux-retention
  "Floor on the per-tick resistive-decay factor: a body keeps at least this much
   of its flux each tick. The decay timescale r²/η can fall below the (Myr-scale)
   `dt` for a compact core, which would annihilate the flux in a single step and
   defeat flux freezing — the same large-dt coupling hazard as the stellar wind.
   Capping the per-tick loss keeps the decay gentle and dt-robust, so flux
   freezing can amplify a contracting core's field (the physically observed
   outcome: protostars have strong, amplified fields)." 0.97)

(defn resistive-decay
  "Non-ideal flux loss over dt: dB/dt = -ηB/L², with magnetic diffusivity η and
   length scale L≈radius. Reduced proxy for Ohmic dissipation / ambipolar
   diffusion. Effect ~ r²/η: ≈no decay in diffuse gas, gentle in dense cores. The
   per-tick factor is floored at `min-flux-retention` so a large dt cannot wipe a
   compact core's flux in one step. `eta` is a small magnetic diffusivity tuned so
   flux freezing dominates during collapse (real cores retain & amplify field)."
  ([b-field radius dt] (resistive-decay b-field radius dt 1.0e4))
  ([b-field radius dt eta]
   (if (and (lf/finite-vec3? b-field) (pos? (double radius)))
     (let [r      (double radius)
           rate   (/ (double eta) (* r r))           ;; 1/s
           factor (math/exp (- (* rate (double dt)))) ;; ∈ (0,1]
           factor (max (double min-flux-retention) (min 1.0 factor))]
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

;; --- ECS field system -------------------------------------------------------

(def ^:private resolved-field-states
  "Matter states whose magnetic flux is frozen into the resolved body."
  #{:planetesimal :gas-giant :brown-dwarf :planet :protostar :star})

(defn- field-entity-data
  "Project one entity into the compact representation used by the field system."
  [world eid]
  (let [b-field (ecs/get-component world eid c/b-field)
        radius  (double (or (ecs/get-component world eid c/radius) 0.0))
        state   (ecs/get-component world eid c/matter-state)
        resolved? (contains? resolved-field-states state)]
    {:eid eid
     :b-field b-field
     :radius radius
     :resolved? resolved?
     :flux (when (and resolved? (lf/finite-vec3? b-field))
             (or (ecs/get-component world eid c/frozen-flux)
                 (sp/v* b-field (* radius radius))))}))

(defn- field-cell
  "Compute [eid b-field' frozen-flux'] for one entity under resistive decay."
  [dt {:keys [eid b-field radius resolved? flux]}]
  (when (and (lf/finite-vec3? b-field) (pos? radius))
    (if resolved?
      (let [flux'  (resistive-decay flux radius dt)
            inv-r2 (/ 1.0 (* radius radius))
            b'     (sp/v* flux' inv-r2)]
        (when (lf/bounded-b-field? b')
          [eid b' flux']))
      (let [b' (resistive-decay b-field radius dt)]
        (when (lf/bounded-b-field? b')
          [eid b' nil])))))

(defn field-system
  "Double-buffer write-set system: SOLE writer of b-field.

    Magnetic flux Φ = B·R² is frozen into a body when it condenses and conserved
    as it contracts, so B = Φ/R² amplifies as Structure shrinks the radius — ideal
    flux freezing (B ∝ 1/R² ∝ ρ^{2/3}) — while Φ itself decays by Ohmic/ambipolar
    resistivity (real only in dense cores). Diffuse :nebula gas keeps its seeded
    field with the same light resistive decay. Replaces collapse's flux-freezing
    and em's b-field decay; Φ (frozen-flux) is the reference that turns the
    amplification into a derivation from the radius Structure owns."
  [dt]
  (let [dt (double dt)]
    {:id     :field
     :writes #{c/b-field c/frozen-flux}
     :run    (fn [world]
               (profile/profile-sections
                world
                [[:field/scan
                  (fn [_w]
                    {:entities (par/par-mapv #(field-entity-data world %)
                                             (ecs/entities-with world c/b-field c/radius))})]
                 [:field/compute
                  (fn [{:keys [entities]}]
                    (let [cells (par/par-mapv #(field-cell dt %) entities)]
                      (reduce
                       (fn [ws cell]
                         (if-let [[eid b' flux'] cell]
                           (cond-> (assoc-in ws [c/b-field eid] b')
                             flux' (assoc-in [c/frozen-flux eid] flux'))
                           ws))
                       {}
                       cells)))]]))}))
