(ns domain.stellar-test
  "Tests for the stellar nebula/star formation domain helpers and ECS systems.
   These are epistemic contracts: every physical invariant asserted here must
   hold before downstream systems (disc formation, EM, regime) can be trusted."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.stellar :as stellar]
   [domain.em      :as em]
   [domain.ecs.core :as ecs]
   [domain.ecs.event :as event]
   [domain.ecs.components :as c]
   [domain.physics.collision :as collision]
   [shape.spatial :as sp]))

;; --- Angular momentum helpers ------------------------------------------------

(deftest test-orbital-angular-momentum
  (testing "L = m (r × v) is perpendicular to the orbital plane"
    (let [m 1e30
          r [1e15 0.0 0.0]
          v [0.0 2e3 0.0]
          L (stellar/orbital-angular-momentum m r v)]
      (is (= 0.0 (first L)))
      (is (= 0.0 (second L)))
      (is (pos? (nth L 2)))
      (is (< (Math/abs (- (nth L 2) (* m 1e15 2e3))) 1e20)))))

(deftest test-moment-of-inertia-sphere
  (testing "I = (2/5) M R² for a uniform sphere"
    (is (< (Math/abs (- (stellar/moment-of-inertia 1.0 1.0) 0.4)) 1e-12))
    (is (< (Math/abs (- (stellar/moment-of-inertia 2e30 1e9) (* 0.4 2e30 1e18))) 1e20))))

(deftest test-spin-from-angular-momentum
  (testing "ω = L/I about the rotation axis"
    (let [L [0.0 0.0 1e40]
          I (stellar/moment-of-inertia 2e30 1e9)
          w (stellar/spin-from-angular-momentum [0.0 0.0 1e40] 2e30 1e9)]
      (is (= 0.0 (first w)))
      (is (= 0.0 (second w)))
      (is (pos? (nth w 2)))
      (is (< (Math/abs (- (nth w 2) (/ 1e40 I))) 1e-10)))))

;; --- Seeding -----------------------------------------------------------------

(deftest test-seed-clump-angular-momentum
  (testing "A clump seeded with position and velocity carries orbital L"
    (let [cm (stellar/seed-clump {:position [1e15 0.0 0.0]
                                  :velocity [0.0 2e3 0.0]
                                  :mass 1e30
                                  :radius 1e14})]
      (is (some? (get cm c/angular-momentum)))
      (is (pos? (nth (get cm c/angular-momentum) 2)))
      (is (some? (get cm c/spin)))
      (is (< (Math/abs (- (get cm c/oblateness) 1.0)) 1e-12)
          "non-rotating seed starts spherical"))))

;; --- Merge conservation ------------------------------------------------------

(deftest test-merge-conserves-angular-momentum
  (testing "Two colliding clumps merge and conserve total L"
    (let [base    (-> (ecs/empty-world)
                      (event/with-ledger)
                      (event/register-handler :event/collision
                                              stellar/stellar-merge-handler))
          [w1 ea] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e30
                                             :radius 1.0
                                             :angular-momentum [0.0 0.0 1e42]})
          [w2 eb] (stellar/spawn-clump w1   {:position [0.5 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e30
                                             :radius 1.0
                                             :angular-momentum [0.0 0.0 5e41]})
          La      (ecs/get-component w2 ea c/angular-momentum)
          Lb      (ecs/get-component w2 eb c/angular-momentum)
          total-L (sp/v+ La Lb)
          w3      (collision/collision-detection-system w2)
          survivor (first (ecs/entities-with w3 c/mass))
          final-L (ecs/get-component w3 survivor c/angular-momentum)]
      (is (= 1 (count (ecs/entities-with w3 c/mass))))
      (is (every? (fn [[a b]] (< (Math/abs (- a b)) 1e30))
                  (map vector final-L total-L))))))

(deftest test-merge-conserves-orbital-angular-momentum
  (testing "Orbital L of two moving bodies is added to the merged body's spin"
    (let [base    (-> (ecs/empty-world)
                      (event/with-ledger)
                      (event/register-handler :event/collision
                                              stellar/stellar-merge-handler))
          ;; two equal masses orbiting each other in the xy plane
          [w1 ea] (stellar/spawn-clump base {:position [1e12 0.0 0.0]
                                             :velocity [0.0 1e4 0.0]
                                             :mass 1e25
                                             :radius 5.0e11})
          [w2 eb] (stellar/spawn-clump w1   {:position [-1e12 0.0 0.0]
                                             :velocity [0.0 -1e4 0.0]
                                             :mass 1e25
                                             :radius 5.0e11})
          w3      (collision/collision-detection-system w2)
          survivor (first (ecs/entities-with w3 c/mass))
          L       (ecs/get-component w3 survivor c/angular-momentum)]
      ;; total L is ~ 2 * m * r * v  = 2e42 kg m²/s in z
      (is (pos? (nth L 2)))
      (is (>= (nth L 2) 1e41)))))

(deftest test-equivalent-radius
  (testing "r_eq of a sphere equals its radius"
    (is (< (Math/abs (- (stellar/equivalent-radius 5.0 5.0) 5.0)) 1e-12)))
  (testing "Flattening at fixed volume increases equatorial radius"
    (let [r 5.0
          c 3.0
          a (Math/sqrt (/ (* r r r) c))]
      (is (< (Math/abs (- (stellar/equivalent-radius a c) r)) 1e-12)
          "oblate spheroid with same volume as sphere r=5"))))

(deftest test-oblate-density-conserves-mass
  (testing "Mass = density × oblate volume"
    (let [m 2e30 a 1e15 c 5e14
          rho (stellar/oblate-density m a c)
          v (* (/ 4.0 3.0) Math/PI a a c)]
      (is (< (Math/abs (- (* rho v) m)) 1e10)))))

(deftest test-rotation-axis
  (testing "Unit vector along L; defaults to z when L is zero"
    (is (= [0.0 0.0 1.0] (stellar/rotation-axis [0.0 0.0 0.0])))
    (let [n (stellar/rotation-axis [0.0 0.0 1e40])]
      (is (< (Math/abs (- (sp/len n) 1.0)) 1e-12))
      (is (= [0.0 0.0 1.0] n)))))

(deftest test-collapse-flattens-rotating-clump
  (testing "A rotating protostar becomes oblate as it contracts"
    (let [base (ecs/empty-world)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e30
                                             :radius 1e15
                                             :matter-state :protostar
                                             :angular-momentum [0.0 0.0 1e45]})
          a0   (ecs/get-component w eid c/radius)
          ob0  (ecs/get-component w eid c/oblateness)
          w2   (stellar/collapse-system w)
          a1   (ecs/get-component w2 eid c/radius)
          c1   (* a1 (ecs/get-component w2 eid c/oblateness))
          m    (ecs/get-component w2 eid c/mass)
          rho1 (ecs/get-component w2 eid c/density)]
      (is (< (ecs/get-component w2 eid c/oblateness) ob0)
          "oblateness drops below spherical")
      (is (< c1 a1) "polar radius is smaller than equatorial radius")
      (is (< (Math/abs (- (stellar/oblate-density m a1 c1) rho1)) 1e-6)
          "density matches oblate spheroid volume"))))

(deftest test-collapse-mass-conserved
  (testing "Mass is unchanged after oblate collapse"
    (let [base (ecs/empty-world)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e30
                                             :radius 1e15
                                             :matter-state :protostar
                                             :angular-momentum [0.0 0.0 1e45]})
          m0   (ecs/get-component w eid c/mass)
          w2   (stellar/collapse-system w)
          m1   (ecs/get-component w2 eid c/mass)]
      (is (= m0 m1)))))

(deftest test-anisotropic-flux-freeze
  (testing "Collapse along field lines amplifies B more than isotropic collapse"
    (let [b0 [0.0 0.0 1.0e-9]
              rho0 1.0 rho1 8.0
              b-iso (em/flux-freeze b0 rho0 rho1 0.0)
              b-ani (em/flux-freeze b0 rho0 rho1 1.0)]
          (is (> (sp/len b-ani) (sp/len b-iso))
              "field-stretching collapse amplifies B more")
          (is (< (Math/abs (- (sp/len b-iso) (* 4.0 (sp/len b0)))) 1e-15)
              "isotropic still scales as ρ^(2/3)"))))

;; --- Collapse conservation ---------------------------------------------------

(deftest test-collapse-conserves-angular-momentum
  (testing "A contracting protostar spins up while L stays constant"
    (let [base (ecs/empty-world)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e30
                                             :radius 1e15
                                             :matter-state :protostar
                                             :angular-momentum [0.0 0.0 1e45]})
          L0   (ecs/get-component w eid c/angular-momentum)
          spin0 (ecs/get-component w eid c/spin)
          w2   (stellar/collapse-system w)
          L1   (ecs/get-component w2 eid c/angular-momentum)
          spin1 (ecs/get-component w2 eid c/spin)
          r1   (ecs/get-component w2 eid c/radius)]
      (is (every? (fn [[a b]] (< (Math/abs (- a b)) 1e30))
                  (map vector L1 L0))
          "angular momentum is conserved")
      (is (> (sp/len spin1) (sp/len spin0)) "spin increases as radius shrinks")
      (is (< r1 1e15) "radius shrinks"))))

(deftest test-collapse-flattens-rotating-clump
  (testing "A rotating protostar becomes oblate as it contracts"
    (let [base (ecs/empty-world)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e30
                                             :radius 1e15
                                             :matter-state :protostar
                                             :angular-momentum [0.0 0.0 1e45]})
          w2   (stellar/collapse-system w)
          ob   (ecs/get-component w2 eid c/oblateness)]
      (is (some? ob))
      (is (< ob 1.0) "oblateness drops below spherical"))))

;; --- Contraction floor (no pinpoint stars) -----------------------------------

(deftest test-main-sequence-radius
  (testing "Main-sequence radius is ~R_sun at 1 M_sun and grows with mass"
    (let [law-ns (requiring-resolve 'law.stellar/main-sequence-radius)]
      (is (< (Math/abs (- (law-ns 1.989e30) 6.957e8)) 1e7)
          "≈ solar radius at one solar mass")
      (is (< (law-ns 5e29) (law-ns 1.989e30))
          "a lower-mass star is smaller")
      (is (> (law-ns 4e30) (law-ns 1.989e30))
          "a higher-mass star is larger"))))

(deftest test-collapse-floors-at-main-sequence
  (testing "A protostar contracts toward, but never below, its main-sequence radius"
    (let [floor   ((requiring-resolve 'law.stellar/main-sequence-radius) 2e30)
          ;; Contraction is rate-limited to τ=:phase0/contraction-time; pick
          ;; dt ≫ τ so each step contracts at the full per-tick cap
          ;; (collapse-fraction) and the core actually reaches the floor within a
          ;; bounded number of steps — this test pins the floor invariant, not
          ;; the production (tens-of-Myr) contraction pace.
          base    (assoc (ecs/empty-world) :sim/dt 1e15 :phase0/contraction-time 1e12)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e30
                                             :radius 1e15
                                             :matter-state :protostar
                                             :angular-momentum [0.0 0.0 0.0]})
          ;; many contraction steps — old code would halve to a point each tick
          w'      (nth (iterate stellar/collapse-system w) 80)
          r       (ecs/get-component w' eid c/radius)]
      (is (>= r (* 0.999 floor)) "radius does not collapse below the floor")
      (is (< (Math/abs (- r floor)) (* 0.01 floor)) "radius settles AT the floor")
          ;; one more step does not shrink it further
          (is (< (Math/abs (- r (ecs/get-component (stellar/collapse-system w') eid c/radius)))
                 (* 1e-6 floor))
              "contraction has stopped"))))

;; --- Ignition ----------------------------------------------------------------

(deftest test-collapse-heats-toward-fusion
  (testing "A contracting protostar heats adiabatically and can reach fusion"
    (let [base (ecs/empty-world)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e30
                                             :radius 1e10
                                             :matter-state :protostar
                                             :temperature 1e6
                                             :density 4.7})
          w' (loop [wi w n 0]
               (if (>= n 2000)
                 wi
                 (let [w-next (stellar/collapse-system wi)
                       t (ecs/get-component w-next eid c/temperature)
                       p (ecs/get-component w-next eid c/pressure)]
                   (if (law.stellar/fusion-possible? {:temperature t
                                                      :pressure p
                                                      :composition {:H 0.75}})
                     w-next
                     (recur w-next (inc n))))))
          t (ecs/get-component w' eid c/temperature)
          p (ecs/get-component w' eid c/pressure)]
      (is (> t law.stellar/fusion-temp-threshold)
          "temperature reaches fusion threshold")
      (is (> p law.stellar/fusion-pressure-threshold)
          "pressure reaches fusion threshold"))))

;; --- Accretion radius (stars keep eating) ------------------------------------

(deftest test-accretion-radius-set-on-protostar
  (testing "A clump reaching star-forming mass freezes its radius as a feeding zone"
    (let [base    (ecs/empty-world)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1.1e30   ;; above star-mass-threshold
                                             :radius 1e14})
          w2      (stellar/classify-system w)]
      (is (= :protostar (ecs/get-component w2 eid c/matter-state)))
      (is (= 1e14 (ecs/get-component w2 eid c/accretion-radius))
          "feeding zone is the pre-contraction radius"))))

(deftest test-star-accretes-beyond-its-photosphere
  (testing "A contracted star still captures gas inside its feeding zone"
    (let [base    (-> (ecs/empty-world)
                      (event/with-ledger)
                      (event/register-handler :event/collision
                                              stellar/stellar-merge-handler))
          ;; star with a tiny photosphere but a large feeding zone
          [w1 _]  (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1.5e30
                                             :radius 1e9          ;; photosphere
                                             :matter-state :star})
          star    (first (ecs/entities-with w1 c/mass))
          w1      (ecs/put-component w1 star c/accretion-radius 1e14)
          ;; gas clump well outside the photosphere but inside the feeding zone
          [w2 _]  (stellar/spawn-clump w1 {:position [5e13 0.0 0.0]
                                           :velocity [0.0 0.0 0.0]
                                           :mass 5e27
                                           :radius 6e13})
          w3      (collision/collision-detection-system w2)]
      (is (= 1 (count (ecs/entities-with w3 c/mass)))
          "the gas was accreted even though it never touched the photosphere"))))

(deftest test-merge-preserves-accretion-radius
  (testing "Merging keeps the larger feeding zone (no runaway, no reset to photosphere)"
    (let [base    (-> (ecs/empty-world)
                      (event/with-ledger)
                      (event/register-handler :event/collision
                                              stellar/stellar-merge-handler))
          [w1 _]  (stellar/spawn-clump base {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                                             :mass 1.5e30 :radius 1e9 :matter-state :star})
          star    (first (ecs/entities-with w1 c/mass))
          w1      (ecs/put-component w1 star c/accretion-radius 1e14)
          [w2 _]  (stellar/spawn-clump w1 {:position [5e13 0.0 0.0] :velocity [0.0 0.0 0.0]
                                           :mass 5e27 :radius 6e13})
          w3      (collision/collision-detection-system w2)
          surv    (first (ecs/entities-with w3 c/mass))]
      (is (>= (ecs/get-component w3 surv c/accretion-radius) 1e14)
          "feeding zone is retained through the merge"))))

(deftest test-nonrotating-collapse-stays-spherical
  (testing "A non-rotating protostar stays spherical"
    (let [base (ecs/empty-world)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e30
                                             :radius 1e15
                                             :matter-state :protostar
                                             :angular-momentum [0.0 0.0 0.0]})
          w2   (stellar/collapse-system w)
          ob   (ecs/get-component w2 eid c/oblateness)]
      (is (< (Math/abs (- ob 1.0)) 1e-12)))))
