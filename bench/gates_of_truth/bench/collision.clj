(ns gates-of-truth.bench.collision
  "Collision detection benchmarks.

   The collision system reuses the Barnes-Hut octree for broad-phase overlap
   detection. Key costs:
   1. Collidable body extraction (filter + projection)
   2. Tree construction (shared with gravity)
   3. Overlap queries per body (tree walk + distance checks)
   4. Event dispatch for detected pairs"
  (:require
   [domain.physics.collision :as collision]
   [domain.gravity.barnes-hut :as bh]
   [domain.ecs.core          :as ecs]
   [domain.ecs.event         :as event]
   [domain.ecs.components    :as c]
   [shape.spatial            :as sp]))

;; ---------------------------------------------------------------------------
;; Test worlds
;; ---------------------------------------------------------------------------

(defn- make-world-with-bodies
  "World with N resolved bodies (non-nebula) at random positions.
   fraction-colliding controls what fraction are close enough to potentially collide."
  [n fraction-colliding]
  (let [world (-> (ecs/empty-world) (event/with-ledger))
        rng   (java.util.Random. 42)]
    (reduce (fn [w i]
              (let [[w' eid] (ecs/spawn w)
                    ;; Most bodies spread out, some clustered
                    spread  (if (< (.nextDouble rng) fraction-colliding)
                              1.0e9    ;; clustered: may overlap
                              1.0e14)  ;; spread: won't collide
                    pos     (sp/vec3 (* spread (- (* 2.0 (.nextDouble rng)) 1.0))
                                    (* spread (- (* 2.0 (.nextDouble rng)) 1.0))
                                    (* spread (- (* 2.0 (.nextDouble rng)) 1.0)))
                    radius  (* 6.957e8 (+ 0.5 (* 2.0 (.nextDouble rng))))]
                (ecs/put-components w' eid
                  {c/position     pos
                   c/velocity     [0.0 0.0 0.0]
                   c/mass         (* 1.989e30 (+ 0.5 (.nextDouble rng)))
                   c/radius       radius
                   c/matter-state :protostar})))
            world
            (range n))))

(defn- make-overlap-world
  "World where ~half the bodies actually overlap (worst-case for pair detection)."
  [n]
  (let [world (-> (ecs/empty-world) (event/with-ledger))
        rng   (java.util.Random. 42)]
    (reduce (fn [w i]
              (let [[w' eid] (ecs/spawn w)
                    cluster  (zero? (mod i 2))
                    base-r   6.957e8
                    radius   (* base-r (+ 0.5 (.nextDouble rng)))
                    pos      (if cluster
                               ;; Clustered: bodies near origin, overlapping
                               (sp/vec3 (* base-r 0.5 (- (* 2.0 (.nextDouble rng)) 1.0))
                                        (* base-r 0.5 (- (* 2.0 (.nextDouble rng)) 1.0))
                                        (* base-r 0.5 (- (* 2.0 (.nextDouble rng)) 1.0)))
                               ;; Spread: far apart
                               (sp/vec3 (* 1.0e14 (- (* 2.0 (.nextDouble rng)) 1.0))
                                        (* 1.0e14 (- (* 2.0 (.nextDouble rng)) 1.0))
                                        (* 1.0e14 (- (* 2.0 (.nextDouble rng)) 1.0))))]
                (ecs/put-components w' eid
                  {c/position     pos
                   c/velocity     [0.0 0.0 0.0]
                   c/mass         (* 1.989e30 (+ 0.5 (.nextDouble rng)))
                   c/radius       radius
                   c/matter-state :protostar})))
            world
            (range n))))

;; ---------------------------------------------------------------------------
;; Benchmarks
;; ---------------------------------------------------------------------------

(defn run [quick-bench full-bench]
  (let [w100    (make-world-with-bodies 100 0.1)
        w500    (make-world-with-bodies 500 0.1)
        w1000   (make-world-with-bodies 1000 0.1)
        w-overlap (make-overlap-world 200)]

    ;; --- Full collision detection pipeline ---
    (quick-bench "collision-detection-system (100 bodies, 10% close)"
      (fn [] (collision/collision-detection-system w100)))

    (quick-bench "collision-detection-system (500 bodies, 10% close)"
      (fn [] (collision/collision-detection-system w500)))

    (quick-bench "collision-detection-system (1000 bodies, 10% close)"
      (fn [] (collision/collision-detection-system w1000)))

    (quick-bench "collision-detection-system (200 bodies, 50% overlap)"
      (fn [] (collision/collision-detection-system w-overlap)))

    ;; --- Cost breakdown ---
    ;; Test body extraction separately
    (quick-bench "collidable-bodies extraction (1000 entities)"
      (fn [] (#'collision/collidable-bodies w1000)))

    ;; Tree build (shared with gravity, but collision does it per tick)
    (let [bodies (#'collision/collidable-bodies w1000)
          recs   (mapv (fn [[eid pos r v m]]
                         {:id eid :position pos :radius (double r) :velocity v :mass (double m)})
                       bodies)]
      (quick-bench "collision tree build (1000 bodies)"
        (fn [] (bh/build-tree recs)))

      (quick-bench "detect-pairs (1000 bodies)"
        (fn [] (#'collision/detect-pairs bodies)))

      ;; Overlap query per body
      (let [tree (bh/build-tree recs)
            q    (first recs)]
        (quick-bench "collect-overlaps (1 query, 1000 bodies in tree)"
          (fn [] (#'collision/collect-overlaps tree q (:radius q) [])))))

    ;; --- Scaling analysis ---
    (println "\n  Collision Scaling:")
    (println "    Overlap detection cost depends on body density.")
    (println "    Clustered bodies → more pairs → quadratic blowup potential.")
    (println "    Check if tree pruning prevents O(N²) in practice.")))
