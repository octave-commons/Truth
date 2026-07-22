(ns infra.camera-test
  "Tests for the orbital camera and world-tracking functions in infra.camera.
   These are pure geometry/ECS fns; no OpenGL is exercised."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.player :as player]
   [domain.stellar.seeder :as seeder]
   [infra.camera :as cam]
   [infra.camera.navigation.tether :as tether]
   [law.narrowing :as law]
   [shape.spatial :as sp]))

(deftest test-update-camera-track-largest-cluster
  (testing "Camera target moves toward the largest mass cluster"
    (let [[w _] (seeder/spawn-clump (ecs/empty-world)
                                    {:position [3e15 0.0 0.0] :mass 2e30 :radius 1e14
                                     :matter-state :star})
          cam0 (cam/make-camera)
          cam1 (cam/update-camera-for-world cam0 w (assoc (cam/default-camera-settings)
                                                          :mode :track-largest-cluster))]
      (is (not= (:target cam0) (:target cam1)))
      (is (> (first (:target cam1)) 0.0) "target shifts toward positive x")
      (is (pos? (:distance cam1)) "distance remains positive"))))

(deftest test-largest-mass-cluster
  (testing "Finds the densest cluster and ignores isolated bodies"
    (let [bodies [[[0.0 0.0 0.0] 100.0]
                  [[1.0 0.0 0.0] 100.0]
                  [[2.0 0.0 0.0] 100.0]
                  [[100.0 0.0 0.0] 1.0]]
          cluster (cam/largest-mass-cluster bodies 2.0)]
      (is (> (:mass cluster) 250.0))
      (is (< (:radius cluster) 5.0))
      (is (every? #(< -0.1 % 3.0) (:center cluster)))))
  (testing "Empty body list returns zeroed bounds"
    (let [cluster (cam/largest-mass-cluster [] 2.0)]
      (is (= 0.0 (:mass cluster) (:radius cluster)))
      (is (= [0.0 0.0 0.0] (:center cluster))))))

(deftest test-follow-selection-tracks-exactly-when-close
  (testing "At planetary zoom the camera target snaps to and tracks the selected body"
    (let [[w body-eid] (seeder/spawn-clump (ecs/empty-world)
                                           {:position [1e15 0.0 0.0]
                                            :mass 1e24
                                            :radius 6e8
                                            :matter-state :planet})
          cam0 (assoc (cam/make-camera 10.0) :target [0.0 0.0 0.0])
          settings (assoc (cam/default-camera-settings)
                          :mode :follow-selection
                          :follow-eid body-eid)
          cam1 (cam/update-camera-for-world cam0 w settings)
          w' (ecs/put-component w body-eid c/position [1.01e15 0.0 0.0])
          cam2 (cam/update-camera-for-world cam1 w' settings)]
      (is (= [1.0 0.0 0.0] (:target cam1))
          "camera snaps to selected body when already close")
      (is (= [1.01 0.0 0.0] (:target cam2))
          "camera tracks body exactly after it moves while close"))))

(deftest test-follow-selection-lerps-when-far
  (testing "Far from the selected body the camera still smoothly approaches"
    (let [[w body-eid] (seeder/spawn-clump (ecs/empty-world)
                                           {:position [1e18 0.0 0.0]
                                            :mass 1e24
                                            :radius 6e14
                                            :matter-state :planet})
          cam0 (assoc (cam/make-camera 2000.0) :target [0.0 0.0 0.0])
          settings (assoc (cam/default-camera-settings)
                          :mode :follow-selection
                          :follow-eid body-eid)
          cam1 (cam/update-camera-for-world cam0 w settings)]
      ;; Body is at 1000 ru; camera orbit distance is 2000 ru, so it is well
      ;; outside the close-tracking snap radius. The target should not jump
      ;; all the way to the body on the first frame.
      (is (not= [1000.0 0.0 0.0] (:target cam1)))
      (is (< 0.0 (first (:target cam1)) 1000.0)
          "camera lerps toward a far body"))))

(deftest test-fit-all-bounds
  (testing "Bounding sphere contains the requested percentile of bodies"
    (let [bodies (mapv (fn [i] [[(double i) 0.0 0.0] 1.0]) (range 100))
          bounds (cam/fit-all-bounds bodies 0.90)]
      (is (>= (:radius bounds) 40.0))
      (is (<= (:radius bounds) 50.0))
      (is (= [49.5 0.0 0.0] (:center bounds)))))
  (testing "Empty body list returns zeroed bounds"
    (let [bounds (cam/fit-all-bounds [] 0.90)]
      (is (= 0.0 (:radius bounds)))
      (is (= [0.0 0.0 0.0] (:center bounds))))))

(deftest test-distance-for-radius
  (testing "Distance scales with radius and margin, inversely with FOV"
    (let [d60 (cam/distance-for-radius 10.0 60.0 1.0)
          d90 (cam/distance-for-radius 10.0 90.0 1.0)
          d2x (cam/distance-for-radius 10.0 60.0 2.0)]
      (is (pos? d60))
      (is (< d90 d60) "wider FOV needs closer camera")
      (is (> d2x d60) "larger margin needs farther camera"))))

(deftest test-camera-settings-cycle
  (testing "Camera mode cycles through the four modes"
    (let [s0 (cam/default-camera-settings)
          s1 (cam/cycle-camera-mode s0)
          s2 (cam/cycle-camera-mode s1)
          s3 (cam/cycle-camera-mode s2)
          s4 (cam/cycle-camera-mode s3)]
      (is (= :manual (:mode s0)))
      (is (= :follow-selection (:mode s1)))
      (is (= :track-largest-cluster (:mode s2)))
      (is (= :fit-all (:mode s3)))
      (is (= :manual (:mode s4)))))
  (testing "Fit margin is clamped"
    (let [s (cam/default-camera-settings)]
      (is (>= (:fit-margin (cam/adjust-fit-margin s 0.1)) 1.0))
      (is (<= (:fit-margin (cam/adjust-fit-margin s 10.0)) 4.0)))))

(deftest test-perspective-look-at
  (testing "Perspective and look-at produce sane column-major matrices"
    (let [p (cam/perspective 60.0 1.0 0.1 100.0)
          v (cam/look-at [0.0 0.0 5.0] [0.0 0.0 0.0] [0.0 1.0 0.0])]
      (is (= 16 (count p)))
      (is (= 16 (count v)))
      ;; last column of a view matrix should be the translation/eye term
      (is (not-every? zero? (take 3 (drop 12 v)))))))

(deftest test-camera-forward
  (testing "Forward vector points from camera toward target"
    (let [c (cam/make-camera 10.0)
          f (cam/camera-forward c)]
      (is (< (abs (- (sp/len f) 1.0)) 1e-6) "forward is a unit vector")
      (is (pos? (reduce + (map * f f))) "forward is non-zero"))))

(deftest test-camera-move-basis
  (testing "Move basis: forward follows the full look direction, right stays level"
    (let [c (cam/make-camera 10.0)
          {:keys [forward right]} (cam/camera-move-basis c)
          f (cam/camera-forward c)]
      (is (< (abs (- (sp/len forward) 1.0)) 1e-6) "forward is a unit vector")
      (is (< (abs (- (sp/len right) 1.0)) 1e-6) "right is a unit vector")
      (is (< (abs (sp/dot forward right)) 1e-6) "right ⟂ forward")
      ;; forward is the FULL camera look direction, including the pitch (z) tilt.
      (is (> (sp/dot forward (cam/normalize f)) 0.999) "forward tracks camera look direction")
      (is (not (zero? (nth forward 2))) "pitched forward carries a vertical (z) component")
      ;; strafe stays level regardless of pitch.
      (is (zero? (nth right 2)) "right is horizontal (no z)"))))

(deftest test-flight-move
  (testing "W input moves the target along the camera's horizontal forward"
    (let [c (cam/make-camera 10.0)
          c' (cam/flight-move c {:forward 1.0 :right 0.0} 1.0 (cam/default-camera-settings))
          d  (sp/v- (:target c') (:target c))]
      (is (not= (:target c) (:target c')))
      (is (> (sp/dot (cam/normalize d) (cam/normalize (cam/camera-forward c))) 0.999)
          "forward flight follows the full camera look direction, pitch included")))
  (testing "No input leaves camera unchanged"
    (let [c (cam/make-camera 10.0)
          c' (cam/flight-move c {:forward 0.0 :right 0.0} 1.0 (cam/default-camera-settings))]
      (is (= (:target c) (:target c')))
      (is (= (:position c) (:position c')))))
  (testing "Strafe input moves target along the right vector"
    (let [c (cam/make-camera 10.0)
          c-fwd (cam/flight-move c {:forward 1.0 :right 0.0} 1.0 (cam/default-camera-settings))
          c-rgt (cam/flight-move c {:forward 0.0 :right 1.0} 1.0 (cam/default-camera-settings))]
      (is (not= (:target c-fwd) (:target c-rgt)) "forward and strafe move in different directions"))))

(deftest test-flight-move-scales-with-distance
  (testing "Larger orbit distance yields faster flight for the same speed setting"
    (let [settings (cam/default-camera-settings)
          c-near (cam/make-camera 10.0)
          c-far (cam/make-camera 100.0)
          n (cam/flight-move c-near {:forward 1.0 :right 0.0} 1.0 settings)
          f (cam/flight-move c-far {:forward 1.0 :right 0.0} 1.0 settings)]
      (is (> (sp/dist (:target f) (:target c-far))
             (sp/dist (:target n) (:target c-near)))))))

(deftest test-observer-move-velocity
  (testing "Observer velocity aligns with camera horizontal basis"
    (let [settings (cam/default-camera-settings)
          c (cam/make-camera 10.0)
          v-fwd (cam/observer-move-velocity c {:forward 1.0 :right 0.0} settings)
          v-rgt (cam/observer-move-velocity c {:forward 0.0 :right 1.0} settings)
          v-none (cam/observer-move-velocity c {:forward 0.0 :right 0.0} settings)
          {:keys [forward right]} (cam/camera-move-basis c)
          speed (:move-speed settings)]
      (is (= [0.0 0.0 0.0] v-none))
      (is (< (abs (- (first v-fwd) (* speed (first forward)))) 1.0)
          "forward velocity matches camera forward direction scaled by move speed")
      (is (< (abs (- (first v-rgt) (* speed (first right)))) 1.0)
          "strafe velocity matches camera right direction scaled by move speed")
      (is (not (zero? (nth v-fwd 2))) "forward velocity follows the pitched look direction (has z)")
      (is (zero? (nth v-rgt 2)) "strafe velocity stays horizontal (no vertical z)"))))

;; --- The binding tether (The First Narrowing, child C) ---------------------

(defn- world-with-bound-planet
  "A world with an observer and one planet at [1e16 0 0]; returns
   [world obs-eid world-eid]."
  []
  (let [[w obs-eid] (player/spawn-observer (ecs/empty-world) (sp/vec3 0.0 0.0 0.0))
        [w world-eid] (seeder/spawn-clump w {:position [1.0e16 0.0 0.0]
                                             :mass 1.0e24 :radius 6.0e8
                                             :matter-state :planet})]
    [w obs-eid world-eid]))

(deftest test-tether-strength-curve
  (testing "strength is binding / capture-threshold, clamped to [0,1]"
    (is (= 0.0 (tether/tether-strength 0.0)))
    (is (= 0.5 (tether/tether-strength (* 0.5 law/capture-threshold))))
    (is (= 1.0 (tether/tether-strength law/capture-threshold))
        "fully engaged exactly at the capture threshold")
    (is (= 1.0 (tether/tether-strength 1.0))
        "clamped past the threshold — binding can deepen to 1.0 without overdrive")))

(deftest test-tether-step-follows-binding-depth
  (testing "no binding leaves the camera untouched; binding eases the frame toward the world"
    (let [[w obs-eid world-eid] (world-with-bound-planet)
          cam0 (assoc (cam/make-camera 100.0) :target [0.0 0.0 0.0])
          cam-free (cam/tether-step cam0 w {:input-active? false})
          w-bound (ecs/put-component w obs-eid c/binding
                                     {world-eid (* 0.5 law/capture-threshold)})
          cam1 (cam/tether-step cam0 w-bound {:input-active? false})]
      (is (= cam0 cam-free) "no binding, no pull")
      (is (> (first (:target cam1)) 0.0) "target eases toward the bound world")
      (is (< (first (:target cam1)) 1.0)
          "one frame is a gentle lerp step, not a snap (world sits 10 ru out)")
      (is (< (:distance cam1) (:distance cam0)) "the frame tightens"))))

(deftest test-tether-player-override
  (testing "player input wins outright at any binding depth, even full capture"
    (let [[w obs-eid world-eid] (world-with-bound-planet)
          w-bound (ecs/put-component w obs-eid c/binding {world-eid 1.0})
          cam0 (assoc (cam/make-camera 100.0) :target [5.0 5.0 0.0])]
      (is (= cam0 (cam/tether-step cam0 w-bound {:input-active? true}))
          "while the player fights, the tether does not act"))))

(deftest test-tether-no-jump-at-capture
  (testing "the capture event is invisible to the tether: same binding, same step"
    (let [[w obs-eid world-eid] (world-with-bound-planet)
          w-bound (ecs/put-component w obs-eid c/binding {world-eid law/capture-threshold})
          w-committed (ecs/put-component w-bound world-eid c/commitment-state :committed)
          cam0 (assoc (cam/make-camera 100.0) :target [0.0 0.0 0.0])]
      (is (= (cam/tether-step cam0 w-bound {:input-active? false})
             (cam/tether-step cam0 w-committed {:input-active? false}))
          "commitment-state changes nothing — the tether reads binding only")))
  (testing "camera position is continuous across the capture tick"
    (let [[w obs-eid world-eid] (world-with-bound-planet)
          cam0 (assoc (cam/make-camera 100.0) :target [0.0 0.0 0.0])
          w-below (ecs/put-component w obs-eid c/binding
                                     {world-eid (- law/capture-threshold 0.01)})
          w-at (ecs/put-component w obs-eid c/binding {world-eid law/capture-threshold})
          cam-below (cam/tether-step cam0 w-below {:input-active? false})
          cam-at (cam/tether-step cam0 w-at {:input-active? false})
          disp-below (sp/dist (:target cam-below) (:target cam0))
          dist-step-below (abs (- (:distance cam-below) (:distance cam0)))
          disp-at (sp/dist (:target cam-at) (:target cam0))
          dist-step-at (abs (- (:distance cam-at) (:distance cam0)))]
      (is (pos? disp-below))
      (is (<= disp-at (* 1.05 disp-below))
          "the capture-tick step is the same size as the step before it")
      (is (<= dist-step-at (* 1.05 dist-step-below))
          "distance tightening is continuous across the capture tick"))))

(deftest test-tether-reengages-gently-after-a-fight
  (testing "after the player releases input far from the world, the first resumed step is small"
    (let [[w obs-eid world-eid] (world-with-bound-planet)
          w-bound (ecs/put-component w obs-eid c/binding {world-eid 1.0})
          cam-far (assoc (cam/make-camera 300.0) :target [-50.0 20.0 10.0])
          cam1 (cam/tether-step cam-far w-bound {:input-active? false})
          gap (sp/dist (:target cam-far) [10.0 0.0 0.0])]
      (is (<= (sp/dist (:target cam1) (:target cam-far)) (* 1.001 tether/tether-rate gap))
          "resumption moves at most the lerp fraction of the remaining gap — no lurch"))))
