(ns gates-of-truth.bench
  "Benchmarking suite for Gates of Truth performance analysis.

   Identifies bottlenecks in the ECS substrate, physics pipeline, and full
   Phase 0 tick. Uses Criterium for statistically rigorous measurements with
   JVM warmup, GC pauses, and outlier handling.

   Run: clj -M:bench
   Run specific group: clj -M:bench :ecs
   Run with profile: clj -M:bench :profile"
  (:require
   [criterium.core :as crit])
  (:import [java.lang.management ManagementFactory]))

;; ---------------------------------------------------------------------------
;; Reporting
;; ---------------------------------------------------------------------------

(defn- separator []
  (println)
  (println (apply str (repeat 72 "=")))
  (println))

(defn- group-header [name]
  (separator)
  (println (format "  BENCHMARK GROUP: %s" name))
  (separator))

(defn- print-system-info []
  (let [rt    (ManagementFactory/getRuntimeMXBean)
        mem   (ManagementFactory/getMemoryMXBean)
        heap  (.getHeapMemoryUsage mem)]
    (println "=== System Info ===")
    (println (format "  JVM:      %s %s" (System/getProperty "java.vm.name")
                      (System/getProperty "java.version")))
    (println (format "  Cores:    %d" (.availableProcessors (Runtime/getRuntime))))
    (println (format "  Heap:     %d MB allocated" (/ (.getMax heap) (* 1024 1024))))
    (println (format "  PID:      %s" (.getName rt)))
    (println)))

(defn- format-time
  "Format a time in seconds to a human-readable string."
  [t]
  (cond
    (< t 1.0e-6) (format "%.1f ns" (* t 1.0e9))
    (< t 1.0e-3) (format "%.1f μs" (* t 1.0e6))
    (< t 1.0)    (format "%.1f ms" (* t 1.0e3))
    :else         (format "%.2f s" t)))

(defn- safe-format-time
  "Format a time in seconds to a human-readable string. Handles nil/missing values."
  [t]
  (if (nil? t)
    "N/A"
    (let [t (double t)]
      (cond
        (< t 1.0e-6) (format "%.1f ns" (* t 1.0e9))
        (< t 1.0e-3) (format "%.1f μs" (* t 1.0e6))
        (< t 1.0)    (format "%.1f ms" (* t 1.0e3))
        :else         (format "%.2f s" t)))))

(defn- quick-bench
  "Run a criterium quick-bench (fewer samples, faster feedback)."
  [label f]
  (println (format "\n--- %s ---" label))
  (let [result (crit/quick-benchmark* f {})
        mean (:mean result)
        std (:std result)
        lq (:lower-q result)
        uq (:upper-q result)]
    (println (format "  Mean:   %s" (safe-format-time (first mean))))
    (when std (println (format "  Std:    %s" (safe-format-time (first std)))))
    (when (and lq uq)
      (println (format "  Range:  %s — %s"
                       (safe-format-time (first lq))
                       (safe-format-time (first uq)))))
    result))

(defn- full-bench
  "Run a criterium full benchmark (rigorous, slower)."
  [label f]
  (println (format "\n--- %s ---" label))
  (let [result (crit/benchmark* f {})
        mean (:mean result)
        std (:std result)
        lq (:lower-q result)
        uq (:upper-q result)]
    (println (format "  Mean:   %s" (safe-format-time (first mean))))
    (when std (println (format "  Std:    %s" (safe-format-time (first std)))))
    (when (and lq uq)
      (println (format "  Range:  %s — %s"
                       (safe-format-time (first lq))
                       (safe-format-time (first uq)))))
    (println (format "  Outliers: %s" (:outlier-variance result)))
    result))

;; ---------------------------------------------------------------------------
;; Benchmark registry
;; ---------------------------------------------------------------------------

(defn- resolve-bench-fn
  "Resolve the benchmark run function for a group, loading the namespace on demand."
  [group-name]
  (let [ns-sym (symbol (str "gates-of-truth.bench." (name group-name)))]
    (require ns-sym)
    (ns-resolve (the-ns ns-sym) 'run)))

(def benchmark-groups
  "Map of group keyword → {:label fn :bench-fn var}."
   {:ecs       {:label "ECS Core Operations"      :ns 'gates-of-truth.bench.ecs}
    :gravity   {:label "Barnes-Hut Gravity"        :ns 'gates-of-truth.bench.gravity}
    :collision {:label "Collision Detection"       :ns 'gates-of-truth.bench.collision}
    :hydro     {:label "SPH Hydrodynamics"         :ns 'gates-of-truth.bench.hydro}
    :em        {:label "Electromagnetic Fields"    :ns 'gates-of-truth.bench.em}
    :tick      {:label "Double-Buffer Tick"        :ns 'gates-of-truth.bench.tick}
    :spatial   {:label "Spatial Index Queries"     :ns 'gates-of-truth.bench.spatial}
    :render    {:label "Renderer / Graphics"       :ns 'gates-of-truth.bench.render}
    :phase0    {:label "Full Phase 0 Tick"         :ns 'gates-of-truth.bench.phase0}})

(def group-order
  "Execution order: ECS first (foundational), then physics, then integration."
  [:ecs :spatial :gravity :collision :hydro :em :tick :phase0 :render])

;; ---------------------------------------------------------------------------
;; Profile mode: flame-graph-friendly sampling
;; ---------------------------------------------------------------------------

(defn- run-profile
  "Run each benchmark group's setup + a fixed iteration count for async-profiler
   or JFR sampling. No criterium stats — just hot-loop execution."
  [groups]
  (println "\n=== PROFILE MODE (no stats, hot-loop only) ===")
  (println "Attach async-profiler or JFR to PID:"
           (.getName (ManagementFactory/getRuntimeMXBean)))
  (doseq [k groups]
    (let [{:keys [label ns]} (get benchmark-groups k)]
      (group-header label)
      (require ns)
      (let [setup-fn (ns-resolve (the-ns ns) 'profile-iterations)]
        (if setup-fn
          (dotimes [_ 1000] (setup-fn))
          (println "  No profile-iterations defined for" k))))))

;; ---------------------------------------------------------------------------
;; Main
;; ---------------------------------------------------------------------------

(defn -main
  "Entry point. Args are group keywords: :ecs :gravity :collision :hydro :em
   :tick :spatial :phase0. With no args, runs all groups in order.
   :profile flag enables hot-loop mode for external profilers."
  [& args]
  (let [profile?   (some #{"profile"} args)
        group-args (remove #{"profile"} args)
        groups     (if (seq group-args)
                     (mapv (fn [a] (keyword (if (.startsWith ^String a ":")
                                            (subs a 1)
                                            a)))
                           group-args)
                     group-order)]
    (print-system-info)
    (println (format "Running %d benchmark group(s): %s"
                     (count groups) (mapv name groups)))
    (when profile?
      (println "(Profile mode: attach async-profiler/JFR now)"))

    (if profile?
      (run-profile groups)
      (doseq [k groups]
        (let [{:keys [label ns]} (get benchmark-groups k)]
          (require ns)
          (let [run-fn (ns-resolve (the-ns ns) 'run)]
            (group-header label)
            (run-fn quick-bench full-bench)))))

    (separator)
    (println "=== BENCHMARK COMPLETE ===")
    (println)
    (println "Interpretation guide:")
    (println "  - ECS core ops should be < 1μs (nanosecond ideally)")
    (println "  - Barnes-Hut tree build: O(N log N), check scaling with N")
    (println "  - Gravity acceleration: dominates tick cost at high N")
    (println "  - SPH density: check neighbor query scaling")
    (println "  - Full tick: target 16.6ms for 60 Hz (1000 gas particles)")
    (println "  - Parallel speedup: compare :tick parallel vs sequential")
    (println)
    (println "Bottleneck identification:")
    (println "  - If tree build >> acceleration: tree overhead is the issue")
    (println "  - If SPH >> hydro force: neighbor finding is the bottleneck")
    (println "  - If parallel ≈ sequential: write-set contention or thread starvation")
    (println "  - If full tick >> sum of parts: serialization/barrier overhead")))

;; Convenience for REPL use
(comment
  (quick-bench "test" #(+ 1 1))
  (bench-ecs/run quick-bench full-bench)
  (bench-gravity/run quick-bench full-bench))
