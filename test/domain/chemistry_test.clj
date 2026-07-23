(ns domain.chemistry-test
  "Tests for explicit element composition helpers in domain.chemistry."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.chemistry :as chem]
   [law.composition :as lcomp]))

(deftest blend-is-mass-weighted-and-normalized
  (let [c1 lcomp/primordial-composition
        c2 lcomp/solar-composition
        blended (chem/blend-compositions c1 1.0 c2 1.0)]
    (is (lcomp/composition-sums-to-unity? blended))
    (is (< (abs (- (:H blended)
                   (/ (+ (:H c1) (:H c2)) 2.0)))
           1e-12))
    (is (< (abs (- (:Fe blended)
                   (/ (:Fe c2) 2.0)))
           1e-12))))

(deftest burn-conserves-mass-and-converts-h-to-he
  (let [c lcomp/primordial-composition
        burned (chem/burn-composition c 0.1)
        mass-before (reduce + (vals c))
        mass-after  (reduce + (vals burned))]
    (is (< (abs (- mass-before mass-after)) 1e-12))
    (is (< (:H burned) (:H c)))
    (is (> (:He burned) (:He c)))))

(deftest partition-solids-separates-rock-and-gas
  (let [part (chem/partition-solids lcomp/solar-composition 150.0)]
    (is (pos? (reduce + 0.0 (vals (:solid part)))))
    (is (pos? (reduce + 0.0 (vals (:gas part)))))
    (is (> (get-in part [:gas :H] 0.0) 0.0))
    (is (> (get-in part [:solid :Fe] 0.0) 0.0))
    (is (lcomp/composition-sums-to-unity? (:solid part)))
    (is (lcomp/composition-sums-to-unity? (:gas part)))))

;; --- Sigmoid condensation (spec §6.1, decision §10.3) ------------------------

(deftest solid-fraction-is-half-condensed-at-tc
  (doseq [[_ tc] lcomp/condensation-temperatures]
    (is (< (abs (- (chem/solid-fraction tc tc) 0.5)) 1e-12)
        (str "s(Tc=" tc ") must be exactly 0.5"))))

(deftest solid-fraction-endpoints-per-volatility
  (testing "T >> Tc ⇒ ~0 (gas); T << Tc ⇒ ~1 (solid)"
    (is (< (chem/solid-fraction 300.0 20.0) 1e-3) "H at 300 K is gaseous")
    (is (> (chem/solid-fraction 300.0 1357.0) 0.999) "Fe at 300 K is solid")
    (is (> (chem/solid-fraction 100.0 170.0) 0.9) "water ice at 100 K")
    (is (< (chem/solid-fraction 300.0 170.0) 0.02) "water vapor at 300 K")))

(deftest solid-fraction-honors-30k-width
  (testing "at T = Tc ± ΔT the logistic sits at 1/(1+e^∓1) ≈ 0.731 / 0.269"
    (let [expected-high (/ 1.0 (+ 1.0 (Math/exp -1.0)))
          expected-low  (/ 1.0 (+ 1.0 (Math/exp 1.0)))]
      (is (< (abs (- (chem/solid-fraction 140.0 170.0) expected-high)) 1e-12))
      (is (< (abs (- (chem/solid-fraction 200.0 170.0) expected-low)) 1e-12)))))

(deftest solid-fraction-is-continuous-and-monotone
  (testing "no discontinuity across any Tc in the Lodders table"
    (doseq [[_ tc] lcomp/condensation-temperatures]
      (is (< (abs (- (chem/solid-fraction (+ tc 1.0) tc)
                     (chem/solid-fraction tc tc)))
             0.01)
          (str "1 K step across Tc=" tc " moves s by < 1%"))
      (is (> (chem/solid-fraction (- tc 1.0) tc)
             (chem/solid-fraction tc tc)
             (chem/solid-fraction (+ tc 1.0) tc))
          (str "monotone decreasing around Tc=" tc)))))

(deftest partition-solids-splits-partly-condensed-elements
  (testing "an element at T = Tc feeds BOTH phases proportionally (no cliff)"
    (let [part (chem/partition-solids {:O 0.5 :He 0.5} 170.0)
          ;; O sits exactly at its Tc (half condensed); He (Tc 4 K) is gaseous.
          ;; Under the old hard step the gas phase would carry NO O at all.
          s-o  (chem/solid-fraction 170.0 170.0)
          s-he (chem/solid-fraction 170.0 4.0)
          expected-gas-o (/ (* 0.5 (- 1.0 s-o))
                            (+ (* 0.5 (- 1.0 s-o))
                               (* 0.5 (- 1.0 s-he))))]
      (is (pos? (get-in part [:solid :O] 0.0)) "half the O condenses")
      (is (pos? (get-in part [:gas :O] 0.0)) "half the O stays gaseous")
      (is (< (abs (- (get-in part [:gas :O] 0.0) expected-gas-o)) 1e-12)
          "gas-phase O share matches the exact sigmoid split"))))

(deftest partition-solids-converges-to-hard-step-at-extremes
  (testing "far from every Tc the sigmoid partition matches the old hard step"
    (let [temp 300.0
          part (chem/partition-solids lcomp/solar-composition temp)
          tc-of #(double (get lcomp/condensation-temperatures % 50.0))
          hard-solid (into #{} (filter #(> (tc-of %) temp)) (keys lcomp/solar-composition))
          hard-gas   (into #{} (remove #(> (tc-of %) temp)) (keys lcomp/solar-composition))]
      (doseq [k hard-solid]
        (is (> (get-in part [:solid k] 0.0) 0.0)
            (str k " is in the solid phase")))
      (doseq [k hard-gas]
        (is (> (get-in part [:gas k] 0.0) 0.0)
            (str k " is in the gas phase")))
      ;; and the cross-phase leakage is negligible: gas carries < 1% of the
      ;; refractory mass it would carry under a mis-signed sigmoid
      (is (< (get-in part [:gas :Fe] 0.0) 0.01)))))

(deftest bulk-categories-smooth-across-snow-line
  (testing "ice fraction grows smoothly through the 170 K snow line, no jump"
    (let [below (:ice (chem/bulk-categories lcomp/solar-composition 169.0))
          above (:ice (chem/bulk-categories lcomp/solar-composition 171.0))]
      (is (< (abs (- below above)) 0.02))
      (is (pos? below) "some ice is already condensed just below the snow line")
      (is (pos? above) "some ice survives just above it"))))

(deftest partition-solids-water-ice-at-snow-line
  (testing "Just below 170 K water is solid; H and He remain gaseous."
    (let [part (chem/partition-solids lcomp/solar-composition 169.0)]
      (is (> (get-in part [:solid :O] 0.0) 0.0))
      (is (> (get-in part [:gas :H] 0.0) 0.0))
      (is (> (get-in part [:gas :He] 0.0) 0.0)))))

(deftest bulk-categories-sum-to-one
  (let [cats (chem/bulk-categories lcomp/solar-composition 200.0)
        total (+ (:gas cats) (:rock cats) (:metal cats) (:ice cats))]
    (is (< (abs (- total 1.0)) 1e-12))))

(deftest condensed-inventory-has-all-three-keys
  (let [inv (chem/condensed-inventory lcomp/solar-composition 200.0)]
    (is (contains? inv :solid))
    (is (contains? inv :gas))
    (is (contains? inv :categories))))

(deftest wind-composition-is-star-composition
  (is (= lcomp/solar-composition (chem/wind-composition lcomp/solar-composition))))

(deftest solar-composition-has-major-tracked-elements
  (is (every? #(contains? lcomp/solar-composition %) #{:H :He :O :C :Ne :Fe :N :Si :Mg :S :Al :Ca :Na :Ni})))
