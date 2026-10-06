;; Optional bounded CPU-stage observation on ONE published native world.
;; Three calls per stage, no warmup/GC tuning and no GPU uploads. Results do not
;; apply to the simulation or alter its world. This is not an isolated benchmark.
(require 'infra.dev.window 'infra.render.units 'infra.render.scene.trails
         'infra.render.mesh 'domain.trail.system)
(let [service @infra.dev.window/service-state
      world @(:world service)
      camera @(:camera service)
      ctx (infra.render.units/make-context camera {:width 1280 :height 720})
      projection (infra.render.scene.trails/project-trails ctx world)
      shapes (:shapes projection)
      histories (vals (get-in world [:components :component/motion-trail]))
      sample-cost (fn [operation]
                    (mapv (fn [_]
                            (let [started (System/nanoTime)
                                  _result (operation)]
                              (/ (double (- (System/nanoTime) started)) 1.0e6)))
                          (range 3)))
      writer (:run (domain.trail.system/trail-system))]
  {:wall-ms (System/currentTimeMillis)
   :tick (:tick world)
   :sim-time (:genesis/sim-time world)
   :dt (:sim/dt world)
   :fixture? (:demo/fixture? world)
   :history-count (count histories)
   :sample-counts (frequencies (map #(count (:samples %)) histories))
   :geometry (:summary projection)
   :writer-ms (sample-cost #(writer world))
   :projection-ms (sample-cost #(infra.render.scene.trails/project-trails ctx world))
   :line-packing-ms (sample-cost #(infra.render.mesh/make-line-mesh shapes))
   :measurement-kind :bounded-live-cpu-observation
   :gpu-cost-measured? false})
