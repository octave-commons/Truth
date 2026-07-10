(ns domain.formation-integration-test
  "Genesis Formation spec Part 8 — headless integration of the star→disk→planet
   pipeline through the REAL `genesis/tick-world` loop (disc-identification →
   disk-evolution → planet seeder → materialize-lifecycle → threshold events).

   Scope note (measured): a default emergent run at gas-count 50 fragments into
   ~56 marginal cores and never forms a single dominant star with a coherent
   Keplerian disc (spec Part 1a — competitive-accretion tuning is still open, and
   `disc-n` stays 0). So this test does NOT assert the emergent 'one star + 3–8
   planets' end-state. Instead it drives the FULL tick pipeline over a clean
   single-star + protoplanetary-disc world, verifying the mechanism this spec
   built end-to-end: real disc bodies are tagged, the seeder fires through
   disk-evolution, planets materialize as bound entities with conserved mass, and
   the arc/event layer observes the transition. When Part 1a lands, the same
   assertions should hold for an emergent disc."
  (:require
   [clojure.math :as math] [clojure.test          :refer [deftest testing is]]
   [domain.genesis        :as genesis]
   [domain.arc            :as arc]
   [domain.ecs.core       :as ecs]
   [domain.ecs.event      :as event]
   [domain.ecs.components  :as c]
   [domain.stellar.seeder :as seeder]
   [domain.physics.cache  :as pcache]
   [law.composition       :as lcomp]
   [law.stellar           :as law]
   [shape.spatial         :as sp]))

(defn- circular-velocity [star-m pos]
  (let [r (sp/len pos)
        v (math/sqrt (/ (* law/G star-m) r))
        [x y _] pos]
    [(* (- v) (/ y r)) (* v (/ x r)) 0.0]))

(defn- seed-star-and-disc
  "Inject one Sun-like :star at the origin carrying a mature protoplanetary disk,
   surrounded by `n` :disc-tagged bodies on circular orbits from 0.3–15 AU."
  [w {:keys [disk-mass body-mass n] :or {disk-mass 1.0e27 body-mass 6.0e24 n 24}}]
  (let [M law/solar-mass
        au law/au
        [w star] (seeder/spawn-clump w
                                     {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                                      :mass M :radius law/solar-radius :temperature 2.0e7
                                      :matter-state :star
                                      :composition lcomp/solar-composition})
        w (-> w
              (ecs/put-component star c/pressure 1.0e13)  ;; fusion sustaining
              (ecs/put-component star c/luminosity law/solar-luminosity)
              (ecs/put-component star c/disk-mass disk-mass)
              (ecs/put-component star c/disk-angular-mom [0.0 0.0 1.0e42])
              (ecs/put-component star c/rotation-axis [0.0 0.0 1.0]))
        ;; Distribute bodies in angle (golden angle) as well as radius so the disc
        ;; is roughly axisymmetric — a collinear line of bodies would exert a large
        ;; net pull that displaces the star within the tick and corrupts the
        ;; planets' measured orbits.
        placements (for [i (range n)]
                     (let [r (* au (math/pow 10.0 (+ (math/log10 0.3)
                                                     (* i (/ (- (math/log10 15.0) (math/log10 0.3))
                                                             (dec n))))))
                           theta (* 2.399963229728653 i)]  ;; golden angle (rad)
                       [(* r (math/cos theta)) (* r (math/sin theta)) 0.0]))
        w (reduce (fn [w pos]
                    (let [[w2 eid] (seeder/spawn-clump w
                                                       {:position pos :velocity (circular-velocity M pos)
                                                        :mass body-mass :radius 1.0e7
                                                        :matter-state :planetesimal})]
                      (ecs/put-component w2 eid c/disc-tag :disc)))
                  w placements)]
    [w star]))

(defn- build-world []
  ;; A negligible-mass nebula keeps create-world valid without dragging the
  ;; system centre-of-mass off the star: tick-world recenters every body by the
  ;; COM each tick, and a massive far nebula would shift newly-spawned planets
  ;; relative to the integrated star. With the star dominating the mass, the
  ;; frame-offset is ~0. dt is small relative to the inner orbital period (~yr)
  ;; so the injected orbits stay stable across the few ticks the test runs; the
  ;; seeder's maturity guard is on sim-time, so it still fires on tick 1.
  (let [w0 (-> (genesis/create-world {:gas-count 4 :nebula-mass 4.0e20
                                      :nebula-radius 2.0e16})
               (assoc :genesis/adaptive-pacing? false
                      :sim/dt 1.0e6
                      :genesis/sim-time 1.0e14
                      :genesis/star-ignition-time 0.0
                      :genesis/disk-maturity 5.0e12))
        [w star] (seed-star-and-disc w0 {})]
    [w star]))

(defn- planets [w]
  (filter #(= :planet (ecs/get-component w % c/matter-state))
          (ecs/entities-with w c/matter-state)))

(deftest full-pipeline-seeds-planets-through-tick-world
  (testing "one tick of the real pipeline materializes ≥1 :planet from the disk"
    (let [[w0 _star] (build-world)
          w1 (genesis/tick-world w0)
          ps (planets w1)]
      (is (pos? (count ps)) "the seeder fired and planets materialized")
      (is (<= 1 (count ps)) "at least one planet formed"))))

(deftest seeded-planets-are-bound-and-typed
  (testing "materialized planets orbit the star (bound) with location-consistent types"
    (let [[w0 star] (build-world)
          w1 (genesis/tick-world w0)
          M   (double (ecs/get-component w1 star c/mass))
          spos (ecs/get-component w1 star c/position)
          svel (ecs/get-component w1 star c/velocity)
          ps  (planets w1)]
      (is (pos? (count ps)))
      (doseq [p ps]
        (let [pos (ecs/get-component w1 p c/position)
              vel (ecs/get-component w1 p c/velocity)
              r   (sp/dist pos spos)
              v   (sp/len (sp/v- vel svel))
              energy (- (* 0.5 v v) (/ (* law/G M) r))
              ptype (ecs/get-component w1 p c/planet-type)]
          (is (neg? energy) (str "planet at " (/ r law/au) " AU should be bound"))
          (is (some? ptype) "planet carries a :planet-type")
          (is (>= r (* 0.1 law/au)) "no planet inside 0.1 AU"))))))

(deftest planet-formation-is-one-shot
  (testing "after seeding, the star is flagged and further ticks add no new planets"
    (let [[w0 _star] (build-world)
          w1 (genesis/tick-world w0)
          n1 (count (planets w1))
          w3 (nth (iterate genesis/tick-world w1) 2)
          n3 (count (planets w3))]
      (is (pos? n1))
      (is (= n1 n3) "the one-shot guard prevents re-seeding on later ticks"))))

(deftest planet-formation-conserves-disc-mass
  (testing "the disk mass consumed is ≥ the total mass of the seeded planets"
    (let [[w0 star] (build-world)
          disk0 (double (ecs/get-component w0 star c/disk-mass))
          w1 (genesis/tick-world w0)
          disk1 (double (or (ecs/get-component w1 star c/disk-mass) 0.0))
          planet-mass (reduce + 0.0 (map #(double (ecs/get-component w1 % c/mass))
                                         (planets w1)))
          consumed (- disk0 disk1)]
      (is (>= consumed 0.0) "disk mass only decreases")
      (is (<= planet-mass (+ consumed (* 1.0e-6 (max 1.0 consumed))))
          "planets draw no more than the disk debit"))))

(deftest arc-and-event-observe-planet-formation
  (testing "tick-world fires :event/planet-formation and the arc reaches genesis-planets-formed"
    (let [[w0 _star] (build-world)
          w1 (genesis/tick-world w0)
          summ (genesis/system-summary w1)
          arc-state (arc/detect-arc summ (:genesis/sim-time w1))
          fired (event/events-of-kind w1 :event/planet-formation)]
      (is (:star? summ) "the star persists (ignition hysteresis)")
      (is (pos? (:planet-count summ)) "summary counts the new planets")
      (is (seq fired) ":event/planet-formation was emitted into the ledger")
      (is (= :arc/genesis-planets-formed arc-state)
          "arc advances to genesis-planets-formed once a planet orbits the star"))))

(defn- without-transient-caches
  "Drop per-tick caches and cache-config flags so two otherwise-identical worlds
   can be compared."
  [world]
  (-> world
      (dissoc :genesis/neighbor-cache-full-rebuild-interval
              :genesis/physics-soa
              :ecs/_query-cache
              :genesis/invalidate-neighbor-cache?)
      (pcache/strip-neighbor-cache)))

(deftest persistent-cache-matches-full-rebuild
  (testing "10 ticks with persistent cache and full-rebuild produce identical worlds"
    (let [base (-> (genesis/create-world {:gas-count 100 :spin 0.0 :turb 0.0
                                          :nebula-radius 2.0e16
                                          :n-seeds 5 :seed-r 0.18})
                   (assoc :sim/G 0.0
                          :genesis/adaptive-pacing? false
                          :sim/dt 0.0))]
      (loop [i 0
             persist base
             full    (assoc base :genesis/invalidate-neighbor-cache? true)]
        (when (< i 10)
          (let [p1 (genesis/tick-world persist)
                f1 (genesis/tick-world full)]
            (is (= (without-transient-caches p1) (without-transient-caches f1))
                (str "worlds diverged at tick " (inc i)))
            (recur (inc i) p1 f1)))))))

(deftest persistent-cache-interval-one-matches-invalidation
  (testing "10 ticks with interval=1 persistent cache match invalidate=true mode"
    (let [base (-> (genesis/create-world {:gas-count 100 :spin 0.0 :turb 0.0
                                          :nebula-radius 2.0e16
                                          :n-seeds 5 :seed-r 0.18})
                   (assoc :sim/G 0.0
                          :genesis/adaptive-pacing? false
                          :sim/dt 0.0))
          interval-one (assoc base :genesis/neighbor-cache-full-rebuild-interval 1)
          invalid      (assoc base :genesis/invalidate-neighbor-cache? true)]
      (loop [i 0
             persist interval-one
             full    invalid]
        (when (< i 10)
          (let [p1 (genesis/tick-world persist)
                f1 (genesis/tick-world full)]
            (is (= (without-transient-caches p1) (without-transient-caches f1))
                (str "worlds diverged at tick " (inc i)))
            (recur (inc i) p1 f1)))))))

(deftest persistent-cache-default-interval-stays-stable
  (testing "30 ticks with default persistent-cache interval stay stable vs full rebuild"
    (let [base (-> (genesis/create-world {:gas-count 30 :spin 0.4 :turb 0.05
                                          :nebula-radius 2.0e16
                                          :n-seeds 5 :seed-r 0.18})
                   (assoc :genesis/adaptive-pacing? false))
          full (assoc base :genesis/invalidate-neighbor-cache? true)
          run #(reduce (fn [w _] (genesis/tick-world w)) % (range 30))
          w-full (run full)
          w-persist (run base)
          body-count #(count (ecs/entities-with % c/matter-state c/mass))
          total-mass #(reduce + 0.0
                              (map (fn [eid]
                                     (double (or (ecs/get-component % eid c/mass) 0.0)))
                                   (ecs/entities-with % c/mass)))
          bc-full (body-count w-full)
          bc-persist (body-count w-persist)
          m-full (total-mass w-full)
          m-persist (total-mass w-persist)]
      (is (pos? bc-persist) "persistent-cache world still has bodies")
      (is (= bc-full bc-persist) "same final body count")
      (is (< (abs (- m-persist m-full))
             (* 1e-6 (max 1.0 m-full)))
          "total mass matches within tolerance"))))
