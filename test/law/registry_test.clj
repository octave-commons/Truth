(ns law.registry-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [shape.core :as shape]
   [law.contract :as contract]
   [law.registry :as registry]))

(defn demo-resource-contract []
  (let [shape (shape/->shape
               {:id          (shape/new-shape-id)
                :kind        :state
                :form        {:id int?}
                :name        "simple-resource"
                :description "demo"})]
    (contract/->contract
     {:id       (shape/new-resource-id)
      :shape-id (:id shape)
      :kind     :type
      :schema   {:id int?}
      :name     "simple-resource-type"})))

(deftest registry-enforces-contract
  (testing "Only resources that pass the registry's contract can be added"
    (let [ct    (demo-resource-contract)
          reg0  (registry/->registry ct)
          reg1  (registry/add reg0 {:id 1})]
      (is (= ct (:resource-contract reg1)))
      (is (= {:id 1} (registry/get-by-id reg1 1)))
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"contract.*violation"
           (registry/add reg1 {:id "not-int"}))))))

(deftest registry-index-consistency
  (testing "Index mirrors the items vector and can be recomputed"
    (let [ct      (demo-resource-contract)
          reg     (-> (registry/->registry ct)
                      (registry/add {:id 1})
                      (registry/add {:id 2})
                      (registry/add {:id 3}))
          rebuilt (registry/rebuild-index reg)]
      (is (= (:index reg) (:index rebuilt)))
      (is (= 3 (count (:items reg))))
      (is (= {:id 2} (registry/get-by-id reg 2))))))

(deftest registry-rejects-duplicate-ids
  (testing "Adding a resource with an existing id fails fast"
    (let [ct  (demo-resource-contract)
          reg (-> (registry/->registry ct)
                  (registry/add {:id 1}))]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"duplicate.*id"
           (registry/add reg {:id 1}))))))
