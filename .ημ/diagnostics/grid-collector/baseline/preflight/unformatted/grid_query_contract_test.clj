(ns domain.spatial.grid-query-contract-test
  "Public radius-query behavior required by order-sensitive physics consumers."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.spatial.index :as spatial]))

(def ^:private scattered-items
  ;; Deliberately neither ID-sorted nor cell-sorted. Two items share one cell.
  [{:id 91 :position [1.25 0.25 0.25] :payload :last-cell}
   {:id 14 :position [-0.75 0.25 0.25] :payload :negative-x}
   {:id 73 :position [0.25 1.25 0.25] :payload :positive-y}
   {:id 26 :position [0.25 0.25 1.25] :payload :positive-z}
   {:id 55 :position [0.5 0.5 0.5] :payload :first-in-cell}
   {:id 19 :position [0.25 0.25 -0.75] :payload :negative-z}
   {:id 88 :position [0.75 0.75 0.75] :payload :second-in-cell}])

(deftest grid-query-preserves-cell-and-bucket-order-and-original-items
  (let [grid (spatial/build-grid scattered-items 1.0)
        found (spatial/grid-within-radius grid [0.0 0.0 0.0] 4.0)
        originals (into {} (map (juxt :id identity)) scattered-items)]
    (is (vector? found))
    (is (= [14 19 55 88 26 73 91] (mapv :id found)))
    (doseq [result found]
      (is (identical? (get originals (:id result)) result)))
    (testing "the world-facing route retains the same ordered result"
      (is (= found (spatial/query-within-radius
                    {:genesis/spatial-grid grid} [0.0 0.0 0.0] 4.0))))))

(deftest grid-query-preserves-predicate-order-and-filtered-order
  (let [visited (atom [])
        found (spatial/grid-within-radius
               (spatial/build-grid scattered-items 1.0) [0.0 0.0 0.0] 4.0
               (fn [item]
                 (swap! visited conj (:id item))
                 (even? (:id item))))]
    (is (= [14 19 55 88 26 73 91] @visited))
    (is (= [14 88 26] (mapv :id found)))))

(deftest grid-query-applies-predicate-before-radius-filter
  (let [visited (atom [])
        grid (spatial/build-grid [{:id :center :position [0.0 0.0 0.0]}
                                  {:id :corner :position [0.9 0.9 0.9]}
                                  {:id :negative-corner :position [-0.9 -0.9 -0.9]}]
                                 1.0)
        found (spatial/grid-within-radius grid [0.0 0.0 0.0] 1.0
                                          (fn [item]
                                            (swap! visited conj (:id item))
                                            true))]
    ;; Both corners are outside the sphere but inside visited cells.
    (is (= [:negative-corner :center :corner] @visited))
    (is (= [:center] (mapv :id found)))))

(deftest grid-query-includes-exact-cutoff-and-self
  (let [grid (spatial/build-grid [{:id :outside :position [1.0000000000000002 0.0 0.0]}
                                  {:id :positive-edge :position [1.0 0.0 0.0]}
                                  {:id :negative-edge :position [-1.0 0.0 0.0]}
                                  {:id :self :position [0.0 0.0 0.0]}]
                                 0.5)]
    (is (= [:negative-edge :self :positive-edge]
           (mapv :id (spatial/grid-within-radius grid [0.0 0.0 0.0] 1.0))))
    (is (= [:self] (mapv :id (spatial/grid-within-radius grid [0.0 0.0 0.0] 0.0))))))

(deftest grid-query-empty-and-disjoint-ranges-do-not-call-predicate
  (doseq [[grid pos radius] [[(spatial/build-grid [] 1.0) [0.0 0.0 0.0] 1.0]
                            [(spatial/build-grid scattered-items 1.0) [100.0 0.0 0.0] 0.5]
                            [(spatial/build-grid scattered-items 1.0) [-100.0 0.0 0.0] 0.5]]]
    (let [visited (atom [])
          found (spatial/grid-within-radius grid pos radius
                                            (fn [item]
                                              (swap! visited conj (:id item))
                                              true))]
      (is (= [] found))
      (is (vector? found))
      (is (empty? @visited)))))
