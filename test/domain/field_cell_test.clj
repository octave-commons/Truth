(ns domain.field-cell-test
  "Player Focus, child A: the regional-cell substrate + lifecycle markers
   (spawn-request-promotion, consumed-demote, promoted-from-cell). Nothing
   here ticks — this is coverage for the substrate itself, not the
   :focus-zone system (child B)."
  (:require
   [clojure.set :as set]
   [clojure.test :refer [deftest is testing]]
   [malli.core :as m]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.field :as field]
   [domain.genesis :as genesis]
   [law.field.schema :as lf]
   [shape.spatial :as sp]))

(def ^:private sample-ledger
  {:mass 1.0e27
   :velocity (sp/vec3 1.0 2.0 3.0)
   :angular-momentum (sp/vec3 0.0 0.0 1.0e30)
   :mean-b (sp/vec3 0.0 0.0 1.0e-9)
   :temperature 15.0
   :composition {:H 0.74 :He 0.24 :Z 0.02}})

(deftest regional-cell-satisfies-schema
  (testing "a regional cell's ledger validates against the statistical-cell schema"
    (is (m/validate lf/statistical-cell-schema sample-ledger)
        "sanity: fixture ledger is itself schema-valid")))

(deftest spawn-regional-cell-constructs-valid-entity
  (testing "spawn-regional-cell builds an entity with statistical-mass + field-zone :regional + position, and no matter-state"
    (let [[w eid] (field/spawn-regional-cell (ecs/empty-world) sample-ledger (sp/vec3 1.0e15 0.0 0.0))]
      (is (ecs/alive? w eid))
      (is (= sample-ledger (ecs/get-component w eid c/statistical-mass)))
      (is (= :regional (ecs/get-component w eid c/field-zone)))
      (is (= (sp/vec3 1.0e15 0.0 0.0) (ecs/get-component w eid c/position)))
      (is (nil? (ecs/get-component w eid c/matter-state))
          "a cell must carry no matter-state, or gravity/hydro/classifier/integrator would see it")
      (is (nil? (ecs/get-component w eid c/mass))
          "a cell must carry no plain c/mass either, or mass-only sweeps would see it"))))

(deftest spawn-regional-cell-rejects-malformed-ledger
  (testing "a ledger missing required keys throws rather than silently corrupting accounting"
    (is (thrown? Exception
                 (field/spawn-regional-cell (ecs/empty-world) {:mass 1.0e27} (sp/vec3 0.0 0.0 0.0))))))

(deftest cell-invisible-to-generic-mass-sweeps
  (testing "the cell carries c/position (by design) but no c/mass, c/velocity, or
            c/body-kind, so every existing entities-with sweep that requires
            any of those (the guard's grep found no c/position-only sweep in
            the codebase) skips it entirely"
    (let [[w eid] (field/spawn-regional-cell (ecs/empty-world) sample-ledger (sp/vec3 0.0 0.0 0.0))]
      (is (contains? (set (ecs/entities-with w c/position)) eid)
          "the cell does carry position — isolation relies on no code doing a position-only sweep")
      (is (empty? (ecs/entities-with w c/mass)))
      (is (empty? (ecs/entities-with w c/velocity)))
      (is (empty? (ecs/entities-with w c/body-kind))))))

(deftest spawn-request-promotion-materializes-a-stamped-clump
  (testing "a spawn-request-promotion spec materializes into a resolved body stamped with promoted-from-cell"
    (let [[w cell-eid] (field/spawn-regional-cell (ecs/empty-world) sample-ledger (sp/vec3 0.0 0.0 0.0))
          spec {:position (sp/vec3 1.0 0.0 0.0)
                :velocity (sp/vec3 0.0 0.0 0.0)
                :mass 1.0e20
                :radius 1.0e6
                :matter-state :planetesimal
                :body-kind :body/rocky
                :extra-components {c/promoted-from-cell cell-eid}}
          w' (-> w
                 (ecs/put-component cell-eid c/spawn-request-promotion [spec])
                 genesis/materialize-lifecycle)
          before-eids (ecs/entities-with w c/matter-state)
          after-eids  (ecs/entities-with w' c/matter-state)
          new-eids    (set/difference (set after-eids) (set before-eids))]
      (is (ecs/alive? w' cell-eid) "the source cell itself is untouched")
      (is (nil? (ecs/get-component w' cell-eid c/spawn-request-promotion))
          "the request is consumed at materialization")
      (is (= 1 (count new-eids)) "exactly one new resolved body materialized")
      (let [new-eid (first new-eids)]
        (is (= :planetesimal (ecs/get-component w' new-eid c/matter-state)))
        (is (= cell-eid (ecs/get-component w' new-eid c/promoted-from-cell))
            "the clump is stamped with its source cell's entity id")))))

(deftest consumed-demote-is-reaped-without-perturbing-others
  (testing "a body marked consumed-demote is despawned at materialize-lifecycle; a sibling body is untouched"
    (let [w (ecs/empty-world)
          [w a] (ecs/spawn w)
          [w b] (ecs/spawn w)
          w (-> w
                (ecs/put-components a {c/matter-state :planetesimal c/mass 1.0e20
                                       c/position (sp/vec3 0.0 0.0 0.0)})
                (ecs/put-components b {c/matter-state :planetesimal c/mass 1.0e20
                                       c/position (sp/vec3 1.0 0.0 0.0)})
                (ecs/put-component a c/consumed-demote true))
          w' (genesis/materialize-lifecycle w)]
      (is (not (ecs/alive? w' a)) "consumed-demote body is reaped")
      (is (ecs/alive? w' b) "sibling body is untouched")
      (is (= 1.0e20 (ecs/get-component w' b c/mass))
          "reaping does not perturb the untouched body's components"))))
