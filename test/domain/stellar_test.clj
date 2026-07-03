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
     [domain.ecs.tick :as tick]
     [domain.physics.collision :as collision]
     [domain.spatial.index    :as spatial]
     [domain.genesis           :as genesis]
     [law.stellar :as law]
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
    (let [          _L [0.0 0.0 1e40]
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
                                             :matter-state :protostar
                                             :angular-momentum [0.0 0.0 1e42]})
          [w2 eb] (stellar/spawn-clump w1   {:position [0.5 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1e30
                                             :radius 1.0
                                             :matter-state :planet
                                             :angular-momentum [0.0 0.0 5e41]})
          La      (ecs/get-component w2 ea c/angular-momentum)
          Lb      (ecs/get-component w2 eb c/angular-momentum)
          total-L (sp/v+ La Lb)
          w2      (spatial/spatial-index w2)
          w3      (collision/collision-detection-system w2)
          w3      (genesis/materialize-lifecycle w3)
          survivor (first (ecs/entities-with w3 c/mass))]
      (is (= 1 (count (ecs/entities-with w3 c/mass))))
      (testing "absorb-merge packet carries the small body's angular momentum"
        (let [pkts (ecs/get-component w3 survivor c/absorb-merge)]
          (is (some? pkts))
          (let [L-absorbed (reduce sp/v+ (map :angular-momentum pkts))
                ;; total L after merge = survivor snapshot L + absorbed L
                final-L (sp/v+ La L-absorbed)]
            (is (every? (fn [[a b]] (< (Math/abs (- a b)) 1e30))
                        (map vector final-L total-L))
                "total angular momentum conserved through the packet")))))))

(deftest test-merge-does-not-balloon-compact-star
  (testing "A compact star accreting a diffuse body stays compact (density-
            conserving accretion), instead of volume-summing to cloud size and
            collapsing its derived virial temperature — the star-disappears bug.
            After conversion: the handler emits c/absorb-merge; radius is
            re-derived by structure next tick (one-tick Jacobi). We verify the
            packet carries the small body's radius (which will NOT balloon the
            survivor — structure re-derives from the combined mass at the
            survivor's density)."
    (let [base    (-> (ecs/empty-world)
                      (event/with-ledger)
                      (event/register-handler :event/collision
                                              stellar/stellar-merge-handler))
          ;; compact star: ~main-sequence radius, high density
          [w1 _star] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                               :velocity [0.0 0.0 0.0]
                                               :mass 2.0e30
                                               :radius 3.0e8
                                               :matter-state :star
                                               :accretion-radius 1.0e12})
          ;; diffuse, freshly-condensed body 1000× larger but lighter
           [w2 _gas] (stellar/spawn-clump w1   {:position [1.0e11 0.0 0.0]
                                               :velocity [0.0 0.0 0.0]
                                               :mass 5.0e29
                                               :radius 5.0e11
                                               :matter-state :protostar
                                               :accretion-radius 1.0e12})
          w2       (spatial/spatial-index w2)
          w3       (collision/collision-detection-system w2)
          w3       (genesis/materialize-lifecycle w3)
          survivor (first (filter #(ecs/alive? w3 %) (ecs/entities-with w3 c/mass)))]
      (is (= 1 (count (filter #(ecs/alive? w3 %) (ecs/entities-with w3 c/mass)))))
      (testing "survivor radius unchanged this tick (re-derived next tick by structure)"
        (let [r' (double (ecs/get-component w3 survivor c/radius))]
          (is (< r' 1.0e9)
              "survivor keeps its compact radius (density-conserving accretion)")))
      (testing "absorb-merge packet emitted with correct mass"
        (let [pkts (ecs/get-component w3 survivor c/absorb-merge)]
          (is (some? pkts))
          (is (= 5.0e29 (:mass (first pkts)))))))))

(deftest test-mass-loss-demotes-never-dissolves
  (testing "A star whose fusion has CEASED demotes down the BOUND ladder
            (star→brown-dwarf→debris) by mass and NEVER returns to :nebula —
            collapse is irreversible; only the shed material becomes gas (winds
            spec §2). While fusion is still sustained, ignition HYSTERESIS keeps
            it a star despite a mass dip below the 0.08 M☉ formation threshold."
    (let [cold     {:matter-state :star :radius 3.0e8 :density 1.0e4
                    :temperature 1.0e5 :pressure 1.0e13   ;; T ≪ sustain → fusion stopped
                    :composition {:H 0.7 :He 0.28 :metals 0.02}}
          hot      (assoc cold :temperature 2.0e7)         ;; still fusing
          gas-mass 1.0e28
          msun     1.989e30
          cold-at  (fn [f] (stellar/classify-next-state (assoc cold :mass (* f msun)) gas-mass))
          hot-at   (fn [f] (stellar/classify-next-state (assoc hot  :mass (* f msun)) gas-mass))]
      ;; hysteresis: a still-fusing star does NOT demote on mass alone
      (is (= :star (hot-at 0.05)) "a still-fusing star keeps burning despite mass loss")
      ;; once fusion has ceased, demotion follows the bound mass ladder
      (is (= :star        (cold-at 0.5)))     ;; above hydrogen-burning → stays a star
      (is (= :brown-dwarf (cold-at 0.05)))    ;; below H, above deuterium → brown dwarf
      (is (= :debris      (cold-at 0.005)))   ;; below deuterium → stripped core
      (is (not-any? #{:nebula} (map cold-at [0.5 0.05 0.005 0.0005]))
          "a collapsed body never re-dissolves to gas"))))

(deftest test-stellar-wind-conserves-mass-and-sheds
  (testing "stellar-wind-system sheds a :nebula parcel and conserves total mass
            (bodies + wind reservoir)."
    (let [[w star] (stellar/spawn-clump (ecs/empty-world)
                     {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                      :mass (* 0.5 1.989e30) :radius 3.0e8 :temperature 2.0e7
                      :matter-state :star
                      :composition {:H 0.7 :He 0.28 :metals 0.02}})
          w     (-> w
                    (ecs/put-component star c/pressure 1.0e13) ;; → fusion-possible → L>0
                    (assoc :sim/dt 1.0e14 :genesis/wind-rate-scale 1.0e3
                           :genesis/wind-parcel-mass 5.0e27
                           :genesis/gas-smoothing-radius 6.0e13))
          total (fn [w] (+ (reduce + (map #(double (or (ecs/get-component w % c/mass) 0.0))
                                          (ecs/entities-with w c/mass)))
                           (reduce + (map #(double (or (ecs/get-component w % c/wind-reservoir) 0.0))
                                          (ecs/entities-with w c/wind-reservoir)))
                           (reduce + (map #(double (or (ecs/get-component w % c/mass-flux-wind) 0.0))
                                          (ecs/entities-with w c/mass-flux-wind)))))
          m0    (total w)
          ws    ((:run (stellar/stellar-wind-system)) w)
          w1    (-> (tick/apply-write-set w ws)
                    (genesis/materialize-lifecycle))]
      (is (< (Math/abs (/ (- (total w1) m0) m0)) 1.0e-12) "total mass conserved")
      (is (some #(= :nebula (ecs/get-component w1 % c/matter-state))
                (ecs/entities-with w1 c/matter-state))
          "a wind parcel was shed as gas"))))

(deftest test-stellar-flare-conserves-and-ejects-hot
  (testing "stellar-flare-system ejects a hot parcel, conserving mass (winds spec
            phase B). flare-period 1 forces a flare on the tick."
    (let [[w _star] (stellar/spawn-clump (ecs/empty-world)
                     {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                      :mass (* 0.5 1.989e30) :radius 3.0e8 :temperature 2.0e7
                      :matter-state :star
                      :composition {:H 0.7 :He 0.28 :metals 0.02}})
          w     (assoc w :sim/dt 1.0e12 :genesis/flare-period 1
                         :genesis/wind-parcel-mass 5.0e27 :genesis/gas-smoothing-radius 6.0e13)
          tmass (fn [w] (+ (reduce + (map #(double (or (ecs/get-component w % c/mass) 0.0))
                                        (ecs/entities-with w c/mass)))
                           (reduce + (map #(double (or (ecs/get-component w % c/mass-flux-flare) 0.0))
                                          (ecs/entities-with w c/mass-flux-flare)))))
          m0    (tmass w)
          ws    ((:run (stellar/stellar-flare-system)) w)
          w1    (-> (tick/apply-write-set w ws)
                    (genesis/materialize-lifecycle))
          hot   (filter #(and (= :nebula (ecs/get-component w1 % c/matter-state))
                              (> (double (or (ecs/get-component w1 % c/temperature) 0.0)) 1.0e6))
                        (ecs/entities-with w1 c/matter-state))]
      (is (< (Math/abs (/ (- (tmass w1) m0) m0)) 1.0e-12) "mass conserved")
      (is (seq hot) "a hot flare parcel was ejected"))))

(deftest test-merge-conserves-orbital-angular-momentum
  (testing "Orbital L of two moving bodies is added to the merged body's spin"
    (let [base    (-> (ecs/empty-world)
                      (event/with-ledger)
                      (event/register-handler :event/collision
                                              stellar/stellar-merge-handler))
          ;; two equal masses orbiting each other — close enough to overlap
          ;; (distance 1e12 < 2×radius 2e12)
          ;; High temperature (malleable/molten) to avoid shatter path
          [w1 _ea] (stellar/spawn-clump base {:position [5e11 0.0 0.0]
                                              :velocity [0.0 1e4 0.0]
                                              :mass 1e25
                                              :radius 1.0e12
                                              :matter-state :debris
                                              :temperature 3000.0})
          [w2 _eb] (stellar/spawn-clump w1   {:position [-5e11 0.0 0.0]
                                               :velocity [0.0 -1e4 0.0]
                                               :mass 1e25
                                               :radius 1.0e12
                                               :matter-state :debris
                                               :temperature 3000.0})
          w2      (spatial/spatial-index w2)
          w3      (collision/collision-detection-system w2)
          w3      (genesis/materialize-lifecycle w3)
          survivor (first (ecs/entities-with w3 c/mass))
          pkts    (ecs/get-component w3 survivor c/absorb-merge)]
      (is (some? pkts) "collision must fire (bodies overlap)")
      (let [L-absorbed (reduce sp/v+ (map :angular-momentum pkts))]
        (is (pos? (nth L-absorbed 2)))
        (is (>= (nth L-absorbed 2) 1e41))))))

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
          _a0 (ecs/get-component w eid c/radius)
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
          ;; Contraction is rate-limited to τ=:genesis/contraction-time; pick
          ;; dt ≫ τ so each step contracts at the full per-tick cap
          ;; (collapse-fraction) and the core actually reaches the floor within a
          ;; bounded number of steps — this test pins the floor invariant, not
          ;; the production (tens-of-Myr) contraction pace.
          base    (assoc (ecs/empty-world) :sim/dt 1e15 :genesis/contraction-time 1e12)
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
    (let [base    (-> (ecs/empty-world)
                      (assoc :genesis/gas-particle-mass 1e27))
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 1.1e30   ;; above star-mass-threshold
                                             :radius 1e14})
          w2      (stellar/classify-system w)]
      (is (= :protostar (ecs/get-component w2 eid c/matter-state)))
      (is (= 1e14 (ecs/get-component w2 eid c/accretion-radius))
          "feeding zone is the pre-contraction radius"))))

(deftest test-star-accretes-beyond-its-photosphere
  (testing "A contracted star still captures debris inside its feeding zone"
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
          ;; debris clump well outside the photosphere but inside the feeding zone
           [w2 _]  (stellar/spawn-clump w1 {:position [5e13 0.0 0.0]
                                           :velocity [0.0 0.0 0.0]
                                           :mass 5e27
                                           :radius 6e13
                                           :matter-state :debris})
          w2      (spatial/spatial-index w2)
          w3      (collision/collision-detection-system w2)
          w3      (genesis/materialize-lifecycle w3)]
      (is (= 1 (count (ecs/entities-with w3 c/mass)))
          "the debris was accreted even though it never touched the photosphere"))))

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
          w2      (spatial/spatial-index w2)
          w3      (collision/collision-detection-system w2)
          w3      (genesis/materialize-lifecycle w3)
          surv    (first (ecs/entities-with w3 c/mass))]
      ;; Accretion radius is NOT written by the merge handler anymore — it stays
      ;; on the survivor unchanged, re-derived by classifier next tick.
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

;; --- Jeans-driven formation -------------------------------------------------

(deftest test-sound-speed
  (testing "Sound speed increases with temperature"
    (let [cs-cold (stellar/sound-speed 10.0)
          cs-hot  (stellar/sound-speed 1000.0)]
      (is (pos? cs-cold))
      (is (> cs-hot cs-cold)))))

(deftest test-jeans-length
  (testing "Jeans length falls with density and rises with temperature"
    (let [lj1 (stellar/jeans-length 1e-9 100.0)
          lj2 (stellar/jeans-length 1e-6 100.0)
          lj3 (stellar/jeans-length 1e-9 1000.0)]
      (is (pos? lj1))
      (is (< lj2 lj1) "higher density -> shorter Jeans length")
      (is (> lj3 lj1) "higher temperature -> longer Jeans length"))))

(deftest test-stable-gas-remains-nebula
  (testing "A hot, tenuous gas particle with r << lambda_J stays diffuse"
    (let [base (ecs/empty-world)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e28
                                             :radius 1e10
                                             :temperature 1000.0})
          ;; override density to a low value so lambda_J is huge
          w2 (-> w
                 (ecs/put-component eid c/density 1e-9)
                 (ecs/put-component eid c/pressure (law/ideal-gas-pressure 1e-9 1000.0)))
          w3 (stellar/jeans-collapse-system w2)]
      (is (= :nebula (ecs/get-component w3 eid c/matter-state)))
      (is (= 1e10 (ecs/get-component w3 eid c/radius))))))

(deftest test-jeans-unstable-gas-promotes
  (testing "A cold, dense gas particle with r > lambda_J collapses to a resolved body"
    (let [base (ecs/empty-world)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e28
                                             :radius 1e14
                                             :temperature 100.0})
          ;; override density so lambda_J is small
          w2 (-> w
                 (ecs/put-component eid c/density 1e-9)
                 (ecs/put-component eid c/pressure (law/ideal-gas-pressure 1e-9 100.0)))
          w3 (stellar/jeans-collapse-system w2)]
      (is (= :debris (ecs/get-component w3 eid c/matter-state)))
      (is (< (ecs/get-component w3 eid c/radius) 1e14) "radius shrinks to resolved-body scale")
      (is (pos? (ecs/get-component w3 eid c/density)))
      (is (nil? (ecs/get-component w3 eid c/hydro-accel)) "gas acceleration removed"))))

(deftest test-jeans-collapse-ignores-resolved-bodies
  (testing "Already-resolved bodies are not affected by Jeans collapse"
    (let [base (ecs/empty-world)
          [w eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                             :velocity [0.0 0.0 0.0]
                                             :mass 2e28
                                             :radius 1e14
                                             :temperature 100.0
                                             :matter-state :planet})
          w2 (-> w
                 (ecs/put-component eid c/density 1e-9)
                 (ecs/put-component eid c/pressure (law/ideal-gas-pressure 1e-9 100.0)))
          w3 (stellar/jeans-collapse-system w2)]
      (is (= :planet (ecs/get-component w3 eid c/matter-state)))
      (is (= 1e14 (ecs/get-component w3 eid c/radius))))))

;; --- Stage 2: Sink formation (isolation criterion + convert-and-seed) --------

(deftest test-within-existing-sink
  (testing "A parcel inside a sink's accretion radius is detected"
    (let [pos [1e10 0.0 0.0]
          zones [{:position [0.0 0.0 0.0] :radius 2e10}]]
      (is (stellar/within-existing-sink? pos zones))))
  (testing "A parcel outside all sinks is not detected"
    (let [pos [1e11 0.0 0.0]
          zones [{:position [0.0 0.0 0.0] :radius 2e10}]]
      (is (not (stellar/within-existing-sink? pos zones)))))
  (testing "nil sink-zones returns nil (no sinks exist)"
    (is (nil? (stellar/within-existing-sink? [1e10 0.0 0.0] nil))))
  (testing "nil position returns nil"
    (is (nil? (stellar/within-existing-sink? nil [{:position [0 0 0] :radius 1e10}])))))

(deftest test-bondi-radius
  (testing "Bondi radius grows with mass"
    (let [r1 (stellar/bondi-radius 1e28 500.0)
          r2 (stellar/bondi-radius 2e28 500.0)]
      (is (pos? r1))
      (is (= (* 2.0 r1) r2))))
  (testing "Bondi radius shrinks with sound speed"
    (let [r1 (stellar/bondi-radius 1e28 500.0)
          r2 (stellar/bondi-radius 1e28 1000.0)]
      (is (= (* 0.25 r1) r2))))
  (testing "Zero mass returns zero"
    (is (= 0.0 (stellar/bondi-radius 0.0 500.0))))
  (testing "Zero sound speed returns zero"
    (is (= 0.0 (stellar/bondi-radius 1e28 0.0)))))

(deftest test-isolation-criterion-blocks-condensation
  (testing "A Jeans-unstable parcel inside a sink's radius does NOT condense"
    (let [;; Create a sink at origin with a large accretion radius
          base (ecs/empty-world)
          [w1 sink-eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                                    :velocity [0.0 0.0 0.0]
                                                    :mass 2e28
                                                    :radius 1e12
                                                    :temperature 10.0
                                                    :density 1e-6
                                                    :matter-state :debris})
          w1 (ecs/put-component w1 sink-eid c/accretion-radius 5e12)
          ;; Create a Jeans-unstable parcel INSIDE the sink's radius
          [w2 parcel-eid] (stellar/spawn-clump w1 {:position [1e12 0.0 0.0]
                                                     :velocity [0.0 0.0 0.0]
                                                     :mass 2e28
                                                     :radius 1e14
                                                     :temperature 10.0})
          w2 (-> w2
                 (ecs/put-component parcel-eid c/density 1e-6)
                 (ecs/put-component parcel-eid c/pressure (law/ideal-gas-pressure 1e-6 10.0)))
          ;; Compute sink zones and classify with isolation check
          zones (stellar/sink-exclusion-zones w2)
          region (stellar/entity->region w2 parcel-eid)
          next-state (stellar/classify-next-state region 1e25 zones)]
      (is (= :nebula next-state) "Parcel inside sink radius stays :nebula")))
  (testing "A Jeans-unstable parcel OUTSIDE sinks condenses normally"
    (let [base (ecs/empty-world)
          [w1 sink-eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                                    :velocity [0.0 0.0 0.0]
                                                    :mass 2e28
                                                    :radius 1e12
                                                    :temperature 10.0
                                                    :density 1e-6
                                                    :matter-state :debris})
          w1 (ecs/put-component w1 sink-eid c/accretion-radius 1e11)
          ;; Create a Jeans-unstable parcel OUTSIDE the sink's radius
          [w2 parcel-eid] (stellar/spawn-clump w1 {:position [1e13 0.0 0.0]
                                                     :velocity [0.0 0.0 0.0]
                                                     :mass 2e28
                                                     :radius 1e14
                                                     :temperature 10.0})
          w2 (-> w2
                 (ecs/put-component parcel-eid c/density 1e-6)
                 (ecs/put-component parcel-eid c/pressure (law/ideal-gas-pressure 1e-6 10.0)))
          zones (stellar/sink-exclusion-zones w2)
          region (stellar/entity->region w2 parcel-eid)
          next-state (stellar/classify-next-state region 1e25 zones)]
      (is (= :debris next-state) "Parcel outside sink radius condenses to :debris"))))

(deftest test-sink-formation-absorbs-parcels
  (testing "Newly formed sink absorbs nearby :nebula parcels within accretion radius"
    (let [base (ecs/empty-world)
          ;; Create a sink that just condensed (:debris with accretion-radius)
          [w1 sink-eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                                    :velocity [0.0 0.0 0.0]
                                                    :mass 2e28
                                                    :radius 1e12
                                                    :temperature 10.0
                                                    :matter-state :debris})
          w1 (ecs/put-component w1 sink-eid c/accretion-radius 5e12)
          ;; Create gas parcels within accretion radius
          [w2 p1] (stellar/spawn-clump w1 {:position [1e12 0.0 0.0]
                                            :velocity [100.0 0.0 0.0]
                                            :mass 1e27
                                            :radius 1e10
                                            :temperature 10.0
                                            :matter-state :nebula})
          [w3 p2] (stellar/spawn-clump w2 {:position [0.0 2e12 0.0]
                                            :velocity [0.0 200.0 0.0]
                                            :mass 1e27
                                            :radius 1e10
                                            :temperature 10.0
                                            :matter-state :nebula})
          ;; Create a gas parcel OUTSIDE accretion radius (should not be absorbed)
          [w4 p3] (stellar/spawn-clump w3 {:position [1e14 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e27
                                            :radius 1e10
                                            :temperature 10.0
                                            :matter-state :nebula})
          ;; Run sink formation
          w5 (stellar/sink-formation-system w4)
          sink-mass (ecs/get-component w5 sink-eid c/mass)
          absorbs (ecs/get-component w5 sink-eid c/absorb-accrete)]
      ;; Parcels within radius are marked consumed (reaped by materialize-lifecycle)
      (is (some? (ecs/get-component w5 p1 c/consumed-accrete)) "Parcel within radius is consumed")
      (is (some? (ecs/get-component w5 p2 c/consumed-accrete)) "Parcel within radius is consumed")
      ;; Parcel outside radius is untouched
      (is (ecs/alive? w5 p3) "Parcel outside radius survives")
      (is (nil? (ecs/get-component w5 p3 c/consumed-accrete)) "Parcel outside radius not consumed")
      ;; Absorb-accrete influence emitted (integrator applies mass/velocity next tick)
      (is (some? absorbs) "Absorb-accrete influence emitted on sink")
      (is (= 2 (count absorbs)) "Two parcels absorbed")
      (is (= (+ 1e27 1e27) (reduce + 0.0 (map :mass absorbs)))
          "Total absorbed mass matches parcels")
      (is (false? (:disk-route (first absorbs))) "Debris sink → no disk-route")
      (is (= (double sink-mass) 2e28) "Sink bulk mass unchanged (integrator applies next tick)")
      (is (zero? (double (or (ecs/get-component w5 sink-eid c/disk-mass) 0.0)))
          "Debris sink forms no disk")
      (is (zero? (first (ecs/get-component w5 sink-eid c/velocity)))
          "Sink velocity unchanged (COM blend applied by integrator next tick)"))))

(deftest test-sink-formation-absorbs-debris-onto-protostar
  (testing "A protostar sink drains nearby small :debris via absorb-accrete"
    (let [base (ecs/empty-world)
          ;; A forming core (protostar) with a wide feeding zone
          [w1 sink-eid] (stellar/spawn-clump base {:position [0.0 0.0 0.0]
                                                    :velocity [0.0 0.0 0.0]
                                                    :mass 5e28
                                                    :radius 1e12
                                                    :temperature 1000.0
                                                    :matter-state :protostar})
          w1 (ecs/put-component w1 sink-eid c/accretion-radius 5e12)
          ;; A small planetesimal inside the feeding zone
          [w2 deb] (stellar/spawn-clump w1 {:position [1e12 0.0 0.0]
                                            :velocity [0.0 0.0 0.0]
                                            :mass 1e27
                                            :radius 1e10
                                            :temperature 50.0
                                            :matter-state :debris})
          w3 (stellar/sink-formation-system w2)
          absorbs (ecs/get-component w3 sink-eid c/absorb-accrete)]
      (is (some? (ecs/get-component w3 deb c/consumed-accrete))
          "Debris within the feeding zone is consumed (swarm drained)")
      ;; Absorb-accrete influence emitted with :disk-route true (protostar = disk-former)
      (is (some? absorbs)
          "Absorb-accrete influence emitted on protostar sink")
      (is (= 1 (count absorbs))
          "One debris parcel absorbed")
      (is (= 1e27 (:mass (first absorbs)))
          "Absorb packet carries the parcel mass")
      (is (true? (:disk-route (first absorbs)))
          "Disk-route set for protostar sink (mass goes to disk)")
      ;; Bulk mass unchanged (disk-route → disk-evolution handles it next tick)
      (is (= (double (ecs/get-component w3 sink-eid c/mass)) 5e28)
          "Protostar bulk mass unchanged")
      ;; Disk-mass is NOT updated here (disk-evolution reads absorb-accrete
      ;; and adds to disk-mass; it runs after sink-formation in the barrier chain)
      (is (zero? (double (or (ecs/get-component w3 sink-eid c/disk-mass) 0.0)))
          "Disk-mass unchanged (disk-evolution applies absorb-accrete next barrier)"))))

;; --- Fusion promotion barrier ------------------------------------------------

(deftest test-fusion-promotion-sets-luminosity
  (testing "A protostar at main-sequence radius with fusion-possible gets promotion-signal"
    ;; Regression: the parallel double-buffer path had a one-tick lag where the
    ;; classifier read stale density from the frozen snapshot, so fusion-possible?
    ;; never returned true on the transition tick. The fusion-promotion barrier
    ;; runs after the fold and sees the contracted density, then emits a signal
    ;; that the classifier and fusion-system pick up on the NEXT tick (spec §7).
    (let [mass   (* 1.5 law/solar-mass)
          r-ms   (law/main-sequence-radius mass)
          rho    (/ mass (* 4/3 Math/PI (Math/pow r-ms 3)))
          t-vir  (stellar/virial-temperature mass r-ms)
          press  (law/ideal-gas-pressure rho t-vir)
          c   {:H 0.74 :He 0.24 :metals 0.02}
          [w eid] (stellar/spawn-clump (ecs/empty-world)
                                       {:position [0 0 0] :velocity [0 0 0]
                                        :mass mass :radius r-ms
                                        :temperature t-vir :density rho
                                        :pressure press :composition c
                                        :matter-state :protostar})
          ;; Barrier emits promotion-signal; classifier/fusion apply it next tick
          w'     (stellar/fusion-promotion-system w)
          sig    (ecs/get-component w' eid c/promotion-signal)
          state' (ecs/get-component w' eid c/matter-state)
          lum'   (double (or (ecs/get-component w' eid c/luminosity) 0.0))]
      (is (some? sig) "Promotion signal emitted")
      (is (= :star (:promotion sig)) "Signal targets :star promotion")
      (is (pos? (:luminosity sig 0.0)) "Signal carries non-zero luminosity")
      (is (= :protostar state') "Matter-state unchanged (applied by classifier next tick)")
      (is (zero? lum') "Luminosity unchanged (applied by fusion-system next tick)")))

  (testing "An existing star with zero luminosity gets promotion-signal"
    (let [mass   (* 1.5 law/solar-mass)
          r-ms   (law/main-sequence-radius mass)
          rho    (/ mass (* 4/3 Math/PI (Math/pow r-ms 3)))
          t-vir  (stellar/virial-temperature mass r-ms)
          press  (law/ideal-gas-pressure rho t-vir)
          c   {:H 0.74 :He 0.24 :metals 0.02}
          [w eid] (stellar/spawn-clump (ecs/empty-world)
                                       {:position [0 0 0] :velocity [0 0 0]
                                        :mass mass :radius r-ms
                                        :temperature t-vir :density rho
                                        :pressure press :composition c
                                        :matter-state :star})
          w'     (stellar/fusion-promotion-system w)
          sig    (ecs/get-component w' eid c/promotion-signal)]
      (is (some? sig) "Promotion signal emitted for zero-luminosity star")
      (is (= :star (:promotion sig)) "Signal confirms :star")
      (is (pos? (:luminosity sig 0.0)) "Signal carries non-zero luminosity"))))
