(ns domain.stellar
  "Stellar nebula collapse, star formation, and accretion — expressed as pure
   physics helpers plus ECS systems that operate on the shared world.

   This is Phase 0 of the universal simulation substrate. The same components
   (temperature, density, pressure, composition, mass, radius) and the same
   operations carry forward into geology, climate, and chemistry later — only
   the magnitudes change. There is no separate stellar world model: a clump of
   nebular gas and a finished planet are both just entities."
  (:require
   [law.stellar           :as law]
   [law.field             :as lf]
   [law.composition       :as lcomp]
   [law.sed               :as lsed]
   [domain.em             :as em]
   [domain.hydro          :as hydro]
   [domain.ecs.core       :as ecs]
   [domain.ecs.parallel   :as par]
   [domain.ecs.tick       :as tick]
   [domain.ecs.components  :as c]
   [domain.profile         :as profile]
   [shape.spatial         :as sp]))

;; Forward declarations: the stellar-wind system (an accretion-region barrier
;; system, grouped with sink-formation) spawns gas parcels via the nebula-seeding
;; helpers, which are defined further down the file.
(declare spawn-clump default-composition)

;; --- Pure thermodynamics ----------------------------------------------------

(defn body-density
  "Density of a uniform sphere of given mass and radius."
  [mass radius]
  (/ mass (* (/ 4.0 3.0) Math/PI (Math/pow radius 3))))

(defn moment-of-inertia
  "Moment of inertia I = (2/5) M R² for a uniform solid sphere. kg m²."
  [mass radius]
  (* 0.4 (double mass) (Math/pow (double radius) 2)))

(defn orbital-angular-momentum
  "Orbital specific angular momentum L = m (r × v). Vector in kg m²/s."
  [mass position velocity]
  (let [[x y z] position
        [vx vy vz] velocity
        m (double mass)]
    [(* m (- (* y vz) (* z vy)))
     (* m (- (* z vx) (* x vz)))
     (* m (- (* x vy) (* y vx)))]))

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
                    (Math/sqrt (/ (* 8.0 law/G) (* 3.0 radius)))
                    0.0)
        x (if (pos? omega-max) (/ omega omega-max) 0.0)]
    (max 0.05 (min 1.0 (- 1.0 (* 0.45 x x))))))

(defn equivalent-radius
  "Mean radius of an oblate spheroid with equatorial radius a and polar radius
   c: r_eq = (a² c)^(1/3). Same volume as a sphere of radius r_eq."
  [a c]
  (if (and (pos? (double a)) (pos? (double c)))
    (Math/pow (* (Math/pow (double a) 2) (double c)) (/ 1.0 3.0))
    0.0))

(defn oblate-density
  "Density of a uniform oblate spheroid of mass M, equatorial radius a,
   polar radius c."
  [mass a c]
  (if (and (pos? (double mass)) (pos? (double a)) (pos? (double c)))
    (/ (double mass) (* (/ 4.0 3.0) Math/PI (Math/pow (double a) 2) (double c)))
    0.0))

(defn oblate-moment-of-inertia
  "Moment of inertia of a uniform oblate spheroid about its symmetry (spin)
   axis: I_z = (2/5) M a². kg m²."
  [mass a]
  (* 0.4 (double mass) (Math/pow (double a) 2)))

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
  "Given a clump's mass, conserved angular momentum L, current equatorial
   radius `a`, current oblateness `o`, and collapse fraction, return the new
   shape as {:equatorial-radius :polar-radius :oblateness :spin :rotation-axis}.

   Mass is conserved by shrinking the equivalent spherical radius
   r_eq = (a² c)^(1/3) by (1 - collapse-fraction), then solving for the new
   equatorial radius and oblateness self-consistently: spin depends on a,
   oblateness depends on spin, and a depends on oblateness via fixed volume.

   `floor` is a hard lower bound on the equivalent radius (the main-sequence
   radius for a star-forming core); the body contracts toward it and stops,
   instead of halving to a point every tick."
  ([mass L a o collapse-fraction] (oblate-collapse-shape mass L a o collapse-fraction 0.0))
  ([mass L a o collapse-fraction floor]
   (let [c       (if (pos? (double o)) (* a o) a)
         r-eq    (equivalent-radius a c)
         r-eq'   (max (double floor) (* r-eq (- 1.0 collapse-fraction)))
         axis    (rotation-axis L)
        ;; iterative self-consistent solve for a' and o'
         [a' o' spin'] (loop [o-i (max 0.05 (min 1.0 (double o))) n 0]
                         (let [a-i    (/ r-eq' (Math/pow o-i (/ 1.0 3.0)))
                               spin-i (spin-from-angular-momentum-oblate L mass a-i)
                               o-next (max 0.05 (min 1.0 (oblateness-from-spin spin-i a-i)))]
                           (if (or (>= n 4) (< (Math/abs (- o-next o-i)) 1e-6))
                             [a-i o-next spin-i]
                             (recur o-next (inc n)))))]
     {:equatorial-radius a'
      :polar-radius      (* a' o')
      :oblateness        o'
      :spin              spin'
      :rotation-axis     axis})))

(defn gravitational-collapse-rate
  "Collapse rate for a region based on the Jeans instability. Returns 1/s if the
   region is larger than its Jeans length (unstable), else 0."
  [{:keys [density temperature radius]}]
  (let [sound-speed   (Math/sqrt (/ (* law/k-B temperature) law/m-H))
        jeans-length  (* sound-speed (Math/sqrt (/ Math/PI (* law/G density))))
        collapse-time (Math/sqrt (/ (* 3 Math/PI) (* 32 law/G density)))]
    (if (> radius jeans-length)
      (/ 1.0 collapse-time)
      0.0)))

(defn jeans-unstable?
  "True if a region is gravitationally unstable and will tend to collapse."
  [region]
  (pos? (gravitational-collapse-rate region)))

(defn compression-heating
  "Adiabatic compression temperature: T ∝ ρ^(γ-1), γ = 5/3 for monatomic gas."
  [initial-temp initial-density final-density]
  (* initial-temp (Math/pow (/ final-density initial-density) 0.667)))

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
      (Math/pow (/ L (* 4.0 Math/PI R R law/stefan-boltzmann)) 0.25)
      0.0)))

(defn self-gravity-pressure
  "Central pressure of a self-gravitating uniform sphere: P ≈ G M² / ((4/3π) R⁴).
   Rises steeply as the core contracts."
  [mass radius]
  (/ (* law/G mass mass) (* (/ 4.0 3.0) Math/PI (Math/pow radius 4))))

(defn radiative-cooling-delta
  "Temperature drop (K) over dt from radiating as a grey body, with a crude
   optical-depth correction so dense regions cool slowly. The drop is clamped
   to an exponential decay toward the CMB floor so the large dynamical timestep
   does not instantly freeze small bodies."
  [{:keys [temperature radius density]} dt]
  (let [surface-area  (* 4.0 Math/PI radius radius)
        optical-depth (* density radius 1e-20)
        emissivity    (/ 1.0 (+ 1.0 optical-depth))
        t             (double (or temperature 3.0))
        power-at-t    (* law/stefan-boltzmann surface-area emissivity
                         (Math/pow t 4))
        mass          (* density (/ 4.0 3.0) Math/PI (Math/pow radius 3))
        specific-heat (* 2.5 law/k-B (/ 1.0 law/m-H))
        cmb           3.0]
    (if (and (pos? mass) (pos? specific-heat) (pos? t))
      (let [tau     (/ (* mass specific-heat t) power-at-t)
            factor  (- 1.0 (Math/exp (- (/ (double dt) tau))))]
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
      (* 1e-35 density X X (Math/pow (/ temperature 1e7) 4)))
    0.0))

(defn luminosity-from-fusion
  "Total luminosity emitted by a fusing body of given radius."
  [fusion-energy-rate radius]
  (* fusion-energy-rate (/ 4.0 3.0) Math/PI (Math/pow radius 3)))

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
                 (/ 4.0 3.0) Math/PI (Math/pow radius 3))]
      (if (pos? raw)
        (max 1e26 (min 1e29 (* raw 1e50)))
        1e26))
    0.0))

;; --- Complexity / time scale ------------------------------------------------

(defn complexity-score
  "Observable complexity from a tally of the system. Higher complexity slows
   simulation time — the universe becomes more articulate as it cools.

   Note: only *collapsed* bodies (stars and planets) count as complex.
   Diffuse nebula clumps are not yet resolved into distinct objects, so they
   should not compress time to real-time."
  [{:keys [_body-count star? fusion? planet-count]}]
  (+ (if star? 5 0)
     (if fusion? 20 0)
     (* 10 planet-count)))

;; Observable complexity now drives the adaptive game clock and integration
;; step in `domain.phase0` (see `pacing-tiers`), keyed on the detected formation
;; phase rather than a single continuous time-compression factor.

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
    (Math/sqrt (/ (* lf/gamma law/k-B (double temperature)) law/m-H))
    0.0))

(defn jeans-length
  "Jeans length λ_J = c_s √(π / (G ρ)) for a gas of sound speed c_s and density ρ.
   Returns 0 for non-positive inputs."
  [density temperature]
  (let [rho (double (or density 0.0))
        cs  (sound-speed temperature)]
    (if (pos? rho)
      (* cs (Math/sqrt (/ Math/PI (* law/G rho))))
      0.0)))

(defn jeans-collapse-system
  "Promote Jeans-unstable `:nebula` gas particles to resolved bodies. A fixed-mass
   gas sample becomes a physical object when self-gravity overcomes thermal
   pressure. The new state is set from the particle's mass via `law/mass-class`,
   its radius is shrunk to a resolved-body density, and it is removed from the
   SPH gas pool so it can only grow further by collision merging with other
   resolved bodies.

   A freshly promoted body keeps the gas sample's original radius as its
   `c/accretion-radius` for one tick. This gives bodies that formed side-by-side
   in a dense filament a chance to merge gravitationally before orbital motion
   spreads them apart; without it each promoted body is a pinpoint that rarely
   touches another."
  [world]
  (let [eids (ecs/entities-with world c/matter-state c/position c/density
                                c/radius c/temperature c/mass)
        gas-mass (:phase0/gas-particle-mass world)]
    (reduce (fn [w eid]
              (if (not= :nebula (ecs/get-component w eid c/matter-state))
                w
                (let [mass  (double (ecs/get-component w eid c/mass))
                      rho   (double (ecs/get-component w eid c/density))
                      temp  (double (ecs/get-component w eid c/temperature))
                      r     (double (ecs/get-component w eid c/radius))
                      lam-j (jeans-length rho temp)
                      state (law/mass-class mass gas-mass)]
                  (if (and (> r lam-j) (not= state :nebula))
                    (let [r'        (case state
                                      :debris     (Math/pow (/ mass (* (/ 4.0 3.0) Math/PI 2.0e3)) (/ 1.0 3.0))
                                      :planet     (Math/pow (/ mass (* (/ 4.0 3.0) Math/PI 1.0e3)) (/ 1.0 3.0))
                                      :protostar  r)
                          rho'      (/ mass (* (/ 4.0 3.0) Math/PI r' r' r'))
                          press'    (law/ideal-gas-pressure rho' temp)
                          ;; Keep the original gas smoothing length as the
                          ;; collision/gravitational feeding radius for this
                          ;; tick so nearby promoted bodies can merge before
                          ;; orbital spreading separates them.
                          accr      (* 50.0 r)]
                      (cond-> w
                        true  (ecs/put-component eid c/matter-state state)
                        true  (ecs/put-component eid c/radius r')
                        true  (ecs/put-component eid c/density rho')
                        true  (ecs/put-component eid c/pressure press')
                        true  (ecs/put-component eid c/accretion-radius accr)
                        true  (ecs/remove-component eid c/hydro-accel)))
                    w))))
            world
            eids)))

(defn collapse-system
  "A protostar — a clump that has accreted past the star-forming mass — contracts
   each tick under self-gravity: radius shrinks, density rises, and compression
   heating drives core temperature and pressure toward ignition (Kelvin–Helmholtz
   contraction). Its frozen-in magnetic field amplifies as B ∝ ρ^(2/3).

   Diffuse gas does NOT collapse in place here — it assembles by N-body gravity
   and accretion (collisions). Only the resolved star-forming core contracts.

   Contraction is RATE-LIMITED to the Kelvin–Helmholtz timescale, not a fixed
   fraction per tick: the equivalent radius relaxes toward the main-sequence
   floor as 1 − e^(−dt/τ), where τ = `:phase0/contraction-time` (default ~30
   Myr). With a fixed fraction the core reached the floor in a handful of ticks
   and ignited in ~50 kyr; rate-limiting spreads the ignition event over tens of
   Myr of simulation time, independent of how large `dt` is, while
   `collapse-fraction` remains a hard per-tick cap for stability.

   Temperature is heated adiabatically from the body's previous temperature as
   it compresses, then bounded below by the virial temperature so the core does
   not cool below the gravitational binding energy scale. Pressure follows from
   the ideal gas law."
  [{:keys [phase0/collapse-fraction phase0/contraction-time sim/dt]
    :or   {collapse-fraction 0.5 contraction-time 9.5e14 dt 1.0e12} :as world}]
  (let [frac (min (double collapse-fraction)
                  (- 1.0 (Math/exp (- (/ (double dt) (double contraction-time))))))]
    (reduce
     (fn [w eid]
       (let [region (entity->region world eid)
             {:keys [mass radius matter-state temperature density]} region]
         (if (and (= :protostar matter-state) radius mass)
           (let [L           (or (ecs/get-component world eid c/angular-momentum) [0.0 0.0 0.0])
                 o           (or (ecs/get-component world eid c/oblateness) 1.0)
                 floor       (law/main-sequence-radius mass)
                 shape       (oblate-collapse-shape mass L radius o frac floor)
                 a'          (:equatorial-radius shape)
                 c'          (:polar-radius shape)
                 new-density (oblate-density mass a' c')
                 r-eq        (equivalent-radius a' c')
                 t-vir       (virial-temperature mass r-eq)
                 t-adiabatic (compression-heating (max (double (or temperature 3.0)) t-vir)
                                                  density new-density)
                 new-temp    (max t-vir t-adiabatic)
                 new-press   (law/ideal-gas-pressure new-density new-temp)
                 new-spin    (:spin shape)
                 new-axis    (:rotation-axis shape)
                 anisotropy  (- 1.0 (:oblateness shape))
                 new-b       (when-let [b (:b-field region)]
                               (em/flux-freeze b density new-density anisotropy))]
             (cond-> w
               true  (ecs/put-component eid c/radius         a')
               true  (ecs/put-component eid c/density        new-density)
               true  (ecs/put-component eid c/temperature    new-temp)
               true  (ecs/put-component eid c/pressure       new-press)
               true  (ecs/put-component eid c/spin           new-spin)
               true  (ecs/put-component eid c/oblateness     (:oblateness shape))
               true  (ecs/put-component eid c/rotation-axis  new-axis)
               new-b (ecs/put-component eid c/b-field        new-b)))
           w)))
     world
     (ecs/entities-with world c/matter-state c/temperature c/density c/radius c/mass))))

(defn classify-system
  "Set each clump's matter-state from the mass it has accreted from the cloud
   (law/mass-class): gas -> debris -> planet -> protostar. Formation is emergent —
   a clump becomes a planet or a star-forming core because it ATE enough gas, not
   because it was seeded that way. Stars never declassify; ignition (protostar ->
   star) is left to the fusion system once contraction makes the core hot enough."
  [world]
  (let [eids     (ecs/entities-with world c/matter-state c/mass)
        gas-mass (:phase0/gas-particle-mass world)
        updates  (mapv
                  (fn [eid]
                    (let [state (ecs/get-component world eid c/matter-state)]
                      [eid state
                       (if (= :star state)
                         state
                         (law/mass-class (ecs/get-component world eid c/mass) gas-mass))]))
                  eids)]
    (reduce (fn [w [eid old nw]]
              (if (= old nw)
                w
                (let [w (ecs/put-component w eid c/matter-state nw)
                      old-gas-radius (when (= old :nebula)
                                       (double (or (ecs/get-component w eid c/radius) 0.0)))]
                  ;; When a clump first promotes out of the gas pool, give it a
                  ;; temporary gravitational feeding radius so it can merge with
                  ;; nearby promoted bodies before orbital spreading separates
                  ;; them. Protostars additionally freeze their pre-contraction
                  ;; radius so they keep sweeping up mass after collapse.
                  (cond
                    (and (= nw :protostar)
                         (nil? (ecs/get-component w eid c/accretion-radius)))
                    (ecs/put-component w eid c/accretion-radius
                                       (double (or old-gas-radius
                                                   (ecs/get-component w eid c/radius) 0.0)))
                    (and (= old :nebula)
                         (not= nw :nebula)
                         (nil? (ecs/get-component w eid c/accretion-radius)))
                    (ecs/put-component w eid c/accretion-radius
                                       (* 100.0 (double (or old-gas-radius
                                                            (ecs/get-component w eid c/radius) 0.0))))
                    :else w))))
            world
            updates)))

(defn fusion-system
  "Fan-out system: sole writer of c/luminosity.

    Reads c/promotion-signal from the previous tick's fusion-promotion barrier
    and applies its :luminosity value. Falls back to computing luminosity from
    scratch when there is no signal (initial ignition before the barrier path
    activates). The mask to #{c/luminosity} in the legacy bridge ensures no
    matter-state writes leak through."
  [world]
  (profile/profile-sections
   world
   [[:fusion/scan
     (fn [w]
       {:promotions (get-in w [:components c/promotion-signal] {})
        :eids       (ecs/entities-with w c/matter-state c/temperature c/pressure c/composition)})]
    [:fusion/burn
     (fn [{:keys [promotions eids]}]
       (reduce
        (fn [w eid]
          (let [region (entity->region world eid)
                sig    (get promotions eid)
                lum    (if sig
                         (:luminosity sig)
                         (when (law/fusion-possible? region)
                           (star-luminosity region)))]
            (if lum
              (ecs/put-component w eid c/luminosity lum)
              w)))
        world
        eids))]]))

(defn fusion-promotion-system
  "Fan-out emitter: emits c/promotion-signal for protostars that now meet fusion
    conditions (and for stars with stale zero luminosity).

    Instead of directly writing c/matter-state and c/luminosity (conflicting with
    classifier and fusion respectively — spec §7), it emits a signal that both
    systems read on the NEXT tick's frozen snapshot. The one-tick latency is
    accepted (§2). Runs in the parallel fan-out (was a post-fold barrier)."
  [world]
  (profile/profile-sections
   world
   [[:fusion-promotion/scan
     (fn [w]
       (let [w' (reduce (fn [w eid]
                          (ecs/remove-component w eid c/promotion-signal))
                        w
                        (keys (get-in w [:components c/promotion-signal] {})))]
         {:world w'
          :eids  (ecs/entities-with w' c/matter-state c/temperature c/pressure
                                    c/composition c/density c/radius c/mass)}))]
    [:fusion-promotion/evaluate
     (fn [{:keys [world eids]}]
       {:world   world
        :signals (into []
                       (keep (fn [eid]
                               (let [state (ecs/get-component world eid c/matter-state)
                                     region (entity->region world eid)]
                                 (cond
                                   ;; Protostar → star promotion
                                   (and (= :protostar state) (law/fusion-possible? region))
                                   [eid {:promotion :star
                                         :luminosity (star-luminosity region)}]

                                   ;; Existing star with zero luminosity → refresh
                                   (and (= :star state) (law/fusion-possible? region)
                                        (let [lum (double (or (ecs/get-component world eid c/luminosity) 0.0))]
                                          (zero? lum)))
                                   [eid {:promotion :star
                                         :luminosity (star-luminosity region)}]

                                   :else nil))))
                       eids)})]
    [:fusion-promotion/write-set
     (fn [{:keys [world signals]}]
       (if (seq signals)
         (reduce (fn [w [eid sig]] (ecs/put-component w eid c/promotion-signal sig))
                 world signals)
         world))]]))

;; --- Radiation from stars ---------------------------------------------------

(defn irradiance-at
  "Radiative flux (W/m²) from a star of given luminosity at distance r."
  [luminosity r]
  (if (pos? r)
    (/ (double luminosity) (* 4.0 Math/PI r r))
    0.0))

(defn radiation-equilibrium-temperature
  "Equilibrium temperature (K) of a grey-body at distance r from a star with
   the given luminosity, assuming a moderate albedo."
  [luminosity r]
  (let [S (irradiance-at luminosity r)]
    (if (pos? S)
      (Math/pow (/ (* 0.7 S) (* 4.0 law/stefan-boltzmann)) 0.25)
      0.0)))

(defn radiation-heating-delta
  "Temperature rise (K) over dt for a body heated by a nearby star."
  [{:keys [mass radius density]} luminosity r dt]
  (let [absorbed (* 0.7 (irradiance-at luminosity r) Math/PI radius radius)
        specific-heat (* 2.5 law/k-B (/ 1.0 law/m-H))
        body-mass (or mass (* density (/ 4.0 3.0) Math/PI (Math/pow radius 3)))]
    (if (pos? body-mass)
      (/ (* absorbed dt) (* body-mass specific-heat))
      0.0)))

(defn sed-heating-delta
  "Temperature rise (K) over dt for a body heated by a star's SED bands.
   Uses vis+NIR for surface heating (climate) and XUV for upper-atmosphere
   heating. More physically accurate than bolometric heating for planets
   with atmospheres. Falls back to bolometric if bands are nil."
  [{:keys [mass radius density]} bands r dt]
  (let [body-mass (or mass (* density (/ 4.0 3.0) Math/PI (Math/pow radius 3)))
        specific-heat (* 2.5 law/k-B (/ 1.0 law/m-H))]
    (if-not (pos? body-mass)
      0.0
      (if (seq bands)
        ;; Band-specific: vis+NIR for surface, XUV for atmosphere
        (let [L-climate (lsed/climate-luminosity bands)
              L-xuv     (lsed/xuv-luminosity bands)
              ;; Climate heating: 70% absorbed by surface
              S-climate  (irradiance-at L-climate r)
              absorbed   (* 0.7 S-climate Math/PI radius radius)
              ;; XUV heating: 90% absorbed by upper atmosphere (if any)
              S-xuv      (irradiance-at L-xuv r)
              xuv-absorbed (* 0.9 S-xuv Math/PI radius radius)]
          (/ (* (+ absorbed xuv-absorbed) dt) (* body-mass specific-heat)))
        ;; Fallback: bolometric
        (radiation-heating-delta {:mass mass :radius radius :density density}
                                 (reduce + 0.0 (map (fn [[_ v]] (double v)) bands))
                                 r dt)))))

;; --- Panchromatic SED (Phase 1) ---------------------------------------------
;; Stars emit radiation across the full EM spectrum. The SED shape is set by
;; T_eff and log g. A scalar bolometric luminosity obscures band-dependent
;; effects: XUV drives atmospheric escape, FUV/NUV affect photochemistry,
;; IR regulates climate. This system computes per-band luminosities from
;; pre-tabulated spectral templates (law.sed/spectral-templates).
;; Source: docs/research/phase1-radiation-plasma-truth.md §2

(defn stellar-sed-system
  "Double-buffer write-set system: SOLE writer of c/sed-bands for :star entities.
   Computes T_eff from L and R (Stefan-Boltzmann), selects the nearest spectral
   template from law.sed, and scales band fractions by L_bol.

   Precondition: entity is :star with positive luminosity and radius.
   Postcondition: c/sed-bands is a map of band-keyword → Watts, summing to L_bol.
   When c/flare-boost is active (tick < decay-tick), XUV bands are multiplied
   by the boost factor. This models transient XUV enhancement from stellar flares."
  []
  {:id     :stellar-sed
   :writes #{c/sed-bands}
   :run    (fn [world]
             (let [tick  (or (:tick world) 0)
                   eids  (ecs/entities-with world c/matter-state c/luminosity c/radius)
                   stars (filterv #(= :star (ecs/get-component world % c/matter-state)) eids)
                   bands (into {}
                               (keep (fn [eid]
                                       (let [L    (double (or (ecs/get-component world eid c/luminosity) 0.0))
                                             R    (double (or (ecs/get-component world eid c/radius) 0.0))
                                             teff (effective-temperature L R)
                                             logg (if (pos? R)
                                                    (Math/log10 (/ (* law/G (double (ecs/get-component world eid c/mass)) 1000.0)
                                                                   (* R R)))
                                                    4.5)]
                                         (when (pos? L)
                                           (let [base-bands (lsed/select-sed-bands teff logg L)
                                                 ;; Apply flare XUV boost if active
                                                 boost (ecs/get-component world eid c/flare-boost)]
                                             (if (and boost (< tick (long (:decay-tick boost 0))))
                                               (let [f (double (:factor boost 1.0))]
                                                 (-> base-bands
                                                     (update :xray #(* (double %) f))
                                                     (update :euv  #(* (double %) f))))
                                               base-bands))))))
                               stars)]
               {c/sed-bands bands}))})

;; --- Stellar atmosphere shells (Phase 1) ------------------------------------
;; Real stars have stratified atmospheres: photosphere, chromosphere, transition
;; region, corona. Each layer has distinct temperature, density, ionization, and
;; magnetic field. The corona is the source of XUV radiation and stellar winds.
;; This system derives a 4-layer profile from T_eff, log g, and B-field.
;; Source: docs/research/phase1-radiation-plasma-truth.md §3

(defn- atmosphere-from-teff
  "Build a 4-layer atmosphere profile from stellar parameters.
   Returns a vector of shell maps ordered photosphere → corona."
  [teff logg b-field]
  (let [;; Photosphere: T ≈ T_eff, dense, partially ionized
        photosphere {:layer/id            :photosphere
                     :temperature         teff
                     :electron-density    (* 1.0e17 (Math/pow (/ teff 5800.0) 2.5))
                     :ionization-fraction (min 1.0 (max 0.01 (/ teff 1.0e4)))
                     :b-field             (or b-field [0.0 0.0 0.0])
                     :height              0.0}
        ;; Chromosphere: T ~ 10^4 K, rising temperature, strong emission lines
        chromosphere {:layer/id            :chromosphere
                      :temperature         (max teff 1.0e4)
                      :electron-density    (* 1.0e16 (Math/pow (/ teff 5800.0) 1.5))
                      :ionization-fraction (min 1.0 (max 0.1 (/ teff 6.0e3)))
                      :b-field             (or b-field [0.0 0.0 0.0])
                      :height             (* 5.0e5 (max 1.0 (/ 4.5 logg)))}
        ;; Transition region: steep T gradient, high ionization
        transition {:layer/id            :transition
                    :temperature         (max (* 2.0 teff) 1.0e5)
                    :electron-density    (* 1.0e14 (Math/pow (/ teff 5800.0) 0.5))
                    :ionization-fraction 0.9
                    :b-field             (or b-field [0.0 0.0 0.0])
                    :height             (* 2.0e6 (max 1.0 (/ 4.5 logg)))}
        ;; Corona: hot (1-3 × 10^6 K), low density, fully ionized, XUV source
        ;; Corona temperature scales with stellar activity (hotter stars → hotter corona)
        corona-t  (min 3.0e7 (max 1.0e6 (* 200.0 teff)))
        corona    {:layer/id            :corona
                   :temperature         corona-t
                   :electron-density    (* 1.0e12 (Math/pow (/ teff 5800.0) 0.3))
                   :ionization-fraction 1.0
                   :b-field             (or b-field [0.0 0.0 0.0])
                   :height             (* 1.0e8 (max 1.0 (/ 4.5 logg)))}]
    [photosphere chromosphere transition corona]))

(defn atmosphere-shells-system
  "Double-buffer write-set system: SOLE writer of c/atmosphere-shells for :star
   entities. Derives a four-layer atmosphere profile (photosphere, chromosphere,
   transition, corona) from T_eff, log g, and magnetic field.

   Precondition: entity is :star with positive luminosity and radius.
   Postcondition: c/atmosphere-shells is a vector of 4 shell maps, each with
   :layer/id, :temperature, :electron-density, :ionization-fraction, :b-field, :height."
  []
  {:id     :atmosphere-shells
   :writes #{c/atmosphere-shells}
   :run    (fn [world]
             (let [eids (ecs/entities-with world c/matter-state c/luminosity c/radius c/mass)
                   stars (filterv #(= :star (ecs/get-component world % c/matter-state)) eids)
                   shells (into {}
                                (keep (fn [eid]
                                        (let [L     (double (or (ecs/get-component world eid c/luminosity) 0.0))
                                              R     (double (or (ecs/get-component world eid c/radius) 0.0))
                                              M     (double (or (ecs/get-component world eid c/mass) 0.0))
                                              B     (ecs/get-component world eid c/b-field)
                                              teff  (effective-temperature L R)
                                              logg  (if (pos? R)
                                                      (Math/log10 (/ (* law/G M 1000.0) (* R R)))
                                                      4.5)]
                                          (when (pos? teff)
                                            [eid (atmosphere-from-teff teff logg B)]))))
                                stars)]
               {c/atmosphere-shells shells}))})

;; --- Deuterium depletion (Phase 0) ------------------------------------------
;; D is the most fragile isotope — destroyed at T > 10⁶ K, well below fusion
;; temperatures. Every star that forms destroys its D. This is a ONE-WAY gate:
;; D never re-appears once destroyed. Sub-stellar bodies retain primordial D.
;; Source: docs/research/cosmology/primordial-nucleosynthesis-yields.md §8

(def ^:const deuterium-destruction-temp
  "Temperature (K) above which deuterium is destroyed. 10⁶ K — well below
   fusion ignition (10⁷ K) but above any planetary/stellar photosphere."
  1.0e6)

(defn deuterium-depletion-system
  "Write-set emitter: sole writer of :component/comp.depletion — the set of
   composition keys to zero for any body whose temperature exceeds
   deuterium-destruction-temp (just :D). One-way gate; the integrator owns
   :component/composition and applies the burn (comp.burn) then this depletion
   (spec §7.5). A pure snapshot-reading fan-out emitter (no longer a serial
   barrier). Auto-clears the influence when a body cools back below the gate
   (harmless: D is already gone)."
  []
  {:id     :deuterium-depletion
   :writes #{c/comp-depletion}
   :run    (fn [world]
             (let [eids (ecs/entities-with world c/matter-state c/temperature c/composition)
                   cell (into {}
                              (keep (fn [eid]
                                      (let [T    (double (or (ecs/get-component world eid c/temperature) 0.0))
                                            composition (ecs/get-component world eid c/composition)]
                                        (when (and (> T deuterium-destruction-temp)
                                                   (pos? (double (:D composition 0.0))))
                                          [eid #{:D}]))))
                              eids)]
               (tick/contribution-write-set
                c/comp-depletion cell
                (keys (get-in world [:components c/comp-depletion])))))})

;; --- The classifier: authentic matter-state state machine -------------------
;; See docs/notes/2026.06.26-authentic-phase0-formation-physics.md §3. The two
;; physical axes are kept separate: Jeans instability gates whether diffuse gas
;; CONDENSES; accreted mass gates WHAT a condensed core becomes; temperature +
;; mass gate IGNITION. No axis stands in for another (the old bug, where mass
;; alone turned diffuse gas straight into solid bodies).

(def ^:const core-condensation-density
  "Central density (kg/m³) at which collapsing gas becomes optically thick and a
   self-gravitating hydrostatic core forms — the first-core threshold. Diffuse
   cloud gas sits at ~1e-16; crossing this (while Jeans-unstable) is the authentic
   nebula→resolved condensation trigger. It also caps the SPH density: once gas is
   this dense it stops being a fluid sample and becomes a body."
  1.0e-10)

(defn sink-exclusion-zones
  "Precompute the accretion radii and positions of all existing sinks (bodies
   that have condensed out of the nebula). Returns a seq of {:position :radius}
   maps for use in the isolation criterion. Pure — reads only from the frozen
   snapshot."
  [world]
  (let [sinks (ecs/entities-with world c/matter-state c/accretion-radius c/position)]
    (mapv (fn [eid]
            {:position (ecs/get-component world eid c/position)
             :radius   (double (or (ecs/get-component world eid c/accretion-radius) 0.0))})
          sinks)))

(defn within-existing-sink?
  "True when a parcel's position falls inside any existing sink's accretion
   radius. This is the isolation criterion from Federrath et al. (2010) — a
   parcel can only condense if it is NOT within an existing sink's zone.
   Prevents the cloud from condensing wholesale after the first sink forms."
  [parcel-pos sink-zones]
  (when (and parcel-pos (seq sink-zones))
    (some (fn [{:keys [position radius]}]
            (let [d (sp/dist parcel-pos position)]
              (< d (double (or radius 0.0)))))
          sink-zones)))

(defn bondi-radius
  "Bondi accretion radius: r = G·M / c_s². Grows with mass — a forming star's
   capture zone expands as it accretes. Returns 0 for non-positive inputs."
  [mass snd-spd]
  (let [m (double (or mass 0.0))
        cs (double (or snd-spd 0.0))]
    (if (and (pos? m) (pos? cs))
      (/ (* law/G m) (* cs cs))
      0.0)))

(defn contraction-stalled?
  "True when a contracting core has reached its main-sequence/degenerate radius
   floor while still below the hydrogen-ignition temperature — i.e. it will never
   ignite hydrogen. This is the brown-dwarf outcome."
  [radius mass temperature]
  (and radius mass temperature
       (<= (double radius) (* 1.05 (law/main-sequence-radius mass)))
       (< (double temperature) law/fusion-temp-threshold)))

(defn classify-next-state
  "Pure transition function for one body's matter-state, given its physical
   region and the cloud's fixed gas-particle mass. Authentic formation beats:

     :nebula  --Jeans-unstable & accreted past one parcel-->  condensed core
                  condensed & mass ≥ deuterium limit  -> :protostar
                  condensed & sub-stellar             -> :debris (planetesimal)
     :debris  --accreted to ≥ deuterium limit-->             :protostar
     :protostar  --T≥1e7 & M≥0.08 M⊙ & H-->                  :star
                 --contraction stalled & 0.013–0.08 M⊙-->    :brown-dwarf
     :star / :brown-dwarf                                    terminal
     :planet                                                 owned by the disk
                                                             sub-grid (beat 6)

   `sink-zones` is an optional seq of {:position :radius} maps for existing
   sinks (from `sink-exclusion-zones`). When provided, a :nebula parcel can
   only condense if it is outside all existing sinks' accretion radii — the
   isolation criterion (Federrath et al. 2010)."
  ([region gas-particle-mass]
   (classify-next-state region gas-particle-mass nil))
  ([{:keys [matter-state mass radius density temperature position] :as region}
    gas-particle-mass sink-zones]
   (let [m  (double (or mass 0.0))
         pm (double (or gas-particle-mass 0.0))]
     (case matter-state
      ;; Mass-loss down-ladder (winds/stripping shed mass — see
      ;; phase0-stellar-winds-and-mass-loss spec). A collapsed body that drops
      ;; below a burning threshold degrades to the next bound state down; it
      ;; NEVER returns to :nebula (collapse is irreversible — the shed material
      ;; is what becomes gas, not the core). Above threshold these are terminal.
       :star        (cond (>= m law/hydrogen-burning-mass)  :star
                          (>= m law/deuterium-burning-mass) :brown-dwarf
                          :else                             :debris)
       :brown-dwarf (if  (>= m law/deuterium-burning-mass)  :brown-dwarf :debris)
       :planet      :planet
       :protostar   (cond
                      (and (>= m law/hydrogen-burning-mass)
                           (law/fusion-possible? region))
                      :star

                      (and (>= m law/deuterium-burning-mass)
                           (<  m law/hydrogen-burning-mass)
                           (contraction-stalled? radius m temperature))
                      :brown-dwarf

                      (< m law/deuterium-burning-mass) :debris

                      :else :protostar)
       :debris      (if (>= m law/deuterium-burning-mass) :protostar :debris)
      ;; :nebula (and any nil): diffuse gas condenses when it is Jeans-unstable
      ;; AND has either reached the hydrostatic-core density (gravity has
      ;; compressed it past the first-core threshold) OR accreted past a single
      ;; gas parcel. Density-gated condensation is the authentic trigger and it
      ;; also caps the SPH gas density (a condensed body uses material density).
       (if (and (jeans-unstable? region)
                (or (>= (double (or density 0.0)) core-condensation-density)
                    (> m pm))
               ;; Isolation criterion: not within an existing sink's accretion
               ;; radius. Prevents wholesale condensation after the first sink
               ;; forms. (Federrath et al. 2010)
                (not (within-existing-sink? position sink-zones)))
         (if (>= m law/deuterium-burning-mass) :protostar :debris)
         (or matter-state :nebula))))))

(def ^:const feeding-zone-factor
  "How many gas smoothing-lengths wide a freshly-condensed body's gravitational
   feeding zone is. The toy resolution cannot resolve real gas accretion onto a
   core, so a condensing body latches a capture radius this many times its gas
   smoothing length and sweeps up neighbours by literal overlap (the merge
   handler keeps the larger zone). Captured from the diffuse GAS radius at the
   instant of condensation — before Structure's KH contraction shrinks the
   photosphere — so the zone stays wide enough for a core to assemble.

   The zone must span ~twice the initial inter-parcel spacing (≈ extent/N^(1/3))
   so the first overdense body reaches SEVERAL neighbours and runs away, rather
   than just touching its nearest one. At the condensation smoothing length
   (≈0.1·0.004·extent) that is a factor of a few hundred. This constant is the
   floor — validated to ignite the default ~10³-parcel production cloud (a star
   by ~t=180) where sequential forms nothing at all; `create-world` raises it for
   coarser (fewer-parcel) clouds via `resolution-feeding-zone-factor`. Below it
   the cloud condenses into a sub-stellar debris/protostar swarm that fragments
   instead of assembling a core (design §7c)." 50.0)

(def ^:const condense-interval
  "Minimum sim-time (seconds) between successive nebula→body condensations. The
   nebula→resolved transition is the only thing that turns smoothly-orbiting gas
   into a collidable sink, so pacing it by PHYSICS (sim-time) rather than by tick
   count is what keeps formation watchable at a fixed 60 Hz tick rate: between
   condensations the cloud runs many ticks of pure self-gravity (visible infall
   and rotation) instead of condensing wholesale in a handful of ticks the
   instant the homologous collapse crosses the density threshold." 3.0e11)

(defn condense-tick?
  "True when a new condensation is permitted this tick: either the timestep
   already spans a full `condense-interval`, or sim-time crosses an interval
   boundary during this step. Stateless — derived from `:phase0/sim-time` and
   `:sim/dt` on the frozen snapshot, so it holds across the parallel fan-out."
  [world]
  (let [t  (double (or (:phase0/sim-time world) 0.0))
        dt (double (or (:sim/dt world) 0.0))]
    (or (not (pos? condense-interval))
        (>= dt condense-interval)
        (not= (Math/floor (/ t condense-interval))
              (Math/floor (/ (+ t dt) condense-interval))))))

(defn classifier-system
  "Double-buffer write-set system: SOLE writer of matter-state AND accretion-radius.
    Reads each body's physics from the frozen snapshot and applies `classify-next-state`.
    Throttled: at most ONE new condensation per tick (the densest Jeans-unstable parcel),
    and only on a `condense-tick?` so the formation is paced by sim-time, not tick rate.
    The accretion-radius is set on the throttled condensation candidate so that
    `sink-formation-system` can absorb nearby parcels on the same tick."
  []
  {:id     :classifier
   :writes #{c/matter-state c/accretion-radius}
   :reads  #{c/promotion-signal}
   :run
   (fn [world]
     (profile/profile-sections
      world
      [[:classifier/scan
        (fn [w]
          {:gas-mass      (:phase0/gas-particle-mass w)
           :eids          (ecs/entities-with w c/matter-state c/mass)
           :zones         (sink-exclusion-zones w)
           :promotions    (get-in w [:components c/promotion-signal] {})
           :may-condense? (condense-tick? w)
           :factor        (double (:phase0/feeding-zone-factor w feeding-zone-factor))
           :gas-r         (double (or (:phase0/gas-smoothing-radius w) 0.0))})]
       [:classifier/transitions
        (fn [{:keys [gas-mass eids zones promotions may-condense? factor gas-r] :as state}]
          (let [transitions
                (into {}
                      (keep (fn [eid]
                              (let [region (entity->region world eid)
                                    cur    (:matter-state region)
                                    sig    (get promotions eid)
                                    nxt    (if sig
                                             (:promotion sig)
                                             (classify-next-state region gas-mass zones))]
                                (when (not= cur nxt) [eid {:old cur :new nxt :region region}]))))
                      eids)
                condense-candidates
                (if-not may-condense?
                  []
                  (filterv (fn [[_ {:keys [old new]}]]
                             (and (= old :nebula) (or (= new :debris) (= new :protostar))))
                           transitions))
                best-condense
                (when (seq condense-candidates)
                  (key (apply max-key
                              (fn [[eid]]
                                (double (or (:density (:region (get transitions eid))) 0.0)))
                              condense-candidates)))
                applied
                (into {}
                      (keep (fn [[eid {:keys [old new]}]]
                              (let [is-condense? (and (= old :nebula)
                                                      (or (= new :debris) (= new :protostar)))]
                                (when (or (not is-condense?) (= eid best-condense))
                                  [eid new]))))
                      transitions)
                acc-radius
                (when best-condense
                  ;; Use the GAS smoothing radius (stored at world creation), not the
                  ;; post-condensation body radius. The gas radius is the smoothing
                  ;; length BEFORE KH contraction shrinks the photosphere, so the
                  ;; feeding zone is wide enough for the core to sweep up neighbors.
                  (when (pos? gas-r) (* factor gas-r)))]
            (assoc state
                   :transitions transitions
                   :best-condense best-condense
                   :applied applied
                   :acc-radius acc-radius)))]
       [:classifier/write-set
        (fn [{:keys [applied acc-radius best-condense]}]
          (cond-> {c/matter-state applied}
            acc-radius (assoc c/accretion-radius {best-condense acc-radius})))]]))})

(defn resolution-feeding-zone-factor
  "Feeding-zone factor scaled to the cloud's resolution: a core must bridge the
   initial inter-parcel spacing (≈ extent/N^(1/3)) to capture neighbours, and the
   spacing/smoothing-length ratio grows as the parcel count shrinks. Returns the
   `feeding-zone-factor` floor for the default kilo-parcel cloud and larger for
   coarser clouds, so condensed bodies assemble a core at any resolution."
  [gas-count]
  (let [n (double (max 1 (or gas-count 1000)))]
    (max feeding-zone-factor (/ 500.0 (Math/pow n (/ 1.0 3.0))))))

(defn accretion-zone-system
  "Double-buffer write-set system: SOLE writer of accretion-radius (the
   gravitational feeding zone of a star-forming body). It latches the zone at the
   exact instant of condensation by reusing the classifier's own decision:
   for every diffuse :nebula parcel that `classify-next-state` will promote out of
   the gas THIS tick, it writes a feeding zone of `feeding-zone-factor` × the
   parcel's current gas smoothing radius. Both systems read the same frozen
   snapshot and the same predicate, so the feeding zone and the matter-state flip
   land on the same tick — closing the race in which a parcel condensed (via the
   density gate) one tick before the old jeans-collapse gate (Jeans length with γ)
   would have fired, leaving it resolved but with no feeding zone, hence never
   collidable and unable to assemble a core. Bodies already resolved keep their
   zone (it is never removed and never shrinks)."
  []
  {:id     :jeans-collapse
   :writes #{c/accretion-radius}
   :run    (fn [world]
             (let [gas-mass (:phase0/gas-particle-mass world)
                   factor   (double (:phase0/feeding-zone-factor world feeding-zone-factor))
                   eids     (ecs/entities-with world c/matter-state c/mass c/radius)]
               {c/accretion-radius
                (into {}
                      (keep (fn [eid]
                              (let [region (entity->region world eid)
                                    r      (double (or (:radius region) 0.0))]
                                (when (and (= :nebula (:matter-state region))
                                           (pos? r)
                                           (not= :nebula (classify-next-state region gas-mass)))
                                  [eid (* factor r)]))))
                      eids)}))})

(defn- absorb-parcels
  "Emit absorb-accrete + consumed-accrete for the absorbed parcels, instead of
   directly writing position/velocity/mass/disk-mass (spec §5). The integrator
   reads absorb-accrete next tick and applies COM-preserving velocity/position/
   mass changes; disk-evolution reads it to grow disk-mass/disk-angular-mom.
   The parcels are marked consumed and reaped by materialize-lifecycle."
  [world sink-eid parcels]
  (let [sink-p    (or (ecs/get-component world sink-eid c/position) [0 0 0])
        sink-v    (or (ecs/get-component world sink-eid c/velocity) [0 0 0])
        sink-state (ecs/get-component world sink-eid c/matter-state)
        disk-former? (contains? #{:protostar :star} sink-state)
        ;; Build per-parcel absorb data packets
        packets (mapv (fn [eid]
                        (let [m (double (or (ecs/get-component world eid c/mass) 0.0))
                              v (or (ecs/get-component world eid c/velocity) [0 0 0])
                              p (or (ecs/get-component world eid c/position) [0 0 0])
                              r-rel (sp/v- p sink-p)
                              v-rel (sp/v- v sink-v)
                              L-p (orbital-angular-momentum m r-rel v-rel)]
                          {:mass m :velocity v :position p
                           :angular-momentum L-p
                           :disk-route disk-former?}))
                      parcels)]
    (-> world
        ;; Emit absorb-accrete on the sink (the integrator/disk-evolution
        ;; read this next tick; caller clears for non-absorbing sinks)
        (ecs/put-component sink-eid c/absorb-accrete packets)
        ;; Emit consumed markers on the parcels (reaped by materialize-lifecycle)
        (as-> w (reduce (fn [w eid] (ecs/put-component w eid c/consumed-accrete true))
                        w parcels)))))

(declare imf-accretion-bias stellar-feedback-temperature hash01 feedback-radius
         sphere-radius planet-material-density)

(defn sink-formation-system
  "Fan-out emitter: every sink absorbs :nebula gas parcels within its
   gravitational capture zone. Three Phase 1 additions:

   1. IMF bias: accretion probability is mass-dependent — high-mass sinks
      accrete less efficiently, steering toward the Kroupa/Salpeter IMF.
   2. Stellar feedback: UV radiation from nearby stars heats gas parcels,
      suppressing Jeans collapse in their vicinity (feedback radius ~0.5 AU).
   3. Disk formation: angular momentum of accreted material is tracked in
      c/disk-angular-mom and c/disk-mass.

   Emits absorb-accrete influence + consumed-accrete lifecycle markers (spec §5)
   instead of directly writing contended physical state. Clears stale absorb-accrete
   before processing so the integrator never double-counts.

   Runs in the parallel fan-out (was a post-fold barrier)."
  [world]
  (let [;; Clear stale absorb-accrete from ALL entities (the integrator consumed
        ;; last tick's; if we don't clear, lingering stale packets double-count).
        world (reduce (fn [w eid]
                        (ecs/remove-component w eid c/absorb-accrete))
                      world
                      (keys (get-in world [:components c/absorb-accrete] {})))
        sinks       (ecs/entities-with world c/matter-state c/accretion-radius c/position c/mass)
        gas-parcels (ecs/entities-with world c/matter-state c/position c/mass c/velocity)
        ;; Precompute star positions + luminosities for feedback
        star-data (mapv (fn [eid]
                          {:pos (ecs/get-component world eid c/position)
                           :lum (double (or (ecs/get-component world eid c/luminosity) 0.0))})
                        (filterv #(= :star (ecs/get-component world % c/matter-state))
                                 (ecs/entities-with world c/matter-state c/position c/luminosity)))]
    (if (empty? sinks)
      world
      (reduce
       (fn [w sink-eid]
         (if-not (ecs/alive? w sink-eid)
           w
           (let [sink-pos (ecs/get-component w sink-eid c/position)
                 sink-acc (double (or (ecs/get-component w sink-eid c/accretion-radius) 0.0))
                 sink-m   (double (or (ecs/get-component w sink-eid c/mass) 0.0))
                 bias     (imf-accretion-bias sink-m)
                 nearby   (filterv
                           (fn [eid]
                             (and (not= eid sink-eid)
                                  (ecs/alive? w eid)
                                  (nil? (ecs/get-component w eid c/consumed-accrete))
                                  (let [pstate (ecs/get-component w eid c/matter-state)]
                                    (and
                                     (or (= :nebula pstate) (= :debris pstate))
                                     (let [pos  (ecs/get-component w eid c/position)
                                           dist (sp/dist sink-pos pos)]
                                       (and (< dist sink-acc)
                                               ;; IMF bias: probabilistic accretion for high-mass sinks
                                            (< (hash01 (hash [eid sink-eid (:tick world)])) bias)
                                            (if (= :nebula pstate)
                                                 ;; Stellar feedback: reject gas heated above Jeans temp
                                              (< (stellar-feedback-temperature pos star-data feedback-radius)
                                                 1.0e4) ;; ~10⁴ K suppresses Jeans
                                                 ;; Solid debris: hierarchical capture — a sink only
                                                 ;; swallows a planetesimal LESS massive than itself,
                                                 ;; so the larger body grows (and the swarm shrinks)
                                                 ;; rather than two equals double-absorbing.
                                              (< (double (or (ecs/get-component w eid c/mass) 0.0))
                                                 sink-m))))))))
                           gas-parcels)]
             (if (seq nearby)
               (absorb-parcels w sink-eid nearby)
               w))))
       world
       sinks))))

;; --- Stellar formation: IMF, disks, feedback (Phase 1) ----------------------
;; Three improvements to the formation pipeline:
;; 1. IMF bias: mass-dependent accretion efficiency steers toward Kroupa distribution
;; 2. Disk formation: angular momentum of accreted material → protoplanetary disk
;; 3. Stellar feedback: UV heating suppresses Jeans collapse near hot stars

(defn- hash01
  "Deterministic [0,1) value from an integer key — for stable, non-random
   per-entity decisions. Used by IMF bias for accretion probability."
  [n]
  (/ (double (mod (* (+ 1 (long n)) 2654435761) 1000003)) 1000003.0))

(def ^:const solar-mass-kg 1.989e30) ;; kg

(defn imf-accretion-bias
  "Mass-dependent accretion efficiency bias from the Kroupa IMF.
   Returns a factor in (0, 1] that multiplies the accretion probability.
   Low-mass sinks accrete efficiently (factor ~1); high-mass sinks are
   suppressed (factor < 1) to steer toward the observed IMF slope.

   Kroupa slopes: α₀ = -0.3 (m < 0.08 M☉), α₁ = -1.3 (0.08-0.5 M☉),
   α₂ = -2.3 (m > 0.5 M☉, Salpeter). We use the INVERSE of the slope
   as the bias: high-mass sinks have positive exponent → factor < 1."
  [mass]
  (let [m (double (or mass 0.0))
        m-msun (/ m solar-mass-kg)]
    (cond
      (< m-msun 0.08) 1.0                    ;; brown dwarf regime: no suppression
      (< m-msun 0.5)  (Math/pow (/ 0.5 m-msun) 0.15) ;; gentle suppression
      (< m-msun 2.0)  (Math/pow (/ 1.0 m-msun) 0.25) ;; moderate suppression
      :else            (Math/pow (/ 2.0 m-msun) 0.4)))) ;; strong suppression for O-stars

(defn stellar-feedback-temperature
  "Temperature added to a gas parcel by UV radiation from nearby stars.
   Uses the bolometric luminosity of all stars within a feedback radius.
   Returns the additional temperature (K) from UV heating."
  [gas-pos star-data fb-radius]
  (reduce (fn [acc {:keys [pos lum]}]
            (let [d (sp/dist gas-pos pos)]
              (if (< d fb-radius)
                ;; UV heating: F = L/(4πd²), ΔT = F·dt/(m·c_p) simplified
                ;; Use a calibrated scaling: ΔT ∝ L/d²
                (let [F (/ (double lum) (* 4.0 Math/PI d d))]
                  (+ acc (* 0.01 F))) ;; calibrated to suppress Jeans collapse
                acc)))
          0.0 star-data))

(def ^:const feedback-radius
  "Distance (m) within which stellar UV feedback suppresses Jeans collapse.
   ~0.5 AU — the photoevaporation radius of a typical HII region."
  7.5e10)

;; --- Disk formation: angular momentum tracking --------------------------------
;; When gas accretes onto a sink, the infalling material carries angular momentum
;; relative to the sink's center. If the specific angular momentum is high enough,
;; the material forms a disk rather than falling directly onto the star.

(def ^:const disk-formation-threshold
  "Minimum specific angular momentum (m²/s) for disk formation.
   Below this, material accretes directly. Above, a disk forms.
   Typical value: ~10¹⁵ m²/s for a solar-mass star at 0.1 AU."
  1.0e15)

;; --- Stellar winds: mass loss as gas (phase A of the winds spec) -------------

(def ^:const speed-of-light 2.99792458e8) ;; m/s

(defn disk-radius
  "Outer radius (m) of a centrifugally-supported disk from specific angular
   momentum: r_disk = j² / (G M). The disk forms where rotation balances gravity."
  [specific-angular-momentum mass]
  (let [j (double (or specific-angular-momentum 0.0))
        M (double (or mass 0.0))]
    (if (and (pos? j) (pos? M))
      (/ (* j j) (* law/G M))
      0.0)))

(def ^:const disk-viscous-alpha
  "Shakura-Sunyaev viscosity parameter. α ~ 0.01 for typical protoplanetary disks."
  0.01)

(def ^:const disk-sound-speed
  "Characteristic sound speed in a protoplanetary disk (m/s). ~300 m/s at 1 AU."
  300.0)

(def ^:const disk-fragment-threshold
  "Disk-to-star mass ratio above which the disk becomes gravitationally unstable
   and fragments into planetary embryos. From Toomre instability: Q = c_s Ω / (π G Σ) < 1.
   Empirically, M_disk/M_star > 0.1 triggers fragmentation."
  0.1)

(def ^:const binary-fragment-threshold
  "Disk-to-star mass ratio above which the disk fragments into a stellar companion.
   Much more massive disk needed for binary formation. ~0.5 M_star."
  0.5)

(defn disk-viscous-timescale
  "Viscous timescale (s) for a protoplanetary disk: t_visc = R² / (α c_s H)
   where H = c_s/Ω is the disk scale height. For a Keplerian disk at radius R:
   t_visc ~ R² / (α c_s² / Ω_K) ~ R^(3/2) / (α c_s²) × √(G M)."
  [dsk-rad mass]
  (let [R (double (or dsk-rad 0.0))
        M (double (or mass 0.0))]
    (if (and (pos? R) (pos? M))
      ;; t_visc = R^(3/2) / (α × c_s^2) × √(G M / R)  ≈  R² / (α × c_s × H)
      ;; Simplified: t_visc ~ 1e6 yr × (R/AU)^1.5 × (M_sun/M)^0.5
      (let [R-au (/ R 1.5e11)
            M-msun (/ M solar-mass-kg)]
        (* 1.0e6 3.15e7 ;; 1 Myr in seconds
           (Math/pow (max 0.01 R-au) 1.5)
           (Math/pow (max 0.01 M-msun) -0.5)
           (/ 0.01 disk-viscous-alpha)))
      1.0e13))) ;; fallback: ~300 kyr

(def ^:const min-fragment-orbit-periods
  "A fragment (planet embryo / binary companion) must be placed on an orbit whose
   period spans at least this many integration steps. Below it the leapfrog step
   (`x' = x + v·dt`) overshoots the whole orbit in one tick, so the integrator
   flings the fragment off on a near-straight line at its Keplerian speed instead
   of letting it orbit — the 'debris flung everywhere' ejection. 50 steps/orbit
   keeps the orbit resolved and the fragment bound." 50.0)

(defn resolvable-orbit-radius
  "Smallest orbital radius around mass `M` whose period is ≥ `min-periods`·`dt`.
   T = 2π√(r³/GM) ≥ min-periods·dt  ⇒  r ≥ ∛(GM·(min-periods·dt / 2π)²). A fragment
   placed at this radius (on a circular orbit) is bound AND resolvable at the
   current timestep, so it stays in the system rather than being ejected."
  [M dt min-periods]
  (let [M  (double (or M 0.0))
        dt (double (or dt 0.0))
        k  (double (or min-periods 1.0))]
    (if (and (pos? M) (pos? dt))
      (Math/cbrt (* law/G M (Math/pow (/ (* k dt) (* 2.0 Math/PI)) 2)))
      0.0)))

(defn disk-evolution-system
  "Fan-out emitter: evolves protoplanetary disks on the viscous timescale and
   triggers planet/binary formation via gravitational instability.

   1. Absorb-accrete processing: reads c/absorb-accrete packets from sink-formation
      and adds disk-routed mass/angmom to c/disk-mass and c/disk-angular-mom (spec §5).
   2. Viscous accretion: transfers disk mass to the star at Ṁ = M_disk / t_visc.
      Angular momentum is conserved — the star spins up, disk shrinks.
   3. Planet formation: when M_disk/M_star > 0.1 (Toomre instability), the disk
      fragments into planetary embryos. Emits c/spawn-request-disk.
   4. Binary formation: when M_disk/M_star > 0.5, the disk fragments into a
      stellar companion (:protostar). Emits c/spawn-request-disk.

   Fragment spawns are materialized next tick by materialize-lifecycle (one-tick
   Jacobi delay). Runs in the parallel fan-out (was a post-fold barrier)."
  [world]
  (let [dt (double (or (:sim/dt world) 1.0e12))
        ;; Incorporate disk-routed absorb-accrete packets from sink-formation
        world (reduce-kv
               (fn [w eid packets]
                 (let [disk-pkts (filter :disk-route packets)]
                   (if (seq disk-pkts)
                     (let [add-mass (reduce + 0.0 (map :mass disk-pkts))
                           add-L    (reduce sp/v+ [0.0 0.0 0.0] (map :angular-momentum disk-pkts))
                           old-dm   (double (or (ecs/get-component w eid c/disk-mass) 0.0))
                           old-L    (or (ecs/get-component w eid c/disk-angular-mom) [0.0 0.0 0.0])]
                       (-> w
                           (ecs/put-component eid c/disk-mass (+ old-dm add-mass))
                           (ecs/put-component eid c/disk-angular-mom (sp/v+ old-L add-L))))
                     w)))
               world
               (get-in world [:components c/absorb-accrete] {}))]
    (reduce
     (fn [w eid]
       (if-not (ecs/alive? w eid)
         w
         (let [M       (double (or (ecs/get-component w eid c/mass) 0.0))
               disk-m  (double (or (ecs/get-component w eid c/disk-mass) 0.0))
               disk-L  (or (ecs/get-component w eid c/disk-angular-mom) [0.0 0.0 0.0])
               disk-j  (sp/len disk-L)]
           (if-not (and (pos? M) (pos? disk-m))
             w
             (let [ratio    (/ disk-m M)
                    ;; Disk outer radius from angular momentum
                   r-disk   (disk-radius (/ disk-j (max 1.0 disk-m)) M)
                   t-visc   (disk-viscous-timescale r-disk M)
                    ;; Viscous accretion rate: Ṁ = M_disk / t_visc × dt
                   mdot-visc (* disk-m (/ dt t-visc))
                   dm        (min mdot-visc (* 0.05 disk-m)) ;; cap at 5% per tick
                   disk-m'   (- disk-m dm)
                   M'        (+ M dm)
                    ;; Angular momentum transfer: star spins up, disk shrinks
                    ;; L_disk scales with disk mass (assuming same specific L)
                   L-transfer (if (pos? disk-m)
                                (sp/v* disk-L (/ dm disk-m))
                                [0.0 0.0 0.0])
                   L-star    (or (ecs/get-component w eid c/angular-momentum) [0.0 0.0 0.0])
                   L-star'   (sp/v+ L-star L-transfer)
                   disk-L'   (sp/v- disk-L L-transfer)
                    ;; Emit influences (integrator owns mass/angmom/spin — spec §7.5)
                   w' (-> w
                          (ecs/put-component eid c/disk-mass disk-m')
                          (ecs/put-component eid c/disk-angular-mom disk-L')
                          (ecs/put-component eid c/mass-flux-disk dm)
                          (ecs/put-component eid c/torque-disk L-transfer))]
                ;; Check for gravitational instability
               (cond
                  ;; Binary formation: massive disk fragments into companion
                 (> ratio binary-fragment-threshold)
                 (let [companion-m (* 0.3 disk-m') ;; companion gets 30% of disk
                       r-disk-now  (disk-radius (/ (sp/len disk-L') (max 1.0 disk-m')) M')
                        ;; Place companion at half the disk radius, but never inside
                        ;; the dt-resolvable radius (else the integrator flings it).
                       r-orbit     (max (* 0.5 (max 1.0e10 r-disk-now))
                                        (resolvable-orbit-radius M' dt min-fragment-orbit-periods))
                        ;; Circular orbit speed
                       v-orbit     (Math/sqrt (/ (* law/G M') r-orbit))
                        ;; Random orbital phase
                       angle       (* 2.0 Math/PI (hash01 (hash [eid (:tick world) :binary])))
                       pos         (ecs/get-component w' eid c/position)
                       offset      [(* r-orbit (Math/cos angle))
                                    (* r-orbit (Math/sin angle))
                                    0.0]
                       comp-pos    (sp/v+ pos offset)
                       comp-vel    (sp/v+ (ecs/get-component w' eid c/velocity)
                                          [(* (- v-orbit) (Math/sin angle))
                                           (* v-orbit (Math/cos angle))
                                           0.0])
                        ;; Emit spawn request (materialized next tick by materialize-lifecycle)
                       spawn-spec  {:position comp-pos :velocity comp-vel
                                    :mass companion-m
                                    :radius (sphere-radius companion-m 1.0e3)
                                    :matter-state :protostar
                                    :composition (or (ecs/get-component w' eid c/composition)
                                                     default-composition)
                                    :temperature 1000.0}
                       w'' (ecs/put-component w' eid c/spawn-request-disk [spawn-spec])
                        ;; Update disk after fragmentation
                       w''' (-> w''
                                (ecs/put-component eid c/disk-mass (- disk-m' companion-m))
                                (ecs/put-component eid c/disk-angular-mom
                                                   (sp/v* disk-L' (/ (- disk-m' companion-m)
                                                                     (max 1.0 disk-m')))))]
                   w''')

                  ;; Planet formation: disk fragments into planetary embryo
                 (> ratio disk-fragment-threshold)
                 (let [embryo-m (* 0.1 disk-m') ;; embryo gets 10% of disk
                       r-disk-now (disk-radius (/ (sp/len disk-L') (max 1.0 disk-m')) M')
                        ;; never inside the dt-resolvable radius (else it is flung)
                       r-orbit   (max (* 0.3 (max 1.0e10 r-disk-now))
                                      (resolvable-orbit-radius M' dt min-fragment-orbit-periods))
                       v-orbit   (Math/sqrt (/ (* law/G M') r-orbit))
                       angle     (* 2.0 Math/PI (hash01 (hash [eid (:tick world) :planet])))
                       pos       (ecs/get-component w' eid c/position)
                       offset    [(* r-orbit (Math/cos angle))
                                  (* r-orbit (Math/sin angle))
                                  0.0]
                       epos      (sp/v+ pos offset)
                       evel      (sp/v+ (ecs/get-component w' eid c/velocity)
                                        [(* (- v-orbit) (Math/sin angle))
                                         (* v-orbit (Math/cos angle))
                                         0.0])
                        ;; Emit spawn request (materialized next tick by materialize-lifecycle)
                       spawn-spec {:position epos :velocity evel
                                   :mass embryo-m
                                   :radius (sphere-radius embryo-m planet-material-density)
                                   :matter-state :debris
                                   :composition (or (ecs/get-component w' eid c/composition)
                                                    default-composition)
                                   :temperature 300.0}
                       w'' (ecs/put-component w' eid c/spawn-request-disk [spawn-spec])
                       w''' (-> w''
                                (ecs/put-component eid c/disk-mass (- disk-m' embryo-m))
                                (ecs/put-component eid c/disk-angular-mom
                                                   (sp/v* disk-L' (/ (- disk-m' embryo-m)
                                                                     (max 1.0 disk-m')))))]
                   w''')

                  ;; Just viscous evolution, no fragmentation
                 :else w'))))))
     world
     (filterv (fn [eid]
                (let [dm (double (or (ecs/get-component world eid c/disk-mass) 0.0))]
                  (pos? dm)))
              (ecs/entities-with world c/matter-state c/mass c/disk-mass)))))

(defn wind-direction
  "A deterministic-but-varying outward unit vector for a wind ejection, seeded by
   entity id + tick (no Math/random — banned, it would break resume). Uniform-ish
   over the sphere so successive ejections fan out instead of streaming one way."
  [eid tick]
  (let [h     (Math/abs (long (hash [eid tick])))
        theta (* 2.0 Math/PI (/ (double (mod h 1009)) 1009.0))
        z     (- (* 2.0 (/ (double (mod (quot h 1009) 1013)) 1013.0)) 1.0)
        r     (Math/sqrt (max 0.0 (- 1.0 (* z z))))]
    [(* r (Math/cos theta)) (* r (Math/sin theta)) z]))

(defn- wind-step
  "Compute one star's wind mass loss / ejection for `stellar-wind-system`.
    `ctx` carries all tick params plus `:sources` (field sources) and `:world`."
  [ctx eid]
  (let [{:keys [world k dt tick p-mass v-fac max-frac abl gas-r sources]} ctx]
    (when (= :star (ecs/get-component world eid c/matter-state))
      (let [M (double (or (ecs/get-component world eid c/mass) 0.0))
            R (double (or (ecs/get-component world eid c/radius) 0.0))]
        (when (and (pos? M) (pos? R))
          (let [region  (entity->region world eid)
                L       (double (star-luminosity region))
                v-esc   (Math/sqrt (/ (* 2.0 law/G M) R))
                shells  (ecs/get-component world eid c/atmosphere-shells)
                sed     (ecs/get-component world eid c/sed-bands)
                corona-t (when shells
                           (some #(when (= :corona (:layer/id %)) (:temperature %)) shells))
                L-xuv   (when sed (lsed/xuv-luminosity (:bands sed)))
                T-escape (/ (* law/m-H v-esc v-esc) (* 2.0 law/k-B))
                v-wind   (if (and corona-t (pos? T-escape))
                           (* v-esc (Math/sqrt (/ (double corona-t) T-escape)))
                           v-esc)
                L-drive  (double (or L-xuv L))
                mdot     (if (pos? v-esc) (/ (* k L-drive) (* v-esc speed-of-light)) 0.0)
                dm       (min (* mdot dt) (* M max-frac))
                resv     (+ (double (or (ecs/get-component world eid c/wind-reservoir) 0.0)) dm)
                M1       (- M dm)]
            (cond
              (<= M1 abl) {:eid eid :consumed true}

              (< resv p-mass)
              {:eid eid :mass-flux (- dm) :reservoir resv}

              :else
              (let [rhat (wind-direction eid tick)
                    pos  (ecs/get-component world eid c/position)
                    acc  (double (or (ecs/get-component world eid c/accretion-radius)
                                     (* 100.0 R)))
                    v-w  (min v-wind (/ (* v-fac acc) (max 1.0 dt)))
                    ppos (sp/v+ pos (sp/v* rhat R))
                    pvel (sp/v+ (ecs/get-component world eid c/velocity) (sp/v* rhat v-w))
                    dv   (sp/v* rhat (- (* (/ p-mass M1) v-w)))
                    ram  (if (pos? R) (/ (* mdot v-w) (* 4.0 Math/PI R R)) 0.0)
                    ion  (if corona-t (min 1.0 (max 0.5 (/ (double corona-t) 1.0e6))) 0.3)
                    extra (cond-> {}
                            (pos? ion) (assoc c/ionization-fraction ion)
                            (pos? ram) (assoc c/ram-pressure ram))]
                {:eid eid :mass-flux (- dm) :reservoir (- resv p-mass) :dv dv
                 :spawn {:position ppos :velocity pvel :mass p-mass
                         :radius gas-r :matter-state :nebula
                         :composition (or (:composition region) default-composition)
                         :b-field (em/net-field-at ppos sources nil)
                         :temperature (max 3.0 (virial-temperature M1 R))
                         :extra-components extra}}))))))))

(defn stellar-wind-system
  "Write-set emitter: stars shed mass as ionized plasma (Parker wind).

    Phase 0 behavior (no atmosphere shells): Ṁ = k·L/(v_esc·c) — single-scattering
    radiation limit, neutral gas parcels.

    Phase 1 behavior (atmosphere shells available): uses coronal temperature T_c
    from c/atmosphere-shells to compute Parker wind speed v_∞ = v_esc·√(T_c/T_esc)
    and XUV luminosity from c/sed-bands to drive mass loss Ṁ ∝ L_XUV/(v_esc·c).

    Sole writer of c/wind-reservoir (its own accumulator) plus the influences the
    integrator/world-construction consume: mass-flux.wind (the loss, negative),
    dv.wind (the ejection recoil), spawn-request.wind (the parcel, tagged with
    ionization/ram via :extra-components), and consumed.wind (a star ablated below
    the floor). A pure snapshot-reading fan-out emitter (was a serial barrier);
    mass and momentum are conserved across the launch (one-tick lag, accepted)."
  []
  {:id     :stellar-wind
   :writes #{c/wind-reservoir c/mass-flux-wind c/dv-wind
             c/spawn-request-wind c/consumed-wind}
   :run
   (fn [world]
     (profile/profile-sections
      world
      [[:stellar-wind/scan
        (fn [w]
          {:world  w
           :k      (double (:phase0/wind-rate-scale w 1.0))
           :dt     (double (or (:sim/dt w) 1.0e12))
           :tick   (or (:tick w) 0)
           :p-mass (double (or (:phase0/wind-parcel-mass w)
                               (some-> (:phase0/gas-particle-mass w) (* 0.25))
                               1.0e27))
           :v-fac  (double (:phase0/wind-speed-factor w 0.15))
           :max-frac (double (:phase0/wind-max-loss-frac w 0.01))
           :abl    (double (:phase0/ablation-floor w (* 1.0e-3 law/deuterium-burning-mass)))
           :gas-r  (double (or (:phase0/gas-smoothing-radius w) 6.0e13))
           :stars  (ecs/entities-with w c/matter-state c/mass c/radius
                                      c/position c/velocity)
           :sources (em/field-sources w)})]
       [:stellar-wind/compute
        (fn [ctx]
          (assoc ctx :results (vec (keep (partial wind-step ctx) (:stars ctx)))))]
       [:stellar-wind/write-set
        (fn [{:keys [world results]}]
           ;; clear stale wind mass-flux / recoil from stars that stopped shedding
          (let [cleared (reduce (fn [ws eid]
                                  (-> ws (assoc-in [c/mass-flux-wind eid] tick/removed)
                                      (assoc-in [c/dv-wind eid] tick/removed)))
                                {} (keys (get-in world [:components c/mass-flux-wind])))]
            (reduce
             (fn [ws {:keys [eid mass-flux reservoir dv spawn consumed]}]
               (cond-> ws
                 consumed     (assoc-in [c/consumed-wind eid] true)
                 mass-flux    (assoc-in [c/mass-flux-wind eid] mass-flux)
                 reservoir    (assoc-in [c/wind-reservoir eid] reservoir)
                 dv           (assoc-in [c/dv-wind eid] dv)
                 spawn        (assoc-in [c/spawn-request-wind eid] [spawn])))
             cleared
             results)))]]))})

(defn stellar-flare-system
  "Fan-out emitter (winds spec phase B): episodic coronal mass ejections.
   Occasionally a star flings a larger, hotter blob along its rotation axis — a
   bright flare/CME riding on top of the steady wind. Mass is debited directly
   from the star (parcel mass = debit, conserved), and the drift is velocity-
   capped exactly like the wind (`v ≤ flare-speed-factor · feeding-zone / dt`), so
   flares never blow the system apart. Bipolar: successive flares alternate poles.

   Tunables: `:phase0/flare-period` (ticks between flares per star; 0 disables),
   `:phase0/flare-mass-factor` (× wind-parcel mass), `:phase0/flare-speed-factor`
   (drift per tick as a fraction of the feeding zone), `:phase0/flare-temp-factor`
   (× virial temperature, for brightness). A flare never fires if it would pull
   the star below half the hydrogen-burning mass — flares decorate, they don't
   demote."
  []
  {:id     :stellar-flare
   :writes #{c/mass-flux-flare c/dv-flare c/spawn-request-flare c/flare-boost}
   :run
   (fn [world]
     (let [period (long (:phase0/flare-period world 0))] ;; default OFF — opt in via :phase0/flare-period
       (if-not (pos? period)
         {}
         (let [sources (em/field-sources world) ;; launch-point field sampler (research §5)
               dt     (double (or (:sim/dt world) 1.0e12))
               tick   (or (:tick world) 0)
               p-mass (double (or (:phase0/wind-parcel-mass world)
                                  (some-> (:phase0/gas-particle-mass world) (* 0.25))
                                  1.0e27))
               m-fac  (double (:phase0/flare-mass-factor world 3.0))
               v-fac  (double (:phase0/flare-speed-factor world 0.4))
               t-fac  (double (:phase0/flare-temp-factor world 3.0))
               gas-r  (double (or (:phase0/gas-smoothing-radius world) 6.0e13))
               floor  (* 0.5 law/hydrogen-burning-mass)
               stars  (ecs/entities-with world c/matter-state c/mass c/radius
                                         c/position c/velocity)
               fires  (keep
                       (fn [eid]
                         (when (and (= :star (ecs/get-component world eid c/matter-state))
                                    (zero? (mod (Math/abs (long (hash [:flare eid tick]))) period)))
                           (let [M  (double (or (ecs/get-component world eid c/mass) 0.0))
                                 R  (double (or (ecs/get-component world eid c/radius) 0.0))
                                 fm (* m-fac p-mass)]
                             (when (and (pos? R) (> (- M fm) floor))
                               (let [region (entity->region world eid)
                                     v-esc  (Math/sqrt (/ (* 2.0 law/G M) R))
                                     acc    (double (or (ecs/get-component world eid c/accretion-radius)
                                                        (* 100.0 R)))
                                     v-fl   (min v-esc (/ (* v-fac acc) (max 1.0 dt)))
                                     axis   (let [a (ecs/get-component world eid c/rotation-axis)]
                                              (if (and a (pos? (sp/len a))) a (wind-direction eid tick)))
                                     sign   (if (even? (mod (Math/abs (long (hash [eid tick]))) 2)) 1.0 -1.0)
                                     rhat   (sp/v* axis sign)
                                     pos    (ecs/get-component world eid c/position)
                                     vel    (ecs/get-component world eid c/velocity)
                                     ppos   (sp/v+ pos (sp/v* rhat R))
                                     pvel   (sp/v+ vel (sp/v* rhat v-fl))
                                     dv     (sp/v* rhat (- (* (/ fm (- M fm)) v-fl)))]
                                 {:eid eid :mass-flux (- fm) :dv dv
                                  :boost {:factor    (* t-fac 10.0)
                                          :decay-tick (+ tick (long (max 1.0 (/ 3.6e3 (max 1.0 dt)))))}
                                  :spawn {:position ppos :velocity pvel :mass fm :radius gas-r
                                          :matter-state :nebula
                                          :composition (or (:composition region) default-composition)
                                          :b-field (em/net-field-at ppos sources nil)
                                          :temperature (max 3.0 (* t-fac (virial-temperature M R)))}})))))
                       stars)]
           (reduce
            (fn [ws {:keys [eid mass-flux dv boost spawn]}]
              (-> ws
                  (assoc-in [c/mass-flux-flare eid] mass-flux)
                  (assoc-in [c/dv-flare eid] dv)
                  (assoc-in [c/flare-boost eid] boost)
                  (assoc-in [c/spawn-request-flare eid] [spawn])))
            ;; clear stale flare mass-flux / recoil from stars that did not fire
            (let [prior (keys (get-in world [:components c/mass-flux-flare]))]
              (reduce (fn [ws eid] (-> ws (assoc-in [c/mass-flux-flare eid] tick/removed)
                                       (assoc-in [c/dv-flare eid] tick/removed)))
                      {} prior))
            fires)))))})

;; --- The Structure owner: shape + compactness (double-buffer step 7b) -------
;; radius and density are ONE geometric fact (ρ = m / V, V = 4/3π r³), so a
;; single owner computes the pair, with the primary↔derived direction flipping
;; by regime. This is the future home of the voxel shape representation (note §7).

(def ^:const debris-material-density 2.0e3) ;; kg/m³ — rocky planetesimal
(def ^:const planet-material-density 1.0e3) ;; kg/m³ — mixed rock/ice/volatile

(defn sphere-radius
  "Radius of a uniform sphere of `mass` at material `density`: r = (3m/4πρ)^(1/3)."
  [mass density]
  (Math/pow (/ (* 3.0 (double mass)) (* 4.0 Math/PI (double density))) (/ 1.0 3.0)))

(defn resolved-shape
  "Shape + compactness for a RESOLVED body, by matter-state. Solids are
   incompressible (fixed material density → radius follows mass); cores contract
   on the Kelvin–Helmholtz timescale toward the main-sequence radius floor,
   flattening under their own angular momentum. Returns a map of the components
   to write (a subset of radius/density/oblateness/rotation-axis)."
  [{:keys [matter-state mass radius oblateness angular-momentum]}
   collapse-fraction contraction-time dt]
  (let [m (double (or mass 0.0))]
    (case matter-state
      :debris (let [r (sphere-radius m debris-material-density)]
                {:radius r :density debris-material-density})
      :planet (let [r (sphere-radius m planet-material-density)]
                {:radius r :density planet-material-density})
      (:protostar :star)
      (let [L     (or angular-momentum [0.0 0.0 0.0])
            o     (double (or oblateness 1.0))
            a     (double (or radius (sphere-radius m planet-material-density)))
            frac  (min (double collapse-fraction)
                       (- 1.0 (Math/exp (- (/ (double dt) (double contraction-time))))))
            floor (law/main-sequence-radius m)
            {:keys [equatorial-radius polar-radius] :as shape}
            (oblate-collapse-shape m L a o frac floor)]
        {:radius        equatorial-radius
         :density       (oblate-density m equatorial-radius polar-radius)
         :oblateness    (:oblateness shape)
         :rotation-axis (:rotation-axis shape)})
      nil)))

(defn structure-system
  "Double-buffer write-set system: SOLE writer of the body's shape and the
   compactness it implies — radius, density, and (for cores) oblateness +
   rotation-axis. Computed per matter-state (design note §7b):
     :nebula           SPH density + adaptive smoothing radius (fluid sample)
     :debris / :planet fixed material density → radius from mass (solid)
     :protostar/:star  KH oblate contraction toward the main-sequence floor
   Replaces the radius/density writes of density-system, jeans-collapse, and
   collapse. The future home of the voxel shape representation."
  []
  {:id     :structure
   :writes #{c/radius c/density c/oblateness c/rotation-axis}
   :run    (fn [world]
             (let [cf (:phase0/collapse-fraction world 0.5)
                   ct (:phase0/contraction-time world 9.5e14)
                   dt (:sim/dt world 1.0e12)
                    ;; gas branch (SPH): density primary, radius derived
                   gas-ws (let [profile? (:phase0/profile-subsystems? world)
                                [gas-results dt-query]
                                (if profile?
                                  (profile/timing #(hydro/gas-structure world))
                                  [(hydro/gas-structure world) 0])]
                            (profile/profile-section
                             world :structure/gas-reduce
                             (fn [_world]
                               (reduce (fn [ws [eid rho r]]
                                         (if (and (lf/finite-number? rho) (pos? rho)
                                                  (lf/finite-number? r) (pos? r))
                                           (-> ws (assoc-in [c/density eid] rho)
                                               (assoc-in [c/radius eid] r))
                                           ws))
                                       (if profile?
                                         {:phase0/_profile {:structure/gas-query (double dt-query)}}
                                         {})
                                       gas-results))))]
               ;; resolved branch: radius primary (or material density), rest derived
               (profile/profile-section
                world :structure/resolved
                (fn [_world]
                  (reduce
                   (fn [ws eid]
                     (let [region (entity->region world eid)]
                       (if-let [s (and (#{:debris :planet :protostar :star}
                                        (:matter-state region))
                                       (resolved-shape region cf ct dt))]
                         (cond-> ws
                           (:radius s)        (assoc-in [c/radius eid] (:radius s))
                           (:density s)       (assoc-in [c/density eid] (:density s))
                           (:oblateness s)    (assoc-in [c/oblateness eid] (:oblateness s))
                           (:rotation-axis s) (assoc-in [c/rotation-axis eid] (:rotation-axis s)))
                         ws)))
                   gas-ws
                   (ecs/entities-with world c/matter-state c/mass c/radius))))))})

(defn temperature-system
  "Double-buffer write-set system: SOLE writer of temperature.
     :protostar / :star  T = virial temperature G M m_H / (k_B R) — compression
                         (Kelvin–Helmholtz) heating that RISES as Structure
                         contracts the radius, carrying the core to ignition. A
                         pure derivation from mass + radius (no frozen reference).
     :debris / :planet   radiative: cool toward the CMB, warmed by nearby stars.
     :nebula             skipped — diffuse gas stays at its seeded background.
   Replaces collapse's compression heating and the legacy thermal-system."
  [dt]
  {:id     :thermal
   :writes #{c/temperature}
   :run    (fn [world]
             (let [stars     (ecs/entities-with world c/matter-state c/luminosity c/position)
                   star-lums (mapv #(ecs/get-component world % c/luminosity) stars)
                   star-poss (mapv #(ecs/get-component world % c/position) stars)
                    ;; SED bands for band-aware heating (nil for stars without SED)
                   star-bands (mapv #(some-> (ecs/get-component world % c/sed-bands)
                                             :bands)
                                    stars)
                   eids      (ecs/entities-with world c/matter-state c/temperature
                                                c/density c/radius c/mass c/position)
                   cells (par/par-mapv
                          (fn [eid]
                            (let [region (entity->region world eid)
                                  state  (:matter-state region)
                                  m      (:mass region)
                                  r      (:radius region)]
                              (cond
                                (and (#{:protostar :star} state) m r)
                                [eid (virial-temperature m r)]

                                (#{:debris :planet} state)
                                (let [pos       (:position region)
                                      star-heat (reduce
                                                 (fn [acc [lum spos bands]]
                                                   (if (pos? (double (or lum 0.0)))
                                                     (let [dist (sp/dist pos spos)]
                                                       (+ acc (if bands
                                                                (sed-heating-delta region bands dist dt)
                                                                (radiation-heating-delta region lum dist dt))))
                                                     acc))
                                                 0.0 (map vector star-lums star-poss star-bands))
                                      t    (double (or (:temperature region) 3.0))
                                      drp (radiative-cooling-delta region dt)]
                                  [eid (max 3.0 (- (+ t star-heat) drp))])

                                :else nil)))
                          eids)]
               {c/temperature (into {} (keep identity) cells)}))})

(defn eos-system
  "Double-buffer write-set system: pressure as the pure equation of state
   P = ρ k_B T / m_H (`law/ideal-gas-pressure`) for every body carrying density
   and temperature. Sole writer of pressure — the single-writer replacement for
   the four legacy systems (density / jeans-collapse / collapse / thermal) that
   each recomputed this identical ideal-gas pressure. Reads ρ and T from the
   frozen snapshot (one-tick latency, negligible for a derived quantity)."
  []
  {:id     :eos
   :writes #{c/pressure}
   :run    (fn [world]
             (let [eids (ecs/entities-with world c/density c/temperature)]
               {c/pressure
                (into {}
                      (keep (fn [eid]
                              (let [rho (ecs/get-component world eid c/density)
                                    t   (ecs/get-component world eid c/temperature)]
                                (when (and rho t)
                                  [eid (law/ideal-gas-pressure rho t)]))))
                      eids)}))})

;; --- Accretion (collision response) -----------------------------------------

(defn- shatter-bodies
  "Brittle collision response: the smaller body breaks into two :debris fragments
   (mass AND momentum conserved — each fragment is half the mass with symmetric
   ± split velocities), the larger body survives. The split axis is deterministic
   (perpendicular to the impact line), fragments are placed clear of the larger
   body, and over the Myr-scale dt they immediately disperse — so this does not
   cascade. Makes law.stellar/malleability load-bearing (cold brittle bodies
   shatter; hot molten ones fall through to the merge path).

   Emits c/spawn-request-shatter (handled by materialize-lifecycle) and
   c/consumed-merge instead of inline spawning — no single-writer violation."
  [world big small ms*]
  (let [ms      (double (:mass ms*))
        rs      (double (:radius ms*))
        pos     (ecs/get-component world small c/position)
        vs      (ecs/get-component world small c/velocity)
        big-pos (ecs/get-component world big c/position)
        rl      (double (or (ecs/get-component world big c/radius) 0.0))
        away    (let [d (sp/v- pos big-pos) l (sp/len d)]
                  (if (pos? l) (sp/v* d (/ 1.0 l)) [1.0 0.0 0.0]))
        perp    (let [rf (if (> (Math/abs (double (nth away 0))) 0.9)
                           [0.0 1.0 0.0] [1.0 0.0 0.0])
                      x   (sp/cross away rf) l (sp/len x)]
                  (if (pos? l) (sp/v* x (/ 1.0 l)) [0.0 1.0 0.0]))
        frag-m  (* 0.5 ms)
        frag-r  (* rs (Math/cbrt 0.5))
        center  (sp/v+ big-pos (sp/v* away (* 1.2 (+ rl frag-r)))) ;; clear of big
        sep     (* 1.5 frag-r)
        dvs     (min 50.0 (* 0.1 (sp/len vs)))   ;; tiny symmetric split (momentum-conserving)
        spec    (fn [s] {:position     (sp/v+ center (sp/v* perp (* s sep)))
                         :velocity     (sp/v+ vs (sp/v* perp (* s dvs)))
                         :mass         frag-m :radius frag-r :matter-state :debris
                         :composition  (:composition ms*)
                         :temperature  (:temperature ms*)})]
    (cond-> world
      true (ecs/put-component big c/spawn-request-shatter
                              [(spec 1.0) (spec -1.0)])
      true (ecs/put-component small c/consumed-merge true))))

(defn stellar-merge-handler
  "Collision handler that merges the smaller body into the larger AND blends
    their stellar state (mass-weighted composition, max temperature, conserved
    momentum AND angular momentum, volume-summed radius). Registered for
    :event/collision."
  [world event]
  (let [{:keys [eid-a eid-b]} (:payload event)]
    (if (and (ecs/alive? world eid-a) (ecs/alive? world eid-b))
      (let [a (entity->region world eid-a)
            b (entity->region world eid-b)
            ma (double (:mass a)) mb (double (:mass b))
            [big small mb* ms*] (if (>= ma mb) [eid-a eid-b a b] [eid-b eid-a b a])
            va (ecs/get-component world big c/velocity)
            vs (ecs/get-component world small c/velocity)
            t-cold (min (double (or (:temperature mb*) 0.0))
                        (double (or (:temperature ms*) 0.0)))
            ;; Population guard: shatter is the only collision outcome that ADDS a
            ;; body (small → 2 fragments, net +1). Left unbounded it inflates the
            ;; debris swarm faster than accretion can drain it, and the resolved
            ;; N-body cost (gravity + broad-phase) climbs with it. Once the swarm
            ;; is already large, fall through to the inelastic merge (which removes
            ;; a body) instead of shattering. Uses the prior tick's resolved count
            ;; (O(1), carried on the snapshot) — exact count is unnecessary for a
            ;; soft cap. Disable with :phase0/max-resolved-bodies 0.
            budget (long (:phase0/max-resolved-bodies world 400))
            resolved (long (or (get-in world [:phase0/stats :resolved-count]) 0))]
        (if (and (> (double (:mass ms*)) law/shatter-min-mass)
                 (< (law/malleability t-cold) law/shatter-malleability-max)
                 (> (sp/len (sp/v- va vs)) law/shatter-dv-threshold)
                 (or (<= budget 0) (< resolved budget)))
          ;; brittle body + hard impact → the smaller shatters into debris
          (shatter-bodies world big small ms*)
          ;; molten or gentle impact → inelastic merge (emit absorb-merge packet)
          ;; The integrator applies mass, velocity, position (mass-weighted blend),
          ;; angular-momentum, temperature (with impact heating), and composition
          ;; next tick. Derived quantities (radius, density, pressure, oblateness,
          ;; rotation-axis) are re-structured by their single-owner systems —
          ;; one-tick Jacobi delay (spec §9).
          (let [ml (double (:mass mb*)) ms (double (:mass ms*))
                ;; angular momentum: the small body's spin + its orbital AM about
                ;; the big one. The integrator adds this to the survivor's snapshot
                ;; L, so we do NOT include the big body's La here (avoid double-count).
                r-small (ecs/get-component world small c/position)
                r-big   (ecs/get-component world big c/position)
                r-rel   (sp/v- r-small r-big)
                v-rel   (sp/v- vs va)
                Ls  (or (ecs/get-component world small c/angular-momentum)
                        (orbital-angular-momentum ms
                                                  (ecs/get-component world small c/position)
                                                  vs))
                L-orbital-small (orbital-angular-momentum ms r-rel v-rel)
                L-small (sp/v+ Ls L-orbital-small)]
            (cond-> world
              true (ecs/put-component big c/absorb-merge
                                      [{:mass              ms
                                        :velocity          vs
                                        :position          r-small
                                        :angular-momentum  L-small
                                        :composition       (:composition ms*)
                                        :temperature       (:temperature ms*)}])
              true (ecs/put-component small c/consumed-merge true)))))
      world)))

;; --- Nebula seeding ---------------------------------------------------------

(def default-composition
  "Primordial BBN composition by mass fraction. Matches law.composition/primordial-composition
   but kept as a plain map (no trace isotopes) for backward compatibility with systems
   that only check :H :He :metals. Trace isotopes (D, He3, Li7) are added by the
   primordial-composition-system when it runs at world initialization."
  {:H lcomp/primordial-H :He lcomp/primordial-He :metals lcomp/primordial-metals})

(defn seed-clump
  "Return the component map for one nebular clump entity. Carries a magnetic
   field vector (defaulting to the coherent large-scale nebular field) so the
   EM layer and regime classifier have field state from the first tick."
  [{:keys [position velocity mass radius temperature composition matter-state
           body-kind b-field angular-momentum]
    :or   {velocity [0.0 0.0 0.0]
           temperature 10.0
           composition default-composition
           matter-state :nebula
           body-kind :body/gas}}]
  (let [density (body-density mass radius)
        L (or angular-momentum (orbital-angular-momentum mass position velocity))
        spin (spin-from-angular-momentum L mass radius)
        resolved? (not= matter-state :nebula)]
    (cond-> {c/position     position
             c/velocity     velocity
             c/mass         mass
             c/radius       radius
             c/body-kind    body-kind
             c/temperature  temperature
             c/density      density
             c/pressure     (law/ideal-gas-pressure density temperature)
             c/composition  composition
             c/luminosity   0.0
             c/b-field      (or b-field (em/seed-field))
             c/matter-state matter-state
             c/angular-momentum L
             c/spin         spin
             c/oblateness   1.0
             c/rotation-axis (rotation-axis L)}
      resolved? (assoc c/accretion-radius radius))))

(defn spawn-clump
  "Spawn one nebular clump entity from a seed spec. Returns [world eid]."
  [world spec]
  (let [[w eid] (ecs/spawn world)]
    [(ecs/put-components w eid (seed-clump spec)) eid]))
