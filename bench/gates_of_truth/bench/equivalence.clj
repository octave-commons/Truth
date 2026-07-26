(ns gates-of-truth.bench.equivalence
  "WORKING-TREE ONLY (do not commit): equivalence + before/after delta for the
   completion-order overlapped fold in domain.ecs.tick/run-parallel.

   Evidence contract (persistent-neighbor-cache.md §4 pattern): the new fold
   must produce the SAME world as the old declaration-order fold — verified by
   value-equality over N consecutive ticks, from multiple seeds, with subsystem
   profiling both off and on.

   2026-07-22: extended with the WINDOWED-EQUIVALENCE contract for the
   staleness-budgeted shared pair walk (kanban/tasks/perf-big5-shared-neighbor-pass.md,
   owner decision: byte-equivalence explicitly relaxed). `windowed-equivalence-report`
   runs the default staleness budget against the fresh-density reference
   (:genesis/density-stale-max-ticks 1) in lockstep over a tick window and
   reports the max relative drift per quantity against the documented epsilons
   in `windowed-epsilons`."
  (:require
   [domain.ecs.components :as c]
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

;; ---------------------------------------------------------------------------
;; Windowed equivalence: staleness-budgeted shared walk vs fresh-density
;; reference (perf-big5-shared-neighbor-pass).
;;
;; Contract: over a 48-tick window the budgeted world must track the
;; fresh-density reference within these per-quantity RELATIVE epsilons, and
;; the drift must be BOUNDED (no monotonic blow-up — stale density lag, not
;; chaotic divergence). Epsilon rationale:
;;   positions/velocities 1e-3 — density lag ≤ 4 ticks feeds force lag; over
;;     48 ticks the trajectory drift is second-order small.
;;   masses 1e-9 — mass channels do not read density on this timescale;
;;     anything larger signals a real coupling bug, not staleness.
;;   density/pressure/radius/temperature 5e-2 — a ≤ density-stale-max-ticks
;;     (4)-tick-old SPH estimate in a collapsing nebula; generous upper bound,
;;     expected observed drift is ≪ this (see report output).
;; ---------------------------------------------------------------------------

(def windowed-epsilons
  "The documented per-quantity relative epsilons of the windowed-equivalence
   contract (owner decision 2026-07-22). See the ns-level comment for the
   rationale per quantity."
  {c/position    1e-3
   c/velocity    1e-3
   c/mass        1e-9
   c/density     5e-2
   c/pressure    5e-2
   c/radius      5e-2
   c/temperature 5e-2})

(def ^:private window-pts
  "Tick offsets (1-based) whose drift values are printed as the boundedness
   series — the full window's max is always reported regardless."
  [1 2 4 8 12 16 20 24 28 32 36 40 44 48])

(defn- rel-drift
  "Relative drift |a−b|/|b|; 0 when both are 0, ##Inf when only b is 0."
  [a b]
  (let [a (double a) b (double b)]
    (cond (zero? b) (if (zero? a) 0.0 ##Inf)
          :else     (Math/abs (/ (- a b) b)))))

(defn- val-drift
  "Relative drift between scalar or vec3 component values."
  [a b]
  (if (sequential? a)
    (reduce (fn [m [x y]] (max m (rel-drift x y))) 0.0 (map vector a b))
    (rel-drift a b)))

(defn- world-drift
  "Max relative drift per quantity between worlds `wa` (budgeted) and `wb`
   (reference) over their SHARED entities, plus the count of entities present
   in only one world (set divergence)."
  [wa wb]
  (into {}
        (map (fn [[ct _eps]]
               (let [ma (get-in wa [:components ct] {})
                     mb (get-in wb [:components ct] {})
                     shared (filter #(contains? mb %) (keys ma))
                     set-div (+ (count (remove #(contains? mb %) (keys ma)))
                                (count (remove #(contains? ma %) (keys mb))))
                     d (reduce (fn [m eid]
                                 (max m (val-drift (get ma eid) (get mb eid))))
                               0.0 shared)]
                 [ct {:max-rel d :set-divergence set-div}])))
        windowed-epsilons))

(defn- merge-drift-max
  "Fold a per-tick drift report into the running per-quantity maxima."
  [maxes drift]
  (merge-with (fn [a b] {:max-rel (max (:max-rel a) (:max-rel b))
                         :set-divergence (max (:set-divergence a)
                                              (:set-divergence b))})
              maxes drift))

(defn windowed-equivalence-report
  "Run the staleness-budgeted shared walk and the fresh-density reference
   (:genesis/density-stale-max-ticks 1) in lockstep over a 48-tick window at
   {100,500,1000} particles. Prints the drift series at `window-pts` ticks
   (boundedness evidence) and the per-quantity max drift against
   `windowed-epsilons` (contract verdict)."
  []
  (let [n-ticks 48]
    (doseq [gas [100 500 1000]]
      (let [w0 (make-world gas)]
        (println (format "\n  gas=%-5d ticks=%d  (budgeted vs fresh-density reference)"
                         gas n-ticks))
        (println "    tick |   density drift |  position drift |   radius drift")
        (loop [i      0
               w-bud  w0
               w-ref  (assoc w0 :genesis/density-stale-max-ticks 1)
               maxes  {}]
          (if (>= i n-ticks)
            (do
              (println "    ---- contract verdict (max relative drift over window) ----")
              (doseq [[ct eps] windowed-epsilons]
                (let [{:keys [max-rel set-divergence]} (get maxes ct)
                      verdict (cond (pos? (long set-divergence)) "SET-DIVERGED"
                                    (<= max-rel eps)             "PASS"
                                    :else                        "FAIL")]
                  (println (format "    %-24s max=%.3e  eps=%.0e  %s"
                                   (name ct) (double max-rel) (double eps) verdict)))))
            (let [w-bud' (gtick/tick-world w-bud)
                  w-ref' (gtick/tick-world w-ref)
                  drift  (world-drift w-bud' w-ref')]
              (when (some #(= (inc i) %) window-pts)
                (println (format "    %4d | %15.3e | %15.3e | %13.3e"
                                 (inc i)
                                 (double (get-in drift [c/density :max-rel]))
                                 (double (get-in drift [c/position :max-rel]))
                                 (double (get-in drift [c/radius :max-rel])))))
              (recur (inc i) w-bud' w-ref' (merge-drift-max maxes drift)))))))))


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
  (println "\n=== WINDOWED EQUIVALENCE (staleness budget vs fresh-density reference) ===")
  (windowed-equivalence-report)
  (println "\n=== BEFORE/AFTER DELTA (same JVM, interleaved) ===")
  (delta-report))
