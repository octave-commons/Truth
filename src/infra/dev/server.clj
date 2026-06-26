(ns infra.dev.server
  "Development service entry point.

   Starts a dedicated GLFW window on a background thread and an nREPL
   server so you can connect from another terminal/Emacs/Cursive and
   mutate the running simulation in real time.

   Run:
     clj -M:dev

   Connect from a second terminal:
     clj -M:repl --connect localhost:7888

   Then explore:
     (require '[infra.dev.window :as w])
     @(:camera @w/service-state)
     (swap! (:camera @w/service-state) assoc :distance 400.0)
     (w/reload-shaders!)
     (w/take-screenshot! \"/tmp/truth-dev.png\")"
  (:require
    [nrepl.server     :as nrepl]
    [infra.dev.window :as window]
    [infra.render     :as render]
    [domain.particles.phase0 :as phase0]))

(defn -main
  "Start the dev window + nREPL background service.

   The window shows Phase 0: a particle gas cloud collapsing under self-gravity,
   fragmenting into protostars, and flattening into a disk through inelastic
   accretion. Massive sinks are promoted to resolved stars/planets. When a system
   finishes forming (or the spark's coherence fades) we drift to a fresh nebula
   and begin again."
  [& _args]
  (println "Booting Gates of Truth dev service...")
  (let [world  (atom (phase0/create-world))
        _      (window/start! world
                 {:tick-fn            phase0/tick-world
                  :bodies-fn          render/particle-phase0-bodies-from-world
                  :camera             (render/make-camera 40.0)
                  :sim-frame-interval 4
                  :on-step            (fn [w]
                                        (if (:phase0/active w)
                                          w
                                          (phase0/create-world)))})
        server (nrepl/start-server :port 7888 :bind "127.0.0.1")]
    (println "nREPL server listening on 127.0.0.1:7888")
    (.addShutdownHook
      (Runtime/getRuntime)
      (Thread. #(do (window/stop!)
                    (nrepl/stop-server server)
                    (println "Dev service shut down."))))
    @(promise)))
