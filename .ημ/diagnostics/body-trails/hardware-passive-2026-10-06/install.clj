;; Bounded passive diagnostic: actual projection input and full native frames.
;; No camera, body, history, time, HUD, field, selection or input mutation.
(require '[clojure.java.io :as io]
         '[domain.ecs.components :as c]
         '[domain.ecs.core :as ecs]
         '[infra.render.units :as units]
         '[infra.render.scene.trails :as trails]
         '[law.trail :as trail-law]
         'infra.render 'infra.render.window 'infra.dev.window.lifecycle)
(import '(org.lwjgl.glfw GLFW)
        '(org.lwjgl.opengl GL11 GL21 GL30)
        '(org.lwjgl.stb STBImageWrite))

(let [service @infra.dev.window.lifecycle/service-state
      pid (.pid (java.lang.ProcessHandle/current))
      _ (assert (= 4070721 pid) "Unexpected process")
      _ (assert (= ":0" (System/getenv "DISPLAY")) "Unexpected display")
      _ (assert (= "7896" (System/getenv "TRUTH_DEMO_PORT")) "Unexpected port")
      _ (assert (= 275227937 (System/identityHashCode (:world service))) "Unexpected world")
      _ (assert (= 129843170276688 (:window service)) "Unexpected native window")
      _ (assert (and (.isAlive ^Thread (:thread service))
                     (.isAlive ^Thread (:sim-thread service))) "Worker is not alive")
      _ (assert (and (nil? (:error service))
                     (nil? (:ui/error-state @(:config service)))) "Service has errors")
      previous @(ns-resolve 'user 'truth-hardware-frame-observer)
      _ (assert (and (not (:active? @(:state previous)))
                     (identical? @(:scene-var previous) (:original-scene previous)))
                "Previous observer is not restored")
      _ (assert (nil? (ns-resolve 'user 'truth-passive-trail-observer)) "Already installed")
      directory (io/file (System/getProperty "user.dir")
                         ".ημ/diagnostics/body-trails/hardware-passive-2026-10-06/captures")
      _ (assert (.mkdir directory) "Capture directory already exists")
      cfg-atom (:config service)
      original-cfg @cfg-atom
      body-key-present? (contains? original-cfg :bodies-fn)
      original-bodies (:bodies-fn original-cfg infra.render/bodies-from-world)
      scene-var #'infra.render/render-scene
      original-scene @scene-var
      guard (Object.)
      projection (atom nil)
      wrappers (atom nil)
      now (System/currentTimeMillis)
      state (atom {:phase :preparing :active? false :pid pid :display ":0" :port 7896
                   :world-atom-identity 275227937 :window (:window service)
                   :started-wall-ms now :deadline-wall-ms (+ now 60000)
                   :capture-attempts 0 :captures [] :errors [] :raw-gl-samples []
                   :body-calls 0 :scene-calls 0 :unpaired-frames 0 :targets nil :initial-sim-time nil
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
          (target-ids [world shapes]
            (let [ids (sort (distinct (keep :trail/entity shapes)))
                  find-state (fn [matter]
                               (first (filter #(and (= matter (ecs/get-component world % c/matter-state))
                                                    (<= 4 (count (filter (fn [s] (= % (:trail/entity s))) shapes))))
                                              ids)))]
              (when-let [star (find-state :star)]
                (when-let [planet (find-state :planet)] {:star star :planet planet}))))
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
                  row {:capture index :pid pid :world-atom-identity 275227937
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
                                     (:targets @state)))}]
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
                    (if-let [world (:world @projection)]
                      (let [actual (filterv :trail/entity (:bodies scene))
                          targets (or (:targets @state) (target-ids world actual))
                          sim-time (:genesis/sim-time world)
                          initial (or (:initial-sim-time @state) sim-time)
                          due (+ initial (* (count (:captures @state))
                                            0.5 (:horizon trail-law/default-options)))]
                      (when (and targets (>= sim-time due))
                        (swap! state assoc :targets targets :initial-sim-time initial)
                        (swap! state update :capture-attempts inc)
                        (capture! scene world (trails/project-trails
                                              (units/make-context (:camera scene)
                                                                  {:width (:width scene) :height (:height scene)})
                                              world) actual)
                        (when (= 3 (count (:captures @state))) (restore! :complete))))
                      (swap! state update :unpaired-frames inc)))
                  (catch Throwable t (error! :observation t) (restore! :probe-failed))))))]
    (let [body-wrapper (fn [world]
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
                   :original-bodies original-bodies :wrappers wrappers}]
      (reset! wrappers {:bodies body-wrapper :scene scene-wrapper})
      (intern 'user 'truth-passive-trail-observer control)
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
          (save-safely!))
        (catch Throwable t (error! :installation t) (restore! :installation-failed) (throw t)))
      {:installed true :pid pid :maximum-captures 3 :wall-deadline-ms 60000
       :horizon-sim-seconds (:horizon trail-law/default-options)})))
