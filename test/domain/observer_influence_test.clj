(ns domain.observer-influence-test
  "μ for the observer's pull-toward-focus influence: observation writes a bounded
   acceleration (sole writer of :component/accel.observer) that pulls bodies
   inside the collapse radius toward the focus — 'reality condenses where you
   look' — and the nudge is dt-robust (cannot blow up over a Myr-scale step).
   See docs/specs/phase0-player-focus-and-dual-representation.md."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core      :as ecs]
   [domain.ecs.components  :as c]
   [domain.ecs.registry   :as reg]
   [domain.ecs.tick       :as tick]
   [domain.player         :as player]
   [shape.spatial         :as sp]))

(def ^:private focus (sp/vec3 0.0 0.0 0.0))

(defn- world-with-body
  "Observer focused at the origin (radius 1e15, coherence 0.8 ⇒ collapse radius
   8e14) plus one body at `body-pos`."
  [body-pos]
  (let [[w _] (player/spawn-observer (ecs/empty-world) focus)
        w     (player/update-observer w #(player/set-focus % focus 1.0e15 0.5))
        [w b] (ecs/spawn w)
        w     (ecs/put-components w b {c/position body-pos c/mass 1.0e28})]
    [(assoc w :sim/dt 1.0e12 :tick 1 :genesis/complexity 10) b]))

(defn- run
  "Run the fan-out accel.observer emitter and fold its write-set — the emitter is
   now the sole writer of accel.observer (observer-system only updates the map)."
  [w]
  (tick/apply-write-set w ((:run (player/observer-acceleration-system)) w)))

;; --- single-writer ----------------------------------------------------------

(deftest accel-observer-not-a-fan-out-conflict
  (testing "observer influence is barrier-staged; it introduces no fan-out conflict"
    (is (empty? (reg/write-conflicts reg/systems))))
  (testing "the integrator reads accel.observer so the nudge is integrated"
    (is (contains? (->> reg/systems (filter #(= :integrator (:id %))) first :reads)
                   c/accel-observer))))

;; --- pulling behaviour ------------------------------------------------------

(deftest focused-body-is-pulled-toward-focus
  (let [[w b] (world-with-body (sp/vec3 1.0e14 0.0 0.0))   ;; inside 8e14
        a     (ecs/get-component (run w) b c/accel-observer)]
    (is (some? a) "a body inside the collapse radius receives observer accel")
    (testing "and the pull points toward the focus (−x here)"
      (is (neg? (first a)))
      (is (< (Math/abs (double (second a))) 1.0e-30))
      (is (< (Math/abs (double (nth a 2))) 1.0e-30)))))

(deftest body-outside-collapse-radius-is-untouched
  (let [[w b] (world-with-body (sp/vec3 1.0e16 0.0 0.0))]  ;; far outside 8e14
    (is (nil? (ecs/get-component (run w) b c/accel-observer)))))

(deftest influence-clears-when-focus-leaves
  (let [[w b] (world-with-body (sp/vec3 1.0e14 0.0 0.0))
        w1    (run w)
        _     (is (some? (ecs/get-component w1 b c/accel-observer)))
        ;; move focus far from the body, run again
        w2    (run (player/update-observer w1
                                           #(player/set-focus % (sp/vec3 1.0e17 0.0 0.0) 1.0e15 0.5)))]
    (is (nil? (ecs/get-component w2 b c/accel-observer))
        "stale influence is cleared once the body leaves the zone")))

(deftest disabled-when-influence-speed-zero
  (let [[w b] (world-with-body (sp/vec3 1.0e14 0.0 0.0))
        w     (assoc w :genesis/observer-influence-speed 0.0)]
    (is (nil? (ecs/get-component (run w) b c/accel-observer)))))

;; --- the dt hazard ----------------------------------------------------------

(deftest nudge-is-dt-robust
  (testing "Δv = accel·dt is bounded and independent of dt (no Myr-step blow-up)"
    (let [obs (-> (player/create-observer focus)
                  (player/set-focus focus 1.0e15 0.5))
          pos (sp/vec3 1.0e14 0.0 0.0)
          a-small (player/observer-acceleration obs pos 1.0e6  1.0e3)
          a-huge  (player/observer-acceleration obs pos 1.0e18 1.0e3)
          dv-small (sp/v* a-small 1.0e6)
          dv-huge  (sp/v* a-huge  1.0e18)]
      (is (< (sp/len (sp/v- dv-small dv-huge)) 1.0e-6)
          "same Δv whether dt is 1e6 s or 1e18 s")
      (is (<= (sp/len dv-huge) (* 0.1 1.0e3 1.0000001))
          "Δv bounded by influence-strength(≤0.1)·ref-speed"))))
