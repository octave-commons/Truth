(require '[clojure.pprint :as pp]
         '[domain.ecs.components :as c]
         '[domain.ecs.core :as ecs]
         '[domain.ecs.tick :as tick]
         '[domain.integrator :as integrator]
         '[domain.player.flight :as flight]
         '[domain.player.state :as state]
         '[law.stellar :as stellar])

;; A separate one-body diagnostic fixture, never the natural world or live UI.
;; Both production systems read the same frozen input via the real Jacobi fold.
(defn step [world dt input]
  (tick/run-parallel
   (flight/set-thrust (assoc world :sim/dt dt) input)
   [(flight/thrust-acceleration-system) (integrator/integrator-system dt)]))

(defn x [world eid component]
  (first (ecs/get-component world eid component)))

(defn sample [world eid dt]
  {:position-au (/ (x world eid c/position) stellar/au)
   :velocity-times-dt-au (/ (* dt (x world eid c/velocity)) stellar/au)})

(defn response [dt displacement]
  (let [[initial eid] (state/spawn-observer (ecs/empty-world) [0.0 0.0 0.0])
        initial (assoc initial :genesis/spark-flight-displacement displacement)
        pulse (reductions (fn [world input] (step world dt input))
                          initial (cons [1.0 0.0 0.0] (repeat 1000 nil)))
        steady (nth (iterate #(step % dt [1.0 0.0 0.0]) initial) 1000)
        released (nth (iterate #(step % dt nil) steady) 1000)]
    {:dt dt
     :displacement-m-per-tick displacement
     :retention flight/default-damping-retention
     :first-pulse-samples (mapv #(sample % eid dt) (take 6 pulse))
     :pulse-total-translation-au (:position-au (sample (last pulse) eid dt))
     :held-terminal (sample steady eid dt)
     :release-total-translation-au (/ (- (x released eid c/position)
                                           (x steady eid c/position)) stellar/au)
     :released-final (sample released eid dt)}))

(try
  (pp/pprint
   {:kind :isolated-production-jacobi-response
    :natural-world-mutated? false
    :other-forces? false
    :constant-dt? true
    :results (mapv (fn [[dt displacement]] (response dt displacement))
                   [[1.0e7 flight/default-displacement-per-tick]
                    [4.1e9 flight/default-displacement-per-tick]
                    [1.0e12 1.0e12]])})
  (finally (shutdown-agents)))
