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
    (is (< (Math/abs (- (:H blended)
                        (/ (+ (:H c1) (:H c2)) 2.0)))
           1e-12))
    (is (< (Math/abs (- (:Fe blended)
                        (/ (:Fe c2) 2.0)))
           1e-12))))

(deftest burn-conserves-mass-and-converts-h-to-he
  (let [c lcomp/primordial-composition
        burned (chem/burn-composition c 0.1)
        mass-before (reduce + (vals c))
        mass-after  (reduce + (vals burned))]
    (is (< (Math/abs (- mass-before mass-after)) 1e-12))
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

(deftest partition-solids-water-ice-at-snow-line
  "Just below 170 K water is solid; H and He remain gaseous."
  (let [part (chem/partition-solids lcomp/solar-composition 169.0)]
    (is (> (get-in part [:solid :O] 0.0) 0.0))
    (is (> (get-in part [:gas :H] 0.0) 0.0))
    (is (> (get-in part [:gas :He] 0.0) 0.0))))

(deftest bulk-categories-sum-to-one
  (let [cats (chem/bulk-categories lcomp/solar-composition 200.0)
        total (+ (:gas cats) (:rock cats) (:metal cats) (:ice cats))]
    (is (< (Math/abs (- total 1.0)) 1e-12))))

(deftest condensed-inventory-has-all-three-keys
  (let [inv (chem/condensed-inventory lcomp/solar-composition 200.0)]
    (is (contains? inv :solid))
    (is (contains? inv :gas))
    (is (contains? inv :categories))))

(deftest wind-composition-is-star-composition
  (is (= lcomp/solar-composition (chem/wind-composition lcomp/solar-composition))))

(deftest solar-composition-has-major-tracked-elements
  (is (every? #(contains? lcomp/solar-composition %) #{:H :He :O :C :Ne :Fe :N :Si :Mg :S :Al :Ca :Na :Ni})))
