(ns domain.phase0
  "Phase 0: Stellar Nebula — composition layer over the ECS substrate.

   This is NOT a separate engine. It bootstraps a normal ECS world, wires the
   stellar/thermal/fusion/collision/observer systems into a tick pipeline, seeds
   a nebula of entities and the player's observer spark, and drives the world
   forward while emitting threshold events into the shared ledger.

   Everything here is pure data transformation; rendering and IO live in infra."
  (:require
   [domain.stellar          :as stellar]
   [domain.em               :as em]
   [domain.hydro            :as hydro]
   [domain.regime           :as regime]
   [domain.chemistry        :as chemistry]
   [domain.player           :as player]
   [domain.intervention     :as intervention]
   [domain.pacing           :as pacing]
   [law.stellar             :as law]
   [law.composition         :as lcomp]
   [law.sed                 :as lsed]
   [law.plasma              :as lplasma]
   [law.registry            :as lreg]
   [domain.ecs.core         :as ecs]
   [domain.ecs.event        :as event]
   [domain.ecs.registry     :as reg]
   [domain.ecs.tick         :as tick]
   [domain.ecs.components    :as c]
    [domain.orbital.system   :as orbital]
    [domain.integrator       :as integ]
    [domain.physics.collision :as collision]
    [domain.spatial.index    :as spatial]
    [shape.spatial           :as sp]))

;; --- Nebula seeding ---------------------------------------------------------

(defn- gas-particle-spec
  "One equal-mass, self-gravitating gas particle of a cold, rotating, turbulent
   cloud. Nothing is pre-formed: every particle starts as diffuse gas. Solid-body
   rotation (sub-virial, so the cloud collapses) plus turbulence and a bias toward
   `seeds` (overdensity centres) give the cloud the structure it needs to
   fragment and accrete into clumps, planets, and a star-forming core."
  [^java.util.Random rng extent pmass prad v-vir omega seeds n-seeds seed-r turb]
  (let [to-seed? (and (seq seeds) (< (.nextDouble rng) 0.40))
        [px py pz]
        (if to-seed?
          (let [[cx cy cz] (nth seeds (.nextInt rng n-seeds))
                g (fn [s] (* s extent seed-r (.nextGaussian rng)))]
            [(+ cx (g 1.0)) (+ cy (g 1.0)) (+ cz (g 0.6))])
           (let [r  (* extent (Math/pow (.nextDouble rng) 0.5)) ; centrally concentrated but diffuse
                th (* 2.0 Math/PI (.nextDouble rng))
                ph (Math/acos (- (* 2.0 (.nextDouble rng)) 1.0))]
            [(* r (Math/sin ph) (Math/cos th))
             (* r (Math/sin ph) (Math/sin th))
             (* r (Math/cos ph))]))
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
      :composition lcomp/primordial-composition}))

(defn seed-nebula
  "Seed a cold, rotating, turbulent, self-gravitating gas cloud on the single ECS
   world — `gas-count` equal-mass particles, no pre-placed core or planets. Stars,
   planets, and debris must EMERGE by gravitational collapse and accretion. A few
   Gaussian overdensity seeds give the cloud something to fragment around.
   Deterministic (seeded RNG) so runs and tests reproduce."
  ([world total-mass extent] (seed-nebula world total-mass extent {}))
  (   [world total-mass extent {:keys [gas-count n-seeds seed-r spin turb seed]
                             ;; `seed-r` widened (0.12→0.18 of extent) so the
                             ;; overdensity clumps are diffuse, not pinpoints: at
                             ;; 0.12 each seed's local free-fall time was far
                             ;; shorter than the timestep, so it imploded in a
                             ;; couple of ticks ("collapses awfully fast"). Wider,
                             ;; sparser seeds resolve the collapse over many ticks.
                             :or   {gas-count 1000 n-seeds 5 seed-r 0.18
                                    spin 0.55 turb 0.08 seed 42}}]
   (let [rng    (java.util.Random. (long seed))
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
         v-vir  (Math/sqrt (/ (* law/G total-mass) extent))
         omega  (/ (* spin v-vir) extent)
         seeds  (vec (repeatedly n-seeds
                       (fn [] (sp/vec3 (* extent 0.8 (- (* 2.0 (.nextDouble rng)) 1.0))
                                       (* extent 0.8 (- (* 2.0 (.nextDouble rng)) 1.0))
                                       (* extent 0.25 (- (* 2.0 (.nextDouble rng)) 1.0))))))
         specs  (mapv (fn [_] (gas-particle-spec rng extent pmass prad
                                                 v-vir omega seeds n-seeds seed-r turb))
                      (range gas-count))
         ;; anchor the centre of mass: subtract the net momentum (equal masses,
         ;; so just the mean velocity) so the whole system doesn't drift away.
         n      (double (count specs))
         mean-v (reduce (fn [acc s] (sp/v+ acc (:velocity s))) (sp/vec3 0 0 0) specs)
         mean-v (sp/v* mean-v (/ 1.0 n))
         specs  (mapv (fn [s] (update s :velocity sp/v- mean-v)) specs)]
     (reduce (fn [w s] (first (stellar/spawn-clump w s))) world specs))))

;; --- Observable progress ----------------------------------------------------
;; The game clock / timestep pacing lives in `domain.pacing` (a fixed 60 Hz tick
;; rate with a `dt` that dilates with the bulk cloud's dynamical time).

(defn thermal-progress
  "Smooth 0→1 measure of how far the system has climbed from cold nebular gas
   (~10 K) toward fusion ignition (~1e7 K), on a log-temperature ramp."
  [peak-temp]
  (let [t (max 10.0 (double (or peak-temp 10.0)))]
    (max 0.0 (min 1.0 (/ (- (Math/log10 t) 1.0) 6.0)))))

(def ^:private solar-mass 1.989e30)

(defn stats-of
  "Observable readouts for the HUD, tallied once per tick from the post-physics
   world and a precomputed `summ`: total mass (kg and solar masses),
   mass-weighted mean temperature, peak temperature, and the body/resolved/
   star/planet counts. Pure; cached on the world so the renderer reads it
   cheaply every frame instead of re-walking the entity set at 60 Hz."
  [world summ]
  (let [eids   (ecs/entities-with world c/mass)
        [m mt peak]
        (reduce (fn [[m mt peak] eid]
                  (let [mass (double (or (ecs/get-component world eid c/mass) 0.0))
                        t    (double (or (ecs/get-component world eid c/temperature) 0.0))]
                    [(+ m mass) (+ mt (* mass t)) (max peak t)]))
                [0.0 0.0 0.0] eids)
        ;; Mass held OFF the bulk c/mass ledger but still part of the system:
        ;; a protostar/star routes accreted material into c/disk-mass (returned to
        ;; bulk later by disk-evolution-system's viscous accretion), and a star
        ;; debits shed-but-not-yet-ejected wind mass into c/wind-reservoir. Both
        ;; are real mass; excluding them made the headline total visibly "bounce"
        ;; (and under-count) as accretion shuttled mass through the disk. The total
        ;; is conserved only when all three buckets are summed.
        disk-mass (reduce (fn [acc eid]
                            (+ acc (double (or (ecs/get-component world eid c/disk-mass) 0.0))))
                          0.0 (ecs/entities-with world c/disk-mass))
        resv-mass (reduce (fn [acc eid]
                            (+ acc (double (or (ecs/get-component world eid c/wind-reservoir) 0.0))))
                          0.0 (ecs/entities-with world c/wind-reservoir))
        total     (+ m disk-mass resv-mass)
        resolved-mass (reduce (fn [acc r]
                                (if (= :nebula (:matter-state r))
                                  acc
                                  (+ acc (double (or (:mass r) 0.0)))))
                              0.0 (:regions summ))]
    {:total-mass-kg     total
     :total-mass-msun   (/ total solar-mass)
     :bulk-mass-kg      m
     :disk-mass-kg      disk-mass
     :resolved-fraction (if (pos? m) (/ resolved-mass m) 0.0)
     :avg-temp          (if (pos? m) (/ mt m) 0.0)
     :peak-temp         peak
     :body-count        (:body-count summ)
     :resolved-count    (:resolved-count summ)
     :star-count        (count (:stars summ))
     :planet-count      (:planet-count summ)
     ;; Phase 1 stats
     :xuv-escape-count  (count (ecs/entities-with world c/atmosphere-escape))
     :sed-band-count    (count (ecs/entities-with world c/sed-bands))
     :lod-local         (count (filterv #(= :local (ecs/get-component world % c/lod-level))
                                        (ecs/entities-with world c/lod-level)))
     :lod-system        (count (filterv #(= :system (ecs/get-component world % c/lod-level))
                                        (ecs/entities-with world c/lod-level)))
     :lod-galaxy        (count (filterv #(= :galaxy (ecs/get-component world % c/lod-level))
                                        (ecs/entities-with world c/lod-level)))
     ;; IMF mass histogram: star counts in mass bins (solar masses)
     ;; Bins: <0.1, 0.1-0.5, 0.5-1, 1-2, 2-5, 5-10, 10-50, >50
     :imf-bins          (let [stars (filterv #(= :star (ecs/get-component world % c/matter-state))
                                             (ecs/entities-with world c/matter-state c/mass))
                              bins (vec (repeat 8 0))]
                          (reduce (fn [b eid]
                                    (let [m-msun (/ (double (or (ecs/get-component world eid c/mass) 0.0))
                                                    1.989e30)]
                                      (cond
                                        (< m-msun 0.1)  (update b 0 inc)
                                        (< m-msun 0.5)  (update b 1 inc)
                                        (< m-msun 1.0)  (update b 2 inc)
                                        (< m-msun 2.0)  (update b 3 inc)
                                        (< m-msun 5.0)  (update b 4 inc)
                                        (< m-msun 10.0) (update b 5 inc)
                                        (< m-msun 50.0) (update b 6 inc)
                                        :else           (update b 7 inc))))
                                  bins stars))
     :disk-count        (count (filterv #(pos? (double (or (ecs/get-component world % c/disk-mass) 0.0)))
                                        (ecs/entities-with world c/disk-mass)))}))

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
   Disable with :phase0/validate-seed? false."
  [world]
  (when-not (false? (:phase0/validate-seed? world))
    (reduce (fn [reg eid] (lreg/add reg (entity->matter-state-resource world eid)))
            (lreg/->registry law/matter-state-contract)
            (ecs/entities-with world c/matter-state c/mass c/radius
                               c/position c/velocity c/density c/temperature
                               c/composition c/pressure)))
  world)

(defn create-world
  "Bootstrap a Phase 0 world ready to tick."
  ([] (create-world {}))
   ([{:keys [G theta dt softening nebula-mass nebula-radius collapse-fraction
             contraction-time gas-count spin turb wind-rate-scale]
      ;; `dt`/`softening` default to the cold-cloud pacing values (`pacing-for`
      ;; at complexity 0); pass them only to override. Softening is matched to the
      ;; timestep: the dynamical time at the Plummer length must exceed dt or
      ;; close passes inject energy and eject gas (the cloud "evaporates").
      ;; ε ≳ (G·M·dt²)^(1/3); the cold-cloud ε≈5e14 m keeps the system bound.
      ;;
      ;; Timescale tuning (sim-time, NOT wall-clock — see `pacing-for`):
      ;;  • `nebula-radius` sets the cloud's free-fall time t_ff ∝ R^1.5; the
      ;;    default below stretches nebula collapse + local accretion to tens of
      ;;    Myr so formation is grand rather than a few-Myr blink. Raising it
      ;;    lengthens that further, but accretion is LOCAL (literal-overlap
      ;;    merges, not teleport) so too diffuse a cloud may never assemble a
      ;;    stellar core — there is a ceiling before star formation stalls.
      ;;  • `contraction-time` (τ) sets how long the protostar takes to contract
      ;;    to ignition; ~30 Myr by default (see `stellar/collapse-system`).
      ;;  • `spin` is the cloud's rotational support (fraction of virial). Higher
      ;;    spin flattens the collapse into a rotationally-supported disk that can
      ;;    fragment into planets instead of all draining onto the core.
      ;;  • `wind-rate-scale` (k_wind) is the stellar-wind intensity dial (see
      ;;    phase0-stellar-winds-and-mass-loss spec). Default is cinematic-lively:
      ;;    stars visibly shed and reabsorb gas, and a dominant star emerges from
      ;;    competitive reabsorption. Set ~1.0 for physically-subtle winds.
      :or   {G law/G theta 0.5
             nebula-mass 4e30 nebula-radius 2.0e16 collapse-fraction 0.5
             contraction-time 9.5e14 gas-count 1000 spin 0.6 turb 0.15
             wind-rate-scale 1.5}}]
   (let [neb     (pacing/pacing-for (pacing/dynamical-time nebula-radius nebula-mass)
                                    nebula-radius)
         pmass   (/ (double nebula-mass) gas-count)
         base    (-> (ecs/empty-world)
                     (event/with-ledger)
                     (event/register-handler :event/collision
                                             stellar/stellar-merge-handler)
                     (assoc :sim/G G :sim/theta theta
                            :sim/dt (or dt (:dt neb)) :sim/softening (or softening (:softening neb))
                            :phase0/sim-time          0.0
                            :phase0/time-scale        (:rate neb)
                            :phase0/rate-yr           (:rate-yr neb)
                            :phase0/stats             nil
                            :phase0/complexity        0
                            :phase0/phase             :initializing
                            :phase0/active            true
                            ;; Adaptive pacing dilates :sim/dt with complexity at
                            ;; a fixed 60 Hz tick rate (see `pacing-for`). Set
                            ;; false to hold :sim/dt constant — useful for fast,
                            ;; pace-independent tests and deterministic runs.
                            :phase0/adaptive-pacing?  true
                            :phase0/wind-rate-scale   wind-rate-scale
                            :phase0/collapse-fraction collapse-fraction
                            :phase0/contraction-time  contraction-time
                            :phase0/gas-particle-mass pmass
                            :phase0/feeding-zone-factor
                            (stellar/resolution-feeding-zone-factor gas-count)))
         seeded (seed-nebula base nebula-mass nebula-radius
                             {:gas-count gas-count :spin spin :turb turb})
         ;; Store the gas smoothing radius so the classifier can compute
         ;; accretion radii from it (before KH contraction shrinks bodies).
          seeded (assoc seeded :phase0/gas-smoothing-radius (* nebula-radius 0.003))
         [w _]  (player/spawn-observer seeded (sp/vec3 0 0 (* nebula-radius 2)))]
     (assert-seed-contracts! w))))

;; --- Observable summary -----------------------------------------------------

(defn system-summary
  "Tally the world's resolved matter into the shape used for complexity, phase
   detection, and habitability."
  [world]
  (let [eids    (ecs/entities-with world c/matter-state c/mass)
        regions (mapv #(stellar/entity->region world %) eids)
        stars   (filterv #(= :star (:matter-state %)) regions)
        planets (filterv #(= :planet (:matter-state %)) regions)
        ;; resolved = anything that has condensed out of the diffuse gas
        resolved (filterv #(not= :nebula (:matter-state %)) regions)]
    {:body-count     (count regions)
     :resolved-count (count resolved)
     :star?          (boolean (seq stars))
     :fusion?        (boolean (seq stars))
     :planet-count   (count planets)
     :stars          stars
     :planets        planets
     :regions        regions}))

(defn detect-phase
  "Detect the current phase of the forming system from its summary."
  [{:keys [star? planet-count body-count regions]} sim-time]
  (let [nebula?    (some #(= :nebula (:matter-state %)) regions)
        protostar? (some #(= :protostar (:matter-state %)) regions)
        planet?    (some #(= :planet (:matter-state %)) regions)
        debris?    (some #(= :debris (:matter-state %)) regions)]
    (cond
      (and star? (pos? planet-count)) :phase-0/planets-formed
      (and star? (>= body-count 3))   :phase-0/accretion
      star?                           :phase-0/ignition
      protostar?                      :phase-0/protostar
      (or planet? debris?)            :phase-0/accretion
      (zero? body-count)              :phase-0/dispersed
      (and nebula? (< sim-time 1e18)) :phase-0/nebula-collapse
      :else                           :phase-0/dispersed)))

;; --- Tick driver ------------------------------------------------------------

(defn- emit-threshold
  "Emit a threshold event into the ledger at the world's current tick."
  [world kind data]
  (event/dispatch world
    (event/->event {:tick     (:tick world)
                    :kind     kind
                    :entities #{}
                    :payload  {:data data}})))

(defn center-of-mass
  "Mass-weighted centre of mass of every positioned body, or [0 0 0] when empty.
   A global reduction over the snapshot — the recenter frame-offset (spec §6, §8)."
  [world]
  (let [eids (ecs/entities-with world c/position c/mass)]
    (if (seq eids)
      (let [[sx sy sz m]
            (reduce (fn [[ax ay az am] eid]
                      (let [[x y z] (ecs/get-component world eid c/position)
                            mm (double (ecs/get-component world eid c/mass))]
                        [(+ ax (* (double x) mm)) (+ ay (* (double y) mm))
                         (+ az (* (double z) mm)) (+ am mm)]))
                    [0.0 0.0 0.0 0.0] eids)]
        (if (pos? m) [(/ sx m) (/ sy m) (/ sz m)] [0.0 0.0 0.0]))
      [0.0 0.0 0.0])))

(defn xuv-atmospheric-escape-system
  "Write-set emitter: planetary atmospheric escape under stellar XUV. For each
   :planet, find the nearest star's SED bands, compute the XUV flux at the
   planet's distance, the escape regime, and the mass-loss rate; emit the mass
   loss as :component/mass-flux.xuv (negative, the integrator owns mass) and the
   diagnostic :component/atmosphere-escape (its own column). A pure snapshot-
   reading fan-out emitter (was a serial barrier writing mass directly). Mass
   loss is clamped to ≤1% of M per tick and never below a 1e20 kg rocky core."
  []
  {:id     :xuv-atmospheric-escape
   :writes #{c/mass-flux-xuv c/atmosphere-escape}
   :run
   (fn [world]
     (let [dt     (double (or (:sim/dt world) 1.0e12))
           stars  (ecs/entities-with world c/matter-state c/luminosity c/position c/sed-bands)
           star-data (mapv (fn [eid]
                             {:eid eid
                              :pos (ecs/get-component world eid c/position)
                              :sed (ecs/get-component world eid c/sed-bands)})
                           stars)
           planets (filterv #(= :planet (ecs/get-component world % c/matter-state))
                            (ecs/entities-with world c/matter-state c/mass c/radius c/position))
           results
           (keep (fn [eid]
                   (let [pos   (ecs/get-component world eid c/position)
                         R     (double (or (ecs/get-component world eid c/radius) 0.0))
                         M     (double (or (ecs/get-component world eid c/mass) 0.0))
                         nearest (when (seq star-data)
                                   (apply min-key #(sp/dist pos (:pos %)) star-data))
                         dist    (when nearest (sp/dist pos (:pos nearest)))
                         bands   (when nearest (get-in nearest [:sed :bands]))]
                     (when (and bands dist (pos? dist) (pos? R) (pos? M))
                       (let [L-xuv (lsed/xuv-luminosity bands)
                             F-xuv (lplasma/xuv-flux-at L-xuv dist)
                             regime (lplasma/escape-regime F-xuv R)
                             mdot   (case regime
                                      :energy-limited
                                      (lplasma/energy-limited-escape F-xuv R M 0.15)
                                      :recombination-limited
                                      (* (lplasma/energy-limited-escape F-xuv R M 0.15) 0.6)
                                      :blow-off
                                      (* (lplasma/energy-limited-escape F-xuv R M 0.15) 2.0)
                                      0.0)
                             ;; dm = Ṁ·dt, ≤1% of M/tick, never below a 1e20 kg core
                             dm     (min (* mdot dt) (* 0.01 M) (max 0.0 (- M 1.0e20)))]
                         [eid dm {:regime regime :xuv-flux F-xuv :mass-loss-rate mdot}]))))
                 planets)]
       {c/mass-flux-xuv (into {} (keep (fn [[eid dm _]] (when (pos? dm) [eid (- dm)]))) results)
        c/atmosphere-escape (into {} (map (fn [[eid _ esc]] [eid esc])) results)}))})

;; --- LOD Scheduler (Phase 1) ------------------------------------------------
;; Observer-centric level-of-detail: assigns c/lod-level to stars and planets
;; based on distance from the player's focus. Controls which Phase 1 systems
;; are relevant at each fidelity level.
;; :local  — within 0.5 AU: full detail (atmosphere shells, XUV escape, CME)
;; :system — within 5 AU: band luminosities and steady winds
;; :galaxy — beyond 5 AU: coarse SED only

(def ^:const lod-local-radius
  "Distance (m) within which entities are at :local LOD. ~0.5 AU."
  7.5e10)

(def ^:const lod-system-radius
  "Distance (m) within which entities are at :system LOD. ~5 AU."
  7.5e11)

(defn lod-scheduler
  "Assign c/lod-level (:local, :system, :galaxy) to every star and planet
   based on distance from the player observer's focus position."
  [world]
  (if-let [obs (player/get-observer world)]
    (let [focus (:focus-position obs [0.0 0.0 0.0])
          eids (filterv (fn [eid]
                          (let [st (ecs/get-component world eid c/matter-state)]
                            (or (= :star st) (= :planet st))))
                        (ecs/entities-with world c/matter-state c/position))]
      (reduce (fn [w eid]
                (let [pos  (ecs/get-component w eid c/position)
                      dist (sp/dist focus pos)
                      level (cond
                              (< dist lod-local-radius)  :local
                              (< dist lod-system-radius) :system
                              :else                       :galaxy)]
                  (ecs/put-component w eid c/lod-level level)))
              world
              eids))
    world))

;; --- Magnetosphere coupling (Phase 1) ----------------------------------------
;; Stellar wind and CME parcels carry ram pressure and B-field. When they reach
;; a planet, they compress its magnetosphere. The standoff distance r_mp is where
;; the planet's magnetic pressure equals the wind ram pressure: B²/(2μ₀) = P_ram.

(def ^:const mu-0 1.25663706212e-6) ;; vacuum permeability (T·m/A)

(defn- magnetopause-distance
  "Standoff distance (m) where planetary magnetic pressure balances wind ram
   pressure: r_mp = R_p × (B_p² / (2μ₀ P_ram))^(1/6). Returns R_p when no wind."
  [planet-radius planet-b-field ram-pressure]
  (let [Rp  (double (or planet-radius 0.0))
        Bp  (double (or planet-b-field 0.0))
        Pram (double (or ram-pressure 0.0))]
    (if (and (pos? Rp) (pos? Bp) (pos? Pram))
      (* Rp (Math/pow (/ (* Bp Bp) (* 2.0 mu-0 Pram)) (/ 1.0 6.0)))
      Rp)))

(defn magnetosphere-coupling-system
  "For each :planet, find nearby ionized wind/CME parcels and compute magnetosphere
   compression. Writes c/magnetosphere with standoff distance and compression factor.
   A compressed magnetosphere (small standoff) means more atmospheric exposure.
   Runs in the parallel fan-out (was a cargo-cult barrier)."
  [world]
  (let [wind-parcels (filterv (fn [eid]
                                (let [st (ecs/get-component world eid c/matter-state)]
                                  (and (= :nebula st)
                                       (pos? (double (or (ecs/get-component world eid c/ionization-fraction) 0.0))))))
                              (ecs/entities-with world c/matter-state c/position c/mass c/radius))
        wind-data (mapv (fn [eid]
                          {:pos (ecs/get-component world eid c/position)
                           :ram (double (or (ecs/get-component world eid c/ram-pressure) 0.0))})
                        wind-parcels)
        planets (filterv #(= :planet (ecs/get-component world % c/matter-state))
                         (ecs/entities-with world c/matter-state c/position c/radius))]
    (reduce (fn [w eid]
              (let [pos    (ecs/get-component w eid c/position)
                    Rp     (double (or (ecs/get-component w eid c/radius) 0.0))
                    Bp     (double (or (some-> (ecs/get-component w eid c/b-field) sp/len) 0.0))
                    cutoff (* 10.0 Rp)
                    nearby-ram (reduce (fn [acc wd]
                                         (if (< (sp/dist pos (:pos wd)) cutoff)
                                           (+ acc (:ram wd))
                                           acc))
                                       0.0 wind-data)
                    r-mp       (magnetopause-distance Rp Bp nearby-ram)
                    compression (if (pos? Rp) (min 10.0 (/ Rp (max 1.0e3 r-mp))) 1.0)]
                (ecs/put-component w eid c/magnetosphere
                                  {:standoff-distance r-mp
                                   :compression compression})))
            world
            planets)))

(defn physics-systems-parallel
  "The transform systems as write-set systems for the double-buffer fan-out
   (`domain.ecs.tick/run-parallel`). Each entry is its legacy `(fn [world] world')`
   wrapped by the bridge and masked to its registry-declared `:writes`.

   EXCLUDES barrier systems that do not belong in the parallel region:
     • `recenter` — a global centre-of-mass reduction; runs at the barrier or
       becomes a frame-offset in the motion integrator (spec §5).
     • `disk-evolution`, `fusion-promotion`, `sink-formation` — post-fold barriers
       that read folded state (Part C will convert these).

   Gravity and motion are NATIVE write-set systems (the gravity tree-walk runs
   on its own thread, the integrator sums all accel.* contributions). The rest
   are still legacy `(fn [world] world')` systems run through the bridge and
   masked to their registry-declared `:writes`. Transitional: the caller folds
   with `:last-wins` until single-writer holds (spec §9). Legacy `:writes` is
   sourced from `domain.ecs.registry` so the pairing and the invariant cannot
   drift apart."
  [{:keys [sim/G sim/theta sim/dt sim/softening]}]
  (let [writes-for (fn [id] (some #(when (= id (:id %)) (:writes %)) reg/systems))
        legacy     (fn [id f] (tick/legacy-system id (writes-for id) f))]
    [;; native write-set systems (force emitters + integrator)
     (orbital/gravity-acceleration G theta (or softening 1e14))
     (hydro/pressure-acceleration)
     (em/lorentz-acceleration-system)
     (intervention/warp-acceleration-system)
     (player/observer-acceleration-system)
     (intervention/thermal-intervention-system)
     (integ/integrator-system dt)
     ;; legacy-bridged transform systems
     (stellar/structure-system)
     (stellar/eos-system)
     (stellar/classifier-system)
      (em/field-system dt)
      (legacy :fusion         stellar/fusion-system)
      (stellar/stellar-sed-system)
      (stellar/atmosphere-shells-system)
      (chemistry/nucleosynthesis-system dt)
      (stellar/deuterium-depletion-system)
      (stellar/stellar-wind-system)
      (stellar/stellar-flare-system)
      (xuv-atmospheric-escape-system)
     (legacy :regime         regime/regime-system)
     ;; em's magnetic braking → torque.em (the integrator owns angular-momentum/
     ;; spin); Lorentz via em-lorentz, b-field via field.
     (em/em-torque-system dt)
      ;; Collision detection: now a fan-out emitter (B3). Its handler emits
      ;; c/absorb-merge, c/consumed-merge, c/spawn-request-shatter — all
      ;; single-writer. Runs in parallel, not serially at the barrier.
      (legacy :collision-detection collision/collision-detection-system)
      ;; Former serial barriers — now fan-out emitters (Part C):
      ;; Fusion promotion: emits c/promotion-signal (single-writer).
      (legacy :fusion-promotion stellar/fusion-promotion-system)
      ;; Sink formation: emits c/absorb-accrete, c/consumed-accrete (single-writer).
      (legacy :sink-formation stellar/sink-formation-system)
      ;; Disk evolution: emits c/disk-mass, c/disk-angular-mom, c/mass-flux-disk,
      ;; c/torque-disk, c/spawn-request-disk (all single-writer).
      (legacy :disk-evolution stellar/disk-evolution-system)
      ;; LOD scheduler: assigns c/lod-level (single-writer, was cargo-cult barrier).
      (legacy :lod-scheduler lod-scheduler)
      ;; Magnetosphere coupling: computes c/magnetosphere (single-writer, was
      ;; cargo-cult barrier).
      (legacy :magnetosphere-coupling magnetosphere-coupling-system)]))

(def ^:private consumed-markers
  "Lifecycle reap markers; an entity carrying ANY is despawned at world-construction."
  [c/consumed-merge c/consumed-accrete c/consumed-wind])

(def ^:private spawn-request-components
  "Lifecycle spawn requests; each is {eid [seed-spec ...]} materialized into new
   entities at world-construction."
  [c/spawn-request-wind c/spawn-request-flare
   c/spawn-request-accretion c/spawn-request-shatter
   c/spawn-request-disk])

(defn materialize-lifecycle
  "World-construction step (spec §5): spawn the entities requested by the fan-out
   lifecycle emitters (spawn-request.*), then reap every entity marked consumed.*.
   This is NOT a contended-state write — continuous state (mass/momentum/blend)
   flows through the integrator as influences; only entity creation/removal
   happens here, which is automatically Jacobi-consistent (a body created this
   tick was invisible to every this-tick reader; a body removed was fully present
   in the snapshot). Pure: world → world'."
  [world]
  (let [;; spawn requested entities, clearing each request as it is consumed
        spawned
        (reduce
         (fn [w req-ct]
           (reduce-kv
            (fn [w eid specs]
              (let [w (ecs/remove-component w eid req-ct)]
                (reduce (fn [w spec]
                          (let [extra (:extra-components spec)
                                [w2 neweid] (stellar/spawn-clump w (dissoc spec :extra-components))]
                            (reduce-kv (fn [w k v] (ecs/put-component w neweid k v))
                                       w2 (or extra {}))))
                        w specs)))
            w
            (get-in w [:components req-ct] {})))
         world
         spawn-request-components)
        ;; reap every consumed entity (despawn removes all its components)
        consumed (into #{} (mapcat #(keys (get-in spawned [:components %] {})))
                       consumed-markers)]
    (reduce ecs/despawn spawned consumed)))

(defn step-physics
  "Run one tick of physics over `world` (already tick-advanced).

   Every system runs concurrently reading the FROZEN `world` and writing only
   the components it owns; the disjoint write-sets fold at a single barrier
   (`:throw` enforces single-writer at runtime). No serial barrier systems remain
   — all are fan-out emitters (Part C). System order in the parallel region is
   irrelevant by construction.

   This double-buffer single-writer pipeline is the ONLY runtime (design §7c):
   density-gated condensation, the single-writer classifier, and sink accretion
   form a star. There is deliberately no second simulation path."
  [world]
  (tick/run-parallel world (physics-systems-parallel world)))

(defn tick-world
  "Advance the world by one tick. Pure: world -> world'."
  [world]
  (if-not (:phase0/active world)
    world
  (let [dt         (:sim/dt world)
        effective-dt dt
        prev       (system-summary world)
        prev-phase (:phase0/phase world)
        ;; advance logical tick first so every event this step shares its tick;
        ;; arm the integrator with the recenter frame-offset — the COM of THIS
        ;; snapshot, subtracted from every new position so the formation stays in
        ;; its COM frame (spec §6: a one-tick-stale, pure-Galilean shift, replacing
        ;; the old post-fold recenter-system). A world scalar, single-owner.
        world1     (-> (ecs/advance-tick world)
                       (assoc :phase0/frame-offset (center-of-mass world))
                       spatial/spatial-index)
        world2     (-> (step-physics world1)
                       (intervention/expire-interventions)
                       materialize-lifecycle)
        summ       (system-summary world2)
        complexity (stellar/complexity-score summ)
        phase      (detect-phase summ (:phase0/sim-time world2))
        stats      (stats-of world2 summ)
        ;; Fixed tick rate, dilating timestep: the per-tick step tracks the BULK
        ;; cloud's dynamical time (see `pacing-for`). The tick count never changes
        ;; — 60 Hz throughout — so as the cloud actually collapses, t_dyn shrinks,
        ;; the clock dilates, and every body (all sharing the contracting scale)
        ;; stays resolved. A single hot protostar does not change the bulk scale,
        ;; so it can never freeze the outer cloud — the failure of the old
        ;; temperature-driven dt.
        ;; Disabled (`:phase0/adaptive-pacing? false`) → :sim/dt is held constant.
        ;; Time slip: when the observer's attention has lapsed (low coherence) over
        ;; a low-complexity region, the clock SLIPS — the per-tick step inflates and
        ;; the unwatched universe fast-forwards until something draws the eye back.
        ;; Reads the observer's coherence from the input SNAPSHOT (observer-system is
        ;; its sole writer and hasn't run; the value is identical in world/1/2), so
        ;; the new dt and the new coherence both land NEXT tick — the same Jacobi lag
        ;; pacing already carries, not a Gauss–Seidel ordering dependence. Like
        ;; pacing it's an adaptive-clock scalar, so it only applies when adaptive
        ;; pacing is on.
        slipping?      (when-let [obs (player/get-observer world2)]
                         (player/time-slip-threshold? obs complexity))
        pacing         (when-not (false? (:phase0/adaptive-pacing? world))
                         (-> (pacing/pace world2)
                             (pacing/with-time-slip (boolean slipping?))))
        world3     (cond-> world2
                     (and (:star? summ) (not (:star? prev)))
                     (emit-threshold :event/stellar-ignition (first (:stars summ)))

                     (> (:planet-count summ) (:planet-count prev))
                     (emit-threshold :event/planet-formation (first (:planets summ)))

                     (not= phase prev-phase)
                     (emit-threshold :event/phase-transition {:from prev-phase :to phase}))
        ;; `dt` here is the step this tick actually integrated (captured above);
        ;; advance the clock by it. When adaptive, arm the NEXT tick with the
        ;; complexity-refined dt/softening and report the derived wall-clock rate
        ;; for the player's clock; otherwise leave the fixed step in place.
        world4     (cond-> (assoc world3
                             :phase0/complexity complexity
                             :phase0/stats      stats
                             :phase0/phase      phase
                             :phase0/sim-time   (+ (:phase0/sim-time world3) dt))
                     pacing (assoc :phase0/time-scale    (:rate pacing)
                                   :phase0/rate-yr       (:rate-yr pacing)
                                   :phase0/time-slipping? (boolean (:time-slipping? pacing))
                                   :sim/dt               (:dt pacing)
                                   :sim/softening        (:softening pacing)))
        world5     ((player/observer-system effective-dt) world4)
        obs        (player/get-observer world5)]
    (assoc world5 :phase0/active
           (and (player/can-interact? obs)
                (not= phase :phase-0/dispersed))))))

;; --- Field insight ----------------------------------------------------------

(defn field-report
  "A one-line readout of the live fields for insight: tick/phase, body counts,
   temperature range, peak magnetic field, and the regime histogram."
  [world]
  (let [eids    (ecs/entities-with world c/matter-state)
        temps   (keep #(ecs/get-component world % c/temperature) eids)
        bmags   (keep #(some-> (ecs/get-component world % c/b-field) sp/len) eids)
        regimes (frequencies (keep #(ecs/get-component world % c/regime) eids))
        summ    (system-summary world)]
    (format "t=%-4d %-22s | bodies=%-4d resolved=%-3d star=%-5s planets=%d | T=%.0f..%.1e K | Bmax=%.1e T | %s"
            (:tick world) (name (:phase0/phase world))
            (:body-count summ) (:resolved-count summ)
            (str (:star? summ)) (:planet-count summ)
            (double (reduce min 1.0e30 temps)) (double (reduce max 0.0 temps))
            (double (reduce max 0.0 bmags))
            (pr-str regimes))))

;; --- Player input -----------------------------------------------------------

(defn handle-input
  "Apply a player control to the world's observer."
  [world input-type & args]
  (case input-type
    :move-focus  (let [[pos] args]
                   (player/update-observer world
                     #(player/set-focus % pos (:focus-radius %) (:focus-intensity %))))
    :narrow-focus (player/update-observer world #(player/narrow-focus % 2.0))
    :widen-focus  (player/update-observer world #(player/widen-focus % 2.0))
    :release      (player/update-observer world
                    #(player/release-focus %
                       (fn [pos]
                         (let [dir (sp/v- (sp/vec3 0 0 0) pos)
                               l   (sp/len dir)]
                           (if (pos? l) (sp/v* dir (/ 1.0 l)) dir)))))
    world))

;; --- Habitability / handoff -------------------------------------------------

(defn habitability-of
  "Habitability score of a resolved body region for the chemistry model."
  [region]
  (chemistry/habitability-score region))

(defn habitable-worlds
  "Resolved planet regions with non-trivial habitability potential, for the
   handoff to Phase 1."
  [world]
  (->> (:planets (system-summary world))
       (filter #(> (habitability-of %) 0.2))))

(defn ready-for-phase-1?
  [world]
  (and (= (:phase0/phase world) :phase-0/planets-formed)
       (seq (habitable-worlds world))))

;; --- Endings ----------------------------------------------------------------

(defn world-ending
  "If the world has reached a terminal state, describe it; else nil."
  [world]
  (let [phase (:phase0/phase world)
        obs   (player/get-observer world)]
    (cond
      (ready-for-phase-1? world)
      {:type :success
       :worlds (habitable-worlds world)
       :time (:phase0/sim-time world)
       :message "A world capable of harboring life has formed."}

      (and obs (not (player/can-interact? obs)))
      {:type :fadeout
       :message "You dissolve back into the quantum foam."}

      (= phase :phase-0/dispersed)
      {:type :dispersal
       :message "The nebula disperses. No stars form here."}

      (and (= phase :phase-0/planets-formed) (empty? (habitable-worlds world)))
      {:type :sterile
       :message "Beautiful, but sterile. Life will not arise here."}

      :else nil)))
