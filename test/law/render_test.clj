(ns law.render-test
  "Tests for Malli schemas in law.render."
  (:require
    [clojure.test :refer [deftest testing is]]
    [law.render :as lr]))

(deftest test-render-context-schema
  (testing "Valid and invalid render contexts"
    (is (lr/valid-render-context? {:scale 1.0e15
                                   :camera {}
                                   :viewport {:width 1280 :height 720}}))
    (is (not (lr/valid-render-context? {:scale 1.0e15
                                        :camera {}
                                        :viewport {:width 1280}}))
        "missing height is invalid")
    (is (not (lr/valid-render-context? {:camera {}
                                        :viewport {:width 1 :height 1}}))
        "missing scale is invalid")))

(deftest test-render-shape-schema
  (testing "Render shape schema accepts expected shapes"
    (is (lr/valid-render-shape? {:render-mode :body
                                 :position [0.0 0.0 0.0]
                                 :radius 1.0
                                 :color [1.0 0.0 0.0]}))
    (is (lr/valid-render-shape? {:render-mode :particle
                                 :position [0.0 0.0 0.0]}))
    (is (not (lr/valid-render-shape? {:render-mode :unknown
                                      :position [0.0 0.0 0.0]}))
        "unknown render mode is invalid")
    (is (not (lr/valid-render-shape? {:render-mode :body
                                      :position [0.0 0.0]}))
        "position must be 3D")))
