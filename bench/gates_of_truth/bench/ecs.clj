(ns gates-of-truth.bench.ecs
  "ECS core operations benchmark.

   Tests the fundamental operations that every system depends on:
   - Entity spawn/despawn lifecycle
   - Component get/put (single and batch)
   - Archetype queries (entities-with)
   - Entity projection (all-of)
   - World traversal (all-entities)
   - System runner overhead"
  (:require
   [domain.ecs.core      :as ecs]
   [domain.ecs.components :as c]))

;; ---------------------------------------------------------------------------
;; Test world factories
;; ---------------------------------------------------------------------------

(defn- make-world-with-entities
  "Create a world with N entities carrying a standard set of components."
  [n]
  (let [world (ecs/empty-world)]
    (reduce (fn [w i]
              (let [[w' eid] (ecs/spawn w)]
                (ecs/put-components w' eid
                  {c/position     [(double i) 0.0 0.0]
                   c/velocity     [0.0 0.0 0.0]
                   c/mass         (* 1.0e20 (inc i))
                   c/radius       (* 1.0e6 (inc i))
                   c/temperature  300.0
                   c/density      1000.0
                   c/pressure     1.0e5
                   c/matter-state :nebula
                   c/b-field      [0.0 0.0 1.0e-9]})))
            world
            (range n))))

(defn- make-sparse-world
  "World where only 10% of entities carry :component/b-field (tests query pruning)."
  [n]
  (let [world (ecs/empty-world)]
    (reduce (fn [w i]
              (let [[w' eid] (ecs/spawn w)
                    base {c/position    [(double i) 0.0 0.0]
                          c/velocity    [0.0 0.0 0.0]
                          c/mass        (* 1.0e20 (inc i))
                          c/radius      (* 1.0e6 (inc i))
                          c/matter-state (if (zero? (mod i 10))
                                           :nebula
                                           :debris)}]
                (ecs/put-components w' eid
                  (if (zero? (mod i 10))
                    (assoc base c/b-field [0.0 0.0 1.0e-9])
                    base))))
            world
            (range n))))

;; ---------------------------------------------------------------------------
;; Benchmarks
;; ---------------------------------------------------------------------------

(defn run [quick-bench full-bench]
  (let [w100   (make-world-with-entities 100)
        w1000  (make-world-with-entities 1000)
        w5000  (make-world-with-entities 5000)
        wsparse (make-sparse-world 1000)]

    ;; --- Entity lifecycle ---
    (quick-bench "spawn (1 entity, world has 1000)"
      (fn [] (ecs/spawn w1000)))

    (quick-bench "spawn (1 entity, world has 5000)"
      (fn [] (ecs/spawn w5000)))

    (quick-bench "despawn (1 entity from 1000, 9 components)"
      (fn [] (ecs/despawn w1000 500)))

    (quick-bench "alive? check"
      (fn [] (ecs/alive? w1000 500)))

    ;; --- Component access ---
    (quick-bench "get-component (1 read, 1000 entities)"
      (fn [] (ecs/get-component w1000 500 c/position)))

    (quick-bench "put-component (1 write, 1000 entities)"
      (fn [] (ecs/put-component w1000 500 c/position [1.0 2.0 3.0])))

    (quick-bench "put-components (9 components at once)"
      (fn [] (ecs/put-components w1000 500
               {c/position    [1.0 2.0 3.0]
                c/velocity    [0.1 0.0 0.0]
                c/mass        1.0e30
                c/radius      1.0e9
                c/temperature 5000.0
                c/density     100.0
                c/pressure    1.0e6
                c/b-field     [0.0 0.0 1.0e-6]
                c/matter-state :protostar})))

    (quick-bench "get-components (all components for 1 entity)"
      (fn [] (ecs/get-components w1000 500)))

    (quick-bench "has-component? (true case)"
      (fn [] (ecs/has-component? w1000 500 c/position)))

    (quick-bench "has-component? (false case)"
      (fn [] (ecs/has-component? w1000 500 c/frozen-flux)))

    (quick-bench "remove-component (single)"
      (fn [] (ecs/remove-component w1000 500 c/b-field)))

    ;; --- Archetype queries ---
    (quick-bench "entities-with [:component/mass] — 1000 entities"
      (fn [] (ecs/entities-with w1000 c/mass)))

    (quick-bench "entities-with [:component/mass :component/position] — 1000"
      (fn [] (ecs/entities-with w1000 c/mass c/position)))

    (quick-bench "entities-with [:component/mass :component/b-field] — 1000 (sparse)"
      (fn [] (ecs/entities-with wsparse c/mass c/b-field)))

    (quick-bench "entities-with [:component/matter-state :component/mass :component/radius :component/position :component/velocity] — 1000"
      (fn [] (ecs/entities-with w1000 c/matter-state c/mass c/radius
                               c/position c/velocity)))

    (quick-bench "entities-with scaling: 100 vs 1000 vs 5000"
      (fn []
        [(count (ecs/entities-with w100 c/mass))
         (count (ecs/entities-with w1000 c/mass))
         (count (ecs/entities-with w5000 c/mass))]))

    ;; --- Entity projection ---
    (quick-bench "all-of [:position :velocity :mass :radius :body-kind] — 1000"
      (fn [] (doall (ecs/all-of w1000 c/position c/velocity c/mass c/radius c/matter-state))))

    ;; --- World traversal ---
    (quick-bench "all-entities (1000)"
      (fn [] (ecs/all-entities w1000)))

    (quick-bench "all-entities (5000)"
      (fn [] (ecs/all-entities w5000)))

    ;; --- System runner ---
    (let [identity-system (fn [w] w)]
      (quick-bench "run-system (identity, 1000 entities)"
        (fn [] (ecs/run-system w1000 identity-system)))

      (quick-bench "run-systems (10 identity systems, 1000 entities)"
        (fn [] (ecs/run-systems w1000 (repeat 10 identity-system)))))

    ;; --- Archetype update ---
    (quick-bench "archetype read (1 entity)"
      (fn [] (ecs/archetype w1000 500)))

    ;; --- Summary ---
    (println "\n  ECS Scaling Summary:")
    (doseq [n [100 1000 5000]]
      (let [w (case n 100 w100, 1000 w1000, 5000 w5000)
            _ (System/gc)]
        (println (format "    N=%5d: entities-with [:mass] → %d entities"
                         n (count (ecs/entities-with w c/mass))))))))
