(require '[clojure.test :as t] 'demo-test)
(binding [t/*report-counters* (ref t/*initial-report-counters*)]
  (t/test-vars [#'demo-test/interrupted-selection-restores-settings-without-cancelling-handoff
               #'demo-test/selection-wait-failure-restores-settings-and-preserves-original-error])
  (let [counts @t/*report-counters*]
    (println (pr-str counts))
    (shutdown-agents)
    (System/exit (if (zero? (+ (:fail counts) (:error counts))) 0 1))))
