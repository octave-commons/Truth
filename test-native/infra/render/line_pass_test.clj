(ns infra.render.line-pass-test
  "Native regression for the production line pass in its actual GL context."
  (:require
   [clojure.test :refer [deftest is testing]]
   [infra.render.scene.setup :as setup]
   [infra.render.shader :as shader]
   [infra.render.window :as window])
  (:import
   (org.lwjgl BufferUtils)
   (org.lwjgl.glfw GLFW)
   (org.lwjgl.opengl GL GL11 GL20 GL30 GL32)))

(def ^:private extent 64)

(def ^:private identity-matrix
  [1.0 0.0 0.0 0.0
   0.0 1.0 0.0 0.0
   0.0 0.0 1.0 0.0
   0.0 0.0 0.0 1.0])

(defn- with-hidden-context! [test-fn]
  (try
    (window/init-glfw)
    (let [handle (@#'window/create-offscreen-window extent extent)]
      (try
        (test-fn)
        (finally
          (GLFW/glfwMakeContextCurrent 0)
          (GL/setCapabilities nil)
          (GLFW/glfwDestroyWindow handle))))
    (finally
      (GLFW/glfwTerminate)
      (when-let [callback (GLFW/glfwSetErrorCallback nil)]
        (.free callback)))))

(defn- framebuffer-has-color? []
  (let [pixels (BufferUtils/createByteBuffer (* extent extent 3))]
    (GL11/glReadPixels 0 0 extent extent GL11/GL_RGB GL11/GL_UNSIGNED_BYTE pixels)
    (boolean
     (some #(pos? (bit-and 0xff (.get pixels (int %))))
           (range (.limit pixels))))))

(defn- draw-line! [program vertices]
  (GL11/glViewport 0 0 extent extent)
  (GL11/glDisable GL11/GL_DEPTH_TEST)
  (GL11/glClearColor 0.0 0.0 0.0 1.0)
  (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
  (@#'setup/render-lines-pass program identity-matrix identity-matrix vertices))

(deftest production-line-pass-draws-without-native-errors
  (with-hidden-context!
    (fn []
      (testing "the test uses the same forward-compatible core context as the game"
        (is (pos? (bit-and GL30/GL_CONTEXT_FLAG_FORWARD_COMPATIBLE_BIT
                           (GL11/glGetInteger GL30/GL_CONTEXT_FLAGS))))
        (is (pos? (bit-and GL32/GL_CONTEXT_CORE_PROFILE_BIT
                           (GL11/glGetInteger GL32/GL_CONTEXT_PROFILE_MASK)))))
      (let [program (shader/compile-program shader/line-program)
            vertices [{:position [-0.75 0.0 0.0] :color [1.0 0.5 0.25]}
                      {:position [0.75 0.0 0.0] :color [1.0 0.5 0.25]}]]
        (try
          (is (= GL11/GL_NO_ERROR (GL11/glGetError)) "context and shader setup")
          (doseq [[label line] [["legacy default opacity" vertices]
                                ["fading trail opacity" (mapv #(assoc %1 :alpha %2)
                                                              vertices [0.2 0.8])]]]
            (testing label
              (draw-line! program line)
              (is (= GL11/GL_NO_ERROR (GL11/glGetError))
                  "the real line pass must not generate a native GL error")
              (is (framebuffer-has-color?) "the real pass must draw visible pixels")
              (is (= GL11/GL_NO_ERROR (GL11/glGetError)) "framebuffer readback")))
          (finally
            (GL20/glUseProgram 0)
            (GL20/glDeleteProgram program)))))))
