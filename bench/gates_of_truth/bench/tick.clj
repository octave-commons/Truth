(ns gates-of-truth.bench.tick
  "Double-buffer tick pipeline benchmarks.

   Tests the core parallel execution infrastructure:
   1. Write-set construction and folding
   2. Parallel vs sequential fan-out
   3. Legacy system bridge (diff-write-set)
   4. Single-writer conflict detection
   5. Full parallel tick pipeline"
  (:require
   [domain.ecs.tick      :as tick]
   [domain.ecs.core      :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.parallel  :as par]
   [shape.spatial        :as sp]))

;; ---------------------------------------------------------------------------
;; Test data
;; ---------------------------------------------------------------------------

(defn- make-world-with-components
  "World with N entities and M component types, for write-set tests."
  [n m]
  (let [world (ecs/empty-world)
        ckeys (take m [c/position c/velocity c/mass c/radius c/temperature
                       c/density c/pressure c/b-field c/matter-state])]
    (reduce (fn [w i]
              (let [[w' eid] (ecs/spawn w)]
                (reduce (fn [w'' ck]
                          (let [val (condp = ck
                                      c/position    [(double i) 0.0 0.0]
                                      c/velocity    [0.0 0.0 0.0]
                                      c/mass        1.0e20
                                      c/radius      1.0e6
                                      c/temperature 300.0
                                      c/density     1000.0
                                      c/pressure    1.0e5
                                      c/b-field     [0.0 0.0 1.0e-9]
                                      c/matter-state :nebula
                                      nil)]
                            (if val
                              (ecs/put-components w'' eid {ck val})
                              w'')))
                        w'
                        ckeys)))
            world
            (range n))))

;; ---------------------------------------------------------------------------
;; Benchmarks
;; ---------------------------------------------------------------------------

(defn run [quick-bench full-bench]
  (let [w100   (make-world-with-components 100 9)
        w1000  (make-world-with-components 1000 9)]

    ;; --- Write-set operations ---
    (let [ws {c/position (into {} (map (fn [eid] [eid [(rand) (rand) (rand)]])
                                       (range 100)))
              c/velocity (into {} (map (fn [eid] [eid [(rand) (rand) (rand)]])
                                       (range 100)))}]

      (quick-bench "apply-write-set (200 entries across 2 ctypes)"
        (fn [] (tick/apply-write-set w100 ws)))

      (quick-bench "apply-write-set (2000 entries across 2 ctypes)"
        (fn [] (tick/apply-write-set w1000
                 {c/position (into {} (map (fn [eid] [eid [(rand) (rand) (rand)]])
                                           (range 1000)))
                  c/velocity (into {} (map (fn [eid] [eid [(rand) (rand) (rand)]])
                                           (range 1000)))}))))

    ;; --- Write-set folding ---
    (let [wsets [[:system-a {c/position {0 [1.0 0.0 0.0] 1 [2.0 0.0 0.0]}}]
                 [:system-b {c/velocity {0 [0.1 0.0 0.0] 1 [0.2 0.0 0.0]}}]
                 [:system-c {c/mass {0 1.0e30 1 2.0e30}}]]]

      (quick-bench "fold 3 write-sets (4 entities)"
        (fn [] (tick/fold w100 wsets)))

      (quick-bench "colliding-ctypes check (3 systems, no conflict)"
        (fn [] (tick/colliding-ctypes wsets))))

    ;; --- Conflict detection ---
    (let [conflicting [[:sys-a {c/position {0 [1.0 0.0 0.0]}}]
                       [:sys-b {c/position {0 [2.0 0.0 0.0]}}]]]
      (quick-bench "colliding-ctypes (conflict present)"
        (fn [] (tick/colliding-ctypes conflicting))))

    ;; --- Legacy bridge ---
    (let [simple-sys (fn [world]
                        (reduce (fn [w eid]
                                  (ecs/put-component w eid c/temperature
                                    (+ (or (ecs/get-component w eid c/temperature) 0.0) 1.0)))
                                world
                                (take 50 (ecs/entities-with world c/temperature))))]
      (quick-bench "diff-write-set (50 entity changes, 1 ctype)"
        (fn [] (tick/diff-write-set w1000 (simple-sys w1000) #{c/temperature})))

      (quick-bench "legacy-system wrapper (50 entities)"
        (fn []
          (let [sys (tick/legacy-system :temp #{c/temperature} simple-sys)]
            ((:run sys) w1000)))))

    ;; --- Contribution write-set ---
    (quick-bench "contribution-write-set (100 entries)"
      (fn []
        (tick/contribution-write-set c/accel-gravity
          (into {} (map (fn [eid] [eid [(rand) (rand) (rand)]]) (range 100)))
          (range 100))))

    ;; --- Parallel execution ---
    ;; Each system writes to a different component type to avoid single-writer conflicts
    (let [component-pool [c/position c/velocity c/mass c/radius c/temperature
                          c/density c/pressure c/b-field c/matter-state]
          noop-systems (mapv (fn [i]
                               (let [ctype (nth component-pool (mod i (count component-pool)))]
                                 {:id     (keyword (str "sys-" i))
                                  :writes #{ctype}
                                  :run    (fn [world]
                                            {ctype
                                             (into {} (map (fn [eid]
                                                             [eid (or (ecs/get-component world eid ctype) 0.0)])
                                                          (take 100 (ecs/all-entities world))))})}))
                             (range 8))]

      (quick-bench "run-sequential (8 systems, 100 entities)"
        (fn [] (tick/run-sequential w100 noop-systems)))

      (quick-bench "run-parallel (8 systems, 100 entities)"
        (fn [] (tick/run-parallel w100 noop-systems)))

      ;; Larger test with :last-wins to avoid single-writer violations
      (let [big-systems (mapv (fn [i]
                                {:id     (keyword (str "big-" i))
                                 :writes #{c/temperature}
                                 :run    (fn [world]
                                           {c/temperature
                                            (into {} (map (fn [eid]
                                                            [eid (+ (or (ecs/get-component world eid c/temperature) 0.0) 0.1)])
                                                          (take 500 (ecs/all-entities world))))})})
                              (range 8))]
        (quick-bench "run-sequential (8 systems, 500 entities, :last-wins)"
          (fn [] (tick/run-sequential w1000 big-systems :on-conflict :last-wins)))

        (quick-bench "run-parallel (8 systems, 500 entities, :last-wins)"
          (fn [] (tick/run-parallel w1000 big-systems :on-conflict :last-wins)))))

    ;; --- Scaling summary ---
    (println "\n  Tick Pipeline Scaling:")
    (println "    Write-set apply should be O(changes).")
    (println "    Parallel speedup limited by: largest system + barrier overhead.")
    (println "    If parallel ≈ sequential: thread contention or small workload.")))
