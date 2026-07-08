(ns domain.regime-test
  "Tests for the Phase 0 dimensionless-number regime classifier.

   Value assertions here compute each diagnostic's analytic value independently
   (from the SI constants) and compare — not just its sign. That pins the actual
   arithmetic, so a mutated operator (e.g. * -> / in c_s = √(γp/ρ)) is caught,
   not just an accidental sign flip."
  (:require
   [clojure.math :as math] [clojure.test :refer [deftest testing is]]
   [domain.regime :as regime]
   [domain.em :as em]
   [shape.spatial :as sp]
   [law.field :as lf]
   [law.stellar :as ls]))

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

(defn approx=
  "Relative equality for doubles (default 1e-9), tolerant near zero."
  ([a b] (approx= a b 1e-9))
  ([a b rel]
   (let [a (double a) b (double b)]
     (<= (abs (- a b))
         (* rel (max 1.0 (abs a) (abs b)))))))

(deftest test-sound-speed
  (testing "Sound speed equals the analytic c_s = √(γ p / ρ)"
    (is (approx= (regime/sound-speed dense-warm)
                 (math/sqrt (/ (* lf/gamma (double (:pressure dense-warm)))
                               (double (:density dense-warm)))))))
  (testing "Non-positive pressure or density yields zero"
    (is (zero? (regime/sound-speed {:pressure 0.0 :density 1.0})))
    (is (zero? (regime/sound-speed {:pressure 1e5 :density 0.0}))))
  (testing "Missing pressure or density yields zero (guard, not NaN/throw)"
    ;; Kills `and -> or`: with `or` the guard passes on a present key and the
    ;; body then dereferences the missing one, throwing on (double nil).
    (is (zero? (regime/sound-speed {:density 1.0})))
    (is (zero? (regime/sound-speed {:pressure 1e5})))
    (is (zero? (regime/sound-speed {})))))

(deftest test-plasma-beta
  (testing "β equals P_gas / P_B"
    (is (approx= (regime/plasma-beta dense-warm)
                 (/ (double (:pressure dense-warm))
                    (em/magnetic-pressure (:b-field dense-warm))))))
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
  (testing "Mach number equals |v| / c_s"
    (let [moving (assoc dense-warm :velocity [1e4 0.0 0.0])]
      (is (approx= (regime/mach moving)
                   (/ (sp/len (:velocity moving))
                      (regime/sound-speed moving))))))
  (testing "Alfvén-Mach number equals |v| / v_A"
    (let [moving (assoc dense-warm :velocity [1e4 0.0 0.0])]
      (is (approx= (regime/alfven-mach moving)
                   (/ (sp/len (:velocity moving))
                      (em/alfven-speed (:b-field moving)
                                       (:density moving)))))))
  (testing "Zero sound/Alfvén speed with motion is infinite Mach"
    ;; c_s = 0 (no pressure) but the flow moves: ℳ -> ∞, not 0 or NaN.
    (is (= Double/POSITIVE_INFINITY
           (regime/mach {:pressure 0.0 :density 1.0 :velocity [1.0 0.0 0.0]})))
    (is (= Double/POSITIVE_INFINITY
           (regime/alfven-mach {:b-field [0.0 0.0 0.0] :density 1.0
                                :velocity [1.0 0.0 0.0]})))))

(deftest test-jeans-ratio
  (testing "Jeans ratio equals radius / λ_J with λ_J = c_s √(π / (G ρ))"
    (let [{:keys [density temperature radius]} diffuse-cloud
          c-s (math/sqrt (/ (* ls/k-B (double temperature)) ls/m-H))
          lam (* c-s (math/sqrt (/ math/PI (* ls/G (double density)))))]
      (is (approx= (regime/jeans-ratio diffuse-cloud)
                   (/ (double radius) lam)))))
  (testing "Diffuse massive cold gas is Jeans-unstable (ratio ≥ 1)"
    (is (>= (regime/jeans-ratio diffuse-cloud) 1.0)))
  (testing "Small dense warm gas is stable (ratio < 1)"
    (is (< (regime/jeans-ratio dense-warm) 1.0)))
  (testing "Missing density/temperature/radius yields zero (guard, not throw)"
    ;; Kills `and -> or`: `or` lets a present key satisfy the guard, then the
    ;; body dereferences a missing one via (double nil).
    (is (zero? (regime/jeans-ratio {:density 1.0 :radius 1e5})))
    (is (zero? (regime/jeans-ratio {:temperature 10.0 :radius 1e5})))
    (is (zero? (regime/jeans-ratio {:density 1.0 :temperature 10.0})))))

(deftest test-classify-boundaries
  ;; The classifier's thresholds are closed on the unstable/magnetized side:
  ;; L/λ_J ≥ 1, β < 1, M_A ≤ 1 (see classify docstring). Pin each boundary with
  ;; an input that lands *exactly* on it — this is the only place a `>=`↔`>` /
  ;; `<`↔`<=` flip changes the tag, so it is what kills those mutants.
  (testing "jeans ratio exactly 1 ⇒ gravitationally unstable (≥, not >)"
    (let [density 1e-18 temperature 10.0
          c-s (math/sqrt (/ (* ls/k-B (double temperature)) ls/m-H))
          lam (* c-s (math/sqrt (/ math/PI (* ls/G (double density)))))
          cell {:density density :temperature temperature :radius lam
                :pressure 1e-13 :velocity [0.0 0.0 0.0] :b-field [0.0 0.0 1e-9]}]
      (is (== 1.0 (regime/jeans-ratio cell)) "radius = λ_J ⇒ ratio is exactly 1")
      (is (= :gravitationally-unstable (:regime (regime/classify cell))))))
  (testing "β exactly 1 is NOT magnetized (β < 1 is strict) ⇒ gravity-hydro"
    (let [bfield [0.0 0.0 1.0]
          cell {:density 5500.0 :temperature 300.0 :radius 1e5
                :pressure (em/magnetic-pressure bfield) ; ⇒ β = P_B / P_B = 1
                :velocity [0.0 0.0 0.0] :b-field bfield}]
      (is (== 1.0 (regime/plasma-beta cell)))
      (is (< (regime/jeans-ratio cell) 1.0))
      (is (= :gravity-hydro (:regime (regime/classify cell))))))
  (testing "Alfvén-Mach exactly 1 IS magnetized (M_A ≤ 1 is inclusive) ⇒ mhd"
    (let [density 2.0
          bfield [0.0 0.0 (math/sqrt (* lf/mu-0 density))] ; ⇒ v_A = 1
          va (em/alfven-speed bfield density)
          cell {:density density :temperature 10.0 :radius 1.0
                :pressure 1e-3 :velocity [va 0.0 0.0] :b-field bfield}]
      (is (< (regime/plasma-beta cell) 1.0))
      (is (< (regime/jeans-ratio cell) 1.0))
      (is (== 1.0 (regime/alfven-mach cell)) "|v| = v_A ⇒ M_A is exactly 1")
      (is (= :mhd-dominated (:regime (regime/classify cell)))))))

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
