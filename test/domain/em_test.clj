(ns domain.em-test
  "Tests for the Phase 0 electromagnetic / MHD-lite layer."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.em     :as em]
   [law.field     :as lf]
   [shape.spatial :as sp]))

(deftest test-magnetic-pressure
  (testing "P_B = |B|²/(2μ₀), positive for a real field, zero for none"
    (is (pos? (em/magnetic-pressure [0.0 0.0 1.0e-9])))
    (is (zero? (em/magnetic-pressure [0.0 0.0 0.0])))
    (is (zero? (em/magnetic-pressure nil))))
  (testing "scales with the square of the field magnitude"
    (let [p1 (em/magnetic-pressure [0.0 0.0 1.0e-9])
          p2 (em/magnetic-pressure [0.0 0.0 2.0e-9])]
      (is (< (abs (- (/ p2 p1) 4.0)) 1e-6) "doubling B quadruples P_B"))))

(deftest test-alfven-speed
  (testing "Alfvén speed is positive with a field and zero without"
    (is (pos? (em/alfven-speed [0.0 0.0 1.0e-9] 1.0e-18)))
    (is (zero? (em/alfven-speed [0.0 0.0 0.0] 1.0e-18)))
    (is (zero? (em/alfven-speed [0.0 0.0 1.0e-9] 0.0)))))

(deftest test-flux-freeze
  (testing "Compression amplifies the frozen-in field by (ρ'/ρ)^(2/3)"
    (let [b   [0.0 0.0 1.0e-9]
          b'  (em/flux-freeze b 1.0 8.0)] ;; density ×8 → field ×8^(2/3)=4
      (is (< (abs (- (sp/len b') (* 4.0 (sp/len b)))) 1e-15))
      (is (zero? (first b')) "direction is preserved")
      (is (zero? (second b')))))
  (testing "Expansion weakens the field"
    (is (< (sp/len (em/flux-freeze [0.0 0.0 1.0e-9] 8.0 1.0))
           1.0e-9)))
  (testing "Amplification is clamped to the bound — no blow-up"
    (let [b' (em/flux-freeze [0.0 0.0 1.0] 1.0 1.0e30)]
      (is (<= (sp/len b') lf/max-b-field))
      (is (lf/bounded-b-field? b')))))

(deftest test-magnetic-support
  (testing "A strongly magnetized small clump is supported against gravity"
    (is (em/magnetically-supported?
         {:b-field [0.0 0.0 1.0e-4] :mass 1.0e20 :radius 1.0e14})))
  (testing "A massive core is super-critical and is NOT held by its field"
    (is (not (em/magnetically-supported?
              {:b-field [0.0 0.0 1.0e-9] :mass 1.8e31 :radius 5.0e14}))))
  (testing "No field means no support"
    (is (not (em/magnetically-supported?
              {:b-field nil :mass 1.0e20 :radius 1.0e14})))))

(deftest test-resistive-decay
  (testing "Diffuse gas (huge radius) keeps its field; dense cores shed flux"
    (let [b [0.0 0.0 1.0e-9]]
      (is (< (abs (- (sp/len (em/resistive-decay b 1.0e17 1.0e9))
                     (sp/len b)))
             1e-20) "negligible decay in the diffuse nebula")
      (is (< (sp/len (em/resistive-decay b 1.0e3 1.0e9))
             (sp/len b)) "real decay in a compact core")))
  (testing "Decay never amplifies and never goes negative-magnitude"
    (let [b' (em/resistive-decay [0.0 0.0 1.0e-9] 1.0e3 1.0e12)]
      (is (<= 0.0 (sp/len b') 1.0e-9)))))

(deftest test-seed-field
  (testing "Seed field is a finite vec3 aligned with the spin (z) axis"
    (let [b (em/seed-field)]
      (is (lf/finite-vec3? b))
      (is (zero? (first b)))
      (is (zero? (second b)))
      (is (pos? (nth b 2))))))
