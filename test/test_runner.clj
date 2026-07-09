#_{:splint/disable [naming/single-segment-namespace]}
(ns test-runner
  "Lightweight, group-aware test runner.

   Loads only the namespaces that match the selected group(s), so targeted
   runs do not pay the cost of loading the entire test tree. Complements the
   existing cognitect test-runner (alias :test) which always loads every test
   namespace.

   Examples:
     clojure -M:test:test-runner -g domain
     clojure -M:test:test-runner -g integration
     clojure -M:test:test-runner -g domain -g render
     clojure -M:test:unit-test            ; alias for -g unit"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :as t])
  (:import
   [java.io File]))

(defn- ns-from-file
  "Convert a file under `test-dir` into a namespace symbol."
  [^File test-dir ^File f]
  (let [rel (str/replace (.getPath f) (str (.getPath test-dir) File/separator) "")]
    (when (str/ends-with? rel ".clj")
      (-> rel
          (str/replace #"\.clj$" "")
          (str/replace (re-pattern File/separator) ".")
          (str/replace #"_" "-")
          symbol))))

(defn- all-test-namespaces
  "All namespaces declared under the test/ directory, excluding the runner."
  []
  (let [test-dir (io/file "test")]
    (->> (file-seq test-dir)
         (filter #(.isFile ^File %))
         (keep #(ns-from-file test-dir %))
         (remove #{'test-runner})
         distinct
         sort)))

(def ^:private integration-namespaces
  "Simulation/integration tests that are intentionally slow or heavy."
  #{'benchmark-coverage-test
    'domain.arc-test
    'domain.condensation-seeder-test
    'domain.dominant-star-test
    'domain.ecs.parallel-integration-test
    'domain.formation-integration-test
    'domain.genesis-test})

(defn- ns-matches?
  "Does namespace symbol `ns-sym` belong to `group`?"
  [ns-sym group]
  (let [s (str ns-sym)]
    (case group
      "all" true
      "unit" true
      "fast" (not (integration-namespaces ns-sym))
      "integration" (integration-namespaces ns-sym)
      "domain" (str/starts-with? s "domain.")
      "infra" (str/starts-with? s "infra.")
      "law" (str/starts-with? s "law.")
      "shape" (str/starts-with? s "shape.")
      "render" (or (str/starts-with? s "infra.render.")
                   (= s "infra.appearance-test")
                   (= s "infra.dev.window-test"))
      "architecture" (= s "architecture-test")
      false)))

(defn- selected-namespaces
  [groups]
  (let [all (all-test-namespaces)]
    (distinct
     (for [ns-sym all
           group groups
           :when (ns-matches? ns-sym group)]
       ns-sym))))

(defn- run-ns
  "Require and run a single test namespace."
  [ns-sym]
  (require ns-sym)
  (t/test-ns (find-ns ns-sym)))

(defn- summarize
  "Aggregate per-ns summaries and print the final result."
  [results]
  (let [totals (reduce (fn [acc m]
                         (merge-with + acc (select-keys m [:test :pass :fail :error])))
                       {:test 0 :pass 0 :fail 0 :error 0}
                       results)]
    (println (format "\nRan %d tests containing %d assertions."
                     (:test totals) (:pass totals)))
    (println (format "%d failures, %d errors." (:fail totals) (:error totals)))
    (if (and (zero? (:fail totals)) (zero? (:error totals)))
      0
      1)))

(defn- usage
  [msg]
  (println msg)
  (println "Usage: clojure -M:test:<alias> | clojure -M:test:test-runner -g <group> ...")
  (println "Groups: all, unit, fast, integration, domain, infra, law, shape, render, architecture")
  (System/exit 1))

(defn -main
  "Entry point for group-aware test runs."
  [& args]
  (if (or (empty? args) (some #{"-h" "--help"} args))
    (usage "Run tests by group.")
    (let [parsed (loop [[flag & more] args groups []]
                   (cond
                     (nil? flag)
                     groups

                     (or (= flag "-g") (= flag "--group"))
                     (if (seq more)
                       (recur (rest more) (conj groups (first more)))
                       (usage "Missing group after -g"))

                     :else
                     (usage (str "Unknown flag: " flag))))
          groups (if (seq parsed) parsed ["all"])
          selected (selected-namespaces groups)]
      (when (empty? selected)
        (println "No namespaces matched the selected groups.")
        (System/exit 0))
      (println (format "Running groups: %s" (str/join ", " groups)))
      (println (format "Selected namespaces: %d" (count selected)))
      (System/exit (summarize (doall (map run-ns selected)))))))
