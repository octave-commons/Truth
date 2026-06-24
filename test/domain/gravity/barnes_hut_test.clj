(ns domain.gravity.barnes-hut-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [shape.spatial :as spatial]
    [domain.gravity.barnes-hut :as bh]))

(def ^:const G 6.67408e-11)

(deftest bh-single-body
  (let [b    (spatial/->body
               {:id 1 :mass 1.0 :radius 1.0 :kind :body/test
                :position (spatial/vec3 1.0 2.0 3.0)
                :velocity (spatial/vec3 0.0 0.0 0.0)})
        tree (bh/build-tree [b])]
    (is (= :leaf (:type tree)))
    (is (= 1.0 (:mass tree)))
    (is (= [1.0 2.0 3.0] (:com tree)))))

(deftest bh-two-bodies
  (let [b1   (spatial/->body
               {:id 1 :mass 2.0 :radius 1.0 :kind :body/test
                :position (spatial/vec3 0.0 0.0 0.0)
                :velocity (spatial/vec3 0.0 0.0 0.0)})
        b2   (spatial/->body
               {:id 2 :mass 3.0 :radius 1.0 :kind :body/test
                :position (spatial/vec3 1.0 0.0 0.0)
                :velocity (spatial/vec3 0.0 0.0 0.0)})
        tree (bh/build-tree [b1 b2])]
    (is (= :internal (:type tree)))
    (is (= 5.0 (double (:mass tree))))
    (let [[cx _ _] (:com tree)]
      (is (< (Math/abs (- cx 0.6)) 1e-9)))))

(deftest single-body-force
  (testing "With one other body, BH force matches direct Newtonian force"
    (let [sun   (spatial/->body
                  {:id 1 :mass 1.0e6 :radius 1.0 :kind :body/star
                   :position (spatial/vec3 0.0 0.0 0.0)
                   :velocity (spatial/vec3 0.0 0.0 0.0)})
          earth (spatial/->body
                  {:id 2 :mass 1.0 :radius 1.0 :kind :body/planet
                   :position (spatial/vec3 10.0 0.0 0.0)
                   :velocity (spatial/vec3 0.0 0.0 0.0)})
          tree  (bh/build-tree [sun earth])
          θ     0.1
          acc   (bh/acceleration G θ tree earth)
          r     10.0
          a-mag (/ (* G (:mass sun)) (* r r))
          expected (spatial/vec3 (- a-mag) 0.0 0.0)]
      (is (<= (spatial/dist acc expected) 1.0e-9)))))

(deftest symmetric-cancelation
  (testing "Equal masses symmetrically arranged around a center have ~0 net acceleration"
    (let [b1 (spatial/->body {:id 1 :mass 1.0 :radius 1.0 :kind :body/test
                                :position (spatial/vec3 -1.0 0.0 0.0)
                                :velocity (spatial/vec3 0.0 0.0 0.0)})
          b2 (spatial/->body {:id 2 :mass 1.0 :radius 1.0 :kind :body/test
                                :position (spatial/vec3  1.0 0.0 0.0)
                                :velocity (spatial/vec3 0.0 0.0 0.0)})
          center (spatial/->body {:id 3 :mass 1.0 :radius 1.0 :kind :body/test
                                  :position (spatial/vec3 0.0 0.0 0.0)
                                  :velocity (spatial/vec3 0.0 0.0 0.0)})
          tree (bh/build-tree [b1 b2 center])
          θ   0.5
          acc (bh/acceleration G θ tree center)]
      (is (<= (spatial/len acc) 1.0e-6)))))
