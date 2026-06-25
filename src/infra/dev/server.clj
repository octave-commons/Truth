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
    [infra.main       :as main]))

(defn -main
  "Start the dev window + nREPL background service."
  [& _args]
  (println "Booting Gates of Truth dev service...")
  (let [world  (main/make-demo-world)
        _      (window/start! world)
        server (nrepl/start-server :port 7888 :bind "127.0.0.1")]
    (println "nREPL server listening on 127.0.0.1:7888")
    (.addShutdownHook
      (Runtime/getRuntime)
      (Thread. #(do (window/stop!)
                    (nrepl/stop-server server)
                    (println "Dev service shut down."))))
    @(promise)))
