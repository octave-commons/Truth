(ns infra.dev.window-test
  "Tests for the dev window's error-recovery helpers and render-loop helpers."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [domain.ecs.core :as ecs]
   [domain.ecs.event :as event]
   [domain.genesis :as genesis]
   [domain.player :as player]
   [infra.camera :as cam]
   [infra.render.units :as units]
   [infra.dev.window]
   [infra.dev.window.loop :as loop]))

(use-fixtures :each
  (fn [test-fn]
    (test-fn)
    ;; Clean up any dumps this test wrote.
    (let [dir (io/file "/tmp" "gates-of-truth" "dumps")]
      (when (.exists dir)
        (doseq [f (.listFiles dir)]
          (when (str/starts-with? (.getName f) "truth-error-")
            (.delete f)))))))

(deftest dump-error-artifacts-persists-world-and-ledger
  (testing "a world + its ledger are written to timestamped EDN files"
    (let [w     (-> (ecs/empty-world)
                    (assoc :tick 42)
                    (event/with-ledger)
                    (event/emit {:tick 42 :kind :test/thud}))
          dump  (@#'infra.dev.window.loop/dump-error-artifacts! w)]
      (is (.exists (io/file (:world-path dump))))
      (is (.exists (io/file (:ledger-path dump))))
      (is (.exists (io/file (:meta-path dump))))
      (is (= 42 (-> (slurp (:world-path dump)) read-string :tick)))
      (is (= :test/thud (-> (slurp (:ledger-path dump)) read-string :events first :kind)))
      (is (= 42 (-> (slurp (:meta-path dump)) read-string :tick))))))

(deftest sync-observer-focus-to-camera
  (testing "The spark's focus follows the camera target in tracking modes, never the mouse."
    (let [world (genesis/create-world {:gas-count 3})
          obs   (player/get-observer world)
          spark-pos (player/observer-position world)
          camera (cam/update-camera-for-world
                  (cam/make-camera) world
                  (assoc (cam/default-camera-settings) :mode :fit-all))
          ctx    (units/make-context camera {:width 1280 :height 720})
          target-world (units/render->world ctx (:target camera))]
      (testing "non-manual mode snaps the focus to the camera target — but NEVER the spark's position (a gravity-bound ECS column since card 4)"
        (let [world' (@#'infra.dev.window.loop/sync-observer-focus-to-camera
                      world camera ctx :fit-all)
              obs' (player/get-observer world')]
          (is (= target-world (:focus-position obs'))
              "focus is locked to the camera target in world metres")
          (is (= spark-pos (player/observer-position world'))
              "spark position is the c/position column, untouched by the camera")
          (is (= (:focus-radius obs) (:focus-radius obs'))
              "focus radius is preserved")
          (is (= (:focus-intensity obs) (:focus-intensity obs'))
              "focus intensity is preserved")))
      (testing "manual mode leaves the focus under player control"
        (let [world' (@#'infra.dev.window.loop/sync-observer-focus-to-camera
                      world camera ctx :manual)
              obs' (player/get-observer world')]
          (is (= (:focus-position obs) (:focus-position obs'))
              "focus is not overwritten in manual mode")))
      (testing "world without an observer is unchanged"
        (let [empty-world (ecs/empty-world)
              world' (@#'infra.dev.window.loop/sync-observer-focus-to-camera
                      empty-world camera ctx :fit-all)]
          (is (= empty-world world')))))))

(deftest flight-intents-drain-before-tick-publish
  (testing "manual-flight intents (thrust direction + focus-follow) enqueued
            through the IntentAtom are drained on the sim thread BEFORE the
            tick — the sim thread is the sole writer of the world and no
            intent is lost between the sim's deref and its publish
            (flight-no-jump-accel; the drift position teleport is gone)"
    (let [[w _]   (player/spawn-observer (ecs/empty-world) [0.0 0.0 0.0])
          world-atom (atom w)
          queue   (java.util.concurrent.ConcurrentLinkedQueue.)
          intents (loop/->IntentAtom queue world-atom)]
      (testing "the render thread's swap! enqueues; it never mutates the world"
        (swap! intents player/set-thrust [0.0 1.0 0.0])
        (swap! intents player/focus-follow [1.5e10 0.0 0.0])
        (is (nil? (:player/thrust @world-atom)))
        (is (= [0.0 0.0 0.0] (:focus-position (player/get-observer @world-atom)))))
      (testing "sim iteration 1: drain applies both intents, then publish"
        (let [w0 (@#'infra.dev.window.loop/drain-intents @world-atom queue)]
          ;; a mid-tick thrust release lands between drain and publish
          (swap! intents player/set-thrust nil)
          (reset! world-atom w0)
          (is (= [0.0 1.0 0.0] (:player/thrust @world-atom))
              "thrust direction recorded exactly once")
          (is (= [1.5e10 0.0 0.0] (:focus-position (player/get-observer @world-atom)))
              "focus rides the mote position plus the nudge offset")
          (is (= [0.0 0.0 0.0] (player/observer-position @world-atom))
              "NEITHER intent touched the spark's c/position — the integrator owns motion")))
      (testing "sim iteration 2: the mid-tick release survives to the next drain"
        (let [w1 (@#'infra.dev.window.loop/drain-intents @world-atom queue)]
          (reset! world-atom w1)
          (is (nil? (:player/thrust @world-atom))
              "release clears the channel — the damping term then coasts the mote down"))))))

(deftest dump-error-artifacts-graceful-on-failure
  (testing "if writing fails the function returns an error map instead of throwing"
    (let [w (assoc (ecs/empty-world) :tick 1)]
      (with-redefs [spit (fn [& _] (throw (ex-info "disk full" {})))]
        (let [dump (@#'infra.dev.window.loop/dump-error-artifacts! w)]
          (is (contains? dump :error)))))))

(deftest log-frame-error-includes-tick-and-paths
  (testing "the error log line contains the failing tick and dump paths"
    (let [out  (java.io.StringWriter.)
          err  (ex-info "boom" {:x 1})
          dump {:world-path "/tmp/w.edn" :ledger-path "/tmp/l.edn"}]
      (binding [*err* out]
        (@#'infra.dev.window.loop/log-frame-error! err 123 dump))
      (let [line (str out)]
        (is (re-find #"tick=123" line))
        (is (re-find #"world=/tmp/w\.edn" line))
        (is (re-find #"ledger=/tmp/l\.edn" line))))))
