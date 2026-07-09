(ns domain.disk-evolution-test
  "Tests for protoplanetary disk regime and restricted GI fragmentation."
  (:require
   [clojure.math :as math] [clojure.test :refer [deftest testing is]]
   [domain.stellar :as stellar]
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
        [w eid] (stellar/spawn-clump
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
          ws (stellar/disk-evolution-system w)
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
          ws (stellar/disk-evolution-system w0)
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
          ws (stellar/disk-evolution-system w)
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
          ws (stellar/disk-evolution-system w)
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
          ws (stellar/disk-evolution-system w)
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
                (ecs/put-component star c/disk-fragments-spawned stellar/max-gi-fragments-per-disk))
          ws (stellar/disk-evolution-system w)
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
          ws (stellar/disk-evolution-system w0)
          planet-spawns (get-in ws [:components c/spawn-request-planet star])]
      (is (nil? planet-spawns)))))
