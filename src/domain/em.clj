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
     - Lorentz force      : (∇ × B) × B / μ₀, applied to velocities via the
                            orbital integrator (a = f/ρ).
     - magnetic braking   : poloidal field threading a rotating clump exerts a
                            torque that transports angular momentum outward.

   All formulas are SI (see law.field). Pure data transformation; no IO."
  (:require
   [law.field         :as lf]
   [law.stellar       :as ls]
   [domain.hydro      :as hydro]
   [shape.spatial     :as sp]
   [domain.ecs.core   :as ecs]
   [domain.ecs.parallel :as par]
   [domain.ecs.tick   :as tick]
   [domain.ecs.components :as c]))

;; --- Pure field physics -----------------------------------------------------

(defn curl-estimate
  "Estimate (∇ × B) at a clump from neighboring b-field vectors using an SPH-like
   curl formula. Returns a vector in T/m. Zero neighbors → zero curl.

   Uses the symmetric SPH curl: (∇ × B)_i = Σ_j m_j/ρ_j (B_i - B_j) × ∇_i W_ij."
  [b-field density position neighbors]
  (if (or (not (lf/finite-vec3? b-field))
          (not (pos? (double density))))
    [0.0 0.0 0.0]
    (reduce
      (fn [acc n]
        (let [r-ij   (sp/v- position (:position n))
              h      (* 0.5 (+ (or (:radius n) 1.0) 1.0))
              grad   (hydro/kernel-gradient r-ij h)
              db     (sp/v- b-field (:b-field n))
              ;; ∇ × B contribution from neighbor j
              contrib (sp/cross db grad)]
          (sp/v+ acc (sp/v* contrib (/ (double (:mass n 1.0))
                                        (double (:density n 1.0)))))))
      [0.0 0.0 0.0]
      neighbors)))

(defn lorentz-force-density
  "Lorentz force density f = (∇ × B) × B / μ₀  (SI). N/m³. Always perpendicular
   to B. Uses the SPH curl estimate above on the N-body substrate."
  [b-field curl-b]
  (if (and (lf/finite-vec3? b-field) (lf/finite-vec3? curl-b))
    (let [cross (sp/cross curl-b b-field)]
      (sp/v* cross (/ 1.0 lf/mu-0)))
    [0.0 0.0 0.0]))

(defn lorentz-acceleration
  "Lorentz acceleration a = f/ρ = (∇ × B) × B / (μ₀ ρ)  (SI). m/s²."
  [b-field curl-b density]
  (if (pos? (double density))
    (sp/v* (lorentz-force-density b-field curl-b) (/ 1.0 (double density)))
    [0.0 0.0 0.0]))

(defn magnetic-torque
  "Torque density τ = r × f about the origin, where f is the Lorentz force
   density. N/m."
  [position lorentz-force]
  (sp/cross position lorentz-force))

(def ^:const braking-fraction-per-time
  "Cap on magnetic-braking angular-momentum loss, as a fraction of L removed per
   second of SIM-TIME (so the per-step cap is this × dt). ~1/τ_brake with a
   braking timescale τ_brake ≈ 1e14 s (free-fall scale of a molecular cloud);
   gentle enough that the cloud's spin survives the collapse rather than being
   braked away in the first seconds of real time." 1.0e-14)

(defn magnetic-braking-torque
  "Compute the magnetic braking torque on a rotating clump: τ along the rotation
   axis. The field is assumed to be primarily poloidal (threading the rotation
   axis); differential rotation wraps it into a toroidal component whose tension
   brakes the spin. This is a phenomenological per-body reduction of the full
   MHD braking torque.

   Returns the angular momentum REMOVED this step (a vector aligned with
   `rotation-axis`), proportional to B² ρ^(-1/2) r³ ω · dt — the characteristic
   Alfvén-wave torque integrated over the timestep `dt`. Pacing by sim-time (× dt)
   rather than a per-tick fraction is essential now the tick rate is a fixed
   60 Hz: a per-tick cap would shed angular momentum ~38× faster than the old
   variable cadence, braking the cloud's rotation away in seconds."
  [{:keys [mass radius density b-field angular-momentum rotation-axis]} dt]
  (if (and (pos? (double mass))
           (pos? (double radius))
           (pos? (double density))
           (lf/finite-vec3? b-field)
           (lf/finite-vec3? angular-momentum))
    (let [B2   (sp/len2 b-field)
          omega (if (and rotation-axis (lf/finite-vec3? rotation-axis))
                  (sp/dot angular-momentum rotation-axis)
                  (sp/len angular-momentum))
          ;; characteristic Alfvén torque RATE: ~ B² r³ / (μ₀ ρ^(1/2)) · (ω / v_A)
          ;; simplify to ~ B² r³ ω / √(ρ) / μ₀ (angular momentum per unit sim-time)
          base (* B2 (Math/pow (double radius) 3) (Math/abs omega))
          denom (* lf/mu-0 (Math/sqrt (double density)))
          tau-raw  (if (pos? denom) (/ base denom) 0.0)
          ;; angular momentum removed THIS step = rate · dt
          dL-raw   (* tau-raw (double dt))
          L-mag    (sp/len angular-momentum)
          ;; cap the fractional loss to `braking-fraction-per-time · dt`, so the
          ;; brake is sim-time-paced (tick-rate-independent) and never reverses sign
          dL-max   (* braking-fraction-per-time L-mag (double dt))
          dL       (min dL-raw dL-max)
          ;; direction opposes angular momentum
          axis (or rotation-axis [0.0 0.0 1.0])
          sign (- (if (pos? omega) 1.0 -1.0))]
      (sp/v* axis (* sign (min dL (* 1e30 mass)))))
    [0.0 0.0 0.0]))

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

;; --- The field as a FIELD: dipole superposition -----------------------------
;; Each body is a magnetic dipole source. The magnetic field at ANY point in
;; space is the superposition of every body's dipole field — so it is defined
;; everywhere, and star–star / planet–star interactions EMERGE from the sum
;; rather than being asserted. This is the N-body analogue (like Barnes–Hut
;; gravity) for magnetism: sources are bodies, the field is sampled wherever it
;; is needed (on each body, and along traced field lines for rendering).

(def ^:const mu0-over-4pi 1.0e-7) ;; μ₀/4π exactly (T·m/A)

(defn dipole-moment
  "Magnetic dipole moment m (A·m²) of a body whose surface field magnitude is
   |b-field| at radius `radius`, aligned with `b-field`. From the on-axis dipole
   relation B_pole = μ₀|m|/(2π R³) ⇒ m = (2π R³/μ₀)·B."
  [b-field radius]
  (let [r (double (or radius 0.0))]
    (if (and (lf/finite-vec3? b-field) (pos? r))
      (sp/v* b-field (/ (* 2.0 Math/PI r r r) lf/mu-0))
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
            (if (identical? position self-pos)
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
           ;; effective exponent: 2/3 for isotropic, 1 for collapse along B
           exp   (+ (* (- 1.0 a) (/ 2.0 3.0)) (* a 1.0))
           ratio (Math/pow (/ (double new-density) (double old-density)) exp)
           scaled (sp/v* b-field ratio)
           mag    (sp/len scaled)]
       (if (> mag lf/max-b-field)
         ;; clamp magnitude, keep direction
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
           factor (Math/exp (- (* rate (double dt)))) ;; ∈ (0,1]
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

;; --- ECS system -------------------------------------------------------------

(defn- entity->em-data
  "Project an ECS entity into the map the SPH/Lorentz functions expect."
  [world eid]
  {:eid      eid
   :position (ecs/get-component world eid c/position)
   :mass     (ecs/get-component world eid c/mass)
   :radius   (ecs/get-component world eid c/radius)
   :density  (ecs/get-component world eid c/density)
   :pressure (ecs/get-component world eid c/pressure)
   :b-field  (ecs/get-component world eid c/b-field)
   :angular-momentum (ecs/get-component world eid c/angular-momentum)
   :rotation-axis    (ecs/get-component world eid c/rotation-axis)
   :state    (ecs/get-component world eid c/matter-state)})

(defn- em-active?
  "EM force/torque dynamics matter for diffuse and contracting gas."
  [state]
  (contains? #{:nebula :protostar} state))

(defn- neighbors-within
  "All EM-active entities within cutoff distance of `center`."
  [center cutoff all-data]
  (let [cut2 (* cutoff cutoff)]
    (filterv
      (fn [n]
        (let [r2 (sp/len2 (sp/v- center (:position n)))]
          (<= r2 cut2)))
      all-data)))

(defn em-system
  "The EM tick step. Computes:
     1. Lorentz acceleration a = (∇×B)×B / (μ₀ ρ) stored on c/hydro-accel so
        the orbital integrator applies it alongside gravity and hydro.
     2. Magnetic braking torque applied to c/angular-momentum and c/spin.
     3. Resistive flux decay applied to c/b-field.

   Diffuse clumps keep their field essentially unchanged; dense cores slowly
   shed flux — the design's non-ideal hook."
  [dt]
  (fn [world]
    (let [eids     (ecs/entities-with world c/b-field c/radius c/position
                                      c/density c/angular-momentum)
          all-data (mapv #(entity->em-data world %) eids)
          active   (filterv #(em-active? (:state %)) all-data)
          ;; Lorentz + braking
          updates1 (par/par-mapv
                     (fn [data]
                       (let [h       (* 2.0 (double (or (:radius data) 1.0)))
                             nbrs    (neighbors-within (:position data) h active)
                             curl-b  (curl-estimate (:b-field data)
                                                    (:density data)
                                                    (:position data)
                                                    nbrs)
                             lorentz (lorentz-acceleration (:b-field data)
                                                           curl-b
                                                           (:density data))
                             torque  (magnetic-braking-torque data dt)]
                         [(:eid data) lorentz torque]))
                     active)
          world1   (reduce (fn [w [eid a torque]]
                             (let [w' (if (lf/finite-vec3? a)
                                        (ecs/put-component w eid c/hydro-accel
                                                           (sp/v+ (or (ecs/get-component w eid c/hydro-accel)
                                                                      [0.0 0.0 0.0])
                                                                  a))
                                        w)
                                   L  (ecs/get-component w eid c/angular-momentum)
                                   new-L (if (and (lf/finite-vec3? L) (lf/finite-vec3? torque))
                                           (sp/v+ L torque)
                                           L)
                                   mass  (ecs/get-component w eid c/mass)
                                   radius (ecs/get-component w eid c/radius)
                                   new-spin (let [I (* 0.4 mass radius radius)]
                                              (if (pos? I)
                                                (sp/v* new-L (/ 1.0 I))
                                                [0.0 0.0 0.0]))]
                               (if new-L
                                 (-> w'
                                     (ecs/put-component eid c/angular-momentum new-L)
                                     (ecs/put-component eid c/spin new-spin))
                                 w')))
                           world
                           updates1)
          ;; Resistive decay
          updates2 (par/par-mapv
                     (fn [eid]
                       [eid (resistive-decay (ecs/get-component world1 eid c/b-field)
                                             (ecs/get-component world1 eid c/radius)
                                             dt)])
                     eids)]
      (reduce (fn [w [eid b]]
                (if (lf/bounded-b-field? b)
                  (ecs/put-component w eid c/b-field b)
                  w))
              world1
              updates2))))

(defn field-system
  "Double-buffer write-set system: SOLE writer of b-field.

   Magnetic flux Φ = B·R² is frozen into a body when it condenses and conserved
   as it contracts, so B = Φ/R² amplifies as Structure shrinks the radius — ideal
   flux freezing (B ∝ 1/R² ∝ ρ^{2/3}) — while Φ itself decays by Ohmic/ambipolar
   resistivity (real only in dense cores). Diffuse :nebula gas keeps its seeded
   field with the same light resistive decay. Replaces collapse's flux-freezing
   and em-system's b-field decay; Φ (frozen-flux) is the reference that turns the
   amplification into a derivation from the radius Structure owns."
  [dt]
  {:id     :field
   :writes #{c/b-field c/frozen-flux}
   :run    (fn [world]
             (let [eids (ecs/entities-with world c/b-field c/radius)]
               (reduce
                 (fn [ws eid]
                   (let [b (ecs/get-component world eid c/b-field)
                         r (double (or (ecs/get-component world eid c/radius) 0.0))
                         resolved? (contains? #{:debris :planet :protostar :star}
                                              (ecs/get-component world eid c/matter-state))]
                     (if (and (lf/finite-vec3? b) (pos? r))
                       (if resolved?
                         ;; flux frozen at condensation, conserved then decayed:
                         ;; B = Φ/R² — amplifies as R contracts.
                         (let [flux  (or (ecs/get-component world eid c/frozen-flux)
                                         (sp/v* b (* r r)))
                               flux' (resistive-decay flux r dt)
                               b'    (sp/v* flux' (/ 1.0 (* r r)))]
                           (if (lf/bounded-b-field? b')
                             (-> ws (assoc-in [c/b-field eid] b')
                                    (assoc-in [c/frozen-flux eid] flux'))
                             ws))
                         ;; diffuse gas: resistive decay only, no flux freezing
                         (let [b' (resistive-decay b r dt)]
                           (cond-> ws (lf/bounded-b-field? b') (assoc-in [c/b-field eid] b'))))
                       ws)))
                 {}
                 eids)))})

(defn lorentz-acceleration-system
  "Double-buffer write-set system: Lorentz acceleration a = (∇×B)×B/(μ₀ρ) for
   every EM-active clump → `accel.lorentz`. Reads the frozen snapshot, writes
   ONLY accel.lorentz, and clears the contribution from bodies that are no longer
   EM-active. Sole writer of accel.lorentz — the single-writer replacement for
   the Lorentz half of `em-system` (which kept adding into `hydro-accel`).

   The legacy `em-system` still handles magnetic braking (angular-momentum/spin)
   and resistive flux decay (b-field); those accumulators are decomposed in a
   later step. Until then em's own hydro-accel write is masked off by the bridge."
  []
  {:id     :em-lorentz
   :writes #{c/accel-lorentz}
   :run    (fn [world]
             (let [eids     (ecs/entities-with world c/b-field c/radius c/position
                                               c/density c/angular-momentum)
                   all-data (mapv #(entity->em-data world %) eids)
                   active   (filterv #(em-active? (:state %)) all-data)
                   computed (par/par-mapv
                              (fn [data]
                                (let [h      (* 2.0 (double (or (:radius data) 1.0)))
                                      nbrs   (neighbors-within (:position data) h active)
                                      curl-b (curl-estimate (:b-field data) (:density data)
                                                            (:position data) nbrs)]
                                  [(:eid data)
                                   (lorentz-acceleration (:b-field data) curl-b (:density data))]))
                              active)
                   cell     (reduce (fn [m [eid a]]
                                      (if (lf/finite-vec3? a) (assoc m eid a) m))
                                    {} computed)]
               (tick/contribution-write-set
                 c/accel-lorentz cell
                 (keys (get-in world [:components c/accel-lorentz])))))})