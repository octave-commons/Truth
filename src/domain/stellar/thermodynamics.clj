(ns domain.stellar.thermodynamics
  "Pure thermodynamic, angular-momentum, and region-projection helpers for
   stellar bodies.  These functions do not write ECS components; they are the
   physics primitives consumed by the stellar systems."
  (:require
   [clojure.math :as math] [law.stellar           :as law]
   [law.field             :as lf]
   [shape.spatial         :as sp]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components :as c]))

;; --- Pure thermodynamics ----------------------------------------------------

(defn body-density
  "Density of a uniform sphere of given mass and radius."
  [mass radius]
  (/ mass (* (/ 4.0 3.0) math/PI (math/pow radius 3))))

(defn moment-of-inertia
  "Moment of inertia I = (2/5) M R² for a uniform solid sphere. kg m²."
  [mass radius]
  (* 0.4 (double mass) (math/pow (double radius) 2)))

(defn orbital-angular-momentum
  "Orbital specific angular momentum L = m (r × v). Vector in kg m²/s."
  [mass position velocity]
  (sp/v* (sp/cross position velocity) (double mass)))

(defn spin-from-angular-momentum
  "Convert a body's total angular momentum vector to a spin vector ω = L/I,
   assuming the body rotates about L and is a uniform sphere of the given
   radius. Returns [ωx ωy ωz] rad/s."
  [angular-momentum mass radius]
  (let [I (moment-of-inertia mass radius)]
    (if (pos? I)
      (sp/v* angular-momentum (/ 1.0 I))
      [0.0 0.0 0.0])))

(defn oblateness-from-spin
  "Estimate polar/equatorial axis ratio c/a from spin magnitude ω.
   A non-rotating body is spherical (1.0). Fast spin flattens toward 0.
   This is a phenomenological estimate, not a full Maclaurin solution."
  [spin radius]
  (let [omega (sp/len spin)
        ;; Characteristic break-up angular velocity for a uniform sphere
        omega-max (if (pos? radius)
                    (math/sqrt (/ (* 8.0 law/G) (* 3.0 radius)))
                    0.0)
        x (if (pos? omega-max) (/ omega omega-max) 0.0)]
    (max 0.05 (min 1.0 (- 1.0 (* 0.45 x x))))))

(defn equivalent-radius
  "Mean radius of an oblate spheroid with equatorial radius a and polar radius
   c: r_eq = (a² c)^(1/3). Same volume as a sphere of radius r_eq."
  [a c]
  (if (and (pos? (double a)) (pos? (double c)))
    (math/pow (* (math/pow (double a) 2) (double c)) (/ 1.0 3.0))
    0.0))

(defn oblate-density
  "Density of a uniform oblate spheroid of mass M, equatorial radius a,
   polar radius c."
  [mass a c]
  (if (and (pos? (double mass)) (pos? (double a)) (pos? (double c)))
    (/ (double mass) (* (/ 4.0 3.0) math/PI (math/pow (double a) 2) (double c)))
    0.0))

(defn oblate-moment-of-inertia
  "Moment of inertia of a uniform oblate spheroid about its symmetry (spin)
   axis: I_z = (2/5) M a². kg m²."
  [mass a]
  (* 0.4 (double mass) (math/pow (double a) 2)))

(defn rotation-axis
  "Unit vector along angular-momentum vector L. Returns [0 0 1] when L is zero."
  [angular-momentum]
  (let [L (or angular-momentum [0.0 0.0 0.0])
        l (sp/len L)]
    (if (pos? l)
      (sp/v* L (/ 1.0 l))
      [0.0 0.0 1.0])))

(defn spin-from-angular-momentum-oblate
  "Spin vector from L for an oblate spheroid of equatorial radius a."
  [angular-momentum mass a]
  (let [I (oblate-moment-of-inertia mass a)]
    (if (pos? I)
      (sp/v* angular-momentum (/ 1.0 I))
      [0.0 0.0 0.0])))

(defn oblate-collapse-shape
  "Given a clump's `mass`, conserved `angular-momentum` L, current equatorial
   radius `equatorial-radius`, current `oblateness`, and `collapse-fraction`,
   return the new shape as
   {:equatorial-radius :polar-radius :oblateness :spin :rotation-axis}.

   Mass is conserved by shrinking the equivalent spherical radius
   r_eq = (a² c)^(1/3) by (1 - collapse-fraction), then solving for the new
   equatorial radius and oblateness self-consistently: spin depends on a,
   oblateness depends on spin, and a depends on oblateness via fixed volume.

   `floor` is a hard lower bound on the equivalent radius (the main-sequence
   radius for a star-forming core); the body contracts toward it and stops,
   instead of halving to a point every tick."
  [{:keys [mass angular-momentum equatorial-radius oblateness collapse-fraction floor]
    :or   {floor 0.0}}]
  (let [a       (double equatorial-radius)
        o       (double oblateness)
        c       (if (pos? o) (* a o) a)
        r-eq    (equivalent-radius a c)
        r-eq'   (max (double floor) (* r-eq (- 1.0 (double collapse-fraction))))
        axis    (rotation-axis angular-momentum)
         ;; iterative self-consistent solve for a' and o'
        [a' o' spin'] (loop [o-i (max 0.05 (min 1.0 (double o))) n 0]
                        (let [a-i    (/ r-eq' (math/pow o-i (/ 1.0 3.0)))
                              spin-i (spin-from-angular-momentum-oblate angular-momentum mass a-i)
                              o-next (max 0.05 (min 1.0 (oblateness-from-spin spin-i a-i)))]
                          (if (or (>= n 4) (< (abs (- o-next o-i)) 1e-6))
                            [a-i o-next spin-i]
                            (recur o-next (inc n)))))]
    {:equatorial-radius a'
     :polar-radius      (* a' o')
     :oblateness        o'
     :spin              spin'
     :rotation-axis     axis}))

(defn compression-heating
  "Adiabatic compression temperature: T ∝ ρ^(γ-1), γ = 5/3 for monatomic gas."
  [initial-temp initial-density final-density]
  (* initial-temp (math/pow (/ final-density initial-density) 0.667)))

(defn virial-temperature
  "Characteristic temperature of a self-gravitating gas sphere from the virial
   theorem: T ≈ G M m_H / (k_B R). As a collapsing core contracts (R shrinks),
   this rises — it is what carries a protostar toward ignition."
  [mass radius]
  (/ (* law/G mass law/m-H) (* law/k-B radius)))

(defn effective-temperature
  "Stellar effective temperature (K) from Stefan-Boltzmann: L = 4π R² σ T_eff⁴.
   T_eff = (L / (4π R² σ))^(1/4). Returns 0 for non-positive inputs."
  [luminosity radius]
  (let [L (double (or luminosity 0.0))
        R (double (or radius 0.0))]
    (if (and (pos? L) (pos? R))
      (math/pow (/ L (* 4.0 math/PI R R law/stefan-boltzmann)) 0.25)
      0.0)))

(defn self-gravity-pressure
  "Central pressure of a self-gravitating uniform sphere: P ≈ G M² / ((4/3π) R⁴).
   Rises steeply as the core contracts."
  [mass radius]
  (/ (* law/G mass mass) (* (/ 4.0 3.0) math/PI (math/pow radius 4))))

(defn radiative-cooling-delta
  "Temperature drop (K) over dt from radiating as a grey body, with a crude
   optical-depth correction so dense regions cool slowly. The drop is clamped
   to an exponential decay toward the CMB floor so the large dynamical timestep
   does not instantly freeze small bodies."
  [{:keys [temperature radius density]} dt]
  (let [surface-area  (* 4.0 math/PI radius radius)
        optical-depth (* density radius 1e-20)
        emissivity    (/ 1.0 (+ 1.0 optical-depth))
        t             (double (or temperature 3.0))
        power-at-t    (* law/stefan-boltzmann surface-area emissivity
                         (math/pow t 4))
        mass          (* density (/ 4.0 3.0) math/PI (math/pow radius 3))
        specific-heat (* 2.5 law/k-B (/ 1.0 law/m-H))
        cmb           3.0]
    (if (and (pos? mass) (pos? specific-heat) (pos? t))
      (let [tau     (/ (* mass specific-heat t) power-at-t)
            factor  (- 1.0 (math/exp (- (/ (double dt) tau))))]
        (* (- t cmb) factor))
      0.0)))

;; --- Fusion -----------------------------------------------------------------

(defn fusion-rate
  "Energy generation rate per unit mass once a body crosses ignition thresholds.
   Simplified PP-chain dependence ∝ ρ X² T^4."
  [{:keys [temperature pressure composition density]}]
  (if (and (> temperature law/fusion-temp-threshold)
           (> pressure law/fusion-pressure-threshold))
    (let [X (get composition :H 0.75)]
      (* 1e-35 density X X (math/pow (/ temperature 1e7) 4)))
    0.0))

;; UNUSED-PENDING: Disc/stellar-structure physics implemented ahead of the system that consumes
;; it — no write-set emitter reads these yet.
;; See kanban/tasks/phase-0-protoplanetary-disc-implementation-spec.md
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn luminosity-from-fusion
  "Total luminosity emitted by a fusing body of given radius."
  [fusion-energy-rate radius]
  (* fusion-energy-rate (/ 4.0 3.0) math/PI (math/pow radius 3)))

;; --- Star luminosity normalization ------------------------------------------

(defn star-luminosity
  "Return a representative bolometric luminosity (W) for a star. We scale from
   the toy fusion power to a range that makes nearby debris/planets visibly hot
   (~hundreds of K) without boiling the whole nebula. Keeps ratios meaningful."
  [{:keys [temperature pressure composition density radius]}]
  (if (law/fusion-possible? {:temperature temperature :pressure pressure :composition composition})
    (let [raw (* (fusion-rate {:temperature temperature
                               :pressure pressure
                               :composition composition
                               :density density})
                 (/ 4.0 3.0) math/PI (math/pow radius 3))]
      (if (pos? raw)
        (max 1e26 (min 1e29 (* raw 1e50)))
        1e26))
    0.0))

;; Observable complexity is folded into the adaptive game clock in
;; `domain.pacing/pacing-for` as a per-tick step cap, combined with the bulk
;; dynamical time bound that keeps the integrator stable.

;; --- ECS projection ---------------------------------------------------------

(defn entity->region
  "Project an entity's components into the plain map the pure physics fns expect."
  [world eid]
  {:id          eid
   :position    (ecs/get-component world eid c/position)
   :mass        (ecs/get-component world eid c/mass)
   :radius      (ecs/get-component world eid c/radius)
   :temperature (ecs/get-component world eid c/temperature)
   :density     (ecs/get-component world eid c/density)
   :pressure    (ecs/get-component world eid c/pressure)
   :composition (ecs/get-component world eid c/composition)
   :matter-state (ecs/get-component world eid c/matter-state)
   :b-field     (ecs/get-component world eid c/b-field)})

;; --- ECS systems ------------------------------------------------------------

(defn sound-speed
  "Adiabatic sound speed c_s = √(γ k_B T / m_H) for an ideal gas. m/s."
  [temperature]
  (if (pos? (double temperature))
    (math/sqrt (/ (* lf/gamma law/k-B (double temperature)) law/m-H))
    0.0))

(def ^:const solar-mass-kg 1.989e30) ;; kg
