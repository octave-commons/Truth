(ns law.seed-contract-test
  "μ for the boot-time contract guard: law.stellar/matter-state-contract is now
   load-bearing — the world bootstrap folds every seeded body through a
   law.registry and fails fast on a malformed seed. See AGENTS.md › cross-boundary
   validators and docs/notes/specs/…-002-μ0-shapes-claims-contracts.md."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.phase0    :as phase0]
   [domain.ecs.core   :as ecs]
   [domain.ecs.components :as c]
   [law.stellar      :as law]
   [law.registry     :as lreg]
   [shape.spatial    :as sp]))

(def ^:private valid-body
  {:id 0 :position (sp/vec3 0.0 0.0 0.0) :velocity (sp/vec3 0.0 0.0 0.0)
   :mass 1.0e30 :radius 1.0e9 :temperature 1.0e4 :density 1.0e3
   :composition {:H 0.7 :He 0.3} :state :star :luminosity 0.0 :pressure 1.0e12})

(deftest registered-contract-accepts-valid-and-rejects-malformed
  (let [reg (lreg/->registry law/matter-state-contract)]
    (testing "a well-formed matter-state body is admitted"
      (is (some? (lreg/add reg valid-body))))
    (testing "negative mass (fails pos?) is rejected"
      (is (thrown? clojure.lang.ExceptionInfo
                   (lreg/add reg (assoc valid-body :mass -1.0)))))
    (testing "a missing required key is rejected"
      (is (thrown? clojure.lang.ExceptionInfo
                   (lreg/add reg (dissoc valid-body :density)))))
    (testing "an extra key is rejected (it's a :type contract)"
      (is (thrown? clojure.lang.ExceptionInfo
                   (lreg/add reg (assoc valid-body :rogue 1)))))
    (testing "integer entity ids are accepted (not just UUIDs)"
      (is (some? (lreg/add reg (assoc valid-body :id 42)))))))

(deftest bootstrap-passes-on-real-seed
  (testing "a freshly seeded world satisfies the matter-state contract (no throw)"
    (is (some? (phase0/create-world {:gas-count 40})))))

(deftest bootstrap-rejects-an-injected-bad-body
  (testing "a seed body with a non-positive temperature fails the boot guard"
    (let [w     (phase0/create-world {:gas-count 20})
          [w b] (ecs/spawn w)
          bad   (ecs/put-components w b
                  {c/position (sp/vec3 0.0 0.0 0.0) c/velocity (sp/vec3 0.0 0.0 0.0)
                   c/mass 1.0e28 c/radius 1.0e12 c/temperature -5.0 c/density 1.0e-15
                   c/composition {:H 1.0} c/matter-state :nebula c/pressure 0.0})]
      (is (thrown? clojure.lang.ExceptionInfo (phase0/assert-seed-contracts! bad))))))

(deftest guard-can-be-disabled
  (testing ":phase0/validate-seed? false skips the assertion"
    (let [w     (phase0/create-world {:gas-count 20})
          [w b] (ecs/spawn w)
          bad   (-> (ecs/put-components w b
                      {c/position (sp/vec3 0.0 0.0 0.0) c/velocity (sp/vec3 0.0 0.0 0.0)
                       c/mass 1.0e28 c/radius 1.0e12 c/temperature -5.0 c/density 1.0e-15
                       c/composition {:H 1.0} c/matter-state :nebula c/pressure 0.0})
                    (assoc :phase0/validate-seed? false))]
      (is (some? (phase0/assert-seed-contracts! bad))))))
