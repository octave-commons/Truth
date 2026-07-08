(ns domain.eos-test
  "Step 5: pressure as a pure equation of state. One :eos system owns pressure
   and derives P = ρ k_B T / m_H; the former four writers no longer touch it."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.stellar.structure :as structure]
   [law.stellar :as law]))

(deftest eos-derives-ideal-gas-pressure
  (let [[w e0] (ecs/spawn (ecs/empty-world))
        [w e1] (ecs/spawn w)
        w   (-> (ecs/put-components w e0 {c/density 1.0e-16 c/temperature 50.0})
                (ecs/put-components e1 {c/density 1.0e3  c/temperature 1.0e6}))
        sys (structure/eos-system)
        ws  ((:run sys) w)
        p   (get ws c/pressure)]
    (testing "system contract: sole writer of pressure"
      (is (= :eos (:id sys)))
      (is (= #{c/pressure} (:writes sys)))
      (is (= #{c/pressure} (set (keys ws)))))
    (testing "pressure equals law/ideal-gas-pressure for every body"
      (is (= (law/ideal-gas-pressure 1.0e-16 50.0) (get p e0)))
      (is (= (law/ideal-gas-pressure 1.0e3 1.0e6)  (get p e1))))))

(deftest eos-skips-bodies-missing-density-or-temperature
  (let [[w e0] (ecs/spawn (ecs/empty-world))
        [w e1] (ecs/spawn w)
        w  (-> (ecs/put-components w e0 {c/density 1.0e-16 c/temperature 50.0})
               (ecs/put-component e1 c/density 1.0e-16))   ;; no temperature
        ws ((:run (structure/eos-system)) w)]
    (is (contains? (get ws c/pressure) e0))
    (is (not (contains? (get ws c/pressure) e1))
        "a body without temperature gets no pressure")))
