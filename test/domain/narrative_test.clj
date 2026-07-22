(ns domain.narrative-test
  "Tests for the narrative presence / mood layer."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.narrative :as narrative]
   [domain.genesis   :as genesis]
   [domain.player    :as player]
   [domain.ecs.core  :as ecs]
   [domain.ecs.event :as event]
   [domain.ecs.components :as c]
   [infra.render.hud :as rhud]
   [infra.render.scene.hud :as hud]))

(deftest test-mood-wonder-after-ignition
  (testing "A :stellar-ignition event drives the mood to :wonder"
    (let [events [{:kind :event/stellar-ignition :tick 1}]]
      (is (= :wonder (narrative/mood-from-events events :anticipation 0.8 :arc/genesis-ignition nil)))))
  (testing "Life emergence and gate discovery also yield :wonder"
    (is (= :wonder (narrative/mood-from-events [{:kind :event/life-emergence}] :dread 0.8 :arc/life-emergence nil)))
    (is (= :wonder (narrative/mood-from-events [{:kind :event/gate-discovery}] :dread 0.8 :arc/life-emergence nil)))))

(deftest test-mood-dread-as-coherence-fades
  (testing "Low coherence with no threshold events drifts the mood to :dread"
    (is (= :dread (narrative/mood-from-events [] :anticipation 0.1 :arc/genesis-accretion nil))))
  (testing "Low coherence is ignored when a threshold event occurs this tick"
    (is (= :wonder (narrative/mood-from-events [{:kind :event/stellar-ignition}] :anticipation 0.1 :arc/genesis-ignition nil)))))

(deftest test-mood-sterile-on-fadeout
  (testing "A dispersed/sterile ending sets the mood to :sterility"
    (is (= :sterility (narrative/mood-from-events [] :wonder 0.8 :arc/genesis-dispersed :dispersal)))
    (is (= :sterility (narrative/mood-from-events [] :wonder 0.8 :arc/genesis-planets-formed :sterile))))
  (testing "A :sterility mood overrides fading coherence"
    (is (= :sterility (narrative/mood-from-events [] :anticipation 0.1 :arc/genesis-dispersed :dispersal)))))

(deftest test-narrative-system-writes-state
  (testing "narrative-system writes a :component/narrative-state on the observer"
    (let [w0 (-> (genesis/create-world {:gas-count 20})
                 (event/with-ledger)
                 (event/emit (event/->event {:tick 0 :kind :event/stellar-ignition :entities #{}})))
          w1 ((narrative/narrative-system) w0)
          eid (player/observer-entity w1)]
      (is (some? eid))
      (is (= :wonder (:mood (ecs/get-component w1 eid c/narrative-state)))))))

(deftest test-mood-tint-does-not-crash-render
  (testing "hud-rects-from-world includes a mood-tint rectangle and does not crash"
    (let [w0 (genesis/create-world {:gas-count 20})
          rects (hud/hud-rects-from-world w0)]
      (is (seq rects))
      (is (every? #(and (:x0 %) (:y0 %) (:x1 %) (:y1 %) (:color %)) rects))
      (is (some (fn [r] (= -1.0 (:x0 r) (:y0 r))) rects)
          "full-screen mood tint rectangle is present"))))

(deftest test-mood-tenderness-on-commitment
  (testing "a :world-commitment event drives the mood to :tenderness"
    (is (= :tenderness (narrative/mood-from-events [{:kind :event/world-commitment :tick 1}]
                                                   :anticipation 0.8 :arc/genesis-planets-formed nil))))
  (testing "commitment suppresses the dread drift at low coherence"
    (is (= :tenderness (narrative/mood-from-events [{:kind :event/world-commitment :tick 1}]
                                                   :anticipation 0.1 :arc/genesis-planets-formed nil))))
  (testing "wonder still wins when ignition and commitment share a tick"
    (is (= :wonder (narrative/mood-from-events [{:kind :event/world-commitment :tick 1}
                                                {:kind :event/stellar-ignition :tick 1}]
                                               :anticipation 0.8 :arc/genesis-ignition nil)))))

(deftest test-commitment-line-enqueued-once
  (testing "on :event/world-commitment the narrator stamps ONE ambient line"
    (let [w0  (-> (genesis/create-world {:gas-count 20})
                  (event/with-ledger)
                  (event/emit (event/->event {:tick 0 :kind :event/world-commitment :entities #{}})))
          w1  ((narrative/narrative-system) w0)
          eid (player/observer-entity w1)
          st1 (ecs/get-component w1 eid c/narrative-state)]
      (is (= :tenderness (:mood st1)))
      (is (= narrative/commitment-line (get-in st1 [:last-line :text])))
      (is (= :ambient (get-in st1 [:last-line :attribution]))
          "ambient, never addressed")
      (is (= 0 (:last-utterance-tick st1)))
      (testing "a second pass over the same event cannot re-speak the line"
        (let [w2  ((narrative/narrative-system) w1)
              st2 (ecs/get-component w2 eid c/narrative-state)]
          (is (= st1 st2)))))))

(deftest test-ambient-line-floats-and-fades
  (testing "the HUD surfaces the ambient line as a dim viewport float, then lets it go"
    (let [w0 (-> (genesis/create-world {:gas-count 20})
                 (event/with-ledger)
                 (event/emit (event/->event {:tick 0 :kind :event/world-commitment :entities #{}})))
          w1 ((narrative/narrative-system) w0)
          text-fresh (rhud/observer-hud-text w1 1280 720)
          line-fresh (some #(when (= narrative/commitment-line (:text %)) %) text-fresh)]
      (is (some? line-fresh) "the line is in the HUD text")
      (is (< (nth (:color line-fresh) 3) 0.6) "dim, ambient — not an announcement")
      (let [w-old (assoc w1 :tick 10000)
            text-old (rhud/observer-hud-text w-old 1280 720)]
        (is (nil? (some #(when (= narrative/commitment-line (:text %)) %) text-old))
            "the float fades away entirely after the fade window")))))
