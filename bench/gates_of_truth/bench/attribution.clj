(ns gates-of-truth.bench.attribution
  "WORKING-TREE ONLY (do not commit): attribute the ~11ms gap between
   sum-of-named-systems and full tick-world @500.

   Isolates: spatial-index, build-physics-soa, per-system :run sum (ALL
   systems), fold cost, future/fan-out overhead, materialize-lifecycle,
   promotion/handoff/commitment emits, summary/stats/pacing/phase-events."
  (:require
   [domain.genesis.bootstrap :as bootstrap]
   [domain.genesis.summary :as summary]
   [domain.genesis.systems :as systems]
   [domain.genesis.tick :as gtick]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.tick :as tick]
   [domain.physics.cache :as pcache]
   [domain.spatial.index :as spatial]
   [domain.intervention :as intervention]
   [domain.stellar.classifier :as classifier]
   [domain.player :as player]
   [domain.pacing :as pacing]
   [domain.ecology :as ecology]
   [domain.voxel.sculpt :as sculpt]))

(defn- make-medium-world []
  (bootstrap/create-world {:gas-count 500 :nebula-mass 2e30 :nebula-radius 1.5e16}))

(defn- ms [nanos] (/ (double nanos) 1e6))

(defn- time-n
  "Mean ms over n runs of (f), after warm runs."
  [f n warm]
  (dotimes [_ warm] (f))
  (let [t0 (System/nanoTime)]
    (dotimes [_ n] (f))
    (/ (ms (- (System/nanoTime) t0)) n)))

(defn run-attribution []
  (let [w0 (make-medium-world)
        ;; GLOBAL JIT warmup: fully exercise every code path the tick touches
        ;; before any measurement, mirroring criterium's warmup.
        _ (println "warming up (60 ticks)...")
        _ (reduce (fn [w _] (gtick/tick-world w)) w0 (range 60))
        w (make-medium-world)
        ;; world1: tick-advanced + spatial tree (the step-physics input)
        build-world1 (fn [] (-> (ecs/advance-tick w) spatial/spatial-index))
        w1 (build-world1)
        systems (systems/physics-systems-parallel w1)
        w1-cached (-> w1 ecs/with-query-cache pcache/build-physics-soa)]

    ;; ---- segment timings (means over runs) ----
    (let [t-advance+spatial (time-n build-world1 10 3)
          t-soa (time-n #(pcache/build-physics-soa (ecs/with-query-cache w1)) 10 3)
          ;; per-system :run, sequentially, on the same cached snapshot the
          ;; fan-out sees
          per-system
          (mapv (fn [{:keys [id run]}]
                  [id (time-n #(run w1-cached) 5 2)])
                systems)
          sum-systems (reduce + (map second per-system))
          ;; sequential: systems + fold, no futures
          t-sequential (time-n #(tick/run-sequential w1-cached systems) 10 3)
          ;; parallel: futures + systems + fold (the production fan-out)
          t-parallel (time-n #(tick/run-parallel w1-cached systems) 10 3)
          ;; fold alone: precompute write-sets, then time the fold
          wsets (mapv (fn [{:keys [id run]}] [id (run w1-cached)]) systems)
          t-fold (time-n #(tick/fold w1-cached wsets) 10 3)
          total-cells (reduce + (map (fn [[_ ws]]
                                       (reduce + (map (fn [[_ m]] (count m)) ws)))
                                     wsets))
          ;; conflict check alone
          t-conflict (time-n #(tick/colliding-ctypes wsets) 20 5)
          ;; step-physics total (production shape)
          t-step-physics (time-n #(gtick/step-physics w1) 10 3)
          ;; post-physics serial stages
          w2 (gtick/step-physics w1)
          t-expire+sculpt (time-n #(-> w2 intervention/expire-interventions
                                       sculpt/clear-sculpt-ops) 10 3)
          t-materialize (time-n #(bootstrap/materialize-lifecycle w2) 10 3)
          t-promotions (time-n #(gtick/emit-promotion-events w2 w1) 10 3)
          t-handoff (time-n #(gtick/emit-handoff-event w2) 20 5)
          t-commitment (time-n #(gtick/emit-commitment-event w2) 20 5)
          t-summary (time-n #(summary/system-summary w2) 10 3)
          summ (summary/system-summary w2)
          t-complexity (time-n #(classifier/complexity-score summ) 20 5)
          t-stats (time-n #(summary/stats-of w2 summ) 10 3)
          t-pacing (time-n #(pacing/pace w2 (classifier/complexity-score summ)) 10 3)
          t-phase-events (time-n #(ecology/emit-phase-events w2 w1) 10 3)
          t-tick-world (time-n #(gtick/tick-world w) 10 3)

          ;; --- fan-out anatomy ---
          ;; spawn+barrier+fold with EMPTY write-sets: pure orchestration cost
          noop-systems (mapv (fn [{:keys [id]}] {:id id :run (fn [_] {})}) systems)
          t-parallel-noop (time-n #(tick/run-parallel w1-cached noop-systems) 20 5)
          ;; spawn-only / barrier-only split on the REAL systems
          t-spawn (time-n #(let [futs (mapv (fn [{:keys [run]}] (future (run w1-cached))) systems)]
                             (run! deref futs))
                          10 3)
          ;; top-5 longest systems alone in parallel: ideal critical path
          big5 (->> per-system (sort-by second >) (take 5) (map first) set)
          big5-systems (filterv #(big5 (:id %)) systems)
          t-parallel-big5 (time-n #(tick/run-parallel w1-cached big5-systems) 10 3)
          ;; parallel WITHOUT the CHM query cache (fallback live scans)
          w1-nocache (pcache/build-physics-soa w1)
          t-parallel-nocache (time-n #(tick/run-parallel w1-nocache systems) 10 3)
          ;; parallel with a FRESH (cold) CHM each run — the production shape:
          ;; every tick attaches a brand-new query cache, so the fan-out itself
          ;; pays every first-compute, concurrently, through CHM.computeIfAbsent
          t-parallel-coldcache (time-n #(tick/run-parallel
                                         (-> w1 ecs/with-query-cache pcache/build-physics-soa)
                                         systems)
                                       10 3)
          ;; sequential with a FRESH cold cache each run (control)
          t-sequential-coldcache (time-n #(tick/run-sequential
                                           (-> w1 ecs/with-query-cache pcache/build-physics-soa)
                                           systems)
                                         10 3)
          ;; OVERSUBSCRIPTION PROBE: force every INNER par-mapv sequential while
          ;; keeping the OUTER system fan-out parallel. If the big systems each
          ;; spawning ~15 inner futures is the contention tax, this collapses
          ;; the parallel wall time toward the big-5 critical path.
          t-parallel-inner-seq (with-redefs [domain.ecs.parallel/par-mapv
                                             (fn [f coll] (mapv f coll))]
                                 (time-n #(tick/run-parallel w1-cached systems) 10 3))
          ;; GC pressure proxy: allocation-heavy? measure parallel with serial GC disabled is
          ;; out of scope; instead measure sequential on COLD snapshot (fresh world each time
          ;; is too slow) — skip.
          ]

      {:attribution
       {:advance-tick+spatial-index t-advance+spatial
        :build-physics-soa+query-cache t-soa
        :systems-sum-sequential sum-systems
        :run-sequential-total t-sequential
        :run-parallel-total t-parallel
        :fold-alone t-fold
        :conflict-check-alone t-conflict
        :write-set-cells-total total-cells
        :step-physics-total t-step-physics
        :expire+sculpt t-expire+sculpt
        :materialize-lifecycle t-materialize
        :emit-promotion-events t-promotions
        :emit-handoff t-handoff
        :emit-commitment t-commitment
        :system-summary t-summary
        :complexity-score t-complexity
        :stats-of t-stats
        :pacing t-pacing
        :ecology-phase-events t-phase-events
        :tick-world-total t-tick-world
        :fanout-noop-orchestration t-parallel-noop
        :fanout-spawn+barrier-real t-spawn
        :fanout-parallel-big5-only t-parallel-big5
        :fanout-parallel-no-query-cache t-parallel-nocache
        :fanout-parallel-cold-cache t-parallel-coldcache
        :fanout-sequential-cold-cache t-sequential-coldcache
        :fanout-parallel-inner-sequential t-parallel-inner-seq}
       :derived
       {:future-overhead (max 0.0 (- t-parallel t-sequential))
        :sequential-residual (- t-sequential sum-systems t-fold)
        :step-physics-overhead (- t-step-physics t-soa t-parallel)
        :post-physics-serial (+ t-expire+sculpt t-materialize t-promotions
                                t-handoff t-commitment t-summary t-complexity
                                t-stats t-pacing t-phase-events)
        :tick-gap (- t-tick-world t-advance+spatial t-step-physics)}
       :per-system (sort-by second > per-system)})))

(defn -main []
  (let [{:keys [attribution derived per-system]} (run-attribution)]
    (println "\n=== ATTRIBUTION @500 (ms, means) ===")
    (doseq [[k v] attribution]
      (println (format "  %-36s %8.3f" (name k) (double v))))
    (println "\n=== DERIVED ===")
    (doseq [[k v] derived] (println (format "  %-36s %8.3f" (name k) v)))
    (println "\n=== PER-SYSTEM :run (all systems, sorted) ===")
    (doseq [[id v] per-system] (println (format "  %-40s %8.3f" (str id) v)))))
