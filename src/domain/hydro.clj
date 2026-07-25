(ns domain.hydro
  "Facade for the SPH hydrodynamic subsystems.

   Hydrodynamics on the N-body clump substrate. The clumps are Lagrangian gas
   parcels; each particle is a moving sample of a continuous fluid field. Density
   is computed with the standard SPH sum ρ_i = Σ_j m_j W(r_ij, h), and pressure
   gradients are estimated with the antisymmetric SPH pressure-gradient formula:

       a_i = − Σ_j m_j (P_i/ρ_i² + P_j/ρ_j²) ∇_i W(r_ij, h_ij)

   The cubic-spline (M4) kernel is used; the formulation conserves linear and
   angular momentum exactly because the pairwise force is antisymmetric.
   Pure data transformation; no IO."
  (:require
   [clojure.math :as math]
   [law.field :as lf]
   [domain.chemistry :as chemistry]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.hydro.kernel :as kernel]
   [domain.hydro.density :as density]
   [domain.hydro.pressure :as pressure]))

;; ---------------------------------------------------------------------------
;; Kernel primitives (domain.hydro.kernel)
;; ---------------------------------------------------------------------------

(def cubic-spline-w
  "Dimensionless cubic spline (M4) kernel W(q)."
  kernel/cubic-spline-w)

(def kernel-shape
  "Dimensionless M4 cubic-spline falloff w(r², h) ∈ [0, 1]."
  kernel/kernel-shape)

(def kernel-r2
  "Cubic-spline SPH kernel W(r²,h) in 3D."
  kernel/kernel-r2)

(def kernel
  "Cubic-spline SPH kernel W(r,h) in 3D."
  kernel/kernel)

(def kernel-gradient
  "Gradient ∇_i W(r_ij, h) of the cubic spline kernel."
  kernel/kernel-gradient)

(def pressure-term
  "Symmetric SPH pressure term P_i/ρ_i² + P_j/ρ_j²."
  kernel/pressure-term)

;; ---------------------------------------------------------------------------
;; Density pass (domain.hydro.density)
;; ---------------------------------------------------------------------------

(def sph-density
  "SPH density estimate ρ_i = Σ_j m_j W(r_ij, h)."
  density/sph-density)

(def sph-density-from-cache
  "SPH density computed directly from a neighbor-cache entry."
  density/sph-density-from-cache)

(def smoothing-length-from-dist
  "Geometric SPH smoothing length given nearest-neighbour distance."
  density/smoothing-length-from-dist)

(def density-system
  "SPH density pass: compute ρ_i for every `:nebula` particle."
  density/density-system)

(def gas-structure
  "Gas branch of the Structure owner: returns `[[eid density radius] ...]`."
  density/gas-structure)

;; ---------------------------------------------------------------------------
;; Pressure gradient (domain.hydro.pressure)
;; ---------------------------------------------------------------------------

(def pressure-gradient-acceleration
  "SPH pressure-gradient acceleration for one particle."
  pressure/pressure-gradient-acceleration)

(def hydro-system
  "Compute pressure-gradient acceleration and store it on `c/hydro-accel`."
  pressure/hydro-system)

(def pressure-acceleration
  "Write-set system: pressure-gradient acceleration → `accel.pressure`."
  pressure/pressure-acceleration)

;; ---------------------------------------------------------------------------
;; Shared utilities (domain.hydro.common)
;; ---------------------------------------------------------------------------

;; ---------------------------------------------------------------------------
;; Facade-only utilities
;; ---------------------------------------------------------------------------

(defn sound-speed
  "Adiabatic sound speed c_s = √(γ P / ρ) for an ideal gas. m/s."
  [density pressure]
  (if (and (pos? (double density)) (pos? (double pressure)))
    (math/sqrt (/ (* lf/gamma (double pressure)) (double density)))
    0.0))

(defn gas-samples
  "Pure render-facing projection of the SPH gas field.

   For every hydro-active entity (`law.field/hydro-em-active?`: :nebula and
   :protostar) returns {:eid :position :smoothing-h :density :temperature
   :ionization :matter-state} in SI units — the shape
   `law.field/gas-sample-schema` describes. Reads the components the tick's
   density pass maintains: :smoothing-h is 2 × c/radius per the SPH
   convention (the density pass stores radius = h/2), :density is the SPH
   density it wrote. Performs no SPH sums, tree queries, or neighbor-cache
   reads, so it is cheap and safe to call from the render thread every
   frame."
  [world]
  (->> (ecs/entities-with world c/position c/matter-state)
       (keep (fn [eid]
               (let [state (ecs/get-component world eid c/matter-state)
                     temp  (ecs/get-component world eid c/temperature)
                     composition  (ecs/get-component world eid c/composition)]
                 (when (lf/hydro-em-active? state)
                   {:eid eid
                    :position (ecs/get-component world eid c/position)
                    :smoothing-h (* 2.0 (double (or (ecs/get-component world eid c/radius) 3.0e13)))
                    :density (double (or (ecs/get-component world eid c/density) 1e-18))
                    :temperature temp
                    :ionization (double (or (ecs/get-component world eid c/ionization-fraction) 0.0))
                    :matter-state state
                    :disc-tag (ecs/get-component world eid c/disc-tag)
                    :composition composition
                    :solid-fraction (let [cats (chemistry/bulk-categories (or composition {}) (double (or temp 10.0)))]
                                      (+ (double (:rock cats 0.0))
                                         (double (:metal cats 0.0))
                                         (double (:ice cats 0.0))))}))))
       vec))
