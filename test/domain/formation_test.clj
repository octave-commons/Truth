(ns domain.formation-test
  "Unit tests for the star→disk→planet formation pipeline (Genesis Formation
   spec Parts 2, 3, 4). Disc identification and Toomre-Q stability live in
   domain.stellar; the sub-grid planet seeder lives in domain.planet-formation."
  (:require
   [clojure.test          :refer [deftest testing is]]
   [domain.stellar        :as stellar]
   [domain.planet-formation :as pf]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [law.stellar           :as law]
   [shape.spatial         :as sp]))

(def solar-mass law/solar-mass)
(def au law/au)

;; --- Part 2: disc identification -------------------------------------------

(defn- central [m] {:star-pos [0.0 0.0 0.0] :star-v [0.0 0.0 0.0] :star-m m})

(defn- circular-velocity
  "Tangential (in the xy-plane) circular-orbit velocity at position `pos`."
  [star-m pos]
  (let [r (sp/len pos)
        v (Math/sqrt (/ (* law/G star-m) r))
        ;; tangential direction: rotate the radial unit vector 90° in xy
        [x y _] pos]
    [(* (- v) (/ y r)) (* v (/ x r)) 0.0]))

(deftest disc-classify-keplerian-is-disc
  (testing "a body on a circular Keplerian orbit is rotationally supported → :disc"
    (let [M   solar-mass
          pos [au 0.0 0.0]
          vel (circular-velocity M pos)
          region {:position pos :velocity vel :mass 1.0e25
                  :matter-state :debris :oblateness 1.0}]
      (is (= :disc (stellar/disc-classify region (central M)))))))

(deftest disc-classify-radial-infall-is-envelope
  (testing "a body falling straight in (bound, no tangential motion) → :envelope"
    (let [M   solar-mass
          pos [au 0.0 0.0]
          v-in (* 0.3 (Math/sqrt (/ (* law/G M) au)))  ;; slow inward, stays bound
          region {:position pos :velocity [(- v-in) 0.0 0.0] :mass 1.0e25
                  :matter-state :debris :oblateness 1.0}]
      (is (= :envelope (stellar/disc-classify region (central M)))))))

(deftest disc-classify-hyperbolic-is-outflow
  (testing "an unbound (super-escape) body → :outflow (component doc: unbound/hyperbolic)"
    (let [M   solar-mass
          pos [au 0.0 0.0]
          v-esc (Math/sqrt (/ (* 2.0 law/G M) au))
          region {:position pos :velocity [(* 2.0 v-esc) 0.0 0.0] :mass 1.0e25
                  :matter-state :debris :oblateness 1.0}]
      (is (= :outflow (stellar/disc-classify region (central M)))))))

(deftest disc-classify-oblate-spinner-is-disc
  (testing "a moderately flattened body on a disc orbit is still :disc (h/r < 0.3)"
    (let [M   solar-mass
          pos [au 0.0 0.0]
          vel (circular-velocity M pos)
          region {:position pos :velocity vel :mass 1.0e25
                  :matter-state :debris :oblateness 0.9}]  ;; h/r = 0.1
      (is (= :disc (stellar/disc-classify region (central M)))))))

(deftest disc-classify-star-itself-is-nil
  (testing "the central star (matter-state :star) is not disc material → nil"
    (let [region {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0] :mass solar-mass
                  :matter-state :star :oblateness 1.0}]
      (is (nil? (stellar/disc-classify region (central solar-mass)))))))

;; --- Part 3: Toomre Q + disc regime ----------------------------------------

(deftest toomre-q-hot-thin-disc-is-stable
  (testing "a hot, low-mass disc has Q > 1 (stable against fragmentation)"
    (let [Q (stellar/toomre-q solar-mass 1.0e25 au 1000.0)]
      (is (> Q 1.0) (str "expected Q>1, got " Q)))))

(deftest toomre-q-cold-massive-disc-is-unstable
  (testing "a cold, massive disc has Q < 1 (gravitationally unstable)"
    (let [Q (stellar/toomre-q solar-mass 5.0e29 au 20.0)]
      (is (< Q 1.0) (str "expected Q<1, got " Q)))))

(deftest disc-regime-stable-when-Q-above-one
  (testing "Q > 1 classifies as :stable-disc"
    (is (= :stable-disc (stellar/disc-regime solar-mass 1.0e25 au 1000.0)))))

(deftest disc-regime-fragments-when-cold-and-fast-cooling
  (testing "Q < 1 with fast cooling (t_cool < 3 Ω⁻¹) → :gravitationally-unstable"
    ;; A cold, massive disc far out: small Ω keeps the cooling-time ratio low.
    (let [regime (stellar/disc-regime solar-mass 5.0e30 (* 50.0 au) 100.0)]
      (is (= :gravitationally-unstable regime) (str "got " regime)))))

(deftest disc-regime-no-fragment-when-cold-and-slow-cooling
  (testing "Q < 1 with slow cooling → :unstable-no-fragment"
    (let [regime (stellar/disc-regime solar-mass 5.0e29 au 20.0)]
      (is (= :unstable-no-fragment regime) (str "got " regime)))))

;; --- Part 4: planet sub-grid seeder pure functions -------------------------

(deftest snow-line-at-expected-radius
  (testing "a Sun-luminosity star puts the 170 K snow line near ~2.7 AU"
    (let [r (pf/snow-line-radius law/solar-luminosity)
          r-au (/ r au)]
      (is (< 2.0 r-au 3.5) (str "snow line " r-au " AU")))))

(deftest sigma-jumps-beyond-snow-line
  (testing "solid surface density jumps ~3.5× just beyond the snow line"
    (let [snow (* 2.7 au)
          inside  (pf/solid-surface-density 100.0 (* 2.0 au) snow 0.015)
          outside (pf/solid-surface-density 100.0 (* 3.5 au) snow 0.015)]
      (is (< (Math/abs (- (/ outside inside) 3.5)) 1.0e-6)))))

(deftest terrestrial-inside-snow-line
  (testing "a low-mass body inside the snow line is :terrestrial"
    (is (= :terrestrial (pf/planet-type (* 1.0 au) 1000.0 (* 2.7 au) 3.0e-6)))))

(deftest gas-giant-beyond-snow-line
  (testing "a massive body beyond the snow line is a :gas-giant"
    (is (= :gas-giant (pf/planet-type (* 5.0 au) 5000.0 (* 2.7 au) 1.0)))))

(deftest ice-giant-beyond-snow-line-moderate-mass
  (testing "a moderate-mass body beyond the snow line is an :ice-giant"
    (is (= :ice-giant (pf/planet-type (* 5.0 au) 5000.0 (* 2.7 au) 0.05)))))

;; --- Part 4: the seeder over a built disc ----------------------------------

(defn- build-disk-world
  "A world with one Sun-like :star at the origin carrying a protoplanetary disk,
   plus `n` :disc-tagged bodies spread logarithmically from 0.3 to 15 AU. Each
   disc body is placed on a circular orbit and given `body-mass`."
  [{:keys [disk-mass body-mass n sim-time ignition-time maturity]
    :or {disk-mass 1.0e27 body-mass 6.0e24 n 24
         sim-time 1.0e14 ignition-time 0.0 maturity pf/disk-maturity-seconds}}]
  (let [M solar-mass
        [w star] (stellar/spawn-clump (ecs/empty-world)
                   {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                    :mass M :radius law/solar-radius :temperature 5800.0
                    :matter-state :star
                    :composition {:H 0.7 :He 0.28 :metals 0.02}})
        w (-> w
              (ecs/put-component star c/luminosity law/solar-luminosity)
              (ecs/put-component star c/disk-mass disk-mass)
              (ecs/put-component star c/disk-angular-mom [0.0 0.0 1.0e42])
              (ecs/put-component star c/rotation-axis [0.0 0.0 1.0]))
        radii (for [i (range n)]
                (* au (Math/pow 10.0 (+ (Math/log10 0.3)
                                        (* i (/ (- (Math/log10 15.0) (Math/log10 0.3))
                                                (dec n)))))))
        w (reduce (fn [w r]
                    (let [pos [r 0.0 0.0]
                          vel (circular-velocity M pos)
                          [w2 eid] (stellar/spawn-clump w
                                     {:position pos :velocity vel
                                      :mass body-mass :radius 1.0e7
                                      :matter-state :debris})]
                      (ecs/put-component w2 eid c/disc-tag :disc)))
                  w radii)]
    [(assoc w :genesis/sim-time sim-time
              :genesis/star-ignition-time ignition-time
              :genesis/disk-maturity maturity
              :tick 100)
     star]))

(deftest seeder-produces-planets-on-a-mature-disk
  (testing "planet-seeds emits ≥1 planet spec once the disk has matured"
    (let [[w star] (build-disk-world {})
          res (pf/planet-seeds w star)]
      (is (some? res))
      (is (seq (:spawns res)) "at least one annulus seeds a planet"))))

(deftest seeder-does-not-run-before-maturity
  (testing "no planets are seeded while disk-age < disk-maturity"
    (let [[w star] (build-disk-world {:sim-time 1.0e12})]  ;; age ≪ 1 Myr
      (is (nil? (pf/planet-seeds w star))))))

(deftest seeder-does-not-rerun-once-seeded
  (testing "a star already flagged c/planets-seeded is not re-seeded"
    (let [[w star] (build-disk-world {})
          w (ecs/put-component w star c/planets-seeded true)]
      (is (nil? (pf/planet-seeds w star))))))

(deftest seeder-conserves-disc-mass
  (testing "total seeded planet mass ≤ the disk mass consumed (conservation)"
    (let [[w star] (build-disk-world {})
          res (pf/planet-seeds w star)
          disk0 (double (ecs/get-component w star c/disk-mass))
          seeded-mass (reduce + 0.0 (map #(:mass (second %)) (:spawns res)))
          consumed (- disk0 (:disk-m res))]
      (is (<= seeded-mass (+ consumed (* 1.0e-6 (max 1.0 consumed))))
          "planets draw no more than the debit (modulo float summation order)")
      (is (>= consumed 0.0) "disk mass only decreases"))))

(deftest seeded-planets-are-on-bound-orbits
  (testing "each seeded planet has negative orbital energy relative to the star"
    (let [[w star] (build-disk-world {})
          res (pf/planet-seeds w star)
          M   (double (ecs/get-component w star c/mass))
          star-pos (ecs/get-component w star c/position)
          star-v   (ecs/get-component w star c/velocity)]
      (is (seq (:spawns res)))
      (doseq [[_ spec] (:spawns res)]
        (let [r (sp/dist (:position spec) star-pos)
              v (sp/len (sp/v- (:velocity spec) star-v))
              energy (- (* 0.5 v v) (/ (* law/G M) r))]
          (is (neg? energy) (str "planet at " (/ r au) " AU should be bound, E=" energy))
          (is (>= r (* pf/min-planet-orbit-radius-au au))
              "no planet inside the inner radius"))))))

(deftest seeded-planet-types-match-location
  (testing "planets inside the snow line are terrestrial; those beyond are giants"
    (let [[w star] (build-disk-world {})
          res (pf/planet-seeds w star)
          snow (pf/snow-line-radius law/solar-luminosity)
          star-pos (ecs/get-component w star c/position)]
      (doseq [[_ spec] (:spawns res)]
        (let [r (sp/dist (:position spec) star-pos)]
          (if (> r snow)
            (is (#{:ice-giant :gas-giant} (:planet-type spec))
                (str "beyond snow line → giant, got " (:planet-type spec)))
            (is (= :terrestrial (:planet-type spec))
                (str "inside snow line → terrestrial, got " (:planet-type spec)))))))))
