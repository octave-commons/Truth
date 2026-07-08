(ns domain.genesis.tick
  "Phase 0 tick orchestration: threshold events, the parallel physics step, and
   the advance-simulation-clock post-processing that drives the simulation time."
  (:require
   [domain.genesis.bootstrap :as bootstrap]
   [domain.genesis.summary :as summary]
   [domain.genesis.systems :as systems]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.event :as event]
   [domain.ecs.tick :as tick]
   [domain.physics.cache :as pcache]
   [domain.spatial.index :as spatial]
   [domain.intervention :as intervention]
   [domain.stellar.classifier :as classifier]
   [domain.player :as player]
   [domain.pacing :as pacing]
   [domain.ecology :as ecology]))

;; --- Threshold events -------------------------------------------------------

(defn emit-threshold
  "Emit a threshold event into the ledger at the world's current tick."
  [world kind data]
  (event/dispatch world
                  (event/->event {:tick     (:tick world)
                                  :kind     kind
                                  :entities #{}
                                  :payload  {:data data}})))

(defn- promotion-event-kind
  "Map a matter-state transition to the agency-paying event kind. Returns nil
   for downward, unchanged, or already-emitted transitions."
  [old-state new-state]
  (case new-state
    :condensed-core (when (= old-state :nebula)        :event/condensed-core-formation)
    :star             (when (= old-state :protostar)     :event/stellar-ignition)
    :protostar        (when (= old-state :nebula)        :event/protostar-formation)
    :brown-dwarf      (when (= old-state :nebula)        :event/brown-dwarf-formation)
    :gas-giant        (when (= old-state :nebula)        :event/gas-giant-formation)
    :planetesimal     (when (= old-state :nebula)        :event/planetesimal-formation)
    :planet           (when (= old-state :nebula)        :event/planet-formation)
    nil))

(defn- emit-promotion-event
  "Emit a single promotion event for `eid` transitioning from `old-state` to
   `new-state`. Newly spawned entities (planets from disk fragmentation, etc.)
   are treated as promotions from `:nebula` because the player witnesses matter
   condensing into a new form, even though the entity did not exist before."
  [world eid old-state new-state]
  (let [old-state' (or old-state :nebula)
        kind       (promotion-event-kind old-state' new-state)]
    (if kind
      (emit-threshold world kind {:eid eid :from old-state' :to new-state})
      world)))

(defn emit-promotion-events
  "Emit per-body matter-state promotion events between `before` (pre-physics
   snapshot) and `after` (post-physics world). Every body that becomes a star,
   protostar, planet, or resolves from nebula pays agency when witnessed."
  [after before]
  (let [before-eids (set (ecs/entities-with before c/matter-state))
        before-states (reduce (fn [m eid]
                                (assoc m eid (ecs/get-component before eid c/matter-state)))
                              {}
                              before-eids)]
    (reduce
     (fn [w eid]
       (let [new-state (ecs/get-component after eid c/matter-state)
             old-state (get before-states eid)]
         (emit-promotion-event w eid old-state new-state)))
     after
     (ecs/entities-with after c/matter-state))))

;; --- Tick driver ------------------------------------------------------------

(defn step-physics
  "Run one tick of physics over `world` (already tick-advanced).

   ONE fan-out, no phases: every system — the integrator included — reads the
   same frozen snapshot and emits a write-set for the components it owns
   (kanban/tasks/perf-60fps-parallel-tick.md). The integrator therefore sums the
   accel/influence channels emitted LAST tick: forces, like every other
   channel, propagate with one tick of Jacobi lag. There is deliberately no
   post-fold phase and no second simulation path.

   Transient snapshot caches — `:ecs/_query-cache` and `:genesis/physics-soa` —
   are built before the fan-out and stripped after the fold. The
   `:genesis/neighbor-cache` is now persistent across ticks: it is rebuilt from
   the previous tick's cache in `step-physics` and survives the fold so the next
   tick can reuse valid entries."
  [world]
  (let [systems (systems/physics-systems-parallel world)
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

(defn- tick-physics
  "Run one step of physics + lifecycle on the already tick-advanced world."
  [world]
  (-> (step-physics world)
      (intervention/expire-interventions)
      bootstrap/materialize-lifecycle
      (emit-promotion-events world)))

(defn- advance-simulation-clock
  "Compute stats, complexity, pacing, and advance sim-time for the post-physics
   world. `world1` is the pre-physics snapshot used for phase-event context."
  [world2 world1]
  (let [dt         (:sim/dt world1)
        summ       (summary/system-summary world2)
        complexity (classifier/complexity-score summ)
        stats      (summary/stats-of world2 summ)
        slipping?  (when-let [obs (player/get-observer world2)]
                     (player/time-slip-threshold? obs complexity))
        pacing     (when-not (false? (:genesis/adaptive-pacing? world2))
                     (-> (pacing/pace world2 complexity)
                         (pacing/with-time-slip (boolean slipping?))))
        world3     (cond-> world2
                     (and (:star? summ) (zero? (:genesis/star-ignition-time world2)))
                     (assoc :genesis/star-ignition-time (:genesis/sim-time world2))

                     :always
                     (ecology/emit-phase-events world1))]
    (cond-> (assoc world3
                   :genesis/complexity complexity
                   :genesis/stats      stats
                   :genesis/sim-time   (+ (:genesis/sim-time world3) dt)
                   :genesis/_prev-summary summ)
      pacing (assoc :genesis/time-scale    (:rate pacing)
                    :genesis/rate-yr       (:rate-yr pacing)
                    :genesis/time-slipping? (boolean (:time-slipping? pacing))
                    :sim/dt               (:dt pacing)
                    :sim/softening        (:softening pacing)))))

(defn tick-world
  "Advance the world by one tick. Pure: world -> world'."
  [world]
  (if-not (:genesis/active world)
    world
    (let [world1 (-> (ecs/advance-tick world)
                     spatial/spatial-index)]
      (advance-simulation-clock (tick-physics world1) world1))))
