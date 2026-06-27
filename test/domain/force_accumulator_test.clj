(ns domain.force-accumulator-test
  "Step 4: hydro-accel decomposed into single-owner accel.pressure (hydro) and
   accel.lorentz (em). Each emitter writes only its own contribution and clears
   it from bodies that stop being active."
  (:require
    [clojure.test :refer [deftest is testing]]
    [domain.ecs.core :as ecs]
    [domain.ecs.components :as c]
    [domain.ecs.tick :as tick]
    [domain.hydro :as hydro]
    [domain.em :as em]))

(defn- gas [w eid pos]
  (ecs/put-components w eid {c/matter-state :nebula
                             c/position pos
                             c/density 1.0e-16 c/pressure 1.0e-9
                             c/mass 1.0e28 c/radius 1.0e13
                             c/b-field [1.0e-8 0.0 0.0]
                             c/angular-momentum [0.0 0.0 1.0e40]}))

(deftest pressure-system-emits-only-accel-pressure
  (let [[w e0] (ecs/spawn (ecs/empty-world))
        [w e1] (ecs/spawn w)
        w   (-> (gas w e0 [0.0 0.0 0.0]) (gas e1 [1.0e13 0.0 0.0]))
        sys (hydro/pressure-acceleration)
        ws  ((:run sys) w)]
    (testing "system contract"
      (is (= :hydro (:id sys)))
      (is (= #{c/accel-pressure} (:writes sys))))
    (testing "writes only accel.pressure, a finite vector per active gas body"
      (is (= #{c/accel-pressure} (set (keys ws))))
      (let [ap (get ws c/accel-pressure)]
        (is (= #{e0 e1} (set (keys ap))))
        (is (every? (fn [v] (every? #(Double/isFinite (double %)) v)) (vals ap)))))))

(deftest pressure-system-clears-stale-contribution
  ;; A body that carried accel.pressure but is no longer hydro-active (became
  ;; :debris) must have its contribution cleared with the removed sentinel.
  (let [[w e0] (ecs/spawn (ecs/empty-world))
        [w e1] (ecs/spawn w)
        w   (-> (gas w e0 [0.0 0.0 0.0])
                (gas e1 [1.0e13 0.0 0.0])
                ;; e1 was contributing last tick, but is now solid debris
                (ecs/put-component e1 c/matter-state :debris)
                (ecs/put-component e1 c/accel-pressure [9.0 9.0 9.0]))
        ws  ((:run (hydro/pressure-acceleration)) w)]
    (is (tick/removed? (get-in ws [c/accel-pressure e1]))
        "stale contribution on the now-inactive body is cleared")
    (is (contains? (get ws c/accel-pressure) e0)
        "still-active body keeps a fresh contribution")
    (testing "folding applies the clear"
      (let [out (tick/run-parallel w [(hydro/pressure-acceleration)])]
        (is (nil? (ecs/get-component out e1 c/accel-pressure)))))))

(deftest lorentz-system-emits-only-accel-lorentz
  (let [[w e0] (ecs/spawn (ecs/empty-world))
        [w e1] (ecs/spawn w)
        w   (-> (gas w e0 [0.0 0.0 0.0]) (gas e1 [1.0e13 0.0 0.0]))
        sys (em/lorentz-acceleration-system)
        ws  ((:run sys) w)]
    (testing "system contract"
      (is (= :em-lorentz (:id sys)))
      (is (= #{c/accel-lorentz} (:writes sys))))
    (testing "writes only accel.lorentz, finite per active body"
      (is (= #{c/accel-lorentz} (set (keys ws))))
      (is (every? (fn [v] (every? #(Double/isFinite (double %)) v))
                  (vals (get ws c/accel-lorentz)))))))
