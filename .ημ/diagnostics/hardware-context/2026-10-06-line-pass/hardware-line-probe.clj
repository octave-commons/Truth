;; Prepared only. Run in a fresh, disposable JVM after root releases resources.
;; Reuses the unchanged existing native regression and its cleanup boundary.
(require '[clojure.test :as test]
         '[infra.render.line-pass-test :as line-test])
(import '(org.lwjgl.glfw GLFW)
        '(org.lwjgl.opengl GL11))

(let [context-var #'line-test/with-hidden-context!
      original-context @context-var
      observed (atom [])
      summary
      (with-redefs-fn
        {context-var
         (fn [test-fn]
           (original-context
             (fn []
               (let [handle (GLFW/glfwGetCurrentContext)
                     identity {:display (System/getenv "DISPLAY")
                               :vendor (GL11/glGetString GL11/GL_VENDOR)
                               :renderer (GL11/glGetString GL11/GL_RENDERER)
                               :version (GL11/glGetString GL11/GL_VERSION)
                               :visible? (not= GLFW/GLFW_FALSE
                                               (GLFW/glfwGetWindowAttrib
                                                 handle GLFW/GLFW_VISIBLE))}]
                 (swap! observed conj identity)
                 (prn {:hardware-native-test-context identity})
                 (flush)
                 (when (:visible? identity)
                   (throw (ex-info "Expected the existing hidden native-test window"
                                   identity)))
                 (test-fn)))))}
        #(test/run-tests 'infra.render.line-pass-test))]
  (prn {:native-test-summary summary
        :contexts-observed (count @observed)
        :original-test-context-function-restored? (identical? original-context
                                                              @context-var)
        :hardware-verdict :requires-inspection-of-recorded-context-identity})
  (flush)
  (shutdown-agents)
  (System/exit (if (and (= 1 (count @observed))
                        (= 1 (:test summary))
                        (= 9 (:pass summary))
                        (zero? (+ (:fail summary) (:error summary)))
                        (identical? original-context @context-var))
                 0
                 1)))
