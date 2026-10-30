;; Repeatable 32 m initial-cloud experiment; run from the Truth parent folder.
(require '[rotation-study :as study] '[adaptive-material :as adaptive]
         '[material-experiment :as exp] '[domain.genesis :as genesis])
(let [initial (-> (exp/initial-world) study/subdivide
                  (assoc :sim/dt 0.5 :sim/theta 0.5 :material/coarse-enabled? false))
      out "truth/docs/demo/rotating-material-2026-10-24/fine-cloud"]
  (loop [w initial i 0]
    (when (zero? (mod i 240))
      (adaptive/snapshot! out (quot i 240) w "32 m material cloud assembly")
      (println :fine-cloud i (:genesis/sim-time w)) (flush))
    (if (= i 4800)
      (do (spit (str out "/final-checkpoint.edn") (pr-str (adaptive/checkpoint w)))
          (println :fine-cloud-complete))
      (recur (genesis/tick-world w) (inc i)))))
(shutdown-agents)
