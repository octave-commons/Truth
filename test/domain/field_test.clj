(ns domain.field-test
  "Step 7e: the Field owner. Magnetic flux Φ = B·R² is frozen in at condensation
   and conserved, so B = Φ/R² amplifies as Structure contracts the radius (flux
   freezing); diffuse gas just decays resistively."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.em :as em]))

(defn- body [w eid b r state]
  (ecs/put-components w eid {c/b-field b c/radius r c/matter-state state c/density 1.0e3}))

(deftest field-freezes-flux-then-amplifies-on-contraction
  (let [[w e] (ecs/spawn (ecs/empty-world))
        w     (body w e [1.0e-6 0.0 0.0] 1.0e12 :protostar)
        sys   (em/field-system 1.0e12)
        ws1   ((:run sys) w)]
    (testing "sole writer of b-field + frozen-flux"
      (is (= :field (:id sys)))
      (is (= #{c/b-field c/frozen-flux} (:writes sys))))
    (testing "capture tick: Φ stored, B ≈ unchanged (radius steady, decay negligible)"
      (is (some? (get-in ws1 [c/frozen-flux e])))
      (is (< (Math/abs (- (first (get-in ws1 [c/b-field e])) 1.0e-6)) 1.0e-7)))
    (testing "halving the radius amplifies B ~4× (B ∝ 1/R²)"
      (let [flux (get-in ws1 [c/frozen-flux e])
            w2   (-> w (ecs/put-component e c/radius 5.0e11)
                     (ecs/put-component e c/frozen-flux flux))
            bx   (first (get-in ((:run sys) w2) [c/b-field e]))]
        (is (> bx 3.5e-6) "flux freezing amplifies the field as the core shrinks")))))

(deftest field-diffuse-gas-decays-without-freezing
  (let [[w e] (ecs/spawn (ecs/empty-world))
        w     (body w e [1.0e-8 0.0 0.0] 1.0e14 :nebula)
        ws    ((:run (em/field-system 1.0e12)) w)]
    (is (nil? (get-in ws [c/frozen-flux e])) "gas carries no frozen flux")
    (is (some? (get-in ws [c/b-field e])) "gas field persists (lightly decayed)")))
