(ns domain.physics.cache-test
  "Tests for the persistent hydro/EM shared neighbor cache."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.tick :as tick]
   [domain.physics.cache :as cache]
   [domain.spatial.index :as spatial]
   [domain.stellar :as stellar]
   [law.field :as lfield]))

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

(defn- cache-map
  "Return {eid entry} from the current `c/neighbor-cache` components."
  [world]
  (get-in world [:components c/neighbor-cache] {}))

(defn- apply-cache-write-set
  "Apply a write-set from `rebuild-neighbor-cache` to `world`."
  [world write-set]
  (reduce-kv (fn [w eid entry]
               (if (tick/removed? entry)
                 (ecs/remove-component w eid c/neighbor-cache)
                 (ecs/put-component w eid c/neighbor-cache entry)))
             world
             (get write-set c/neighbor-cache {})))

(defn- rebuild-cache
  "Build or refresh the cache components on `world` at `tick`."
  [world tick]
  (apply-cache-write-set world (cache/rebuild-neighbor-cache world tick)))

(deftest test-cache-built-for-active-particles
  (testing "The cache contains an entry for every hydro/EM-active particle"
    (let [w (-> (seeded-world 50) spatial/spatial-index cache/build-neighbor-cache)
          cache (cache-map w)
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
          eid (first (keys (cache-map w)))
          entry (ecs/get-component w eid c/neighbor-cache)]
      (is (some #(= (:id %) eid) (:neighbors entry))))))

(deftest test-cache-gradients-are-finite
  (testing "All cached gradients are finite 3-vectors"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)]
      (doseq [entry (vals (cache-map w))
              grad  (concat (:gradients entry) (:curl-gradients entry))]
        (is (vector? grad))
        (is (= 3 (count grad)))
        (is (every? #(and (number? %) (Double/isFinite (double %))) grad))))))

(deftest test-cache-h-positive
  (testing "Every smoothing length is a positive finite number"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)]
      (doseq [entry (vals (cache-map w))]
        (is (pos? (:h entry)))
        (is (Double/isFinite (double (:h entry))))))))

(deftest test-cache-matches-spatial-query
  (testing "Cache neighbors are exactly the grid neighbors within the cache radius"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          entry (ecs/get-component w eid c/neighbor-cache)
          pos (:position entry)
          query-r (:query-r entry)
          grid (:genesis/spatial-grid w)
          expected (set (map :id (spatial/grid-within-radius grid pos query-r (constantly true))))
          actual (set (map :id (:neighbors entry)))]
      (is (= expected actual)))))

(deftest test-cache-entry-reused-below-tolerance
  (testing "The neighbor set is reused when the entity moves less than displacement-tolerance * h"
    (let [w (-> (seeded-world 20) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          entry (ecs/get-component w eid c/neighbor-cache)
          h (:h entry)
          anchor (:anchor-position entry)
          delta (* 0.5 cache/displacement-tolerance h)
          [x y z] (ecs/get-component w eid c/position)
          w2 (-> w
                 (ecs/put-component eid c/position [(+ x delta) y z])
                 spatial/spatial-index
                 (rebuild-cache 1))
          entry2 (ecs/get-component w2 eid c/neighbor-cache)
          old-ids (set (map :id (:neighbors entry)))
          new-ids (set (map :id (:neighbors entry2)))]
      (is (= old-ids new-ids))
      (is (= anchor (:anchor-position entry2))
          "reused entry keeps the anchor of the last real query")
      (is (= [(+ x delta) y z] (:position entry2))
          "reused entry reads the current position"))))

(deftest test-cache-entry-rebuilt-above-tolerance
  (testing "A cache entry is requeried when the entity moves more than displacement-tolerance * h"
    (let [w (-> (seeded-world 20) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          entry (ecs/get-component w eid c/neighbor-cache)
          h (:h entry)
          delta (* 2.0 cache/displacement-tolerance h)
          [x y z] (ecs/get-component w eid c/position)
          moved [(+ x delta) y z]
          w2 (-> w
                 (ecs/put-component eid c/position moved)
                 spatial/spatial-index
                 (rebuild-cache 1))
          entry2 (ecs/get-component w2 eid c/neighbor-cache)]
      (is (= moved (:anchor-position entry2))
          "rebuilt entry re-anchors at the fresh query position"))))

(deftest test-cache-drift-accumulates-against-anchor
  (testing "Displacement is measured from the last spatial query, not the last tick"
    (let [w (-> (seeded-world 20) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          entry (ecs/get-component w eid c/neighbor-cache)
          h (:h entry)
          anchor (:anchor-position entry)
          ;; Each step is below tolerance, but the two together exceed it —
          ;; the second rebuild call must requery.
          step (* 0.6 cache/displacement-tolerance h)
          [x y z] (ecs/get-component w eid c/position)
          w2 (-> w
                 (ecs/put-component eid c/position [(+ x step) y z])
                 spatial/spatial-index
                 (rebuild-cache 1))
          w3 (-> w2
                 (ecs/put-component eid c/position [(+ x step step) y z])
                 spatial/spatial-index
                 (rebuild-cache 2))]
      (is (= anchor (get-in w2 [:components c/neighbor-cache eid :anchor-position]))
          "first sub-tolerance move reuses the entry")
      (is (= [(+ x step step) y z]
             (get-in w3 [:components c/neighbor-cache eid :anchor-position]))
          "accumulated drift past tolerance forces a requery"))))

(deftest test-reused-entry-refreshes-neighbor-fields
  (testing "A reused entry reads neighbor field data from the current snapshot"
    (let [w (-> (seeded-world 20) spatial/spatial-index cache/build-neighbor-cache)
          cache0 (cache-map w)
          ;; Find an entry with a neighbor other than itself.
          [eid entry] (first (filter (fn [[k v]]
                                       (some #(not= (:id %) k) (:neighbors v)))
                                     cache0))
          nbr-id (:id (first (filter #(not= (:id %) eid) (:neighbors entry))))
          new-density 4.2e-3
          w2 (-> w
                 (ecs/put-component nbr-id c/density new-density)
                 spatial/spatial-index
                 (rebuild-cache 1))
          refreshed (->> (ecs/get-component w2 eid c/neighbor-cache)
                         :neighbors
                         (filter #(= (:id %) nbr-id))
                         first)]
      (is (some? refreshed))
      (is (= new-density (:density refreshed))
          "the cached neighbor map carries the neighbor's current density"))))

(deftest test-cache-entry-evicted-when-inactive
  (testing "A cache entry is evicted when the entity is no longer hydro/EM-active"
    (let [w (-> (seeded-world 20) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          w2 (-> w
                 (ecs/put-component eid c/matter-state :star)
                 spatial/spatial-index
                 (rebuild-cache 1))]
      (is (not (contains? (cache-map w2) eid))))))

(deftest test-forced-full-rebuild-on-interval
  (testing "A cache entry is rebuilt when tick is a multiple of the rebuild interval"
    (let [w (-> (seeded-world 20) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          h (get-in w [:components c/neighbor-cache eid :h])
          delta (* 0.5 cache/displacement-tolerance h)
          [x y z] (ecs/get-component w eid c/position)
          moved [(+ x delta) y z]
          w2 (-> w
                 (ecs/put-component eid c/position moved)
                 spatial/spatial-index
                 (rebuild-cache 10))]
      (is (= moved (get-in w2 [:components c/neighbor-cache eid :anchor-position]))
          "forced interval rebuild re-anchors at the fresh query position"))))

(deftest test-full-rebuild-matches-initial-rebuild
  (testing "Full-rebuild mode produces the same cache as a fresh rebuild on tick 0"
    (let [w (-> (seeded-world 20) spatial/spatial-index)
          fresh-cache (cache-map (cache/build-neighbor-cache w))
          rebuilt-cache (cache-map (rebuild-cache w 0))]
      (is (= fresh-cache rebuilt-cache)))))
