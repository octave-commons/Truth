(ns domain.life-arc-test
  "The life act: habitable planets adopt an ecology, autonomous chemistry
   carries it across the prebiotic gap, :event/life-emergence reaches the
   ledger, and the arc narrows to :arc/life-emergence."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecology :as ecology]
   [domain.arc :as arc]
   [domain.ecs.core :as ecs]
   [domain.ecs.event :as event]
   [domain.ecs.components :as c]
   [domain.stellar.seeder :as seeder]
   [law.ecology :as le]))

(defn- world-with-planet
  "Minimal world with one habitable :planet entity. Returns [world eid]."
  []
  (let [w (-> (ecs/empty-world) (event/with-ledger))
        [w eid] (seeder/spawn-clump
                 w {:position [1.5e11 0.0 0.0] :velocity [0.0 0.0 0.0]
                    :mass 5.97e24 :radius 6.4e6 :temperature 300.0
                    :matter-state :planet
                    :composition {:H 0.05 :O 0.25 :Mg 0.15 :Si 0.16 :Fe 0.30
                                  :Al 0.02 :Ca 0.03 :Na 0.01 :Ni 0.02
                                  :C 0.005 :N 0.005 :S 0.005}})
        w (ecs/put-component w eid c/pressure 1.0e5)]
    [w eid]))

(deftest habitable-planet-adopts-ecology
  (let [[w eid] (world-with-planet)
        w (assoc w :tick ecology/ecology-interval-ticks)
        ws ((:run (ecology/ecology-system)) w)
        eco (get-in ws [c/ecology eid])]
    (testing "the ecology system writes a fresh abiotic ecology for the planet"
      (is (some? eco))
      (is (= :abiotic (:phase eco)))
      (is (> (:moisture eco) 0.3) "volatile-rich world starts moist")
      (is (ecology/habitable? eco) "300 K maps into the habitable temp band"))
    (testing "off-cadence ticks do nothing"
      (is (= {} ((:run (ecology/ecology-system))
                 (assoc w :tick (inc ecology/ecology-interval-ticks))))))))

(deftest autonomous-chemistry-reaches-life
  (testing "self-seed + prebiotic drift carry an unattended world to life"
    (let [eco0 (ecology/make-ecology {:moisture 0.45 :temp 0.5})
          ;; run the pure tick chain until living or budget exhausted
          result (loop [eco eco0 n 0]
                   (if (or (ecology/living? eco) (> n 2000))
                     [eco n]
                     (recur (first (ecology/tick-ecology eco n 1)) (inc n))))
          [eco n] result]
      (is (ecology/living? eco) "life emerges without player intervention")
      (is (> n 50) "but not instantly — the chemistry takes its time")
      (is (= :prokaryotic (:phase eco)))
      (is (seq (:record eco)) "transitions are recorded"))))

(deftest life-emergence-event-reaches-ledger
  (let [[w eid] (world-with-planet)
        living  (ecology/make-ecology {:moisture 0.5 :temp 0.5 :seeded true
                                       :biomass 0.2 :phase le/phase-prokaryotic
                                       :record [{:tick 1 :phase :prokaryotic
                                                 :biomass 0.2 :complexity 0.0}]})
        prebio  (assoc living :phase le/phase-prebiotic :record [])
        prev-w  (ecs/put-component w eid c/ecology prebio)
        cur-w   (-> (ecs/put-component w eid c/ecology living)
                    (assoc :tick 7))
        w'      (ecology/emit-phase-events cur-w prev-w)
        evts    (event/events-of-kind w' :event/life-emergence)]
    (testing "crossing prebiotic → prokaryotic fires :event/life-emergence"
      (is (= 1 (count evts)))
      (is (= #{eid} (:entities (first evts)))))
    (testing "no re-fire when the phase does not change"
      (is (empty? (event/events-of-kind
                   (ecology/emit-phase-events cur-w cur-w)
                   :event/life-emergence))))))

(deftest arc-narrows-on-life
  (let [summ {:star? true :planet-count 1 :body-count 5
              :regions [{:matter-state :star} {:matter-state :planet}]}]
    (testing "with life, the arc advances past planets-formed"
      (is (= :arc/genesis-planets-formed (arc/detect-arc summ 1.0e15)))
      (is (= :arc/life-emergence (arc/detect-arc summ 1.0e15 true))))
    (testing "life arc has player-facing text"
      (is (string? (arc/quest-for :arc/life-emergence)))
      (is (string? (arc/description-for :arc/life-emergence)))))
  (testing "living-worlds finds the living ecology"
    (let [[w eid] (world-with-planet)
          w (ecs/put-component w eid c/ecology
                               (ecology/make-ecology {:phase le/phase-prokaryotic}))]
      (is (= [eid] (arc/living-worlds w))))))
