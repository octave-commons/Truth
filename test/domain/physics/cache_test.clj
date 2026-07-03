(ns domain.physics.cache-test
  "Tests for the transient hydro/EM shared neighbor cache."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.physics.cache :as cache]
   [domain.spatial.index :as spatial]
   [domain.stellar :as stellar]
   [law.field :as lfield]
   [shape.spatial :as sp]))

(defn- seeded-world
  ([] (seeded-world 20))
  ([n]
   (let [base (ecs/empty-world)]
     (reduce (fn [w i]
               (first (stellar/spawn-clump
                       w {:position [(double (* i 1e14)) 0.0 0.0]
                          :velocity [0.0 0.0 0.0]
                          :mass 1e28
                          :radius 2e14
                          :matter-state :nebula
                          :density 1e-18
                          :pressure 1e-13
                          :temperature 12.0
                          :b-field [0.0 0.0 1.0e-9]})))
             base (range n)))))

(deftest test-cache-built-for-active-particles
  (testing "The cache contains an entry for every hydro/EM-active particle"
    (let [w (-> (seeded-world 50) spatial/spatial-index cache/build-neighbor-cache)
          cache (:genesis/neighbor-cache w)
          active-eids (filterv #(lfield/hydro-em-active?
                                 (ecs/get-component w % c/matter-state))
                               (ecs/entities-with w c/matter-state c/position c/radius c/mass))]
      (is (map? cache))
      (is (pos? (count cache)))
      (is (every? #(contains? cache %) active-eids))
      (is (every? cache/neighbor-cache-entry? (vals cache))))))

(deftest test-cache-neighbors-include-self
  (testing "A particle is its own nearest neighbor and appears in its cache"
    (let [w (-> (seeded-world 10) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (:genesis/neighbor-cache w)))
          entry (get-in w [:genesis/neighbor-cache eid])]
      (is (some #(= (:id %) eid) (:neighbors entry))))))

(deftest test-cache-gradients-are-finite
  (testing "All cached gradients are finite 3-vectors"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)]
      (doseq [entry (vals (:genesis/neighbor-cache w))
              grad  (concat (:gradients entry) (:curl-gradients entry))]
        (is (vector? grad))
        (is (= 3 (count grad)))
        (is (every? #(and (number? %) (Double/isFinite (double %))) grad))))))

(deftest test-cache-h-positive
  (testing "Every smoothing length is a positive finite number"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)]
      (doseq [entry (vals (:genesis/neighbor-cache w))]
        (is (pos? (:h entry)))
        (is (Double/isFinite (double (:h entry))))))))

(deftest test-cache-matches-spatial-query
  (testing "Cache neighbors are exactly the grid neighbors within the cache radius"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (:genesis/neighbor-cache w)))
          entry (get-in w [:genesis/neighbor-cache eid])
          pos (:position entry)
          r (:radius entry)
          query-r (max (:h entry) (* 2.0 r))
          grid (:genesis/spatial-grid w)
          expected (set (map :id (spatial/grid-within-radius grid pos query-r (constantly true))))
          actual (set (map :id (:neighbors entry)))]
      (is (= expected actual)))))
