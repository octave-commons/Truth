(ns evidence.grid
  "Bounded collector characterization and costs using existing benchmark factories."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [criterium.core :as criterium]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.genesis :as genesis]
   [domain.physics.cache :as cache]
   [domain.spatial.index :as spatial]
   [gates-of-truth.bench :as bench]
   [gates-of-truth.bench.phase0 :as phase0]
   [gates-of-truth.bench.spatial :as spatial-bench])
  (:import
   [java.lang.management ManagementFactory]
   [java.nio.charset StandardCharsets]
   [java.nio.file Files]
   [java.security MessageDigest]
   [java.util HexFormat UUID]))

(def ^:private baseline-index-sha
  "280941b79defe025dbdd7c1478940b956c1e0dc9bd270fa4c66f5170f1a94701")

(def ^:private source-paths
  ["src/domain/spatial/index.clj" "src/domain/physics/cache/neighbor.clj"
   "src/domain/genesis/bootstrap.clj" "src/domain/genesis/tick.clj"
   "src/domain/player/state.clj" "src/domain/stellar/merge.clj"
   "bench/gates_of_truth/bench.clj" "bench/gates_of_truth/bench/phase0.clj"
   "bench/gates_of_truth/bench/spatial.clj" "deps.edn"])

(def ^:private settings
  {:spatial {:factory-seed 42 :count 1000 :extent 1.0e14 :cell-size 1.0e13
             :query-centers :first-16-items :radii [1.0e13 4.0e13]
             :queries-per-batch 32 :predicate :default}
   :phase0 {500 {:gas-count 500 :nebula-mass 2.0e30 :nebula-radius 1.5e16}
            1000 {:gas-count 1000 :nebula-mass 4.0e30 :nebula-radius 2.0e16}}
   :phase0-default-seed 42 :component-window-ticks 4
   :criterium :existing-quick-bench-defaults
   :serial-counter-repetitions 100
   :comparison-exclusions [[:components c/observer :each-entity :id]]})

(defn- digest-bytes [payload]
  (.formatHex (HexFormat/of) (.digest (MessageDigest/getInstance "SHA-256") payload)))

(defn- source-sha [path]
  (digest-bytes (Files/readAllBytes (.toPath (io/file path)))))

(defn- source-hashes []
  (into (sorted-map) (map (fn [path] [path (source-sha path)])) source-paths))

(defn- stable-data
  "Represent data with ordered map/set entries and exact floating bits for hashing."
  [value]
  (cond
    (double? value) [:float64 (Double/doubleToRawLongBits value)]
    (float? value) [:float32 (Float/floatToRawIntBits value)]
    (map? value) [:map (->> value
                           (map (fn [[k v]] [(stable-data k) (stable-data v)]))
                           (sort-by (comp pr-str first)) vec)]
    (set? value) [:set (->> value (map stable-data) (sort-by pr-str) vec)]
    (vector? value) [:vector (mapv stable-data value)]
    (list? value) [:list (mapv stable-data value)]
    (or (nil? value) (boolean? value) (string? value) (keyword? value)
        (symbol? value) (integer? value) (ratio? value) (instance? UUID value)) value
    :else (throw (ex-info "Unexpected value in compact numerical evidence"
                          {:class (str (class value))}))))

(defn- fingerprint [value]
  (digest-bytes (.getBytes (pr-str (stable-data value)) StandardCharsets/UTF_8)))

(defn- write-new! [path data]
  (when (.exists (io/file path))
    (throw (ex-info "Evidence already exists; do not overwrite" {:path path})))
  (let [text (str (pr-str data) "\n")]
    (when-not (= data (edn/read-string text))
      (throw (ex-info "Evidence does not round-trip as ordinary EDN" {:path path})))
    (io/make-parents path)
    (spit path text)))

(defn- comparable-components [world]
  ;; The production observer UUID is random independently of the nebula seed.
  ;; Only the comparison projection omits it; no measured world is altered.
  (let [components (:components world)]
    (if-some [observers (get components c/observer)]
      (assoc components c/observer
             (into {} (map (fn [[eid observer]] [eid (dissoc observer :id)])) observers))
      components)))

(defn- component-evidence [world]
  {:tick (:tick world) :alive-count (count (:alive world))
   :components-sha256 (fingerprint (comparable-components world))})

(defn- world-input [world]
  {:components (component-evidence world)
   :alive-sha256 (fingerprint (:alive world))
   :archetypes-sha256 (fingerprint (:archetypes world))
   :parameters (into (sorted-map)
                     (remove (fn [[k _]]
                               (#{:components :archetypes :alive :handlers :rewind-handlers :ledger} k))) world)
   :ledger-event-count (count (get-in world [:ledger :events]))
   :handler-keys (set (keys (:handlers world)))})

(defn- make-workloads []
  (let [make-spatial (fn [label factory]
                       (let [items (factory 1000 1.0e14)
                             grid (spatial/build-grid items 1.0e13)
                             queries (vec (for [item (take 16 items), radius [1.0e13 4.0e13]]
                                            {:position (:position item) :radius radius}))]
                         {:label label :items items :grid grid :queries queries}))
        worlds {500 (#'phase0/make-medium-world)
                1000 (#'phase0/make-large-world)}]
    {:grid [(make-spatial "grid-uniform-1000" #'spatial-bench/make-uniform-points)
            (make-spatial "grid-clustered-1000" #'spatial-bench/make-clustered-points)]
     :worlds worlds
     :neighbor (into (sorted-map)
                     (map (fn [[n world]]
                            [n (-> world ecs/advance-tick spatial/spatial-index
                                   ecs/with-query-cache cache/build-physics-soa)])) worlds)}))

(defn- grid-batch [{:keys [grid queries]}]
  (mapv (fn [{:keys [position radius]}]
          (spatial/grid-within-radius grid position radius)) queries))

(defn- workload-evidence [{:keys [grid worlds neighbor]}]
  {:grid (mapv (fn [{:keys [label items queries] :as workload}]
                 {:label label :item-count (count items) :items-sha256 (fingerprint items)
                  :queries queries :ordered-result-ids (mapv #(mapv :id %) (grid-batch workload))})
               grid)
   :phase0-inputs (into (sorted-map)
                        (map (fn [[n world]] [n (world-input world)])) worlds)
   :neighbor (mapv (fn [[n snapshot]]
                     (when (seq (get-in snapshot [:components c/neighbor-cache]))
                       (throw (ex-info "Expected fresh rebuild without prior entries" {:size n})))
                     (let [ws ((:run (cache/neighbor-cache-system)) snapshot)]
                       (when-not (seq (get ws c/neighbor-cache))
                         (throw (ex-info "Neighbor workload produced no entries" {:size n})))
                       {:size n :snapshot-components (component-evidence snapshot)
                        :spatial-items-sha256 (fingerprint (:genesis/spatial-items snapshot))
                        :prior-entry-count 0 :entry-count (count (get ws c/neighbor-cache))
                        :write-set-sha256 (fingerprint ws)})) neighbor)
   :component-windows
   (into (sorted-map)
         (map (fn [[n world]]
                [n (mapv component-evidence
                         (rest (take 5 (iterate genesis/tick-world world))))])) worlds)})

(defn- capture! [path]
  (when-not (= baseline-index-sha (source-sha "src/domain/spatial/index.clj"))
    (throw (ex-info "Capture must use unchanged baseline collector" {})))
  (write-new! path {:baseline-head "b395c4049718f7ce015ddf25fc0a192d97821373"
                   :settings settings :sources (source-hashes)
                   :observations (workload-evidence (make-workloads))}))

(defn- serial-cost [f]
  (let [^com.sun.management.ThreadMXBean metrics (ManagementFactory/getThreadMXBean)
        thread-id (.getId (Thread/currentThread))]
    (when-not (and (.isCurrentThreadCpuTimeSupported metrics) (.isThreadCpuTimeEnabled metrics)
                   (.isThreadAllocatedMemorySupported metrics) (.isThreadAllocatedMemoryEnabled metrics))
      (throw (ex-info "Required thread CPU/allocation counters unavailable" {})))
    (let [cpu0 (.getCurrentThreadCpuTime metrics)
          bytes0 (.getThreadAllocatedBytes metrics thread-id)
          result (criterium/execute-expr 100 f)
          bytes1 (.getThreadAllocatedBytes metrics thread-id)
          cpu1 (.getCurrentThreadCpuTime metrics)]
      {:repetitions 100 :current-thread-only? true
       :cpu-ns-per-batch (/ (- cpu1 cpu0) 100.0)
       :bytes-per-batch (/ (- bytes1 bytes0) 100.0)
       :execute-wall-ns (first result) :last-result-hash (hash (second result))
       :includes-consumption-overhead? true})))

(defn- measure! [output label f serial?]
  (let [result (#'bench/quick-bench label f)
        counts (:outliers result)]
    (when-not (instance? criterium.core.OutlierCount counts)
      (throw (ex-info "Unexpected Criterium outlier representation" {})))
    (write-new! (str output "/" label ".edn")
                (cond-> {:label label
                         :criterium (-> (select-keys result [:mean :variance :lower-q :upper-q
                                                            :sample-mean :sample-variance :samples
                                                            :sample-count :execution-count :total-time
                                                            :warmup-executions :warmup-time :options
                                                            :outliers :outlier-variance])
                                        (assoc :outliers {:record-type "criterium.core.OutlierCount"
                                                          :fields (into {} counts)}))}
                  serial? (assoc :post-warmup-serial-cost (serial-cost f)))))
  label)

(defn- benchmark! [fixtures output]
  (let [frozen (edn/read-string (slurp fixtures))
        sources (source-hashes)
        workloads (make-workloads)
        observed (workload-evidence workloads)
        selected (atom [])
        skipped (atom [])
        labels {"tick-world (500 particles)" "phase0-500"
                "tick-world (1000 particles)" "phase0-1000"}
        callback (fn [label f]
                   (if-let [selected-label (get labels label)]
                     (swap! selected conj (measure! output selected-label f false))
                     (swap! skipped conj label)))]
    (when-not (= settings (:settings frozen))
      (throw (ex-info "Workload settings changed" {})))
    (when-not (= sources (:sources frozen))
      (throw (ex-info "Baseline source changed" {:actual sources :expected (:sources frozen)})))
    (when-not (= observed (:observations frozen))
      (write-new! (str output "/mismatched-observations.edn") observed)
      (throw (ex-info "Recreated baseline inputs or numerical outcomes differ" {})))
    (write-new! (str output "/inputs.edn")
                {:fixtures-sha256 (source-sha fixtures) :sources sources :settings settings
                 :observations-match? true :heap-max-bytes (.maxMemory (Runtime/getRuntime))})
    (doseq [{:keys [label] :as workload} (:grid workloads)]
      (measure! output label #(grid-batch workload) true))
    (doseq [[n snapshot] (:neighbor workloads)]
      (let [run (:run (cache/neighbor-cache-system))]
        (measure! output (str "neighbor-rebuild-" n) #(run snapshot) false)))
    ;; Reuse the existing group's actual tick closures. Replace only its two
    ;; factories with their already verified outputs; no measured tick is forked.
    (with-redefs-fn {#'phase0/make-medium-world (constantly (get-in workloads [:worlds 500]))
                    #'phase0/make-large-world (constantly (get-in workloads [:worlds 1000]))}
      #(phase0/run callback callback))
    (when-not (= ["phase0-500" "phase0-1000"] @selected)
      (throw (ex-info "Existing Phase0 selection changed" {:actual @selected})))
    (write-new! (str output "/coverage.edn")
                {:grid-cases (mapv :label (:grid workloads))
                 :neighbor-sizes (vec (keys (:neighbor workloads)))
                 :registered-selected @selected :registered-skipped @skipped
                 :claims "Six Criterium cases. Four-tick component fingerprints are characterization, not timing. Existing group setup profiles are not additional Criterium cases or native FPS."})))

(defn -main
  "Capture compact baseline evidence or measure its six unchanged-source cases."
  [action fixtures & [output]]
  (try
    (case action
      "capture" (capture! fixtures)
      "baseline" (benchmark! fixtures output)
      (throw (ex-info "Unknown baseline action" {:action action})))
    (finally (shutdown-agents))))
