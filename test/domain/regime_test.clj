(ns domain.regime-test
  "Tests for the Phase 0 dimensionless-number regime classifier."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.regime :as regime]))

(def diffuse-cloud
  "Diffuse, massive, cold, field-weak — the Jeans-unstable nebula case."
  {:density 1e-18 :temperature 10.0 :radius 1e17
   :pressure 1e-13 :velocity [0.0 0.0 0.0] :b-field [0.0 0.0 1e-9]})

(def dense-warm
  "Small, dense, warm — stable against collapse."
  {:density 5500.0 :temperature 300.0 :radius 1e5
   :pressure 1e5 :velocity [0.0 0.0 0.0] :b-field [0.0 0.0 1e-9]})

(def magnetized
  "Weak gas pressure, strong field — magnetically dominated."
  {:density 1.0 :temperature 10.0 :radius 1e5
   :pressure 1e-3 :velocity [0.0 0.0 0.0] :b-field [0.0 0.0 1.0]})

(deftest test-sound-speed
  (testing "Sound speed rises with temperature/pressure, zero without state"
    (is (pos? (regime/sound-speed dense-warm)))
    (is (zero? (regime/sound-speed {:pressure 0.0 :density 1.0})))))

(deftest test-plasma-beta
  (testing "β ≫ 1 when gas pressure dominates a weak field"
    (is (> (regime/plasma-beta dense-warm) 1.0)))
  (testing "β ≪ 1 when a strong field dominates weak gas pressure"
    (is (< (regime/plasma-beta magnetized) 1.0)))
  (testing "β is infinite with no field"
    (is (= Double/POSITIVE_INFINITY
           (regime/plasma-beta {:pressure 1e5 :b-field [0.0 0.0 0.0]})))))

(deftest test-mach-numbers
  (testing "At rest both Mach numbers are zero"
    (is (zero? (regime/mach diffuse-cloud)))
    (is (zero? (regime/alfven-mach diffuse-cloud))))
  (testing "A moving flow has positive Mach and Alfvén-Mach numbers"
    (let [moving (assoc dense-warm :velocity [1e4 0.0 0.0])]
      (is (pos? (regime/mach moving)))
      (is (pos? (regime/alfven-mach moving))))))

(deftest test-jeans-ratio
  (testing "Diffuse massive cold gas is Jeans-unstable (ratio ≥ 1)"
    (is (>= (regime/jeans-ratio diffuse-cloud) 1.0)))
  (testing "Small dense warm gas is stable (ratio < 1)"
    (is (< (regime/jeans-ratio dense-warm) 1.0))))

(deftest test-classify
  (testing "Diffuse Jeans-unstable cloud classifies as gravitationally unstable"
    (is (= :gravitationally-unstable (:regime (regime/classify diffuse-cloud)))))
  (testing "Stable, gas-pressure-dominated clump is gravity-hydro"
    (is (= :gravity-hydro (:regime (regime/classify dense-warm)))))
  (testing "Strong-field, low-β, sub-Alfvénic clump is MHD-dominated"
    (is (= :mhd-dominated (:regime (regime/classify magnetized)))))
  (testing "Classification carries the raw diagnostics"
    (let [{:keys [numbers]} (regime/classify dense-warm)]
      (is (contains? numbers :beta))
      (is (contains? numbers :mach))
      (is (contains? numbers :alfven-mach))
      (is (contains? numbers :jeans-ratio)))))
