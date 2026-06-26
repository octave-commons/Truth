(ns domain.particles.field-test
  "Tests for the gas particle field: seeding, accretion, and stepping."
  (:require [clojure.test :refer [deftest testing is]]
            [domain.particles.field :as field]
            [domain.particles.pm :as pm]))

(deftest test-make-field
  (testing "A fresh field has the requested capacity and zero mass"
    (let [f (field/make-field 100 1.0 1.0)]
      (is (= 100 (.cap f)))
      (is (zero? (field/total-mass f))))))

(deftest test-seed-cloud
  (testing "Seeding fills the requested number of live particles"
    (let [f (field/make-field 200 1.0 1.0)
          rng (java.util.Random. 42)]
      (field/seed-cloud! f {:n 100 :cloud-r 20.0 :spin 0.05 :turb 0.1
                            :particle-mass 0.01 :rng rng})
      (is (= 100 (field/live-count f)))
      (is (> (field/total-mass f) 0.0)))))

(deftest test-accrete-merges-overlapping
  (testing "Particles close enough merge into one"
    (let [f (field/make-field 4 1.0 1.0)
          ^doubles px (.px f) ^doubles py (.py f) ^doubles pz (.pz f)
          ^doubles vx (.vx f) ^doubles vy (.vy f) ^doubles vz (.vz f)
          ^doubles mass (.mass f) ^doubles radius (.radius f)]
      ;; two overlapping particles of equal mass
      (aset px 0 0.0) (aset py 0 0.0) (aset pz 0 0.0)
      (aset px 1 0.5) (aset py 1 0.0) (aset pz 1 0.0)
      (aset vx 0 1.0) (aset vy 0 0.0) (aset vz 0 0.0)
      (aset vx 1 -1.0) (aset vy 1 0.0) (aset vz 1 0.0)
      (aset mass 0 1.0) (aset mass 1 1.0)
      (aset radius 0 1.0) (aset radius 1 1.0)
      (field/accrete! f 2.0)
      (is (= 1 (field/live-count f)))
      (is (< (Math/abs (- (field/total-mass f) 2.0)) 1e-9))
      ;; momentum conserved: 1*1 + 1*(-1) = 0
      (is (< (Math/abs (aget vx 0)) 1e-9)))))

(deftest test-step-preserves-mass
  (testing "A single step does not lose total mass"
    (let [f (field/make-field 200 1.0 1.0)
          rng (java.util.Random. 7)
          mesh (pm/make-mesh 16 40.0 1.0)]
      (field/seed-cloud! f {:n 100 :cloud-r 10.0 :spin 0.05 :turb 0.05
                            :particle-mass 0.01 :rng rng})
      (let [before (field/total-mass f)]
        (field/step! f mesh 0.01 {})
        (is (< (Math/abs (- (field/total-mass f) before)) 1e-9))))))

(deftest test-sink-particles
  (testing "sink-particles reports particles above the mass threshold"
    (let [f (field/make-field 4 1.0 1.0)
          ^doubles mass (.mass f)]
      (aset mass 0 0.1)
      (aset mass 1 5.0)
      (aset mass 2 3.0)
      (aset mass 3 0.0)
      (is (= [[1 5.0] [2 3.0]] (field/sink-particles f 2.0))))))
