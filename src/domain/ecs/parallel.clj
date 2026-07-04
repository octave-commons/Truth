(ns domain.ecs.parallel
  "Deterministic parallel map for ECS systems.

   Pure ECS systems compute an independent result per entity, then fold those
   results back into the world. The per-entity computation is embarrassingly
   parallel; only the fold is sequential. `par-mapv` runs `f` over a collection
   in chunked futures across cores and returns results in original order, so the
   outcome is independent of scheduling — same contract as `mapv`.")

(def ^:private n-cores
  (max 1 (.availableProcessors (Runtime/getRuntime))))

(def ^:private parallel-threshold
  "Below this many items the future/threadpool overhead outweighs the work, so
   fall back to a plain sequential mapv. Kept low (with `min-chunk-size` as the
   real granularity guard) so late-game per-state populations — a few hundred
   resolved bodies — still fan out instead of running serially
   (docs/specs/perf-60fps-parallel-tick.md, Fix 4)."
  64)

(def ^:private min-chunk-size
  "Never create chunks smaller than this; tiny chunks dilute the future overhead."
  32)

(defn par-mapv
  "Like `mapv` but evaluates `f` in parallel chunks. `f` must be pure (no shared
   mutable state). Order is preserved. Chunk size targets enough tasks to saturate
   the available cores for typical ECS workloads."
  [f coll]
  (let [items (vec coll)
        n     (count items)]
    (if (< n parallel-threshold)
      (mapv f items)
      (let [chunks (max 1 (min n-cores (quot n min-chunk-size)))
            size   (long (Math/ceil (/ (double n) chunks)))
            futs   (mapv (fn [part] (future (mapv f part)))
                         (partition-all size items))]
        (into [] (mapcat deref) futs)))))
