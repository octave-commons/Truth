(ns domain.ecology-test
  "Tests for the toy ecology model: passive ticks, phase transitions, extinction,
   and player ability effects."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecology :as eco]))

(deftest make-ecology-defaults
  (let [e (eco/make-ecology)]
    (is (= 0.0 (:moisture e)))
    (is (= 0.5 (:temp e)))
    (is (= 0.0 (:biomass e)))
    (is (= 0.0 (:complexity e)))
    (is (= 0.5 (:stability e)))
    (is (= :abiotic (:phase e)))
    (is (false? (:seeded e)))
    (is (vector? (:record e)))))

(deftest make-ecology-overrides
  (let [e (eco/make-ecology {:moisture 0.3 :phase :prokaryotic})]
    (is (= 0.3 (:moisture e)))
    (is (= :prokaryotic (:phase e)))))

(deftest habitable-band
  (is (eco/habitable? (eco/make-ecology {:temp 0.5})))
  (is (not (eco/habitable? (eco/make-ecology {:temp 0.2}))))
  (is (not (eco/habitable? (eco/make-ecology {:temp 0.8})))))

(deftest passive-tick-moisture
  (testing "moisture gains when temp < 0.6"
    (let [e (eco/passive-tick (eco/make-ecology {:moisture 0.1 :temp 0.5}))]
      (is (> (:moisture e) 0.1))))
  (testing "moisture loses when temp >= 0.6"
    (let [e (eco/passive-tick (eco/make-ecology {:moisture 0.5 :temp 0.7}))]
      (is (< (:moisture e) 0.5)))))

(deftest passive-tick-growth-conditions
  (testing "biomass and complexity grow only when living, habitable, and moist"
    (let [base {:moisture 0.2 :temp 0.5 :biomass 0.1 :complexity 0.1
                :phase :prokaryotic :stability 0.5}
          e   (eco/passive-tick (eco/make-ecology base))]
      (is (> (:biomass e) 0.1))
      (is (> (:complexity e) 0.1))))
  (testing "abiotic phase does not grow biomass"
    (let [base {:moisture 0.2 :temp 0.5 :biomass 0.1 :complexity 0.1
                :phase :abiotic :stability 0.5}
          e   (eco/passive-tick (eco/make-ecology base))]
      (is (= 0.1 (:biomass e)))
      (is (= 0.1 (:complexity e)))))
  (testing "low moisture blocks growth"
    (let [base {:moisture 0.05 :temp 0.5 :biomass 0.1 :complexity 0.1
                :phase :prokaryotic :stability 0.5}
          e   (eco/passive-tick (eco/make-ecology base))]
      (is (= 0.1 (:biomass e)))
      (is (= 0.1 (:complexity e))))))

(deftest passive-tick-stability
  (testing "stability recovers when habitable"
    (let [e (eco/passive-tick (eco/make-ecology {:stability 0.3 :temp 0.5}))]
      (is (> (:stability e) 0.3))))
  (testing "stability drains when not habitable"
    (let [e (eco/passive-tick (eco/make-ecology {:stability 0.5 :temp 0.1}))]
      (is (< (:stability e) 0.5)))))

(deftest passive-tick-temperature-reverts
  (let [e-cold (eco/passive-tick (eco/make-ecology {:temp 0.1}))
        e-hot  (eco/passive-tick (eco/make-ecology {:temp 0.9}))]
    (is (> (:temp e-cold) 0.1))
    (is (< (:temp e-hot) 0.9))))

(deftest collapse-drains-biomass
  (let [base {:moisture 0.2 :temp 0.5 :biomass 0.2 :complexity 0.1
              :phase :prokaryotic :stability 0.05}
        e   (eco/passive-tick (eco/make-ecology base))]
    (is (true? (eco/collapse? (eco/make-ecology base))))
    (is (< (:biomass e) 0.2))))

(deftest phase-transition-abiotic-to-prebiotic
  (let [e (assoc (eco/make-ecology) :seeded true :moisture 0.2)]
    (is (= :prebiotic (eco/check-phase-transition e)))))

(deftest phase-transition-prebiotic-to-prokaryotic
  (let [e (assoc (eco/make-ecology) :phase :prebiotic :biomass 0.2 :temp 0.5)]
    (is (= :prokaryotic (eco/check-phase-transition e)))))

(deftest phase-transition-prokaryotic-to-eukaryotic
  (let [e (assoc (eco/make-ecology) :phase :prokaryotic :biomass 0.4
                 :complexity 0.25)]
    (is (= :eukaryotic (eco/check-phase-transition e)))))

(deftest phase-transition-eukaryotic-to-multicellular
  (let [e (assoc (eco/make-ecology) :phase :eukaryotic :complexity 0.5
                 :stability 0.5)]
    (is (= :multicellular (eco/check-phase-transition e)))))

(deftest phase-transition-multicellular-to-complex
  (let [e (assoc (eco/make-ecology) :phase :multicellular :complexity 0.75
                 :biomass 0.65)]
    (is (= :complex (eco/check-phase-transition e)))))

(deftest advance-phase-records-transition
  (let [e (eco/advance-phase (eco/make-ecology) 7 :prebiotic)]
    (is (= :prebiotic (:phase e)))
    (is (= 1 (count (:record e))))
    (is (= 7 (:tick (first (:record e)))))))

(deftest maybe-advance-phase-returns-event
  (let [[e evt] (eco/maybe-advance-phase
                 (assoc (eco/make-ecology) :seeded true :moisture 0.2)
                 42 99)]
    (is (= :prebiotic (:phase e)))
    (is (some? evt))
    (is (= :event/ecology-phase-transition (:kind evt)))
    (is (= 42 (:tick evt)))
    (is (= #{99} (:entities evt)))))

(deftest maybe-advance-phase-no-event-when-no-transition
  (let [[e evt] (eco/maybe-advance-phase (eco/make-ecology) 0 1)]
    (is (= :abiotic (:phase e)))
    (is (nil? evt))))

(deftest extinction-resets-to-abiotic
  (let [e (assoc (eco/make-ecology) :phase :prokaryotic :biomass 0.04
                 :seeded true :complexity 0.1)]
    (is (true? (eco/extinction? e)))
    (let [e' (eco/extinguish e)]
      (is (= :abiotic (:phase e')))
      (is (false? (:seeded e')))
      (is (zero? (:biomass e')))
      (is (zero? (:complexity e'))))))

(deftest maybe-extinguish-emits-event
  (let [[e evt] (eco/maybe-extinguish
                 (assoc (eco/make-ecology) :phase :prokaryotic :biomass 0.04)
                 10 2)]
    (is (= :abiotic (:phase e)))
    (is (= :event/ecology-extinction (:kind evt)))))

(deftest apply-seed-requires-moisture
  (let [[e ok? reason] (eco/apply-seed (eco/make-ecology {:moisture 0.1}))]
    (is (false? ok?))
    (is (= :seed-failed-moisture reason))
    (is (false? (:seeded e)))))

(deftest apply-seed-requires-abiotic
  (let [[_e ok? reason] (eco/apply-seed
                         (eco/make-ecology {:moisture 0.3 :phase :prebiotic}))]
    (is (false? ok?))
    (is (= :seed-failed-not-abiotic reason))))

(deftest apply-seed-succeeds
  (let [[e ok? reason] (eco/apply-seed (eco/make-ecology {:moisture 0.3}))]
    (is (true? ok?))
    (is (= :seed-ok reason))
    (is (true? (:seeded e)))
    (is (= 0.04 (:biomass e)))))

(deftest apply-heat-and-cool
  (let [[e-hot _] (eco/apply-heat (eco/make-ecology {:temp 0.5}))
        [e-cold _] (eco/apply-cool (eco/make-ecology {:temp 0.5}))]
    (is (= 0.58 (:temp e-hot)))
    (is (= 0.42 (:temp e-cold)))))

(deftest apply-spark-requirements
  (testing "spark fails when not seeded"
    (let [[_ ok? reason] (eco/apply-spark (eco/make-ecology {:temp 0.5}))]
      (is (false? ok?))
      (is (= :spark-failed-not-seeded reason))))
  (testing "spark fails when not habitable"
    (let [[_ ok? reason] (eco/apply-spark (eco/make-ecology {:seeded true :temp 0.1}))]
      (is (false? ok?))
      (is (= :spark-failed-not-habitable reason))))
  (testing "spark succeeds"
    (let [[e ok?] (eco/apply-spark (eco/make-ecology {:seeded true :temp 0.5}))]
      (is (true? ok?))
      (is (= 0.06 (:biomass e)))
      (is (= 0.03 (:complexity e))))))

(deftest apply-grow-locked-before-prokaryotic
  (let [[_ ok? reason] (eco/apply-grow (eco/make-ecology {:phase :prebiotic}))]
    (is (false? ok?))
    (is (= :grow-locked reason))))

(deftest apply-grow-succeeds
  (let [[e ok?] (eco/apply-grow (eco/make-ecology {:phase :prokaryotic}))]
    (is (true? ok?))
    (is (= 0.12 (:biomass e)))
    (is (= 0.04 (:complexity e)))))

(deftest apply-evolve-locked-before-eukaryotic
  (let [[_ ok? reason] (eco/apply-evolve (eco/make-ecology {:phase :prokaryotic}))]
    (is (false? ok?))
    (is (= :evolve-locked reason))))

(deftest apply-evolve-succeeds
  (let [[e ok?] (eco/apply-evolve (eco/make-ecology {:phase :eukaryotic}))]
    (is (true? ok?))
    (is (= 0.15 (:complexity e)))
    (is (= 0.45 (:stability e)))))

(deftest tick-ecology-runs-passive-then-transition
  (let [base (assoc (eco/make-ecology)
                    :seeded true :moisture 0.2 :temp 0.5
                    :biomass 0.2 :phase :prebiotic)
        [e evts] (eco/tick-ecology base 5 7)]
    (is (= :prokaryotic (:phase e)))
    (is (= 1 (count evts)))
    (is (= :event/ecology-phase-transition (:kind (first evts))))))

(deftest values-remain-clamped
  (let [e (assoc (eco/make-ecology) :temp 0.99)
        [e-hot _] (eco/apply-heat e)]
    (is (= 1.0 (:temp e-hot)))))
