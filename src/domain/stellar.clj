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
    [domain.em             :as em]
    [domain.hydro          :as hydro]
    [domain.ecs.core       :as ecs]
    [domain.ecs.parallel   :as par]
    [domain.ecs.components  :as c]
    [shape.spatial         :as sp]))

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
  [{:keys [body-count star? fusion? planet-count]}]
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
    (reduce (fn [w [eid old new]]
              (if (= old new)
                w
                (let [w (ecs/put-component w eid c/matter-state new)
                      old-gas-radius (when (= old :nebula)
                                       (double (or (ecs/get-component w eid c/radius) 0.0)))]
                  ;; When a clump first promotes out of the gas pool, give it a
                  ;; temporary gravitational feeding radius so it can merge with
                  ;; nearby promoted bodies before orbital spreading separates
                  ;; them. Protostars additionally freeze their pre-contraction
                  ;; radius so they keep sweeping up mass after collapse.
                  (cond
                    (and (= new :protostar)
                         (nil? (ecs/get-component w eid c/accretion-radius)))
                    (ecs/put-component w eid c/accretion-radius
                                       (double (or old-gas-radius
                                                   (ecs/get-component w eid c/radius) 0.0)))
                    (and (= old :nebula)
                         (not= new :nebula)
                         (nil? (ecs/get-component w eid c/accretion-radius)))
                    (ecs/put-component w eid c/accretion-radius
                                       (* 100.0 (double (or old-gas-radius
                                                           (ecs/get-component w eid c/radius) 0.0))))
                    :else w))))
            world
            updates)))

(defn fusion-system
  "Any body whose temperature, pressure, and composition cross the ignition
   thresholds becomes a star: it emits light and stops contracting.

   NOTE: In the sequential pipeline this runs AFTER collapse-system so it sees
   post-contraction density/pressure. In the parallel pipeline the legacy bridge
   masks writes to #{c/luminosity} — the matter-state promotion is handled by
   fusion-promotion-system (a barrier that runs after the fold)."
  [world]
  (reduce
   (fn [w eid]
     (let [region (entity->region world eid)]
       (if (and (not= :star (:matter-state region))
                (law/fusion-possible? region))
         (let [lum (star-luminosity region)]
           (-> w
               (ecs/put-component eid c/luminosity   lum)
               (ecs/put-component eid c/matter-state :star)))
         w)))
   world
   (ecs/entities-with world c/matter-state c/temperature c/pressure c/composition)))

(defn fusion-promotion-system
  "Post-fold barrier system: promotes protostars to stars once the contracted
   density and virial temperature cross the fusion thresholds.

   In the parallel double-buffer pipeline, the classifier (sole writer of
   matter-state) reads from the FROZEN snapshot where density is still the
   pre-contraction value — so the fusion gate never opens on the transition
   tick. This barrier runs AFTER the fold, seeing the updated density from
   structure-system and pressure from eos-system, and performs the promotion
   that the classifier missed.

   Also refreshes luminosity for any existing :star whose luminosity is zero
   (fixes stars promoted before this system existed)."
  [world]
  (let [eids (ecs/entities-with world c/matter-state c/temperature c/pressure
                                c/composition c/density c/radius c/mass)]
    (reduce
     (fn [w eid]
       (let [state (ecs/get-component w eid c/matter-state)
             region (entity->region w eid)]
         (cond
           ;; Protostar that now meets fusion conditions → promote to star
           (and (= :protostar state) (law/fusion-possible? region))
           (let [lum (star-luminosity region)]
             (-> w
                 (ecs/put-component eid c/luminosity   lum)
                 (ecs/put-component eid c/matter-state :star)))

           ;; Existing star with zero luminosity → recompute
           (and (= :star state) (law/fusion-possible? region)
                (let [lum (double (or (ecs/get-component w eid c/luminosity) 0.0))]
                  (zero? lum)))
           (ecs/put-component w eid c/luminosity (star-luminosity region))

           :else w)))
     world
     eids)))

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

(defn thermal-system
  "Radiative cooling + stellar irradiation heating. Diffuse :nebula gas stays at
   its seeded background temperature (thermal equilibrium with the ISRF).
   Resolved bodies cool toward the CMB floor, but are first heated by any nearby
   stars so inner debris/planets stay hot. The large dynamical timestep makes
   exponential cooling toward the floor necessary to avoid instant freezing."
  [dt]
  (fn [world]
    (let [stars       (ecs/entities-with world c/matter-state c/luminosity c/position)
          star-lums   (mapv #(ecs/get-component world % c/luminosity) stars)
          star-poss   (mapv #(ecs/get-component world % c/position) stars)
          eids        (ecs/entities-with world c/matter-state c/temperature c/density c/radius c/position)
          updates     (par/par-mapv
                       (fn [eid]
                         (let [region (entity->region world eid)
                               state  (:matter-state region)
                               pos    (ecs/get-component world eid c/position)]
                           (when-not (= :star state)
                             (let [star-heat (reduce
                                               (fn [acc [lum star-pos]]
                                                 (if (pos? (double lum))
                                                   (+ acc (radiation-heating-delta
                                                            region lum (sp/dist pos star-pos) dt))
                                                   acc))
                                               0.0
                                               (map vector star-lums star-poss))
                                   temp (:temperature region)
                                   new-temp
                                   (if (= :nebula state)
                                     temp
                                     (let [drop   (radiative-cooling-delta region dt)
                                           heated (+ temp star-heat)
                                           target 3.0]
                                       (max target (- heated drop))))
                                   new-press (law/ideal-gas-pressure (:density region) new-temp)]
                               [eid new-temp new-press]))))
                       eids)]
      (reduce (fn [w u]
                (if u
                  (let [[eid t p] u]
                    (-> w
                        (ecs/put-component eid c/temperature t)
                        (ecs/put-component eid c/pressure p)))
                  w))
              world
              updates))))

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
  [mass sound-speed]
  (let [m (double (or mass 0.0))
        cs (double (or sound-speed 0.0))]
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
      :star        :star
      :brown-dwarf :brown-dwarf
      :planet      :planet
      :protostar   (cond
                     (and (>= m law/hydrogen-burning-mass)
                          (law/fusion-possible? region))
                     :star

                     (and (>= m law/deuterium-burning-mass)
                          (<  m law/hydrogen-burning-mass)
                          (contraction-stalled? radius m temperature))
                     :brown-dwarf

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
   :run
   (fn [world]
     (let [gas-mass (:phase0/gas-particle-mass world)
           eids     (ecs/entities-with world c/matter-state c/mass)
           zones    (sink-exclusion-zones world)
           may-condense? (condense-tick? world)
           transitions
           (into {}
                 (keep (fn [eid]
                         (let [region (entity->region world eid)
                               cur    (:matter-state region)
                               nxt    (classify-next-state region gas-mass zones)]
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
           factor (double (:phase0/feeding-zone-factor world feeding-zone-factor))
           gas-r  (double (or (:phase0/gas-smoothing-radius world) 0.0))
           acc-radius
           (when best-condense
             ;; Use the GAS smoothing radius (stored at world creation), not the
             ;; post-condensation body radius. The gas radius is the smoothing
             ;; length BEFORE KH contraction shrinks the photosphere, so the
             ;; feeding zone is wide enough for the core to sweep up neighbors.
             (when (pos? gas-r) (* factor gas-r)))]
       (cond-> {c/matter-state applied}
         acc-radius (assoc c/accretion-radius {best-condense acc-radius}))))})

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
  "Merge mass + momentum from `parcels` into `sink-eid`, despawn the parcels.
   Mass-weighted centroid for position preserves COM."
  [world sink-eid parcels]
  (let [sink-mass (double (or (ecs/get-component world sink-eid c/mass) 0.0))
        sink-v    (or (ecs/get-component world sink-eid c/velocity) [0 0 0])
        sink-p    (or (ecs/get-component world sink-eid c/position) [0 0 0])
        ;; Sum absorbed mass + momentum + mass-weighted position
        absorbed  (reduce
                    (fn [acc eid]
                      (let [m (double (or (ecs/get-component world eid c/mass) 0.0))
                            v (or (ecs/get-component world eid c/velocity) [0 0 0])
                            p (or (ecs/get-component world eid c/position) [0 0 0])]
                        {:total-mass (+ (:total-mass acc) m)
                         :px (+ (:px acc) (* m (double (nth v 0))))
                         :py (+ (:py acc) (* m (double (nth v 1))))
                         :pz (+ (:pz acc) (* m (double (nth v 2))))
                         :cx (+ (:cx acc) (* m (double (nth p 0))))
                         :cy (+ (:cy acc) (* m (double (nth p 1))))
                         :cz (+ (:cz acc) (* m (double (nth p 2))))}))
                    {:total-mass 0.0 :px 0.0 :py 0.0 :pz 0.0 :cx 0.0 :cy 0.0 :cz 0.0}
                    parcels)
        new-mass (+ sink-mass (:total-mass absorbed))
        ;; Momentum conservation: mass-weighted velocity
        old-px (* sink-mass (double (nth sink-v 0)))
        old-py (* sink-mass (double (nth sink-v 1)))
        old-pz (* sink-mass (double (nth sink-v 2)))
        new-v   [(/ (+ old-px (:px absorbed)) new-mass)
                 (/ (+ old-py (:py absorbed)) new-mass)
                 (/ (+ old-pz (:pz absorbed)) new-mass)]
        ;; COM preservation: mass-weighted position
        old-cx (* sink-mass (double (nth sink-p 0)))
        old-cy (* sink-mass (double (nth sink-p 1)))
        old-cz (* sink-mass (double (nth sink-p 2)))
        new-pos [(/ (+ old-cx (:cx absorbed)) new-mass)
                 (/ (+ old-cy (:cy absorbed)) new-mass)
                 (/ (+ old-cz (:cz absorbed)) new-mass)]
        ;; Update sink
        w (-> world
              (ecs/put-component sink-eid c/mass new-mass)
              (ecs/put-component sink-eid c/velocity new-v)
              (ecs/put-component sink-eid c/position new-pos))]
    ;; Despawn absorbed parcels
    (reduce (fn [w eid] (ecs/despawn w eid)) w parcels)))

(defn sink-formation-system
  "Serial barrier system: for newly-formed sinks (debris/protostar with accretion-radius),
   absorb all :nebula parcels within their accretion radius. This is the convert-and-seed
   mechanism from Bate et al. (1995) — newly formed sinks eat their neighbors on the
   tick they condense, preventing the cloud from condensing wholesale.

   Stars and established sinks grow through the collision system (overlap merges),
   NOT through this system. This prevents the central star from vacuuming up all
   disk gas before planets can form.

   Runs AFTER the parallel fan-out and collision detection, at the serial barrier.
   Mass + momentum are conserved (mass-weighted centroid for position)."
  [world]
  (let [;; Only NEWLY-FORMED sinks: debris or protostar WITH accretion-radius.
        ;; Stars grow through collisions, not through absorbing surrounding gas.
        sinks       (->> (ecs/entities-with world c/matter-state c/accretion-radius c/position c/mass)
                         (filterv (fn [eid]
                                    (let [state (ecs/get-component world eid c/matter-state)]
                                      (or (= :debris state) (= :protostar state))))))
        ;; All nebula parcels that could be absorbed
        gas-parcels (ecs/entities-with world c/matter-state c/position c/mass c/velocity)]
    (if (empty? sinks)
      world
      ;; For each new sink, absorb nearby gas parcels within its accretion radius
      (reduce
        (fn [w sink-eid]
          (if-not (ecs/alive? w sink-eid)
            w
            (let [sink-pos (ecs/get-component w sink-eid c/position)
                  sink-acc (double (or (ecs/get-component w sink-eid c/accretion-radius) 0.0))
                  nearby   (filterv
                             (fn [eid]
                               (and (not= eid sink-eid)
                                    (ecs/alive? w eid)
                                    (= :nebula (ecs/get-component w eid c/matter-state))
                                    (< (sp/dist sink-pos (ecs/get-component w eid c/position))
                                       sink-acc)))
                             gas-parcels)]
              (if (seq nearby)
                (absorb-parcels w sink-eid nearby)
                w))))
        world
        sinks))))

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
                   gas-ws (reduce (fn [ws [eid rho r]]
                                    (if (and (lf/finite-number? rho) (pos? rho)
                                             (lf/finite-number? r) (pos? r))
                                      (-> ws (assoc-in [c/density eid] rho)
                                             (assoc-in [c/radius eid] r))
                                      ws))
                                  {} (hydro/gas-structure world))]
               ;; resolved branch: radius primary (or material density), rest derived
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
                 (ecs/entities-with world c/matter-state c/mass c/radius))))})

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
                                                   (fn [acc [lum spos]]
                                                     (if (pos? (double (or lum 0.0)))
                                                       (+ acc (radiation-heating-delta
                                                                region lum (sp/dist pos spos) dt))
                                                       acc))
                                                   0.0 (map vector star-lums star-poss))
                                       t    (double (or (:temperature region) 3.0))
                                       drop (radiative-cooling-delta region dt)]
                                   [eid (max 3.0 (- (+ t star-heat) drop))])

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
            ml (double (:mass mb*)) ms (double (:mass ms*))
            total (+ ml ms)
            va (ecs/get-component world big c/velocity)
            vs (ecs/get-component world small c/velocity)
            v' (let [px (+ (* (nth va 0) ml) (* (nth vs 0) ms))
                     py (+ (* (nth va 1) ml) (* (nth vs 1) ms))
                     pz (+ (* (nth va 2) ml) (* (nth vs 2) ms))]
                 [(/ px total) (/ py total) (/ pz total)])
            rl (double (:radius mb*)) rs (double (:radius ms*))
            ;; Conserve the SURVIVOR's density: the accreted mass is added at the
            ;; larger body's density (r' = rl·(total/ml)^⅓), NOT by volume-summing
            ;; the two radii. Volume-summing treats a parcel's fluid *smoothing*
            ;; radius as a material radius, so a compact star (R~1e8 m) absorbing a
            ;; freshly-condensed, still-diffuse body (R~1e11–1e13 m) ballooned to
            ;; cloud size. temperature-system then derives T = virial(M,R) ∝ 1/R
            ;; every tick, collapsing the bloated star to ~1e3 K, and structure
            ;; never re-contracts a :star — so the star turned into a cold diffuse
            ;; blob and "vanished". Density-conserving accretion keeps it compact.
            r' (if (and (pos? rl) (pos? ml))
                 (* rl (Math/cbrt (/ total ml)))
                 (Math/cbrt (+ (* rl rl rl) (* rs rs rs))))
            ;; Preserve the gravitational feeding zone: a star-forming body keeps
            ;; the larger accretion radius of the two (never below the merged
            ;; photosphere). nil if neither participant was star-forming.
            acc-big   (ecs/get-component world big c/accretion-radius)
            acc-small (ecs/get-component world small c/accretion-radius)
            acc' (when (or acc-big acc-small)
                   (max (double (or acc-big 0.0)) (double (or acc-small 0.0)) r'))
            comp (let [cl (or (:composition mb*) {}) cs (or (:composition ms*) {})]
                   (into {} (for [k (into (set (keys cl)) (keys cs))]
                              [k (/ (+ (* (get cl k 0.0) ml) (* (get cs k 0.0) ms))
                                    total)])))
            ;; mass-weighted base temperature plus impact heating: the kinetic
            ;; energy lost in the inelastic merge raises the merged body's temp,
            ;; so high-speed impacts flare hot (ΔT = E_lost / (M·c_p)).
            base-temp (/ (+ (* (or (:temperature mb*) 0.0) ml)
                            (* (or (:temperature ms*) 0.0) ms))
                         total)
            dvx (- (double (nth va 0)) (double (nth vs 0)))
            dvy (- (double (nth va 1)) (double (nth vs 1)))
            dvz (- (double (nth va 2)) (double (nth vs 2)))
            e-lost (* 0.5 (/ (* ml ms) total)
                      (+ (* dvx dvx) (* dvy dvy) (* dvz dvz)))
            impact-dt (/ (* e-lost law/m-H) (* total 2.5 law/k-B))
            temp' (+ base-temp impact-dt)
            dens' (body-density total r')
            ;; angular momentum conservation: orbital + spin of both bodies
            La  (or (ecs/get-component world big c/angular-momentum)
                    (orbital-angular-momentum ml
                                              (ecs/get-component world big c/position)
                                              va))
            Ls  (or (ecs/get-component world small c/angular-momentum)
                    (orbital-angular-momentum ms
                                              (ecs/get-component world small c/position)
                                              vs))
            ;; add the orbital angular momentum of the small body about the big one
            r-small (ecs/get-component world small c/position)
            r-big   (ecs/get-component world big c/position)
            r-rel   (sp/v- r-small r-big)
            v-rel   (sp/v- vs va)
            L-orbital-small (orbital-angular-momentum ms r-rel v-rel)
             L-total (sp/v+ (sp/v+ La Ls) L-orbital-small)
             spin' (spin-from-angular-momentum-oblate L-total total r')
             ob'   (oblateness-from-spin spin' r')
             axis' (rotation-axis L-total)
             ;; Place the merged body at the MASS-WEIGHTED CENTROID of the two,
             ;; not at the larger body's position. Otherwise the smaller body's
             ;; mass teleports onto the larger one, the system centre of mass
             ;; jumps, and `recenter-system` re-centres by translating EVERY body
             ;; — so the whole cloud appears to teleport on every merge. The
             ;; centroid conserves the centre of mass, keeping motion fluid.
             pos'  (sp/v* (sp/v+ (sp/v* r-big ml) (sp/v* r-small ms)) (/ 1.0 total))]
         (cond-> world
             true (ecs/put-component big c/position    pos')
             true (ecs/put-component big c/mass        total)
             true (ecs/put-component big c/radius      r')
             true (ecs/put-component big c/velocity    v')
             true (ecs/put-component big c/composition comp)
             true (ecs/put-component big c/temperature temp')
             true (ecs/put-component big c/density     dens')
             true (ecs/put-component big c/pressure    (law/ideal-gas-pressure dens' temp'))
             true (ecs/put-component big c/angular-momentum L-total)
             true (ecs/put-component big c/spin        spin')
             true (ecs/put-component big c/oblateness  ob')
             true (ecs/put-component big c/rotation-axis axis')
             acc' (ecs/put-component big c/accretion-radius acc')
             true (ecs/despawn small)))
       world)))

;; --- Nebula seeding ---------------------------------------------------------

(def default-composition
  "Primordial nebular composition by mass fraction (H/He dominated)."
  {:H 0.75 :He 0.24 :metals 0.01})

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
