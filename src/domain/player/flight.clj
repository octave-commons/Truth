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
       a = dir · D · (1 − retention) / dt² − v · (1 − retention) / dt
     The integrator sums it with every other accel.* channel and stays
     the sole writer of c/position/c/velocity.

   UNITS (the physics-dt-unit-mismatch lesson): `:sim/dt` dilates with
   the bulk-collapse dynamical time and the integrator advances x by
   v·dt, so the thrust term is sized by DISPLACEMENT per tick, not Δv:
   at constant dt a held key drives terminal v·dt → the selected D.
   The Jacobi fold consumes the prior acceleration channel, so coasting
   is a delayed second-order response, not exact per-tick retention.
   (A fixed Δv per tick would make displacement proportional to dt —
   dilation-dependent, the exact bug class this comment exists to
   prevent.) Changing dt also changes the prior-channel kick. The sim
   thread targets 60 ticks/s but does not guarantee that wall-clock pace.
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
  "Cruise displacement (m/tick) at full thrust and constant-dt equilibrium.

   The quantity is normalized for `:sim/dt` dilation
   (physics-dt-unit-mismatch: the integrator advances x by v·dt, so a fixed
   Δv per tick makes displacement PROPORTIONAL to dt — the 2026-07-23 live
   fling: 6e13 m/s Δv at dt=4.1e9 s moved the spark 8e24 m in one tick).
   3.0e14 m/tick crosses a ~1e17 m world in ~333 settled-thrust ticks;
   wall time depends on actual simulation throughput. At fixed dt, the
   delayed thrust/damping channels have equilibrium v·dt = D. Unequal
   timesteps do not preserve that trajectory because the integrator consumes
   the prior channel. Live knob: `:genesis/spark-flight-displacement`."
  3.0e14)

(def default-damping-retention
  "Retention parameter for the always-on Wave 0 flight-assist damping.

   The emitted brake is −v·(1−r)/dt. With the one-tick channel delay,
   force-free constant-dt motion follows w[n+1]=w[n]−(1−r)w[n−1], where
   w=v·dt. At r=.97 its slow decay root is ~.96904, not exactly r.
   Release from settled thrust coasts rD/(1−r). Live knob:
   `:genesis/spark-damping-retention`; the FA toggle remains Wave 1."
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
  "Emit manual thrust and proto-assist through the sole `c/accel-thrust` channel.

   Read the intent's world-axis direction, selected displacement D and current
   velocity. Emit dir·D·(1−r)/dt² − v·(1−r)/dt on the observer. The integrator
   consumes this on the following tick, whose dt may differ; only the fixed-dt
   equilibrium guarantees v·dt=D. Auto-clear when there is no observer."
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
             ;; With constant dt the delayed channel has equilibrium v·dt = D.
             a-thrust  (if-let [dir (:player/thrust world)]
                         (sp/v* dir (/ (* disp (- 1.0 retention)) (* dt dt)))
                         zero3)
             a-damp    (sp/v* v (- (/ (- 1.0 retention) dt)))
             cell      {eid (sp/v+ a-thrust a-damp)}]
         (tick/contribution-write-set
          c/accel-thrust cell
          (keys (get-in world [:components c/accel-thrust]))))
       {c/accel-thrust {}}))})
