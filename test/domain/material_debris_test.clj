(ns domain.material-debris-test
  (:require [clojure.test :refer [deftest is]]
            [domain.material :as material]
            [law.material :as lm]
            [domain.ecs.core :as ecs]
            [domain.ecs.components :as c]
            [domain.ecs.tick :as tick]
            [domain.integrator :as integrator]))

(defn cell-world []
  (reduce (fn [w [pos vel]]
            (let [[w id] (ecs/spawn w)]
              (ecs/put-components w id
                                  {c/position pos c/velocity vel c/mass 1000.0
                                   c/radius 1.0 c/body-kind :material-cell
                                   c/temperature 180.0 c/internal-energy 144000000.0
                                   c/material-cell {:silicate-kg 1000.0 :iron-kg 0.0
                                                    :water-kg 0.0 :edge-m 2.0
                                                    :pressure-pa 1.0}})))
          (assoc (ecs/empty-world) :sim/dt 0.001
                 :material/contact-frequency 1.0 :material/damping-ratio 0.6)
          [[[0.0 0.0 0.0] [1.0 0.0 0.0]] [[1.9 0.0 0.0] [-1.0 0.0 0.0]]]))

(deftest water-is-a-stoichiometric-carrier
  (let [stocks {:silicate-kg 0.0 :iron-kg 0.0 :water-kg 9.0 :edge-m 2.0 :pressure-pa 1.0}
        els (lm/element-masses stocks)]
    (is (< (abs (- 9.0 (reduce + (vals els)))) 1e-12))
    (is (pos? (:H els)))
    (is (pos? (:O els)))
    (is (thrown? clojure.lang.ExceptionInfo (lm/validate-cell! (assoc stocks :water-kg -1.0))))))

(deftest contact-force-and-heat-contract
  (let [w (cell-world) ws ((:run (material/contact-system)) w)
        a (get ws c/accel-material) q (get ws c/heat-material)]
    (is (< (abs (+ (first (a 0)) (first (a 1)))) 1e-12))
    (is (neg? (first (a 0))))
    (is (every? pos? (vals q)))))

(deftest internal-energy-channel-is-integrated-once
  (let [w (ecs/put-component (cell-world) 0 c/heat-material 25.0)
        before (ecs/get-component w 0 c/internal-energy)
        after (tick/apply-write-set w ((:run (integrator/integrator-system 0.001)) w))
        clean (tick/apply-write-set after ((:run (material/contact-system))
                                          (-> after (ecs/put-component 1 c/position [20.0 0.0 0.0]))))]
    (is (= (+ before 25.0) (ecs/get-component after 0 c/internal-energy)))
    (is (zero? (ecs/get-component clean 0 c/heat-material)))))

(deftest labels-are-observations
  (let [w (cell-world) ws ((:run (material/aggregate-system)) w)]
    (is (= #{c/material-aggregate} (set (keys ws))))
    (is (= 2 (get-in ws [c/material-aggregate 0 :cells])))))

(deftest latent-heat-does-not-create-water
  (let [cell {:silicate-kg 0.0 :iron-kg 0.0 :water-kg 9.0 :edge-m 2.0 :pressure-pa 1.0}
        boundary (lm/sublimation-temperature 1.0)
        energy (+ (* (lm/heat-capacity cell) boundary) (* 0.5 9.0 2834000.0))
        phases (lm/thermal-state cell energy)]
    (is (< (abs (- 4.5 (:water-vapor-kg phases))) 1e-10))
    (is (< (abs (- 9.0 (+ (:water-vapor-kg phases) (:water-ice-kg phases)))) 1e-10))
    (is (= boundary (:temperature phases)))))

(defn collision-energy-error [dt]
  (let [initial (cell-world)
        steps (long (/ 2.0 dt))
        final (nth (iterate #(tick/run-parallel (assoc % :sim/dt dt)
                                                [(material/contact-system) (integrator/integrator-system dt)]) initial) steps)
        items (material/cells final)
        kinetic (reduce + (map #(* 0.5 (:m %) (reduce + (map (fn [v] (* v v)) (:v %)))) items))
        overlap (max 0.0 (- 2.0 (shape.spatial/dist (:p (first items)) (:p (second items)))))
        spring (* 0.5 500.0 overlap overlap)
        heat (- (reduce + (vals (get-in final [:components c/internal-energy]))) 288000000.0)]
    (abs (- (+ kinetic spring heat) 1002.5))))

(deftest contact-step-convergence
  (is (< (collision-energy-error 0.005) (* 0.75 (collision-energy-error 0.01)))))

(deftest regional-quantization-conserves-extensive-stocks
  (let [w (cell-world) grid (material/regional-grid w 0.7)
        entries (vals grid)]
    (is (< (abs (- 2000.0 (reduce + (map :mass-kg entries)))) 1e-9))
    (is (< (abs (- 288000000.0 (reduce + (map :internal-J entries)))) 1e-5))
    (is (< (abs (- 2000.0 (reduce + (map #(get-in % [:stocks-kg :silicate-kg]) entries)))) 1e-9))
    (is (< (abs (first (reduce shape.spatial/v+ [0.0 0.0 0.0] (map :momentum entries)))) 1e-9))))
