(ns infra.dev.window.loop
  "Live dev window per-frame loop and sim thread.

   The sim thread owns the world-atom; the render thread only reads it and
   enqueues input intents through an IntentAtom.  Error handlers in both threads
   dump world+ledger artifacts and publish :ui/error-state for the renderer."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [domain.orbital.system :as orbital]
   [domain.player :as player]
   [domain.intervention :as intervention]
   [infra.inspect :as inspect]
   [infra.render :as render]
   [infra.render.shader :as sh]
   [infra.render.units :as units]
   [infra.menu :as menu]
   [infra.camera :as cam])
  (:import
   (org.lwjgl.glfw GLFW)
   (org.lwjgl.opengl GL11 GL15 GL30)))

(deftype IntentAtom [^java.util.concurrent.ConcurrentLinkedQueue queue world-atom]
  clojure.lang.IDeref
  (deref [_] (deref world-atom))
  clojure.lang.IAtom
  (swap [_ f] (.add queue f) (deref world-atom))
  (swap [_ f a] (.add queue (fn [w] (f w a))) (deref world-atom))
  (swap [_ f a b] (.add queue (fn [w] (f w a b))) (deref world-atom))
  (swap [_ f a b args] (.add queue (fn [w] (apply f w a b args))) (deref world-atom))
  (compareAndSet [_ _ _]
    (throw (UnsupportedOperationException.
            "IntentAtom: world mutations must go through swap!/reset! (intents)")))
  (reset [_ v] (.add queue (constantly v)) v))

(defn- drain-intents
  "Apply every queued intent to `w`, in arrival order.  An intent that throws or
   returns a non-map is dropped (logged) rather than corrupting the world."
  [w ^java.util.concurrent.ConcurrentLinkedQueue queue]
  (loop [w w]
    (if-let [f (.poll queue)]
      (recur (try
               (let [w' (f w)]
                 (if (map? w') w' w))
               (catch Throwable t
                 (binding [*out* *err*]
                   (println "[INTENT ERROR]" (.getMessage t)))
                 w)))
      w)))

(defn delete-mesh
  "Release the GPU buffers for a mesh map."
  [{:keys [vao vbo]}]
  (when (and vao (pos? vao))
    (GL30/glDeleteVertexArrays vao))
  (when (and vbo (pos? vbo))
    (GL15/glDeleteBuffers vbo)))

(defn- ensure-resources [config-atom]
  (swap! config-atom
         (fn [{:keys [mesh subdivisions requested-subdivisions] :as cfg}]
           (let [subdivisions (or requested-subdivisions subdivisions 2)
                 cfg          (assoc cfg :subdivisions subdivisions)
                 programs     (sh/ensure-builtins!)]
             (cond-> cfg
               true (assoc :body-program (:body programs)
                           :line-program (:line programs)
                           :sprite-program (:sprite programs)
                           :hud-program (:hud programs)
                           :volume-program (:volume programs))
               (or (nil? mesh)
                   (not= subdivisions requested-subdivisions))
               (assoc :mesh (render/upload-mesh (render/make-sphere-mesh subdivisions))
                      :requested-subdivisions nil))))))

(defn- handle-screenshot-request [world-atom config-atom]
  (when-let [{:keys [path result opts]} (:screenshot-request @config-atom)]
    (try
      (render/render-to-file world-atom path (or opts {}))
      (deliver result {:ok true :path path})
      (catch Throwable t
        (deliver result {:error t}))
      (finally
        (swap! config-atom dissoc :screenshot-request)))))

(def ^:private default-tick-fn
  "Fallback per-tick world advance: pure gravity (the Sun/Earth/Moon demo)."
  (fn [w] ((orbital/orbital-system 6.674e-11 0.5 0.5) w)))

(declare dump-error-artifacts! log-frame-error!)

(defn sim-loop
  "The dedicated simulation thread: drain intents → tick → publish → pace."
  [{:keys [world-atom intent-queue config-atom stop-atom service-state]}]
  (let [period-ns 16666667]
    (loop [iter (long 0)]
      (when-not @stop-atom
        (let [t0  (System/nanoTime)
              cfg @config-atom
              w0  (drain-intents @world-atom intent-queue)
              w1  (if (:ui/error-state cfg)
                    w0
                    (let [tick-fn (:tick-fn cfg default-tick-fn)
                          on-step (:on-step cfg identity)
                          tick?   (or (:genesis/time-scale w0)
                                      (zero? (mod iter (long (:sim-frame-interval cfg 1)))))]
                      (if-not tick?
                        w0
                        (try
                          (on-step (tick-fn w0))
                          (catch Throwable t
                            (let [tick (long (or (:tick w0) 0))
                                  dump (dump-error-artifacts! w0)]
                              (log-frame-error! t tick dump)
                              (swap! config-atom assoc :ui/error-state
                                     {:exception t :tick tick
                                      :timestamp (System/currentTimeMillis) :paths dump})
                              (swap! service-state assoc :error t)
                              w0))))))]
          (reset! world-atom w1)
          (let [elapsed  (- (System/nanoTime) t0)
                sleep-ms (quot (- period-ns elapsed) 1000000)]
            (when (pos? sleep-ms) (Thread/sleep sleep-ms)))
          (recur (inc iter)))))))

(declare sync-cursor-mode!)

(defn- sync-observer-focus-to-camera
  "In non-manual camera modes, snap the observer spark and its focus to the
   camera target.  In manual mode leave the focus under player control."
  [world camera ctx mode]
  (if-let [obs (player/get-observer world)]
    (if (= :manual mode)
      world
      (let [target (units/render->world ctx (:target camera))]
        (player/put-observer
         world
         (-> obs
             (assoc :position target)
             (assoc :focus-position target)
             (assoc :focus-radius (:focus-radius obs))
             (assoc :focus-intensity (:focus-intensity obs))))))
    world))

(defn- sync-cursor-mode!
  "Capture the cursor for third-person mouse-look, or free it
   (GLFW_CURSOR_NORMAL) whenever the menu needs clicks."
  [window config-atom cfg]
  (let [free? (or (not= :manual (:mode cfg :manual))
                  (boolean (:ui/active-domain cfg))
                  (boolean (:ui/cursor-free? cfg)))
        want  (if free? GLFW/GLFW_CURSOR_NORMAL GLFW/GLFW_CURSOR_DISABLED)]
    (when (not= want (:ui/applied-cursor cfg))
      (GLFW/glfwSetInputMode window GLFW/GLFW_CURSOR want)
      (swap! config-atom assoc :ui/applied-cursor want))))

(def ^:private error-dump-root
  "Directory for world + ledger dumps captured on frame errors."
  (io/file "/tmp" "gates-of-truth" "dumps"))

(defn- dump-error-artifacts!
  "Persist the current world and its event ledger to timestamped EDN files."
  [world]
  (try
    (.mkdirs error-dump-root)
    (let [tick  (long (or (:tick world) 0))
          ts    (System/currentTimeMillis)
          base  (format "truth-error-t%06d-%d" tick ts)
          wf    (io/file error-dump-root (str base "-world.edn"))
          lf    (io/file error-dump-root (str base "-ledger.edn"))
          meta-file  (io/file error-dump-root (str base "-meta.edn"))]
      (spit wf (pr-str (dissoc world :ledger)))
      (spit lf (pr-str (:ledger world)))
      (spit meta-file (pr-str {:tick tick :timestamp ts
                               :world-file (.getName wf)
                               :ledger-file (.getName lf)}))
      {:world-path  (.getAbsolutePath wf)
       :ledger-path (.getAbsolutePath lf)
       :meta-path   (.getAbsolutePath meta-file)})
    (catch Throwable t
      {:error (.getMessage t)})))

(defn- log-frame-error!
  "Emit a one-line error report including tick and dump paths."
  [err tick dump-paths]
  (binding [*out* *err*]
    (println (format "[FRAME ERROR] tick=%d world=%s ledger=%s"
                     tick (:world-path dump-paths) (:ledger-path dump-paths))))
  (.printStackTrace ^Throwable err))

(defn- render-error-frame!
  "Draw a red error banner covering the top of the window."
  [window config-atom err tick]
  (try
    (let [wbuf (int-array 1) hbuf (int-array 1)
          _    (GLFW/glfwGetFramebufferSize window wbuf hbuf)
          fb-w (max 1 (aget wbuf 0))
          fb-h (max 1 (aget hbuf 0))
          hud  (:hud-program @config-atom)
          msg  (or (.getMessage ^Throwable err) (str (class err)))
          lines (->> (str/split-lines msg)
                     (map #(subs % 0 (min 140 (count %))))
                     (take 8))
          pad  16.0
          header-h 28.0
          line-h   20.0
          box-h    (+ header-h (* line-h (count lines)) (* 2.0 pad))
          ndcx     (fn [px] (- (/ (* 2.0 (double px)) fb-w) 1.0))
          ndcy     (fn [py] (- 1.0 (/ (* 2.0 (double py)) fb-h)))
          rect     {:x0 (ndcx 0.0) :y0 (ndcy box-h) :x1 1.0 :y1 1.0
                    :color [0.15 0.02 0.02 0.92]}
          text     (concat
                    [{:text (format "SYSTEM ERROR  tick=%d" tick)
                      :x pad :y (+ pad 4.0) :scale 1.6 :color [1.0 0.25 0.25 1.0]}]
                    (map-indexed
                     (fn [i line]
                       {:text line
                        :x pad :y (+ pad header-h (* i line-h))
                        :scale 1.3 :color [1.0 0.7 0.7 1.0]})
                     lines))]
      (GL11/glViewport 0 0 fb-w fb-h)
      (GL11/glClearColor 0.02 0.02 0.05 1.0)
      (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
      (render/render-hud hud [rect])
      (render/render-text hud text fb-w fb-h))
    (catch Throwable t
      (binding [*out* *err*]
        (println "[FRAME ERROR] failed to draw error overlay:" (.getMessage t)))))
  (try
    (GLFW/glfwSwapBuffers window)
    (Thread/sleep 16)
    (catch Throwable _ nil)))

(defn- render-frame-once
  "Render one frame.  Returns truthy while the window should stay open."
  [{:keys [window world-atom camera-atom config-atom frame-atom time-atom
           last-t-atom keys-atom]}]
  (try
    (if-let [err-state (:ui/error-state @config-atom)]
      (do
        (render-error-frame! window config-atom (:exception err-state) (:tick err-state))
        true)
      (do
        (ensure-resources config-atom)
        (GLFW/glfwPollEvents)
        (let [cfg       @config-atom
              bodies-fn (:bodies-fn cfg render/bodies-from-world)
              now       (GLFW/glfwGetTime)
              wall-dt   (let [lt @last-t-atom]
                          (reset! last-t-atom now)
                          (if lt (min 0.1 (- now lt)) 0.016))
              _         (sync-cursor-mode! window config-atom cfg)
              _         (swap! frame-atom inc)
              _         (swap! time-atom + wall-dt)
              w         @world-atom
              bodies    (bodies-fn w)
              wbuf      (int-array 1)
              hbuf      (int-array 1)
              _         (GLFW/glfwGetFramebufferSize window wbuf hbuf)
              fb-w      (max 1 (aget wbuf 0))
              fb-h      (max 1 (aget hbuf 0))
              cam-settings cfg
              _         (when (= :manual (:mode cam-settings))
                          (let [ks @keys-atom
                                input {:forward (cond (ks GLFW/GLFW_KEY_W) 1.0 (ks GLFW/GLFW_KEY_S) -1.0 :else 0.0)
                                       :right   (cond (ks GLFW/GLFW_KEY_D) 1.0 (ks GLFW/GLFW_KEY_A) -1.0 :else 0.0)}]
                            (when (or (not= 0.0 (:forward input)) (not= 0.0 (:right input)))
                              (let [velocity (cam/observer-move-velocity @camera-atom input cam-settings)]
                                (swap! world-atom player/update-observer #(player/drift % velocity wall-dt))
                                (swap! world-atom player/update-observer
                                       (fn [o] (player/set-focus o (:position o) (:focus-radius o) (:focus-intensity o))))))))
              w         @world-atom
              _         (swap! camera-atom cam/update-camera-for-world w cam-settings)
              cam       @camera-atom
              ctx       (units/make-context cam {:width fb-w :height fb-h})
              [cur-sx cur-sy] (when-let [cur (:cursor cfg)]
                                (let [winw (int-array 1) winh (int-array 1)
                                      _    (GLFW/glfwGetWindowSize window winw winh)]
                                  [(* (double (first cur)) (/ (double fb-w) (max 1 (aget winw 0))))
                                   (* (double (second cur)) (/ (double fb-h) (max 1 (aget winh 0))))]))
              menu      (menu/menu-hud cfg w fb-w fb-h)
              over-menu? (boolean (and cur-sx (menu/over-regions? (:regions menu) cur-sx cur-sy)))
              _         (when (not= :manual (:mode cam-settings))
                          (swap! world-atom sync-observer-focus-to-camera cam ctx (:mode cam-settings)))
              _         (when-let [ar (:action-request cfg)]
                          (when-let [obs (player/get-observer @world-atom)]
                            (swap! world-atom intervention/place (:kind ar) (:focus-position obs)))
                          (swap! config-atom dissoc :action-request))
              _         (when-let [prn-val (:pick-request cfg)]
                          (let [winw (int-array 1) winh (int-array 1)
                                _    (GLFW/glfwGetWindowSize window winw winh)
                                sx   (* (double (:x prn-val)) (/ (double fb-w) (max 1 (aget winw 0))))
                                sy   (* (double (:y prn-val)) (/ (double fb-h) (max 1 (aget winh 0))))]
                            (if-let [hit (menu/hit-at (:hits menu) sx sy)]
                              (do
                                (when-let [wf (menu/world-action (:action hit))]
                                  (swap! world-atom wf))
                                (swap! config-atom #(cond-> (dissoc % :pick-request)
                                                      (nil? (menu/world-action (:action hit)))
                                                      (menu/apply-action (:action hit)))))
                              (let [eid (inspect/pick-entity ctx bodies sx sy)]
                                (swap! config-atom #(-> % (dissoc :pick-request)
                                                        (menu/apply-action [:ui/select-entity eid])))))))
              sel       (let [s (:selection @config-atom)]
                          (when (and s (inspect/selected-shape bodies s)) s))
              _         (when (and (:selection @config-atom) (nil? sel))
                          (swap! config-atom #(menu/apply-action % [:ui/select-entity nil])))
              _         (if-let [shape (and sel (inspect/selected-shape bodies sel))]
                          (swap! config-atom assoc :zoom-min
                                 (cam/min-approach-distance (:radius shape)))
                          (when (:zoom-min cfg)
                            (swap! config-atom dissoc :zoom-min)))
              hover     (when (and cur-sx (not over-menu?)) (inspect/pick-entity ctx bodies cur-sx cur-sy))
              overlay   (concat (when sel (inspect/selection-overlay-shapes ctx w sel bodies))
                                (inspect/hover-overlay-shapes ctx bodies hover sel)
                                (inspect/intervention-overlay-shapes ctx w))
              card      (when sel (inspect/inspector-card ctx w sel bodies))
              controls  (render/controls-hud w fb-w fb-h)
              view-bar  (render/view-bar-hud cfg cam fb-w fb-h)
              bodies    (if (seq overlay) (into (vec bodies) overlay) bodies)
              hud       (-> (vec (render/hud-rects-from-world w))
                            (into (:rects controls))
                            (into (:rects card))
                            (into (:rects view-bar))
                            (into (:rects menu)))
              hud-text  (concat (render/hud-text-from-world w)
                                (render/observer-hud-text w fb-w fb-h)
                                (:text controls)
                                (:text card)
                                (:text view-bar)
                                (:text menu))
              volume    (when (:volumetric? cfg true)
                          (render/frame-volume ctx w (:volume-program cfg)
                                               (:volume-res cfg :medium)
                                               (:volume-config cfg)))]
          (render/render-scene {:body-program (:body-program cfg)
                                :line-program (:line-program cfg)
                                :sprite-program (:sprite-program cfg)
                                :hud-program (:hud-program cfg)
                                :hud hud
                                :hud-text hud-text
                                :volume volume}
                               (:mesh cfg)
                               cam
                               fb-w fb-h
                               bodies
                               @time-atom)
          (render/delete-volume volume))
        (handle-screenshot-request world-atom config-atom)
        (GLFW/glfwSwapBuffers window)
        (Thread/sleep 16)
        (not (GLFW/glfwWindowShouldClose window))))
    (catch Throwable t
      (let [tick (long (or (:tick @world-atom) 0))
            dump (dump-error-artifacts! @world-atom)]
        (log-frame-error! t tick dump)
        (swap! config-atom assoc :ui/error-state
               {:exception t :tick tick :timestamp (System/currentTimeMillis) :paths dump})
        (render-error-frame! window config-atom t tick))
      true)))

(defn window-loop
  "The render thread.  Receives the world only through `world-intents` — an
   IntentAtom whose deref reads the sim thread's latest published world and
   whose swap! enqueues intents.  This thread never writes the world-atom."
  [{:keys [world-intents camera-atom config-atom stop-atom service-state]}]
  (try
    (render/init-glfw)
    (let [{:keys [width height]} @config-atom
          window     (render/create-window width height "Gates of Truth — Dev Window")
          frame-atom (atom 0)
          time-atom  (atom 0.0)
          last-t-atom (atom nil)
          ks         (atom {})]
      (swap! service-state assoc :window window)
      (GLFW/glfwSetInputMode window GLFW/GLFW_CURSOR GLFW/GLFW_CURSOR_DISABLED)
      (render/setup-input {:window window
                           :camera-atom camera-atom
                           :keys-atom ks
                           :config-atom config-atom
                           :world-atom world-intents})
      (loop []
        (when (and (not @stop-atom)
                   (render-frame-once {:window window
                                       :world-atom world-intents
                                       :camera-atom camera-atom
                                       :config-atom config-atom
                                       :frame-atom frame-atom
                                       :time-atom time-atom
                                       :last-t-atom last-t-atom
                                       :keys-atom ks}))
          (recur))))
    (catch Throwable t
      (swap! service-state assoc :error t)
      (throw t))))
