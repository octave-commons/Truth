(ns domain.particles.phase0-test
  "Tests for the particle-field Phase 0 orchestrator."
  (:require [clojure.test :refer [deftest testing is]]
            [domain.particles.phase0 :as pp]
            [domain.ecs.components :as c]
            [domain.ecs.core :as ecs]
            [domain.player :as player]))

(deftest test-particle-world-construction
  (testing "A fresh particle world has a field, mesh, observer, and no resolved bodies"
    (let [w (pp/create-world)]
      (is (some? (:phase0/field w)))
      (is (some? (:phase0/mesh w)))
      (is (= :particle (:phase0/mode w)))
      (is (= 1 (count (ecs/entities-with w c/observer))))
      (is (zero? (:body-count (pp/system-summary w))))
      (is (= :phase-0/nebula-collapse (:phase0/phase w))))))

(deftest test-particle-tick-advances-field
  (testing "Ticking consumes particles into sinks and may promote them"
    (let [w0 (pp/create-world)
          w1 (pp/tick-world w0)
          s0 (pp/system-summary w0)
          s1 (pp/system-summary w1)]
      (is (<= (:live-particles s1) (:live-particles s0)))
      (is (>= (:body-count s1) (:body-count s0))))))

(deftest test-particle-run-reaches-star
  (testing "A longer run produces at least one star"
    (let [final (loop [w (pp/create-world) i 0]
                  (if (or (> i 400) (not (:phase0/active w)) (pp/world-ending w))
                    w
                    (recur (pp/tick-world w) (inc i))))
          summ  (pp/system-summary final)]
      (is (:star? summ) "a star should ignite from the collapsing cloud")
      (is (pos? (:body-count summ))))))

(deftest test-particle-input
  (testing "Player controls operate on the observer"
    (let [w (pp/create-world)
          before (:focus-radius (player/get-observer w))
          narrowed (pp/handle-input w :narrow-focus)]
      (is (< (:focus-radius (player/get-observer narrowed)) before)))))
