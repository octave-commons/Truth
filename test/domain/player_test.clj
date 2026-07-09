(ns domain.player-test
  "Observer mechanics: coherence drain/regen, agency (quanta) accrual from
   witnessed threshold events, and affordance checks."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core     :as ecs]
   [domain.ecs.event    :as event]
   [domain.player       :as player]))

(deftest agency-gain-maps-event-categories
  (testing "Each transition category pays a distinct amount of influence quanta"
    (is (= 3.0  (player/agency-gain-from-event :nebula-collapse)))
    (is (= 12.0 (player/agency-gain-from-event :protostar-formation)))
    (is (= 25.0 (player/agency-gain-from-event :stellar-ignition)))
    (is (= 10.0 (player/agency-gain-from-event :planet-formation)))
    (is (= 5.0  (player/agency-gain-from-event :phase-transition)))
    (is (= 1.0  (player/agency-gain-from-event :collision)))
    (is (= 0.0  (player/agency-gain-from-event :unknown-event)))))

(deftest resonance-gain-maps-event-categories
  (testing "Each transition category pays a distinct amount of resonance"
    (is (= 1 (player/resonance-gain-from-event :nebula-collapse)))
    (is (= 1 (player/resonance-gain-from-event :protostar-formation)))
    (is (= 2 (player/resonance-gain-from-event :stellar-ignition)))
    (is (= 1 (player/resonance-gain-from-event :planet-formation)))
    (is (= 1 (player/resonance-gain-from-event :phase-transition)))
    (is (= 4 (player/resonance-gain-from-event :life-emergence)))
    (is (= 8 (player/resonance-gain-from-event :gate-discovery)))
    (is (zero? (player/resonance-gain-from-event :unknown-event)))))

(deftest accrue-agency-adds-quanta-from-events
  (testing "Witnessing new event categories increases spendable agency"
    (let [obs  (player/create-observer [0.0 0.0 0.0])
          obs' (player/accrue-agency obs [:nebula-collapse :protostar-formation])]
      (is (= 15.0 (:agency obs'))))))

(deftest accrue-agency-pays-every-occurrence
  (testing "Agency is a spendable resource: every event occurrence pays, even duplicates"
    (let [obs  (player/create-observer [0.0 0.0 0.0])
          obs' (player/accrue-agency obs [:collision :collision :collision])]
      (is (= 3.0 (:agency obs'))))))

(deftest accrue-resonance-pays-only-first-time
  (testing "Resonance is a progression resource: only the first crossing of each threshold pays"
    (let [obs  (player/create-observer [0.0 0.0 0.0])
          obs' (-> obs
                   (player/accrue-resonance [:nebula-collapse :protostar-formation])
                   (player/accrue-resonance [:protostar-formation :stellar-ignition])
                   (player/accrue-resonance [:stellar-ignition :stellar-ignition]))]
      (is (== 4.0 (:resonance obs')) "nebula 1 + protostar 1 + star 2 = 4, duplicates ignored")
      (is (= #{:nebula-collapse :protostar-formation :stellar-ignition}
             (:resonance-thresholds obs'))))))

(deftest observer-system-accrues-agency-from-ledger-events
  (testing "A nebula-collapse + protostar-formation tick pays the observer"
    (let [[w _eid] (player/spawn-observer (ecs/empty-world) [0.0 0.0 0.0])
          w       (-> w
                      (event/with-ledger)
                      (assoc :tick 7
                             :genesis/complexity 1)
                      (event/emit (event/->event {:tick 7 :kind :event/nebula-collapse :entities #{}}))
                      (event/emit (event/->event {:tick 7 :kind :event/protostar-formation :entities #{}})))
          w'      ((player/observer-system 1.0) w)
          obs     (player/get-observer w')]
      (is (= 15.0 (:agency obs)) "observer banks 3 + 12 quanta")
      (is (= 2.0 (:resonance obs)) "observer banks 1 + 1 resonance"))))

(deftest observer-system-accrues-agency-from-condensed-core-formation
  (testing "A nebula -> condensed-core promotion pays quanta like any upgrade event"
    (let [[w _eid] (player/spawn-observer (ecs/empty-world) [0.0 0.0 0.0])
          w       (-> w
                      (event/with-ledger)
                      (assoc :tick 7
                             :genesis/complexity 1)
                      (event/emit (event/->event {:tick 7 :kind :event/condensed-core-formation :entities #{}})))
          w'      ((player/observer-system 1.0) w)
          obs     (player/get-observer w')]
      (is (= 3.0 (:agency obs)) "condensed core formation awards 3 quanta")
      (is (== 1.0 (:resonance obs)) "condensed core formation adds 1 resonance"))))

(deftest observer-system-pays-agency-for-every-event-occurrence
  (testing "Multiple threshold events on the same tick each award agency"
    (let [[w _eid] (player/spawn-observer (ecs/empty-world) [0.0 0.0 0.0])
          w       (-> w
                      (event/with-ledger)
                      (assoc :tick 3 :genesis/complexity 1)
                      (event/emit (event/->event {:tick 3 :kind :event/nebula-collapse :entities #{}}))
                      (event/emit (event/->event {:tick 3 :kind :event/nebula-collapse :entities #{}}))
                      (event/emit (event/->event {:tick 3 :kind :event/nebula-collapse :entities #{}})))
          w'      ((player/observer-system 1.0) w)
          obs     (player/get-observer w')]
      (is (= 9.0 (:agency obs)) "three nebula collapses = 9 quanta")
      (is (== 1.0 (:resonance obs)) "only one resonance from repeated nebula collapses"))))

(deftest can-afford-and-spend-agency
  (testing "Spending respects the current agency balance"
    (let [obs (-> (player/create-observer [0.0 0.0 0.0])
                  (assoc :agency 10.0))]
      (is (player/can-afford? obs 10.0))
      (is (not (player/can-afford? obs 10.1)))
      (is (= 3.0 (:agency (player/spend-agency obs 7.0))))
      (is (= 0.0 (:agency (player/spend-agency obs 100.0))) "spend clamps at zero"))))
