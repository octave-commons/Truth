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
   [domain.ecs.registry   :as reg]
   [domain.ecs.tick       :as tick]
   [domain.ecs.components  :as c]
   [domain.planet-formation :as pf]
   [domain.profile         :as profile]
   [domain.spatial.index   :as spatial]
   [shape.spatial         :as sp]))

;; Forward declarations: the stellar-wind system (an accretion-region barrier
;; system, grouped with sink-formation) spawns gas parcels via the nebula-seeding
;; helpers, which are defined further down the file.
(declare spawn-clump default-composition entity->region
         sphere-radius debris-material-density planet-material-density)

(def ^:private zero3 [0.0 0.0 0.0])

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

;; --- Disc identification (Part 2) -------------------------------------------

(defn- unit [v]
  (let [l (sp/len v)] (if (pos? l) (sp/v* v (/ 1.0 l)) v)))

(defn disc-classify
  "Classify a body's kinematic relationship to a central star as one of:
     :disc      — rotationally supported (v_tang > 2|v_rad|, h/r < 0.3, bound)
     :envelope  — radially infalling, still gravitationally bound
     :outflow   — unbound / hyperbolic relative to the star
     nil        — central star itself or missing data

   Uses the region map from `entity->region` plus the central star's
   position, velocity, and mass. The h/r estimate is taken from oblateness
   (c/a ≈ 1 - h/r for a thin disc)."
  [region central-star]
  (let [{:keys [position velocity mass matter-state]} region
        {:keys [star-pos star-v star-m]} central-star
        valid-vec? (fn [v] (and (vector? v) (= 3 (count v)) (every? number? v)))]
    (when (and (valid-vec? position)
               (valid-vec? velocity)
               (valid-vec? star-pos)
               (valid-vec? star-v)
               (number? star-m)
               (pos? (double star-m))
               (not= matter-state :star))
      (let [r  (sp/v- position star-pos)
            v  (sp/v- velocity star-v)
            d  (sp/len r)]
        (when (pos? d)
          (let [rhat    (unit r)
                vr      (sp/dot v rhat)
                v-perp  (sp/v- v (sp/v* rhat vr))
                vt      (sp/len v-perp)
                v2      (sp/len2 v)
                mu      (* law/G (+ (double (or mass 0.0)) star-m))
                bound?  (<= v2 (/ (* 2.0 mu) d))
                h-over-r (max 0.0 (min 1.0 (- 1.0 (double (or (:oblateness region) 1.0)))))]
            (cond
              (not bound?)                :outflow
              (and (> vt (* 2.0 (Math/abs vr)))
                   (< h-over-r 0.3))      :disc
              :else                       :envelope)))))))

(defn disc-identification-system
  "Double-buffer write-set system: SOLE writer of c/disc-tag.

   Tags every non-star body relative to the most massive :star or :protostar
   in the world. Runs after the regime-system so regime tags are available,
   but disc-tag is independent and has its own single-writer column."
  []
  {:id     :disc-identification
   :writes #{c/disc-tag}
   :run    (fn [world]
             (let [candidates (filterv #(let [s (ecs/get-component world % c/matter-state)]
                                          (or (= s :star) (= s :protostar)))
                                       (ecs/entities-with world c/matter-state c/mass))
                   central    (when (seq candidates)
                                (apply max-key #(ecs/get-component world % c/mass) candidates))
                   central-star (when central
                                  {:star-pos (ecs/get-component world central c/position)
                                   :star-v   (ecs/get-component world central c/velocity)
                                   :star-m   (double (or (ecs/get-component world central c/mass) 0.0))})
                   eids       (ecs/entities-with world c/matter-state c/position c/velocity c/mass)]
               (if-not central
                 {c/disc-tag {}}
                 {c/disc-tag
                  (into {}
                        (keep (fn [eid]
                                (let [region (entity->region world eid)
                                      region (assoc region :oblateness
                                                    (or (ecs/get-component world eid c/oblateness) 1.0))]
                                  (when-let [tag (disc-classify region central-star)]
                                    [eid tag]))))
                        eids)})))})

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
        gas-mass (:genesis/gas-particle-mass world)]
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
                                      (:planetesimal) (sphere-radius mass debris-material-density)
                                      (:gas-giant :brown-dwarf) (sphere-radius mass planet-material-density)
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
   floor as 1 − e^(−dt/τ), where τ = `:genesis/contraction-time` (default ~30
   Myr). With a fixed fraction the core reached the floor in a handful of ticks
   and ignited in ~50 kyr; rate-limiting spreads the ignition event over tens of
   Myr of simulation time, independent of how large `dt` is, while
   `collapse-fraction` remains a hard per-tick cap for stability.

   Temperature is heated adiabatically from the body's previous temperature as
   it compresses, then bounded below by the virial temperature so the core does
   not cool below the gravitational binding energy scale. Pressure follows from
   the ideal gas law."
  [{:keys [genesis/collapse-fraction genesis/contraction-time sim/dt]
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
        gas-mass (:genesis/gas-particle-mass world)
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

(defn- registry-writes
  "This system's declared :writes from domain.ecs.registry — sourced from the
   registry so the emitter and the single-writer declaration cannot drift."
  [id]
  (some #(when (= id (:id %)) (:writes %)) reg/systems))

(defn fusion-system
  "Double-buffer write-set system: SOLE writer of c/luminosity.

   Reads fusion-promotion's one-tick-stale c/promotion-signal and applies its
   :luminosity value. Falls back to computing luminosity from scratch when
   there is no signal (initial ignition before a signal has propagated). Emits
   only the luminosities that CHANGED; a body whose fusion has ceased keeps its
   stale luminosity (never removed — same as the legacy path)."
  []
  {:id     :fusion
   :writes (registry-writes :fusion)
   :run
   (fn [world]
     (profile/profile-sections
      world
      [[:fusion/scan
        (fn [w]
          {:promotions (get-in w [:components c/promotion-signal] {})
           :eids       (ecs/entities-with w c/matter-state c/temperature c/pressure c/composition)})]
       [:fusion/burn
        (fn [{:keys [promotions eids]}]
          {c/luminosity
           (into {}
                 (keep (fn [eid]
                         (let [region (entity->region world eid)
                               sig    (get promotions eid)
                               lum    (if sig
                                        (:luminosity sig)
                                        (when (law/fusion-possible? region)
                                          (star-luminosity region)))]
                           (when (and lum
                                      (not= lum (ecs/get-component world eid c/luminosity)))
                             [eid lum]))))
                 eids)})]]))})

(defn fusion-promotion-system
  "Double-buffer write-set system: SOLE writer of c/promotion-signal — emits a
   signal for protostars that now meet fusion conditions (and for stars with
   stale zero luminosity).

   Instead of directly writing c/matter-state and c/luminosity (conflicting with
   classifier and fusion respectively — spec §7), it emits a signal that both
   systems read on the NEXT tick's frozen snapshot. The one-tick latency is
   accepted (§2). Signals not re-emitted this tick are cleared with the
   `removed` sentinel (single owner clears its own staleness).

   0-arity returns the native write-set system for the fan-out; 1-arity applies
   the emitted write-set to `world` and returns the updated world — a
   convenience for benches, tests, and REPL use."
  ([world] (tick/apply-write-set world ((:run (fusion-promotion-system)) world)))
  ([]
   {:id     :fusion-promotion
    :writes (registry-writes :fusion-promotion)
    :run
    (fn [world]
      (profile/profile-sections
       world
       [[:fusion-promotion/scan
         (fn [w]
           {:prior (keys (get-in w [:components c/promotion-signal] {}))
            :eids  (ecs/entities-with w c/matter-state c/temperature c/pressure
                                      c/composition c/density c/radius c/mass)})]
        [:fusion-promotion/evaluate
         (fn [{:keys [prior eids]}]
           {:prior   prior
            :signals (into {}
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
         (fn [{:keys [prior signals]}]
           (tick/contribution-write-set c/promotion-signal signals prior))]]))}))

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

(def ^:const capture-velocity-dispersion
  "Characteristic ambient velocity scale (m/s) — the combined thermal sound speed
   + turbulent/infall dispersion of nebular gas — used as the denominator floor in
   the Bondi capture radius. Bounds the radius so a cold, slow core does not get an
   unbounded feeding zone; ~0.25 km/s ≈ the sound speed of ~10 K molecular gas." 250.0)

(defn pending-absorbed-mass
  "Σ mass of the absorb packets (accrete + merge) already emitted for `eid` and
   sitting in the snapshot awaiting the integrator. With the integrator in the
   fan-out (spec Fix 5) an absorbed parcel's mass lands on the sink one tick
   after its packet is emitted; anything that gates on the sink's mass must
   count that in-flight generation or the Bondi runaway doubles its doubling
   time (each capture generation would wait a tick to enlarge the reach).
   Reads only snapshot channels — Jacobi-pure prediction, like the gravity
   drift predictor."
  [world eid]
  (reduce (fn [acc ct]
            (reduce (fn [a p] (+ a (double (or (:mass p) 0.0))))
                    acc
                    (get-in world [:components ct eid] [])))
          0.0
          [c/absorb-accrete c/absorb-merge]))

(defn effective-accretion-radius
  "The gravitational capture / feeding radius actually in force for a sink this
   tick (used by sink-formation to decide which gas it swallows): the LARGER of its
   frozen condensation feeding zone (set once by the classifier, the sole writer of
   c/accretion-radius) and its mass-dependent Bondi radius r = GM/c_s². Because the
   Bondi term grows ∝ M, the most massive core reaches — and captures — the most
   gas, so it accretes fastest and RUNS AWAY, funnelling the cloud into one dominant
   star instead of a swarm of equal cores (spec Part 1a, Bonnell/Bate competitive
   accretion). The mass includes the snapshot's in-flight absorb packets
   (`pending-absorbed-mass`) so the runaway is not slowed by the integrator's
   one-tick application lag. Read-only: never writes the component, so the
   single-writer invariant on c/accretion-radius holds."
  [world eid]
  (let [frozen (double (or (ecs/get-component world eid c/accretion-radius) 0.0))]
    (if (false? (:genesis/competitive-accretion? world))
      frozen ;; disabled → fixed condensation zone (the pre-Part-1a fragmenting behaviour)
      (let [m   (+ (double (or (ecs/get-component world eid c/mass) 0.0))
                   (pending-absorbed-mass world eid))]
        ;; The capture denominator is the ambient velocity DISPERSION of the gas
        ;; relative to the core — the cold nebular sound speed plus turbulence
        ;; (~0.25 km/s). Deliberately NOT the sink's own temperature (a protostar is
        ;; hot, c_s ~ 10⁵ m/s, but it is the GAS being captured whose thermal energy
        ;; resists capture), and NOT the sink's bulk speed in the world frame: in a
        ;; coherently collapsing/rotating cloud a core moves WITH its local gas, so
        ;; the Bondi–Hoyle relative velocity is the turbulent dispersion, not the
        ;; core's absolute speed. Using absolute speed collapses the radius for every
        ;; fast-moving core and defeats the runaway (the cloud just fragments).
        (max frozen (bondi-radius m capture-velocity-dispersion))))))

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
                   condensed & mass ≥ H-burning limit      -> :protostar
                   condensed & mass ≥ deuterium limit      -> :brown-dwarf
                   condensed & sub-stellar                 -> :gas-giant / :planetesimal
     :planetesimal/:gas-giant --accreted to ≥ deuterium-->    :brown-dwarf
     :brown-dwarf --accreted to ≥ H-burning-->                :protostar
     :protostar  --T≥1e7 & M≥0.08 M⊙ & H-->                  :star
                 --contraction stalled & 0.013–0.08 M⊙-->    :brown-dwarf
     :star / :brown-dwarf / :gas-giant / :planetesimal       terminal or down-ladder
     :planet                                                 owned by the disk
                                                              sub-grid (beat 6)

   The sub-stellar mass ladder is literature-grounded:
     :planetesimal  < opacity limit          (< ~3 M_J)
     :gas-giant     opacity limit to desert   (~3–30 M_J)
     :brown-dwarf   desert to H-burning       (~30–80 M_J)

   See docs/research/physics/stellar-nebula-mass-hierarchy.md.

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
       ;; Mass-loss down-ladder (winds/stripping shed mass). A collapsed body
       ;; that drops below a burning threshold degrades to the next bound state
       ;; down; it NEVER returns to :nebula (collapse is irreversible). Above
       ;; threshold these are terminal.
       ;; Ignition HYSTERESIS (spec Part 1b): once a body is a :star it does NOT
       ;; demote on mass alone. While fusion is still self-sustaining it stays a
       ;; star; only a star whose fusion has actually ceased demotes down the
       ;; bound mass ladder (star → brown-dwarf → gas-giant → planetesimal).
       :star        (cond (law/fusion-sustaining? region)   :star
                          (>= m law/hydrogen-burning-mass)  :star
                          (>= m law/deuterium-burning-mass) :brown-dwarf
                          :else                             (law/substellar-mass-class m))
       :brown-dwarf (cond (>= m law/hydrogen-burning-mass)  :protostar
                          (>= m law/deuterium-burning-mass) :brown-dwarf
                          :else                             (law/substellar-mass-class m))

       :gas-giant   (cond (>= m law/hydrogen-burning-mass)  :protostar
                          (>= m law/deuterium-burning-mass) :brown-dwarf
                          :else                             (law/substellar-mass-class m))
       :planetesimal (cond (>= m law/hydrogen-burning-mass)  :protostar
                           (>= m law/deuterium-burning-mass) :brown-dwarf
                           :else                             (law/substellar-mass-class m))
       :planet      :planet
       :protostar   (cond
                      (and (>= m law/hydrogen-burning-mass)
                           (law/fusion-possible? region))
                      :star

                      (and (>= m law/deuterium-burning-mass)
                           (<  m law/hydrogen-burning-mass)
                           (contraction-stalled? radius m temperature))
                      :brown-dwarf

                      (< m law/deuterium-burning-mass)
                      (law/substellar-mass-class m)

                      :else :protostar)
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
         (cond
           (>= m law/hydrogen-burning-mass) :protostar
           (>= m law/deuterium-burning-mass) :brown-dwarf
           :else (law/substellar-mass-class m))
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
   boundary during this step. Stateless — derived from `:genesis/sim-time` and
   `:sim/dt` on the frozen snapshot, so it holds across the parallel fan-out."
  [world]
  (let [t  (double (or (:genesis/sim-time world) 0.0))
        dt (double (or (:sim/dt world) 0.0))]
    (or (not (pos? condense-interval))
        (>= dt condense-interval)
        (not= (Math/floor (/ t condense-interval))
              (Math/floor (/ (+ t dt) condense-interval))))))

(defn classifier-system
  "Double-buffer write-set system: SOLE writer of matter-state AND accretion-radius.
   Applies `classify-next-state` to every body. :nebula → :planetesimal
   condensation is deferred to `condensation-seeder-system` (seed-and-grow). All
   other condense transitions (:gas-giant, :brown-dwarf, :protostar) still promote
   the whole gas parcel and latch an accretion-radius so the big sink can feed.
   Non-condense transitions (up/down the substellar ladder, ignition) are applied
   directly. Condensation pacing lives in the seeder."
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
          {:gas-mass      (:genesis/gas-particle-mass w)
           :eids          (ecs/entities-with w c/matter-state c/mass)
           :zones         (sink-exclusion-zones w)
           :promotions    (get-in w [:components c/promotion-signal] {})})]
       [:classifier/transitions
        (fn [{:keys [gas-mass eids zones promotions] :as state}]
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
                ;; Seed-and-grow: only :planetesimal condensations become small
                ;; seeds. Bigger gas-collapse outcomes still promote whole parcels.
                planetesimal-condenses
                (filterv (fn [[_ {:keys [old new]}]]
                           (and (= old :nebula) (= new :planetesimal)))
                         transitions)
                big-condenses
                (filterv (fn [[_ {:keys [old new]}]]
                           (and (= old :nebula) (not= new :nebula) (not= new :planetesimal)))
                         transitions)
                best-big-condense
                (when (seq big-condenses)
                  (key (apply max-key
                              (fn [[eid]]
                                (double (or (:density (:region (get transitions eid))) 0.0)))
                              big-condenses)))
                applied
                (into {}
                      (keep (fn [[eid {:keys [old new]}]]
                              (cond
                                ;; Small-body seed-and-grow: parent parcel stays nebula.
                                (and (= old :nebula) (= new :planetesimal)) nil
                                ;; Big gas-collapse: whole-parcel promotion.
                                (and (= old :nebula) (not= new :nebula))
                                (when (= eid best-big-condense) [eid new])
                                ;; Non-condense transitions.
                                :else [eid new])))
                      transitions)
                acc-radius-map
                (when best-big-condense
                  (let [gas-r (double (or (:genesis/gas-smoothing-radius world) 0.0))
                        factor (double (:genesis/feeding-zone-factor world feeding-zone-factor))]
                    (when (pos? gas-r)
                      {best-big-condense (* factor gas-r)})))]
            (assoc state
                   :transitions transitions
                   :best-big-condense best-big-condense
                   :applied applied
                   :acc-radius-map acc-radius-map)))]
       [:classifier/write-set
        (fn [{:keys [applied acc-radius-map]}]
          (cond-> {c/matter-state applied}
            acc-radius-map (assoc c/accretion-radius acc-radius-map)))]]))})

(defn condensation-seeder-system
  "Fan-out emitter: when a :nebula parcel would condense to :planetesimal,
   instead spawn a small physical seed and debit that mass from the parent parcel.
   Gated by `condense-tick?`, a one-shot `c/condensation-seeded` marker per parcel,
   a local-density-maximum filter, and a per-tick seed cap. The parent parcel stays
   :nebula; the seed materializes next tick and becomes a resolved sink. Growth
   after seeding is collisional / rare BHL capture, not a runaway channel."
  []
  {:id     :condensation-seeder
   :ns     'domain.stellar
   :reads  #{c/matter-state c/mass c/density c/position c/velocity
             c/radius c/composition c/temperature c/condensation-seeded}
   :writes #{c/spawn-request-condense c/mass-flux-condense c/condensation-seeded}
   :run
   (fn [world]
     (when (condense-tick? world)
       (let [gas-mass      (:genesis/gas-particle-mass world)
             zones         (sink-exclusion-zones world)
             gas-r         (double (or (:genesis/gas-smoothing-radius world) 0.0))
             radius-factor (double (or (:genesis/condensation-local-radius-factor world) 2.0))
             max-seeds     (long (or (:genesis/max-condensation-seeds-per-tick world) 1))
             seed-mass     (pf/condensation-seed-mass world)
             seed-r        (sphere-radius seed-mass debris-material-density)
             candidates    (->> (ecs/entities-with world c/matter-state c/mass c/density
                                                   c/position c/velocity c/radius c/composition c/temperature)
                                (filter (fn [eid]
                                          (and (= :nebula (ecs/get-component world eid c/matter-state))
                                               (not (ecs/get-component world eid c/condensation-seeded))
                                               (let [region (entity->region world eid)]
                                                 (= :planetesimal (classify-next-state region gas-mass zones))))))
                                (filterv (fn [eid]
                                           (let [rho   (double (or (ecs/get-component world eid c/density) 0.0))
                                                 pos   (ecs/get-component world eid c/position)
                                                 r     (* radius-factor gas-r)
                                                 nbrs  (spatial/query-within-radius world pos r
                                                                                    #(= :nebula (:matter-state %)))]
                                             (every? #(>= rho (double (or (:density %) 0.0)))
                                                     (remove #(= (:id %) eid) nbrs))))))
             selected      (->> (sort-by #(double (or (ecs/get-component world % c/density) 0.0)) > candidates)
                                (take max-seeds))]
         (reduce (fn [ws eid]
                   (let [pos      (or (ecs/get-component world eid c/position) zero3)
                         v        (or (ecs/get-component world eid c/velocity) zero3)
                         parent-r (double (or (ecs/get-component world eid c/radius) 0.0))
                         comp     (or (ecs/get-component world eid c/composition) default-composition)
                         temp     (double (or (ecs/get-component world eid c/temperature) 10.0))
                         ;; Deterministic offset direction from eid.
                         dir-raw  [(double (mod (* (long eid) 2654435761) 1000003))
                                   (double (mod (* (long eid) 2654435761 7) 1000003))
                                   (double (mod (* (long eid) 2654435761 13) 1000003))]
                         dir      (let [l (sp/len dir-raw)]
                                    (if (pos? l) (sp/v* dir-raw (/ 1.0 l)) [1.0 0.0 0.0]))
                         offset   (* 1.1 (+ parent-r seed-r))
                         seed-pos (sp/v+ pos (sp/v* dir offset))
                         spec     {:position     seed-pos
                                   :velocity     v
                                   :mass         seed-mass
                                   :radius       seed-r
                                   :matter-state :planetesimal
                                   :body-kind    :body/rocky
                                   :composition  comp
                                   :temperature  temp}]
                     (-> ws
                         (update-in [c/spawn-request-condense eid] (fnil conj []) spec)
                         (assoc-in [c/mass-flux-condense eid] (- (double seed-mass)))
                         (assoc-in [c/condensation-seeded eid] true))))
                 {}
                 selected))))})

(defn resolution-feeding-zone-factor
  "Feeding-zone factor scaled to the cloud's resolution: a core must bridge the
   initial inter-parcel spacing (≈ extent/N^(1/3)) to capture neighbours, and the
   spacing/smoothing-length ratio grows as the parcel count shrinks. Returns the
   `feeding-zone-factor` floor for the default kilo-parcel cloud and larger for
   coarser clouds, so condensed bodies assemble a core at any resolution."
  [gas-count]
  (let [n (double (max 1 (or gas-count 1000)))]
    (max feeding-zone-factor (/ 500.0 (Math/pow n (/ 1.0 3.0))))))

(defn- absorb-packets
  "Build the absorb-accrete packet vector for the parcels a sink swallows this
   tick, instead of directly writing position/velocity/mass/disk-mass (spec §5).
   The integrator reads absorb-accrete next tick and applies COM-preserving
   velocity/position/mass changes; disk-evolution reads it to grow
   disk-mass/disk-angular-mom. Pure — reads only the frozen snapshot.

   Only diffuse :nebula gas is routed through the disk (so it can form a
   rotationally-supported accretion disk around a protostar/star). Swallowed
    solid/degenerate bodies (:planetesimal, :gas-giant, :brown-dwarf,
    :protostar, etc.) are merged directly into the sink's mass, preserving

   the sink's mass, preserving hierarchical competitive accretion without
   inflating the disk past its fragmentation threshold."
  [world sink-eid parcels]
  (let [sink-p    (or (ecs/get-component world sink-eid c/position) [0 0 0])
        sink-v    (or (ecs/get-component world sink-eid c/velocity) [0 0 0])
        sink-state (ecs/get-component world sink-eid c/matter-state)
        disk-former? (contains? #{:protostar :star} sink-state)]
    (mapv (fn [eid]
            (let [m (double (or (ecs/get-component world eid c/mass) 0.0))
                  v (or (ecs/get-component world eid c/velocity) [0 0 0])
                  p (or (ecs/get-component world eid c/position) [0 0 0])
                  pstate (ecs/get-component world eid c/matter-state)
                  r-rel (sp/v- p sink-p)
                  v-rel (sp/v- v sink-v)
                  L-p (orbital-angular-momentum m r-rel v-rel)]
              {:mass m :velocity v :position p
               :angular-momentum L-p
                ;; Diffuse gas and small planetesimals are routed
                ;; through the disk around a protostar/star so they
                ;; can participate in viscous accretion and planet
                ;; formation. Swallowed gas-giant embryos, brown
                ;; dwarfs, and protostellar fragments are merged
                ;; directly into the sink (spec Part 1a competitive
                ;; accretion — fragments are swallowed, not re-disked).
               :disk-route (and disk-former?
                                (or (= :nebula pstate)
                                    (= :planetesimal pstate)))}))

          parcels)))

(declare imf-accretion-bias stellar-feedback-temperature hash01 feedback-radius
         sphere-radius planet-material-density)

(defn sink-formation-system
  "Double-buffer write-set system: every sink absorbs :nebula gas parcels within
   its gravitational capture zone. Three Phase 1 additions:

   1. IMF bias: accretion probability is mass-dependent — high-mass sinks
      accrete less efficiently, steering toward the Kroupa/Salpeter IMF.
   2. Stellar feedback: UV radiation from nearby stars heats gas parcels,
      suppressing Jeans collapse in their vicinity (feedback radius ~0.5 AU).
   3. Disk formation: angular momentum of accreted material is tracked in
      c/disk-angular-mom and c/disk-mass.

   Emits absorb-accrete influence + consumed-accrete lifecycle markers (spec §5)
   instead of directly writing contended physical state. Stale absorb-accrete
   entries not re-emitted this tick get the `removed` sentinel (the integrator
   consumed last tick's; lingering packets would double-count) — and the Bondi
   feeding radius is computed WITHOUT the snapshot's in-flight accrete packets,
   matching the clear-first legacy path. Parcels claimed by one sink this tick
   are tracked locally so a later (smaller) sink cannot double-claim them.

   0-arity returns the native write-set system for the fan-out; 1-arity applies
   the emitted write-set to `world` and returns the updated world — a
   convenience for benches, tests, and REPL use."
  ([world] (tick/apply-write-set world ((:run (sink-formation-system)) world)))
  ([]
   {:id     :sink-formation
    :writes (registry-writes :sink-formation)
    :run
    (fn [world]
      (let [prior-absorb (keys (get-in world [:components c/absorb-accrete] {}))
            ;; The feeding radius must NOT count the snapshot's own stale accrete
            ;; packets (they are cleared this tick): drop the column before
            ;; `effective-accretion-radius` reads `pending-absorbed-mass`.
            w0          (update world :components dissoc c/absorb-accrete)
            sinks       (->> (ecs/entities-with world c/matter-state c/accretion-radius c/position c/mass)
                             (sort-by #(double (or (ecs/get-component world % c/mass) 0.0)) #(compare %2 %1))
                             vec)
            gas-parcels (ecs/entities-with world c/matter-state c/position c/mass c/velocity)
            ;; Precompute star positions + luminosities for feedback
            star-data (mapv (fn [eid]
                              {:pos (ecs/get-component world eid c/position)
                               :lum (double (or (ecs/get-component world eid c/luminosity) 0.0))})
                            (filterv #(= :star (ecs/get-component world % c/matter-state))
                                     (ecs/entities-with world c/matter-state c/position c/luminosity)))
            ;; Parcels already marked consumed in the snapshot are never re-claimed.
            consumed0 (set (keys (get-in world [:components c/consumed-accrete] {})))
            [absorbs consumed]
            (reduce
             (fn [[absorbs consumed :as acc] sink-eid]
               (if-not (ecs/alive? world sink-eid)
                 acc
                 (let [sink-pos (ecs/get-component world sink-eid c/position)
                       sink-m   (double (or (ecs/get-component world sink-eid c/mass) 0.0))
                       ;; Competitive accretion (spec Part 1a): capture within the
                       ;; mass-dependent effective radius, so the most massive core runs
                       ;; away and funnels the cloud into one dominant star.
                       sink-acc (effective-accretion-radius w0 sink-eid)
                       bias     (imf-accretion-bias sink-m)
                       nearby   (filterv
                                 (fn [eid]
                                   (and (not= eid sink-eid)
                                        (ecs/alive? world eid)
                                        (not (contains? consumed eid))
                                        (let [pstate (ecs/get-component world eid c/matter-state)
                                              pmass  (double (or (ecs/get-component world eid c/mass) 0.0))
                                              competitive? (not (false? (:genesis/competitive-accretion? world)))]
                                          (and
                                            ;; Nebula GAS is no longer swallowed whole here:
                                            ;; gas→sink accretion is the sole responsibility of
                                            ;; domain.mass-transfer's gradual BHL channel (M3),
                                            ;; so the two do not double-count (mass conservation).
                                            ;; This system still handles hierarchical CAPTURE of
                                            ;; smaller solid/degenerate bodies and — under
                                            ;; competitive accretion — smaller protostellar
                                            ;; fragments. Planets and stars are terminal/disk-
                                            ;; owned and merge only via literal collision.
                                           (or (and (#{:planetesimal :gas-giant :brown-dwarf} pstate)
                                                    (< pmass sink-m))
                                               (and competitive?
                                                    (= :protostar pstate)
                                                    (< pmass sink-m)))

                                           (let [pos  (ecs/get-component world eid c/position)
                                                 dist (sp/dist sink-pos pos)]
                                             (and (< dist sink-acc)
                                                  ;; IMF bias: probabilistic accretion for high-mass sinks
                                                  (< (hash01 (hash [eid sink-eid (:tick world)])) bias)
                                                  (if (= :nebula pstate)
                                                    ;; Stellar feedback: reject gas heated above Jeans temp
                                                    (< (stellar-feedback-temperature pos star-data feedback-radius)
                                                       1.0e4) ;; ~10⁴ K suppresses Jeans
                                                    ;; Solid debris / protostar: hierarchical capture — a sink only
                                                    ;; swallows a body LESS massive than itself, so the larger body grows
                                                    ;; (and the swarm shrinks) rather than two equals double-absorbing.
                                                    true)))))))
                                 gas-parcels)]
                   (if (seq nearby)
                     [(assoc absorbs sink-eid (absorb-packets world sink-eid nearby))
                      (into consumed nearby)]
                     acc))))
             [{} consumed0]
             sinks)
            new-consumed (reduce disj consumed consumed0)]
        (cond-> (tick/contribution-write-set c/absorb-accrete absorbs prior-absorb)
          (seq new-consumed)
          (assoc c/consumed-accrete (into {} (map (fn [eid] [eid true])) new-consumed)))))}))

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

(def ^:const max-gi-fragments-per-disk
  "Maximum number of direct gravitational-instability fragments a single disk
   is allowed to spawn before it is forced to settle."
  3)

(def ^:const disk-outer-temperature
  "Characteristic temperature (K) of the outer disk annulus used for Toomre Q
   and cooling-time estimates."
  100.0)

(def ^:const gi-fragment-mass-cap
  "Direct GI fragments are capped below the deuterium-burning limit so they
   always classify as :gas-giant, not brown dwarf or protostar."
  (* 0.5 law/deuterium-burning-mass))

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

;; --- Toomre Q + cooling time (Part 3) ---------------------------------------

(defn toomre-q
  "Toomre Q = c_s · Ω / (π G Σ) for a disc annulus. Estimates:
     c_s  = adiabatic sound speed at temperature T
     Ω    = Keplerian angular speed √(GM / r³)
     Σ    = surface density M_disc / (π r²)  (thin-disc approximation)
   Returns +∞ when Σ is zero."
  [star-mass disc-mass radius temperature]
  (let [M (double (or star-mass 0.0))
        m (double (or disc-mass 0.0))
        r (double (or radius 0.0))
        T (double (or temperature 0.0))]
    (if (and (pos? M) (pos? m) (pos? r) (pos? T))
      (let [cs    (sound-speed T)
            Omega (Math/sqrt (/ (* law/G M) (* r r r)))
            Sigma (/ m (* Math/PI r r))]
        (if (pos? Sigma)
          (/ (* cs Omega) (* Math/PI law/G Sigma))
          Double/POSITIVE_INFINITY))
      Double/POSITIVE_INFINITY)))

(defn cooling-time-ratio
  "Gammie (2001) cooling-time to dynamical-time ratio: t_cool / Ω⁻¹.
   Estimates t_cool ≈ (Σ c_s²) / (2 σ T⁴) in seconds and Ω = √(GM/r³).
   Lower values mean faster cooling → fragmentation when Q < 1."
  [star-mass disc-mass radius temperature]
  (let [M (double (or star-mass 0.0))
        m (double (or disc-mass 0.0))
        r (double (or radius 0.0))
        T (double (or temperature 0.0))]
    (if (and (pos? M) (pos? m) (pos? r) (pos? T))
      (let [cs    (sound-speed T)
            Sigma (/ m (* Math/PI r r))
            Omega (Math/sqrt (/ (* law/G M) (* r r r)))
            t-cool (if (pos? Sigma)
                     (/ (* Sigma cs cs)
                        (* 2.0 law/stefan-boltzmann T T T T))
                     0.0)]
        (if (pos? Omega)
          (* t-cool Omega)
          Double/POSITIVE_INFINITY))
      Double/POSITIVE_INFINITY)))

(defn disc-regime
  "Map Toomre Q and cooling time to a disc stability regime keyword.
   Only valid for rotationally-supported disc material; callers must check
   c/disc-tag = :disc first."
  [star-mass disc-mass radius temperature]
  (let [Q (toomre-q star-mass disc-mass radius temperature)
        cool-ratio (cooling-time-ratio star-mass disc-mass radius temperature)]
    (if (Double/isFinite Q)
      (cond
        (> Q 1.0)                         :stable-disc
        (and (<= Q 1.0) (< cool-ratio 3.0)) :gravitationally-unstable
        :else                             :unstable-no-fragment)
      :stable-disc)))

(defn disk-regime-map
  "Compute the scalar disk-regime map for a star+disk.
   Returns {:toomre-q :cooling-beta :regime :solid-surface-density :snow-line}."
  [star-mass disk-mass disk-radius luminosity composition]
  (let [T disk-outer-temperature
        Q (toomre-q star-mass disk-mass disk-radius T)
        beta (cooling-time-ratio star-mass disk-mass disk-radius T)
        snow-line (pf/snow-line-radius luminosity)
        sigma-gas (if (and (pos? disk-mass) (pos? disk-radius))
                    (/ disk-mass (* Math/PI disk-radius disk-radius))
                    0.0)
        Z (lcomp/metallicity composition)
        sigma-solid (if (pos? sigma-gas)
                      (* sigma-gas Z
                         (if (> disk-radius snow-line)
                           pf/ice-enhancement-factor
                           1.0))
                      0.0)
        regime (cond
                 (and (> Q 1.5) (pos? sigma-solid)) :core-accretion-zone
                 (> Q 1.0) :stable-disc
                 (and (<= Q 1.0) (< beta 3.0)) :fragmenting
                 :else :gravito-turbulent)]
    {:toomre-q Q
     :cooling-beta beta
     :regime regime
     :solid-surface-density sigma-solid
     :snow-line snow-line}))

(defn- put-tracked
  "`ecs/put-component` on disk-evolution's internal working world, recording the
   written cell in the `::disk-ws` write-set accumulator carried on the world
   map. The emitter returns the accumulated write-set (later writes to the same
   cell win); the working world itself is discarded — it exists only so the
   pass's later steps (viscous transfer, fragmentation, planet seeding) can read
   the earlier steps' this-tick disk state."
  [w eid ctype v]
  (-> (ecs/put-component w eid ctype v)
      (update ::disk-ws assoc-in [ctype eid] v)))

(defn- disk-evolution-pass
  "The disk-evolution computation on a working copy of the frozen snapshot;
   every component write goes through `put-tracked` so the accumulated
   `::disk-ws` IS the system's write-set. See `disk-evolution-system`.

   1. Absorb-accrete processing: reads c/absorb-accrete packets from sink-formation
      and adds disk-routed mass/angmom to c/disk-mass and c/disk-angular-mom (spec §5).
   2. Viscous accretion: transfers disk mass to the star at Ṁ = M_disk / t_visc.
      Angular momentum is conserved — the star spins up, disk shrinks.
    3. Disk-instability fragmentation: when M_disk/M_star > 0.1 (Toomre) the disk
       sheds a self-gravitating clump (sub-stellar embryo via `substellar-mass-class`);
       > 0.5 → a stellar companion (:protostar). Emits c/spawn-request-disk.

   4. Sub-grid planet seeder (spec Part 4): once a dominant :star's disk has
      matured (disk-age > :genesis/disk-maturity) and has NOT yet been seeded,
      `planet-formation/planet-seeds` converts the disk's solid surface density
      into :planet entities by a core-accretion prescription (NOT by merging gas
      parcels — canonical note §1, beat 6). Emits c/spawn-request-planet, sets
      the one-shot c/planets-seeded flag, and debits the consumed mass/angular
      momentum from c/disk-mass / c/disk-angular-mom (conservation).

   Fragment/planet spawns are materialized next tick by materialize-lifecycle
   (one-tick Jacobi delay). Runs in the parallel fan-out (was a post-fold
   barrier)."
  [world]
  (let [dt  (double (or (:sim/dt world) 1.0e12))
        eps (double (or (:sim/softening world) 0.0))
        ;; Incorporate disk-routed absorb-accrete packets from sink-formation
        ;; (solid bodies still captured whole by the hierarchical path).
        world (reduce-kv
               (fn [w eid packets]
                 (let [disk-pkts (filter :disk-route packets)]
                   (if (seq disk-pkts)
                     (let [add-mass (reduce + 0.0 (map :mass disk-pkts))
                           add-L    (reduce sp/v+ [0.0 0.0 0.0] (map :angular-momentum disk-pkts))
                           old-dm   (double (or (ecs/get-component w eid c/disk-mass) 0.0))
                           old-L    (or (ecs/get-component w eid c/disk-angular-mom) [0.0 0.0 0.0])]
                       (-> w
                           (put-tracked eid c/disk-mass (+ old-dm add-mass))
                           (put-tracked eid c/disk-angular-mom (sp/v+ old-L add-L))))
                     w)))
               world
               (get-in world [:components c/absorb-accrete] {}))
        ;; Incorporate gradual gas accretion routed to the disk by
        ;; domain.mass-transfer (c/disk-mass-flux / c/disk-l-flux). This is the
        ;; gas→disk channel that replaced sink-formation's whole-parcel gas
        ;; swallowing (M3); disk-evolution is the sole writer of c/disk-mass and
        ;; c/disk-angular-mom, so folding it here keeps single-writer.
        world (let [dmf (get-in world [:components c/disk-mass-flux] {})
                    dlf (get-in world [:components c/disk-l-flux] {})]
                (reduce
                 (fn [w eid]
                   (let [add-mass (double (or (get dmf eid) 0.0))]
                     (if (pos? add-mass)
                       (let [add-L  (or (get dlf eid) [0.0 0.0 0.0])
                             old-dm (double (or (ecs/get-component w eid c/disk-mass) 0.0))
                             old-L  (or (ecs/get-component w eid c/disk-angular-mom) [0.0 0.0 0.0])]
                         (-> w
                             (put-tracked eid c/disk-mass (+ old-dm add-mass))
                             (put-tracked eid c/disk-angular-mom (sp/v+ old-L add-L))))
                       w)))
                 world
                 (keys dmf)))
        evolve
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
                             (put-tracked eid c/disk-mass disk-m')
                             (put-tracked eid c/disk-angular-mom disk-L')
                             (put-tracked eid c/mass-flux-disk dm)
                             (put-tracked eid c/torque-disk L-transfer))
                       ;; Disk regime (scalar per star) for tests and planet seeder
                      L-star    (double (or (ecs/get-component w eid c/luminosity) 0.0))
                      comp      (or (ecs/get-component w eid c/composition) default-composition)
                      fragments-spawned (long (or (ecs/get-component w eid c/disk-fragments-spawned) 0))
                      regime-map (-> (disk-regime-map M disk-m r-disk L-star comp)
                                     (merge (get-in world [:test/disk-regime eid] {})))
                      w' (-> w'
                             (put-tracked eid c/disk-regime regime-map)
                             (put-tracked eid c/disk-fragments-spawned fragments-spawned))]
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
                         ;; Circular orbit speed in the SOFTENED field the
                         ;; integrator applies — the unsoftened √(GM/r) at an
                         ;; r-orbit inside the Plummer length ejected every
                         ;; fragment at several × the cloud escape speed.
                          v-orbit     (law/softened-circular-speed M' r-orbit eps)
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
                          w'' (put-tracked w' eid c/spawn-request-disk [spawn-spec])
                         ;; Update disk after fragmentation
                          w''' (-> w''
                                   (put-tracked eid c/disk-mass (- disk-m' companion-m))
                                   (put-tracked eid c/disk-angular-mom
                                                (sp/v* disk-L' (/ (- disk-m' companion-m)
                                                                  (max 1.0 disk-m')))))]
                      w''')

                   ;; GI fragment: fast-cooling disk spawns a gas-giant embryo only
                    (and (> ratio disk-fragment-threshold)
                         (= :fragmenting (:regime regime-map))
                         (< fragments-spawned max-gi-fragments-per-disk))
                    (let [embryo-m-raw (* 0.1 disk-m')
                          embryo-m (min embryo-m-raw gi-fragment-mass-cap)
                          r-disk-now (disk-radius (/ (sp/len disk-L') (max 1.0 disk-m')) M')
                         ;; never inside the dt-resolvable radius (else it is flung)
                          r-orbit   (max (* 0.3 (max 1.0e10 r-disk-now))
                                         (resolvable-orbit-radius M' dt min-fragment-orbit-periods))
                         ;; softened-field circular speed — see binary branch
                          v-orbit   (law/softened-circular-speed M' r-orbit eps)
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
                                      :matter-state :gas-giant
                                      :composition (or (ecs/get-component w' eid c/composition)
                                                       default-composition)
                                      :temperature 300.0}

                          w'' (put-tracked w' eid c/spawn-request-disk [spawn-spec])
                          w''' (-> w''
                                   (put-tracked eid c/disk-mass (- disk-m' embryo-m))
                                   (put-tracked eid c/disk-angular-mom
                                                (sp/v* disk-L' (/ (- disk-m' embryo-m)
                                                                  (max 1.0 disk-m'))))
                                   (put-tracked eid c/disk-fragments-spawned (inc fragments-spawned)))]
                      (if (>= embryo-m law/opacity-limit-mass)
                        w'''
                        w'))

                   ;; Just viscous evolution, no fragmentation
                    :else w'))))))
        world-evolved
        (reduce
         evolve
         world
         (filterv (fn [eid]
                    (let [dm (double (or (ecs/get-component world eid c/disk-mass) 0.0))]
                      (pos? dm)))
                  (ecs/entities-with world c/matter-state c/mass c/disk-mass)))]
    ;; Sub-grid planet seeder (Part 4): a one-shot per mature disk. Reads the
    ;; POST-viscous disk state (world-evolved) so its mass/angular-momentum debit
    ;; composes with this tick's viscous transfer and conservation holds. Only
    ;; seeds around a dominant :star; `planet-seeds` guards on disk maturity and
    ;; the c/planets-seeded flag, returning nil when it must not fire yet.
    (reduce
     (fn [w star]
       (let [res (pf/planet-seeds w star)]
         (if (and res (seq (:spawns res)))
           (-> w
               (put-tracked star c/disk-mass (:disk-m res))
               (put-tracked star c/disk-angular-mom (:disk-L res))
               (put-tracked star c/planets-seeded true)
               (put-tracked star c/spawn-request-planet (mapv second (:spawns res))))
           w)))
     world-evolved
     (filterv (fn [eid]
                (and (= :star (ecs/get-component world-evolved eid c/matter-state))
                     (pos? (double (or (ecs/get-component world-evolved eid c/disk-mass) 0.0)))
                     (nil? (ecs/get-component world-evolved eid c/spawn-request-disk))))
              (ecs/entities-with world-evolved c/matter-state c/mass c/disk-mass)))))

(defn disk-evolution-system
  "Double-buffer write-set system: evolves protoplanetary disks on the viscous
   timescale and triggers planet/binary formation via gravitational instability
   (see `disk-evolution-pass` for the physics). Sole writer of c/disk-mass,
   c/disk-angular-mom, c/mass-flux-disk, c/torque-disk, c/spawn-request-disk,
   c/spawn-request-planet, and c/planets-seeded.

   The pass runs on an internal working copy of the frozen snapshot (its later
   steps read its earlier steps' this-tick disk state); only the accumulated
   write-set leaves the emitter — no world diff.

   0-arity returns the native write-set system for the fan-out; 1-arity applies
   the pass to `world` and returns the updated world — a convenience for
   benches, tests, and REPL use."
  ([world] (dissoc (disk-evolution-pass world) ::disk-ws))
  ([]
   {:id     :disk-evolution
    :writes (registry-writes :disk-evolution)
    :run    (fn [world] (get (disk-evolution-pass world) ::disk-ws {}))}))

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
                    t-wind (double (or corona-t 1.5e6))
                    t-launch (max 1.0e6 t-wind)
                    ion  (min 1.0 (max 0.9 (/ t-launch 1.0e6)))
                    extra (cond-> {}
                            (pos? ion) (assoc c/ionization-fraction ion)
                            (pos? ram) (assoc c/ram-pressure ram))]
                {:eid eid :mass-flux (- dm) :reservoir (- resv p-mass) :dv dv
                 :spawn {:position ppos :velocity pvel :mass p-mass
                         :radius gas-r :matter-state :nebula
                         :composition (or (:composition region) default-composition)
                         :b-field (em/net-field-at ppos sources nil)
                         :temperature t-launch
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
           :k      (double (:genesis/wind-rate-scale w 1.0))
           :dt     (double (or (:sim/dt w) 1.0e12))
           :tick   (or (:tick w) 0)
           :p-mass (double (or (:genesis/wind-parcel-mass w)
                               (some-> (:genesis/gas-particle-mass w) (* 0.25))
                               1.0e27))
           :v-fac  (double (:genesis/wind-speed-factor w 0.15))
           :max-frac (double (:genesis/wind-max-loss-frac w 0.01))
           :abl    (double (:genesis/ablation-floor w (* 1.0e-3 law/deuterium-burning-mass)))
           :gas-r  (double (or (:genesis/gas-smoothing-radius w) 6.0e13))
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

   Tunables: `:genesis/flare-period` (ticks between flares per star; 0 disables),
   `:genesis/flare-mass-factor` (× wind-parcel mass), `:genesis/flare-speed-factor`
   (drift per tick as a fraction of the feeding zone), `:genesis/flare-temp-factor`
   (× virial temperature, for brightness). A flare never fires if it would pull
   the star below half the hydrogen-burning mass — flares decorate, they don't
   demote."
  []
  {:id     :stellar-flare
   :writes #{c/mass-flux-flare c/dv-flare c/spawn-request-flare c/flare-boost}
   :run
   (fn [world]
     (let [period (long (:genesis/flare-period world 0))] ;; default OFF — opt in via :genesis/flare-period
       (if-not (pos? period)
         {}
         (let [sources (em/field-sources world) ;; launch-point field sampler (research §5)
               dt     (double (or (:sim/dt world) 1.0e12))
               tick   (or (:tick world) 0)
               p-mass (double (or (:genesis/wind-parcel-mass world)
                                  (some-> (:genesis/gas-particle-mass world) (* 0.25))
                                  1.0e27))
               m-fac  (double (:genesis/flare-mass-factor world 3.0))
               v-fac  (double (:genesis/flare-speed-factor world 0.4))
               t-fac  (double (:genesis/flare-temp-factor world 3.0))
               gas-r  (double (or (:genesis/gas-smoothing-radius world) 6.0e13))
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
      :planetesimal (let [r (sphere-radius m debris-material-density)]
                      {:radius r :density debris-material-density})
      (:gas-giant :brown-dwarf :planet)
      (let [r (sphere-radius m planet-material-density)]
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
     :planetesimal / :gas-giant / :brown-dwarf / :planet fixed material density → radius from mass (solid)
     :protostar/:star  KH oblate contraction toward the main-sequence floor
   Replaces the radius/density writes of density-system, jeans-collapse, and
   collapse. The future home of the voxel shape representation."
  []
  {:id     :structure
   :writes #{c/radius c/density c/oblateness c/rotation-axis}
   :run    (fn [world]
             (let [cf (:genesis/collapse-fraction world 0.5)
                   ct (:genesis/contraction-time world 9.5e14)
                   dt (:sim/dt world 1.0e12)
                    ;; gas branch (SPH): density primary, radius derived
                   gas-ws (let [profile? (:genesis/profile-subsystems? world)
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
                                         {:genesis/_profile {:structure/gas-query (double dt-query)}}
                                         {})
                                       gas-results))))]
               ;; resolved branch: radius primary (or material density), rest derived.
               ;; Resolved bodies are selected straight off the matter-state
               ;; component map — projecting every entity (mostly gas) through
               ;; entity->region just to discard it dominated this branch.
               (profile/profile-section
                world :structure/resolved
                (fn [_world]
                  (let [ms-map   (get-in world [:components c/matter-state] {})
                        mass-map (get-in world [:components c/mass] {})
                        rad-map  (get-in world [:components c/radius] {})
                        resolved-eids
                        (persistent!
                         (reduce-kv (fn [acc eid st]
                                      (if (and (#{:planetesimal :gas-giant :brown-dwarf :planet :protostar :star} st)
                                               (contains? mass-map eid)
                                               (contains? rad-map eid))
                                        (conj! acc eid)
                                        acc))
                                    (transient [])
                                    ms-map))
                        shapes (par/par-mapv
                                (fn [eid]
                                  [eid (resolved-shape (entity->region world eid) cf ct dt)])
                                resolved-eids)]
                    (reduce
                     (fn [ws [eid s]]
                       (if s
                         (cond-> ws
                           (:radius s)        (assoc-in [c/radius eid] (:radius s))
                           (:density s)       (assoc-in [c/density eid] (:density s))
                           (:oblateness s)    (assoc-in [c/oblateness eid] (:oblateness s))
                           (:rotation-axis s) (assoc-in [c/rotation-axis eid] (:rotation-axis s)))
                         ws))
                     gas-ws
                     shapes))))))})

(defn temperature-system
  "Double-buffer write-set system: SOLE writer of temperature.
     :protostar / :star  T = virial temperature G M m_H / (k_B R) — compression
                         (Kelvin–Helmholtz) heating that RISES as Structure
                         contracts the radius, carrying the core to ignition. A
                         pure derivation from mass + radius (no frozen reference).
      :planetesimal / :gas-giant / :brown-dwarf / :planet   radiative: cool toward the CMB, warmed by nearby stars.

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

                                (#{:planetesimal :gas-giant :brown-dwarf :planet} state)

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

                                (= :nebula state)
                                (let [t   (double (or (:temperature region) 3.0))
                                      drp (radiative-cooling-delta region dt)]
                                  [eid (max 3.0 (- t drp))])

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
  "Brittle collision response: the smaller body breaks into two :planetesimal fragments
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
                         :mass         frag-m :radius frag-r :matter-state :planetesimal
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
            ;; soft cap. Disable with :genesis/max-resolved-bodies 0.
            budget (long (:genesis/max-resolved-bodies world 400))
            resolved (long (or (get-in world [:genesis/stats :resolved-count]) 0))]
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
  "Fallback composition for a spawned parcel that inherits none from its source.
   Population-I (solar) so the explicit element set carries metals — a metal-free
   fallback would zero out solid surface density and block planet seeding. Bodies
   normally carry their accreted composition; this is only the last-resort default."
  lcomp/solar-composition)

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
