(ns law.composition-test
  "Coverage tests for explicit element composition contracts."
  (:require
   [clojure.test :refer [deftest is testing]]
   [law.composition :as comp]))

(deftest primordial-fractions-sum-to-one
  (is (< (Math/abs (- (+ comp/primordial-H comp/primordial-He
                         comp/primordial-D comp/primordial-He3
                         comp/primordial-Li7)
                      1.0))
         0.01)))

(deftest solar-composition-sums-to-one
  (is (comp/composition-sums-to-unity? comp/solar-composition))
  (is (< (Math/abs (- (comp/metallicity comp/solar-composition) comp/solar-metallicity))
         1e-4)))

(deftest mass-fraction?-accepts-valid
  (is (comp/mass-fraction? 0.0))
  (is (comp/mass-fraction? 0.5))
  (is (comp/mass-fraction? 1.0))
  (is (not (comp/mass-fraction? 1.1)))
  (is (not (comp/mass-fraction? -0.1)))
  (is (not (comp/mass-fraction? Double/NaN))))

(deftest positive-mass-fraction?-accepts-valid
  (is (comp/positive-mass-fraction? 0.001))
  (is (comp/positive-mass-fraction? 1.0))
  (is (not (comp/positive-mass-fraction? 0.0)))
  (is (not (comp/positive-mass-fraction? -0.1))))

(deftest trace-mass-fraction?-accepts-valid
  (is (comp/trace-mass-fraction? 0.0))
  (is (comp/trace-mass-fraction? 1e-20))
  (is (comp/trace-mass-fraction? 1.0))
  (is (not (comp/trace-mass-fraction? -1e-20))))

(deftest composition-sums-to-unity?-works
  (is (comp/composition-sums-to-unity? comp/primordial-composition))
  (is (comp/composition-sums-to-unity? comp/solar-composition))
  (is (comp/composition-sums-to-unity? {:H 0.5 :He 0.5}))
  (is (not (comp/composition-sums-to-unity? {:H 0.5 :He 0.4}))))

(deftest primordial-composition?-detects-primordial
  (is (comp/primordial-composition? comp/primordial-composition))
  (is (not (comp/primordial-composition? comp/solar-composition))))

(deftest metallicity-computes-z
  (is (< (Math/abs (- (comp/metallicity comp/primordial-composition) 0.0)) 1e-3))
  (is (< (Math/abs (- (comp/metallicity comp/solar-composition) comp/solar-metallicity)) 1e-4))
  (is (< (Math/abs (- (comp/metallicity {:H 0.7 :He 0.1}) 0.2)) 1e-12)))

(deftest normalize-preserves-ratios
  (let [n (comp/normalize {:H 1.0 :He 0.25})]
    (is (comp/composition-sums-to-unity? n))
    (is (< (Math/abs (- (:H n) 0.8)) 1e-12))))

(deftest contracts-are-constructs
  (is (= ::comp/composition (:id comp/composition-contract)))
  (is (= ::comp/primordial-composition (:id comp/primordial-composition-contract))))
