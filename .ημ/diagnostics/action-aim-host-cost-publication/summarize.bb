(require '[clojure.edn :as edn] '[cheshire.core :as json])
(let [root ".ημ/diagnostics/action-aim-host-cost-02/runs/"]
 (println (json/generate-string
  (into {} (for [rev ["baseline-02" "candidate-01"]]
    [rev (into {} (for [c ["empty" "legacy-16" "sculpt-1" "sculpt-8"]
      :let [d (edn/read-string (slurp (str root rev "/" c ".edn")))]]
      [c {:summary (:summary d) :criterium-mean (get-in d [:criterium :mean])
          :outlier-variance (get-in d [:criterium :outlier-variance])}]))])))))
