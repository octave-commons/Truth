(ns domain.disk-evolution-test
  "Tests for protoplanetary disk regime and restricted GI fragmentation."
  (:require
   [clojure.math :as math] [clojure.test :refer [deftest testing is]]
   [domain.stellar.disc-evolution :as disc-evolution]
   [domain.stellar.seeder :as seeder]
   [domain.stellar.structure :as structure]
   [domain.planet-formation :as pf]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [law.composition :as lcomp]
   [law.stellar :as law]
   [shape.spatial :as sp]))

(defn- star-with-disk
  "Return [world star-eid] with a star carrying a disk of `disk-mass` and
   specific angular momentum chosen so the disk radius is ~`radius-m`."
  [disk-mass radius-m]
  (let [M law/solar-mass
        j (math/sqrt (* law/G M radius-m))
        disk-L [0.0 0.0 (* disk-mass j)]
        [w eid] (seeder/spawn-clump
                 (ecs/empty-world)
                 {:position [0.0 0.0 0.0]
                  :velocity [0.0 0.0 0.0]
                  :mass M
                  :radius law/solar-radius
                  :temperature 2.0e7
                  :matter-state :star
                  :composition lcomp/solar-composition})]
    [(-> w
         (ecs/put-component eid c/luminosity law/solar-luminosity)
         (ecs/put-component eid c/pressure 1.0e13)
         (ecs/put-component eid c/disk-mass disk-mass)
         (ecs/put-component eid c/disk-angular-mom disk-L)
         (ecs/put-component eid c/rotation-axis [0.0 0.0 1.0])
         (assoc :sim/dt 1.0e10
                :tick 1000))
     eid]))

(deftest disk-regime-map-contains-all-keys
  (testing "disk-regime-map returns the scalar regime shape"
    (let [regime (structure/disk-regime-map {:star-mass law/solar-mass
                                             :disk-mass (* 0.1 law/solar-mass)
                                             :disk-radius 1.5e11
                                             :luminosity law/solar-luminosity
                                             :composition lcomp/solar-composition})]
      (is (contains? regime :toomre-q))
      (is (contains? regime :cooling-beta))
      (is (contains? regime :regime))
      (is (contains? regime :solid-surface-density))
      (is (contains? regime :snow-line))
      (is (pos? (:snow-line regime))))))

(deftest snow-line-jumps-solid-surface-density
  (testing "solid surface density is enhanced beyond the snow line"
    (let [snow-line (pf/snow-line-radius law/solar-luminosity)
          inside (structure/disk-regime-map {:star-mass law/solar-mass
                                             :disk-mass (* 0.1 law/solar-mass)
                                             :disk-radius (* 0.9 snow-line)
                                             :luminosity law/solar-luminosity
                                             :composition lcomp/solar-composition})
          outside (structure/disk-regime-map {:star-mass law/solar-mass
                                              :disk-mass (* 0.1 law/solar-mass)
                                              :disk-radius (* 1.1 snow-line)
                                              :luminosity law/solar-luminosity
                                              :composition lcomp/solar-composition})]
      (is (< (* 0.9 snow-line) snow-line (* 1.1 snow-line)))
      (is (> (:solid-surface-density outside) (* 2.0 (:solid-surface-density inside)))
          "beyond snow line solids are ice-enhanced"))))

(deftest fragmenting-disk-spawns-gas-giant-only
  (testing "when the disk-regime is :fragmenting, a :gas-giant embryo is spawned"
    (let [disk-m (* 0.8 law/solar-mass)
          [w star] (star-with-disk disk-m 1.5e11)
          ;; Force a fragmenting regime so we test the spawn logic.
          w (assoc-in w [:test/disk-regime star]
                      {:toomre-q 0.5 :cooling-beta 1.5
                       :regime :fragmenting
                       :solid-surface-density 10.0
                       :snow-line 2.7e11})
          ws (disc-evolution/disk-evolution-system w)
          spawns (get-in ws [:components c/spawn-request-disk star])
          spawn (first spawns)]
      (is (some? spawn))
      (is (= :gas-giant (:matter-state spawn)))
      (is (<= (:mass spawn) (* 0.5 law/deuterium-burning-mass))
          "GI fragment is capped at or below the deuterium limit")
      (is (>= (:mass spawn) law/opacity-limit-mass)
          "GI fragment is above the opacity limit"))))

(deftest gravito-turbulent-disk-does-not-fragment
  (testing "a disk with Q < 1 but beta > 3 does not fragment"
    (let [disk-m (* 0.8 law/solar-mass)
          [w0 star] (star-with-disk disk-m 1.5e11)
          w0 (assoc-in w0 [:test/disk-regime star]
                       {:toomre-q 0.5 :cooling-beta 10.0
                        :regime :gravito-turbulent
                        :solid-surface-density 10.0
                        :snow-line 2.7e11})
          ws (disc-evolution/disk-evolution-system w0)
          spawns (get-in ws [:components c/spawn-request-disk star])]
      (is (nil? spawns)))))

(deftest disk-never-spawns-planetesimal
  (testing "direct disk fragmentation never emits :planetesimal"
    (let [disk-m (* 0.8 law/solar-mass)
          [w star] (star-with-disk disk-m 1.5e11)
          w (assoc-in w [:test/disk-regime star]
                      {:toomre-q 0.5 :cooling-beta 1.5
                       :regime :fragmenting
                       :solid-surface-density 10.0
                       :snow-line 2.7e11})
          ws (disc-evolution/disk-evolution-system w)
          spawns (get-in ws [:components c/spawn-request-disk star])]
      (is (every? #(not= :planetesimal (:matter-state %)) spawns)))))

(deftest disk-mass-and-angmom-conserved-through-fragment-spawn
  (testing "fragment debit conserves disk mass and angular momentum"
    (let [disk-m (* 0.8 law/solar-mass)
          [w star] (star-with-disk disk-m 1.5e11)
          w (assoc-in w [:test/disk-regime star]
                      {:toomre-q 0.5 :cooling-beta 1.5
                       :regime :fragmenting
                       :solid-surface-density 10.0
                       :snow-line 2.7e11})
          m0 (double (ecs/get-component w star c/disk-mass))
          L0 (sp/len (ecs/get-component w star c/disk-angular-mom))
          ws (disc-evolution/disk-evolution-system w)
          disk-m1 (double (get-in ws [:components c/disk-mass star]))
          L1 (sp/len (get-in ws [:components c/disk-angular-mom star]))
          mass-flux (double (get-in ws [:components c/mass-flux-disk star] 0.0))
          spawn (first (get-in ws [:components c/spawn-request-disk star]))]
      (is (< disk-m1 m0) "disk mass decreased")
      (is (< (abs (- (- m0 disk-m1) (+ (:mass spawn) mass-flux)))
             (* 1.0e-9 (+ m0 disk-m1)))
          "disk mass debit equals fragment mass plus viscous accretion")
      (is (< (abs (- L1 (* L0 (/ disk-m1 m0)))) (* 1.0e-9 L0))
          "specific angular momentum is conserved"))))

(deftest disk-regime-is-written
  (testing "disk-evolution always writes a scalar disk-regime for disk-holding stars"
    (let [disk-m (* 0.1 law/solar-mass)
          [w star] (star-with-disk disk-m 1.5e11)
          ws (disc-evolution/disk-evolution-system w)
          regime (get-in ws [:components c/disk-regime star])]
      (is (contains? regime :toomre-q))
      (is (contains? regime :cooling-beta))
      (is (contains? regime :regime))
      (is (contains? regime :solid-surface-density))
      (is (contains? regime :snow-line)))))

(deftest fragment-cap-limits-spawns
  (testing "a disk with fragments-spawned = cap does not fragment again"
    (let [disk-m (* 0.8 law/solar-mass)
          [w star] (star-with-disk disk-m 1.5e11)
          w (-> w
                (assoc-in [:test/disk-regime star]
                          {:toomre-q 0.5 :cooling-beta 1.5
                           :regime :fragmenting
                           :solid-surface-density 10.0
                           :snow-line 2.7e11})
                (ecs/put-component star c/disk-fragments-spawned disc-evolution/max-gi-fragments-per-disk))
          ws (disc-evolution/disk-evolution-system w)
          spawns (get-in ws [:components c/spawn-request-disk star])]
      (is (nil? spawns)))))

(deftest planet-seeding-skips-fragmenting-star
  (testing "a star that fragments its disk on this tick does not also seed planets"
    (let [disk-m (* 0.8 law/solar-mass)
          [w0 star] (star-with-disk disk-m 1.5e11)
          w0 (-> w0
                 (assoc :genesis/sim-time 1.0e14
                        :genesis/star-ignition-time 0.0
                        :genesis/disk-maturity 1.0e12)
                 (assoc-in [:test/disk-regime star]
                           {:toomre-q 0.5 :cooling-beta 1.5
                            :regime :fragmenting
                            :solid-surface-density 10.0
                            :snow-line 2.7e11}))
          ws (disc-evolution/disk-evolution-system w0)
          planet-spawns (get-in ws [:components c/spawn-request-planet star])]
      (is (nil? planet-spawns)))))

(defn- fragmenting-ws
  "Run disk-evolution on a forced-:fragmenting disk of `disk-mass` at
   `radius-m`, with optional world overrides. Returns the write-set."
  [disk-mass radius-m overrides]
  (let [[w star] (star-with-disk disk-mass radius-m)
        w (-> w
              (merge overrides)
              (assoc-in [:test/disk-regime star]
                        {:toomre-q 0.5 :cooling-beta 1.5
                         :regime :fragmenting
                         :solid-surface-density 10.0
                         :snow-line 2.7e11}))]
    [(disc-evolution/disk-evolution-system w) star]))

(defn- spawn-radius
  "Orbital radius (m) of a spawn spec: distance from the star at the origin."
  [spawn]
  (sp/len (:position spawn)))

(deftest gi-fragment-placed-at-physical-disk-radius
  (testing "GI fragment spawns at 0.3× the physical disk radius, not the dt floor"
    (let [[ws star] (fragmenting-ws (* 0.8 law/solar-mass) 1.5e12 {})
          spawn (first (get-in ws [:components c/spawn-request-disk star]))
          r (spawn-radius spawn)]
      (is (some? spawn))
      (is (< (abs (- r (* 0.3 1.5e12))) (* 0.05 1.5e12))
          "spawn radius ≈ 0.3× disk radius (10 AU disk → ~3 AU)")
      (is (< r (* 10.0 law/au))
          "well inside the 100-AU apoapsis gate, nowhere near the old ~162 AU floor"))))

(deftest gi-fragment-placement-ignores-global-dt
  (testing "a 1000× larger bulk dt does not push the spawn radius out"
    (let [[ws-small star-s] (fragmenting-ws (* 0.8 law/solar-mass) 1.5e12 {:sim/dt 1.0e10})
          [ws-large star-l] (fragmenting-ws (* 0.8 law/solar-mass) 1.5e12 {:sim/dt 1.0e13})
          r-small (spawn-radius (first (get-in ws-small [:components c/spawn-request-disk star-s])))
          r-large (spawn-radius (first (get-in ws-large [:components c/spawn-request-disk star-l])))]
      (is (< r-large (* 10.0 law/au))
          "even at dt = 1e13 s the fragment stays at the physical disk radius")
      (is (< (abs (- r-large r-small)) (* 0.25 r-small))
          "radius moves only with the (dt-scaled) viscous debit, not the dt floor"))))

(deftest gi-fragment-placement-respects-physical-floor
  (testing "a tiny disk cannot place a fragment below the 0.3-AU K-clamp floor"
    (let [[ws star] (fragmenting-ws (* 0.8 law/solar-mass) 3.0e10 {})
          spawn (first (get-in ws [:components c/spawn-request-disk star]))
          r (spawn-radius spawn)]
      (is (some? spawn))
      (is (< (abs (- r disc-evolution/fragment-placement-floor-m))
             (* 1.0e-6 disc-evolution/fragment-placement-floor-m))
          "0.3× of a 0.2-AU disk would be below the floor; clamped to 0.3 AU"))))

(deftest binary-companion-placed-at-physical-disk-radius
  (testing "binary companion spawns at 0.5× the physical disk radius, dt-decoupled"
    (let [[w star] (star-with-disk (* 1.2 law/solar-mass) 1.5e12)
          ws (disc-evolution/disk-evolution-system w)
          spawn (first (get-in ws [:components c/spawn-request-disk star]))
          r (spawn-radius spawn)]
      (is (some? spawn))
      (is (= :protostar (:matter-state spawn)))
      (is (< (abs (- r (* 0.5 1.5e12))) (* 0.05 1.5e12))
          "spawn radius ≈ 0.5× disk radius (10 AU disk → ~5 AU)")
      (is (< r (* 10.0 law/au))))))

;; --- formation-placement-v2: disk-scale gate + Hill-stable clamp -------------

(defn- fragmenting-ws-with-perturber
  "fragmenting-ws plus a second massive body (a perturber star of
   `perturber-mass` at `dist-m` along +x), so the Hill-stable clamp has a
   tidal field to bite on."
  [disk-mass radius-m perturber-mass dist-m]
  (let [[w star] (star-with-disk disk-mass radius-m)
        [w _pert] (seeder/spawn-clump
                   w {:position [dist-m 0.0 0.0]
                      :velocity [0.0 0.0 0.0]
                      :mass perturber-mass
                      :radius law/solar-radius
                      :temperature 2.0e7
                      :matter-state :star
                      :composition lcomp/solar-composition})
        w (assoc-in w [:test/disk-regime star]
                    {:toomre-q 0.5 :cooling-beta 1.5
                     :regime :fragmenting
                     :solid-surface-density 10.0
                     :snow-line 2.7e11})]
    [(disc-evolution/disk-evolution-system w) star]))

(deftest clump-scale-disk-gate-blocks-gi-spawn
  (testing "a disk with r-disk = 1e4 AU produces NO spawn (disk-scale gate)"
    (let [[ws star] (fragmenting-ws (* 0.8 law/solar-mass) (* 1.0e4 law/au) {})
          spawns (get-in ws [:components c/spawn-request-disk star])]
      (is (nil? spawns)
          "a clump-scale 'disk' is still collapsing — spawning into it is the kAU birth defect"))))

(deftest clump-scale-disk-gate-blocks-binary-spawn
  (testing "a clump-scale disk does not spawn a binary companion either"
    (let [[w star] (star-with-disk (* 1.2 law/solar-mass) (* 1.0e4 law/au))
          ws (disc-evolution/disk-evolution-system w)
          spawns (get-in ws [:components c/spawn-request-disk star])]
      (is (nil? spawns)))))

(deftest sane-disk-passes-gate-and-spawns-at-physical-radius
  (testing "a sane 20 AU disk passes the gate and spawns at 0.3× r-disk"
    (let [r-disk (* 20.0 law/au)
          [ws star] (fragmenting-ws (* 0.8 law/solar-mass) r-disk {})
          spawn (first (get-in ws [:components c/spawn-request-disk star]))
          r (spawn-radius spawn)]
      (is (some? spawn))
      (is (< (abs (- r (* 0.3 r-disk))) (* 0.05 r-disk))
          "spawn radius ≈ 0.3× disk radius (20 AU disk → ~6 AU)"))))

(deftest hill-clamp-caps-spawn-radius
  (testing "the Hill clamp caps r-orbit where the 100× dominance fails"
    (let [dist (* 29.24 law/au)
          [ws star] (fragmenting-ws-with-perturber (* 0.8 law/solar-mass) (* 20.0 law/au)
                                                   law/solar-mass dist)
          spawn (first (get-in ws [:components c/spawn-request-disk star]))
          r (spawn-radius spawn)
          ;; r_max = ∛(M_host·d³ / (2×100×m_pert)) — equal masses → d/∛200 ≈ 5 AU
          r-hill-expected (math/cbrt (/ (* law/solar-mass dist dist dist)
                                        (* 200.0 law/solar-mass)))]
      (is (some? spawn))
      (is (< (abs (- r r-hill-expected)) (* 0.10 r-hill-expected))
          "capped at the tidal-dominance radius (~5 AU), not the raw 6 AU")
      (is (< r (* 0.3 20.0 law/au))
          "strictly below the unclamped 0.3× r-disk placement"))))

(deftest hill-clamp-below-floor-skips-spawn
  (testing "when the clamp would cross the 0.3 AU floor the spawn is skipped, never placed"
    (let [[ws star] (fragmenting-ws-with-perturber (* 0.8 law/solar-mass) (* 20.0 law/au)
                                                   law/solar-mass law/au)
          spawns (get-in ws [:components c/spawn-request-disk star])]
      (is (nil? spawns)
          "r-hill-max ≈ 0.17 AU < 0.3 AU floor → skip this tick, retry later"))))

(deftest no-spawn-ever-beyond-100-au-from-host
  (testing "FORMED-radius invariant: across synthetic disk states no spawn lands > 100 AU out"
    (doseq [r-disk-au [1.0 5.0 20.0 50.0 99.0]]
      (let [[ws star] (fragmenting-ws (* 0.8 law/solar-mass) (* r-disk-au law/au) {})
            spawn (first (get-in ws [:components c/spawn-request-disk star]))]
        (is (some? spawn) (str "sane disk at " r-disk-au " AU still fragments"))
        (when spawn
          (is (<= (spawn-radius spawn) disc-evolution/max-fragmentation-disk-radius)
              (str "spawn from a " r-disk-au " AU disk stays ≤ 100 AU from host"))
          (is (>= (spawn-radius spawn) disc-evolution/fragment-placement-floor-m)
              "and never below the 0.3 AU floor"))))
    (doseq [dist-au [10.0 30.0 100.0]]
      (let [[ws star] (fragmenting-ws-with-perturber (* 0.8 law/solar-mass) (* 20.0 law/au)
                                                     law/solar-mass (* dist-au law/au))
            spawn (first (get-in ws [:components c/spawn-request-disk star]))]
        (when spawn
          (is (<= (spawn-radius spawn) disc-evolution/max-fragmentation-disk-radius)
              (str "clamped spawn (perturber at " dist-au " AU) stays ≤ 100 AU from host")))))))
