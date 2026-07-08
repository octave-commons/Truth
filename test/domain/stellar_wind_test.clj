(ns domain.stellar-wind-test
  "Tests for stellar wind plasma state: stars emit a radial wind profile that
   heats, ionizes, and ablates nearby gas parcels instead of spawning ballistic
   parcels."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.stellar :as stellar]
   [domain.stellar.structure :as structure]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.tick :as tick]
   [law.stellar :as law]))

(defn- world-with-star
  "Return [world star-eid] with a luminous star."
  []
  (let [w (ecs/empty-world)
        [w eid] (stellar/spawn-clump
                 w {:position [0.0 0.0 0.0]
                    :velocity [0.0 0.0 0.0]
                    :mass (* 1.0 law/solar-mass)
                    :radius (* 1.0 law/solar-radius)
                    :matter-state :star
                    :temperature 2.0e8
                    :composition {:H 0.70 :He 0.28 :O 0.005 :C 0.005}})
        w (ecs/put-component w eid c/luminosity law/solar-luminosity)]
    [w eid]))

(deftest wind-profile-is-ionized-and-hot
  (let [[w star] (world-with-star)
        w (-> w
              (assoc :genesis/wind-rate-scale 1.0e6
                     :sim/dt 1.0e13
                     :tick 0))
        ws ((:run (stellar/stellar-wind-system)) w)
        profile (get-in ws [c/wind-profile star])]
    (is (some? profile) "star emitted a wind profile")
    (is (pos? (:wind/dot-m profile)) "profile has positive mass-loss rate")
    (is (pos? (:wind/v-escape profile)) "profile has positive launch speed")
    (is (>= (:wind/ionization profile) 0.3) "profile is ionized")
    (is (>= (:wind/corona-t profile) 1.0e6) "profile corona temperature is hot")))

(deftest wind-profile-mass-loss-rate-scales-with-luminosity
  (let [[w star] (world-with-star)
        w (assoc w :sim/dt 1.0e13 :tick 0)
        low-k  ((:run (stellar/stellar-wind-system)) (assoc w :genesis/wind-rate-scale 1.0))
        high-k ((:run (stellar/stellar-wind-system)) (assoc w :genesis/wind-rate-scale 10.0))
        mdot-low  (:wind/dot-m (get-in low-k  [c/wind-profile star]))
        mdot-high (:wind/dot-m (get-in high-k [c/wind-profile star]))
        ratio     (/ mdot-high mdot-low)]
    (is (pos? mdot-low))
    (is (pos? mdot-high))
    (is (< 5.0 ratio 15.0) "mass-loss rate scales roughly linearly with k")))

(deftest wind-ablation-heats-nearby-parcel
  (let [[w star] (world-with-star)
        gas-pos [1.0e14 0.0 0.0]
        [w gas] (stellar/spawn-clump w {:position gas-pos
                                        :velocity [0.0 0.0 0.0]
                                        :mass 1.0e28
                                        :radius 6.0e13
                                        :matter-state :nebula
                                        :density 1.0e-16
                                        :temperature 10.0})
        w (-> w
              (assoc :genesis/wind-rate-scale 1.0e6
                     :genesis/wind-interaction-factor 10.0
                     :genesis/gas-smoothing-radius 6.0e13
                     :sim/dt 1.0e13
                     :tick 0))
        w1 (tick/apply-write-set w ((:run (stellar/stellar-wind-system)) w))
        ws ((:run (stellar/wind-ablation-system)) w1)
        heating (get-in ws [c/wind-heating gas])]
    (is (some? heating) "parcel received wind-heating influence")
    (is (pos? (:wind-heating/delta-t heating)) "heating is positive")
    (is (pos? (:wind-heating/ionization-rate heating)) "ionization rate is positive")
    (is (= star (:wind-heating/source-eid heating)) "source is the star")))

(deftest wind-ablation-mass-conserved-in-ledger
  (let [[w star] (world-with-star)
        gas-pos [1.0e14 0.0 0.0]
        [w gas] (stellar/spawn-clump w {:position gas-pos
                                        :velocity [0.0 0.0 0.0]
                                        :mass 1.0e28
                                        :radius 6.0e13
                                        :matter-state :nebula
                                        :density 1.0e-16
                                        :temperature 10.0})
        w (-> w
              (assoc :genesis/wind-rate-scale 1.0e6
                     :genesis/wind-interaction-factor 10.0
                     :genesis/gas-smoothing-radius 6.0e13
                     :sim/dt 1.0e13
                     :tick 0))
        w1 (tick/apply-write-set w ((:run (stellar/stellar-wind-system)) w))
        ws ((:run (stellar/wind-ablation-system)) w1)
        dm-gas (:wind-heating/mass-loss (get-in ws [c/wind-heating gas]))
        dm-star (get-in ws [c/wind-mass-lost star])]
    (is (pos? dm-gas) "gas parcel lost mass")
    (is (pos? dm-star) "star ledger recorded ablated mass")
    (is (< (abs (- dm-gas dm-star)) 1.0e-6) "ablated mass equals ledger entry")))

(deftest wind-ablation-respects-min-mass-floor
  (testing "A parcel at or below the ablation floor loses no mass"
    (let [[w _star] (world-with-star)
          gas-pos [1.0e14 0.0 0.0]
          [w gas] (stellar/spawn-clump w {:position gas-pos
                                          :velocity [0.0 0.0 0.0]
                                          :mass 1.0e28
                                          :radius 6.0e13
                                          :matter-state :nebula
                                          :density 1.0e-16
                                          :temperature 10.0})
          w (-> w
                (assoc :genesis/wind-rate-scale 1.0e6
                       :genesis/wind-interaction-factor 10.0
                       :genesis/gas-smoothing-radius 6.0e13
                       :genesis/wind-ablation-min-mass 1.0e28
                       :sim/dt 1.0e13
                       :tick 0))
          w1 (tick/apply-write-set w ((:run (stellar/stellar-wind-system)) w))
          ws ((:run (stellar/wind-ablation-system)) w1)
          heating (get-in ws [c/wind-heating gas])]
      (is (nil? heating) "parcel at floor is not ablated"))))

(deftest wind-ablation-caps-per-tick-mass-loss
  (testing "Mass loss per tick is capped at :genesis/wind-max-mass-loss-frac"
    (let [max-frac 0.05
          [w _star] (world-with-star)
          gas-pos [1.0e14 0.0 0.0]
          [w gas] (stellar/spawn-clump w {:position gas-pos
                                          :velocity [0.0 0.0 0.0]
                                          :mass 1.0e28
                                          :radius 6.0e13
                                          :matter-state :nebula
                                          :density 1.0e-16
                                          :temperature 10.0})
          w (-> w
                (assoc :genesis/wind-rate-scale 1.0e9
                       :genesis/wind-interaction-factor 10.0
                       :genesis/gas-smoothing-radius 6.0e13
                       :genesis/wind-max-mass-loss-frac max-frac
                       :genesis/wind-energy-cap-fraction 1.0e30
                       :genesis/gas-particle-mass 4.0e27
                       :sim/dt 1.0e14
                       :tick 0))
          w1 (tick/apply-write-set w ((:run (stellar/stellar-wind-system)) w))
          ws ((:run (stellar/wind-ablation-system)) w1)
          dm (:wind-heating/mass-loss (get-in ws [c/wind-heating gas]))
          expected (* max-frac 1.0e28)]
      (is (some? dm) "parcel received mass-loss")
      (is (pos? dm) "mass loss is positive")
      (is (<= dm expected) "mass loss is bounded by the per-tick cap")
      (is (< (abs (- dm expected)) 1.0e20) "mass loss hits the per-tick cap"))))

(deftest hot-nebula-cools-radiatively
  (let [w (ecs/empty-world)
        [w eid] (stellar/spawn-clump
                 w {:position [1.0e15 0.0 0.0]
                    :velocity [0.0 0.0 0.0]
                    :mass 1.0e24
                    :radius 1.0e13
                    :matter-state :nebula
                    :density 1.0e-16
                    :temperature 1.0e6})
        w (assoc w :sim/dt 1.0e10)
        ws ((:run (structure/temperature-system 1.0e10)) w)
        t1 (get-in ws [c/temperature eid])]
    (is (< t1 1.0e6))
    (is (>= t1 3.0))))
