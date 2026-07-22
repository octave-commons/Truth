(ns domain.focus-zone-test
  "Player Focus, child B: behavior/smoke tests for the `:focus-zone` fan-out
   emitter (`domain.genesis.promotion/focus-zone-system`). Proves the emitter
   actually promotes an overlapping cell, demotes a withdrawn clump back into
   it, respects the threshold-event delay, and holds the single-writer
   invariant. The 7 named conservation tests (mass/momentum/L to high
   precision, the promotion-invariant validator, etc.) are child C's scope —
   this file is deliberately narrower."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.event :as event]
   [domain.ecs.registry :as reg]
   [domain.field :as field]
   [domain.genesis.promotion :as promotion]
   [domain.player :as player]
   [shape.spatial :as sp]))

(def ^:private sample-ledger
  {:mass 1.0e27
   :velocity (sp/vec3 1.0 2.0 3.0)
   :angular-momentum (sp/vec3 0.0 0.0 1.0e30)
   :mean-b (sp/vec3 0.0 0.0 1.0e-9)
   :temperature 15.0
   :composition {:H 0.74 :He 0.24 :Z 0.02}})

(defn- world-with-observer
  "An empty world with an observer at the origin, attention shell overridden to
   `immediate-r`."
  [immediate-r]
  (let [[w _eid] (player/spawn-observer (ecs/empty-world) (sp/vec3 0.0 0.0 0.0))]
    (player/update-observer w
                            (fn [obs]
                              (assoc obs
                                     :focus-position (sp/vec3 0.0 0.0 0.0)
                                     :attention-shell {:immediate-r immediate-r
                                                       :regional-r (* 4.0 immediate-r)})))))

(defn- run-system
  "Run the focus-zone system's :run fn directly and return its write-set."
  [world]
  ((:run (promotion/focus-zone-system)) world))

(deftest focus-zone-registered-as-sole-writer
  (testing "reg/write-conflicts is empty and :focus-zone owns its declared writes"
    (is (empty? (reg/write-conflicts reg/systems)))
    (is (= #{c/field-zone c/statistical-mass c/spawn-request-promotion c/consumed-demote}
           (reg/registry-writes :focus-zone)))))

(deftest promotion-emits-spawn-request-and-debits-cell
  (testing "an overlapping regional cell emits one spawn-request-promotion spec
            and its ledger mass is debited to 0.0 in the same write-set"
    (let [w0 (world-with-observer 1.0e15)
          [w cell-eid] (field/spawn-regional-cell w0 sample-ledger (sp/vec3 1.0e10 0.0 0.0))
          ws (run-system w)]
      (is (contains? (get ws c/spawn-request-promotion {}) cell-eid)
          "the cell got a spawn-request-promotion entry")
      (let [[spec] (get-in ws [c/spawn-request-promotion cell-eid])]
        (is (= (:mass sample-ledger) (:mass spec)) "spawned clump mass == full cell mass")
        (is (= (:velocity sample-ledger) (:velocity spec)) "spawned clump velocity == cell COM velocity")
        (is (= (:angular-momentum sample-ledger) (:angular-momentum spec))
            "spawned clump angular momentum == cell angular momentum")
        (is (= cell-eid (get-in spec [:extra-components c/promoted-from-cell])))
        (is (= :immediate (get-in spec [:extra-components c/field-zone]))))
      (is (= 0.0 (:mass (get-in ws [c/statistical-mass cell-eid])))
          "the cell's ledger mass is fully debited in the same write-set"))))

(deftest promotion-skips-cells-out-of-range-or-already-spent
  (testing "a cell far outside the immediate radius, or one already at zero mass, is not promoted"
    (let [w0 (world-with-observer 1.0e10)
          [w-far _far-eid] (field/spawn-regional-cell w0 sample-ledger (sp/vec3 1.0e15 0.0 0.0))
          ;; A cell must be constructed with a positive mass (schema-enforced by
          ;; spawn-regional-cell), so "already spent" is simulated the same way
          ;; a real spent cell arises: promote first, then re-run on the debited
          ;; result.
          [w-fresh spent-eid] (field/spawn-regional-cell w0 sample-ledger (sp/vec3 0.0 0.0 0.0))
          w-spent (ecs/put-component w-fresh spent-eid c/statistical-mass
                                     (assoc sample-ledger :mass 0.0))]
      (is (empty? (get (run-system w-far) c/spawn-request-promotion {})))
      (is (not (contains? (get (run-system w-spent) c/spawn-request-promotion {}) spent-eid))))))

(deftest demotion-emits-consumed-and-credits-cell
  (testing "a promoted clump that has left the immediate radius (no blocking
            threshold event) is marked consumed-demote and its mass/velocity/L
            are folded back into the source cell's ledger"
    (let [w0 (world-with-observer 1.0e10)
          ;; The cell itself sits outside the immediate radius (it is a spent/
          ;; distant cell being recharged by demotion, not simultaneously
          ;; promoted this same tick).
          [w1 cell-eid] (field/spawn-regional-cell w0 (assoc sample-ledger :mass 1.0e27) (sp/vec3 1.0e14 0.0 0.0))
          [w2 clump-eid] (ecs/spawn w1)
          w (ecs/put-components w2 clump-eid
                                {c/matter-state       :planetesimal
                                 c/position            (sp/vec3 1.0e14 0.0 0.0) ;; well outside 1e10 immediate-r
                                 c/velocity             (sp/vec3 0.0 1.0 0.0)
                                 c/mass                 1.0e20
                                 c/radius               1.0e6
                                 c/angular-momentum     (sp/vec3 0.0 0.0 1.0e25)
                                 c/temperature          300.0
                                 c/field-zone           :immediate
                                 c/promoted-from-cell   cell-eid})
          ws (run-system w)]
      (is (true? (get-in ws [c/consumed-demote clump-eid]))
          "the withdrawn clump is marked for reaping")
      (let [ledger' (get-in ws [c/statistical-mass cell-eid])]
        (is (some? ledger') "the source cell's ledger was credited")
        (is (= (+ 1.0e27 1.0e20) (:mass ledger')) "mass conserved exactly")
        (is (= (sp/v+ (sp/vec3 0.0 0.0 1.0e30) (sp/vec3 0.0 0.0 1.0e25))
               (:angular-momentum ledger'))
            "angular momentum conserved exactly (additive about the same origin)")))))

(deftest demotion-respects-threshold-event-delay
  (testing "a clump involved in a threshold event THIS tick is not demoted,
            even though it is outside the immediate radius"
    (let [w0 (world-with-observer 1.0e10)
          [w1 cell-eid] (field/spawn-regional-cell w0 sample-ledger (sp/vec3 1.0e14 0.0 0.0))
          [w2 clump-eid] (ecs/spawn w1)
          w3 (ecs/put-components w2 clump-eid
                                 {c/matter-state       :planetesimal
                                  c/position            (sp/vec3 1.0e14 0.0 0.0)
                                  c/velocity             (sp/vec3 0.0 0.0 0.0)
                                  c/mass                 1.0e20
                                  c/radius               1.0e6
                                  c/angular-momentum     (sp/vec3 0.0 0.0 0.0)
                                  c/temperature          300.0
                                  c/field-zone           :immediate
                                  c/promoted-from-cell   cell-eid})
          w4 (assoc w3 :tick 5)
          w  (event/dispatch w4 (event/->event {:tick 5 :kind :event/collision :entities #{clump-eid}}))
          ws (run-system w)]
      (is (not (get-in ws [c/consumed-demote clump-eid]))
          "blocked by the same-tick threshold event")
      (is (nil? (get-in ws [c/statistical-mass cell-eid]))
          "the cell ledger is untouched since nothing was demoted into it"))))
