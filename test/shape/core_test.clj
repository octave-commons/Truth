(ns shape.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [shape.core :as shape]))

(deftest shape-is-pure-description
  (testing "Shape describes form and meaning, not validity"
    (let [s (shape/->shape
             {:id          (shape/new-shape-id)
              :kind        :state
              :form        {:x int? :y int?}
              :name        "position"
              :description "2D integer position in world space"})]
      (is (= :state (:kind s)))
      (is (= "position" (:name s)))
      (is (map? (:form s)))
      (is (nil? (:valid? s))))))

(deftest claim-binds-shape-to-value
  (testing "Claim pairs a Shape with a concrete value and context"
    (let [shape-id (shape/new-shape-id)
          claim    (shape/->claim
                    {:id          (shape/new-claim-id)
                     :shape-id    shape-id
                     :value       {:x 10 :y 20}
                     :context     {:tick 42
                                   :entity-id 1001}
                     :asserted-by :system/physics})]
      (is (= shape-id (:shape-id claim)))
      (is (= {:x 10 :y 20} (:value claim)))
      (is (= 42 (get-in claim [:context :tick])))
      (is (= :system/physics (:asserted-by claim))))))
