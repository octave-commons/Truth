;; OFFLINE DRAFT: target guard deliberately refuses installation.
;; Bounded diagnostic follow pass. It changes camera/selection through existing APIs.
;; Follow affects attention; the evolved world cannot be restored or called unchanged.
;; No desktop input, body/history/time injection, field/HUD filtering or extra GL context.
(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[domain.ecs.components :as c]
         '[domain.ecs.core :as ecs]
         '[infra.render.units :as units]
         '[infra.render.scene.trails :as trails]
         '[law.trail :as trail-law]
         '[infra.camera :as camera]
         '[infra.menu :as menu]
         '[shape.spatial :as sp]
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
      _ (assert (= 131581 pid) "Unexpected process")
      _ (assert (= "b04f7fb6-ea65-4b47-8866-73e24a6887f6"
                   (str/trim (read-proc "/proc/sys/kernel/random/boot_id"))) "Unexpected boot")
      stat (read-proc "/proc/self/stat")
      stat-fields (str/split (subs stat (+ 2 (.lastIndexOf stat ")"))) #"\s+")
      _ (assert (= "125503" (nth stat-fields 19)) "Process start changed")
      _ (assert (= "/home/err/spaces/foresight/.worktrees/truth-validation"
                   (System/getProperty "user.dir")) "Working directory changed")
      ;; Fail closed until a fresh resumed-world read selects real targets and
      ;; root reviews the pinned result. Never reuse prior-world entity IDs.
      targets nil
      _ (assert (and (= #{:star :planet} (set (keys targets)))
                     (every? integer? (vals targets))) "Fresh target selection pending")
      _ (assert (= ":0" (System/getenv "DISPLAY")) "Unexpected display")
      _ (assert (= "7896" (System/getenv "TRUTH_DEMO_PORT")) "Unexpected port")
      _ (assert (= 919497450 (System/identityHashCode (:world service))) "Unexpected world")
      _ (assert (= 137768861339584 (:window service)) "Unexpected native window")
      _ (assert (and (.isAlive ^Thread (:thread service))
                     (.isAlive ^Thread (:sim-thread service))) "Worker is not alive")
      _ (assert (and (nil? (:error service))
                     (nil? (:ui/error-state @(:config service)))) "Service has errors")
      previous-var (ns-resolve 'user 'truth-native-current-frame-observer)
      _ (assert previous-var "Previous frame observer is absent")
      previous @previous-var
      _ (assert (and (not (:active? @(:state previous)))
                     (identical? @(:scene-var previous) (:original-scene previous)))
                "Previous observer is not restored")
      _ (assert (nil? (ns-resolve 'user 'truth-follow-trail-observer)) "Already installed")
      directory (io/file (System/getProperty "user.dir")
                         ".ημ/diagnostics/body-trails/hardware-follow-2026-10-07/captures")
      _ (assert (.mkdir directory) "Capture directory already exists")
      cfg-atom (:config service)
      original-cfg @cfg-atom
      body-key-present? (contains? original-cfg :bodies-fn)
      original-bodies (:bodies-fn original-cfg infra.render/bodies-from-world)
      scene-var #'infra.render/render-scene
      original-scene @scene-var
      guard (Object.)
      projection (atom nil)
      view-state (atom nil)
      wrappers (atom nil)
      now (System/currentTimeMillis)
      state (atom {:phase :preparing :active? false :pid pid :display ":0" :port 7896
                   :world-atom-identity 919497450 :window (:window service)
                   :started-wall-ms now :deadline-wall-ms (+ now 90000)
                   :capture-attempts 0 :captures [] :errors [] :raw-gl-samples []
                   :body-calls 0 :scene-calls 0 :unpaired-frames 0
                   :targets targets
                   :stage :prepare :active-kind nil :stage-captures 0
                   :stage-first-sim-time nil :framing []
                   :view-intervention-started? false
                   :camera-restored? true :view-fields-restored? true
                   :scene-root-restored? false :bodies-config-restored? false})]
  (letfn [(save! [] (spit (io/file directory "observer-state.edn") (str (pr-str @state) "\n")))
          (error! [scope t]
            (swap! state update :errors conj {:scope scope :error (str t)
                                              :wall-ms (System/currentTimeMillis)}))
          (save-safely! [] (try (save!) (catch Throwable t (error! :state-file t))))
          (restore! [reason]
            (locking guard
              (swap! state assoc :active? false :phase reason
                     :closed-wall-ms (System/currentTimeMillis))
              (try
                (alter-var-root scene-var
                                (fn [current]
                                  (cond
                                    (identical? current (:scene @wrappers)) original-scene
                                    (identical? current original-scene) original-scene
                                    :else (throw (ex-info "Scene root changed owner" {})))))
                (swap! state assoc :scene-root-restored? (identical? @scene-var original-scene))
                (catch Throwable t (error! :scene-restoration t)))
              (try
                (swap! cfg-atom
                       (fn [cfg]
                         (let [current (:bodies-fn cfg infra.render/bodies-from-world)]
                           (assert (or (identical? current (:bodies @wrappers))
                                       (identical? current original-bodies))
                                   "Body projection config changed owner")
                           (if body-key-present?
                             (assoc cfg :bodies-fn original-bodies)
                             (dissoc cfg :bodies-fn)))))
                (swap! state assoc :bodies-config-restored?
                       (and (= body-key-present? (contains? @cfg-atom :bodies-fn))
                            (identical? (:bodies-fn @cfg-atom infra.render/bodies-from-world)
                                        original-bodies)))
                (catch Throwable t (error! :body-config-restoration t)))
              (when-let [{:keys [camera-value fields]} @view-state]
                (try
                  (swap! cfg-atom
                         (fn [cfg]
                           (reduce-kv (fn [result field-key {:keys [present? value]}]
                                        (if present? (assoc result field-key value) (dissoc result field-key)))
                                      cfg fields)))
                  (reset! (:camera service) camera-value)
                  (swap! state assoc :camera-restored? (= camera-value @(:camera service))
                         :view-fields-restored?
                         (every? (fn [[field-key {:keys [present? value]}]]
                                   (and (= present? (contains? @cfg-atom field-key))
                                        (or (not present?) (= value (get @cfg-atom field-key))))) fields))
                  (catch Throwable t (error! :view-restoration t))))
              (reset! projection nil)
              (save-safely!)
              @state))
          (gl-errors! [stage]
            (let [codes (loop [codes []]
                          (let [code (GL11/glGetError) result (conj codes code)]
                            (if (or (zero? code) (= 16 (count result))) result (recur result))))]
              (swap! state update :raw-gl-samples conj
                     {:attempt (:capture-attempts @state) :stage stage :codes codes
                      :truncated? (not (zero? (peek codes)))})
              (assert (= [0] codes) (str "Native GL errors " stage " " codes))))
          (begin-target! [kind world scene]
            (let [_ (assert (identical? (Thread/currentThread) (:thread service)) "Wrong view intervention thread")
                  _ (assert (= (:window service) (GLFW/glfwGetCurrentContext)) "Wrong view intervention context")
                  _ (assert (identical? service @infra.dev.window.lifecycle/service-state) "Service changed")
                  eid (get-in @state [:targets kind])
                  matter (ecs/get-component world eid c/matter-state)
                  position (ecs/get-component world eid c/position)
                  history (ecs/get-component world eid c/motion-trail)
                  _ (assert (= kind matter) "Target no longer has its natural star/planet state")
                  _ (assert (and position (trail-law/valid-trail? history)
                                 (<= 2 (count (:samples history)))) "Target lacks real history")
                  _ (assert (false? (:demo/fixture? world)) "Unexpected fixture")
                  ctx (units/make-context (:camera scene) {:width (:width scene) :height (:height scene)})
                  radius-metres (apply max (map #(sp/dist position (:position %)) (:samples history)))
                  radius-ru (/ radius-metres (:scale ctx))
                  body-radius (ecs/get-component world eid c/radius)
                  body-radius-ru (units/phys->body-render-radius ctx body-radius)
                  min-distance (camera/min-approach-distance body-radius-ru)
                  fit-distance (camera/distance-for-radius radius-ru 60.0 1.6)
                  distance (max min-distance fit-distance)
                  _ (assert (and (trail-law/finite-number? distance) (pos? distance)) "Invalid camera distance")
                  before @(:camera service)]
              (when-not @view-state
                (let [cfg @cfg-atom
                      saved {:camera-value before
                             :fields (into {} (map (fn [field-key]
                                                     [field-key {:present? (contains? cfg field-key) :value (get cfg field-key)}])
                                                   [:mode :selection :follow-eid :zoom-min]))}]
                  (reset! view-state saved)
                  (swap! state assoc :prior-view saved :view-intervention-started? true
                         :camera-restored? false :view-fields-restored? false)))
              ;; Existing ordinary menu selection establishes follow; no target hard-snap.
              (swap! cfg-atom menu/apply-action [:ui/select-entity eid])
              (swap! (:camera service) #(-> % (assoc :distance distance) camera/update-camera-position))
              (swap! state #(-> %
                               (assoc :stage :settling :active-kind kind :stage-captures 0
                                      :stage-first-sim-time nil
                                      :settle-start-ms (System/currentTimeMillis)
                                      :settle-start-scene (:scene-calls @state))
                               (update :framing conj
                                       {:kind kind :eid eid :projection-tick (:tick world)
                                        :physical-radius body-radius :history-radius-metres radius-metres
                                        :history-radius-render-units radius-ru :fit-distance fit-distance
                                        :minimum-distance min-distance :chosen-distance distance
                                        :prior-camera before :changed-camera @(:camera service)})))
              (save-safely!)))
          (settled? [scene world]
            (let [eid (get-in @state [:targets (:active-kind @state)])
                  position (ecs/get-component world eid c/position)
                  ctx (units/make-context (:camera scene) {:width (:width scene) :height (:height scene)})
                  point (when position (units/render->screen ctx (units/world->render ctx position)))]
              (and point (>= (- (:scene-calls @state) (:settle-start-scene @state)) 3)
                   (<= (* 0.30 (:width scene)) (first point) (* 0.70 (:width scene)))
                   (<= (* 0.30 (:height scene)) (second point) (* 0.70 (:height scene))))))
          (capture! [scene world projected actual]
            (let [{:keys [width height camera]} scene
                  ctx (units/make-context camera {:width width :height height})
                  _ (assert (identical? (Thread/currentThread) (:thread service)) "Wrong render thread")
                  _ (assert (= (:window service) (GLFW/glfwGetCurrentContext)) "Wrong GL context")
                  _ (assert (identical? (:world service)
                                        (:world @infra.dev.window.lifecycle/service-state)) "World changed")
                  _ (assert (and (integer? width) (integer? height)
                                 (<= 1 width 4096) (<= 1 height 4096)) "Invalid framebuffer")
                  _ (assert (= actual (:shapes projected)) "Actual scene trail geometry differs from production projection")
                  _ (gl-errors! :after-production-scene)
                  _ (assert (zero? (GL11/glGetInteger GL30/GL_READ_FRAMEBUFFER_BINDING)) "Nondefault read FBO")
                  _ (assert (zero? (GL11/glGetInteger GL21/GL_PIXEL_PACK_BUFFER_BINDING)) "Pixel pack buffer active")
                  _ (assert (and (zero? (GL11/glGetInteger GL11/GL_PACK_ROW_LENGTH))
                                 (zero? (GL11/glGetInteger GL11/GL_PACK_SKIP_ROWS))
                                 (zero? (GL11/glGetInteger GL11/GL_PACK_SKIP_PIXELS))
                                 (= 4 (GL11/glGetInteger GL11/GL_PACK_ALIGNMENT))) "Unexpected pack layout")
                  read-buffer (GL11/glGetInteger GL11/GL_READ_BUFFER)
                  pixels (try
                           (GL11/glReadBuffer GL11/GL_BACK)
                           (let [buffer (@#'infra.render.window/read-pixels width height)]
                             (gl-errors! :after-readback) buffer)
                           (finally (GL11/glReadBuffer read-buffer)))
                  _ (assert (= read-buffer (GL11/glGetInteger GL11/GL_READ_BUFFER)) "Read buffer not restored")
                  _ (gl-errors! :after-read-buffer-restoration)
                  rgba (@#'infra.render.window/flip-rgba-vertical pixels width height)
                  index (:capture-attempts @state)
                  stem (format "pid-%d-frame-%02d" pid index)
                  published @(:world service)
                  row {:capture index :active-kind (:active-kind @state) :stage-capture (inc (:stage-captures @state)) :pid pid :world-atom-identity 919497450
                       :window (:window service) :display ":0" :port 7896
                       :width width :height height :image (str stem ".png")
                       :wall-ms (System/currentTimeMillis)
                       :gl {:vendor (GL11/glGetString GL11/GL_VENDOR)
                            :renderer (GL11/glGetString GL11/GL_RENDERER)
                            :version (GL11/glGetString GL11/GL_VERSION)}
                       :boundary :after-full-scene-and-hud-before-swap
                       :projection-input (select-keys world [:tick :genesis/sim-time :sim/dt :genesis/frame-offset
                                                            :arc/current :demo/scenario :demo/fixture?])
                       :published-read-after-scene (select-keys published [:tick :genesis/sim-time])
                       :scene-camera camera :scene-origin (:render-origin scene)
                       :scene-shape-count (count (:bodies scene))
                       :scene-line-count (count (filter #(= :line (:render-mode %)) (:bodies scene)))
                       :tagged-trail-vertex-count (count actual) :budget (:summary projected)
                       :actual-trails-equal-production-projection? true
                       :read-buffer-before read-buffer :read-buffer-restored? true
                       :targets
                       (into {} (map (fn [[kind eid]]
                                       [kind {:eid eid :matter-state (ecs/get-component world eid c/matter-state)
                                              :position (ecs/get-component world eid c/position)
                                              :history (ecs/get-component world eid c/motion-trail)
                                              :actual-vertices
                                              (mapv #(assoc % :framebuffer (units/render->screen ctx (:position %)))
                                                    (filter #(= eid (:trail/entity %)) actual))}])
                                     (select-keys (:targets @state) [(:active-kind @state)])))}]
              (assert (STBImageWrite/stbi_write_png (.getPath (io/file directory (str stem ".png")))
                                                    width height 4 rgba (* 4 width)) "PNG writer failed")
              (spit (io/file directory (str stem ".edn")) (str (pr-str row) "\n"))
              (swap! state update :captures conj row)
              (save-safely!)))
          (observe! [scene]
            (locking guard
              (when (:active? @state)
                (try
                  (swap! state update :scene-calls inc)
                  (if (>= (System/currentTimeMillis) (:deadline-wall-ms @state))
                    (restore! :deadline-reached)
                    (if-let [world (:world (let [input @projection]
                                           (reset! projection nil)
                                           input))]
                      (if (= :prepare (:stage @state))
                        (begin-target! :star world scene)
                        (let [kind (:active-kind @state)
                              eid (get-in @state [:targets kind])
                              _ (assert (= kind (ecs/get-component world eid c/matter-state)) "Selected target changed or vanished")
                              _ (assert (= eid (:follow-eid @cfg-atom) (:selection @cfg-atom)) "Diagnostic selection changed")
                              _ (assert (= :follow-selection (:mode @cfg-atom)) "Diagnostic mode changed")
                              waiting? (= :settling (:stage @state))
                              ready? (or (not waiting?) (settled? scene world))]
                          (when (and waiting? (not ready?)
                                     (>= (- (System/currentTimeMillis) (:settle-start-ms @state)) 10000))
                            (throw (ex-info "Natural follow did not settle within ten seconds"
                                            {:kind kind :eid eid :camera (:camera scene)})))
                          (when ready?
                            (let [sim-time (:genesis/sim-time world)
                                  initial (or (:stage-first-sim-time @state) sim-time)
                                  due (+ initial (* (:stage-captures @state)
                                                    0.5 (:horizon trail-law/default-options)))]
                              (when (>= sim-time due)
                                (swap! state assoc :stage :capturing :stage-first-sim-time initial)
                                (assert (< (:capture-attempts @state) 6) "Capture budget exhausted")
                                (swap! state update :capture-attempts inc)
                                (capture! scene world
                                          (trails/project-trails
                                            (units/make-context (:camera scene)
                                                                {:width (:width scene) :height (:height scene)}) world)
                                          (filterv :trail/entity (:bodies scene)))
                                (swap! state update :stage-captures inc)
                                (if (= 3 (:stage-captures @state))
                                  (if (= :star kind) (begin-target! :planet world scene) (restore! :complete))
                                  (save-safely!)))))))
                      (swap! state update :unpaired-frames inc)))
                  (catch Throwable t (error! :observation t) (restore! :probe-failed))))))]
    (let [body-wrapper (fn [world]
                         (locking guard (reset! projection nil))
                         (let [result (original-bodies world)]
                           (locking guard
                             (when (:active? @state)
                               (try
                                 (assert (identical? (Thread/currentThread) (:thread service)) "Wrong projection thread")
                                 (swap! state update :body-calls inc)
                                 (reset! projection {:world world})
                                 (catch Throwable t (error! :projection-observation t) (restore! :probe-failed)))))
                           result))
          scene-wrapper (fn [scene]
                          (let [result (try (original-scene scene)
                                            (catch Throwable t
                                              (try (error! :original-renderer t) (restore! :original-renderer-failed)
                                                   (catch Throwable cleanup (.addSuppressed t cleanup)))
                                              (throw t)))]
                            (observe! scene) result))
          control {:state state :guard guard :restore! restore! :service service
                   :scene-var scene-var :original-scene original-scene
                   :config-atom cfg-atom :body-key-present? body-key-present?
                   :original-bodies original-bodies :wrappers wrappers}
          deadline-thread
          (Thread.
           (fn []
             (try
               (Thread/sleep (max 0 (- (:deadline-wall-ms @state) (System/currentTimeMillis))))
               (locking guard
                 (when (:active? @state)
                   (restore! :wall-deadline-reached)))
               (catch Throwable t
                 (error! :deadline-watchdog t)
                 (save-safely!))))
           "truth-follow-observer-deadline")]
      (reset! wrappers {:bodies body-wrapper :scene scene-wrapper})
      (intern 'user 'truth-follow-trail-observer (assoc control :deadline-thread deadline-thread))
      (try
        (locking guard
          (swap! cfg-atom (fn [cfg]
                            (assert (identical? (:bodies-fn cfg infra.render/bodies-from-world) original-bodies)
                                    "Projection changed before install")
                            (assoc cfg :bodies-fn body-wrapper)))
          (alter-var-root scene-var (fn [current]
                                     (assert (identical? current original-scene) "Scene changed before install")
                                     scene-wrapper))
          (swap! state assoc :active? true :phase :installed)
          (save-safely!)
          (.setDaemon deadline-thread true)
          (.start deadline-thread))
        (catch Throwable t (error! :installation t) (restore! :installation-failed) (throw t)))
      {:installed true :pid pid :maximum-captures 6 :wall-deadline-ms 90000
       :horizon-sim-seconds (:horizon trail-law/default-options)})))
