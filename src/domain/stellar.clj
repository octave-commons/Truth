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
   [domain.em             :as em]
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
   :mass        (ecs/get-component world eid c/mass)
   :radius      (ecs/get-component world eid c/radius)
   :temperature (ecs/get-component world eid c/temperature)
   :density     (ecs/get-component world eid c/density)
   :pressure    (ecs/get-component world eid c/pressure)
   :composition (ecs/get-component world eid c/composition)
   :matter-state (ecs/get-component world eid c/matter-state)
   :b-field     (ecs/get-component world eid c/b-field)})

;; --- ECS systems ------------------------------------------------------------

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
   (law/mass-class): gas → debris → planet → protostar. Formation is emergent —
   a clump becomes a planet or a star-forming core because it ATE enough gas, not
   because it was seeded that way. Stars never declassify; ignition (protostar →
   star) is left to the fusion system once contraction makes the core hot enough."
  [world]
  (let [eids    (ecs/entities-with world c/matter-state c/mass)
        updates (par/par-mapv
                 (fn [eid]
                   (let [state (ecs/get-component world eid c/matter-state)]
                     [eid state
                      (if (= :star state)
                        state
                        (law/mass-class (ecs/get-component world eid c/mass)))]))
                 eids)]
    (reduce (fn [w [eid old new]]
              (if (= old new)
                w
                (let [w (ecs/put-component w eid c/matter-state new)]
                  ;; When a clump first reaches star-forming mass, freeze its
                  ;; current (pre-contraction) radius as the gravitational
                  ;; feeding zone, so it keeps accreting even after the
                  ;; photosphere collapses. See c/accretion-radius.
                  (if (and (= new :protostar)
                           (nil? (ecs/get-component w eid c/accretion-radius)))
                    (ecs/put-component w eid c/accretion-radius
                                       (double (or (ecs/get-component w eid c/radius) 0.0)))
                    w))))
            world
            updates)))

(defn fusion-system
  "Any body whose temperature, pressure, and composition cross the ignition
   thresholds becomes a star: it emits light and stops contracting."
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
            r' (Math/cbrt (+ (* rl rl rl) (* rs rs rs)))
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
        spin (spin-from-angular-momentum L mass radius)]
    {c/position     position
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
     c/rotation-axis (rotation-axis L)}))

(defn spawn-clump
  "Spawn one nebular clump entity from a seed spec. Returns [world eid]."
  [world spec]
  (let [[w eid] (ecs/spawn world)]
    [(ecs/put-components w eid (seed-clump spec)) eid]))
