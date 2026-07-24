(ns domain.player.flight
  "Manual-flight thrust for the spark (card flight-no-jump-accel; design
   docs/designs/spark-flight-and-camera.md §3.1, Wave 0 scope): WASD +
   vertical input becomes an ACCELERATION channel on the spark entity,
   summed by the integrator — never a position/velocity write. Replaces
   the deleted `domain.player.focus/drift` per-frame position teleport,
   whose direct `c/position` write raced the integrator (two writers, one
   component per frame = the visible WASD jump).

   THE PATH (the `:genesis/interventions` precedent):
   - infra (the window loop, manual camera mode only) enqueues
     `set-thrust` intents through the IntentAtom; the sim thread applies
     them serially pre-tick. The intent writes the `:player/thrust` world
     key: a unit direction vec3 in world axes (camera-aim basis until
     Wave 1 orientation lands), or absent when no flight key is held.
   - `thrust-acceleration-system` is a write-set fan-out system, the SOLE
     writer of `c/accel-thrust`, emitting on the observer entity only:
       a = dir · (thrust-dv / dt)  −  v · (1 − retention) / dt
     The integrator sums it with every other accel.* channel and stays
     the sole writer of c/position/c/velocity.

   UNITS (the physics-dt-unit-mismatch lesson): `:sim/dt` dilates with
   the bulk-collapse dynamical time and the integrator advances x by
   v·dt, so the thrust term is sized by DISPLACEMENT per tick, not Δv:
   a held key drives terminal v·dt → `default-displacement-per-tick` at
   ANY dilation, and coast-down is a fixed retained FRACTION per tick.
   (A fixed Δv per tick would make displacement proportional to dt —
   dilation-dependent, the exact bug class this comment exists to
   prevent.) The sim thread paces at ~60 ticks/s
   (infra.dev.window.loop/sim-loop), so per-tick feel is per-frame feel.
   Live-tune via the `:genesis/` knobs below in the pm2 window; Wave 1
   (spark-flight-force-channels) re-expresses this as body-frame thrust
   with real units once orientation exists."
  (:require
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.tick :as tick]
   [domain.player.state :as state]
   [shape.spatial :as sp]))

(def ^:private zero3 [0.0 0.0 0.0])

(def default-displacement-per-tick
  "Target spark displacement (m) PER TICK at full thrust, terminal speed —
   the quantity that must stay constant under `:sim/dt` dilation
   (physics-dt-unit-mismatch: the integrator advances x by v·dt, so a fixed
   Δv per tick makes displacement PROPORTIONAL to dt — the 2026-07-23 live
   fling: 6e13 m/s Δv at dt=4.1e9 s moved the spark 8e24 m in one tick).
   3.0e14 m/tick crosses the late-sim world (~1e17 m) in ~300 ticks ≈ 5 s
   wall at the ~60 Hz sim pace — the old drift's feel (3.0e15 m/s × 16 ms
   frames ≈ 5e13 m/frame) scaled up for a world that has since inflated.
   The thrust term derives Δv = D·(1−retention)/dt so terminal v·dt = D at
   ANY dilated dt. Live knob: `:genesis/spark-flight-displacement`."
  3.0e14)

(def default-damping-retention
  "Fraction of the spark's velocity RETAINED per tick by the proto
   flight-assist (always on in Wave 0 — the FA toggle is Wave 1 card 3).
   0.97: a released mote coasts to ~5% in ln(0.05)/ln(0.97) ≈ 98 ticks
   ≈ 1.6 s at the sim's ~60 Hz pace. Expressed through the accel channel
   as a_damp = −v · (1 − retention) / dt so the integrator's a·dt applies
   exactly the fractional decay at any dilated dt. Live knob:
   `:genesis/spark-damping-retention`."
  0.97)

(defn set-thrust
  "Serial pre-tick intent (the `domain.intervention/place` precedent):
   record the player's manual-flight thrust direction — a unit vec3 in
   world axes — on the `:player/thrust` world key, or clear it when
   `dir` is nil (no flight key held / leaving manual mode). Pure:
   world → world'."
  [world dir]
  (if dir
    (assoc world :player/thrust (mapv double dir))
    (dissoc world :player/thrust)))

(defn thrust-acceleration-system
  "Write-set system (sole writer of `c/accel-thrust`): the spark's
   manual-flight thrust plus the always-on proto flight-assist damping,
   one acceleration channel on the observer entity only. Reads the
   `:player/thrust` world key (input direction, set by intent) and the
   spark's `c/velocity` snapshot (damping); both terms are divided by
   `:sim/dt` so one integrator step lands exactly `thrust-dv` of Δv and
   exactly `(1 − retention)` of fractional decay per tick at any time
   dilation. Auto-clears when there is no observer (the
   contribution-write-set precedent)."
  []
  {:id     :player-thrust
   :writes #{c/accel-thrust}
   :run
   (fn [world]
     (if-let [eid (state/observer-entity world)]
       (let [dt        (max 1.0 (double (or (:sim/dt world) 1.0e12)))
             disp      (double (or (:genesis/spark-flight-displacement world)
                                   default-displacement-per-tick))
             retention (double (or (:genesis/spark-damping-retention world)
                                   default-damping-retention))
             v         (or (ecs/get-component world eid c/velocity) zero3)
             ;; Δv per tick = D·(1−retention)/dt ⇒ terminal v·dt = D at any dt
             a-thrust  (if-let [dir (:player/thrust world)]
                         (sp/v* dir (/ (* disp (- 1.0 retention)) (* dt dt)))
                         zero3)
             a-damp    (sp/v* v (- (/ (- 1.0 retention) dt)))
             cell      {eid (sp/v+ a-thrust a-damp)}]
         (tick/contribution-write-set
          c/accel-thrust cell
          (keys (get-in world [:components c/accel-thrust]))))
       {c/accel-thrust {}}))})
