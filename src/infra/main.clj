(ns infra.main
  "Console and offscreen launch routes over the live game's ECS and renderer.

   Use clojure -M:dev or clojure -M:demo serve for the interactive native window."
  (:require
   [domain.arc :as arc]
   [domain.genesis :as genesis]
   [infra.render :as render]))

(defn run-render-demo
  "Render one actual nebula tick to /tmp/truth-view.png.

   The existing offscreen renderer needs an OpenGL display; use xvfb-run on a
   machine without a desktop. No fixture or alternate simulation is introduced."
  []
  (render/render-to-file (atom (genesis/create-world)) "/tmp/truth-view.png"
                         {:tick-fn arc/tick-genesis
                          :bodies-fn render/phase0-bodies+fields}))

(defn run-phase0-simulation
  "Advance a fresh nebula in the console for at most max-ticks ticks.

   Uses the same physics and narrative tick as the native window. Return the
   final world without resetting it when the budget or a terminal state ends
   the run; a budget limit is not a successful formation outcome."
  [max-ticks]
  (when-not (nat-int? max-ticks)
    (throw (ex-info "Tick budget must be a non-negative integer" {:ticks max-ticks})))
  (println "Gates of Truth — live Phase 0 console simulation")
  (loop [world (genesis/create-world)]
    (when (zero? (mod (:tick world) 5))
      (println (genesis/field-report world)))
    (let [ending (arc/genesis-ending world)
          stop-message (cond
                         ending (:message ending)
                         (not (:genesis/active world)) "Simulation inactive."
                         (>= (:tick world) max-ticks) "Console tick budget reached.")]
      (if stop-message
        (do (println stop-message) world)
        (recur (arc/tick-genesis world))))))

(defn- invalid-arguments
  [args]
  (throw (ex-info "Usage: clojure -M:run [console [ticks]|demo] (default: console, 1000 ticks)"
                  {:arguments args})))

(defn -main
  "Run the console simulation, or render a PNG with the demo subcommand.

   console accepts an optional non-negative tick budget; unknown arguments fail
   before starting a simulation. Release Clojure worker pools on every exit."
  [& args]
  (try
    (cond
      (empty? args) (run-phase0-simulation 1000)
      (= ["demo"] (vec args)) (run-render-demo)
      (and (= "console" (first args)) (<= (count args) 2))
      (let [ticks (if-let [text (second args)] (parse-long text) 1000)]
        (if (nat-int? ticks)
          (run-phase0-simulation ticks)
          (invalid-arguments args)))
      :else (invalid-arguments args))
    (finally (shutdown-agents))))
