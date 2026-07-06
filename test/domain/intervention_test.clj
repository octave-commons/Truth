(ns domain.intervention-test
  "Coverage tests for player interventions (warp wells, repulsors, heat source/sink)."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.tick :as tick]
   [domain.intervention :as iv]
   [domain.player :as player]
   [shape.spatial :as sp]))

(defn- world-with-body
  "Empty world with one body at `pos` and the given mass."
  [pos mass]
  (let [[w e] (ecs/spawn (ecs/empty-world))]
    [(-> w
         (ecs/put-component e c/position (apply sp/vec3 pos))
         (ecs/put-component e c/mass mass))
     e]))

(defn- world-with-observer
  "World with a singleton observer carrying `agency` quanta."
  [agency]
  (let [[w e] (player/spawn-observer (ecs/empty-world) (sp/vec3 0 0 0))
        obs (ecs/get-component w e c/observer)]
    (ecs/put-component w e c/observer (assoc obs :agency agency))))

(deftest cost-of-known-kinds
  (is (= 15.0 (iv/cost-of :warp/well)))
  (is (= 15.0 (iv/cost-of :warp/repulsor)))
  (is (= 15.0 (iv/cost-of :unknown))))

(deftest make-intervention-constructs-warp
  (let [iv (iv/make-intervention :warp/well [1.0 2.0 3.0] 5 {})]
    (is (= :warp/well (:kind iv)))
    (is (= [1.0 2.0 3.0] (:position iv)))
    (is (= 5 (:born-tick iv)))
    (is (= iv/default-radius (:radius iv)))
    (is (= 1.0 (:strength iv)))))

(deftest make-intervention-constructs-heat-source
  (let [iv (iv/make-intervention :heat/source [0 0 0] 0 {})]
    (is (= iv/heat-target-hot (:target-temp iv)))))

(deftest make-intervention-constructs-heat-sink
  (let [iv (iv/make-intervention :heat/sink [0 0 0] 0 {})]
    (is (= iv/heat-target-cold (:target-temp iv)))))

(deftest decay-fraction-fades-linearly
  (let [iv (iv/make-intervention :warp/well [0 0 0] 0 {})]
    (is (= 1.0 (iv/decay-fraction iv 0)))
    (is (< (Math/abs (- (iv/decay-fraction iv (/ iv/default-ttl 2)) 0.5)) 1e-9))
    (is (zero? (iv/decay-fraction iv iv/default-ttl)))
    (is (zero? (iv/decay-fraction iv (+ iv/default-ttl 10))))))

(def ^:private warp-ctx
  "Influence context for direct warp-accel-on calls."
  {:ref-mass 4.0e30 :well-mass-factor iv/default-well-mass-factor})

(deftest warp-accel-on-well-pulls-toward-center
  (let [iv (iv/make-intervention :warp/well [0 0 0] 0 {:radius 10})
        a (iv/warp-accel-on iv [5.0 0 0] 0 warp-ctx)]
    (is (some? a))
    (is (neg? (first a)))
    (is (< (Math/abs (- (last a) 0.0)) 1e-12))))

(deftest warp-accel-on-repulsor-pushes-away
  (let [iv (iv/make-intervention :warp/repulsor [0 0 0] 0 {:radius 10})
        a (iv/warp-accel-on iv [5.0 0 0] 0 warp-ctx)]
    (is (some? a))
    (is (pos? (first a)))))

(deftest warp-accel-on-nil-outside-reach
  (let [iv (iv/make-intervention :warp/well [0 0 0] 0 {:radius 10})]
    ;; reach = halo-reach-factor (3) × radius
    (is (some? (iv/warp-accel-on iv [29.0 0 0] 0 warp-ctx)))
    (is (nil? (iv/warp-accel-on iv [31.0 0 0] 0 warp-ctx)))))

(deftest warp-accel-is-a-diffuse-halo-not-a-point-kick
  (let [iv (iv/make-intervention :warp/well [0 0 0] 0 {:radius 10})]
    (testing "the pull VANISHES toward the centre instead of peaking there"
      (is (< (sp/len (iv/warp-accel-on iv [0.01 0 0] 0 warp-ctx))
             (sp/len (iv/warp-accel-on iv [7.0 0 0] 0 warp-ctx)))))
    (testing "and decays with the intervention (ttl 600 ⇒ half strength at 300)"
      (is (< (sp/len (iv/warp-accel-on iv [7.0 0 0] 300 warp-ctx))
             (sp/len (iv/warp-accel-on iv [7.0 0 0] 0 warp-ctx)))))))

(deftest warp-system-caps-per-tick-dv
  (let [[w e] (world-with-body [5.0 0 0] 1.0)
        dt 1.0e12
        w (-> w
              (assoc :genesis/interventions [(iv/make-intervention :warp/well [0 0 0] 0 {:radius 10})]
                     :sim/dt dt))
        ws ((:run (iv/warp-acceleration-system)) w)
        a  (get-in ws [c/accel-warp e])
        dv-cap (:dv-cap (player/influence-reference w))]
    (is (some? a))
    (is (<= (* (sp/len a) dt) (* dv-cap 1.0000001))
        "summed warp Δv per tick stays under the influence cap")))

(deftest warp-acceleration-system-emits-accel-warp
  (let [[w e] (world-with-body [5.0 0 0] 1.0)
        w (-> w
              (assoc :genesis/interventions [(iv/make-intervention :warp/well [0 0 0] 0 {:radius 10})
                                             (iv/make-intervention :warp/repulsor [10 0 0] 0 {:radius 10})]
                     :sim/dt 1.0))
        sys (iv/warp-acceleration-system)
        ws ((:run sys) w)]
    (is (contains? ws c/accel-warp))
    (is (some? (get-in ws [c/accel-warp e])))))

(deftest warp-acceleration-system-clears-when-empty
  (let [[w e] (world-with-body [5.0 0 0] 1.0)
        sys (iv/warp-acceleration-system)
        ws ((:run sys) w)]
    (is (= {} (get ws c/accel-warp)))))

(deftest expire-interventions-removes-faded
  (let [w {:tick 1000 :genesis/interventions [(iv/make-intervention :warp/well [0 0 0] 0 {:ttl 500})
                                              (iv/make-intervention :warp/well [0 0 0] 900 {:ttl 500})]}
        w' (iv/expire-interventions w)]
    (is (= 1 (count (:genesis/interventions w'))))
    (is (= 900 (:born-tick (first (:genesis/interventions w')))))))

(deftest place-spends-agency-and-adds-intervention
  (let [w (world-with-observer 20.0)
        w' (iv/place w :warp/well [1.0 0 0])]
    (is (= 1 (count (:genesis/interventions w'))))
    (is (= :warp/well (:kind (first (:genesis/interventions w')))))
    (is (< (Math/abs (- (:agency (player/get-observer w')) 5.0)) 1e-9))))

(deftest place-uses-the-world-well-knobs
  (let [w (-> (world-with-observer 20.0)
              (assoc :genesis/well-radius 7.0e15 :genesis/well-ttl 1200))
        iv (first (:genesis/interventions (iv/place w :warp/well [1.0 0 0])))]
    (is (= 7.0e15 (:radius iv)))
    (is (= 1200.0 (:ttl iv)))
    (testing "explicit opts still win over the knobs"
      (let [iv (first (:genesis/interventions
                       (iv/place w :warp/well [1.0 0 0] {:radius 9.0e14})))]
        (is (= 9.0e14 (:radius iv)))))))

(deftest place-is-noop-without-funds
  (let [w (world-with-observer 5.0)
        w' (iv/place w :warp/well [1.0 0 0])]
    (is (zero? (count (:genesis/interventions w'))))
    (is (= 5.0 (:agency (player/get-observer w'))))))

(deftest place-is-noop-without-observer
  (let [w (iv/place (ecs/empty-world) :warp/well [1.0 0 0])]
    (is (zero? (count (:genesis/interventions w))))))

(deftest thermal-step-eases-toward-target
  (let [iv (iv/make-intervention :heat/source [0 0 0] 0 {})
        t' (iv/thermal-step [iv] [0 0 0] 100.0 0)]
    (is (> t' 100.0))
    (is (<= t' iv/max-temp))))

(deftest thermal-step-clamps-to-max
  (let [iv (iv/make-intervention :heat/source [0 0 0] 0 {:target-temp 1.0e8 :strength 100.0})
        t' (iv/thermal-step [iv] [0 0 0] 100.0 0)]
    (is (= iv/max-temp t'))))

(deftest thermal-contributions-lists-in-range-eases
  (let [iv (iv/make-intervention :heat/source [0 0 0] 10 {})
        cs (iv/thermal-contributions [iv] [5.0 0 0] 0)]
    (is (= 1 (count cs)))
    (is (> (:ease (first cs)) 0.0))
    (is (= (double iv/heat-target-hot) (:target-temp (first cs))))))

(deftest thermal-contributions-empty-when-out-of-range
  (let [iv (iv/make-intervention :heat/source [0 0 0] 0 {:radius 10})
        cs (iv/thermal-contributions [iv] [20.0 0 0] 0)]
    (is (empty? cs))))

(deftest apply-thermal-contributions-eases-temperature
  (let [cs [{:target-temp 1000.0 :ease 0.1}]
        t' (iv/apply-thermal-contributions 100.0 cs)]
    (is (> t' 100.0))
    (is (< t' 1000.0))))

(deftest thermal-intervention-system-emits-heat-intervention
  (let [[w e] (world-with-body [5.0 0 0] 1.0)
        w (-> w
              (ecs/put-component e c/matter-state :nebula)
              (ecs/put-component e c/temperature 100.0)
              (assoc :genesis/interventions [(iv/make-intervention :heat/source [0 0 0] 0 {})]))
        sys (iv/thermal-intervention-system)
        ws ((:run sys) w)]
    (is (contains? ws c/heat-intervention))
    (is (seq (get-in ws [c/heat-intervention e])))))

(deftest thermal-intervention-system-skips-non-thermal-states
  (let [[w e] (world-with-body [5.0 0 0] 1.0)
        w (-> w
              (ecs/put-component e c/matter-state :star)
              (ecs/put-component e c/temperature 100.0)
              (assoc :genesis/interventions [(iv/make-intervention :heat/source [0 0 0] 0 {})]))
        sys (iv/thermal-intervention-system)
        ws ((:run sys) w)]
    (is (not (contains? (get ws c/heat-intervention) e)))))
