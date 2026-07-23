(ns gates-of-truth.bench.folddelta
  "WORKING-TREE ONLY (do not commit): criterium delta for the completion-order
   overlapped fold."
  (:require
   [criterium.core :as crit]
   [domain.genesis.bootstrap :as bootstrap]
   [domain.genesis.tick :as gtick]
   [domain.ecs.tick :as tick]
   [domain.ecs.core :as ecs]
   [domain.spatial.index :as spatial]
   [domain.physics.cache :as pcache]
   [domain.genesis.systems :as systems]))

(defn- run-parallel-ordered [world systems]
  (let [futs  (mapv (fn [{:keys [id run]}] [id (future (run world))]) systems)
        wsets (mapv (fn [[id f]] [id (deref f)]) futs)]
    (tick/fold world wsets :on-conflict :throw)))

(defn- make-world [gas-count]
  (bootstrap/create-world {:gas-count gas-count :nebula-mass 2e30 :nebula-radius 1.5e16}))

(defn- report [label result]
  (println (format "  %-44s mean %.3f ms  (%.3f — %.3f)"
                   label
                   (* 1e3 (first (:mean result)))
                   (* 1e3 (first (:lower-q result)))
                   (* 1e3 (first (:upper-q result))))))

(defn -main []
  (doseq [gas [500 1000]]
    (println (str "\n=== gas=" gas " ==="))
    (let [w (make-world gas)
          w1 (-> w ecs/advance-tick spatial/spatial-index)
          systems (systems/physics-systems-parallel w1)
          w1c (-> w1 ecs/with-query-cache pcache/build-physics-soa)]
      ;; interleave old/new to share machine conditions
      (report "run-parallel OLD (declaration-order fold)"
              (crit/quick-benchmark* #(run-parallel-ordered w1c systems) {}))
      (report "run-parallel NEW (completion-order overlapped fold)"
              (crit/quick-benchmark* #(tick/run-parallel w1c systems) {}))
      (report "run-parallel OLD (repeat)"
              (crit/quick-benchmark* #(run-parallel-ordered w1c systems) {}))
      (report "run-parallel NEW (repeat)"
              (crit/quick-benchmark* #(tick/run-parallel w1c systems) {}))
      (report "tick-world NEW"
              (crit/quick-benchmark* #(gtick/tick-world w) {}))
      (report "tick-world OLD"
              (crit/quick-benchmark*
               #(with-redefs [tick/run-parallel
                              (fn [world systems & _] (run-parallel-ordered world systems))]
                  (gtick/tick-world w))
               {}))
      (report "tick-world NEW (repeat)"
              (crit/quick-benchmark* #(gtick/tick-world w) {}))
      (report "tick-world OLD (repeat)"
              (crit/quick-benchmark*
               #(with-redefs [tick/run-parallel
                              (fn [world systems & _] (run-parallel-ordered world systems))]
                  (gtick/tick-world w))
               {})))))
