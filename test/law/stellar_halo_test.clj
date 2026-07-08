(ns law.stellar-halo-test
  "μ for the dark-halo influence field: law.stellar/plummer-acceleration and
   law.stellar/virial-speed — the physical basis of the observer halo and warp
   wells (a large, diffuse body of mass that binds matter and cannot slingshot
   it from a point core)."
  (:require
   [clojure.math :as math] [clojure.test :refer [deftest is testing]]
   [law.stellar :as law]))

(def ^:private M 4.0e30)   ;; default seeded cloud mass
(def ^:private R 2.0e16)   ;; default seeded cloud radius
(def ^:private a 5.0e15)   ;; a focus-radius-scale halo

(deftest virial-speed-is-sqrt-GM-over-R
  (is (< (abs (- (law/virial-speed M R)
                 (math/sqrt (/ (* law/G M) R))))
         1e-9))
  (testing "degenerate scales are 0, not NaN/Infinity"
    (is (zero? (law/virial-speed 0.0 R)))
    (is (zero? (law/virial-speed M 0.0)))
    (is (zero? (law/virial-speed nil nil)))))

(deftest plummer-field-is-diffuse-not-a-point-kick
  (testing "zero force at the centre — no core that can slingshot"
    (is (zero? (law/plummer-acceleration M a 0.0))))
  (testing "the pull peaks at r = a/√2"
    (let [r-peak (/ a (math/sqrt 2.0))
          g-peak (law/plummer-acceleration M a r-peak)]
      (is (> g-peak (law/plummer-acceleration M a (* 0.5 r-peak))))
      (is (> g-peak (law/plummer-acceleration M a (* 2.0 r-peak))))
      (testing "with the analytic peak value 2GM/(3√3 a²)"
        (is (< (abs (- g-peak (/ (* 2.0 law/G M)
                                 (* 3.0 (math/sqrt 3.0) a a))))
               (* 1e-12 g-peak))))))
  (testing "Keplerian GM/r² far outside the scale radius"
    (let [r (* 100.0 a)]
      (is (< (abs (- (law/plummer-acceleration M a r)
                     (/ (* law/G M) (* r r))))
             (* 1e-3 (/ (* law/G M) (* r r)))))))
  (testing "degenerate inputs are 0, not NaN"
    (is (zero? (law/plummer-acceleration 0.0 a 1.0e15)))
    (is (zero? (law/plummer-acceleration nil nil nil)))))

(deftest plummer-matches-the-integrator-softened-field
  (testing "v_c²/r = g: the halo is the same field family softened orbits use"
    (doseq [r [1.0e14 1.0e15 5.0e15 3.0e16]]
      (let [vc (law/softened-circular-speed M r a)
            g  (law/plummer-acceleration M a r)]
        (is (< (abs (- (/ (* vc vc) r) g)) (* 1e-9 g)))))))
