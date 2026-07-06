(ns domain.stellar-wind-test
  "Tests for stellar wind plasma state: hot, ionized, star-composition parcels."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.stellar :as stellar]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.tick :as tick]
   [law.stellar :as law]
   [shape.spatial :as sp]))

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

(deftest wind-parcel-is-ionized-and-hot
  (let [[w star] (world-with-star)
        w (-> w
              (assoc :genesis/wind-parcel-mass 1.0e24
                     :genesis/wind-rate-scale 1.0e6
                     :sim/dt 1.0e13
                     :tick 0)
              (ecs/put-component star c/wind-reservoir 1.0e24))
        ws ((:run (stellar/stellar-wind-system)) w)
        spawns (get-in ws [c/spawn-request-wind star])
        spawn (first spawns)]
    (is (some? spawn))
    (is (>= (get-in spawn [:extra-components c/ionization-fraction]) 0.9))
    (is (>= (:temperature spawn) 1.0e6))))

(deftest wind-launches-with-star-composition
  (let [[w star] (world-with-star)
        star-comp (ecs/get-component w star c/composition)
        w (-> w
              (assoc :genesis/wind-parcel-mass 1.0e24
                     :genesis/wind-rate-scale 1.0e6
                     :sim/dt 1.0e13
                     :tick 0)
              (ecs/put-component star c/wind-reservoir 1.0e24))
        ws ((:run (stellar/stellar-wind-system)) w)
        spawn (first (get-in ws [c/spawn-request-wind star]))]
    (is (= star-comp (:composition spawn)))))

(deftest mass-and-momentum-conserved-in-wind-launch
  (let [[w star] (world-with-star)
        m0 (ecs/get-component w star c/mass)
        v0 (ecs/get-component w star c/velocity)
        w (-> w
              (assoc :genesis/wind-parcel-mass 1.0e24
                     :genesis/wind-rate-scale 1.0e6
                     :sim/dt 1.0e13
                     :tick 0)
              (ecs/put-component star c/wind-reservoir 1.0e24))
        ws ((:run (stellar/stellar-wind-system)) w)
        dm (get-in ws [c/mass-flux-wind star])
        dv (get-in ws [c/dv-wind star])
        spawn (first (get-in ws [c/spawn-request-wind star]))]
    (is (neg? dm))
    (is (= (:mass spawn) 1.0e24) "parcel mass matches configured wind-parcel-mass")
    (is (not= dv [0.0 0.0 0.0]) "star receives recoil")
    ;; Momentum: star recoil M1 * |dv| equals parcel momentum m_p * |v_rel|
    (let [p-mass (:mass spawn)
          M1 (- m0 (- dm))
          rel-v (sp/v- (:velocity spawn) v0)
          recoil-mag (* M1 (sp/len dv))
          parcel-mom-mag (sp/len (sp/v* rel-v p-mass))]
      (is (< (Math/abs (- recoil-mag parcel-mom-mag)) 1.0)))))

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
        ws ((:run (stellar/temperature-system 1.0e10)) w)
        t1 (get-in ws [c/temperature eid])]
    (is (< t1 1.0e6))
    (is (>= t1 3.0))))
