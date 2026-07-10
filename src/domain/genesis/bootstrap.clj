(ns domain.genesis.bootstrap
  "Phase 0 world bootstrap: nebula seeding, empty-world construction, and the
   lifecycle materialization step that spawns and despawns entities between
   ticks. Pure data transformation; no rendering or IO."
  (:require
   [clojure.math :as math] [law.stellar :as law]
   [law.composition :as lcomp]
   [law.registry :as lreg]
   [domain.ecs.core :as ecs]
   [domain.ecs.event :as event]
   [domain.ecs.components :as c]
   [domain.stellar.seeder :as seeder]
   [domain.stellar.merge :as stellar-merge]
   [domain.stellar.sink :as sink]
   [domain.player :as player]
   [domain.pacing :as pacing]
   [domain.intervention :as intervention]
   [shape.spatial :as sp]))

;; --- Nebula seeding ---------------------------------------------------------

(defn- gas-particle-spec
  "One equal-mass, self-gravitating gas particle of a cold, rotating, turbulent
   cloud. Nothing is pre-formed: every particle starts as diffuse gas. Solid-body
   rotation (sub-virial, so the cloud collapses) plus turbulence and a bias toward
   `seeds` (overdensity centres) give the cloud the structure it needs to
   fragment and accrete into clumps, planets, and a star-forming core."
  [^java.util.Random rng extent pmass prad v-vir omega seeds n-seeds seed-r turb composition]
  (let [to-seed? (and (seq seeds) (< (.nextDouble rng) 0.40))
        [px py pz]
        (if to-seed?
          (let [[cx cy cz] (nth seeds (.nextInt rng n-seeds))
                g (fn [s] (* s extent seed-r (.nextGaussian rng)))]
            [(+ cx (g 1.0)) (+ cy (g 1.0)) (+ cz (g 0.6))])
          (let [r  (* extent (math/pow (.nextDouble rng) 0.5)) ; centrally concentrated but diffuse
                th (* 2.0 math/PI (.nextDouble rng))
                ph (math/acos (- (* 2.0 (.nextDouble rng)) 1.0))]
            [(* r (math/sin ph) (math/cos th))
             (* r (math/sin ph) (math/sin th))
             (* r (math/cos ph))]))
        ;; `turb` is now a direct fraction of the gravitational (circular) speed,
        ;; so turbulent support is comparable to gravity rather than a tenth of it.
        jit (fn [] (* turb v-vir (.nextGaussian rng)))]
    {:position    (sp/vec3 px py pz)
     :velocity    (sp/vec3 (+ (* (- omega) py) (jit)) ; solid-body spin about z
                           (+ (* omega px) (jit))
                           (jit))
     :mass        pmass
     :radius      prad
     :temperature 12.0
     :body-kind   :body/gas
     :composition composition}))

(defn- nebula-velocity-scale
  "Circular speed at the cloud edge and corresponding solid-body spin rate."
  [total-mass extent spin]
  (let [v-vir (math/sqrt (/ (* law/G total-mass) extent))
        omega (/ (* spin v-vir) extent)]
    [v-vir omega]))

(defn- seed-positions
  "Gaussian overdensity centres seeded near the cloud centre. Keeping the
   seeds central means the dominant core forms in the middle of the cloud; seeds
   at the edge (the old 0.8×extent uniform distribution) made outer parcels
   collapse first and produced cores all over the nebula."
  [rng extent n-seeds]
  (vec (repeatedly n-seeds
                   (fn [] (sp/vec3 (* extent 0.15 (.nextGaussian rng))
                                   (* extent 0.15 (.nextGaussian rng))
                                   (* extent 0.05 (.nextGaussian rng)))))))

(defn- make-gas-specs
  "Build one gas-particle spec per parcel."
  [rng extent pmass prad v-vir omega seeds n-seeds seed-r turb floor gas-count]
  (mapv (fn [_] (gas-particle-spec rng extent pmass prad v-vir omega seeds n-seeds seed-r turb floor))
        (range gas-count)))

(defn- center-spec-velocities
  "Subtract the mean velocity from every spec so the cloud has zero net momentum."
  [specs]
  (let [n (double (count specs))
        mean-v (-> (reduce (fn [acc s] (sp/v+ acc (:velocity s))) (sp/vec3 0 0 0) specs)
                   (sp/v* (/ 1.0 n)))]
    (mapv (fn [s] (update s :velocity sp/v- mean-v)) specs)))

(defn seed-nebula
  "Seed a cold, rotating, turbulent, self-gravitating gas cloud on the single ECS
   world — `gas-count` equal-mass particles, no pre-placed core or planets. Stars,
   planets, and debris must EMERGE by gravitational collapse and accretion. A few
   Gaussian overdensity seeds give the cloud something to fragment around.
   Deterministic (seeded RNG) so runs and tests reproduce."
  ([world total-mass extent] (seed-nebula world total-mass extent {}))
  ([world total-mass extent {:keys [gas-count n-seeds seed-r spin turb seed metallicity]
                             ;; Single seed (down from 5) so only one overdensity
                             ;; centre collapses early; the rest of the cloud is
                             ;; diffuse and feeds the single core rather than
                             ;; spawning a swarm of competing cores. seed-r widened
                             ;; to 0.25 so the seed is diffuse and resolves over
                             ;; many ticks, not a pinpoint implosion.
                             :or   {gas-count 1000 n-seeds 1 seed-r 0.25
                                    spin 0.55 turb 0.08 seed 42
                                    metallicity :population-i}}]
   (let [rng    (java.util.Random. (long seed))
         ;; Cloud-floor composition. Default Population-I (solar): a present-day
         ;; cloud is already enriched, so metals exist from tick 0 — without them
         ;; the star's Z≈0, solid surface density is ~0, and NO planets can seed
         ;; (see domain.planet-formation/planet-seeds). `:primordial` models a
         ;; first-generation, metal-free cloud.
         floor  (lcomp/metallicity-preset->composition metallicity)
         pmass  (/ (double total-mass) gas-count)
         ;; Render/visual radius for diffuse gas puffs; collision radius is kept
         ;; small so the cloud is transparent and many particles fit in the volume.
         prad   (* extent 0.003)
         ;; Circular speed at the cloud edge, v_circ = √(G·M/R): the velocity scale
         ;; that BALANCES self-gravity. Rotation (`spin`) and turbulence (`turb`)
         ;; are set as fractions of it, so the cloud starts marginally bound
         ;; (2·KE/|PE| ≈ 0.5) and collapses over MANY free-fall times — slowly
         ;; flattening into a rotating disk as turbulent support decays — instead
         ;; of the cold, near-instant free-fall (2·KE/|PE| ≈ 0.02) it did before.
         [v-vir omega] (nebula-velocity-scale total-mass extent spin)
         seeds  (seed-positions rng extent n-seeds)
         specs  (-> (make-gas-specs rng extent pmass prad v-vir omega seeds n-seeds seed-r turb floor gas-count)
                    center-spec-velocities)]
     (reduce (fn [w s] (first (seeder/spawn-clump w s))) world specs))))

;; --- World construction -----------------------------------------------------

(defn- entity->matter-state-resource
  "Project an ECS body into the resource map law.stellar/matter-state-contract
   governs — EXACTLY the schema keys (it's a :type contract, so no extras), with
   :matter-state renamed to :state and :id the entity id (integer)."
  [world eid]
  {:id          eid
   :position    (ecs/get-component world eid c/position)
   :velocity    (ecs/get-component world eid c/velocity)
   :mass        (ecs/get-component world eid c/mass)
   :radius      (ecs/get-component world eid c/radius)
   :temperature (ecs/get-component world eid c/temperature)
   :density     (ecs/get-component world eid c/density)
   :composition (ecs/get-component world eid c/composition)
   :state       (ecs/get-component world eid c/matter-state)
   :luminosity  (double (or (ecs/get-component world eid c/luminosity) 0.0))
   :pressure    (double (or (ecs/get-component world eid c/pressure) 0.0))})

(defn assert-seed-contracts!
  "Boot-time structural guard (AGENTS.md: 'every cross-boundary call must name a
   Malli validator'). Every seeded matter-state body is folded through a
   law.registry governed by law.stellar/matter-state-contract — a malformed seed
   throws HERE, before a long run, rather than corrupting physics mid-flight.
   Runs once at world creation (no per-tick cost). Returns the world unchanged.
   Disable with :genesis/validate-seed? false."
  [world]
  (when-not (false? (:genesis/validate-seed? world))
    (reduce (fn [reg eid] (lreg/add reg (entity->matter-state-resource world eid)))
            (lreg/->registry law/matter-state-contract)
            (ecs/entities-with world c/matter-state c/mass c/radius
                               c/position c/velocity c/density c/temperature
                               c/composition c/pressure)))
  world)

(defn- with-observer
  "Spawn the observer at the nebula centre (origin) so the player starts inside the
   cloud, not outside/below it."
  [world]
  (let [[w _] (player/spawn-observer world (sp/vec3 0 0 0))]
    w))

(defn- validate-world
  "Boot-time structural guard. Returns the world unchanged."
  [world]
  (assert-seed-contracts! world))

(def ^:private default-world-options
  "Default parameters for `create-world`. Override by passing the matching key."
  {:G law/G
   :theta 0.5
   :nebula-mass 4e30
   :nebula-radius 3.0e16
   :collapse-fraction 0.5
   :contraction-time 9.5e14
   :gas-count 1000
   :n-seeds 1
   :seed-r 0.25
   :spin 0.6
   :turb 0.15
   :wind-rate-scale 1.5
   :metallicity :population-i})

(defn- merge-options
  "Apply `default-world-options` to user-supplied opts."
  [opts]
  (merge default-world-options opts))

(defn- base-world
  "Create an empty ECS world, wire the shared ledger and collision handler, and
   attach the sim/genesis parameters that every later subsystem reads."
  [{:keys [G theta dt softening nebula-mass nebula-radius collapse-fraction
           contraction-time gas-count wind-rate-scale metallicity]
    :as _opts}]
  (let [neb (pacing/pacing-for (pacing/dynamical-time nebula-radius nebula-mass)
                               nebula-radius)]
    (-> (ecs/empty-world)
        (event/with-ledger)
        (event/register-handler :event/collision
                                stellar-merge/stellar-merge-handler)
        (assoc :sim/G G
               :sim/theta theta
               :sim/dt (or dt (:dt neb))
               :sim/softening (or softening (:softening neb))
               :genesis/sim-time 0.0
               :genesis/time-scale (:rate neb)
               :genesis/rate-yr (:rate-yr neb)
               :genesis/stats nil
               :genesis/complexity 0
               :genesis/active true
               :genesis/disk-maturity 3.156e13
               :genesis/star-ignition-time 0.0
               :genesis/adaptive-pacing? true
               :genesis/validate-neighbor-cache? false
               :genesis/wind-rate-scale wind-rate-scale
               :genesis/collapse-fraction collapse-fraction
               :genesis/contraction-time contraction-time
               :genesis/gas-particle-mass (/ (double nebula-mass) gas-count)
               :genesis/nebula-mass nebula-mass
               :genesis/nebula-radius nebula-radius
               :genesis/metallicity metallicity
               :genesis/observer-halo-mass-factor player/default-halo-mass-factor
               :genesis/influence-dv-cap player/default-influence-dv-cap
               :genesis/well-mass-factor intervention/default-well-mass-factor
               :genesis/well-radius intervention/default-radius
               :genesis/well-ttl intervention/default-ttl
               :genesis/heat-approach intervention/default-heat-approach
               :genesis/feeding-zone-factor (sink/resolution-feeding-zone-factor gas-count)))))

(defn- seeded-world
  "Seed the nebula of gas particles on `base` and attach the gas smoothing
   radius used by the classifier before bodies contract."
  [base {:keys [nebula-mass nebula-radius gas-count n-seeds seed-r spin turb metallicity]
         :as _opts}]
  (-> (seed-nebula base nebula-mass nebula-radius
                   {:gas-count gas-count :n-seeds n-seeds :seed-r seed-r
                    :spin spin :turb turb :metallicity metallicity})
      (assoc :genesis/gas-smoothing-radius (* nebula-radius 0.003))))

(defn create-world
  "Bootstrap a Phase 0 world ready to tick."
  ([] (create-world {}))
  ([opts]
   (let [opts (merge-options opts)]
     (-> (base-world opts)
         (seeded-world opts)
         with-observer
         validate-world))))

;; --- Lifecycle materialization ----------------------------------------------

(def ^:private consumed-markers
  "Lifecycle reap markers; an entity carrying ANY is despawned at world-construction."
  [c/consumed-merge c/consumed-accrete c/consumed-escape
   c/consumed-transfer c/consumed-ablation])

(def ^:private spawn-request-components
  "Lifecycle spawn requests; each is {eid [seed-spec ...]} materialized into new
   entities at world-construction."
  [c/spawn-request-flare
   c/spawn-request-accretion c/spawn-request-shatter
   c/spawn-request-disk c/spawn-request-planet c/spawn-request-condense])

(defn- spawn-entity
  "Materialize one spawn spec into a new entity, shifting its position by the
   current frame-offset so it lands in the same Galilean frame as its parents."
  [w spec]
  (let [spec  (update spec :position sp/v- (or (:genesis/frame-offset w) [0.0 0.0 0.0]))
        extra (:extra-components spec)
        [w2 neweid] (seeder/spawn-clump w (dissoc spec :extra-components))]
    (reduce-kv (fn [w k v] (ecs/put-component w neweid k v))
               w2 (or extra {}))))

(defn- spawn-requested
  "Materialize every spec for a single spawn-request component type."
  [world req-ct]
  (reduce-kv (fn [w eid specs]
               (let [w (ecs/remove-component w eid req-ct)]
                 (reduce spawn-entity w specs)))
             world
             (get-in world [:components req-ct] {})))

(defn- emit-escape-threshold
  "Aggregate ledger event for bodies that escaped the system this tick."
  [world]
  (let [escapers (keys (get-in world [:components c/consumed-escape] {}))]
    (if (seq escapers)
      (event/dispatch world
                      (event/->event {:tick     (:tick world)
                                      :kind     :event/body-escape
                                      :entities #{}
                                      :payload  {:count (count escapers)
                                                 :mass  (reduce + 0.0
                                                                (map #(double (or (ecs/get-component world % c/mass) 0.0))
                                                                     escapers))}}))
      world)))

(defn- reap-consumed
  "Despawn every entity carrying a consumed.* marker."
  [world]
  (let [consumed (into #{} (mapcat #(keys (get-in world [:components %] {})))
                       consumed-markers)]
    (reduce ecs/despawn world consumed)))

(defn materialize-lifecycle
  "World-construction step (spec §5): spawn the entities requested by the fan-out
   lifecycle emitters (spawn-request.*), then reap every entity marked consumed.*.
   This is NOT a contended-state write — continuous state (mass/momentum/blend)
   flows through the integrator as influences; only entity creation/removal
   happens here, which is automatically Jacobi-consistent (a body created this
   tick was invisible to every this-tick reader; a body removed was fully present
   in the snapshot). Pure: world → world'."
  [world]
  (-> (reduce spawn-requested world spawn-request-components)
      emit-escape-threshold
      reap-consumed))
