;; Read-only native inspection. Dereference one published world. Never applies
;; a write-set, changes input/config/camera, or issues GL calls from nREPL.
(require 'infra.dev.window 'infra.render.units 'infra.render.scene.trails
         'infra.render.shader 'domain.ecs.core 'domain.player 'law.trail)
(let [service @infra.dev.window/service-state
      world @(:world service)
      config @(:config service)
      camera @(:camera service)
      ctx (infra.render.units/make-context camera {:width 1280 :height 720})
      projection (infra.render.scene.trails/project-trails ctx world)
      shapes (:shapes projection)
      alpha (mapv :alpha shapes)
      line-cache (get @infra.render.shader/program-cache :line)
      expected-hash (infra.render.shader/source-hash infra.render.shader/line-program)
      rows (mapv
             (fn [eid]
               (let [getc #(domain.ecs.core/get-component world eid %)
                     history (getc :component/motion-trail)]
                 {:entity eid
                  :body-kind (getc :component/body-kind)
                  :matter-state (getc :component/matter-state)
                  :position (getc :component/position)
                  :valid-history? (law.trail/valid-trail? history)
                  :samples (count (:samples history))
                  :first-sample (first (:samples history))
                  :last-sample (peek (:samples history))
                  :next-at (:next-at history)
                  :skipped-deadlines (:skipped-deadlines history)}))
             (sort (domain.ecs.core/entities-with world :component/motion-trail)))]
  {:wall-ms (System/currentTimeMillis)
   :tick (:tick world)
   :sim-time (:genesis/sim-time world)
   :dt (:sim/dt world)
   :fixture? (:demo/fixture? world)
   :arc (:arc/current world)
   :matter-states (frequencies (vals (get-in world [:components :component/matter-state])))
   :observer (domain.player/get-observer world)
   :thrust-intent (:player/thrust world)
   :camera (into {} camera)
   :controls (select-keys config [:mode :selection :follow-eid :ui/active-domain :ui/cursor-free?])
   :history rows
   :geometry (:summary projection)
   :vertices (count shapes)
   :alpha (when (seq alpha) {:min (reduce min ##Inf alpha) :max (reduce max ##-Inf alpha)
                            :distinct-values (count (set alpha))})
   :segment-probe (vec (take 4 shapes))
   :compiled-line line-cache
   :line-program (:line-program config)
   :expected-line-source-hash expected-hash
   :compiled-current-line? (and (pos? (long (:id line-cache 0)))
                               (= (:hash line-cache) expected-hash)
                               (= (:id line-cache) (:line-program config)))
   :window-thread-alive? (.isAlive (:thread service))
   :service-error (:error service)
   :ui-error (:ui/error-state config)})
