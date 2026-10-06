;; Approved diagnostic wrapper. All GL changes run on the owning render thread.
;; Refuse an existing callback; restore the original output enable state and
;; free only our callback. No ECS, config, world, or physics changes.
(let [observer-var (ns-resolve 'user 'truth-native-observer)
      {:keys [target wrapper]} @observer-var
      prior wrapper
      debug (atom {:status :requested :message-count 0 :messages []})
      wrapped
      (fn [frame-args]
        (let [result (prior frame-args)]
          (try
            (case (:status @debug)
              :requested
              (let [caps (org.lwjgl.opengl.GL/getCapabilities)
                    supported (or (.-OpenGL43 caps) (.-GL_KHR_debug caps))]
                (if-not supported
                  (swap! debug assoc :status :unsupported)
                  (let [existing (org.lwjgl.opengl.GL11/glGetPointer org.lwjgl.opengl.GL43/GL_DEBUG_CALLBACK_FUNCTION)]
                    (if-not (zero? existing)
                      (swap! debug assoc :status :refused-existing-callback :pointer existing)
                      (let [enabled? (org.lwjgl.opengl.GL11/glIsEnabled org.lwjgl.opengl.GL43/GL_DEBUG_OUTPUT)
                            callback
                            (org.lwjgl.opengl.GLDebugMessageCallback/create
                             (reify org.lwjgl.opengl.GLDebugMessageCallbackI
                               (invoke [_ source message-type id severity length message _user-param]
                                 (let [entry {:wall-ms (System/currentTimeMillis)
                                              :source source :type message-type :id id :severity severity
                                              :message (org.lwjgl.opengl.GLDebugMessageCallback/getMessage length message)}]
                                   (swap! debug #(-> %
                                                     (update :message-count inc)
                                                     (update :messages (fn [xs] (vec (take-last 32 (conj xs entry)))))))))))]
                        (org.lwjgl.opengl.GL43/glDebugMessageCallback callback 0)
                        (org.lwjgl.opengl.GL11/glEnable org.lwjgl.opengl.GL43/GL_DEBUG_OUTPUT)
                        (swap! debug assoc :status :installed :original-output-enabled? enabled?
                               :callback callback :installed-thread (.getName (Thread/currentThread))))))))
              :restore-requested
              (do
                (let [^org.lwjgl.opengl.GLDebugMessageCallbackI no-callback nil]
                  (org.lwjgl.opengl.GL43/glDebugMessageCallback no-callback 0))
                (if (:original-output-enabled? @debug)
                  (org.lwjgl.opengl.GL11/glEnable org.lwjgl.opengl.GL43/GL_DEBUG_OUTPUT)
                  (org.lwjgl.opengl.GL11/glDisable org.lwjgl.opengl.GL43/GL_DEBUG_OUTPUT))
                (.free ^org.lwjgl.opengl.GLDebugMessageCallback (:callback @debug))
                (swap! debug #(-> % (dissoc :callback) (assoc :status :restored))))
              nil)
            (catch Throwable error
              (swap! debug assoc :status :probe-error :error (str error))))
          result))]
  (assert (identical? @target prior) "Refuse concurrent callable replacement")
  (alter-var-root target (constantly wrapped))
  (alter-var-root observer-var #(assoc % :wrapper wrapped :gl-debug debug))
  {:debug-install-requested true :message-cap 32})
