;; Read one published world. No world/config/physics mutation.
(let [service @infra.dev.window/service-state
      world @(:world service)
      cfg @(:config service)
      camera @(:camera service)
      observer (domain.player/observer-entity world)
      getc (fn [eid component] (domain.ecs.core/get-component world eid component))
      now (:genesis/sim-time world)
      trails (get-in world [:components domain.ecs.components/motion-trail])
      ctx (infra.render.units/make-context camera {:width 1280 :height 720})
      projection (infra.render.scene.trails/project-trails ctx world)
      monitor (some-> (ns-resolve 'user 'truth-native-observer) deref :state deref)]
  {:wall-ms (System/currentTimeMillis) :tick (:tick world) :sim-time now
   :dt (:sim/dt world) :arc (:arc/current world)
   :scenario (:demo/scenario world) :fixture? (:demo/fixture? world)
   :observer (domain.player/get-observer world)
   :observer-eid observer :position (getc observer domain.ecs.components/position)
   :velocity (getc observer domain.ecs.components/velocity)
   :thrust (:player/thrust world)
   :effective-displacement (or (:genesis/spark-flight-displacement world)
                               domain.player/default-displacement-per-tick)
   :effective-retention (or (:genesis/spark-damping-retention world)
                            domain.player/default-damping-retention)
   :config (select-keys cfg [:mode :selection :follow-eid :ui/active-domain
                            :ui/cursor-free? :focus-offset])
   :camera (select-keys camera [:target :position :distance :yaw :pitch])
   :states (frequencies (vals (get-in world [:components domain.ecs.components/matter-state])))
   :menu (select-keys (infra.menu/menu-hud cfg world 1280 720) [:text :hits])
   :trail-coverage (:summary projection)
   :trails (mapv (fn [[eid history]]
                   (let [segments (shape.trail/segments history
                                                      {:time now :position (getc eid domain.ecs.components/position)})]
                     {:eid eid :kind (getc eid domain.ecs.components/body-kind)
                      :matter (getc eid domain.ecs.components/matter-state)
                      :samples (count (:samples history))
                      :times (mapv :time (:samples history))
                      :next-at (:next-at history) :skipped (:skipped-deadlines history)
                      :segment-count (count segments)
                      :alpha (mapv :alpha (mapcat identity segments))}))
                 (sort-by key trails))
   :monitor monitor
   :service-error (:error service) :ui-error (:ui/error-state cfg)})
