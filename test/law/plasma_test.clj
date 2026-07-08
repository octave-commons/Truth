(ns law.plasma-test
  "Coverage tests for plasma / stellar-wind / atmospheric-escape contracts."
  (:require
   [clojure.test :refer [deftest is testing]]
   [law.plasma :as plasma]))

(deftest physical-constants-are-positive
  (is (pos? plasma/solar-mass-loss-rate))
  (is (pos? plasma/solar-wind-speed))
  (is (pos? plasma/solar-alfven-radius)))

(deftest positive-si?-accepts-valid
  (is (plasma/positive-si? 1.0))
  (is (plasma/positive-si? 1e-30))
  (is (not (plasma/positive-si? 0.0)))
  (is (not (plasma/positive-si? -1.0)))
  (is (not (plasma/positive-si? Double/NaN)))
  (is (not (plasma/positive-si? Double/POSITIVE_INFINITY))))

(deftest non-negative-si?-accepts-valid
  (is (plasma/non-negative-si? 0.0))
  (is (plasma/non-negative-si? 1.0))
  (is (not (plasma/non-negative-si? -1.0))))

(deftest ionization-fraction?-accepts-valid
  (is (plasma/ionization-fraction? 0.0))
  (is (plasma/ionization-fraction? 0.5))
  (is (plasma/ionization-fraction? 1.0))
  (is (not (plasma/ionization-fraction? 1.1)))
  (is (not (plasma/ionization-fraction? -0.1))))

(deftest escape-regime?-accepts-valid
  (is (plasma/escape-regime? :energy-limited))
  (is (plasma/escape-regime? :recombination-limited))
  (is (plasma/escape-regime? :blow-off))
  (is (not (plasma/escape-regime? :unknown))))

(deftest event-kind?-accepts-valid
  (is (plasma/event-kind? :flare))
  (is (plasma/event-kind? :cme))
  (is (plasma/event-kind? :supernova))
  (is (plasma/event-kind? :grb))
  (is (not (plasma/event-kind? :wind))))

(deftest parker-mass-loss-scales
  (is (zero? (plasma/parker-mass-loss 0.0 1.0 1.0)))
  (is (zero? (plasma/parker-mass-loss 1.0 0.0 1.0)))
  (is (zero? (plasma/parker-mass-loss 1.0 1.0 0.0)))
  (let [mdot (plasma/parker-mass-loss 3.828e26 6.18e5 2.5e-13)]
    (is (> mdot 0.0))
    (is (Double/isFinite mdot))))

(deftest ram-pressure-at-earth
  (let [P (plasma/ram-pressure plasma/solar-mass-loss-rate plasma/solar-wind-speed 1.496e11)]
    (is (> P 0.0))
    (is (< P 1e-8))))

(deftest ram-pressure-is-zero-with-invalid-inputs
  (is (zero? (plasma/ram-pressure 0.0 1.0 1.0)))
  (is (zero? (plasma/ram-pressure 1.0 0.0 1.0)))
  (is (zero? (plasma/ram-pressure 1.0 1.0 0.0))))

(deftest xuv-flux-falls-with-distance-squared
  (let [F1 (plasma/xuv-flux-at 1.0e22 1.0e10)
        F2 (plasma/xuv-flux-at 1.0e22 2.0e10)]
    (is (> F1 0.0))
    (is (> F1 F2))
    (is (< (abs (- F2 (/ F1 4.0))) 1e-6))))

(deftest energy-limited-escape-is-positive
  (let [mdot (plasma/energy-limited-escape 0.1 6.371e6 5.972e24 0.15)]
    (is (> mdot 0.0))
    (is (Double/isFinite mdot))))

(deftest recombination-timescale-is-positive
  (let [t (plasma/recombination-timescale 1.0e15)]
    (is (> t 0.0))
    (is (Double/isFinite t))))

(deftest recombination-timescale-is-infinite-for-zero-density
  (is (= Double/POSITIVE_INFINITY (plasma/recombination-timescale 0.0))))

(deftest flow-timescale-is-positive
  (let [t (plasma/flow-timescale 6.371e6)]
    (is (> t 0.0))))

(deftest escape-regime-uses-flux-fallback
  (is (= :energy-limited (plasma/escape-regime 0.01 6.371e6)))
  (is (= :recombination-limited (plasma/escape-regime 0.5 6.371e6)))
  (is (= :blow-off (plasma/escape-regime 10.0 6.371e6))))

(deftest escape-regime-uses-electron-density
  (testing "low density = long recombination time = energy-limited"
    (is (= :energy-limited (plasma/escape-regime 10.0 6.371e6 1.0e10))))
  (testing "very high density = recombination-limited or blow-off"
    (is (#{:recombination-limited :blow-off}
         (plasma/escape-regime 10.0 6.371e6 1.0e20)))))
