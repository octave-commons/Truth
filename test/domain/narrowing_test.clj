(ns domain.narrowing-test
  "The First Narrowing, child A: gravitational binding coupling + cost curve
   (kanban/tasks/narrowing-binding-mechanic.md). Fixtures follow
   domain.focus-conservation-test: a frozen world, the system's :run called
   directly, assertions on the write-set. Pure binding-step and cost-curve
   tests call domain.narrowing fns directly."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.components :as c]
   [domain.ecs.core :as ecs]
   [domain.ecs.registry :as reg]
   [domain.narrowing :as narrowing]
   [domain.player :as player]
   [law.narrowing :as law]
   [shape.spatial :as sp]))

(def ^:private immediate-r 1.0e15)

(defn- world-with-observer
  "An empty world with an observer at the origin. `focus-position` at the
   origin, `focus-intensity` 0.8 (sustained), immediate-r `immediate-r`."
  []
  (let [[w obs-eid] (player/spawn-observer (ecs/empty-world) (sp/vec3 0.0 0.0 0.0))]
    [(player/update-observer w
                             (fn [obs]
                               (assoc obs
                                      :focus-position (sp/vec3 0.0 0.0 0.0)
                                      :focus-intensity 0.8
                                      :attention-shell {:immediate-r immediate-r
                                                        :regional-r (* 4.0 immediate-r)})))
     obs-eid]))

(defn- spawn-candidate
  "Spawn a candidate world (c/planet-candidate + c/position) at `pos`.
   Returns [world eid]."
  [world pos]
  (let [[w eid] (ecs/spawn world)]
    [(ecs/put-components w eid {c/planet-candidate {:planet-id eid}
                                c/position         pos})
     eid]))

(defn- run-system
  "Run the binding system's :run fn directly and return its write-set."
  [world]
  ((:run (narrowing/binding-system)) world))

(defn- approx=
  "Approximate double equality (relative tolerance 1e-9) for fp accrual math."
  [a b]
  (< (Math/abs (- (double a) (double b)))
     (* 1.0e-9 (max 1.0 (Math/abs (double a)) (Math/abs (double b))))))

(deftest accrual-on-sustained-overlapping-focus
  (testing "candidate within the immediate radius, focus sustained: binding
            accrues at the law rate on the observer entity"
    (let [[w0 obs-eid] (world-with-observer)
          [w weid] (spawn-candidate w0 (sp/vec3 1.0e10 0.0 0.0))
          ws (run-system w)
          binding (get-in ws [c/binding obs-eid])]
      (is (= law/accrual-rate (get binding weid))
          "one focused tick accrues exactly one tick of binding")
      (is (law/binding? binding) "the emitted binding map satisfies the law schema"))))

(deftest no-accrual-below-sustained-floor
  (testing "focus-intensity below the floor is a glance, not sustained Focus:
            no binding accrues even inside the immediate radius"
    (let [[w0 obs-eid] (world-with-observer)
          w1 (player/update-observer w0 #(assoc % :focus-intensity 0.3))
          [w weid] (spawn-candidate w1 (sp/vec3 1.0e10 0.0 0.0))
          ws (run-system w)]
      (is (nil? (get-in ws [c/binding obs-eid weid]))
          "unsustained focus does not bind"))))

(deftest no-accrual-outside-immediate-radius
  (testing "a candidate beyond the attention-shell immediate radius does not
            overlap the focus: no binding accrues"
    (let [[w0 obs-eid] (world-with-observer)
          [w weid] (spawn-candidate w0 (sp/vec3 (* 2.0 immediate-r) 0.0 0.0))
          ws (run-system w)]
      (is (nil? (get-in ws [c/binding obs-eid weid]))
          "out-of-shell worlds are invisible to binding"))))

(deftest sticky-decay-when-attention-elsewhere
  (testing "an existing binding decays at the slow sticky rate (no zero-sum
            top-up) when nothing is focused"
    (let [[w0 obs-eid] (world-with-observer)
          [w1 weid] (spawn-candidate w0 (sp/vec3 (* 2.0 immediate-r) 0.0 0.0))
          w (ecs/put-component w1 obs-eid c/binding {weid 0.5})
          ws (run-system w)
          binding (get-in ws [c/binding obs-eid])]
      (is (= (- 0.5 law/decay-rate) (get binding weid))
          "a glance away costs one sticky decay tick, not unbinding"))))

(deftest zero-sum-decay-of-other-worlds
  (testing "binding to world A actively decays binding to world B: attention
            is zero-sum, you can only fall one way"
    (let [[w0 obs-eid] (world-with-observer)
          [w1 wa] (spawn-candidate w0 (sp/vec3 1.0e10 0.0 0.0))
          [w2 wb] (spawn-candidate w1 (sp/vec3 (* 2.0 immediate-r) 0.0 0.0))
          w (ecs/put-component w2 obs-eid c/binding {wb 0.5})
          ws (run-system w)
          binding (get-in ws [c/binding obs-eid])]
      (is (= law/accrual-rate (get binding wa))
          "the focused world accrues")
      (is (= (- 0.5 law/decay-rate law/zero-sum-decay-rate) (get binding wb))
          "the unfocused world pays sticky + zero-sum decay"))))

(deftest sunk-cost-scar-on-un-binding
  (testing "lost binding leaves a permanent sunk scar; re-accruing never
            refunds it"
    (let [[w0 obs-eid] (world-with-observer)
          [w1 weid] (spawn-candidate w0 (sp/vec3 (* 2.0 immediate-r) 0.0 0.0))
          w (ecs/put-component w1 obs-eid c/binding {weid 0.5})
          ws1 (run-system w)
          scar1 (get-in ws1 [c/binding-scar obs-eid weid])]
      (is (approx= (* law/scar-fraction law/decay-rate) scar1)
          "one decay tick scars scar-fraction of the binding lost")
      (testing "the scar persists and grows; it never decreases"
        (let [w2 (-> w
                     (ecs/put-component obs-eid c/binding (get-in ws1 [c/binding obs-eid]))
                     (ecs/put-component obs-eid c/binding-scar (get-in ws1 [c/binding-scar obs-eid])))
              ws2 (run-system w2)
              scar2 (get-in ws2 [c/binding-scar obs-eid weid])]
          (is (approx= (* 2.0 law/scar-fraction law/decay-rate) scar2)
              "a second decay tick adds to the same scar")
          (is (law/binding-scar? (get-in ws2 [c/binding-scar obs-eid]))
              "the emitted scar tally satisfies the law schema"))))))

(deftest cost-curve-monotone-vs-binding
  (testing "nudge cost falls with binding; release cost rises with binding and
            is free at binding 0"
    (let [world-params {:mass 6.0e24 :radius 6.4e6}
          nudge (fn [b] (narrowing/nudge-cost b world-params))
          release (fn [b] (narrowing/release-cost b world-params))]
      (is (> (nudge 0.0) (nudge 0.5) (nudge 0.9))
          "deeper binding = cheaper nudge (leverage from closeness)")
      (is (< (release 0.1) (release 0.5) (release 0.9))
          "deeper binding = costlier release (climbing out of the well)")
      (is (= 0.0 (release 0.0)) "release is free at binding 0")
      (is (pos? (nudge 1.0)) "even at capture a nudge is not quite free"))))

(deftest release-cost-follows-escape-energy-proxy
  (testing "at the same binding, a heavier/deeper-well world is costlier to
            leave — the literal GM/R ordering"
    (let [earth {:mass 6.0e24 :radius 6.4e6}
          super-earth {:mass 1.2e25 :radius 6.4e6}
          b 0.5]
      (is (< (narrowing/release-cost b earth)
             (narrowing/release-cost b super-earth))
          "twice the mass at the same radius = twice the well depth = twice the cost")
      (is (= (/ (narrowing/escape-energy-proxy (:mass super-earth) (:radius super-earth))
                (narrowing/escape-energy-proxy (:mass earth) (:radius earth)))
             (/ (narrowing/release-cost b super-earth)
                (narrowing/release-cost b earth)))
          "release-cost ratio IS the escape-energy-proxy ratio (tuned scale only)"))))

(deftest escape-energy-proxy-degenerate-radius
  (testing "GM/R is 0.0 for a body whose radius has not resolved (mirrors the
            M5 surface-gravity degenerate case)"
    (is (= 0.0 (narrowing/escape-energy-proxy 6.0e24 0.0)))
    (is (= 0.0 (narrowing/escape-energy-proxy 6.0e24 nil)))))

(deftest binding-step-pure-contract
  (testing "the pure step: accrual clamps at 1.0, decay drops worlds at 0,
            signals scale accrual, and neutral signals are the default"
    (let [step (fn [m] (narrowing/binding-step m))]
      (is (= 1.0 (get (:binding (step {:binding {7 0.99} :focused-eids [7]})) 7))
          "accrual clamps at capture, never overshoots")
      (is (nil? (get (:binding (step {:binding {7 0.001}})) 7))
          "a world decayed to zero is dropped from the binding map")
      (is (= (* law/accrual-rate 2.0)
             (get (:binding (step {:focused-eids [7]
                                   :signals {7 {:habitability 2.0}}})) 7))
          "a habitability signal scales the accrual rate")
      (is (= law/accrual-rate
             (get (:binding (step {:focused-eids [7]})) 7))
          "absent signals default to neutral"))))

(deftest single-writer-preserved
  (testing "c/binding and c/binding-scar have exactly one writer across the
            whole registry; the invariant still holds"
    (is (= {} (reg/write-conflicts reg/systems)))
    (is (= [:binding] (get (reg/writers-by-component reg/systems) c/binding)))
    (is (= [:binding] (get (reg/writers-by-component reg/systems) c/binding-scar)))
    (is (= (reg/registry-writes :binding)
           (:writes (narrowing/binding-system)))
        "the emitter's :writes is sourced from the registry, not restated")))
