(ns domain.condensation-seeder-test
  "Tests for seed-and-grow condensation: :nebula parcels that would become
   :planetesimal spawn a small physical seed instead of promoting the whole
   parcel. See docs/specs/seed-and-grow-condensation-realspec.md."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.genesis :as genesis]
   [domain.integrator :as integ]
   [domain.stellar :as stellar]
   [domain.stellar.classifier :as classifier]
   [domain.stellar.structure :as structure]
   [domain.spatial.index]
   [law.stellar :as law]
   [shape.spatial :as sp]))

(def ^:private pm 4.0e27)          ;; one gas parcel ≈ 2 M_Jupiter
(def ^:private cloud-comp {:H 0.71 :He 0.27 :metals 0.015})

(defn- unstable-region
  "A Jeans-unstable gas region with the given mass."
  [m]
  {:matter-state :nebula
   :mass m
   :radius 1.0e14
   :density 1.0e-12
   :temperature 15.0
   :pressure 0.0
   :composition cloud-comp})

(defn- with-condense-tick
  "Return world with `:genesis/sim-time` and `:sim/dt` chosen to make
   `stellar/condense-tick?` true."
  [w dt]
  (assoc w :genesis/sim-time (- classifier/condense-interval (double dt))
         :sim/dt (double dt)))

(deftest seeder-emits-seed-for-planetesimal-condensation
  (testing "a :nebula parcel heading for :planetesimal gets a spawn request"
    (let [w (ecs/empty-world)
          [w eid] (ecs/spawn w)
          region (unstable-region (* 1.2 pm))
          w (-> (ecs/put-components w eid
                                    {c/matter-state :nebula
                                     c/mass (:mass region)
                                     c/radius (:radius region)
                                     c/density (:density region)
                                     c/temperature (:temperature region)
                                     c/position [0.0 0.0 0.0]
                                     c/velocity [0.0 0.0 0.0]
                                     c/composition cloud-comp
                                     c/disc-tag :disc})
                (assoc :genesis/gas-particle-mass pm
                       :genesis/condensation-seed-mass-kg 1.0e16)
                (with-condense-tick 2.0e11))
          ws ((:run (stellar/condensation-seeder-system)) w)
          specs (get-in ws [c/spawn-request-condense eid])]
      (is (= :planetesimal (classifier/classify-next-state region pm))
          "precondition: the parcel is a planetesimal condense candidate")
      (is (seq specs) "spawn request was emitted")
      (is (= 1 (count specs)) "exactly one seed spec")
      (is (= :planetesimal (:matter-state (first specs))))
      (is (= 1.0e16 (:mass (first specs))) "seed mass is the configured constant"))))

(deftest seeder-skips-protostar-scale-condensation
  (testing "a parcel massive enough to collapse straight to protostar is NOT seeded"
    (let [w (ecs/empty-world)
          [w eid] (ecs/spawn w)
          m (* 2.0 law/hydrogen-burning-mass)
          region (assoc (unstable-region m) :mass m :radius 1.0e14
                        :density (* 10.0 classifier/core-condensation-density))
          w (-> (ecs/put-components w eid
                                    {c/matter-state :nebula
                                     c/mass m
                                     c/radius (:radius region)
                                     c/density (:density region)
                                     c/temperature 12.0
                                     c/position [0.0 0.0 0.0]
                                     c/velocity [0.0 0.0 0.0]
                                     c/composition cloud-comp
                                     c/disc-tag :disc})
                (assoc :genesis/gas-particle-mass pm)
                (with-condense-tick 2.0e11))
          ws ((:run (stellar/condensation-seeder-system)) w)]
      (is (not= :planetesimal (classifier/classify-next-state region pm))
          "precondition: this is a big condense, not planetesimal")
      (is (nil? (get-in ws [c/spawn-request-condense eid]))
          "no spawn request for protostar-scale gas"))))

(deftest seeder-debits-parent-and-marks-seeded
  (testing "mass is conserved: seed mass is debited from the parent parcel"
    (let [w (ecs/empty-world)
          [w eid] (ecs/spawn w)
          region (unstable-region (* 1.2 pm))
          w (-> (ecs/put-components w eid
                                    {c/matter-state :nebula
                                     c/mass (:mass region)
                                     c/radius (:radius region)
                                     c/density (:density region)
                                     c/temperature (:temperature region)
                                     c/position [1.0e15 0.0 0.0]
                                     c/velocity [0.0 0.0 0.0]
                                     c/composition cloud-comp
                                     c/disc-tag :disc})
                (domain.spatial.index/spatial-index)
                (assoc :genesis/gas-particle-mass pm
                       :genesis/condensation-seed-mass-kg 1.0e16)
                (with-condense-tick 2.0e11))
          ws ((:run (stellar/condensation-seeder-system)) w)]
      (is (== -1.0e16 (get-in ws [c/mass-flux-condense eid]))
          "parent parcel is debited exactly the seed mass")
      (is (true? (get-in ws [c/condensation-seeded eid]))
          "parent parcel is marked so it cannot seed repeatedly"))))

(deftest seeder-offsets-seed-from-parent
  (testing "the seed is displaced so it does not immediately remerge"
    (let [w (ecs/empty-world)
          [w eid] (ecs/spawn w)
          region (unstable-region (* 1.2 pm))
          w (-> (ecs/put-components w eid
                                    {c/matter-state :nebula
                                     c/mass (:mass region)
                                     c/radius (:radius region)
                                     c/density (:density region)
                                     c/temperature (:temperature region)
                                     c/position [1.0e15 0.0 0.0]
                                     c/velocity [0.0 0.0 0.0]
                                     c/composition cloud-comp
                                     c/disc-tag :disc})
                (domain.spatial.index/spatial-index)
                (assoc :genesis/gas-particle-mass pm
                       :genesis/condensation-seed-mass-kg 1.0e16)
                (with-condense-tick 2.0e11))
          ws ((:run (stellar/condensation-seeder-system)) w)
          spec (first (get-in ws [c/spawn-request-condense eid]))
          d (sp/dist [1.0e15 0.0 0.0] (:position spec))]
      (is (pos? d) "seed position differs from parent position")
      (is (> d (:radius spec)) "seed is outside its own radius from parent"))))

(deftest seeder-respects-one-shot-marker
  (testing "a parcel that already seeded cannot seed again"
    (let [w (ecs/empty-world)
          [w eid] (ecs/spawn w)
          region (unstable-region (* 1.2 pm))
          w (-> (ecs/put-components w eid
                                    {c/matter-state :nebula
                                     c/mass (:mass region)
                                     c/radius (:radius region)
                                     c/density (:density region)
                                     c/temperature (:temperature region)
                                     c/position [0.0 0.0 0.0]
                                     c/velocity [0.0 0.0 0.0]
                                     c/composition cloud-comp
                                     c/condensation-seeded true
                                     c/disc-tag :disc})
                (assoc :genesis/gas-particle-mass pm)
                (with-condense-tick 2.0e11))
          ws ((:run (stellar/condensation-seeder-system)) w)]
      (is (nil? (get-in ws [c/spawn-request-condense eid]))))))

(deftest seeder-skips-nebula-outside-disc
  (testing "a :nebula parcel not in the rotationally-supported disk cannot seed"
    (let [w (ecs/empty-world)
          [w eid] (ecs/spawn w)
          region (unstable-region (* 1.2 pm))
          w (-> (ecs/put-components w eid
                                    {c/matter-state :nebula
                                     c/mass (:mass region)
                                     c/radius (:radius region)
                                     c/density (:density region)
                                     c/temperature (:temperature region)
                                     c/position [0.0 0.0 0.0]
                                     c/velocity [0.0 0.0 0.0]
                                     c/composition cloud-comp
                                     c/disc-tag :envelope})
                (assoc :genesis/gas-particle-mass pm)
                (with-condense-tick 2.0e11))
          ws ((:run (stellar/condensation-seeder-system)) w)]
      (is (nil? (get-in ws [c/spawn-request-condense eid]))))))

(deftest integrator-folds-condense-debit
  (testing "c/mass-flux-condense folds through the generic :mass accumulate"
    (let [w (ecs/empty-world)
          [w parent] (stellar/spawn-clump
                      w {:position [0.0 0.0 0.0]
                         :velocity [0.0 0.0 0.0]
                         :mass 1.0e28
                         :radius 1.0e13
                         :matter-state :nebula
                         :density 1.0e-12
                         :temperature 15.0})
          w (assoc w :sim/dt 1.0e10 :tick 0)
          w (ecs/put-component w parent c/mass-flux-condense -1.0e16)
          ws (integ/mass-ws w)
          parent-m (get-in ws [c/mass parent])]
      (is (== parent-m (- 1.0e28 1.0e16))
          "integrator subtracted the condense debit from parent mass"))))

(deftest classifier-skips-planetesimal-condense
  (testing "classifier-system no longer flips :nebula → :planetesimal"
    (let [w (ecs/empty-world)
          [w eid] (ecs/spawn w)
          region (unstable-region (* 1.2 pm))
          w (-> (ecs/put-components w eid
                                    {c/matter-state :nebula
                                     c/mass (:mass region)
                                     c/radius (:radius region)
                                     c/density (:density region)
                                     c/temperature (:temperature region)
                                     c/pressure 0.0
                                     c/composition cloud-comp
                                     c/position [0.0 0.0 0.0]})
                (assoc :genesis/gas-particle-mass pm
                       :genesis/feeding-zone-factor structure/feeding-zone-factor))
          ws ((:run (classifier/classifier-system)) w)]
      (is (nil? (get-in ws [c/matter-state eid]))
          "parent parcel matter-state is unchanged"))))

(deftest classifier-still-promotes-big-condensations
  (testing "classifier-system still whole-parcel promotes protostar-scale gas"
    (let [w (ecs/empty-world)
          [w eid] (ecs/spawn w)
          m (* 2.0 law/hydrogen-burning-mass)
          region (assoc (unstable-region m) :mass m :radius 1.0e14
                        :density (* 10.0 classifier/core-condensation-density))
          w (-> (ecs/put-components w eid
                                    {c/matter-state :nebula
                                     c/mass m
                                     c/radius (:radius region)
                                     c/density (:density region)
                                     c/temperature 12.0
                                     c/pressure 1.0e13
                                     c/composition cloud-comp
                                     c/position [0.0 0.0 0.0]})
                (assoc :genesis/gas-particle-mass m
                       :genesis/gas-smoothing-radius 1.0e14
                       :genesis/feeding-zone-factor structure/feeding-zone-factor))
          ws ((:run (classifier/classifier-system)) w)]
      (is (= :protostar (get-in ws [c/matter-state eid]))
          "massive gas parcel still collapses to protostar")
      (is (some? (get-in ws [c/accretion-radius eid]))
          "big condense still latches a feeding zone"))))

(deftest seeding-is-bounded-in-collapse
  (testing "a standard collapse run does not spawn unbounded seeds"
    (let [final (loop [w (genesis/create-world {:gas-count 50}) i 0]
                  (if (or (> i 100) (:star? (genesis/system-summary w))
                          (not (:genesis/active w)))
                    w
                    (recur (genesis/tick-world w) (inc i))))
          seeded (count (ecs/entities-with final c/condensation-seeded))
          _resolved (count (ecs/entities-with final c/matter-state))]
      (is (<= seeded 250)
          "seed count stays bounded relative to parcel count")
      (is (every? #(= :planetesimal (ecs/get-component final % c/matter-state))
                  (ecs/entities-with final c/condensation-seeded))
          "every seeded body is a planetesimal"))))
