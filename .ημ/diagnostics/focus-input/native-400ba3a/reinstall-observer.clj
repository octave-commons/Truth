;; Reinstall bounded observer after verified restoration and same-world recovery.
(let [old @(ns-resolve 'user 'truth-native-observer)]
  (assert (identical? @(:target old) (:original old)) "Prior wrapper must be restored")
  (intern 'user 'truth-native-observer-before-recovery old)
  (ns-unmap 'user 'truth-native-observer))
;; Diagnostic instrumentation only, approved for this fresh owned service.
;; Preserve the original one-argument callable, return, and exception path.
;; glGetError runs on the original render thread, first call and every 30th.
;; Storage: counters plus at most 128 raw GL/time samples; no ECS/config writes.
(let [service @infra.dev.window/service-state
      target (ns-resolve 'infra.dev.window.loop 'render-frame-once)
      original @target
      state (atom {:started-ns (System/nanoTime) :started-wall-ms (System/currentTimeMillis)
                   :frames 0 :total-call-ns 0 :min-call-ns nil :max-call-ns 0
                   :gl-samples [] :probe-errors []})
      wrapper (fn [frame-args]
                (let [start (System/nanoTime)
                      result (original frame-args)
                      end (System/nanoTime)]
                  (try
                    (let [frame (inc (:frames @state))
                          sample? (or (= frame 1) (zero? (mod frame 30)))
                          sample (when sample?
                                   {:frame frame :wall-ms (System/currentTimeMillis)
                                    :thread (.getName (Thread/currentThread))
                                    :raw-gl-errors
                                    (loop [codes []]
                                      (let [code (org.lwjgl.opengl.GL11/glGetError)
                                            codes (conj codes code)]
                                        (if (or (zero? code) (>= (count codes) 16))
                                          codes
                                          (recur codes))))})
                          gl-info (when (= frame 1)
                                    {:vendor (org.lwjgl.opengl.GL11/glGetString org.lwjgl.opengl.GL11/GL_VENDOR)
                                     :renderer (org.lwjgl.opengl.GL11/glGetString org.lwjgl.opengl.GL11/GL_RENDERER)
                                     :version (org.lwjgl.opengl.GL11/glGetString org.lwjgl.opengl.GL11/GL_VERSION)})
                          elapsed (- end start)]
                      (swap! state
                             (fn [s]
                               (cond-> (-> s
                                           (assoc :frames frame :last-ns end
                                                  :last-wall-ms (System/currentTimeMillis))
                                           (update :total-call-ns + elapsed)
                                           (update :min-call-ns #(if % (min % elapsed) elapsed))
                                           (update :max-call-ns max elapsed))
                                 sample (update :gl-samples #(vec (take-last 128 (conj % sample))))
                                 gl-info (assoc :gl-info gl-info)))))
                    (catch Throwable error
                      ;; Only probe failures are caught; original is outside try.
                      (swap! state update :probe-errors
                             #(vec (take-last 8 (conj % (str error)))))))
                  result))]
  (assert service "Fresh owned service must be running")
  (assert (nil? (ns-resolve 'user 'truth-native-observer)) "Observer already installed")
  (intern 'user 'truth-native-observer {:target target :original original :wrapper wrapper :state state})
  (alter-var-root target (constantly wrapper))
  {:installed true :sample-every-frames 30 :raw-gl-sample-cap 128
   :call-duration "Original render-frame-once elapsed wall time, includes wait/sleep; not GPU timing"
   :overhead "Diagnostic atom update each call; bounded GL query first and every30 calls"})
