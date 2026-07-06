(ns domain.observer-influence-test
  "μ for the observer's dark-halo influence: observation writes an acceleration
   (sole writer of :component/accel.observer) that is a Plummer halo centred on
   the focus — a large, diffuse body of mass scaled by coherence — so it binds
   and gathers matter instead of kicking it (the old bounded-Δv nudge was 25×
   the cloud's escape speed per tick and ejected whatever it touched). The
   per-tick Δv stays under the influence cap regardless of dt."
  (:require
   [clojure.test :refer [deftest is testing]]
   [law.stellar          :as law]
   [domain.ecs.core      :as ecs]
   [domain.ecs.components  :as c]
   [domain.ecs.registry   :as reg]
   [domain.ecs.tick       :as tick]
   [domain.player         :as player]
   [shape.spatial         :as sp]))

(def ^:private focus (sp/vec3 0.0 0.0 0.0))

;; Reach = halo-reach-factor × focus-radius = 3e15 m for this focus radius.
(def ^:private focus-radius 1.0e15)

(defn- world-with-body
  "Observer focused at the origin (scale radius 1e15 ⇒ reach 3e15) plus one
   body at `body-pos`."
  [body-pos]
  (let [[w _] (player/spawn-observer (ecs/empty-world) focus)
        w     (player/update-observer w #(player/set-focus % focus focus-radius 0.5))
        [w b] (ecs/spawn w)
        w     (ecs/put-components w b {c/position body-pos c/mass 1.0e28})]
    [(assoc w :sim/dt 1.0e12 :tick 1 :genesis/complexity 10) b]))

(defn- run
  "Run the fan-out accel.observer emitter and fold its write-set."
  [w]
  (tick/apply-write-set w ((:run (player/observer-acceleration-system)) w)))

(def ^:private halo-ctx
  "Influence context at the seeded-cloud defaults."
  (assoc (player/influence-reference {})
         :mass-factor player/default-halo-mass-factor))

;; --- single-writer ----------------------------------------------------------

(deftest accel-observer-not-a-fan-out-conflict
  (testing "observer influence introduces no fan-out conflict"
    (is (empty? (reg/write-conflicts reg/systems))))
  (testing "the integrator reads accel.observer so the halo pull is integrated"
    (is (contains? (->> reg/systems (filter #(= :integrator (:id %))) first :reads)
                   c/accel-observer))))

;; --- halo shape ---------------------------------------------------------------

(deftest halo-mass-scales-with-coherence
  (let [obs  (player/create-observer focus)
        m    (player/halo-mass obs 2.0 4.0e30)
        obs' (assoc obs :coherence (* 0.5 (:coherence obs)))]
    (is (pos? m))
    (testing "half the coherence ⇒ half the gravitating mass"
      (is (< (Math/abs (- (player/halo-mass obs' 2.0 4.0e30) (* 0.5 m)))
             (* 1e-12 m))))
    (testing "zero coherence ⇒ no halo at all"
      (is (zero? (player/halo-mass (assoc obs :coherence 0.0) 2.0 4.0e30))))))

(deftest focused-body-is-pulled-toward-focus
  (let [[w b] (world-with-body (sp/vec3 1.0e14 0.0 0.0))   ;; well inside reach
        a     (ecs/get-component (run w) b c/accel-observer)]
    (is (some? a) "a body within reach receives observer accel")
    (testing "and the pull points toward the focus (−x here)"
      (is (neg? (first a)))
      (is (< (Math/abs (double (second a))) 1.0e-30))
      (is (< (Math/abs (double (nth a 2))) 1.0e-30)))))

(deftest pull-is-the-plummer-field
  (testing "inside the cap, accel magnitude IS plummer-acceleration(halo-mass)"
    (let [obs  (-> (player/create-observer focus)
                   (player/set-focus focus focus-radius 0.5))
          pos  (sp/vec3 4.0e14 0.0 0.0)
          dt   1.0                                  ;; tiny dt ⇒ cap can't bind
          a    (player/observer-acceleration obs pos dt halo-ctx)
          M    (player/halo-mass obs (:mass-factor halo-ctx) (:ref-mass halo-ctx))
          g    (law/plummer-acceleration M focus-radius 4.0e14)]
      (is (< (Math/abs (- (sp/len a) g)) (* 1e-9 g))))))

(deftest zero-force-at-the-exact-centre
  (let [[w b] (world-with-body focus)]                     ;; body AT the focus
    (is (nil? (ecs/get-component (run w) b c/accel-observer))
        "a diffuse halo exerts no force at its own centre")))

(deftest body-outside-reach-is-untouched
  (let [[w b] (world-with-body (sp/vec3 1.0e16 0.0 0.0))]  ;; beyond 3×1e15
    (is (nil? (ecs/get-component (run w) b c/accel-observer)))))

(deftest influence-clears-when-focus-leaves
  (let [[w b] (world-with-body (sp/vec3 1.0e14 0.0 0.0))
        w1    (run w)
        _     (is (some? (ecs/get-component w1 b c/accel-observer)))
        w2    (run (player/update-observer w1
                                           #(player/set-focus % (sp/vec3 1.0e17 0.0 0.0)
                                                              focus-radius 0.5)))]
    (is (nil? (ecs/get-component w2 b c/accel-observer))
        "stale influence is cleared once the body leaves the reach")))

(deftest disabled-when-halo-mass-factor-zero
  (let [[w b] (world-with-body (sp/vec3 1.0e14 0.0 0.0))
        w     (assoc w :genesis/observer-halo-mass-factor 0.0)]
    (is (nil? (ecs/get-component (run w) b c/accel-observer)))))

;; --- the dt hazard ----------------------------------------------------------

(deftest per-tick-dv-is-capped-for-any-dt
  (testing "Δv = |a|·dt never exceeds the influence cap, even over absurd steps"
    (let [obs (-> (player/create-observer focus)
                  (player/set-focus focus focus-radius 1.0)
                  (assoc :coherence 1.0))                 ;; strongest halo
          pos (sp/vec3 7.0e14 0.0 0.0)]                    ;; near peak pull
      (doseq [dt [1.0e6 1.0e12 1.0e18]]
        (let [a  (player/observer-acceleration obs pos dt halo-ctx)
              dv (* (sp/len a) dt)]
          (is (<= dv (* (:dv-cap halo-ctx) 1.0000001))
              (str "Δv " dv " within cap at dt " dt)))))))

(deftest cap-defaults-to-the-cloud-virial-speed
  (let [{:keys [dv-cap ref-mass]} (player/influence-reference {})]
    (is (< (Math/abs (- dv-cap (law/virial-speed 4.0e30 2.0e16))) 1e-9))
    (is (= 4.0e30 ref-mass))
    (testing "and follows the knob"
      (is (< (Math/abs (- (:dv-cap (player/influence-reference
                                    {:genesis/influence-dv-cap 2.0}))
                          (* 2.0 dv-cap)))
             1e-9)))))
