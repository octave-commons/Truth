(ns infra.render.shader-test
  "Tests for `infra.render.shader` data shapes and cache logic. These tests do
   not require an OpenGL context; they exercise the pure data side of shader
   program records and the Malli schemas in `law.render`."
  (:require
    [clojure.test :refer [deftest testing is]]
    [law.render :as law]
    [infra.render.shader :as sh]))

(deftest test-program-def-schema
  (testing "Built-in programs validate against the law.render schema"
    (is (law/valid-program-def? sh/body-program))
    (is (law/valid-program-def? sh/particle-program))
    (is (law/valid-program-def? sh/sprite-program))
    (is (law/valid-program-def? sh/line-program))
    (is (law/valid-program-def? sh/hud-program))
    (is (law/valid-program-def? sh/volume-program))))

(deftest test-source-hash-stable
  (testing "Source hashes are stable for identical definitions and differ when source changes"
    (is (= (sh/source-hash sh/body-program) (sh/source-hash sh/body-program)))
    (is (not= (sh/source-hash sh/body-program) (sh/source-hash sh/sprite-program)))
    (let [modified (assoc-in sh/body-program [:vertex :source] "different")]
      (is (not= (sh/source-hash sh/body-program) (sh/source-hash modified))))))

(deftest test-program-cache-keyed-by-name
  (testing "Program ids are retrievable by keyword name after caching"
    ;; We cannot compile without GL, but we can ensure the cache map accepts the
    ;; expected key shape and that helper functions read it.
    (reset! sh/program-cache {})
    (swap! sh/program-cache assoc :body {:id 42 :hash (sh/source-hash sh/body-program)})
    (is (= 42 (sh/program-id :body)))
    (is (= (sh/source-hash sh/body-program) (sh/program-hash :body)))
    (is (nil? (sh/program-id :missing)))))

(deftest test-builtin-programs-list
  (testing "All built-in programs have distinct names"
    (let [names (map :name sh/builtin-programs)]
      (is (= (count names) (count (set names))))
      (is (every? keyword? names)))))

(deftest test-program-def-extracts-slots
  (testing "Program definitions expose inputs/uniforms/outputs as data"
    (is (= #{:aPos} (set (keys (get-in sh/body-program [:vertex :inputs])))))
    (is (= #{:model :view :projection} (set (keys (get-in sh/body-program [:vertex :uniforms])))))
    (is (= #{:color :accent :cameraPos :glow :seed :surfaceType}
           (set (keys (get-in sh/body-program [:fragment :uniforms])))))
    (is (string? (get-in sh/body-program [:vertex :source])))
    (is (string? (get-in sh/body-program [:fragment :source])))))
