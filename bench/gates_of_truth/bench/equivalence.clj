(ns gates-of-truth.bench.equivalence
  "WORKING-TREE ONLY (do not commit): equivalence + before/after delta for the
   completion-order overlapped fold in domain.ecs.tick/run-parallel.

   Evidence contract (persistent-neighbor-cache.md §4 pattern): the new fold
   must produce the SAME world as the old declaration-order fold — verified by
   value-equality over N consecutive ticks, from multiple seeds, with subsystem
   profiling both off and on."
  (:require
   [domain.genesis.bootstrap :as bootstrap]
   [domain.genesis.tick :as gtick]
   [domain.ecs.tick :as tick]
   [domain.ecs.core :as ecs]
   [domain.spatial.index :as spatial]
   [domain.physics.cache :as pcache]
   [domain.genesis.systems :as systems]))

(defn- run-parallel-ordered
  "The PRE-CHANGE implementation of run-parallel (declaration-order deref +
   fold), kept here verbatim as the reference."
  [world systems]
  (let [futs  (mapv (fn [{:keys [id run]}] [id (future (run world))]) systems)
        wsets (mapv (fn [[id f]] [id (deref f)]) futs)]
    (tick/fold world wsets :on-conflict :throw)))

(defn- make-world [gas-count]
  (bootstrap/create-world {:gas-count gas-count
                           :nebula-mass 2e30
                           :nebula-radius 1.5e16}))

(defn- tick-n [f w n]
  (reduce (fn [w _] (f w)) w (range n)))

(defn- normalize
  "Strip inherently nondeterministic content: event UUIDs (random per dispatch,
   even for identical physics) and :genesis/_profile (wall-clock timings)."
  [w]
  (-> w
      (dissoc :genesis/_profile)
      (update :ledger (fn [l] (update l :events (fn [evts] (mapv #(dissoc % :id) evts)))))))

(defn equivalence-report []
  (let [n-ticks 12]
    (doseq [profile? [false true]
            gas [100 500 1000]]
      (let [w0 (cond-> (make-world gas)
                 profile? (assoc :genesis/profile-subsystems? true))
            w-new (tick-n gtick/tick-world w0 n-ticks)
            w-ref (with-redefs [tick/run-parallel
                                (fn [world systems & {:keys [on-conflict]
                                                      :or {on-conflict :throw}}]
                                  (assert (= on-conflict :throw))
                                  (run-parallel-ordered world systems))]
                    (tick-n gtick/tick-world w0 n-ticks))
            same? (= (normalize w-new) (normalize w-ref))]
        (println (format "  gas=%-5d profile=%-5s ticks=%d  identical=%s"
                         gas profile? n-ticks same?))
        (when-not same?
          (println "    components identical?"
                   (= (:components w-new) (:components w-ref)))
          (println "    top-level differing keys (post-normalize):"
                   (filter #(not= (get (normalize w-new) %) (get (normalize w-ref) %))
                           (into #{} (concat (keys w-new) (keys w-ref))))))))))

(defn- ms [nanos] (/ (double nanos) 1e6))

(defn- time-n [f n warm]
  (dotimes [_ warm] (f))
  (let [t0 (System/nanoTime)]
    (dotimes [_ n] (f))
    (/ (ms (- (System/nanoTime) t0)) n)))

(defn delta-report []
  (println "warming up (60 ticks)...")
  (reduce (fn [w _] (gtick/tick-world w)) (make-world 500) (range 60))
  (let [w (make-world 500)
        w1 (-> w ecs/advance-tick spatial/spatial-index)
        systems (systems/physics-systems-parallel w1)
        w1c (-> w1 ecs/with-query-cache pcache/build-physics-soa)
        new-t (time-n #(tick/run-parallel w1c systems) 20 5)
        old-t (time-n #(run-parallel-ordered w1c systems) 20 5)
        tick-new (time-n #(gtick/tick-world w) 20 5)
        tick-old (with-redefs [tick/run-parallel
                               (fn [world systems & _]
                                 (run-parallel-ordered world systems))]
                   (time-n #(gtick/tick-world w) 20 5))]
    (println (format "  run-parallel @500:  old %.3f ms   new %.3f ms   delta %.3f ms"
                     old-t new-t (- new-t old-t)))
    (println (format "  tick-world   @500:  old %.3f ms   new %.3f ms   delta %.3f ms"
                     tick-old tick-new (- tick-new tick-old)))))

(defn -main []
  (println "=== EQUIVALENCE (new completion-order fold vs old ordered fold) ===")
  (equivalence-report)
  (println "\n=== BEFORE/AFTER DELTA (same JVM, interleaved) ===")
  (delta-report))
