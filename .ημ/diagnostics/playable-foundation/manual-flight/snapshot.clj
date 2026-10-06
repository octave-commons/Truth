;; Read-only verification of real native keyboard input on the live default world.
(let [service @infra.dev.window/service-state
      world @(:world service)
      eid (domain.player/observer-entity world)
      getc #(domain.ecs.core/get-component world eid %)]
  {:wall-ms (System/currentTimeMillis)
   :tick (:tick world)
   :sim-time (:genesis/sim-time world)
   :sim-dt (:sim/dt world)
   :fixture? (:demo/fixture? world)
   :arc (:arc/current world)
   :states (frequencies (vals (get-in world [:components domain.ecs.components/matter-state])))
   :observer-eid eid
   :observer (domain.player/get-observer world)
   :position (getc domain.ecs.components/position)
   :velocity (getc domain.ecs.components/velocity)
   :orientation (getc domain.ecs.components/orientation)
   :angular-velocity (getc domain.ecs.components/angular-velocity)
   :accel-thrust (getc domain.ecs.components/accel-thrust)
   :thrust-intent (:player/thrust world)
   :binding (getc domain.ecs.components/binding)
   :camera @(:camera service)
   :window (select-keys @(:config service)
                        [:mode :selection :follow-eid :ui/active-domain :ui/cursor-free?
                         :focus-offset :look-sensitivity :smoothing])
   :service-error (:error service)
   :ui-error (:ui/error-state @(:config service))})
