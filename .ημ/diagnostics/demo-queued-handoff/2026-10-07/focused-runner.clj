(require '[clojure.test :as t] 'demo-test)
(let [[variant historical-path] *command-line-args*]
  (assert (#{"historical-file-overlay" "current"} variant))
  (when (= "historical-file-overlay" variant)
    (assert historical-path)
    (load-file historical-path))
  (println (pr-str {:variant variant
                   :scope "One new regression; current queue/runtime/dependencies with optional historical demo.clj overlay, not a historical whole-tree run."}))
  (binding [t/*report-counters* (ref t/*initial-report-counters*)]
    (t/test-vars [#'demo-test/interrupted-handoff-restores-settings-without-cancelling-queued-world])
    (let [counts @t/*report-counters*]
      (println (pr-str counts))
      (shutdown-agents)
      (System/exit (if (zero? (+ (:fail counts) (:error counts))) 0 1)))))
