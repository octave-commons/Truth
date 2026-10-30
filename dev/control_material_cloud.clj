(require '[adaptive-material :as adaptive] '[material-experiment :as exp]
         '[domain.genesis :as genesis])
(let [initial (assoc (exp/initial-world) :sim/dt 0.5 :sim/theta 0.5 :material/coarse-enabled? false)
      out "truth/docs/demo/rotating-material-2026-10-24/control-cloud"]
  (.mkdirs (clojure.java.io/file out))
  (loop [w initial i 0]
    (when (zero? (mod i 240))
      (adaptive/snapshot! out (quot i 240) w "64 m material cloud control")
      (println :control-cloud i (:genesis/sim-time w)) (flush))
    (if (= i 4800)
      (do (spit (str out "/final-checkpoint.edn") (pr-str (adaptive/checkpoint w)))
          (println :control-cloud-complete))
      (recur (genesis/tick-world w) (inc i)))))
(shutdown-agents)
