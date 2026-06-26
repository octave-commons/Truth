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

(defn ideal-gas-pressure
  "Pressure of a gas region from the ideal gas law: P = ρ k_B T / m_H."
  [density temperature]
  (/ (* density law/k-B temperature) law/m-H))

(defn body-density
  "Density of a uniform sphere of given mass and radius."
  [mass radius]
  (/ mass (* (/ 4.0 3.0) Math/PI (Math/pow radius 3))))

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

(defn time-scale-from-complexity
  "Map observable complexity to a time-compression factor (sim-seconds per tick).
   Starts at ~1e11 (centuries per tick at nebular scale) and slows toward real
   time as complexity rises, but never drops below 1e-3 s/tick so that late-game
   physics integration remains practical."
  [complexity]
  (max 1e-3 (Math/pow 10 (- 11 (* complexity 0.1)))))

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
   each tick under self-gravity: radius shrinks, density rises, and virial heating
   drives core temperature and pressure toward ignition (Kelvin–Helmholtz
   contraction). Its frozen-in magnetic field amplifies as B ∝ ρ^(2/3).

   Diffuse gas does NOT collapse in place here — it assembles by N-body gravity
   and accretion (collisions). Only the resolved star-forming core contracts."
  [{:keys [phase0/collapse-fraction] :or {collapse-fraction 0.5} :as world}]
  (reduce
   (fn [w eid]
     (let [region (entity->region world eid)
           {:keys [mass radius matter-state]} region]
       (if (and (= :protostar matter-state) radius mass)
         (let [new-radius  (* radius (- 1.0 collapse-fraction))
               new-density (body-density mass new-radius)
               new-temp    (virial-temperature mass new-radius)
               new-press   (self-gravity-pressure mass new-radius)
               new-b       (when-let [b (:b-field region)]
                             (em/flux-freeze b (:density region) new-density))]
           (cond-> w
             true  (ecs/put-component eid c/radius      new-radius)
             true  (ecs/put-component eid c/density     new-density)
             true  (ecs/put-component eid c/temperature new-temp)
             true  (ecs/put-component eid c/pressure    new-press)
             new-b (ecs/put-component eid c/b-field      new-b)))
         w)))
   world
   (ecs/entities-with world c/matter-state c/temperature c/density c/radius c/mass)))

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
              (if (= old new) w (ecs/put-component w eid c/matter-state new)))
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
                                   new-press (ideal-gas-pressure (:density region) new-temp)]
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
   momentum, volume-summed radius). Registered for :event/collision."
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
            dens' (body-density total r')]
        (-> world
            (ecs/put-component big c/mass        total)
            (ecs/put-component big c/radius      r')
            (ecs/put-component big c/velocity    v')
            (ecs/put-component big c/composition comp)
            (ecs/put-component big c/temperature temp')
            (ecs/put-component big c/density     dens')
            (ecs/put-component big c/pressure    (ideal-gas-pressure dens' temp'))
            (ecs/despawn small)))
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
           body-kind b-field]
    :or   {velocity [0.0 0.0 0.0]
           temperature 10.0
           composition default-composition
           matter-state :nebula
           body-kind :body/gas}}]
  (let [density (body-density mass radius)]
    {c/position     position
     c/velocity     velocity
     c/mass         mass
     c/radius       radius
     c/body-kind    body-kind
     c/temperature  temperature
     c/density      density
     c/pressure     (ideal-gas-pressure density temperature)
     c/composition  composition
     c/luminosity   0.0
     c/b-field      (or b-field (em/seed-field))
     c/matter-state matter-state}))

(defn spawn-clump
  "Spawn one nebular clump entity from a seed spec. Returns [world eid]."
  [world spec]
  (let [[w eid] (ecs/spawn world)]
    [(ecs/put-components w eid (seed-clump spec)) eid]))
