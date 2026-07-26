(ns domain.physics.cache-test
  "Tests for the persistent hydro/EM shared neighbor cache."
  (:require
   [clojure.test :refer [deftest testing is]]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.tick :as tick]
   [domain.hydro :as hydro]
   [domain.physics.cache :as cache]
   [domain.spatial.index :as spatial]
   [domain.stellar.seeder :as seeder]
   [law.field :as lfield]))

(defn- seeded-world
  ([] (seeded-world 20))
  ([n]
   (let [base (ecs/empty-world)]
     (reduce (fn [w i]
               (first (seeder/spawn-clump
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

(deftest test-cache-entry-slim
  (testing "The cache no longer stores precomputed gradients"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)]
      (doseq [entry (vals (cache-map w))]
        (is (not (contains? entry :gradients)))
        (is (not (contains? entry :curl-gradients)))))))

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
    (let [w (-> (seeded-world 30) spatial/spatial-index)
          fresh-cache (cache-map (cache/build-neighbor-cache w))
          rebuilt-cache (cache-map (rebuild-cache w 0))]
      (is (= fresh-cache rebuilt-cache)))))

;; --- Shared pair walk: pair terms + staleness-budgeted density estimate -----

(deftest test-shared-walk-pair-gradients
  (testing "In-kernel hydro/EM neighbors carry the precomputed pair gradient"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)
          entries (vals (cache-map w))]
      (doseq [entry entries]
        (let [r-c (:radius entry)]
          (doseq [n (:neighbors entry)]
            (let [h (+ r-c (double (or (:radius n) 1.0)))
                  in-kernel? (and (< (double (:r2 n)) (* h h))
                                  (lfield/hydro-em-active? (:matter-state n))
                                  (:density n) (:pressure n) (:mass n))]
              (is (= (boolean in-kernel?) (boolean (:grad n)))
                  "grad present exactly for in-kernel field-carrying pairs")
              (when (:grad n)
                (is (= (:grad n)
                       (let [pos-c (:position entry)
                             pos-n (:position n)
                             rx (- (double (nth pos-c 0)) (double (nth pos-n 0)))
                             ry (- (double (nth pos-c 1)) (double (nth pos-n 1)))
                             rz (- (double (nth pos-c 2)) (double (nth pos-n 2)))]
                         (hydro/kernel-gradient
                          [rx ry rz] (double (:r2 n)) h)))
                    "stored grad is bit-equal to an on-demand evaluation")))))))))

;; Intentional: `(= 0 x)`, not `(zero? x)`. These assert on a map lookup that
;; yields nil for a missing key: `(zero? nil)` THROWS, and `(zero? 0.0)` is true
;; where `(= 0 0.0)` is false. `(= 0 x)` is the stronger assertion — the key
;; exists AND holds integer zero — so the rewrite would weaken the test.
#_{:splint/disable [style/eq-zero]}
(deftest test-density-estimate-fresh-on-build
  (testing "A fresh build's density estimate is bit-equal to sph-density-from-cache"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)]
      (doseq [[eid entry] (cache-map w)]
        (when (= :nebula (:state entry))
          (is (some? (:density-estimate entry)))
          (is (= (:position entry) (:density-anchor entry)))
          (is (= 0 (:density-tick entry)))
          (is (= (:density-estimate entry)
                 (hydro/sph-density-from-cache (:neighbors entry) (:h entry)))
              (str "estimate matches the consumer-side SPH sum for " eid)))))))

;; Intentional: `(= 0 x)`, not `(zero? x)`. These assert on a map lookup that
;; yields nil for a missing key: `(zero? nil)` THROWS, and `(zero? 0.0)` is true
;; where `(= 0 0.0)` is false. `(= 0 x)` is the stronger assertion — the key
;; exists AND holds integer zero — so the rewrite would weaken the test.
#_{:splint/disable [style/eq-zero]}
(deftest test-density-estimate-carried-when-quiet
  (testing "An unmoved parcel's estimate is carried forward within budget"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          entry0 (ecs/get-component w eid c/neighbor-cache)
          w2 (-> w spatial/spatial-index (rebuild-cache 1))
          entry1 (ecs/get-component w2 eid c/neighbor-cache)]
      (is (= (:density-estimate entry0) (:density-estimate entry1))
          "same estimate double, not recomputed")
      (is (= (:density-anchor entry0) (:density-anchor entry1)))
      (is (= 0 (:density-tick entry1))
          "estimate age advances, not the stamp"))))

(deftest test-density-estimate-recomputed-on-displacement
  (testing "Drift past fraction*h from the density anchor forces a recompute,
            even when the neighbor-set skin (0.1*h) is not violated"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          entry0 (ecs/get-component w eid c/neighbor-cache)
          h (:h entry0)
          ;; Between the density budget (0.05*h) and the identity skin (0.1*h):
          ;; the entry refreshes (no fresh query) but the estimate is due.
          delta (* 0.075 h)
          [x y z] (ecs/get-component w eid c/position)
          moved [(+ x delta) y z]
          w2 (-> w
                 (ecs/put-component eid c/position moved)
                 spatial/spatial-index
                 (rebuild-cache 1))
          entry1 (ecs/get-component w2 eid c/neighbor-cache)]
      (is (= (:anchor-position entry0) (:anchor-position entry1))
          "neighbor identities reused (sub-skin move)")
      (is (= moved (:density-anchor entry1))
          "density re-anchored at the recompute position")
      (is (= 1 (:density-tick entry1))))))

(deftest test-density-estimate-max-ticks-cap
  (testing "An unmoved parcel's estimate is recomputed at the max-ticks cap"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          w2 (-> w spatial/spatial-index
                 (rebuild-cache lfield/density-stale-max-ticks))
          entry1 (ecs/get-component w2 eid c/neighbor-cache)]
      (is (= lfield/density-stale-max-ticks (:density-tick entry1))
          "estimate re-stamped at the cap tick"))))

(deftest test-density-estimate-fresh-mode
  (testing "max-ticks 1 (world override) recomputes the estimate every tick"
    (let [w (-> (seeded-world 30)
                (assoc :genesis/density-stale-max-ticks 1)
                spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          w2 (-> w spatial/spatial-index (rebuild-cache 1))
          entry1 (ecs/get-component w2 eid c/neighbor-cache)]
      (is (= 1 (:density-tick entry1))
          "fresh mode re-stamps every tick")
      (is (= (:density-estimate entry1)
             (hydro/sph-density-from-cache (:neighbors entry1) (:h entry1)))
          "fresh estimate matches the consumer-side SPH sum"))))

(defn- move-nearest-neighbor
  "Move `eid`'s cached nearest neighbor (`:nn-id`) along the line toward `eid`
   so their separation becomes `frac-of-dist` of its current value. Used to
   drive the density estimate's h-drift trigger without touching `eid` itself
   (displacement and mass stay quiet)."
  [world eid frac-of-dist]
  (let [entry (ecs/get-component world eid c/neighbor-cache)
        nn-id (:nn-id entry)
        [ax ay az] (ecs/get-component world eid c/position)
        [bx by bz] (ecs/get-component world nn-id c/position)
        f (double frac-of-dist)
        moved [(+ (double ax) (* f (- (double bx) (double ax))))
               (+ (double ay) (* f (- (double by) (double ay))))
               (+ (double az) (* f (- (double bz) (double az))))]]
    (ecs/put-component world nn-id c/position moved)))

(deftest test-density-estimate-mass-drift-trigger
  (testing "Mass drift past the fraction with everything else quiet forces a recompute"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          entry0 (ecs/get-component w eid c/neighbor-cache)
          m0 (double (ecs/get-component w eid c/mass))
          w2 (-> w
                 (ecs/put-component eid c/mass (* 1.10 m0))
                 spatial/spatial-index
                 (rebuild-cache 1))
          entry1 (ecs/get-component w2 eid c/neighbor-cache)]
      (is (= (:anchor-position entry0) (:anchor-position entry1))
          "neighbor identities reused (nothing moved)")
      (is (= 1 (:density-tick entry1))
          "mass drift > 5% re-stamps the estimate")
      (is (= (* 1.10 m0) (:density-m entry1))
          "mass reference updated at the recompute"))))

(deftest test-density-estimate-h-drift-trigger
  (testing "h drift past the fraction with everything else quiet forces a recompute"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          entry0 (ecs/get-component w eid c/neighbor-cache)
          w2 (-> w
                 (move-nearest-neighbor eid 0.89)
                 spatial/spatial-index
                 (rebuild-cache 1))
          entry1 (ecs/get-component w2 eid c/neighbor-cache)]
      (is (= (:anchor-position entry0) (:anchor-position entry1))
          "neighbor identities reused (the central parcel never moved)")
      (is (>= (Math/abs (- (:h entry1) (:density-h entry0)))
              (* 0.05 (:density-h entry0)))
          "h actually moved past the 5% threshold (trigger precondition)")
      (is (= 1 (:density-tick entry1))
          "h drift > 5% re-stamps the estimate")
      (is (= (:h entry1) (:density-h entry1))
          "h reference updated at the recompute"))))

;; Intentional: `(= 0 x)`, not `(zero? x)`. These assert on a map lookup that
;; yields nil for a missing key: `(zero? nil)` THROWS, and `(zero? 0.0)` is true
;; where `(= 0 0.0)` is false. `(= 0 x)` is the stronger assertion — the key
;; exists AND holds integer zero — so the rewrite would weaken the test.
#_{:splint/disable [style/eq-zero]}
(deftest test-density-estimate-quiet-below-thresholds
  (testing "Sub-threshold h and mass drift with all other triggers quiet does NOT recompute"
    (let [w (-> (seeded-world 30) spatial/spatial-index cache/build-neighbor-cache)
          eid (first (keys (cache-map w)))
          entry0 (ecs/get-component w eid c/neighbor-cache)
          m0 (double (ecs/get-component w eid c/mass))
          w2 (-> w
                 (move-nearest-neighbor eid 0.98)
                 (ecs/put-component eid c/mass (* 1.02 m0))
                 spatial/spatial-index
                 (rebuild-cache 1))
          entry1 (ecs/get-component w2 eid c/neighbor-cache)]
      (is (= 0 (:density-tick entry1))
          "estimate age advances, not the stamp")
      (is (= (:density-estimate entry0) (:density-estimate entry1))
          "same estimate double, carried")
      (is (= (:density-h entry0) (:density-h entry1))
          "stale h reference carried")
      (is (= (:density-m entry0) (:density-m entry1))
          "stale mass reference carried"))))
