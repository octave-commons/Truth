(let [d @user/truth-cadence-reload
      s (:old d) target (:frame-var d) original (:original-frame d)
      wrapper
      (fn [args]
        (let [result (original args)]
          (if-not (compare-and-set! (:claimed d) false true)
            result
            (try
              (assert (identical? (:world s)
                                  (:world @infra.dev.window.lifecycle/service-state)))
              (assert (= (:window args) (:window s)))
              (assert (not-any? true? (vals @(:keys-atom args))) "Held key")
              (assert (every? nil? (map @(:config s)
                                       [:screenshot-request :action-request :pick-request]))
                      "Outstanding host request")
              ;; Mouse buttons are separate from the key-state atom.
              (doseq [button [org.lwjgl.glfw.GLFW/GLFW_MOUSE_BUTTON_LEFT
                              org.lwjgl.glfw.GLFW/GLFW_MOUSE_BUTTON_RIGHT
                              org.lwjgl.glfw.GLFW/GLFW_MOUSE_BUTTON_MIDDLE]]
                (assert (= org.lwjgl.glfw.GLFW/GLFW_RELEASE
                           (org.lwjgl.glfw.GLFW/glfwGetMouseButton (:window s) button))
                        "Held mouse button"))
              (reset! (:stop s) true)
              (swap! user/truth-cadence-reload assoc
                     :owner-thread (.getName (Thread/currentThread))
                     :neutral-keys @(:keys-atom args)
                     :camera-at-teardown @(:camera s)
                     :config-at-teardown @(:config s))
              ;; The live context is unshared. Destruction retires its GL objects.
              (org.lwjgl.glfw.Callbacks/glfwFreeCallbacks (:window s))
              (org.lwjgl.glfw.GLFW/glfwMakeContextCurrent 0)
              (org.lwjgl.opengl.GL/setCapabilities nil)
              (org.lwjgl.glfw.GLFW/glfwDestroyWindow (:window s))
              (when-let [h (:historical d)]
                (org.lwjgl.glfw.Callbacks/glfwFreeCallbacks (:window h))
                (org.lwjgl.glfw.GLFW/glfwDestroyWindow (:window h)))
              ;; stop! must not receive a freed native window pointer.
              (swap! infra.dev.window.lifecycle/service-state dissoc :window)
              (deliver (:teardown d) {:ok true :wall-ms (System/currentTimeMillis)})
              false
              (catch Throwable t
                (deliver (:teardown d) {:ok false :error (str t)})
                ;; If cleanup has begun, stop rendering; root must investigate.
                (if @(:stop s) false result))
              (finally
                (alter-var-root target
                                #(if (identical? % (:wrapper @user/truth-cadence-reload))
                                   original %)))))))]
  (assert (identical? @target original) "Frame callable changed since staging")
  (swap! user/truth-cadence-reload assoc :wrapper wrapper)
  (alter-var-root target (constantly wrapper))
  {:teardown-requested true})
