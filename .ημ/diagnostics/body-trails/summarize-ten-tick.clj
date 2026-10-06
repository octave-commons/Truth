;; Derive the targeted report using the standard EDN reader after runs close.
(require '[clojure.edn :as edn]
         '[clojure.string :as str])

(let [directory (first *command-line-args*)
      path #(str directory "/" %)
      read-data #(edn/read-string (slurp (path %)))
      before (read-data "target-before-measurements.edn")
      after (read-data "target-after-measurements.edn")
      before-population (read-data "target-before-population.edn")
      after-population (read-data "target-after-population.edn")
      physical-keys [:tick :sim-time :dt :matter-states]
      unchanged? (= (mapv #(select-keys % physical-keys) before-population)
                    (mapv #(select-keys % physical-keys) after-population))
      compact (fn [measurement]
                (let [stats (:statistics measurement)]
                  {:repetition (:iteration measurement)
                   :mean-ms (* 1000.0 (first (:mean stats)))
                   :lower-quantile-ms (* 1000.0 (first (:lower-q stats)))
                   :upper-quantile-ms (* 1000.0 (first (:upper-q stats)))
                   :samples (:sample-count stats)
                   :executions-per-sample (:execution-count stats)}))
      report {:before (mapv compact before)
              :after (mapv compact after)
              :matching-observed-physical-fields unchanged?
              :physical-fields-compared physical-keys
              :history-population (mapv #(select-keys % [:tick :trailed-bodies :total-samples
                                                        :max-samples :sample-counts
                                                        :skipped-deadlines]) after-population)}
      timing-rows (mapcat (fn [rep-index old after-row]
                           [(format "| %d | Before | %.1f ms | %.1f–%.1f ms |"
                                    rep-index (:mean-ms old) (:lower-quantile-ms old) (:upper-quantile-ms old))
                            (format "| %d | After | %.1f ms | %.1f–%.1f ms |"
                                    rep-index (:mean-ms after-row) (:lower-quantile-ms after-row) (:upper-quantile-ms after-row))])
                         [1 2 3] (:before report) (:after report))
      population-rows (map #(format "| %d | %d | %d | %s |"
                                   (:tick %) (:trailed-bodies %) (:total-samples %) (pr-str (:sample-counts %)))
                            (:history-population report))
      lines (concat
             ["# Targeted ten-tick repeat" ""
              "The full benchmark's +37.3% ten-tick increase did not reproduce consistently"
              "in three targeted repetitions on each exact original source revision. The"
              "raw original result remains preserved; no production optimization was made."
              "This does not establish a general speedup or a narrow regression bound."
              ""
              "Before: `e71b12f1f5fe070695fd1cd82cded2296a9f862d`."
              "After: `3614d34854bd0953ea12aef4aae1e3642b5b9c4b`."
              "Both ran back-to-back in the clean `truth-validation` checkout, with the"
              "authorized clean detached-source switch recorded. Each process exited 0"
              "and was reaped; other owned heavy loads remained held and native PID 2372912"
              "remained suspended. Unrelated host workloads were recorded and untouched."
              ""
              "`ten-tick-repeat.clj` selects the actual `  10 ticks` closure from the existing"
              "`gates-of-truth.bench.phase0/run` callback. The original setup and constructor"
              "are retained. It invokes the existing normal Criterium quick runner three"
              "times; unrelated timed callbacks are skipped. These targeted timings"
              "therefore have different surrounding warmup work than the full-suite run."
              ""
              "| Repetition | Revision | Mean | Reported quantile range |"
              "|---|---|---:|---|"
              ]
             timing-rows
             ["" "Each quick repetition has six samples, one ten-tick execution per sample."
              "Full unrounded means, confidence estimates, samples, outlier data, and"
              "warmup times are retained in the `*-measurements.edn` files. Broad scatter"
              "is visible on both revisions. Three repetitions within one JVM are not"
              "three independent machine environments, and the order was not randomized."
              ""
              "The timed closure starts each invocation from the same immutable fresh"
              "500-gas world. Histories grow within its ten ticks, never across benchmark"
              "invocations. An untimed trace using the same original constructor and"
              "production tick records the following actual after population:"
              ""
              "| Tick | Histories | Total retained samples | Samples per history → count |"
              "|---|---:|---:|---|"]
             population-rows
             [""
              (str "Exact equality of observed tick, simulation-time, dt, and matter-state counts"
                   " between the before and after traces: **" unchanged? "**.")
              "One condensed core appears at tick 2 and becomes a gas giant at tick 6."
              "The second history begins at tick 7, respecting the frozen-snapshot fan-out."
              "By tick 10 there are two histories with ten and four samples, respectively."
              "The trace does not approach the 64-sample capacity or global render cap."
              ""
              "The repeat resolves the earlier case as a non-reproduced signal, not proof"
              "of zero overhead. Mature-history cost, native GPU rendering, visible fading,"
              "and gameplay acceptance still require their separate planned evidence."
              "Source/environment/timing/provenance files and their hashes are included in"
              "`target-CLOSED-FILES.txt` and `target-SHA256SUMS`."
              ""])]
  (assert (= 3 (count before) (count after)))
  (assert unchanged?)
  (spit (path "target-comparison.edn") (str (pr-str report) "\n"))
  (spit (path "target-comparison.md") (str/join "\n" lines))
  (prn (select-keys report [:before :after :matching-observed-physical-fields])))
