(ns infra.main
  "Entry point for Gates of Truth — Phase 0: Stellar Nebula.

   The game and the render demo share ONE world model: the ECS world. Phase 0 is
   a composition layer over it (see domain.phase0). There is no separate
   simulation path."
  (:require
    [domain.phase0         :as phase0]
    [domain.player         :as player]
    [domain.ecs.components :as c]
    [domain.ecs.core       :as ecs]
    [infra.render          :as render]))

;; --- Render demo ------------------------------------------------------------

(defn- body
  "Insert a celestial body entity into the world."
  [world eid kind mass radius pos vel]
  (-> world
      (ecs/put-component eid c/body-kind kind)
      (ecs/put-component eid c/mass     mass)
      (ecs/put-component eid c/radius   radius)
      (ecs/put-component eid c/position pos)
      (ecs/put-component eid c/velocity vel)))

(defn make-demo-world
  "A small Sun/Earth/Moon world for exercising the renderer."
  []
  (atom
    (-> (ecs/empty-world)
        (body :sun   :body/star   1000.0 5.0  [0.0 0.0 0.0]  [0.0 0.0 0.0])
        (body :earth :body/planet 10.0   2.0  [50.0 0.0 0.0] [0.0 0.0 4.47])
        (body :moon  :body/moon   0.1    0.5  [58.0 0.0 0.0] [0.0 0.0 5.57]))))

(defn run-render-demo []
  (println "Rendering Sun/Earth/Moon demo frame...")
  (let [world (make-demo-world)
        path  "/tmp/truth-view.png"]
    (render/render-to-file world path)
    (println "Saved frame to" path)))

;; --- Phase 0 console simulation ---------------------------------------------

(defn run-phase0-simulation
  "Run the Phase 0 stellar-nebula simulation in the console."
  []
  (println "\n=== GATES OF TRUTH — PHASE 0: STELLAR NEBULA ===\n")
  (println "You are a quantum oscillation, a spark of awareness")
  (println "drifting through a vast stellar nebula...\n")
  (loop [w (phase0/create-world) i 0]
    (let [summ (phase0/system-summary w)
          obs  (player/get-observer w)]
      (when (zero? (mod i 5))
        (println (format "tick %3d | %-24s | bodies %d  star %s  planets %d | coherence %.2f | %.2e yr"
                         i (name (:phase0/phase w))
                         (:body-count summ)
                         (if (:star? summ) "yes" "no ")
                         (:planet-count summ)
                         (double (:coherence obs))
                         (/ (:phase0/sim-time w) 3.15e7))))
      (if-let [ending (phase0/world-ending w)]
        (do
          (println "\n=== SIMULATION END ===")
          (println (:message ending))
          (when (= (:type ending) :success)
            (println "Habitable worlds formed:" (count (:worlds ending)))))
        (if (or (> i 1000) (not (:phase0/active w)))
          (println "\nPhase 0 simulation complete.")
          (do (Thread/sleep 30)
              (recur (phase0/tick-world w) (inc i))))))))

(defn -main
  "Launch Gates of Truth. `demo` renders a frame; default runs Phase 0."
  [& args]
  (if (= (first args) "demo")
    (run-render-demo)
    (run-phase0-simulation)))
