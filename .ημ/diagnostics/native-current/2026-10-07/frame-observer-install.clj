;; One-frame diagnostic for the fresh PM2 game PID131581 on :0/7896 only.
;; Intercept the facade actually called by window-loop: after full scene/HUD,
;; before swap. No world/input/config/lifecycle writes and no extra GL context.
;; glGetError consumes error flags; raw bounded samples are retained explicitly.
(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         'infra.render 'infra.render.window 'infra.dev.window.lifecycle)
(import '(org.lwjgl.glfw GLFW)
        '(org.lwjgl.opengl GL11 GL21 GL30)
        '(org.lwjgl.stb STBImageWrite))

(let [service @infra.dev.window.lifecycle/service-state
      pid (.pid (java.lang.ProcessHandle/current))
      read-proc (fn [path]
                  (with-open [r (java.io.RandomAccessFile. path "r")]
                    (let [buffer (byte-array 4096)
                          n (.read r buffer)]
                      (assert (< 0 n 4096) "Empty or oversized proc line")
                      (let [value (String. buffer 0 n java.nio.charset.StandardCharsets/UTF_8)]
                        (assert (str/ends-with? value "\n") "Incomplete proc line")
                        value))))
      _ (assert (= ":0" (System/getenv "DISPLAY")) "Not the hardware display")
      _ (assert (= "7896" (System/getenv "TRUTH_DEMO_PORT")) "Not the new service port")
      _ (assert (= 131581 pid) "Unexpected fresh process")
      _ (assert (= "b04f7fb6-ea65-4b47-8866-73e24a6887f6"
                   (str/trim (read-proc "/proc/sys/kernel/random/boot_id"))) "Unexpected host boot")
      stat (read-proc "/proc/self/stat")
      stat-fields (str/split (subs stat (+ 2 (.lastIndexOf stat ")"))) #"\s+")
      _ (assert (= "125503" (nth stat-fields 19)) "Process start changed")
      _ (assert (= "/home/err/spaces/foresight/.worktrees/truth-validation"
                   (System/getProperty "user.dir")) "Unexpected working directory")
      _ (assert (= 919497450 (System/identityHashCode (:world service))) "Unexpected fresh world")
      _ (assert (= 137768861339584 (:window service)) "Unexpected fresh native window")
      _ (assert (and service (:window service)) "Native window not ready")
      _ (assert (and (.isAlive ^Thread (:thread service))
                     (.isAlive ^Thread (:sim-thread service))) "Native worker not alive")
      _ (assert (and (nil? (:error service))
                     (nil? (:ui/error-state @(:config service)))) "Service already has an error")
      _ (assert (= :nebula (:demo/scenario @(:world service))) "Not the natural nebula route")
      _ (assert (false? (:demo/fixture? @(:world service))) "Not a natural world")
      _ (assert (nil? (ns-resolve 'user 'truth-native-current-frame-observer)) "Already installed")
      directory (io/file (System/getProperty "user.dir")
                         ".ημ/diagnostics/native-current/2026-10-07/frame-capture")
      _ (assert (.mkdir directory) "Capture directory exists or cannot be created")
      scene-var #'infra.render/render-scene
      original-scene @scene-var
      guard (Object.)
      wrapper-slot (atom nil)
      now (System/currentTimeMillis)
      state (atom {:phase :preparing :active? false :pid pid :display ":0" :port 7896
                   :window (:window service)
                   :world-atom-identity (System/identityHashCode (:world service))
                   :boundary :after-full-scene-and-hud-before-swap
                   :started-wall-ms now :deadline-wall-ms (+ now 30000)
                   :scene-calls-observed 0
                   :capture-attempts 0 :captures [] :errors [] :raw-gl-samples []
                   :original-root-restored? false})]
  (letfn [(save-state! []
            (spit (io/file directory "observer-state.edn") (str (pr-str @state) "\n")))
          (record-error! [scope error]
            (swap! state update :errors conj
                   {:scope scope :error (str error) :wall-ms (System/currentTimeMillis)})
            (try
              (binding [*out* *err*]
                (println "Truth hardware frame observer failed:" scope (str error)))
              (catch Throwable _logging-error nil)))
          (save-safely! []
            (try (save-state!)
                 (catch Throwable error
                   (record-error! :state-file error))))
          (restore! [reason]
            (locking guard
              (swap! state assoc :active? false :phase reason
                     :closed-wall-ms (System/currentTimeMillis))
              (try
                (alter-var-root scene-var
                                (fn [current]
                                  (cond
                                    (identical? current @wrapper-slot) original-scene
                                    (identical? current original-scene) original-scene
                                    :else (throw (ex-info "Scene Var belongs to another owner" {})))))
                (swap! state assoc :original-root-restored?
                       (identical? @scene-var original-scene))
                (catch Throwable error (record-error! :root-restoration error)))
              (save-safely!)
              @state))
          (gl-errors! [stage]
            (let [codes (loop [codes []]
                          (let [code (GL11/glGetError)
                                next-codes (conj codes code)]
                            (if (or (zero? code) (= 16 (count next-codes)))
                              next-codes
                              (recur next-codes))))]
              (swap! state update :raw-gl-samples conj
                     {:attempt (:capture-attempts @state) :stage stage :codes codes
                      :truncated? (not (zero? (peek codes)))})
              (when-not (= [0] codes)
                (throw (ex-info "Nonzero or truncated native GL error sample"
                                {:stage stage :codes codes})))))
          (capture! [{:keys [width height t] :as scene}]
            (assert (identical? (Thread/currentThread) (:thread service))
                    "Capture attempted outside the owned render thread")
            (assert (identical? (:world service)
                                (:world @infra.dev.window.lifecycle/service-state))
                    "Service ownership changed")
            (assert (= (:window service) (GLFW/glfwGetCurrentContext))
                    "Owned window context is not current")
            (assert (and (integer? width) (integer? height)
                         (<= 1 width 4096) (<= 1 height 4096)) "Invalid frame dimensions")
            (gl-errors! :after-production-scene)
            ;; These assertions avoid altering framebuffer/PBO/pixel-pack state.
            (assert (zero? (GL11/glGetInteger GL30/GL_READ_FRAMEBUFFER_BINDING))
                    "Expected the native default read framebuffer")
            (assert (zero? (GL11/glGetInteger GL21/GL_PIXEL_PACK_BUFFER_BINDING))
                    "Unexpected pixel pack buffer")
            (assert (and (zero? (GL11/glGetInteger GL11/GL_PACK_ROW_LENGTH))
                         (zero? (GL11/glGetInteger GL11/GL_PACK_SKIP_ROWS))
                         (zero? (GL11/glGetInteger GL11/GL_PACK_SKIP_PIXELS))
                         (= 4 (GL11/glGetInteger GL11/GL_PACK_ALIGNMENT)))
                    "Unexpected pixel pack layout")
            (let [read-buffer (GL11/glGetInteger GL11/GL_READ_BUFFER)
                  pixels (try
                           (GL11/glReadBuffer GL11/GL_BACK)
                           (let [buffer (@#'infra.render.window/read-pixels width height)]
                             (gl-errors! :after-back-buffer-read)
                             buffer)
                           (finally
                             (GL11/glReadBuffer read-buffer)))
                  _ (assert (= read-buffer (GL11/glGetInteger GL11/GL_READ_BUFFER))
                            "Read buffer restoration failed")
                  _ (gl-errors! :after-read-buffer-restoration)
                  rgba (@#'infra.render.window/flip-rgba-vertical pixels width height)
                  index (:capture-attempts @state)
                  image-stem (format "pid-%d-window-%d-frame-%02d" pid (:window service) index)
                  png (io/file directory (str image-stem ".png"))
                  published @(:world service)
                  row {:capture index :pid pid :display ":0" :port 7896
                       :window (:window service)
                       :world-atom-identity (System/identityHashCode (:world service))
                       :thread (.getName (Thread/currentThread))
                       :wall-ms (System/currentTimeMillis) :scene-time t
                       :width width :height height :image (.getName png)
                       :boundary :after-full-scene-and-hud-before-swap
                       :gl {:vendor (GL11/glGetString GL11/GL_VENDOR)
                            :renderer (GL11/glGetString GL11/GL_RENDERER)
                            :version (GL11/glGetString GL11/GL_VERSION)}
                       :published-world-readback-after-scene
                       (select-keys published [:tick :genesis/sim-time :arc/current
                                               :demo/scenario :demo/fixture?])
                       :scene-camera (:camera scene)
                       :scene-body-shape-count (count (:bodies scene))
                       :read-buffer-before read-buffer
                       :read-buffer-restored? true}]
              (assert (STBImageWrite/stbi_write_png (.getPath png) width height 4 rgba (* 4 width))
                      "PNG writer failed")
              (spit (io/file directory (str image-stem ".edn")) (str (pr-str row) "\n"))
              (swap! state update :captures conj row)
              (save-state!)))
          (observe! [scene]
            (locking guard
              (when (:active? @state)
                (try
                  (swap! state update :scene-calls-observed inc)
                  (cond
                    (>= (System/currentTimeMillis) (:deadline-wall-ms @state))
                    (restore! :deadline-reached)
                    (< (:capture-attempts @state) 1)
                    (do
                      (swap! state update :capture-attempts inc)
                      (capture! scene)
                      (restore! :complete)))
                  (catch Throwable error
                    ;; Diagnostic errors are recorded; never poison the game frame.
                    (record-error! :capture error)
                    (restore! :probe-failed))))))]
    (let [wrapper (fn [scene]
                    (let [result (try (original-scene scene)
                                      (catch Throwable error
                                        (try
                                          (locking guard
                                            (record-error! :original-renderer error)
                                            (restore! :original-renderer-failed))
                                          (catch Throwable cleanup-error
                                            (.addSuppressed error cleanup-error)))
                                        (throw error)))]
                      (observe! scene)
                      result))
          control {:state state :guard guard :restore! restore!
                   :scene-var scene-var :original-scene original-scene :wrapper wrapper
                   :service service :directory (.getPath directory)}
          deadline-thread (Thread.
                           (fn []
                             (try
                               (Thread/sleep (max 0 (- (:deadline-wall-ms @state)
                                                      (System/currentTimeMillis))))
                               (locking guard
                                 (when (:active? @state) (restore! :wall-deadline-reached)))
                               (catch Throwable error
                                 (record-error! :deadline-watchdog error)
                                 (save-safely!))))
                           "truth-native-current-frame-deadline")]
      (reset! wrapper-slot wrapper)
      (intern 'user 'truth-native-current-frame-observer (assoc control :deadline-thread deadline-thread))
      (try
        (locking guard
          (alter-var-root scene-var
                          (fn [current]
                            (assert (identical? current original-scene) "Scene root changed before install")
                            wrapper))
          (swap! state assoc :active? true :phase :installed)
          (save-state!)
          (.setDaemon deadline-thread true)
          (.start deadline-thread))
        (catch Throwable error
          (record-error! :installation error)
          (restore! :installation-failed)
          (throw error)))
      {:installed true :pid pid :window (:window service)
       :directory (.getPath directory) :capture-budget 1 :deadline-ms 30000})))
