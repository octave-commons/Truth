(ns infra.render.material-test
  "Tests for `infra.render.material`: the material record shape and
   `draw-material!`'s pass-orchestration logic. GL calls are stubbed via
   `with-redefs` on the namespace's own seams (and on `infra.render.passes`'
   public state helpers) so this runs headless."
  (:require
   [clojure.test :refer [deftest testing is]]
   [infra.render.material :as material]
   [infra.render.passes :as passes])
  (:import
   (org.lwjgl.opengl GL11)))

(deftest test-material-record-shape
  (testing "material fills in default depth state and normalizes uniforms"
    (let [mat (material/material {:program :body :mesh {:vao 1 :count 3}})]
      (is (= :body (:program mat)))
      (is (= {} (:uniforms mat)))
      (is (= {:vao 1 :count 3} (:mesh mat)))
      (is (= {:write? true :test? true} (:depth mat)))))
  (testing "explicit blend/depth/uniforms are preserved"
    (let [mat (material/material {:program 7 :uniforms {:glow 0.5} :mesh {:vao 2 :count 6}
                                  :blend :alpha :depth {:write? false}})]
      (is (= :alpha (:blend mat)))
      (is (= {:write? false :test? true} (:depth mat)) "test? still defaults true"))))

(deftest test-material-record-describes-body-pass
  (testing "a body pass material record carries program, uniforms, mesh, blend, and depth"
    (let [mat (material/material
               {:program 7
                :uniforms {:projection (vec (range 16)) :view (vec (range 16))}
                :mesh {:vao 3 :count 60}
                :blend :none
                :depth {:write? true :test? true}})]
      (is (= #{:program :uniforms :mesh :blend :depth} (set (keys mat))))
      (is (map? (:uniforms mat)))
      (is (= 60 (:count (:mesh mat)))))))

(defn- capture-draw!
  "Run `thunk` with material's GL seams and passes' state helpers redirected
   into a shared log; returns the log."
  [thunk]
  (let [log (atom [])]
    (with-redefs [material/gl-use-program! (fn [id] (swap! log conj [:use-program id]))
                  material/gl-bind-vao!    (fn [vao] (swap! log conj [:bind-vao vao]))
                  material/gl-draw-arrays! (fn [mode n] (swap! log conj [:draw-arrays mode n]))
                  passes/set-blend!        (fn [mode] (swap! log conj [:set-blend mode]))
                  passes/set-depth-write!  (fn [w] (swap! log conj [:set-depth-write w]))
                  passes/set-depth-test!   (fn [t] (swap! log conj [:set-depth-test t]))
                  passes/bind-uniforms!    (fn [pid u] (swap! log conj [:bind-uniforms pid u]))]
      (thunk))
    @log))

(deftest test-draw-material-orchestrates-passes-and-asset
  (testing "draw-material! sets blend/depth, uses the program, binds uniforms, draws, and unbinds"
    (let [mat (material/material {:program 7 :uniforms {:seed 1.0}
                                  :mesh {:vao 3 :count 60} :blend :alpha})
          log (capture-draw! #(material/draw-material! mat {:model :M} GL11/GL_TRIANGLES))]
      (is (= [:set-blend :alpha] (first log)))
      (is (some #(= [:set-depth-write true] %) log))
      (is (some #(= [:set-depth-test true] %) log))
      (is (some #(= [:use-program 7] %) log))
      (is (some #(and (= :bind-uniforms (first %)) (= 7 (nth % 1)) (= {:seed 1.0 :model :M} (nth % 2))) log)
          "extra-uniforms override/merge over the material's static uniforms")
      (is (some #(= [:bind-vao 3] %) log))
      (is (some #(= [:draw-arrays GL11/GL_TRIANGLES 60] %) log))
      (is (= [:bind-vao 0] (last log)) "the VAO is unbound after drawing")))
  (testing "a material with no mesh (not yet ready) is a no-op"
    (let [mat (material/material {:program 7 :mesh nil})
          log (capture-draw! #(material/draw-material! mat))]
      (is (= [] log))))
  (testing "a material whose program hasn't compiled (id 0) is a no-op"
    (let [mat (material/material {:program 0 :mesh {:vao 1 :count 3}})
          log (capture-draw! #(material/draw-material! mat))]
      (is (= [] log)))))
