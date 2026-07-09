(ns domain.arc-test
  "The narrative-arc layer: arc detection, player-facing text, handoff/ending
   outcomes, and the advance-arc tick integration over the genesis substrate."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.arc        :as arc]
   [domain.genesis    :as genesis]
   [domain.player     :as player]
   [domain.ecs.core   :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.event  :as event]))

;; --- Arc detection ----------------------------------------------------------

(deftest detect-arc-returns-arc-keyword
  (testing "Arc follows the state of the resolved matter, in the :arc/ namespace"
    (is (= :arc/genesis-nebula-collapse
           (arc/detect-arc {:star? false :planet-count 0 :body-count 3
                            :regions [{:matter-state :nebula}]} 0.0)))
    (is (= :arc/genesis-protostar
           (arc/detect-arc {:star? false :planet-count 0 :body-count 1
                            :regions [{:matter-state :protostar}]} 0.0)))
    (is (= :arc/genesis-planets-formed
           (arc/detect-arc {:star? true :planet-count 2 :body-count 3
                            :regions [{:matter-state :star}]} 0.0)))
    (is (= :arc/genesis-dispersed
           (arc/detect-arc {:star? false :planet-count 0 :body-count 0
                            :regions []} 1e20)))))

;; --- Player-facing text -----------------------------------------------------

(deftest arc-quest-returns-text-for-genesis-arcs
  (testing "quest-for returns a non-empty string for a genesis arc"
    (is (seq (arc/quest-for :arc/genesis-ignition)))
    (is (string? (arc/quest-for :arc/genesis-ignition))))
  (testing "quest-for degrades gracefully for an unknown/nil arc"
    (is (seq (arc/quest-for nil)))))

(deftest observation-note-uses-arc-and-coherence
  (testing "Low coherence returns the fade warning regardless of arc"
    (is (re-find #"fading"
                 (arc/observation-note
                  {:coherence 0.1 :focus-intensity 0.5 :resonance-events []}
                  :arc/genesis-ignition))))
  (testing "Healthy coherence falls through to arc flavour"
    (is (re-find #"light"
                 (arc/observation-note
                  {:coherence 0.8 :focus-intensity 0.5 :resonance-events []}
                  :arc/genesis-ignition)))))

(deftest event-notification-maps-stellar-ignition
  (is (= "A star ignites! +25 quanta" (arc/event-notification :stellar-ignition nil nil)))
  (is (= "The nebula collapses. +3 quanta" (arc/event-notification :nebula-collapse nil nil)))
  (is (= "A protostar forms. +12 quanta" (arc/event-notification :protostar-formation nil nil)))
  (is (nil? (arc/event-notification :nonexistent nil nil))))

;; --- Handoff / endings ------------------------------------------------------

(defn- world-with-habitable-planet []
  (let [base    (genesis/create-world {:gas-count 20})
        [w eid] (ecs/spawn base)]
    (-> (ecs/put-components w eid
                            {c/mass 6e24 c/radius 6.4e6 c/position [1e16 0 0]
                             c/velocity [0 0 0] c/body-kind :body/planet
                             c/matter-state :planet c/temperature 300.0
                             c/density 5500.0 c/pressure 1e5
                             c/composition {:H2O 0.1 :C 0.01 :N 0.001}
                             c/luminosity 0.0})
        (assoc :arc/current :arc/genesis-planets-formed))))

(deftest ready-to-narrow-true-when-planets-formed-and-habitable
  (testing "planets-formed arc + a habitable candidate ⇒ ready to narrow"
    (is (arc/ready-to-narrow? (world-with-habitable-planet))))
  (testing "planets-formed arc with no habitable candidate ⇒ not ready"
    (is (not (arc/ready-to-narrow?
              (assoc (genesis/create-world {:gas-count 20}) :arc/current :arc/genesis-planets-formed))))))

(deftest genesis-ending-outcomes
  (testing "A habitable planet at planets-formed yields :ready-to-narrow"
    (is (= :ready-to-narrow (:type (arc/genesis-ending (world-with-habitable-planet))))))
  (testing "Exhausted coherence yields a graceful :fadeout"
    (let [w (-> (genesis/create-world {:gas-count 20})
                (player/update-observer #(assoc % :coherence 0.01)))]
      (is (= :fadeout (:type (arc/genesis-ending w))))))
  (testing "A planets-formed arc with no habitable world is :sterile"
    (let [w (assoc (genesis/create-world {:gas-count 20}) :arc/current :arc/genesis-planets-formed)]
      (is (= :sterile (:type (arc/genesis-ending w)))))))

;; --- Tick integration -------------------------------------------------------

(deftest advance-arc-updates-arc-state
  (testing "tick-genesis sets :arc/current after one combined tick"
    (let [w (arc/tick-genesis (genesis/create-world {:gas-count 20}))]
      (is (contains? w :arc/current))
      (is (keyword? (:arc/current w)))
      (is (= "arc" (namespace (:arc/current w)))))))

(deftest genesis-active-ignores-arc
  (testing "genesis/tick-world advances physics without touching arc state"
    (let [w0 (genesis/create-world {:gas-count 20})
          w1 (genesis/tick-world w0)]
      (is (> (:genesis/sim-time w1) (:genesis/sim-time w0))
          "physics ticks (sim-time advances)")
      (is (not (contains? w1 :arc/current))
          "the pure physics loop never writes :arc/current"))))

(deftest arc-transition-emits-threshold-event
  (testing "advance-arc emits :event/phase-transition when the arc changes"
    (let [w  (-> (ecs/empty-world)
                 (event/with-ledger)
                 (assoc :tick 0
                        :genesis/sim-time 0.0
                        :arc/current :arc/genesis-nebula-collapse
                        :genesis/_prev-summary
                        {:star? true :planet-count 1 :body-count 3
                         :regions [{:matter-state :star}]}))
          w' (arc/advance-arc w)]
      (is (= :arc/genesis-planets-formed (:arc/current w')))
      (is (= :arc/genesis-nebula-collapse (:arc/previous w')))
      (is (seq (filter #(= :event/phase-transition (:kind %))
                       (get-in w' [:ledger :events])))
          "a phase-transition threshold event lands in the ledger")))
  (testing "entering nebula-collapse arc emits :event/nebula-collapse"
    (let [w  (-> (ecs/empty-world)
                 (event/with-ledger)
                 (assoc :tick 1
                        :genesis/sim-time 0.0
                        :arc/current :arc/genesis-dispersed
                        :genesis/_prev-summary
                        {:star? false :planet-count 0 :body-count 3
                         :regions [{:matter-state :nebula}]}))
          w' (arc/advance-arc w)]
      (is (= :arc/genesis-nebula-collapse (:arc/current w')))
      (is (seq (filter #(= :event/nebula-collapse (:kind %))
                       (get-in w' [:ledger :events])))
          "a nebula-collapse threshold event lands in the ledger")
      (is (= :nebula-collapse (last (:arc/recent-events w')))
          "the specific event category is surfaced to the player")))
  (testing "entering protostar arc emits :event/phase-transition"
    (let [w  (-> (ecs/empty-world)
                 (event/with-ledger)
                 (assoc :tick 2
                        :genesis/sim-time 0.0
                        :arc/current :arc/genesis-nebula-collapse
                        :genesis/_prev-summary
                        {:star? false :planet-count 0 :body-count 1
                         :regions [{:matter-state :protostar}]}))
          w' (arc/advance-arc w)]
      (is (= :arc/genesis-protostar (:arc/current w')))
      (is (seq (filter #(= :event/phase-transition (:kind %))
                       (get-in w' [:ledger :events])))
          "a phase-transition event lands in the ledger when the arc advances")))

  (testing "per-body protostar formation events are surfaced as :protostar-formation"
    (let [w  (-> (ecs/empty-world)
                 (event/with-ledger)
                 (assoc :tick 2
                        :genesis/sim-time 0.0
                        :arc/current :arc/genesis-protostar)
                 (event/emit (event/->event {:tick 2 :kind :event/protostar-formation :entities #{}})))
          w' (arc/advance-arc w)]
      (is (= :protostar-formation (last (:arc/recent-events w')))
          "arc surfaces per-body protostar-formation events"))))

(deftest tick-genesis-pays-quanta-for-arc-events-on-same-tick
  (testing "arc-emitted events award agency in the same combined tick"
    (let [w0  (-> (genesis/create-world {:gas-count 20})
                  (assoc :arc/current :arc/genesis-dispersed
                         :genesis/_prev-summary
                         {:star? false :planet-count 0 :body-count 3
                          :regions [{:matter-state :nebula}]}))
          obs0 (player/get-observer w0)
          w1  (arc/tick-genesis w0)
          obs1 (player/get-observer w1)]
      (is (= :arc/genesis-nebula-collapse (:arc/current w1))
          "the arc advances to nebula-collapse")
      (is (seq (filter #(= :event/nebula-collapse (:kind %))
                       (get-in w1 [:ledger :events])))
          "a nebula-collapse event is emitted")
      (is (> (:agency obs1) (:agency obs0))
          "the observer gains agency from the arc-emitted event in the same tick"))))

(deftest tick-genesis-pays-quanta-for-phase-transition-on-same-tick
  (testing "phase-transition events award agency in the same combined tick"
    (let [[w0 _] (-> (ecs/empty-world)
                     (event/with-ledger)
                     (player/spawn-observer [0.0 0.0 0.0]))
          w0   (assoc w0 :tick 0
                      :genesis/sim-time 0.0
                      :sim/dt 1.0e12
                      :arc/current :arc/genesis-nebula-collapse
                      :genesis/_prev-summary
                      {:star? true :planet-count 1 :body-count 3
                       :regions [{:matter-state :star}]})
          obs0 (player/get-observer w0)
          w1   (arc/advance-arc w0)
          dt   (:sim/dt w0)
          w2   ((player/observer-system dt) w1)
          obs1 (player/get-observer w2)]
      (is (= :arc/genesis-planets-formed (:arc/current w1))
          "the arc advances to planets-formed")
      (is (seq (filter #(= :event/phase-transition (:kind %))
                       (get-in w1 [:ledger :events])))
          "a phase-transition event is emitted")
      (is (> (:agency obs1) (:agency obs0))
          "the observer gains agency from the phase-transition in the same tick"))))

(deftest life-emergence-notification-includes-body-name
  (testing "When life emerges, the notification names the world"
    (let [[w eid] (ecs/spawn (genesis/create-world {:gas-count 20}))
          w (-> (ecs/put-components w eid
                                    {c/mass 6e24 c/radius 6.4e6 c/position [1e16 0 0]
                                     c/velocity [0 0 0] c/body-kind :body/planet
                                     c/matter-state :planet c/temperature 300.0
                                     c/density 5500.0 c/pressure 1e5
                                     c/composition {:H2O 0.1 :C 0.01 :N 0.001}})
                (event/with-ledger)
                (assoc :tick 7
                       :genesis/sim-time 1e12
                       :arc/current :arc/genesis-planets-formed
                       :genesis/_prev-summary
                       {:star? true :planet-count 1 :body-count 3
                        :regions [{:matter-state :star}]})
                (event/emit (event/->event {:tick 7 :kind :event/life-emergence :entities #{eid}})))
          w' (arc/advance-arc w)]
      (is (re-find #"Life emerges on" (get-in w' [:arc/notification :text])))
      (is (re-find #"! \+50 quanta" (get-in w' [:arc/notification :text]))))))
