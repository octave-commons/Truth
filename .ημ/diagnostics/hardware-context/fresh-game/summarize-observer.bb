(require '[clojure.edn :as edn] '[clojure.java.io :as io])
(let [directory (io/file (first *command-line-args*))
      read-options {:default (fn [tag value] {:record-tag (str tag) :value value})}
      state (edn/read-string read-options (slurp (io/file directory "captures/observer-state.edn")))
      rows (:captures state)
      raw-samples (:raw-gl-samples state)
      _ (assert (= :complete (:phase state)))
      _ (assert (and (false? (:active? state)) (:original-root-restored? state)))
      _ (assert (= 3 (:capture-attempts state) (count rows)))
      _ (assert (empty? (:errors state)))
      _ (assert (and (= 9 (count raw-samples))
                     (every? #(and (= [0] (:codes %)) (false? (:truncated? %))) raw-samples)))
      _ (assert (every? :read-buffer-restored? rows))
      summary {:source "captures/observer-state.edn"
               :kind :derived-from-raw-native-observations
               :pid (:pid state) :window (:window state)
               :world-atom-identity (:world-atom-identity state)
               :display (:display state) :port (:port state)
               :phase (:phase state) :attempts (:capture-attempts state)
               :errors (:errors state) :original-root-restored? (:original-root-restored? state)
               :gl-sample-count (count raw-samples) :all-gl-samples-zero? true
               :boundary (:boundary state)
               :frames (mapv #(select-keys % [:capture :image :wall-ms :width :height :gl
                                             :read-buffer-before :read-buffer-restored?
                                             :published-world-readback-after-scene]) rows)
               :limits ["Screenshot cadence is not FPS; concurrent analysis and native load were present."
                        "World readbacks occur after the scene; their ticks are not exact rendered input."
                        "No injected input, manual approach/commitment, external desktop visibility or Gate claim."]}]
  (spit (io/file directory "derived-observer-summary.edn") (str (pr-str summary) "\n"))
  (println "Verified 3 captures, 9 zero native GL samples, and automatic restoration."))
