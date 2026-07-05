(ns domain.physics.collision-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core                  :as ecs]
   [domain.ecs.components            :as c]
   [domain.ecs.event                 :as event]
   [domain.physics.collision         :as col]
   [domain.physics.collision-response :as response]
   [domain.spatial.index             :as spatial]))

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
                                    c/accretion-radius 1.0
                                    c/matter-state :debris
                                    c/body-kind :body/test})
        w (ecs/put-components w e2 {c/position (if overlap?
                                                 [1.5 0.0 0.0]
                                                 [5.0 0.0 0.0])
                                    c/velocity  [-1.0 0.0 0.0]
                                    c/mass      1.0
                                    c/radius    1.0
                                    c/accretion-radius 1.0
                                    c/matter-state :debris
                                    c/body-kind :body/test})
        w (spatial/spatial-index w)]
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
                                    c/accretion-radius 2.0
                                    c/matter-state :debris
                                    c/body-kind :body/test})
        w (ecs/put-components w e2 {c/position [2.5 0.0 0.0]
                                    c/velocity  [0.0 0.0 0.0]
                                    c/mass      1.0
                                    c/radius    0.5
                                    c/accretion-radius 0.5
                                    c/matter-state :debris
                                    c/body-kind :body/test})
        w (event/register-handler w :event/collision
                                  response/inelastic-merge-handler)
        w (spatial/spatial-index w)
        w' (col/collision-detection-system w)]
    (is (not (contains? (:entities w') e2))
        "Smaller body should be despawned")
    (is (= 11.0 (ecs/get-component w' e1 c/mass))
        "Larger body should absorb mass")))

(deftest literal-overlap-ignores-non-touching-fast-bodies
  ;; Detection is literal overlap, NOT swept prediction: two fast bodies that are
  ;; not touching this instant do not collide, even if their straight-line paths
  ;; would cross during the step. This is deliberate — linear extrapolation over
  ;; the astronomical Phase 0 timestep merges gas from unrelated regions of a
  ;; collapsing (converging) cloud that never actually touches. A high-speed
  ;; fly-through is not an accretion event.
  (let [[w e1] (ecs/spawn (-> (ecs/empty-world)
                              event/with-ledger
                              event/with-handlers
                              (assoc :sim/dt 1.0)))
        [w e2] (ecs/spawn w)
        w (ecs/put-components w e1 {c/position [0.0 0.0 0.0]
                                    c/velocity  [3.0 0.0 0.0]
                                    c/mass      1.0
                                    c/radius    1.0
                                    c/accretion-radius 1.0
                                    c/matter-state :debris
                                    c/body-kind :body/test})
        w (ecs/put-components w e2 {c/position [5.0 0.0 0.0]
                                    c/velocity  [-3.0 0.0 0.0]
                                    c/mass      1.0
                                    c/radius    1.0
                                    c/accretion-radius 1.0
                                    c/matter-state :debris
                                    c/body-kind :body/test})
        w (spatial/spatial-index w)
        w' (col/collision-detection-system w)]
    (is (empty? (event/events-of-kind w' :event/collision))
        "bodies that are not overlapping right now do not merge across a gap")))

(deftest ledger-appends-across-ticks
  (let [[w _e1 _e2] (two-body-world true)
        w  (event/register-handler w :event/collision
                                   response/elastic-bounce-handler)
        systems [col/collision-detection-system]
        w' (ecs/tick w systems)
        w' (ecs/tick w' systems)]
    (is (>= (count (get-in w' [:ledger :events])) 2))))

(deftest gas-particles-do-not-collide
  (testing ":nebula sample particles never produce collision events, even when overlapping"
    (let [[w e1] (ecs/spawn (-> (ecs/empty-world)
                                event/with-ledger
                                event/with-handlers))
          [w e2] (ecs/spawn w)
          w (ecs/put-components w e1 {c/position [0.0 0.0 0.0]
                                      c/velocity  [0.0 0.0 0.0]
                                      c/mass      1e28
                                      c/radius    1e12
                                      c/matter-state :nebula})
          w (ecs/put-components w e2 {c/position [1.0e12 0.0 0.0]
                                      c/velocity  [0.0 0.0 0.0]
                                      c/mass      1e28
                                      c/radius    1e12
                                      c/matter-state :nebula})
          w (spatial/spatial-index w)
          w' (col/collision-detection-system w)]
      (is (empty? (event/events-of-kind w' :event/collision))
          "overlapping gas particles must not collide"))))

(deftest resolved-bodies-still-collide
  (testing "resolved bodies (debris/planet/protostar/star) still produce collision events"
    (let [[w e1] (ecs/spawn (-> (ecs/empty-world)
                                event/with-ledger
                                event/with-handlers))
          [w e2] (ecs/spawn w)
          w (ecs/put-components w e1 {c/position [0.0 0.0 0.0]
                                      c/velocity  [0.0 0.0 0.0]
                                      c/mass      1e28
                                      c/radius    1e12
                                      c/accretion-radius 1e12
                                      c/matter-state :debris})
          w (ecs/put-components w e2 {c/position [1.0e12 0.0 0.0]
                                      c/velocity  [0.0 0.0 0.0]
                                      c/mass      1e28
                                      c/radius    1e12
                                      c/accretion-radius 1e12
                                      c/matter-state :debris})
          w (spatial/spatial-index w)
          w' (col/collision-detection-system w)]
      (is (seq (event/events-of-kind w' :event/collision))
          "overlapping resolved bodies must still collide"))))
