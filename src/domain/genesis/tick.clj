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
   [domain.ecology :as ecology]
   [domain.voxel.sculpt :as sculpt]))

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
   are built before the fan-out and stripped after the fold. The neighbor cache
   is now a persistent component (`c/neighbor-cache`) rebuilt by the
   `:neighbor-cache` fan-out system each tick; it survives the fold so the next
   tick can reuse valid entries."
  [world]
  (let [systems (systems/physics-systems-parallel world)
        world   (-> world
                    (ecs/with-query-cache)
                    (pcache/build-physics-soa))]
    (-> (tick/run-parallel world systems)
        (ecs/strip-query-cache)
        (pcache/strip-physics-soa))))

;; --- M5 handoff Phase 4: the :event/phase0-handoff ledger append ------------
;; See kanban/tasks/ecology-m5-phase4-handoff-event.md and parent
;; kanban/tasks/ecology-water-gate-snowline.md §2, §5. `domain.stellar.
;; classifier/handoff-system` is a genuine write-set fan-out emitter and the
;; SOLE writer of `c/planet-candidate`, but — like `:event/collision` in
;; `domain.physics.collision/collision-detection-system`'s 0-arity fan-out
;; form — a ledger event dispatched from INSIDE a write-set `:run` only
;; mutates a scratch snapshot that gets diffed away at the component-type
;; boundary (`domain.ecs.tick/apply-write-set` only understands component
;; write-sets, not `:ledger`). So, exactly like `emit-promotion-events`
;; above, the real ledger append is a SERIAL step run once per tick after
;; the fold, reacting to what the fan-out already decided.

(defn emit-handoff-event
  "Append a single `:event/phase0-handoff` event to `world`'s ledger the
   first tick its `c/planet-candidate` component is non-empty. Idempotent:
   a no-op once the event is already in the ledger, and a no-op while no
   candidate has yet cleared `handoff-system`'s §2 gate (that gate is the
   single source of truth this step reacts to — it is not re-checked here)."
  [world]
  (let [candidates (get-in world [:components c/planet-candidate] {})]
    (if (or (empty? candidates)
            (seq (event/events-of-kind world :event/phase0-handoff)))
      world
      (emit-threshold world :event/phase0-handoff
                      {:candidates (vec (vals candidates))}))))

;; --- Narrowing B: the :event/world-commitment ledger append ------------------
;; Same serial-emit precedent as emit-handoff-event above: the `:commitment`
;; fan-out system (domain.narrowing/commitment-system) is the SOLE writer of
;; `c/commitment-state`, but a ledger dispatch from inside a write-set `:run`
;; is diffed away at the component-type boundary — so the canonical threshold
;; event (docs/designs/commitment-and-resonance.md §4.2) is appended here,
;; SERIALLY after the fold, reacting to what the fan-out decided.

(defn- commitment-reason
  "The §4.2 `:reason` for the captured world: `:living` when its ecology is in
   a living phase, `:habitable` when it carries the M5 planet-candidate record
   (the stabilized-candidate contract), else `:chosen`."
  [world eid]
  (let [eco (ecs/get-component world eid c/ecology)]
    (cond
      (and eco (ecology/living? eco))                 :living
      (ecs/get-component world eid c/planet-candidate) :habitable
      :else                                           :chosen)))

(defn emit-commitment-event
  "Append the canonical `:event/world-commitment` event
   (commitment-and-resonance.md §4.2) the first tick a world carries
   `c/commitment-state :committed`. Idempotent: a no-op once the event is on
   the ledger, and a no-op before capture. The payload is
   {:world eid :arc (:arc/current world) :reason ...} — under the :data key,
   the same emit-threshold shape as :event/phase0-handoff."
  [world]
  (let [committed (some (fn [[eid state]] (when (= :committed state) eid))
                        (get-in world [:components c/commitment-state] {}))]
    (if (or (nil? committed)
            (seq (event/events-of-kind world :event/world-commitment)))
      world
      (emit-threshold world :event/world-commitment
                      {:world  committed
                       :arc    (:arc/current world)
                       :reason (commitment-reason world committed)}))))

(defn- tick-physics
  "Run one step of physics + lifecycle on the already tick-advanced world."
  [world]
  (-> (step-physics world)
      (intervention/expire-interventions)
      (sculpt/clear-sculpt-ops)
      bootstrap/materialize-lifecycle
      (emit-promotion-events world)
      emit-handoff-event
      emit-commitment-event))

(defn- advance-simulation-clock
  "Compute stats, complexity, pacing, and advance sim-time for the post-physics
   world. `world1` is the pre-physics snapshot used for phase-event context."
  [world2 world1]
  (let [dt         (:sim/dt world1)
        summ       (summary/system-summary world2)
        complexity (classifier/complexity-score summ)
        stats      (summary/stats-of world2 summ)
        obs        (player/get-observer world2)
        slipping?  (when obs (player/time-slip-threshold? obs complexity))
        pacing     (when-not (false? (:genesis/adaptive-pacing? world2))
                     (-> (pacing/pace world2 complexity)
                         (pacing/with-time-slip (when slipping? (:coherence obs)))))
        world3     (cond-> world2
                     (and (:star? summ) (zero? (:genesis/star-ignition-time world2)))
                     (assoc :genesis/star-ignition-time (:genesis/sim-time world2))

                     :always
                     (ecology/emit-phase-events world1))]
    (cond-> (assoc world3
                   :genesis/complexity complexity
                   :genesis/stats      stats
                   :genesis/formation-progress (summary/formation-progress world2 summ)
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
