(ns domain.focus-conservation-test
  "Player Focus, child C: the 7 CANONICAL named conservation tests for the
   `:focus-zone` promotion/demotion round-trip (card:
   kanban/tasks/phase-0-player-focus-c-conservation-tests.md). Where child B's
   `domain.focus-zone-test` proves the emitter fires, these tests prove the
   conserved QUANTITIES — mass, linear momentum, angular momentum — survive
   promotion and demotion to conservation precision, that the ledger survives
   a despawn, that same-tick threshold events delay demotion, and that the
   pure `law.field/promotion-invariant?` validator itself discriminates.

   Precision notes: child B implements WHOLE-cell sampling — the spawn spec's
   :mass/:velocity/:angular-momentum are copied verbatim from the cell ledger
   (`domain.genesis.promotion/promotion-spec`), and demotion credits mass by
   exact addition (`credit-ledger`). So exact equality is assertable here; the
   `promotion-invariant?` tolerance assertions are included as the card's
   conservation contract, not because sampling noise forces them. No partial
   sampling exists yet — when it does, the exact assertions become
   validator-tolerance assertions."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.event :as event]
   [domain.field :as field]
   [domain.genesis :as genesis]
   [domain.genesis.promotion :as promotion]
   [domain.player :as player]
   [law.field.schema :as lf]
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

(defn- apply-write-set
  "Fold a write-set {component {eid value}} onto `world` as component writes —
   the same landing `tick-physics` performs when it materializes a fan-out
   write-set."
  [world ws]
  (reduce-kv (fn [w ct entries]
               (reduce-kv (fn [w' eid v] (ecs/put-component w' eid ct v)) w entries))
             world ws))

(deftest promotion-conserves-mass
  (testing "promoting a 1e27 kg cell: spawn spec mass == cell mass within
            promotion-invariant? tolerance; the cell is debited to 0.0 in the
            same write-set"
    (let [w0 (world-with-observer 1.0e15)
          [w cell-eid] (field/spawn-regional-cell w0 sample-ledger (sp/vec3 1.0e10 0.0 0.0))
          ws (run-system w)
          [spec] (get-in ws [c/spawn-request-promotion cell-eid])]
      (is (some? spec) "the overlapping cell was promoted")
      (is (lf/promotion-invariant? [sample-ledger] [spec])
          "mass/momentum/L conserved within validator tolerance")
      ;; Whole-cell sampling copies :mass verbatim, so exact equality holds
      ;; (see ns docstring — no sampling noise exists to excuse tolerance).
      (is (= (:mass sample-ledger) (:mass spec))
          "spawned clump mass == cell mass exactly")
      (is (= 0.0 (:mass (get-in ws [c/statistical-mass cell-eid])))
          "cell debited to 0.0 — before + after mass still sums to 1e27"))))

(deftest promotion-conserves-momentum
  (testing "a cell with nonzero COM velocity: m·v of the spawned clump equals
            the cell's momentum exactly (velocity copied verbatim)"
    (let [w0 (world-with-observer 1.0e15)
          [w cell-eid] (field/spawn-regional-cell w0 sample-ledger (sp/vec3 1.0e10 0.0 0.0))
          ws (run-system w)
          [spec] (get-in ws [c/spawn-request-promotion cell-eid])]
      (is (some? spec) "the overlapping cell was promoted")
      (is (= (sp/v* (:velocity sample-ledger) (:mass sample-ledger))
             (sp/v* (:velocity spec) (:mass spec)))
          "m·v of the spawned clump == cell momentum")
      (is (lf/promotion-invariant? [sample-ledger] [spec])
          "validator agrees momentum is conserved"))))

(deftest promotion-conserves-angular-momentum
  (testing "a cell with nonzero angular momentum: the spawned clump's L equals
            the source cell's L exactly (copied verbatim)"
    (let [w0 (world-with-observer 1.0e15)
          [w cell-eid] (field/spawn-regional-cell w0 sample-ledger (sp/vec3 1.0e10 0.0 0.0))
          ws (run-system w)
          [spec] (get-in ws [c/spawn-request-promotion cell-eid])]
      (is (some? spec) "the overlapping cell was promoted")
      (is (= (:angular-momentum sample-ledger) (:angular-momentum spec))
          "spawned clump L == source cell L")
      (is (lf/promotion-invariant? [sample-ledger] [spec])
          "validator agrees angular momentum is conserved"))))

(deftest demotion-conserves-mass
  (testing "a resolved body outside the immediate radius carrying
            c/promoted-from-cell: the cell is credited by the body mass exactly
            and the body is marked c/consumed-demote in the same write-set"
    (let [w0 (world-with-observer 1.0e10)
          [w1 cell-eid] (field/spawn-regional-cell w0 sample-ledger (sp/vec3 1.0e14 0.0 0.0))
          [w2 clump-eid] (ecs/spawn w1)
          w (ecs/put-components w2 clump-eid
                                {c/matter-state       :planetesimal
                                 c/position            (sp/vec3 1.0e14 0.0 0.0)
                                 c/velocity             (sp/vec3 0.0 1.0 0.0)
                                 c/mass                 1.0e20
                                 c/radius               1.0e6
                                 c/angular-momentum     (sp/vec3 0.0 0.0 1.0e25)
                                 c/temperature          300.0
                                 c/field-zone           :immediate
                                 c/promoted-from-cell   cell-eid})
          ws (run-system w)
          ledger' (get-in ws [c/statistical-mass cell-eid])]
      (is (true? (get-in ws [c/consumed-demote clump-eid]))
          "the withdrawn body is marked for reaping")
      (is (some? ledger') "the source cell was credited")
      ;; credit-ledger adds masses directly — exact, no sampling involved.
      (is (= (+ (:mass sample-ledger) 1.0e20) (:mass ledger'))
          "cell mass credited by exactly the body mass")
      (is (lf/promotion-invariant?
           [sample-ledger {:mass 1.0e20
                           :velocity (sp/vec3 0.0 1.0 0.0)
                           :angular-momentum (sp/vec3 0.0 0.0 1.0e25)}]
           [ledger'])
          "cell + body before == credited cell after, within validator tolerance"))))

(deftest demotion-preserves-ledger
  (testing "despawning a demoted body leaves prior :ledger events intact —
            the event record of what the resolved body DID survives its return
            to the statistical representation"
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
          ;; A threshold event from a PRIOR tick: it lives in the ledger but does
          ;; not block demotion (only same-tick events block).
          w (-> (assoc w3 :tick 5)
                (event/dispatch (event/->event {:tick 1 :kind :event/collision
                                                :entities #{clump-eid}}))
                (event/dispatch (event/->event {:tick 2 :kind :event/planet-formation
                                                :entities #{clump-eid}})))
          prior-events (get-in w [:ledger :events])
          ws (run-system w)
          w' (-> w
                 (apply-write-set ws)
                 genesis/materialize-lifecycle)]
      (is (= 2 (count prior-events)) "fixture sanity: two prior ledger events")
      (is (true? (get-in ws [c/consumed-demote clump-eid]))
          "the prior-tick events do not block demotion")
      (is (not (ecs/alive? w' clump-eid))
          "the demoted body was despawned at materialize-lifecycle")
      (is (= prior-events (get-in w' [:ledger :events]))
          "every prior ledger event survives the body's despawn, in order"))))

(deftest demotion-threshold-events-delay
  (testing "a body in a THIS-tick threshold event (:event/collision) is NOT
            demoted — no mass moves, nothing is reaped"
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
          w (-> (assoc w3 :tick 5)
                (event/dispatch (event/->event {:tick 5 :kind :event/collision
                                                :entities #{clump-eid}})))
          ws (run-system w)]
      (is (not (get-in ws [c/consumed-demote clump-eid]))
          "blocked by the same-tick threshold event")
      (is (nil? (get-in ws [c/statistical-mass cell-eid]))
          "the cell ledger is untouched — mass neither leaves the body nor
           enters the cell, so conservation holds trivially this tick"))))

(deftest promotion-invariant-validator
  (testing "pure law.field/promotion-invariant?: true for a valid before/after,
            false for a perturbed one"
    (let [before [{:mass 1.0e27
                   :velocity (sp/vec3 1.0 2.0 3.0)
                   :angular-momentum (sp/vec3 0.0 0.0 1.0e30)}]
          valid-after before
          perturbed-mass [(assoc (first before) :mass 1.1e27)]
          perturbed-velocity [(assoc (first before) :velocity (sp/vec3 1.1 2.0 3.0))]
          perturbed-l [(assoc (first before) :angular-momentum (sp/vec3 0.0 0.0 1.1e30))]
          within-tol [(assoc (first before) :mass (* 1.0e27 (+ 1.0 1.0e-8)))]]
      (is (true? (lf/promotion-invariant? before valid-after))
          "identical before/after conserves everything")
      (is (true? (lf/promotion-invariant? before within-tol))
          "a perturbation inside the relative tolerance still passes")
      (is (false? (lf/promotion-invariant? before perturbed-mass))
          "a 10% mass violation is caught")
      (is (false? (lf/promotion-invariant? before perturbed-velocity))
          "a momentum violation is caught")
      (is (false? (lf/promotion-invariant? before perturbed-l))
          "an angular-momentum violation is caught"))))
