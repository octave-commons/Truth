(ns infra.render.asset-test
  "Tests for `infra.render.asset` cache/lifecycle logic. Cache hit/miss and
   disposal are pure map operations tested directly; the GL teardown calls
   are stubbed via `with-redefs` on the namespace's own delete seams so the
   test suite stays headless (no live OpenGL context)."
  (:require
   [clojure.test :refer [deftest testing is]]
   [infra.render.asset :as asset]
   [infra.render.shader :as shader]))

(deftest test-mesh-cache-hit-miss
  (testing "mesh! builds once, then returns the cached entry on a hit"
    (reset! asset/mesh-cache {})
    (let [builds (atom 0)
          build! (fn [] (swap! builds inc) {:vao 1 :vbo 2 :count 3})
          first-entry  (asset/mesh! :sphere-3 build!)
          second-entry (asset/mesh! :sphere-3 build!)]
      (is (= 1 @builds) "the builder only runs on the first (miss) call")
      (is (= first-entry second-entry))
      (is (= {:vao 1 :vbo 2 :count 3} first-entry))))
  (testing "invalidate-meshes! clears the cache so the next call rebuilds"
    (reset! asset/mesh-cache {:sphere-3 {:vao 1 :vbo 2 :count 3}})
    (with-redefs [asset/delete-mesh-gl! (fn [_] nil)]
      (asset/invalidate-meshes!))
    (is (= {} @asset/mesh-cache))
    (let [builds (atom 0)]
      (asset/mesh! :sphere-3 (fn [] (swap! builds inc) {:vao 9 :vbo 9 :count 9}))
      (is (= 1 @builds) "a cleared key rebuilds on next use"))))

(deftest test-texture-cache-hit-miss
  (testing "texture! builds once, then returns the cached entry on a hit"
    (reset! asset/texture-cache {})
    (let [builds (atom 0)
          build! (fn [] (swap! builds inc) {:id 5 :width 32 :height 32})
          first-entry  (asset/texture! :froxel-32 build!)
          second-entry (asset/texture! :froxel-32 build!)]
      (is (= 1 @builds))
      (is (= first-entry second-entry)))))

(deftest test-program-entry-reads-shader-cache
  (testing "asset/program-id and program-entry read infra.render.shader's cache"
    (reset! shader/program-cache {})
    (swap! shader/program-cache assoc :body {:id 42 :hash 123})
    (is (= 42 (asset/program-id :body)))
    (is (= {:id 42 :hash 123} (asset/program-entry :body)))
    (is (nil? (asset/program-id :missing)))))

(deftest test-dispose-asset-single-entry
  (testing "dispose-asset! removes exactly the named mesh/texture, GL calls stubbed"
    (reset! asset/mesh-cache {:a {:vao 1 :count 1} :b {:vao 2 :count 1}})
    (with-redefs [asset/delete-mesh-gl! (fn [_] nil)]
      (asset/dispose-asset! :mesh :a))
    (is (= {:b {:vao 2 :count 1}} @asset/mesh-cache))))

(deftest test-dispose-all-clears-every-cache
  (testing "dispose-all! clears mesh, texture, and program caches"
    (reset! asset/mesh-cache {:a {:vao 1 :count 1}})
    (reset! asset/texture-cache {:t {:id 7 :width 4 :height 4}})
    (reset! shader/program-cache {})
    (with-redefs [asset/delete-mesh-gl! (fn [_] nil)
                  asset/delete-texture-gl! (fn [_] nil)]
      (asset/dispose-all!))
    (is (= {} @asset/mesh-cache))
    (is (= {} @asset/texture-cache))
    (is (= {} @shader/program-cache))))
