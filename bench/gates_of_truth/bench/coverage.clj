(ns gates-of-truth.bench.coverage
  "Benchmark coverage analyzer for Gates of Truth.

   Reports which source namespaces are targeted by the benchmark suite.
   Coverage is declared per benchmark group in `gates-of-truth.bench/benchmark-groups`
   via the `:covers` set of source namespace symbols. See
   `docs/specs/benchmark-coverage.md` for the full model.

   Running:
     clojure -M:bench-coverage
     clojure -M:bench-coverage --threshold 40

   Exit code:
     0 when the report is printed and the threshold (if any) is met.
     1 when registry validation fails or the threshold is not met.

   The analyzer does not run any benchmarks; it only reads the registry and
   scans the source tree. This keeps the report fast and avoids distorting
   benchmark timings with instrumentation.

   TODO: add --json output for CI artifacts."
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [gates-of-truth.bench :as bench])
  (:import
   [java.io File PushbackReader]))

;; ---------------------------------------------------------------------------
;; Source discovery
;; ---------------------------------------------------------------------------

(defn- clj-files
  "Return all .clj files under a directory, recursively."
  [dir]
  (->> (io/file dir)
       file-seq
       (filter #(and (.isFile ^File %) (str/ends-with? (.getName ^File %) ".clj")))))

(defn- ns-from-file
  "Extract the namespace symbol from a .clj file by reading its first ns form.
   Skips leading metadata forms and comments. Returns nil if no ns form is found."
  [file]
  (with-open [r (PushbackReader. (io/reader file))]
    (loop []
      (let [form (try (read r) (catch Exception _ ::eof))]
        (cond
          (= ::eof form) nil
          (and (seq? form) (= 'ns (first form))) (second form)
          :else (recur))))))

(defn source-namespaces
  "Return the set of all namespaces declared under src/."
  []
  (->> (clj-files "src")
       (keep ns-from-file)
       (into #{})))

(defn quadrant
  "Return the quadrant keyword for a namespace symbol: :domain, :infra, :shape,
   :law, or nil for namespaces outside the four quadrants."
  [ns-sym]
  (let [s (name ns-sym)]
    (cond
      (str/starts-with? s "domain.") :domain
      (str/starts-with? s "infra.") :infra
      (str/starts-with? s "shape.") :shape
      (str/starts-with? s "law.") :law
      :else nil)))

;; ---------------------------------------------------------------------------
;; Quadrant filtering
;; ---------------------------------------------------------------------------

(def ^:private reported-quadrants
  "Quadrants included in benchmark coverage totals. `law` is excluded because
   law namespaces are schemas/contracts and are not directly targeted by
   performance benchmarks; including them would add noise to the coverage
   percentage."
  #{:domain :infra :shape})

(def ^:private excluded-namespaces
  "Specific namespaces excluded from coverage totals even if they belong to a
   reported quadrant. `shape.core` is pure data constructors (Shape, Claim,
   UUID helpers) with no performance relevance; `shape.spatial` is the hot-path
   math that remains in the denominator."
  #{'shape.core})

(defn relevant-source-namespaces
  "Return the subset of source namespaces that belong to the reported quadrants
   and are not explicitly excluded."
  [source-ns]
  (into #{} (filter #(and (reported-quadrants (quadrant %))
                          (not (excluded-namespaces %)))
                    source-ns)))

;; ---------------------------------------------------------------------------
;; Registry validation
;; ---------------------------------------------------------------------------

(defn validate-registry
  "Validate that every namespace in every benchmark group's `:covers` set exists
   in `source-namespaces` and is a symbol. Returns a vector of error strings."
  [groups source-ns]
  (vec
   (for [[group-key {:keys [label covers]}] groups
         :let [label (or label (name group-key))]
         covered covers
         :let [err (cond
                     (not (symbol? covered))
                     (format "[%s] %s :covers contains non-symbol: %s"
                             label group-key (pr-str covered))
                     (not (source-ns covered))
                     (format "[%s] %s :covers references unknown namespace: %s"
                             label group-key covered))]
         :when err]
     err)))

;; ---------------------------------------------------------------------------
;; Coverage computation
;; ---------------------------------------------------------------------------

(defn covered-namespaces
  "Return the set of all source namespaces covered by the benchmark groups."
  [groups]
  (->> groups
       vals
       (mapcat :covers)
       (into #{})))

(defn coverage-metrics
  "Compute coverage metrics. Returns a map with:
     :total-total    total source namespaces
     :total-covered  covered source namespaces
     :total-pct      percentage covered
     :by-quadrant    map of quadrant -> {:total :covered :pct}
     :uncovered      set of uncovered namespaces
     :by-group       map of group -> covered count"
  [source-ns groups]
  (let [covered (covered-namespaces groups)
        uncovered (set/difference source-ns covered)
        quadrants (group-by quadrant source-ns)
        q-covered (group-by quadrant (set/intersection source-ns covered))]
    {:total-total (count source-ns)
     :total-covered (count (set/intersection source-ns covered))
     :total-pct (if (zero? (count source-ns))
                  0.0
                  (* 100.0 (/ (count (set/intersection source-ns covered))
                              (count source-ns))))
     :by-quadrant (into {}
                        (for [q (keys quadrants)]
                          (let [q-total (set (quadrants q))
                                q-cov (set (q-covered q #{}))]
                            [q {:total (count q-total)
                                :covered (count (set/intersection q-total q-cov))
                                :pct (if (zero? (count q-total))
                                       0.0
                                       (* 100.0 (/ (count (set/intersection q-total q-cov))
                                                   (count q-total))))}])))
     :uncovered uncovered
     :by-group (into {} (for [[k {:keys [label covers]}] groups]
                          [k {:label (or label (name k))
                              :covered (count (set/intersection source-ns covers))}]))}))

;; ---------------------------------------------------------------------------
;; Reporting
;; ---------------------------------------------------------------------------

(defn- separator []
  (println (apply str (repeat 72 "="))))

(defn- print-header []
  (separator)
  (println "  Gates of Truth — Benchmark Coverage Report")
  (println "  Model: namespace-level, declared coverage (see docs/specs/benchmark-coverage.md)")
  (separator)
  (println))

(defn- print-summary [metrics]
  (println (format "  Total relevant source namespaces: %d" (:total-total metrics)))
  (println (format "  Covered by benchmarks:            %d" (:total-covered metrics)))
  (println (format "  Coverage:                         %.1f%%" (:total-pct metrics)))
  (println "  (law and shape.core excluded from totals — see docs/specs/benchmark-coverage.md)")
  (println))

(defn- print-quadrant-breakdown [metrics]
  (println "  By quadrant:")
  (printf "  %-10s %8s %8s %10s\n" "" "total" "covered" "pct")
  (doseq [q [:domain :infra :shape]]
    (when-let [{:keys [total covered pct]} (get-in metrics [:by-quadrant q])]
      (printf "  %-10s %8d %8d %9.1f%%\n" (name q) total covered pct)))
  (println))

(defn- print-group-table [groups source-ns]
  (println "  Benchmark groups and coverage:")
  (println (format "  %-24s %8s" "Group" "Covered"))
  (doseq [[k {:keys [label covers]}] groups]
    (let [covered (count (set/intersection source-ns covers))]
      (println (format "  %-24s %8d" (format "%s (%s)" label (name k)) covered))))
  (println))

(defn- print-uncovered [metrics]
  (let [uncovered (:uncovered metrics)]
    (if (seq uncovered)
      (do (println "  Uncovered namespaces:")
          (doseq [q [:domain :infra :shape]
                  :let [ns-in-q (sort (filter #(= q (quadrant %)) uncovered))]
                  :when (seq ns-in-q)]
            (println (format "    %s:" (name q)))
            (doseq [ns-sym ns-in-q]
              (println (format "      - %s" ns-sym))))
          (println))
      (println "  All relevant source namespaces are covered by a benchmark group."))))

(defn print-report
  "Print a human-readable benchmark coverage report over the relevant
   quadrants (domain, infra, shape)."
  [groups source-ns]
  (let [metrics (coverage-metrics source-ns groups)]
    (print-header)
    (print-summary metrics)
    (print-quadrant-breakdown metrics)
    (print-group-table groups source-ns)
    (print-uncovered metrics)
    (separator)
    metrics))

;; ---------------------------------------------------------------------------
;; CLI
;; ---------------------------------------------------------------------------

(defn- parse-args
  "Parse command-line args. Returns a map with optional :threshold (double)."
  [args]
  (loop [args args
         opts {}]
    (if (empty? args)
      opts
      (let [arg (first args)]
        (cond
          (= "--threshold" arg)
          (if-let [v (second args)]
            (let [n (parse-double v)]
              (if (and n (not (neg? n)))
                (recur (drop 2 args) (assoc opts :threshold n))
                (throw (ex-info (str "Invalid threshold: " v) {}))))
            (throw (ex-info "--threshold requires a number" {})))
          :else (throw (ex-info (str "Unknown argument: " arg) {})))))))

(defn -main
  "Entry point for the benchmark coverage analyzer.

   Usage:
     clojure -M:bench-coverage
     clojure -M:bench-coverage --threshold 40

   Exits with code 1 if validation fails or the threshold is not met."
  [& args]
  (try
    (let [opts (parse-args args)
          source-ns (source-namespaces)
          groups bench/benchmark-groups
          errors (validate-registry groups source-ns)]
      (when (seq errors)
        (println "Benchmark registry validation errors:")
        (doseq [e errors]
          (println "  " e))
        (System/exit 1))
      (let [relevant-ns (relevant-source-namespaces source-ns)
            metrics (print-report groups relevant-ns)]
        (when-let [threshold (:threshold opts)]
          (when (< (:total-pct metrics) threshold)
            (println)
            (println (format "FAIL: coverage %.1f%% is below threshold %.1f%%"
                             (:total-pct metrics) threshold))
            (System/exit 1)))
        (println)
        (println (format "Coverage: %.1f%%" (:total-pct metrics)))
        (System/exit 0)))
    (catch Exception e
      (println "Error:" (ex-message e))
      (System/exit 1))))

(comment
  (def source-ns (source-namespaces))
  (def groups bench/benchmark-groups)
  (validate-registry groups source-ns)
  (coverage-metrics source-ns groups)
  (print-report groups source-ns))
