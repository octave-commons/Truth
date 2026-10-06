;; Targeted diagnostic only. The timed function is selected unchanged from the
;; existing Phase0 benchmark's injected callback, not copied or reconstructed.
;; Run from each pinned checkout with the ordinary bench dependency/classpath.
(require '[clojure.java.io :as io]
         '[domain.genesis :as genesis]
         '[gates-of-truth.bench :as bench]
         '[gates-of-truth.bench.phase0 :as phase0])

(defn population
  "Read actual history/state population without classifying or altering it."
  [world]
  (let [histories (vals (get-in world [:components :component/motion-trail]))
        sizes (mapv #(count (:samples %)) histories)]
    {:tick (:tick world)
     :sim-time (:genesis/sim-time world)
     :dt (:sim/dt world)
     :matter-states (frequencies (vals (get-in world [:components :component/matter-state])))
     :trailed-bodies (count histories)
     :sample-counts (frequencies sizes)
     :total-samples (reduce + 0 sizes)
     :max-samples (reduce max 0 sizes)
     :skipped-deadlines (reduce + 0 (map :skipped-deadlines histories))}))

(let [output-prefix (first *command-line-args*)
      selected (atom nil)
      quick #'bench/quick-bench
      full #'bench/full-bench
      make-world #'phase0/make-medium-world
      population-path (str output-prefix "-population.edn")
      measurements-path (str output-prefix "-measurements.edn")]
  (assert (and output-prefix quick full make-world))
  (assert (not (.exists (io/file population-path))) "Do not replace existing evidence")
  (assert (not (.exists (io/file measurements-path))) "Do not replace existing evidence")
  (try
    (#'bench/print-system-info)
    ;; Existing setup/profile work still executes. All unrelated timed callbacks
    ;; are skipped; the original closure already captures its original w500.
    (phase0/run (fn [label thunk]
                  (when (= "  10 ticks" label)
                    (assert (nil? @selected) "Target label must occur exactly once")
                    (reset! selected thunk)))
                full)
    (assert (some? @selected) "Existing ten-tick benchmark was not found")
    ;; Same private constructor as the original benchmark. This untimed probe
    ;; records each real production tick; it never modifies a world in place.
    (let [samples (mapv population (take 11 (iterate genesis/tick-world (make-world))))]
      (spit population-path (str (pr-str samples) "\n"))
      (println "UNTIMED POPULATION:" (pr-str samples)))
    (let [results (mapv
                   (fn [rep-index]
                     (let [result (quick (str "ten ticks targeted repetition " rep-index) @selected)]
                       {:iteration rep-index
                        :statistics (select-keys result
                                                 [:mean :variance :lower-q :upper-q :sample-count
                                                  :execution-count :total-time :warmup-time
                                                  :outlier-count :outlier-variance :samples])}))
                   [1 2 3])]
      (spit measurements-path (str (pr-str results) "\n")))
    (println "TARGETED REPEAT COMPLETE")
    (finally (shutdown-agents))))
