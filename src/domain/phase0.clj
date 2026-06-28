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
   [domain.pacing           :as pacing]
   [law.stellar             :as law]
   [domain.ecs.core         :as ecs]
   [domain.ecs.event        :as event]
   [domain.ecs.registry     :as reg]
   [domain.ecs.tick         :as tick]
   [domain.ecs.components    :as c]
   [domain.orbital.system   :as orbital]
   [domain.physics.collision :as collision]
   [shape.spatial           :as sp]))

;; --- Nebula seeding ---------------------------------------------------------

(defn- gas-particle-spec
  "One equal-mass, self-gravitating gas particle of a cold, rotating, turbulent
   cloud. Nothing is pre-formed: every particle starts as diffuse gas. Solid-body
   rotation (sub-virial, so the cloud collapses) plus turbulence and a bias toward
   `seeds` (overdensity centres) give the cloud the structure it needs to
   fragment and accrete into clumps, planets, and a star-forming core."
  [^java.util.Random rng extent pmass prad v-vir omega seeds n-seeds seed-r turb]
  (let [to-seed? (and (seq seeds) (< (.nextDouble rng) 0.55))
        [px py pz]
        (if to-seed?
          (let [[cx cy cz] (nth seeds (.nextInt rng n-seeds))
                g (fn [s] (* s extent seed-r (.nextGaussian rng)))]
            [(+ cx (g 1.0)) (+ cy (g 1.0)) (+ cz (g 0.6))])
          (let [r  (* extent (Math/pow (.nextDouble rng) 0.6)) ; centrally concentrated
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
     :composition {:H 0.74 :He 0.24 :metals 0.02}}))

(defn seed-nebula
  "Seed a cold, rotating, turbulent, self-gravitating gas cloud on the single ECS
   world — `gas-count` equal-mass particles, no pre-placed core or planets. Stars,
   planets, and debris must EMERGE by gravitational collapse and accretion. A few
   Gaussian overdensity seeds give the cloud something to fragment around.
   Deterministic (seeded RNG) so runs and tests reproduce."
  ([world total-mass extent] (seed-nebula world total-mass extent {}))
  (   [world total-mass extent {:keys [gas-count n-seeds seed-r spin turb seed]
                             :or   {gas-count 1000 n-seeds 5 seed-r 0.12
                                    spin 0.55 turb 0.08 seed 42}}]
   (let [rng    (java.util.Random. (long seed))
         pmass  (/ (double total-mass) gas-count)
         ;; Render/visual radius for diffuse gas puffs; collision radius is kept
         ;; small so the cloud is transparent and many particles fit in the volume.
         prad   (* extent 0.004)
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
        resolved-mass (reduce (fn [acc r]
                                (if (= :nebula (:matter-state r))
                                  acc
                                  (+ acc (double (or (:mass r) 0.0)))))
                              0.0 (:regions summ))]
    {:total-mass-kg     m
     :total-mass-msun   (/ m solar-mass)
     :resolved-fraction (if (pos? m) (/ resolved-mass m) 0.0)
     :avg-temp          (if (pos? m) (/ mt m) 0.0)
     :peak-temp         peak
     :body-count        (:body-count summ)
     :resolved-count    (:resolved-count summ)
     :star-count        (count (:stars summ))
     :planet-count      (:planet-count summ)}))

;; --- World construction -----------------------------------------------------

(defn create-world
  "Bootstrap a Phase 0 world ready to tick."
  ([] (create-world {}))
   ([{:keys [G theta dt softening nebula-mass nebula-radius collapse-fraction
             contraction-time gas-count spin turb]
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
      :or   {G law/G theta 0.5
             nebula-mass 4e30 nebula-radius 1.5e16 collapse-fraction 0.5
             contraction-time 9.5e14 gas-count 1000 spin 0.6 turb 0.15}}]
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
                            ;; The authentic double-buffer single-writer path is
                            ;; the live default (design §7c): density-gated
                            ;; condensation + classifier + feeding-zone assembly
                            ;; form a star where the legacy mass-tier pipeline
                            ;; forms nothing at production resolution. Set false
                            ;; to run the legacy sequential Gauss–Seidel pipeline.
                            :phase0/parallel?         true
                            :phase0/collapse-fraction collapse-fraction
                            :phase0/contraction-time  contraction-time
                            :phase0/gas-particle-mass pmass
                            :phase0/feeding-zone-factor
                            (stellar/resolution-feeding-zone-factor gas-count)))
         seeded (seed-nebula base nebula-mass nebula-radius
                             {:gas-count gas-count :spin spin :turb turb})
         ;; Store the gas smoothing radius so the classifier can compute
         ;; accretion radii from it (before KH contraction shrinks bodies).
         seeded (assoc seeded :phase0/gas-smoothing-radius (* nebula-radius 0.004))
         [w _]  (player/spawn-observer seeded (sp/vec3 0 0 (* nebula-radius 2)))]
     w)))

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

(defn recenter-system
  "Shift every body so the system's centre of mass sits at the origin — i.e. view
   the formation in its COM frame. Asymmetric ejections give the bound remnant a
   recoil velocity; without this the whole system slowly drifts out of frame. Pure
   Galilean shift, so dynamics are unchanged."
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
        (if (pos? m)
          (let [cx (/ sx m) cy (/ sy m) cz (/ sz m)]
            (reduce (fn [w eid]
                      (let [[x y z] (ecs/get-component world eid c/position)]
                        (ecs/put-component w eid c/position
                                           [(- (double x) cx) (- (double y) cy) (- (double z) cz)])))
                    world eids))
          world))
      world)))

(defn physics-systems
  "The ordered physical systems run each tick (everything except the observer,
    which must run after complexity and events are known).

   The integrator advances by `:sim/dt` — the in-game seconds this tick covers,
   set by `pacing-for` from the system's complexity. It is NOT scaled by the
   display `time-scale`; the wall-clock rate emerges from the fixed 60 Hz tick
   rate (`rate = dt * ticks-per-second`), so dilating dt dilates the clock."
  [{:keys [sim/G sim/theta sim/dt sim/softening]}]
  (let [effective-dt dt]
    ;; `dt` (`:sim/dt`) is the real integration step in seconds — a fraction of
    ;; the cloud's orbital/free-fall time so the N-body dynamics actually resolve
    ;; (collapse, rotation, accretion). It is NOT multiplied by the display
    ;; `time-scale` (that gave ~1e20 s steps, degenerate motion). `softening` is
    ;; the gravitational softening that keeps the self-gravitating cloud stable.
    ;;
    ;; Tick order: compute SPH density from current positions (so pressure can
    ;; vary with crowding) → compute hydrodynamic pressure-gradient accelerations
    ;; → promote Jeans-unstable gas to resolved bodies → reclassify every clump by
    ;; mass → move under gravity+hydro → merge overlapping resolved bodies →
    ;; contract any star-forming core → ignite fusion → heat/cool by radiation →
    ;; tag the dominant-physics regime → evolve the magnetic field. Formation is
    ;; emergent.
    [(hydro/density-system effective-dt)
     (hydro/hydro-system effective-dt)
     stellar/jeans-collapse-system
     stellar/classify-system
     (orbital/orbital-system G theta effective-dt (or softening 1e14))
     collision/collision-detection-system
     stellar/collapse-system
     stellar/fusion-system
     (stellar/thermal-system effective-dt)
     regime/regime-system
     (em/em-system effective-dt)
     recenter-system]))

(defn physics-systems-parallel
  "The transform systems as write-set systems for the double-buffer fan-out
   (`domain.ecs.tick/run-parallel`). Each entry is its legacy `(fn [world] world')`
   wrapped by the bridge and masked to its registry-declared `:writes`.

   EXCLUDES the two systems that do not belong in the parallel region:
     • `collision-detection` — a discrete-event system whose merge handler
       despawns entities; it runs serially at the barrier (spec §6).
     • `recenter` — a global centre-of-mass reduction; runs at the barrier or
       becomes a frame-offset in the motion integrator (spec §5).

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
     (orbital/motion-integration dt)
     ;; legacy-bridged transform systems
     (stellar/structure-system)
     (stellar/eos-system)
     (stellar/classifier-system)
     (stellar/temperature-system dt)
     (em/field-system dt)
     (legacy :fusion         stellar/fusion-system)
     (legacy :regime         regime/regime-system)
     ;; em's braking/spin (masked to angular-momentum/spin; its legacy hydro-accel
     ;; and b-field writes are dropped — Lorentz via em-lorentz, b-field via field)
     (legacy :em             (em/em-system dt))]))

(defn step-physics
  "Run one tick of physics over `world` (already tick-advanced).

   Default (double-buffer): every system in `physics-systems-parallel` runs
   concurrently reading the FROZEN `world` and writing only the components it
   owns; the disjoint write-sets fold at a single barrier (`:throw` enforces
   single-writer at runtime). Then the SERIAL barrier systems run on the folded
   world — collision/merge (discrete events that despawn) and the centre-of-mass
   recenter (a global reduction). System order is irrelevant by construction.

   The double-buffer path is the live default (`create-world` sets
   `:phase0/parallel? true`): density-gated condensation, the single-writer
   classifier, and feeding-zone assembly form a star — the go-live gap (design
   §7c) is closed. Set `:phase0/parallel? false` to run the legacy SEQUENTIAL
   Gauss–Seidel pipeline (mass-tier promotion), kept for comparison and tests."
  [world]
  (if (:phase0/parallel? world true)
    (-> (tick/run-parallel world (physics-systems-parallel world))
        (collision/collision-detection-system)
        (stellar/fusion-promotion-system)
        (stellar/sink-formation-system)
        (recenter-system))
    (ecs/run-systems world (physics-systems world))))

(defn tick-world
  "Advance the world by one tick. Pure: world -> world'."
  [world]
  (if-not (:phase0/active world)
    world
  (let [dt         (:sim/dt world)
        effective-dt dt
        prev       (system-summary world)
        prev-phase (:phase0/phase world)
        ;; advance logical tick first so every event this step shares its tick
        world1     (ecs/advance-tick world)
        world2     (step-physics world1)
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
        pacing         (when-not (false? (:phase0/adaptive-pacing? world))
                         (pacing/pace world2))
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
                     pacing (assoc :phase0/time-scale (:rate pacing)
                                   :phase0/rate-yr    (:rate-yr pacing)
                                   :sim/dt            (:dt pacing)
                                   :sim/softening     (:softening pacing)))
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
