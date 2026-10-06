(ns evidence.kepler
  "Bounded baseline capture and adapters to existing Criterium/phase0 workloads."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [criterium.core :as criterium]
   [domain.ecs.core :as ecs]
   [domain.genesis :as genesis]
   [domain.orbital.kepler :as kepler]
   [domain.orbital.kepler-workload :as workload]
   [domain.physics.cache.soa :as soa]
   [domain.spatial.index :as spatial]
   [gates-of-truth.bench.phase0 :as phase0])
  (:import
   [java.lang.management ManagementFactory]
   [java.nio.file Files]
   [java.security MessageDigest]
   [java.util HexFormat]))

(def ^:private baseline-sha
  "3707256ad8695a675c29fe3953f5441598cc2f434baa30ab6e33ab48ae1effc1")

(defn- source-sha []
  (.formatHex (HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256")
                       (Files/readAllBytes (.toPath (io/file "src/domain/orbital/kepler.clj"))))))

(defn- write-new! [path data]
  (when (.exists (io/file path))
    (throw (ex-info "Evidence output already exists; do not overwrite" {:path path})))
  (let [text (with-out-str (pprint/pprint data))]
    (when-not (= data (edn/read-string text))
      (throw (ex-info "Evidence does not round-trip as ordinary EDN" {:path path})))
    (io/make-parents path)
    (spit path text)))

(defn- capture! [path]
  (when-not (= baseline-sha (source-sha))
    (throw (ex-info "Oracle must be captured from the unchanged baseline solver" {})))
  (let [public (workload/public-oracle)
        compact (workload/compact-oracle)
        data {:source {:commit "b402939931575e377ae4409d9cd4061dff11df06"
                       :kepler-sha256 baseline-sha}
              :public public :edges (workload/edge-oracle) :compact compact}]
    (write-new! path data)
    (pprint/pprint
     {:oracle path :source (:source data)
      :public-cases (count public)
      :declared-failures (vec (keep (fn [{case-id :case :keys [direction outcome]}]
                                     (when (:error outcome)
                                       {:case case-id :direction direction
                                        :step (:failed-step outcome)
                                        :error (:error outcome)}))
                                   public))
      :compact-paths (mapv (fn [{:keys [path scenario gates]}]
                            {:path path :scenario scenario
                             :all-gates-pass? (every? :pass? gates)
                             :first-offset (:frame-offset (first gates))})
                          compact)})))

(defn- propagation-batch [inputs]
  (mapv (fn [{:keys [mu r v dt]}]
          (loop [state {:position r :velocity v}, i 0]
            (if (= i workload/trajectory-steps)
              state
              (recur (kepler/propagate mu (:position state) (:velocity state) dt)
                     (inc i)))))
        inputs))

(defn- thread-cost [f]
  (let [^com.sun.management.ThreadMXBean metrics (ManagementFactory/getThreadMXBean)
        thread-id (.getId (Thread/currentThread))]
    (when-not (and (.isCurrentThreadCpuTimeSupported metrics)
                   (.isThreadCpuTimeEnabled metrics)
                   (.isThreadAllocatedMemorySupported metrics)
                   (.isThreadAllocatedMemoryEnabled metrics))
      (throw (ex-info "Required thread CPU/allocation counters are unavailable" {})))
    (let [cpu0 (.getCurrentThreadCpuTime metrics)
          bytes0 (.getThreadAllocatedBytes metrics thread-id)
          results (criterium/execute-expr 100 f)
          bytes1 (.getThreadAllocatedBytes metrics thread-id)
          cpu1 (.getCurrentThreadCpuTime metrics)]
      {:repetitions 100 :thread-only? true
       :cpu-ns-per-batch (/ (- cpu1 cpu0) 100.0)
       :bytes-per-batch (/ (- bytes1 bytes0) 100.0)
       :criterium-execute-wall-ns (first results)
       :last-result-hash (hash (second results))
       :includes-criterium-consumption-overhead? true})))

(defn- measure [label f thread-only?]
  (println "Measuring" label)
  (let [result (criterium/quick-benchmark* f {})]
    (cond-> {:label label
             ;; Preserve samples/options; deliberately omit JVM argument metadata.
             :criterium (select-keys result [:mean :variance :lower-q :upper-q
                                             :sample-mean :sample-variance :samples
                                             :sample-count :execution-count :total-time
                                             :warmup-executions :warmup-time :options
                                             :outliers :outlier-variance])}
      thread-only? (assoc :post-warmup-thread-cost (thread-cost f)))))

(defn- propagation-cost! [path]
  (let [groups [[:elliptic #{:circular :ellipse :eccentric :rotated-ellipse
                            :radial-outward :solar-scale :small-scale}]
                [:near-parabolic #{:parabolic :near-parabolic-bound :near-parabolic-unbound}]
                [:hyperbolic #{:hyperbolic :hyperbolic-infall}]]
        results (mapv (fn [[label ids]]
                        (let [inputs (filterv #(ids (:id %)) workload/cases)]
                          (assoc (measure label #(propagation-batch inputs) true)
                                 :inputs inputs :steps workload/trajectory-steps)))
                      groups)
        compact (mapv (fn [scenario]
                        (let [world (workload/compact-world scenario)
                              gate (workload/compact-path-observation world)]
                          (when-not (:pass? gate)
                            (throw (ex-info "Compact benchmark misses Kepler path" gate)))
                          (assoc (measure [:compact-soa scenario]
                                          #(workload/compact-step :soa world) false)
                                 :gate gate)))
                      [:stationary :moving-recentered])]
    (write-new! path {:kepler-sha256 (source-sha) :propagation results :compact compact})))

(defn- phase0-cost! [path]
  (let [selected #{"tick-world (500 particles)" "tick-world (1000 particles)"}
        results (atom [])
        skipped (atom [])
        measure-selected (fn [label f]
                           (if (selected label)
                             (swap! results conj (measure label f false))
                             (swap! skipped conj label)))]
    ;; Execute the existing group's exact closures and setup, not another tick.
    (phase0/run measure-selected measure-selected)
    (when-not (= selected (set (map :label @results)))
      (throw (ex-info "Existing benchmark labels changed" {:measured @results})))
    (doseq [[n world] [[500 (#'phase0/make-medium-world)]
                       [1000 (#'phase0/make-large-world)]]]
      (let [frozen (-> world ecs/advance-tick spatial/spatial-index
                       ecs/with-query-cache soa/build-physics-soa)]
        (dotimes [_ 10] (genesis/tick-world world))
        (dotimes [_ 3]
          (run! (fn [{:keys [run]}] (run frozen)) (genesis/physics-systems-parallel frozen)))
        (dotimes [sample 5]
          (phase0/profile-step-physics-systems-on frozen (str n " prepared sample " sample)))))
    (write-new! path {:kepler-sha256 (source-sha) :selected @results :skipped @skipped
                     :coverage "Only original500/1000 tick closures measured by Criterium; named system samples printed separately. Not the entire phase0 group."})))

(defn -main
  "Run one explicit capture or benchmark action; never connect to a native service."
  [action output]
  (try
    (case action
      "capture" (capture! output)
      "propagation-cost" (propagation-cost! output)
      "phase0-cost" (phase0-cost! output)
      (throw (ex-info "Unknown evidence action" {:action action})))
    (finally (shutdown-agents))))
