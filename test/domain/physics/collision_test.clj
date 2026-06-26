(ns domain.physics.collision-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [domain.ecs.core                  :as ecs]
    [domain.ecs.components            :as c]
    [domain.ecs.event                 :as event]
    [domain.physics.collision         :as col]
    [domain.physics.collision-response :as response]))

(defn- two-body-world
  "Spawn two collidable bodies, optionally overlapping."
  [overlap?]
  (let [[w e1] (ecs/spawn (-> (ecs/empty-world)
                               event/with-ledger
                               event/with-handlers))
        [w e2] (ecs/spawn w)
        w (ecs/put-components w e1 {c/position [0.0 0.0 0.0]
                                    c/velocity  [1.0 0.0 0.0]
                                    c/mass      1.0
                                    c/radius    1.0
                                    c/body-kind :body/test})
        w (ecs/put-components w e2 {c/position (if overlap?
                                                 [1.5 0.0 0.0]
                                                 [5.0 0.0 0.0])
                                    c/velocity  [-1.0 0.0 0.0]
                                    c/mass      1.0
                                    c/radius    1.0
                                    c/body-kind :body/test})]
    [w e1 e2]))

(deftest collision-detected-when-overlapping
  (let [[w _ _] (two-body-world true)
        w'      (col/collision-detection-system w)]
    (is (seq (event/events-of-kind w' :event/collision)))))

(deftest no-collision-when-separated
  (let [[w _ _] (two-body-world false)
        w'      (col/collision-detection-system w)]
    (is (empty? (event/events-of-kind w' :event/collision)))))

(deftest elastic-bounce-conserves-momentum
  (let [[w e1 e2] (two-body-world true)
        w         (event/register-handler w :event/collision
                                          response/elastic-bounce-handler)
        w'        (col/collision-detection-system w)
        momentum  (fn [eid]
                    (let [v (ecs/get-component w' eid c/velocity)
                          m (ecs/get-component w' eid c/mass)]
                      (map #(* % m) v)))
        p1-after  (momentum e1)
        p2-after  (momentum e2)
        p-after   (mapv + p1-after p2-after)]
    (is (every? #(< (Math/abs %) 1e-9) p-after)
        (str "Momentum not conserved: " p-after))))

(deftest inelastic-merge-despawns-smaller
  (let [[w e1] (ecs/spawn (-> (ecs/empty-world)
                               event/with-ledger
                               event/with-handlers))
        [w e2] (ecs/spawn w)
        w (ecs/put-components w e1 {c/position [0.0 0.0 0.0]
                                    c/velocity  [0.0 0.0 0.0]
                                    c/mass      10.0
                                    c/radius    2.0
                                    c/body-kind :body/test})
        w (ecs/put-components w e2 {c/position [2.5 0.0 0.0]
                                    c/velocity  [0.0 0.0 0.0]
                                    c/mass      1.0
                                    c/radius    0.5
                                    c/body-kind :body/test})
        w (event/register-handler w :event/collision
                                  response/inelastic-merge-handler)
        w' (col/collision-detection-system w)]
    (is (not (contains? (:entities w') e2))
        "Smaller body should be despawned")
    (is (= 11.0 (ecs/get-component w' e1 c/mass))
        "Larger body should absorb mass")))

(deftest swept-sphere-catches-tunneling
  (let [[w e1] (ecs/spawn (-> (ecs/empty-world)
                               event/with-ledger
                               event/with-handlers
                               (assoc :sim/dt 1.0)))
        [w e2] (ecs/spawn w)
        ;; Bodies are 5 units apart and each moves 3 units toward the other in
        ;; one step, so their surfaces cross during the step even though they do
        ;; not overlap at either endpoint.
        w (ecs/put-components w e1 {c/position [0.0 0.0 0.0]
                                    c/velocity  [3.0 0.0 0.0]
                                    c/mass      1.0
                                    c/radius    1.0
                                    c/body-kind :body/test})
        w (ecs/put-components w e2 {c/position [5.0 0.0 0.0]
                                    c/velocity  [-3.0 0.0 0.0]
                                    c/mass      1.0
                                    c/radius    1.0
                                    c/body-kind :body/test})
        w' (col/collision-detection-system w)]
    (is (seq (event/events-of-kind w' :event/collision))
        "fast-moving bodies that cross paths during the step must still collide")))

(deftest ledger-appends-across-ticks
  (let [[w e1 e2] (two-body-world true)
        w  (event/register-handler w :event/collision
                                   response/elastic-bounce-handler)
        systems [col/collision-detection-system]
        w' (ecs/tick w systems)
        w' (ecs/tick w' systems)]
    (is (>= (count (get-in w' [:ledger :events])) 2))))
