(ns domain.genesis.formation-progress-test
  "Formation-progress metric (kanban/tasks/formation-progress-metric.md):
   :genesis/formation-progress is a [0,1] world scalar, refreshed every tick by
   the advance-simulation-clock step, equal to (Σstar-mass + Σplanet-mass) /
   :genesis/nebula-mass. Fixture conventions mirror
   domain.formation-integration-test (gravity/dt frozen so the scripted
   nebula→star→planets run is deterministic)."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.genesis :as genesis]
   [domain.stellar.seeder :as seeder]
   [law.composition :as lcomp]
   [law.stellar :as law]))

(defn- build-nebula
  "A frozen pure-nebula world: no gravity, no timestep, no adaptive pacing, so
   injected bodies keep exactly the mass the script gives them."
  []
  (-> (genesis/create-world {:gas-count 10
                             :nebula-mass 4.0e30
                             :nebula-radius 2.0e16})
      (assoc :sim/G 0.0
             :genesis/adaptive-pacing? false
             :sim/dt 0.0)))

(defn- inject-star
  "Spawn a fusion-sustaining :star of `mass` kg at the origin (the component
   set domain.formation-integration-test shows survives the classifier)."
  [w mass]
  (let [[w star] (seeder/spawn-clump
                  w {:position [0.0 0.0 0.0] :velocity [0.0 0.0 0.0]
                     :mass mass :radius law/solar-radius :temperature 2.0e7
                     :matter-state :star
                     :composition lcomp/solar-composition})]
    (-> w
        (ecs/put-component star c/pressure 1.0e13)
        (ecs/put-component star c/luminosity law/solar-luminosity))))

(defn- inject-planet
  "Spawn a :planet of `mass` kg at 5 AU, clear of the star's collision radius."
  [w mass]
  (first (seeder/spawn-clump
          w {:position [(* 5.0 law/au) 0.0 0.0] :velocity [0.0 0.0 0.0]
             :mass mass :radius 1.0e7 :temperature 300.0
             :matter-state :planet})))

(defn- inject-protostar
  "Spawn a :protostar of `mass` kg at 10 AU — an intermediate matter-state the
   metric deliberately excludes."
  [w mass]
  (first (seeder/spawn-clump
          w {:position [(* 10.0 law/au) 0.0 0.0] :velocity [0.0 0.0 0.0]
             :mass mass :radius 1.0e9 :temperature 2.0e3
             :matter-state :protostar
             :composition lcomp/solar-composition})))

(deftest formation-progress-rises-with-bound-mass
  (testing "nebula → star → planets: the scalar rises monotonically toward 1"
    (let [nebula-mass 4.0e30
          star-mass   1.0e30
          planet-mass 1.0e30
          w0 (build-nebula)
          p0 (:genesis/formation-progress (genesis/tick-world w0))
          w1 (genesis/tick-world (inject-star w0 star-mass))
          p1 (:genesis/formation-progress w1)
          w2 (genesis/tick-world (inject-planet w1 planet-mass))
          p2 (:genesis/formation-progress w2)]
      (is (some? p0) "readable off the world every tick")
      (is (<= 0.0 p0 1.0))
      (is (<= 0.0 p1 1.0))
      (is (<= 0.0 p2 1.0))
      (is (<= p0 p1 p2) "monotonically non-decreasing as mass binds")
      (is (== 0.0 p0) "a frozen fresh nebula has exactly zero bound mass")
      (is (< (abs (- p1 (+ p0 (/ star-mass nebula-mass)))) 1.0e-6)
          "binding the star adds its mass fraction")
      (is (< (abs (- p2 (+ p1 (/ planet-mass nebula-mass)))) 1.0e-6)
          "binding the planet adds its mass fraction"))))

(deftest formation-progress-clamps-to-one
  (testing "bound mass above the nebula mass saturates at 1.0"
    (let [w (-> (build-nebula)
                (inject-star 8.0e30)
                (genesis/tick-world))]
      (is (== 1.0 (:genesis/formation-progress w))))))

(deftest formation-progress-excludes-intermediate-states
  (testing "a massive :protostar does NOT move the metric (deliberate exclusion)"
    (let [w (-> (build-nebula)
                (inject-protostar 1.0e30)
                (genesis/tick-world))]
      (is (== 0.0 (:genesis/formation-progress w))
          "intermediate matter-states plateau the signal until promotion"))))

(deftest formation-progress-zero-without-nebula-mass
  (testing "a world with no :genesis/nebula-mass reads 0.0 rather than dividing by zero"
    (let [summ {:stars [{:mass 1.0e30}] :planets []}]
      (is (== 0.0 (genesis/formation-progress {} summ))))))
