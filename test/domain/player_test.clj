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
    (is (= 8.0  (player/agency-gain-from-event :protostar-formation)))
    (is (= 25.0 (player/agency-gain-from-event :stellar-ignition)))
    (is (= 10.0 (player/agency-gain-from-event :planet-formation)))
    (is (= 5.0  (player/agency-gain-from-event :phase-transition)))
    (is (= 1.0  (player/agency-gain-from-event :collision)))
    (is (= 0.0  (player/agency-gain-from-event :unknown-event)))))

(deftest accrue-agency-adds-quanta-from-events
  (testing "Witnessing new event categories increases spendable agency"
    (let [obs  (player/create-observer [0.0 0.0 0.0])
          obs' (player/accrue-agency obs [:nebula-collapse :protostar-formation])]
      (is (= 11.0 (:agency obs'))))))

(deftest observer-system-accrues-agency-from-ledger-events
  (testing "A nebula-collapse + protostar-formation tick pays the observer"
    (let [[w eid] (player/spawn-observer (ecs/empty-world) [0.0 0.0 0.0])
          w       (-> w
                      (event/with-ledger)
                      (assoc :tick 7
                             :genesis/complexity 1)
                      (event/emit (event/->event {:tick 7 :kind :event/nebula-collapse :entities #{}}))
                      (event/emit (event/->event {:tick 7 :kind :event/protostar-formation :entities #{}})))
          w'      ((player/observer-system 1.0) w)
          obs     (player/get-observer w')]
      (is (= 11.0 (:agency obs)) "observer banks 3 + 8 quanta"))))

(deftest can-afford-and-spend-agency
  (testing "Spending respects the current agency balance"
    (let [obs (-> (player/create-observer [0.0 0.0 0.0])
                  (assoc :agency 10.0))]
      (is (player/can-afford? obs 10.0))
      (is (not (player/can-afford? obs 10.1)))
      (is (= 3.0 (:agency (player/spend-agency obs 7.0))))
      (is (= 0.0 (:agency (player/spend-agency obs 100.0))) "spend clamps at zero"))))
