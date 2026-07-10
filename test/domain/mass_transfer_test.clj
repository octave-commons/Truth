(ns domain.mass-transfer-test
  "Tests for gradual mass transfer: BHL sink accretion and Roche-lobe overflow."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.mass-transfer :as mt]
   [domain.integrator :as integ]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.stellar.seeder :as seeder]
   [law.mass-transfer :as lmt]
   [law.stellar :as law]
   [domain.spatial.index]))

(deftest bondi-capture-radius-is-smaller-than-bondi-radius
  (let [M   1.0e30
        c-s 1.0e3
        v   2.0e3
        r-b (lmt/bondi-radius M c-s)
        r-a (lmt/capture-radius M c-s v)]
    (is (pos? r-b))
    (is (pos? r-a))
    (is (< r-a r-b))))

(deftest bhl-rate-increases-with-density
  (let [M     1.0e30
        c-s   1.0e3
        v     1.0e3
        rho1  1.0e-15
        rho2  2.0e-15
        mdot1 (lmt/bhl-accretion-rate M rho1 c-s v)
        mdot2 (lmt/bhl-accretion-rate M rho2 c-s v)]
    (is (pos? mdot1))
    (is (pos? mdot2))
    (is (> mdot2 mdot1))))

(deftest capped-delta-mass-honours-fractional-caps
  (let [dm (lmt/capped-delta-mass
            {:dot-m 1.0e30 :dt 1.0e10
             :gas-mass 1.0e25
             :donor-mass 1.0e24
             :accretion-fraction-cap 0.25
             :donor-fraction-cap 0.25})]
    (is (<= dm (* 0.25 1.0e25)))
    (is (<= dm (* 0.25 1.0e24)))
    (is (pos? dm))))

(deftest roche-lobe-radius-scales-with-separation
  (let [M-d 1.0e30
        M-a 5.0e29
        r1  (lmt/roche-lobe-radius 1.0e11 M-d M-a)
        r2  (lmt/roche-lobe-radius 2.0e11 M-d M-a)]
    (is (pos? r1))
    (is (pos? r2))
    (is (> r2 r1))))

(deftest ritter-rate-is-zero-without-overflow
  (let [M-d 1.0e30
        a   1.0e11
        R-L (lmt/roche-lobe-radius a M-d (* 0.5 M-d))
        rate (lmt/ritter-isothermal-rate M-d a (* 0.5 R-L) R-L)]
    (is (zero? rate))))

(defn- world-with-sink-and-gas
  "Return [world sink-eid gas-eid] with a protostellar sink and one nearby
   nebula parcel inside its capture radius."
  []
  (let [w (ecs/empty-world)
        [w sink] (seeder/spawn-clump
                  w {:position [0.0 0.0 0.0]
                     :velocity [0.0 0.0 0.0]
                     :mass (* 0.1 law/solar-mass)
                     :radius 1.0e10
                     :matter-state :protostar
                     :temperature 1.0e3})
        [w gas] (seeder/spawn-clump
                 w {:position [1.0e11 0.0 0.0]
                    :velocity [-1.0e3 0.0 0.0]
                    :mass 1.0e28
                    :radius 1.0e13
                    :matter-state :nebula
                    :density 1.0e-14
                    :temperature 1.0e2})]
    [w sink gas]))

(deftest accretion-radius-system-emits-rate-metadata
  (let [[w sink _gas] (world-with-sink-and-gas)
        w (assoc w :sim/dt 1.0e12 :tick 0)
        ws ((:run (mt/accretion-radius-system)) w)
        rate (get-in ws [c/accretion-rate sink])]
    (is (some? rate))
    (is (contains? rate :sink/r-acc))
    (is (contains? rate :sink/dot-m))
    (is (contains? rate :sink/regime))))

(deftest sink-flux-system-routes-gas-to-a-protostars-disk
  ;; The sink is a :protostar (disk-former), so captured gas is routed to its
  ;; protoplanetary disk (c/disk-mass-flux), NOT merged straight into the core.
  (let [[w sink gas] (world-with-sink-and-gas)
        w (-> w
              (assoc :sim/dt 1.0e12 :tick 0)
              (domain.spatial.index/spatial-index)
              (ecs/put-component sink c/accretion-rate
                                 {:sink/r-acc 2.0e11
                                  :sink/dot-m 1.0e20
                                  :sink/ambient-density 1.0e-14}))
        ws ((:run (mt/sink-accretion-flux-system)) w)
        disk-dm (get-in ws [c/disk-mass-flux sink])
        gas-dm  (get-in ws [c/mass-flux-transfer gas])]
    (testing "gas is disk-routed and drained from the donor, conservatively"
      (is (pos? disk-dm) "the protostar's disk gains the accreted gas mass")
      (is (neg? gas-dm) "the donor gas parcel is debited")
      (is (== disk-dm (- gas-dm)) "disk gain equals donor loss"))
    (testing "accreted angular momentum feeds the disk-L channel"
      (is (contains? (get ws c/disk-l-flux) sink)))))

(deftest roche-lobe-system-emits-conservative-overflow
  (let [w (ecs/empty-world)
        [w donor] (seeder/spawn-clump
                   w {:position [0.0 0.0 0.0]
                      :velocity [0.0 0.0 0.0]
                      :mass (* 1.0 law/solar-mass)
                      :radius 2.0e9
                      :matter-state :star})
        [w accr] (seeder/spawn-clump
                  w {:position [3.0e9 0.0 0.0]
                     :velocity [0.0 1.0e4 0.0]
                     :mass (* 0.5 law/solar-mass)
                     :radius (* 1.5 law/solar-radius)
                     :matter-state :star})
        [w pair-eid] (ecs/spawn w)
        w (-> w
              (assoc :sim/dt 1.0e6 :tick 0)
              (ecs/put-component pair-eid c/binary-pair
                                 {:binary-pair/donor donor
                                  :binary-pair/accretor accr
                                  :orbit/semi-major-axis 3.0e9
                                  :orbit/eccentricity 0.0}))
        ws ((:run (mt/roche-lobe-system)) w)
        roche (get-in ws [c/roche-lobe pair-eid])
        dm-donor (get-in ws [c/mass-flux-transfer donor])
        dm-accr  (get-in ws [c/mass-flux-transfer accr])]
    (is (some? roche))
    (is (true? (:roche-lobe/overflow? roche)))
    (is (neg? dm-donor))
    (is (pos? dm-accr))
    (is (== dm-donor (- dm-accr))
        "conservative: donor debit equals accretor credit")))

(deftest combined-system-emits-both-kinds
  (let [[w sink gas] (world-with-sink-and-gas)
        [w accr] (seeder/spawn-clump
                  w {:position [1.0e10 0.0 0.0]
                     :velocity [0.0 1.0e4 0.0]
                     :mass (* 0.5 law/solar-mass)
                     :radius (* 1.5 law/solar-radius)
                     :matter-state :star})
        [w pair-eid] (ecs/spawn w)
        w (-> w
              (assoc :sim/dt 1.0e6 :tick 0)
              (domain.spatial.index/spatial-index)
              (ecs/put-component sink c/accretion-rate
                                 {:sink/r-acc 2.0e11
                                  :sink/dot-m 1.0e20
                                  :sink/ambient-density 1.0e-14})
              (ecs/put-component pair-eid c/binary-pair
                                 {:binary-pair/donor sink
                                  :binary-pair/accretor accr
                                  :orbit/semi-major-axis 1.0e10
                                  :orbit/eccentricity 0.0}))
        ws ((:run (mt/mass-transfer-system)) w)
        transfers (get ws c/mass-flux-transfer)]
    (testing "BHL debits the gas donor"
      (is (neg? (get transfers gas))))
    (testing "RLOF credits the accretor"
      (is (pos? (get transfers accr))))))

(deftest integrator-applies-transfer-debit-and-credit
  (let [w (ecs/empty-world)
        [w sink] (seeder/spawn-clump
                  w {:position [0.0 0.0 0.0]
                     :velocity [0.0 0.0 0.0]
                     :mass 1.0e30
                     :radius 1.0e10
                     :matter-state :protostar})
        [w donor] (seeder/spawn-clump
                   w {:position [1.0e11 0.0 0.0]
                      :velocity [0.0 1.0e3 0.0]
                      :mass 1.0e28
                      :radius 1.0e13
                      :matter-state :nebula})
        w (-> w
              (assoc :sim/dt 1.0e12 :tick 0)
              (ecs/put-component sink c/mass-flux-transfer 1.0e27)
              (ecs/put-component donor c/mass-flux-transfer -1.0e27))
        ws (integ/mass-ws w)
        sink-m (get-in ws [c/mass sink])
        donor-m (get-in ws [c/mass donor])]
    (testing "mass-flux-transfer folds through the generic :mass accumulate"
      (is (== sink-m (+ 1.0e30 1.0e27)))
      (is (== donor-m (- 1.0e28 1.0e27))))))

(deftest integrator-reaps-depleted-donors
  (let [w (ecs/empty-world)
        [w sink] (seeder/spawn-clump
                  w {:position [0.0 0.0 0.0]
                     :velocity [0.0 0.0 0.0]
                     :mass 1.0e30
                     :radius 1.0e10
                     :matter-state :protostar})
        [w donor] (seeder/spawn-clump
                   w {:position [1.0e11 0.0 0.0]
                      :velocity [0.0 1.0e3 0.0]
                      :mass 1.0e20
                      :radius 1.0e10
                      :matter-state :nebula})
        w (-> w
              (assoc :sim/dt 1.0e12 :tick 0)
              (ecs/put-component sink c/mass-flux-transfer 1.0e25)
              (ecs/put-component donor c/mass-flux-transfer -1.0e25))
        ws (integ/mass-ws w)]
    (is (get-in ws [c/consumed-transfer donor])
        "donor drained below floor is marked for reaping via consumed-transfer")))
