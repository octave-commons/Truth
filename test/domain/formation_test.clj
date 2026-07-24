(ns domain.formation-test
  "Unit tests for the star→disk→planet formation pipeline (Genesis Formation
   spec Parts 2, 3, 4). Disc identification and Toomre-Q stability live in
   domain.stellar; the sub-grid planet seeder lives in domain.planet-formation."
  (:require
   [clojure.math :as math] [clojure.test          :refer [deftest testing is]]
   [domain.stellar.disc :as disc]
   [domain.stellar.seeder :as seeder]
   [domain.stellar.structure :as structure]
   [domain.genesis :as genesis]
   [domain.planet-formation :as pf]
   [domain.chemistry      :as chem]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [law.composition       :as lcomp]
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
        v (math/sqrt (/ (* law/G star-m) r))
        ;; tangential direction: rotate the radial unit vector 90° in xy
        [x y _] pos]
    [(* (- v) (/ y r)) (* v (/ x r)) 0.0]))

(deftest disc-classify-keplerian-is-disc
  (testing "a body on a circular Keplerian orbit is rotationally supported → :disc"
    (let [M   solar-mass
          pos [au 0.0 0.0]
          vel (circular-velocity M pos)
          region {:position pos :velocity vel :mass 1.0e25
                  :matter-state :planetesimal :oblateness 1.0}]
      (is (= :disc (disc/disc-classify region (central M)))))))

(deftest disc-classify-radial-infall-is-envelope
  (testing "a body falling straight in (bound, no tangential motion) → :envelope"
    (let [M   solar-mass
          pos [au 0.0 0.0]
          v-in (* 0.3 (math/sqrt (/ (* law/G M) au)))  ;; slow inward, stays bound
          region {:position pos :velocity [(- v-in) 0.0 0.0] :mass 1.0e25
                  :matter-state :planetesimal :oblateness 1.0}]
      (is (= :envelope (disc/disc-classify region (central M)))))))

(deftest disc-classify-hyperbolic-is-outflow
  (testing "an unbound (super-escape) body → :outflow (component doc: unbound/hyperbolic)"
    (let [M   solar-mass
          pos [au 0.0 0.0]
          v-esc (math/sqrt (/ (* 2.0 law/G M) au))
          region {:position pos :velocity [(* 2.0 v-esc) 0.0 0.0] :mass 1.0e25
                  :matter-state :planetesimal :oblateness 1.0}]
      (is (= :outflow (disc/disc-classify region (central M)))))))

(deftest disc-classify-oblate-spinner-is-disc
  (testing "a moderately flattened body on a disc orbit is still :disc (h/r < 0.3)"
    (let [M   solar-mass
          pos [au 0.0 0.0]
          vel (circular-velocity M pos)
          region {:position pos :velocity vel :mass 1.0e25
                  :matter-state :planetesimal :oblateness 0.9}]  ;; h/r = 0.1
      (is (= :disc (disc/disc-classify region (central M)))))))

(deftest disc-classify-star-itself-is-nil
  (testing "the central star (matter-state :star) is not disc material → nil"
    (let [region {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0] :mass solar-mass
                  :matter-state :star :oblateness 1.0}]
      (is (nil? (disc/disc-classify region (central solar-mass)))))))

;; --- Part 3: Toomre Q + disc regime ----------------------------------------

(deftest toomre-q-hot-thin-disc-is-stable
  (testing "a hot, low-mass disc has Q > 1 (stable against fragmentation)"
    (let [Q (structure/toomre-q solar-mass 1.0e25 au 1000.0)]
      (is (> Q 1.0) (str "expected Q>1, got " Q)))))

(deftest toomre-q-cold-massive-disc-is-unstable
  (testing "a cold, massive disc has Q < 1 (gravitationally unstable)"
    (let [Q (structure/toomre-q solar-mass 5.0e29 au 20.0)]
      (is (< Q 1.0) (str "expected Q<1, got " Q)))))

(deftest disc-regime-stable-when-Q-above-one
  (testing "Q > 1 classifies as :stable-disc"
    (is (= :stable-disc (structure/disc-regime solar-mass 1.0e25 au 1000.0)))))

(deftest disc-regime-fragments-when-cold-and-fast-cooling
  (testing "Q < 1 with fast cooling (t_cool < 3 Ω⁻¹) → :gravitationally-unstable"
    ;; A cold, massive disc far out: small Ω keeps the cooling-time ratio low.
    (let [regime (structure/disc-regime solar-mass 5.0e30 (* 50.0 au) 100.0)]
      (is (= :gravitationally-unstable regime) (str "got " regime)))))

(deftest disc-regime-no-fragment-when-cold-and-slow-cooling
  (testing "Q < 1 with slow cooling → :unstable-no-fragment"
    (let [regime (structure/disc-regime solar-mass 5.0e29 au 20.0)]
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
      (is (< (abs (- (/ outside inside) 3.5)) 1.0e-6)))))

(deftest terrestrial-inside-snow-line
  (testing "a low-mass body inside the snow line is :terrestrial"
    (is (= :terrestrial (pf/planet-type (* 1.0 au) 1000.0 (* 2.7 au) 3.0e-6)))))

(deftest gas-giant-beyond-snow-line
  (testing "a massive body beyond the snow line is a :gas-giant"
    (is (= :gas-giant (pf/planet-type (* 5.0 au) 5000.0 (* 2.7 au) 1.0)))))

(deftest ice-giant-beyond-snow-line-moderate-mass
  (testing "a moderate-mass body beyond the snow line is an :ice-giant"
    (is (= :ice-giant (pf/planet-type (* 5.0 au) 5000.0 (* 2.7 au) 0.05)))))

;; --- Part 4: isolation mass caps runaway growth ----------------------------

(deftest hill-radius-scales-with-mass-and-distance
  (testing "Hill radius grows with orbital distance; Earth's is ~0.01 AU"
    (let [rh1 (law/hill-radius law/earth-mass law/solar-mass au)
          rh5 (law/hill-radius law/earth-mass law/solar-mass (* 5.0 au))]
      (is (pos? rh1))
      (is (> rh5 rh1) "farther orbit → larger Hill radius")
      (is (< 0.005 (/ rh1 au) 0.02) (str "Earth R_H " (/ rh1 au) " AU")))))

(deftest isolation-mass-sub-earth-at-1-au
  (testing "MMSN-like solids (~7 g/cm²) at 1 AU give a sub-Earth isolation mass"
    (let [m (law/isolation-mass au 70.0 law/solar-mass)]
      (is (pos? m))
      (is (< (/ m law/earth-mass) 1.0) (str "M_iso " (/ m law/earth-mass) " M⊕")))))

(deftest isolation-mass-monotonic
  (testing "isolation mass rises with surface density and with orbital radius"
    (is (> (law/isolation-mass au 140.0 law/solar-mass)
           (law/isolation-mass au 70.0 law/solar-mass)))
    (is (> (law/isolation-mass (* 5.0 au) 70.0 law/solar-mass)
           (law/isolation-mass au 70.0 law/solar-mass)))))

(deftest isolation-mass-zero-on-degenerate
  (testing "degenerate inputs give zero, never NaN"
    (is (zero? (law/isolation-mass 0.0 70.0 law/solar-mass)))
    (is (zero? (law/isolation-mass au 0.0 law/solar-mass)))
    (is (zero? (law/isolation-mass au 70.0 0.0)))))

(deftest mmsn-profile-conserves-disk-mass
  (testing "∫Σ(r)·2πr dr over the disk returns the total disk mass"
    (let [disk-m 1.0e28 r-in (* 0.1 au) r-out (* 30.0 au)
          s0 (pf/mmsn-sigma0 disk-m r-in r-out)
          ;; numerically integrate the profile
          n 4000
          dr (/ (- r-out r-in) n)
          total (reduce + 0.0
                        (for [i (range n)]
                          (let [r (+ r-in (* (+ i 0.5) dr))]
                            (* (pf/mmsn-sigma s0 r) 2.0 math/PI r dr))))]
      (is (< (abs (- (/ total disk-m) 1.0)) 0.01)
          (str "integrated mass ratio " (/ total disk-m))))))

;; --- Part 4: the seeder over a built disc ----------------------------------

(defn- build-disk-world
  "A world with one Sun-like :star at the origin carrying a protoplanetary disk,
   plus `n` :disc-tagged bodies spread logarithmically from 0.3 to 15 AU. Each
   disc body is placed on a circular orbit and given `body-mass`."
  [{:keys [disk-mass body-mass n sim-time ignition-time maturity]
    :or {disk-mass 1.0e27 body-mass 6.0e24 n 24
         sim-time 1.0e14 ignition-time 0.0 maturity pf/disk-maturity-seconds}}]
  (let [M solar-mass
        [w star] (seeder/spawn-clump (ecs/empty-world)
                                     {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                                      :mass M :radius law/solar-radius :temperature 5800.0
                                      :matter-state :star
                                      :composition lcomp/solar-composition})
        w (-> w
              (ecs/put-component star c/luminosity law/solar-luminosity)
              (ecs/put-component star c/disk-mass disk-mass)
              (ecs/put-component star c/disk-angular-mom [0.0 0.0 1.0e42])
              (ecs/put-component star c/rotation-axis [0.0 0.0 1.0]))
        radii (for [i (range n)]
                (* au (math/pow 10.0 (+ (math/log10 0.3)
                                        (* i (/ (- (math/log10 15.0) (math/log10 0.3))
                                                (dec n)))))))
        w (reduce (fn [w r]
                    (let [pos [r 0.0 0.0]
                          vel (circular-velocity M pos)
                          [w2 eid] (seeder/spawn-clump w
                                                       {:position pos :velocity vel
                                                        :mass body-mass :radius 1.0e7
                                                        :matter-state :planetesimal})]
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

(deftest planet-composition-comes-from-local-disk-condensates
  (testing "the seed's bulk composition is the disk's condensed inventory at the
            formation radius, not a static per-type table (spec §6.5, decision §9.1)"
    (let [inside  (pf/planet-composition lcomp/solar-composition 500.0 6.0e24 0.0)
          outside (pf/planet-composition lcomp/solar-composition 100.0 6.0e24 0.0)
          giant   (pf/planet-composition lcomp/solar-composition 100.0 6.0e24 1.0e27)]
      (is (lcomp/composition-sums-to-unity? inside))
      (is (lcomp/composition-sums-to-unity? outside))
      (is (lcomp/composition-sums-to-unity? giant))
      (testing "inside the snow line: rock/metal core, almost no H/He or water"
        (is (> (+ (:Fe inside 0.0) (:Si inside 0.0) (:Mg inside 0.0)) 0.3))
        (is (< (:H inside 0.0) 1e-3))
        (is (< (:O inside 0.0) 1e-2) "water is vapor at 500 K — absent from the core"))
      (testing "beyond the snow line: ices (O C N) join the condensed solids"
        (is (> (:O outside 0.0) 0.1))
        (is (> (:O outside 0.0) (:O inside 0.0))))
      (testing "a runaway giant's captured envelope makes it H/He dominated"
        (is (> (+ (:H giant 0.0) (:He giant 0.0)) 0.9))))))

(deftest seeded-planet-composition-follows-formation-radius
  (testing "each seeded planet's composition is the LOCAL disk's condensed
            inventory: rock/metal inside the snow line, ice-bearing beyond it"
    (let [[w star] (build-disk-world {})
          res (pf/planet-seeds w star)
          snow (pf/snow-line-radius law/solar-luminosity)
          star-pos (ecs/get-component w star c/position)]
      (is (seq (:spawns res)))
      (doseq [[_ spec] (:spawns res)]
        (let [r (sp/dist (:position spec) star-pos)
              comp (:composition spec)
              h-he (+ (:H comp 0.0) (:He comp 0.0))
              cats (chem/bulk-categories comp (:temperature spec))]
          (is (lcomp/composition-sums-to-unity? comp)
              "seeded composition is a normalized element map")
          (cond
            (> h-he 0.5)
            (is (> r snow)
                (str "runaway gas capture only happens beyond the snow line ("
                     (/ r au) " AU)"))
            (> r snow)
            (is (> (:ice cats) 0.01)
                (str "planet at " (/ r au) " AU (beyond snow line) carries ice"))
            :else
            (is (> (+ (:rock cats) (:metal cats)) 0.5)
                (str "planet at " (/ r au) " AU (inside snow line) is rock/metal-rich"))))))))

(deftest terrestrials-stay-small-on-a-massive-disk
  (testing "isolation mass caps inner rocky planets — a massive (0.05 M☉) disk
            must NOT produce a hundreds-of-Earth-mass terrestrial (the reported bug)"
    (let [[w star] (build-disk-world {:disk-mass 1.0e29})  ;; ~0.05 M☉, ~1.7e4 M⊕
          res (pf/planet-seeds w star)
          snow (pf/snow-line-radius law/solar-luminosity)
          star-pos (ecs/get-component w star c/position)]
      (is (seq (:spawns res)))
      (doseq [[_ spec] (:spawns res)]
        (let [r (sp/dist (:position spec) star-pos)
              m-earth (/ (:mass spec) law/earth-mass)]
          (when (<= r snow)
            (is (< m-earth 10.0)
                (str "terrestrial at " (/ r au) " AU is " m-earth
                     " M⊕ — isolation mass must cap it far below the old ~400 M⊕"))))))))

(deftest giant-cores-can-still-form-beyond-ice-line
  (testing "beyond the ice line, runaway gas accretion still builds a real giant
            (isolation mass must not strangle giant formation)"
    (let [[w star] (build-disk-world {:disk-mass 1.0e29})
          res (pf/planet-seeds w star)
          snow (pf/snow-line-radius law/solar-luminosity)
          star-pos (ecs/get-component w star c/position)
          giants (for [[_ spec] (:spawns res)
                       :let [r (sp/dist (:position spec) star-pos)]
                       :when (> r snow)]
                   spec)]
      (is (seq giants) "at least one body seeds beyond the ice line")
      (is (some #(> (/ (:mass %) law/earth-mass) 10.0) giants)
          "at least one giant grows past ~10 M⊕ via runaway gas accretion"))))

;; --- Spawn-seam staleness: the star moves before materialize-lifecycle -----
;; `domain.genesis.tick/tick-physics` runs `step-physics` (the integrator
;; INCLUDED) BEFORE `materialize-lifecycle`. In the formation-era cluster a
;; star can move 10s of AU in a single tick — the same order as the whole
;; seeded orbit (design `docs/designs/multi-timescale-integration.md` §3.0).
;; A spec built from the star's PRE-tick position/velocity and materialized
;; unchanged into the POST-tick world (where the star has already moved) is
;; the "fling machine" reborn at the spawn seam: the planet is born already
;; many AU from its actual parent, with a velocity that pairs with nothing —
;; an instant ejection with no raw-Euler integrator tick involved.

(deftest planet-spawn-survives-parent-motion-within-the-birth-tick
  (testing "a planet-seeds spec materializes bound to the star's CURRENT
            (post-step-physics) position/velocity, not its stale pre-tick one"
    (let [[w star] (build-disk-world {})
          res      (pf/planet-seeds w star)
          _        (is (seq (:spawns res)) "the fixture must actually seed something")
          ;; Populate the spawn-request exactly as domain.stellar.disc-evolution
          ;; does, then simulate the star having ALREADY advanced this tick
          ;; (the ordering `step-physics` → `materialize-lifecycle` guarantees
          ;; in production) by moving it a large-but-formation-era-realistic
          ;; distance before materializing.
          w        (ecs/put-component w star c/spawn-request-planet
                                      (mapv second (:spawns res)))
          star-pos0 (ecs/get-component w star c/position)
          shift     [(* 20.0 au) 0.0 0.0] ;; ~20 AU: within the design's cited 5–20 AU/tick range
          new-star-v [3000.0 0.0 0.0]     ;; a real, nonzero post-move velocity too
          w        (-> w
                       (ecs/put-component star c/position (sp/v+ star-pos0 shift))
                       (ecs/put-component star c/velocity new-star-v))
          w2       (genesis/materialize-lifecycle w)
          star-pos1 (ecs/get-component w2 star c/position)
          star-v1   (ecs/get-component w2 star c/velocity)
          M        (double (ecs/get-component w2 star c/mass))
          planets  (remove #(= % star) (ecs/entities-with w2 c/matter-state c/mass c/position))
          planets  (filter #(= :planet (ecs/get-component w2 % c/matter-state)) planets)]
      (is (seq planets) "materialize-lifecycle actually creates the requested planets")
      (doseq [eid planets]
        (let [pos (ecs/get-component w2 eid c/position)
              vel (ecs/get-component w2 eid c/velocity)
              r   (sp/dist pos star-pos1)
              v   (sp/len (sp/v- vel star-v1))
              energy (- (* 0.5 v v) (/ (* law/G M) r))]
          (is (neg? energy)
              (str "planet at " (/ r au) " AU should be BOUND to the star's "
                   "current state (E=" energy "); a stale anchor births it "
                   "already unbound")))))))
