(ns infra.render.passes-test
  "Tests for `infra.render.passes` blend/depth/cull/uniform helpers. These
   stub the namespace's private GL call seams via `with-redefs` on `#'...`
   vars, so the assertions cover *which* GL state a helper requests without
   requiring a live OpenGL context."
  (:require
   [clojure.test :refer [deftest testing is]]
   [infra.render.passes :as passes])
  (:import
   (org.lwjgl.opengl GL11)))

(defn- capture!
  "Run `thunk` with every passes GL seam redirected into a shared log atom;
   returns the log as a vector of [call-name args...] tuples."
  [thunk]
  (let [log (atom [])]
    (with-redefs [passes/gl-enable!           (fn [cap] (swap! log conj [:enable cap]))
                  passes/gl-disable!          (fn [cap] (swap! log conj [:disable cap]))
                  passes/gl-blend-func!       (fn [s d] (swap! log conj [:blend-func s d]))
                  passes/gl-depth-mask!       (fn [w] (swap! log conj [:depth-mask (boolean w)]))
                  passes/gl-cull-face!        (fn [m] (swap! log conj [:cull-face m]))
                  passes/gl-uniform1i!        (fn [loc v] (swap! log conj [:uniform1i loc v]))
                  passes/gl-uniform1f!        (fn [loc v] (swap! log conj [:uniform1f loc v]))
                  passes/gl-uniform3f!        (fn [loc a b c] (swap! log conj [:uniform3f loc a b c]))
                  passes/gl-uniform4f!        (fn [loc a b c d] (swap! log conj [:uniform4f loc a b c d]))
                  passes/gl-uniform-matrix4fv! (fn [loc m] (swap! log conj [:uniform-matrix4fv loc m]))
                  passes/gl-uniform-location  (fn [program name] [program name])]
      (thunk))
    @log))

(deftest test-blend-state-helpers
  (testing ":alpha enables blending with the standard SRC_ALPHA/ONE_MINUS_SRC_ALPHA function"
    (let [log (capture! #(passes/set-blend! :alpha))]
      (is (= [[:enable GL11/GL_BLEND] [:blend-func GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA]]
             log))))
  (testing ":additive enables blending with ONE/ONE_MINUS_SRC_ALPHA"
    (let [log (capture! #(passes/set-blend! :additive))]
      (is (= [[:enable GL11/GL_BLEND] [:blend-func GL11/GL_ONE GL11/GL_ONE_MINUS_SRC_ALPHA]]
             log))))
  (testing ":none (and nil) disable blending"
    (is (= [[:disable GL11/GL_BLEND]] (capture! #(passes/set-blend! :none))))
    (is (= [[:disable GL11/GL_BLEND]] (capture! #(passes/set-blend! nil))))))

(deftest test-depth-state-helpers
  (testing "set-depth-write! toggles the depth mask"
    (is (= [[:depth-mask true]] (capture! #(passes/set-depth-write! true))))
    (is (= [[:depth-mask false]] (capture! #(passes/set-depth-write! false)))))
  (testing "set-depth-test! enables/disables GL_DEPTH_TEST"
    (is (= [[:enable GL11/GL_DEPTH_TEST]] (capture! #(passes/set-depth-test! true))))
    (is (= [[:disable GL11/GL_DEPTH_TEST]] (capture! #(passes/set-depth-test! false))))))

(deftest test-cull-state-helpers
  (testing ":back and :front enable culling with the matching face"
    (is (= [[:enable GL11/GL_CULL_FACE] [:cull-face GL11/GL_BACK]]
           (capture! #(passes/set-cull! :back))))
    (is (= [[:enable GL11/GL_CULL_FACE] [:cull-face GL11/GL_FRONT]]
           (capture! #(passes/set-cull! :front)))))
  (testing ":none disables culling"
    (is (= [[:disable GL11/GL_CULL_FACE]] (capture! #(passes/set-cull! :none))))))

(deftest test-bind-uniforms-dispatches-by-value-shape
  (testing "bind-uniforms! sniffs GL call shape from the uniform value's shape"
    (let [log (capture! #(passes/bind-uniforms!
                          1 {:model (vec (range 16))
                             :color [0.1 0.2 0.3]
                             :hudColor [0.1 0.2 0.3 0.4]
                             :surfaceType 2
                             :glow 0.5}))]
      (is (some #(= :uniform-matrix4fv (first %)) log))
      (is (some #(= :uniform3f (first %)) log))
      (is (some #(= :uniform4f (first %)) log))
      (is (some #(and (= :uniform1i (first %)) (= 2 (nth % 2))) log))
      (is (some #(and (= :uniform1f (first %)) (= 0.5 (nth % 2))) log)))))
