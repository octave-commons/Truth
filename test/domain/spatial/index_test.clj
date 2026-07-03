(ns domain.spatial.index-test
  "Coverage tests for the spatial neighbour index."
  (:require
   [clojure.test :refer [deftest is testing]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.gravity.barnes-hut :as bh]
   [domain.spatial.index :as spi]
   [shape.spatial :as sp]))

(defn- item [id pos & {:as extra}]
  (merge {:id id :position (apply sp/vec3 pos) :mass 1.0 :radius 1.0} extra))

(deftest build-returns-tree
  (let [tree (spi/build [(item 0 [0 0 0]) (item 1 [10 0 0])])]
    (is (some? tree))
    (is (bh/internal-node? tree))))

(deftest build-leaf-for-single-body
  (let [tree (spi/build [(item 0 [0 0 0])])]
    (is (bh/leaf-node? tree))))

(deftest build-nil-for-empty
  (is (nil? (spi/build []))))

(deftest within-radius-finds-nearby
  (let [items [(item 0 [0 0 0]) (item 1 [1 0 0]) (item 2 [10 0 0])]
        tree (spi/build items)
        found (spi/within-radius tree [0 0 0] 2.0)]
    (is (= 2 (count found)))
    (is (= #{0 1} (set (map :id found))))))

(deftest within-radius-respects-predicate
  (let [items [(item 0 [0 0 0] :matter-state :nebula)
               (item 1 [1 0 0] :matter-state :star)
               (item 2 [10 0 0] :matter-state :nebula)]
        tree (spi/build items)
        found (spi/within-radius tree [0 0 0] 2.0 #(= :nebula (:matter-state %)))]
    (is (= 1 (count found)))
    (is (zero? (:id (first found))))))

(deftest within-radius-empty-tree
  (is (empty? (spi/within-radius nil [0 0 0] 1.0))))

(deftest nearest-dist-finds-closest-other
  (let [items [(item 0 [0 0 0]) (item 1 [3 0 0]) (item 2 [10 0 0])]
        tree (spi/build items)
        d (spi/nearest-dist tree [0 0 0] 0)]
    (is (< (Math/abs (- d 3.0)) 1e-9))))

(deftest nearest-dist-inf-when-alone
  (let [tree (spi/build [(item 0 [0 0 0])])]
    (is (= Double/POSITIVE_INFINITY (spi/nearest-dist tree [0 0 0] 0)))))

(deftest spatial-index-puts-tree-and-grid-on-world
  (let [[w e0] (ecs/spawn (ecs/empty-world))
        [w e1] (ecs/spawn w)
        w (-> w
              (ecs/put-components e0 {c/position (sp/vec3 0 0 0) c/mass 1.0 c/radius 1.0})
              (ecs/put-components e1 {c/position (sp/vec3 10 0 0) c/mass 1.0 c/radius 1.0}))
        w' (spi/spatial-index w)]
    (is (some? (:genesis/spatial-tree w')))
    (is (some? (:genesis/spatial-grid w')))
    (is (= 2 (count (:genesis/spatial-items w'))))))

(deftest spatial-index-skips-entities-without-radius
  (let [[w e] (ecs/spawn (ecs/empty-world))
        w (ecs/put-components w e {c/position (sp/vec3 0 0 0) c/mass 1.0})
        w' (spi/spatial-index w)]
    (is (nil? (:genesis/spatial-tree w')))
    (is (empty? (:genesis/spatial-items w')))))

(deftest query-within-radius-uses-grid-when-present
  (let [grid (spi/build-grid [(item 0 [0 0 0]) (item 1 [1 0 0]) (item 2 [10 0 0])] 2.0)
        w {:genesis/spatial-grid grid}
        found (spi/query-within-radius w [0 0 0] 2.0)]
    (is (= 2 (count found)))))

(deftest query-within-radius-falls-back-to-tree
  (let [items [(item 0 [0 0 0]) (item 1 [1 0 0])]
        w {:genesis/spatial-tree (spi/build items)}
        found (spi/query-within-radius w [0 0 0] 2.0)]
    (is (= 2 (count found)))))

(deftest query-nearest-dist-prefers-tree
  (let [items [(item 0 [0 0 0]) (item 1 [3 0 0])]
        w {:genesis/spatial-tree (spi/build items)}
        d (spi/query-nearest-dist w [0 0 0] 0)]
    (is (< (Math/abs (- d 3.0)) 1e-9))))

(deftest query-nearest-dist-uses-grid-when-no-tree
  (let [grid (spi/build-grid [(item 0 [0 0 0]) (item 1 [4 0 0])] 2.0)
        w {:genesis/spatial-grid grid}
        d (spi/query-nearest-dist w [0 0 0] 0)]
    (is (< (Math/abs (- d 4.0)) 1e-9))))

(deftest query-nearest-dist-inf-when-no-index
  (is (= Double/POSITIVE_INFINITY (spi/query-nearest-dist {} [0 0 0] 0))))

(deftest build-grid-empty
  (let [grid (spi/build-grid [] 1.0)]
    (is (empty? (:cells grid)))
    (is (empty? (:items grid)))))

(deftest grid-within-radius-empty
  (is (empty? (spi/grid-within-radius (spi/build-grid [] 1.0) [0 0 0] 1.0))))

(deftest grid-nearest-dist-inf-when-alone
  (let [grid (spi/build-grid [(item 0 [0 0 0])] 1.0)]
    (is (= Double/POSITIVE_INFINITY (spi/grid-nearest-dist grid [0 0 0] 0)))))
