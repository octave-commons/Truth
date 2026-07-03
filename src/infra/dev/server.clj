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
     (w/take-screenshot! \"/tmp/truth-dev.png\")

     Camera controls in the window:
     C              cycle camera mode (manual / track-largest-cluster / fit-all)
     [ / ]          decrease / increase fit margin
     R              reset camera and settings
     W / S          move the mote forward / backward relative to the camera
     A / D          strafe the mote left / right
     mouse          rotate the camera around the mote (third-person)
     scroll         adjust distance"
  (:require
   [nrepl.server     :as nrepl]
   [infra.dev.window :as window]
   [infra.render     :as render]
   [infra.camera     :as cam]
   [domain.genesis    :as genesis]
   [domain.arc        :as arc]))

(defn -main
  "Start the dev window + nREPL background service.

   The window shows Phase 0 from the single ECS world (domain.genesis): a nebula
   collapsing under self-gravity with its magnetic field flux-freezing and
   amplifying, igniting a star and settling planets, rendered as regime-tinted
   fog, magnetic field lines, and shaded bodies. When a system finishes forming
   (or the spark's coherence fades) we drift to a fresh nebula and begin again."
  [& _args]
  (println "Booting Gates of Truth dev service...")
  (let [world  (atom (genesis/create-world))
        _      (window/start! world
                              {:tick-fn            arc/tick-genesis
                               :bodies-fn          render/phase0-bodies+fields
                               :camera             (cam/make-camera 60.0)
                               :sim-frame-interval 4
                               :on-step            (fn [w]
                                                     (when (zero? (mod (:tick w) 40))
                                                       (println (genesis/field-report w)))
                                                     (if (:genesis/active w)
                                                       w
                                                       (genesis/create-world)))})
        server (nrepl/start-server :port 7888 :bind "127.0.0.1")]
    (println "nREPL server listening on 127.0.0.1:7888")
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread. #(do (window/stop!)
                   (nrepl/stop-server server)
                   (println "Dev service shut down."))))
    @(promise)))
