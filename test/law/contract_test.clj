(ns law.contract-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [shape.core :as shape]
   [law.contract :as contract]))

(defn demo-position-shape []
  (shape/->shape
   {:id          (shape/new-shape-id)
    :kind        :state
    :form        {:x int? :y int?}
    :name        "position"
    :description "2D integer position"}))

(deftest contract-validates-claim
  (testing "Contract validates values for its governed Shape"
    (let [shape      (demo-position-shape)
          c          (contract/->contract
                      {:id         (shape/new-resource-id)
                       :shape-id   (:id shape)
                       :kind       :type
                       :schema     {:x int? :y int?}
                       :name       "position-type"
                       :description "position must have integral x and y"})
          good-claim (shape/->claim
                      {:id          (shape/new-claim-id)
                       :shape-id    (:id shape)
                       :value       {:x 0 :y -5}
                       :context     {:tick 1}
                       :asserted-by :system/physics})
          bad-claim  (shape/->claim
                      {:id          (shape/new-claim-id)
                       :shape-id    (:id shape)
                       :value       {:x 0.5 :y -5}
                       :context     {:tick 1}
                       :asserted-by :system/physics})]
      (is (= ::contract/ok
             (contract/validate c good-claim)))
      (let [res (contract/validate c bad-claim)]
        (is (= ::contract/violation (:result res)))
        (is (= (:id bad-claim)
               (get-in res [:claim :id])))))))

(deftest type-vs-quality-contracts
  (testing "Type contracts forbid extra keys, quality contracts allow them"
    (let [shape-id  (shape/new-shape-id)
          type-c    (contract/->contract
                     {:id       (shape/new-resource-id)
                      :shape-id shape-id
                      :kind     :type
                      :schema   {:mass number?}
                      :name     "mass-type"})
          quality-c (contract/->contract
                     {:id       (shape/new-resource-id)
                      :shape-id shape-id
                      :kind     :quality
                      :schema   {:mass (fn [m] (and (number? m) (pos? m)))}
                      :name     "positive-mass"})
          bare      (shape/->claim
                     {:id          (shape/new-claim-id)
                      :shape-id    shape-id
                      :value       {:mass 10.0}
                      :context     {:tick 1}
                      :asserted-by :system/physics})
          extended  (shape/->claim
                     {:id          (shape/new-claim-id)
                      :shape-id    shape-id
                      :value       {:mass 10.0 :debug "extra"}
                      :context     {:tick 1}
                      :asserted-by :system/physics})]
      (is (= ::contract/ok (contract/validate type-c bare)))
      (is (= ::contract/ok (contract/validate quality-c bare)))
      (is (= ::contract/violation (:result (contract/validate type-c extended))))
      (is (= ::contract/ok (contract/validate quality-c extended))))))
