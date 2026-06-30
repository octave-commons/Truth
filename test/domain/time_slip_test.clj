(ns domain.time-slip-test
  "μ for the time-slip clock behaviour: when the observer's attention has lapsed
   (low coherence) over a low-complexity region, the per-tick step inflates and
   the unwatched universe fast-forwards. The slip DECISION is the observer's
   (`player/time-slip-threshold?`); the per-tick RESCALE is pacing's
   (`pacing/with-time-slip`). See domain/player.clj and domain/pacing.clj."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.player :as player]
   [domain.pacing :as pacing]))

;; --- the slip decision ------------------------------------------------------

(deftest threshold-fires-only-on-low-coherence-and-low-complexity
  (testing "low coherence AND low complexity ⇒ slip"
    (is (player/time-slip-threshold? {:coherence 0.2} 3)))
  (testing "high coherence holds the clock even in a dead region"
    (is (not (player/time-slip-threshold? {:coherence 0.5} 3))))
  (testing "rich complexity holds the clock even at low coherence"
    (is (not (player/time-slip-threshold? {:coherence 0.2} 10))))
  (testing "boundaries are strict (< 0.3 coherence, < 5 complexity)"
    (is (not (player/time-slip-threshold? {:coherence 0.3} 4)))
    (is (not (player/time-slip-threshold? {:coherence 0.29} 5)))
    (is (player/time-slip-threshold? {:coherence 0.29} 4))))

;; --- the per-tick rescale ---------------------------------------------------

(def ^:private base
  {:dt 1.0e9 :rate 6.0e10 :rate-yr 1.0 :softening 5.0e13})

(deftest slip-boosts-dt-and-re-derives-rate
  (let [p (pacing/with-time-slip base true)]
    (testing "dt is boosted by the slip factor"
      (is (= (* pacing/time-slip-factor 1.0e9) (:dt p))))
    (testing "rate is re-derived from the boosted dt (rate = dt·tps)"
      (is (= (* (:dt p) pacing/ticks-per-second) (:rate p)))
      (is (= (/ (:rate p) pacing/seconds-per-year) (:rate-yr p))))
    (testing "softening is untouched (tracks bulk radius, not the clock)"
      (is (= (:softening base) (:softening p))))
    (testing "and the slip is flagged for the HUD"
      (is (true? (:time-slipping? p))))))

(deftest slip-respects-the-ceiling
  (testing "a huge base step is capped at pacing-dt-slip-max while slipping"
    (let [p (pacing/with-time-slip (assoc base :dt 1.0e15) true)]
      (is (= pacing/pacing-dt-slip-max (:dt p)))
      (is (= (* pacing/pacing-dt-slip-max pacing/ticks-per-second) (:rate p))))))

(deftest no-slip-passes-through-unchanged
  (let [p (pacing/with-time-slip base false)]
    (testing "dt/rate/softening are unchanged when not slipping"
      (is (= (:dt base) (:dt p)))
      (is (= (:rate base) (:rate p)))
      (is (= (:softening base) (:softening p))))
    (testing "and the flag reads false (not absent)"
      (is (false? (:time-slipping? p))))))
