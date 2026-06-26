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
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]))

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
   optical-depth correction so dense regions cool slowly."
  [{:keys [temperature radius density]} dt]
  (let [surface-area  (* 4.0 Math/PI radius radius)
        optical-depth (* density radius 1e-20)
        emissivity    (/ 1.0 (+ 1.0 optical-depth))
        power         (* law/stefan-boltzmann surface-area emissivity
                         (Math/pow temperature 4))
        mass          (* density (/ 4.0 3.0) Math/PI (Math/pow radius 3))
        specific-heat (* 2.5 law/k-B (/ 1.0 law/m-H))]
    (if (pos? mass)
      (/ (* power dt) (* mass specific-heat))
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

;; --- Complexity / time scale ------------------------------------------------

(defn complexity-score
  "Observable complexity from a tally of the system. Higher complexity slows
   simulation time — the universe becomes more articulate as it cools."
  [{:keys [body-count star? fusion? planet-count]}]
  (+ body-count
     (if star? 5 0)
     (if fusion? 20 0)
     (* 10 planet-count)))

(defn time-scale-from-complexity
  "Map observable complexity to a time-compression factor (sim-seconds per tick).
   Starts at ~1e14 (deep cosmological fast-forward), slows toward real time as
   complexity rises."
  [complexity]
  (Math/pow 10 (- 14 (* complexity 0.4))))

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
   :matter-state (ecs/get-component world eid c/matter-state)})

(def ^:private collapsing-states #{:nebula :protostar})

;; --- ECS systems ------------------------------------------------------------

(defn collapse-system
  "Gravitationally unstable, not-yet-igniting clumps contract each tick: radius
   shrinks, density rises, and self-gravity drives the core temperature and
   pressure up (virial heating). This is what carries a cold diffuse clump
   toward stellar ignition. A clump that is stable against collapse is left for
   the thermal system to cool — that is the soft-failure path."
  [{:keys [phase0/collapse-fraction] :or {collapse-fraction 0.5} :as world}]
  (reduce
   (fn [w eid]
     (let [region (entity->region world eid)
           {:keys [mass radius matter-state]} region]
       ;; A diffuse nebular clump must be Jeans-unstable to BEGIN collapsing;
       ;; once it is a protostar, self-gravity carries it monotonically inward
       ;; until fusion ignites (Kelvin–Helmholtz contraction).
       (if (and (collapsing-states matter-state)
                radius mass
                (or (= matter-state :protostar)
                    (jeans-unstable? region)))
         (let [new-radius  (* radius (- 1.0 collapse-fraction))
               new-density (body-density mass new-radius)
               new-temp    (virial-temperature mass new-radius)
               new-press   (self-gravity-pressure mass new-radius)]
           (-> w
               (ecs/put-component eid c/radius      new-radius)
               (ecs/put-component eid c/density     new-density)
               (ecs/put-component eid c/temperature new-temp)
               (ecs/put-component eid c/pressure    new-press)
               (ecs/put-component eid c/matter-state :protostar)))
         w)))
   world
   (ecs/entities-with world c/matter-state c/temperature c/density c/radius c/mass)))

(defn classify-system
  "Promote stable (non-collapsing) nebular clumps that are massive enough to be
   rounded by self-gravity into planets. Collapsing protostars are left alone so
   they can continue toward ignition; only clumps that have settled — stable
   against collapse — are frozen into the planet classification handed forward."
  [world]
  (reduce
   (fn [w eid]
     (let [region (entity->region world eid)]
       (if (and (= :nebula (:matter-state region))
                (not (jeans-unstable? region))
                (law/hydrostatic-equilibrium? region))
         (ecs/put-component w eid c/matter-state :planet)
         w)))
   world
   (ecs/entities-with world c/matter-state c/mass c/temperature c/density c/radius)))

(defn fusion-system
  "Any body whose temperature, pressure, and composition cross the ignition
   thresholds becomes a star: it emits light and stops contracting."
  [world]
  (reduce
   (fn [w eid]
     (let [region (entity->region world eid)]
       (if (and (not= :star (:matter-state region))
                (law/fusion-possible? region))
         (let [lum (luminosity-from-fusion (fusion-rate region) (:radius region))]
           (-> w
               (ecs/put-component eid c/luminosity   lum)
               (ecs/put-component eid c/matter-state :star)))
         w)))
   world
   (ecs/entities-with world c/matter-state c/temperature c/pressure c/composition)))

(defn thermal-system
  "Radiative cooling for everything that is not currently fusing. Failed clumps
   and finished planets shed heat; this is the soft-failure path — a clump that
   never reached ignition simply cools into a cold body."
  [dt]
  (fn [world]
    (reduce
     (fn [w eid]
       (let [region (entity->region world eid)]
         (if (= :star (:matter-state region))
           w
           (let [drop (radiative-cooling-delta region dt)
                 new-temp (max 3.0 (- (:temperature region) drop))
                 new-press (ideal-gas-pressure (:density region) new-temp)]
             (-> w
                 (ecs/put-component eid c/temperature new-temp)
                 (ecs/put-component eid c/pressure    new-press))))))
     world
     (ecs/entities-with world c/matter-state c/temperature c/density c/radius))))

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
            temp' (max (or (:temperature mb*) 0.0) (or (:temperature ms*) 0.0))
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
  "Return the component map for one nebular clump entity."
  [{:keys [position velocity mass radius temperature composition matter-state body-kind]
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
     c/matter-state matter-state}))

(defn spawn-clump
  "Spawn one nebular clump entity from a seed spec. Returns [world eid]."
  [world spec]
  (let [[w eid] (ecs/spawn world)]
    [(ecs/put-components w eid (seed-clump spec)) eid]))
