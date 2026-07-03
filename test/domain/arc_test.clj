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
  (is (= "A star ignites! +25 quanta" (arc/event-notification :stellar-ignition)))
  (is (nil? (arc/event-notification :nonexistent))))

;; --- Handoff / endings ------------------------------------------------------

(defn- world-with-habitable-planet []
  (let [base    (genesis/create-world)
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
              (assoc (genesis/create-world) :arc/current :arc/genesis-planets-formed))))))

(deftest genesis-ending-outcomes
  (testing "A habitable planet at planets-formed yields :ready-to-narrow"
    (is (= :ready-to-narrow (:type (arc/genesis-ending (world-with-habitable-planet))))))
  (testing "Exhausted coherence yields a graceful :fadeout"
    (let [w (-> (genesis/create-world)
                (player/update-observer #(assoc % :coherence 0.01)))]
      (is (= :fadeout (:type (arc/genesis-ending w))))))
  (testing "A planets-formed arc with no habitable world is :sterile"
    (let [w (assoc (genesis/create-world) :arc/current :arc/genesis-planets-formed)]
      (is (= :sterile (:type (arc/genesis-ending w)))))))

;; --- Tick integration -------------------------------------------------------

(deftest advance-arc-updates-arc-state
  (testing "tick-genesis sets :arc/current after one combined tick"
    (let [w (arc/tick-genesis (genesis/create-world {:gas-count 20}))]
      (is (contains? w :arc/current))
      (is (keyword? (:arc/current w)))
      (is (= (namespace (:arc/current w)) "arc")))))

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
          "a phase-transition threshold event lands in the ledger"))))
