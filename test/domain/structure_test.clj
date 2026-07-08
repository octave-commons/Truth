(ns domain.structure-test
  "Step 7b: the Structure owner. radius and density are one geometric fact,
   owned by a single system that branches on matter-state — gas SPH, solid
   material density, or KH oblate contraction."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.spatial.index :as spatial]
   [domain.stellar.structure :as structure]))

(defn- finite? [x] (and (number? x) (Double/isFinite (double x))))

(deftest resolved-shape-solids-use-material-density
  (testing "debris: fixed rocky density, radius derived from mass"
    (let [s (structure/resolved-shape {:matter-state :planetesimal :mass 1.0e22} 0.5 9.5e14 1.0e12)]
      (is (= structure/debris-material-density (:density s)))
      (is (= (structure/sphere-radius 1.0e22 structure/debris-material-density) (:radius s)))
      (is (nil? (:oblateness s)) "solids carry no oblate shape")))
  (testing "planet: lower mixed density"
    (is (= structure/planet-material-density
           (:density (structure/resolved-shape {:matter-state :planet :mass 1.0e25}
                                               0.5 9.5e14 1.0e12))))))

(deftest resolved-shape-protostar-contracts-and-flattens
  (let [m 2.0e30
        s (structure/resolved-shape {:matter-state :protostar :mass m :radius 1.0e13
                                     :oblateness 1.0 :angular-momentum [0.0 0.0 1.0e42]}
                                    0.5 9.5e14 1.0e12)]
    (testing "produces a full shape: finite radius, density, oblateness, axis"
      (is (every? finite? [(:radius s) (:density s) (:oblateness s)]))
      (is (= 3 (count (:rotation-axis s)))))
    (testing "contracts: new radius is no larger than the starting radius"
      (is (<= (:radius s) 1.0e13)))
    (testing "spinning core flattens: oblateness ≤ 1"
      (is (<= (:oblateness s) 1.0)))))

(deftest structure-system-owns-radius-and-density-across-regimes
  (let [[w gas]    (ecs/spawn (ecs/empty-world))
        [w deb]    (ecs/spawn w)
        w (-> (ecs/put-components w gas {c/matter-state :nebula c/position [0.0 0.0 0.0]
                                         c/density 1.0e-16 c/pressure 1.0e-10
                                         c/mass 4.0e27 c/radius 6.0e13 c/temperature 15.0})
              (ecs/put-components deb {c/matter-state :planetesimal c/position [1.0e15 0.0 0.0]
                                       c/density 1.0e3 c/pressure 0.0
                                       c/mass 1.0e23 c/radius 1.0e6 c/temperature 100.0}))
        w (spatial/spatial-index w)
        sys (structure/structure-system)
        ws  ((:run sys) w)]
    (testing "sole writer of shape components"
      (is (= :structure (:id sys)))
      (is (= #{c/radius c/density c/oblateness c/rotation-axis} (:writes sys))))
    (testing "gas parcel gets an SPH density and an adaptive radius"
      (is (finite? (get-in ws [c/density gas])))
      (is (pos? (get-in ws [c/density gas])))
      (is (pos? (get-in ws [c/radius gas]))))
    (testing "debris body gets its material density and a mass-derived radius"
      (is (= structure/debris-material-density (get-in ws [c/density deb])))
      (is (= (structure/sphere-radius 1.0e23 structure/debris-material-density)
             (get-in ws [c/radius deb]))))))
