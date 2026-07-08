(ns domain.orbital.kepler-test
  "Coverage tests for two-body Kepler utilities."
  (:require
   [clojure.math :as math] [clojure.test :refer [deftest is testing]]
   [domain.orbital.kepler :as kepler]
   [shape.spatial :as sp]))

(def ^:private AU 1.495978707e11)
(def ^:private GM 1.327124e20)
(def ^:private earth-year-seconds (* 365.25 86400.0))

(deftest kepler-period-matches-earth-year
  (let [T (kepler/kepler-period AU GM)]
    (is (< (abs (- T earth-year-seconds)) 1e6)
        (str "Expected ~" earth-year-seconds " s, got " T))))

(deftest mean-anomaly-wraps-and-scales
  (testing "at t0 the mean anomaly is zero"
    (is (< (abs (kepler/mean-anomaly 0.0 0.0 earth-year-seconds)) 1e-12)))
  (testing "after one full period the anomaly wraps to zero"
    (is (< (abs (kepler/mean-anomaly earth-year-seconds 0.0 earth-year-seconds)) 1e-12)))
  (testing "half a period yields pi"
    (is (< (abs (- (kepler/mean-anomaly (/ earth-year-seconds 2.0) 0.0 earth-year-seconds)
                   math/PI))
           1e-12))))

(deftest eccentric-anomaly-converges
  (testing "circular orbit solves to mean anomaly"
    (is (< (abs (- (kepler/eccentric-anomaly 1.23 0.0) 1.23)) 1e-12)))
  (testing "modest eccentricity converges"
    (let [M 0.5
          e 0.3
          E (kepler/eccentric-anomaly M e)]
      (is (< (abs (- E (* e (math/sin E)) M)) 1e-10))))
  (testing "high eccentricity still converges"
    (let [M 2.5
          e 0.9
          E (kepler/eccentric-anomaly M e)]
      (is (< (abs (- E (* e (math/sin E)) M)) 1e-10)))))

(deftest eccentric-anomaly-throws-on-no-convergence
  (testing "exceeding max iterations throws a clear exception"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"no convergence"
                          (kepler/eccentric-anomaly 1.0 0.5 1e-10 0)))))

(deftest true-anomaly-at-circular-orbit
  (testing "for e=0 true anomaly equals eccentric anomaly"
    (doseq [E [0.0 0.5 1.0 2.0 3.0]]
      (is (< (abs (- (kepler/true-anomaly E 0.0) E)) 1e-12)))))

(deftest orbital-state-is-periodic
  (testing "after one orbital period the body returns to its starting state"
    (let [elements {:a AU :e 0.0 :i 0.0 :Ω 0.0 :ω 0.0 :t0 0.0 :GM GM}
          T        (kepler/kepler-period AU GM)
          state0   (kepler/orbital-state elements 0.0)
          state1   (kepler/orbital-state elements T)]
      (is (< (sp/dist (:position state0) (:position state1)) 1e-3))
      (is (< (sp/dist (:velocity state0) (:velocity state1)) 1e-9)))))

(deftest orbital-state-conserves-energy
  (testing "for a circular orbit specific orbital energy is ~ -GM/2a"
    (let [elements {:a AU :e 0.0 :i 0.0 :Ω 0.0 :ω 0.0 :t0 0.0 :GM GM}
          state    (kepler/orbital-state elements (/ earth-year-seconds 4.0))
          r        (sp/len (:position state))
          v        (sp/len (:velocity state))
          energy   (- (* 0.5 v v) (/ GM r))
          expected (- (/ GM (* 2.0 AU)))]
      (is (< (abs (- energy expected)) 1e3)
          (str "Energy " energy " diverges from expected " expected)))))

(deftest orbital-state-handles-inclination-and-argument-of-periapsis
  (testing "non-zero i, Ω and ω rotate the orbit out of the reference plane"
    (let [elements {:a 1.0e8 :e 0.1 :i 0.5 :Ω 1.0 :ω 0.7 :t0 0.0 :GM 1.0e15}
          state    (kepler/orbital-state elements 0.0)]
      (is (= 3 (count (:position state))))
      (is (= 3 (count (:velocity state))))
      (is (not (zero? (nth (:position state) 2)))))))
