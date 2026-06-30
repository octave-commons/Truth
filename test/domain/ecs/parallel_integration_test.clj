(ns domain.ecs.parallel-integration-test
  "End-to-end smoke test: drive a real bootstrapped Phase 0 world through the
   double-buffer fan-out (domain.ecs.tick) rather than the Gauss–Seidel reduce.
   Proves the real 10 transform systems run concurrently off a frozen snapshot,
   that the legacy bridge masks them to their owned columns, and that the world
   stays structurally well-formed across ticks. Single-writer now holds, so the
   fold runs with :on-conflict :throw — any runtime write-set overlap fails."
  (:require
    [clojure.test :refer [deftest is testing]]
    [domain.ecs.core :as ecs]
    [domain.ecs.components :as c]
    [domain.ecs.tick :as tick]
    [domain.physics.collision :as collision]
    [domain.phase0 :as phase0]))

(defn- parallel-tick
  "One double-buffer tick: advance the logical tick, arm the integrator with the
   COM frame-offset (recenter is now a Galilean shift the integrator applies, not
   a post-fold write — spec §6), fan the transform systems out concurrently and
   fold them, then run the discrete collision/merge barrier."
  [world]
  (let [world   (-> (ecs/advance-tick world)
                    (assoc :phase0/frame-offset (phase0/center-of-mass world)))
        systems (phase0/physics-systems-parallel world)]
    (-> (tick/run-parallel world systems) ;; :on-conflict :throw (default)
        (collision/collision-detection-system)))) ;; barrier: discrete events

(defn- finite-vec3? [v]
  (and (vector? v) (= 3 (count v)) (every? #(and (number? %) (Double/isFinite (double %))) v)))

(deftest fan-out-drives-a-real-world
  (let [w0 (phase0/create-world {:gas-count 150 :dt 1e12})
        n  10
        wn (reduce (fn [w _] (parallel-tick w)) w0 (range n))]
    (testing "the world advanced n ticks without throwing"
      (is (= n (:tick wn))))
    (testing "bodies survive and positions stay finite (no NaN/poof)"
      (let [eids (ecs/entities-with wn c/position c/mass)]
        (is (pos? (count eids)))
        (is (every? #(finite-vec3? (ecs/get-component wn % c/position)) eids))))
    (testing "the component store stays consistent with the archetype index"
      (doseq [eid (ecs/all-entities wn)]
        (doseq [ct (ecs/archetype wn eid)]
          (is (some? (ecs/get-component wn eid ct))
              (str "entity " eid " indexed for " ct " but cell is missing")))))))

(deftest fan-out-is-deterministic-and-matches-sequential-fold
  ;; The guarantee the double buffer makes: given a FIXED snapshot, the parallel
  ;; fan-out introduces no nondeterminism — thread scheduling cannot change the
  ;; result — and it equals the single-threaded fold of the same write-sets.
  ;; (Cross-run reproducibility is a separate, pre-existing concern: create-world
  ;; itself is non-deterministic, independent of this work — see the design note.
  ;; Full system-ORDER independence holds once single-writer removes all conflicts;
  ;; single-writer holds, so the fold is conflict-free under the default :throw.)
  (let [w       (ecs/advance-tick (phase0/create-world {:gas-count 120 :dt 1e12}))
        systems (phase0/physics-systems-parallel w)
        par-a   (tick/run-parallel   w systems)
        par-b   (tick/run-parallel   w systems)
        seqv    (tick/run-sequential w systems)]
    (testing "run-parallel is deterministic on a fixed snapshot"
      (is (= (:components par-a) (:components par-b))))
    (testing "parallel fold == sequential fold of the same write-sets"
      (is (= (:components par-a) (:components seqv))))))
