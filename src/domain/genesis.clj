(ns domain.genesis
  "Phase 0: Stellar Nebula — composition layer over the ECS substrate.

   This is NOT a separate engine. It bootstraps a normal ECS world, wires the
   stellar/thermal/fusion/collision/observer systems into a tick pipeline, seeds
   a nebula of entities and the player's observer spark, and drives the world
   forward while emitting threshold events into the shared ledger.

   Everything here is pure data transformation; rendering and IO live in infra."
  (:require
   [domain.stellar          :as stellar]
   [domain.debris           :as debris]
   [domain.em               :as em]
   [domain.ecology          :as ecology]
   [domain.hydro            :as hydro]
   [domain.regime           :as regime]
   [domain.chemistry        :as chemistry]
   [domain.atmosphere       :as atmosphere]
   [domain.lod              :as lod]
   [domain.player           :as player]
   [domain.intervention     :as intervention]
   [domain.pacing           :as pacing]
   [law.stellar             :as law]
   [law.composition         :as lcomp]
   [law.registry            :as lreg]
   [domain.ecs.core         :as ecs]
   [domain.ecs.event        :as event]
   [domain.ecs.parallel     :as par]
   [domain.ecs.tick         :as tick]
   [domain.ecs.components    :as c]
   [domain.orbital.system   :as orbital]
   [domain.integrator       :as integ]
   [domain.physics.collision :as collision]
   [domain.physics.cache    :as pcache]
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
  ([world total-mass extent {:keys [gas-count n-seeds seed-r spin turb seed]
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

(defn- stats-aux
  "Single-pass accumulator for stats-of. Returns [m mt peak stars bins disk-mass resv-mass].
   The per-entity component reads fan out in parallel (order-preserving), then
   the fold walks the projections in eid order — the same floating-point
   accumulation order as the serial walk it replaces."
  [world eids]
  (let [cells (par/par-mapv
               (fn [eid]
                 [eid
                  (double (or (ecs/get-component world eid c/mass) 0.0))
                  (double (or (ecs/get-component world eid c/temperature) 0.0))
                  (ecs/get-component world eid c/matter-state)
                  (double (or (ecs/get-component world eid c/disk-mass) 0.0))
                  (double (or (ecs/get-component world eid c/wind-reservoir) 0.0))])
               eids)]
    (reduce (fn [[m mt peak stars bins disk-mass resv-mass] [eid mass t st disk resv]]
              [(+ m mass)
               (+ mt (* mass t))
               (max peak t)
               (if (= :star st) (conj stars eid) stars)
               (if (= :star st)
                 (let [m-msun (/ mass 1.989e30)]
                   (cond
                     (< m-msun 0.1)  (update bins 0 inc)
                     (< m-msun 0.5)  (update bins 1 inc)
                     (< m-msun 1.0)  (update bins 2 inc)
                     (< m-msun 2.0)  (update bins 3 inc)
                     (< m-msun 5.0)  (update bins 4 inc)
                     (< m-msun 10.0) (update bins 5 inc)
                     (< m-msun 50.0) (update bins 6 inc)
                     :else           (update bins 7 inc)))
                 bins)
               (+ disk-mass disk)
               (+ resv-mass resv)])
            [0.0 0.0 0.0 [] (vec (repeat 8 0)) 0.0 0.0]
            cells)))

(defn stats-of
  "Observable readouts for the HUD, tallied once per tick from the post-physics
   world and a precomputed `summ`: total mass (kg and solar masses),
   mass-weighted mean temperature, peak temperature, and the body/resolved/
   star/planet counts. Pure; cached on the world so the renderer reads it
   cheaply every frame instead of re-walking the entity set at 60 Hz."
  [world summ]
  (let [eids   (ecs/entities-with world c/mass)
        [m mt peak stars bins disk-mass resv-mass] (stats-aux world eids)
        lod-freq (frequencies (vals (get-in world [:components c/lod-level] {})))
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
     :xuv-escape-count  (count (get-in world [:components c/atmosphere-escape] {}))
     :sed-band-count    (count (get-in world [:components c/sed-bands] {}))
     :lod-local         (get lod-freq :local 0)
     :lod-system        (get lod-freq :system 0)
     :lod-galaxy        (get lod-freq :galaxy 0)
     :imf-bins          bins
     :disk-count        (reduce-kv (fn [n _eid dm]
                                     (if (pos? (double (or dm 0.0))) (inc n) n))
                                   0
                                   (get-in world [:components c/disk-mass] {}))}))

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
                            :genesis/sim-time          0.0
                            :genesis/time-scale        (:rate neb)
                            :genesis/rate-yr           (:rate-yr neb)
                            :genesis/stats             nil
                            :genesis/complexity        0
                            :genesis/active            true
                            :genesis/disk-maturity     3.156e13
                            :genesis/star-ignition-time 0.0
                            ;; Adaptive pacing dilates :sim/dt with complexity at
                            ;; a fixed 60 Hz tick rate (see `pacing-for`). Set
                            ;; false to hold :sim/dt constant — useful for fast,
                            ;; pace-independent tests and deterministic runs.
                            :genesis/adaptive-pacing?  true
                            :genesis/wind-rate-scale   wind-rate-scale
                            :genesis/collapse-fraction collapse-fraction
                            :genesis/contraction-time  contraction-time
                            :genesis/gas-particle-mass pmass
                            :genesis/feeding-zone-factor
                            (stellar/resolution-feeding-zone-factor gas-count)))
         seeded (seed-nebula base nebula-mass nebula-radius
                             {:gas-count gas-count :spin spin :turb turb})
         ;; Store the gas smoothing radius so the classifier can compute
         ;; accretion radii from it (before KH contraction shrinks bodies).
         seeded (assoc seeded :genesis/gas-smoothing-radius (* nebula-radius 0.003))
         ;; Spawn the observer at the nebula's centre (origin) so the player
         ;; starts inside the cloud, not outside/below it.
         [w _]  (player/spawn-observer seeded (sp/vec3 0 0 0))]
     (assert-seed-contracts! w))))

;; --- Observable summary -----------------------------------------------------

(defn system-summary
  "Tally the world's resolved matter into the shape used for complexity, phase
   detection, and habitability. Single-pass over entities with matter-state+mass."
  [world]
  (let [eids    (ecs/entities-with world c/matter-state c/mass)
        ;; The projection (9 component reads per entity) fans out in parallel;
        ;; par-mapv preserves eid order so the tallied vectors are identical to
        ;; the serial walk's.
        regions (par/par-mapv #(stellar/entity->region world %) eids)]
    (loop [stars    []
           planets  []
           resolved []
           i        0]
      (if (= i (count regions))
        {:body-count     (count regions)
         :resolved-count (count resolved)
         :star?          (boolean (seq stars))
         :fusion?        (boolean (seq stars))
         :planet-count   (count planets)
         :stars          stars
         :planets        planets
         :regions        regions}
        (let [r  (nth regions i)
              st (:matter-state r)]
          (recur (if (= :star st) (conj stars r) stars)
                 (if (= :planet st) (conj planets r) planets)
                 (if (= :nebula st) resolved (conj resolved r))
                 (inc i)))))))

(defn- cached-system-summary
  "Return a system summary, caching it on the world under :genesis/_summary-cache
   so repeated reads in the same tick are O(1). The cache is invalidated by
   `tick-world` advancing the tick."
  [world]
  (if-let [cached (get-in world [:genesis/_summary-cache (:tick world)])]
    cached
    (let [s (system-summary world)]
      (assoc-in world [:genesis/_summary-cache (:tick world)] s))))

;; Arc detection (`detect-arc`, formerly `detect-phase`) now lives in
;; `domain.arc`: the current arc is the player's STORY state, interpreted from
;; the physical summary, not a property of the physics loop. The genesis tick
;; below emits physical threshold events; `domain.arc/advance-arc` reads them.

;; --- Tick driver ------------------------------------------------------------

(defn emit-threshold
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

;; Ongoing physics that is not specific to formation moved to its proper owner:
;;   xuv-atmospheric-escape-system → domain.atmosphere
;;   lod-scheduler                 → domain.lod
;;   magnetosphere-coupling-system → domain.em
;; The genesis system table below references them from their new namespaces.

(defn physics-systems-parallel
  "The transform systems as NATIVE write-set systems for the double-buffer
   fan-out (`domain.ecs.tick/run-parallel`). Every entry is
   `{:id kw :writes #{ctype ...} :run (fn [frozen] write-set)}` — each emits
   only the component types it exclusively owns, sourced from its
   registry-declared `:writes` (spec Fix 3: zero `tick/legacy-system` wraps, so
   no per-system world copy or diff).

   EXCLUDES `recenter`, which is not a system at all any more: the integrator
   subtracts the one-tick-stale COM frame-offset (a world scalar set in
   tick-world) from every new position (spec §6)."
  [{:keys [sim/G sim/theta sim/dt sim/softening]}]
  [;; force emitters + integrator
   (orbital/gravity-acceleration G theta (or softening 1e14))
   (hydro/pressure-acceleration)
   (em/lorentz-acceleration-system dt)
   (intervention/warp-acceleration-system)
   (player/observer-acceleration-system)
   (intervention/thermal-intervention-system)
   (integ/integrator-system dt)
   ;; transform systems
   (stellar/structure-system)
   (stellar/eos-system)
   (stellar/classifier-system)
   (em/field-system dt)
   (stellar/fusion-system)
   (stellar/stellar-sed-system)
   (stellar/atmosphere-shells-system)
   (chemistry/nucleosynthesis-system dt)
   (stellar/deuterium-depletion-system)
   (stellar/stellar-wind-system)
   (stellar/stellar-flare-system)
   (atmosphere/xuv-atmospheric-escape-system)
   (stellar/disc-identification-system)
   (regime/regime-system)
   ;; Collision detection: a fan-out emitter (B3). Its handler emits
   ;; c/absorb-merge, c/consumed-merge, c/spawn-request-shatter — all
   ;; single-writer. Runs in parallel, not serially at the barrier.
   (collision/collision-detection-system)
   ;; Former serial barriers — now fan-out emitters (Part C):
   ;; Fusion promotion: emits c/promotion-signal (single-writer).
   (stellar/fusion-promotion-system)
   ;; Sink formation: emits c/absorb-accrete, c/consumed-accrete (single-writer).
   (stellar/sink-formation-system)
   ;; Disk evolution: emits c/disk-mass, c/disk-angular-mom, c/mass-flux-disk,
   ;; c/torque-disk, c/spawn-request-disk (all single-writer).
   (stellar/disk-evolution-system)
   ;; LOD scheduler: assigns c/lod-level (single-writer, was cargo-cult barrier).
   (lod/lod-scheduler)
   ;; Magnetosphere coupling: computes c/magnetosphere (single-writer, was
   ;; cargo-cult barrier).
   (em/magnetosphere-coupling-system)
   ;; Toy biosphere: habitable planets adopt + tick an ecology (single-writer
   ;; of c/ecology; throttled internally to its own slower cadence). Phase
   ;; events are emitted post-physics by ecology/emit-phase-events.
   (ecology/ecology-system)
   ;; Debris sink: unbound debris past the system edge is marked consumed
   ;; (single-writer of c/consumed-escape) and reaped at world-construction.
   ;; Without it late-game N grows without bound (spec Fix 6).
   (debris/debris-reaper-system)])

(def ^:private consumed-markers
  "Lifecycle reap markers; an entity carrying ANY is despawned at world-construction."
  [c/consumed-merge c/consumed-accrete c/consumed-wind c/consumed-escape])

(def ^:private spawn-request-components
  "Lifecycle spawn requests; each is {eid [seed-spec ...]} materialized into new
   entities at world-construction."
  [c/spawn-request-wind c/spawn-request-flare
   c/spawn-request-accretion c/spawn-request-shatter
   c/spawn-request-disk c/spawn-request-planet])

(defn materialize-lifecycle
  "World-construction step (spec §5): spawn the entities requested by the fan-out
   lifecycle emitters (spawn-request.*), then reap every entity marked consumed.*.
   This is NOT a contended-state write — continuous state (mass/momentum/blend)
   flows through the integrator as influences; only entity creation/removal
   happens here, which is automatically Jacobi-consistent (a body created this
   tick was invisible to every this-tick reader; a body removed was fully present
   in the snapshot). Pure: world → world'."
  [world]
  (let [;; Spawn specs were computed by emitters reading the SNAPSHOT frame,
        ;; but the integrator shifted every integrated body by −frame-offset
        ;; this tick. Apply the same shift to spawn positions so new entities
        ;; land in the same frame as their parents (an inner planet's orbit is
        ;; smaller than the offset, so skipping this misplaces it entirely).
        foff (or (:genesis/frame-offset world) [0.0 0.0 0.0])
        ;; spawn requested entities, clearing each request as it is consumed
        spawned
        (reduce
         (fn [w req-ct]
           (reduce-kv
            (fn [w eid specs]
              (let [w (ecs/remove-component w eid req-ct)]
                (reduce (fn [w spec]
                          (let [spec  (update spec :position sp/v- foff)
                                extra (:extra-components spec)
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
                       consumed-markers)
        ;; Escapers leave the books through a ledger event (mass-honest reap):
        ;; one aggregated :event/body-escape per tick.
        escapers (keys (get-in spawned [:components c/consumed-escape] {}))
        spawned  (if (seq escapers)
                   (emit-threshold spawned :event/body-escape
                                   {:count (count escapers)
                                    :mass  (reduce + 0.0
                                                   (map #(double (or (ecs/get-component spawned % c/mass) 0.0))
                                                        escapers))})
                   spawned)]
    (reduce ecs/despawn spawned consumed)))

(defn step-physics
  "Run one tick of physics over `world` (already tick-advanced).

   ONE fan-out, no phases: every system — the integrator included — reads the
   same frozen snapshot and emits a write-set for the components it owns
   (docs/specs/perf-60fps-parallel-tick.md). The integrator therefore sums the
   accel/influence channels emitted LAST tick: forces, like every other
   channel, propagate with one tick of Jacobi lag. There is deliberately no
   post-fold phase and no second simulation path.

   Transient snapshot caches — `:ecs/_query-cache` and `:genesis/physics-soa` —
   are built before the fan-out and stripped after the fold. The
   `:genesis/neighbor-cache` is now persistent across ticks: it is rebuilt from
   the previous tick's cache in `step-physics` and survives the fold so the next
   tick can reuse valid entries."
  [world]
  (let [systems (physics-systems-parallel world)
        ;; The neighbor-cache rebuild and the SoA build both read only the
        ;; frozen input world (spatial tree + components), so they run
        ;; concurrently — the rebuild is the most expensive pre-fan-out step
        ;; and previously serialized in front of the SoA build.
        nb-fut  (future
                  (:genesis/neighbor-cache
                   (pcache/rebuild-neighbor-cache
                    world
                    (when-not (:genesis/invalidate-neighbor-cache? world)
                      (:genesis/neighbor-cache world))
                    (:tick world))))
        world   (-> world
                    (ecs/with-query-cache)
                    (pcache/build-physics-soa)
                    (assoc :genesis/neighbor-cache @nb-fut))]
    (-> (tick/run-parallel world systems)
        (ecs/strip-query-cache)
        (pcache/strip-physics-soa))))

(defn tick-world
  "Advance the world by one tick. Pure: world -> world'."
  [world]
  (if-not (:genesis/active world)
    world
    (let [dt         (:sim/dt world)
          effective-dt dt
          prev       (or (:genesis/_prev-summary world) (system-summary world))
        ;; advance logical tick first so every event this step shares its tick;
        ;; arm the integrator with the recenter frame-offset — the COM of THIS
        ;; snapshot, subtracted from every new position so the formation stays in
        ;; its COM frame (spec §6: a one-tick-stale, pure-Galilean shift, replacing
        ;; the old post-fold recenter-system). A world scalar, single-owner.
          world1     (-> (ecs/advance-tick world)
                         (assoc :genesis/frame-offset (center-of-mass world))
                         spatial/spatial-index)
          world2     (-> (step-physics world1)
                         (intervention/expire-interventions)
                         materialize-lifecycle)
          summ       (system-summary world2)
          complexity (stellar/complexity-score summ)
          stats      (stats-of world2 summ)
        ;; Fixed tick rate, dilating timestep: the per-tick step is bounded by
        ;; the BULK cloud's dynamical time (for gravitational stability) AND by
        ;; the system's observable complexity. As the cloud contracts `t_dyn`
        ;; shrinks; as stars/planets form `complexity` rises; both slow the clock
        ;; so the articulated phases (ignition, accretion, planet formation) play
        ;; out longer. The tick count never changes — 60 Hz throughout. A single
        ;; hot protostar does not change the bulk scale, so it can never freeze
        ;; the outer cloud — the failure of the old temperature-driven dt.
        ;; Disabled (`:genesis/adaptive-pacing? false`) → :sim/dt is held constant.
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
          pacing         (when-not (false? (:genesis/adaptive-pacing? world))
                           (-> (pacing/pace world2 complexity)
                               (pacing/with-time-slip (boolean slipping?))))
        ;; Emit PHYSICAL threshold events only. The arc-transition event
        ;; (:event/phase-transition) is emitted by `domain.arc/advance-arc` when
        ;; the story arc advances — the genesis loop stays arc-agnostic.
          world3     (-> (cond-> world2
                           (and (:star? summ) (not (:star? prev)))
                           (-> (emit-threshold :event/stellar-ignition (first (:stars summ)))
                               (assoc :genesis/star-ignition-time (:genesis/sim-time world2)))

                           (> (:planet-count summ) (:planet-count prev))
                           (emit-threshold :event/planet-formation (first (:planets summ))))
                          ;; Biosphere phase transitions (life emergence,
                          ;; ecology advances, extinctions) — diffed against the
                          ;; pre-physics snapshot.
                         (ecology/emit-phase-events world1))
        ;; `dt` here is the step this tick actually integrated (captured above);
        ;; advance the clock by it. When adaptive, arm the NEXT tick with the
        ;; complexity-refined dt/softening and report the derived wall-clock rate
        ;; for the player's clock; otherwise leave the fixed step in place.
          world4     (cond-> (assoc world3
                                    :genesis/complexity complexity
                                    :genesis/stats      stats
                                    :genesis/sim-time   (+ (:genesis/sim-time world3) dt)
                                    :genesis/_prev-summary summ)
                       pacing (assoc :genesis/time-scale    (:rate pacing)
                                     :genesis/rate-yr       (:rate-yr pacing)
                                     :genesis/time-slipping? (boolean (:time-slipping? pacing))
                                     :sim/dt               (:dt pacing)
                                     :sim/softening        (:softening pacing)))]
       ;; Observer update and :genesis/active live in `domain.arc/tick-genesis`
       ;; so they run AFTER arc events (nebula-collapse, protostar-formation,
       ;; phase-transition) are emitted. The physics loop stays arc-agnostic.
      world4)))

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
            (:tick world) (name (or (:arc/current world) :genesis/ticking))
            (:body-count summ) (:resolved-count summ)
            (str (:star? summ)) (:planet-count summ)
            (double (reduce min 1.0e30 temps)) (double (reduce max 0.0 temps))
            (double (reduce max 0.0 bmags))
            (pr-str regimes))))

;; Player input dispatch (`handle-input`) moved to `infra.input`.
;; Habitability scoring (`habitability-of`, `habitable-worlds`) moved to
;; `domain.habitability`. The handoff predicate (`ready-to-narrow?`) and the
;; terminal-outcome descriptor (`genesis-ending`) live in `domain.arc`.
