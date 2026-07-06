(ns infra.dev.window
  "Live development window service.

   Runs a GLFW/OpenGL window on a dedicated daemon thread. The window
   continuously renders whatever is in the shared `world-atom`, using the
   shared `camera-atom` and a reloadable `renderer-config-atom`.

   Connect from another REPL (e.g. `clj -M:repl --connect localhost:7888`)
   and mutate the atoms to see changes in real time.

   Examples:
     (require '[infra.dev.window :as w])
     (reset! (:world @w/service-state) my-world)
     (swap! (:camera @w/service-state) assoc :distance 400.0)
     (w/reload-shaders!)   ; recompile after editing infra.render shader vars
     (w/reload-mesh! 3)    ; change sphere subdivision level
     (w/take-screenshot! \"/tmp/truth-dev.png\")

     Camera controls in the window:
     C              cycle camera mode (manual / track-largest-cluster / fit-all)
     [ / ]          decrease / increase fit margin
     R              reset camera and settings
     W / S          move the mote forward / backward relative to the camera
     A / D          strafe the mote left / right
     mouse          rotate the camera around the mote (third-person)
     scroll         adjust distance"
  (:require
   [clojure.java.io       :as io]
   [clojure.string        :as str]
   [domain.orbital.system :as orbital]
   [domain.player         :as player]
   [domain.intervention   :as intervention]
   [infra.inspect         :as inspect]
   [infra.render          :as render]
   [infra.render.shader   :as sh]
   [infra.render.units    :as units]
   [infra.menu            :as menu]
   [infra.camera          :as cam])
  (:import
   (org.lwjgl.glfw GLFW)
   (org.lwjgl.opengl GL11 GL15 GL30)))

(defonce service-state
  (atom nil))

(defn- delete-mesh [{:keys [vao vbo]}]
  (when (and vao (pos? vao))
    (GL30/glDeleteVertexArrays vao))
  (when (and vbo (pos? vbo))
    (GL15/glDeleteBuffers vbo)))

(defn- ensure-resources [config-atom]
  (swap! config-atom
         (fn [{:keys [body-program line-program sprite-program hud-program mesh subdivisions requested-subdivisions] :as cfg}]
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

;; --- Sim thread ≠ render thread (spec: perf-60fps-parallel-tick.md, Fix 1) ---
;;
;; The world-atom has exactly ONE writer: the sim thread. Everything the render
;; and input threads want to do to the world (observer drift, focus moves,
;; intervention placement) is enqueued as an INTENT — a pure world → world' fn —
;; and applied by the sim thread at the top of the next tick (≤ one tick period
;; later, and Jacobi-consistent: input lands between ticks, never mid-fold).
;;
;; `IntentAtom` lets the render/input code keep its existing `swap!` call sites
;; verbatim: it looks like an atom, but swap! enqueues instead of mutating, and
;; deref reads the latest world the sim thread published. swap!'s return value
;; is the CURRENT (pre-intent) world — fine for every existing caller, all of
;; which discard it. compareAndSet is deliberately unsupported.

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
  "Apply every queued intent to `w`, in arrival order. An intent that throws or
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

(declare dump-error-artifacts! log-frame-error!)

(defn- sim-loop
  "The dedicated simulation thread: drain intents → tick → publish → pace.

   Targets one tick per `period-ns` (16.6 ms — the fixed 60 Hz tick rate the
   pacing model assumes; `:sim/dt` dilation, not tick rate, is the game clock).
   Free-runs when a tick exceeds the period. Worlds without `:genesis/time-scale`
   (bare demos) tick every `:sim-frame-interval` iterations, as they previously
   ticked every Nth frame.

   On a tick error: dump world+ledger artifacts and set `:ui/error-state` (the
   render thread shows the error frame), then stop ticking — but keep draining
   intents so the queue cannot grow unboundedly."
  [world-atom ^java.util.concurrent.ConcurrentLinkedQueue queue config-atom stop-atom]
  (let [period-ns 16666667]
    (loop [iter (long 0)]
      (when-not @stop-atom
        (let [t0  (System/nanoTime)
              cfg @config-atom
              w0  (drain-intents @world-atom queue)
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
                              (swap! service-state assoc :error t))
                            w0)))))]
          (reset! world-atom w1)
          (let [elapsed  (- (System/nanoTime) t0)
                sleep-ms (quot (- period-ns elapsed) 1000000)]
            (when (pos? sleep-ms) (Thread/sleep sleep-ms)))
          (recur (inc iter)))))))

(defn- sync-cursor-mode!
  "Capture the cursor for third-person mouse-look, or free it
   (GLFW_CURSOR_NORMAL) whenever the menu needs clicks — a panel is open, the
   view is a tracking mode, or the cursor has been Tab-freed. Touches GLFW only
   when the desired mode changes (tracked via :ui/applied-cursor)."
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
  "Persist the current world (minus non-EDN GPU state) and its event ledger to
   timestamped EDN files under `error-dump-root`. Returns a map of paths, or
   `{:error ...}` if writing fails."
  [world]
  (try
    (.mkdirs error-dump-root)
    (let [tick  (long (or (:tick world) 0))
          ts    (System/currentTimeMillis)
          base  (format "truth-error-t%06d-%d" tick ts)
          wf    (io/file error-dump-root (str base "-world.edn"))
          lf    (io/file error-dump-root (str base "-ledger.edn"))
          meta  (io/file error-dump-root (str base "-meta.edn"))]
      (spit wf (pr-str (dissoc world :ledger)))
      (spit lf (pr-str (:ledger world)))
      (spit meta (pr-str {:tick tick :timestamp ts
                          :world-file (.getName wf)
                          :ledger-file (.getName lf)}))
      {:world-path  (.getAbsolutePath wf)
       :ledger-path (.getAbsolutePath lf)
       :meta-path   (.getAbsolutePath meta)})
    (catch Throwable t
      {:error (.getMessage t)})))

(defn- log-frame-error!
  "Emit a one-line error report including tick and dump paths, plus the full
   stack trace on stderr."
  [err tick dump-paths]
  (binding [*out* *err*]
    (println (format "[FRAME ERROR] tick=%d world=%s ledger=%s"
                     tick (:world-path dump-paths) (:ledger-path dump-paths))))
  (.printStackTrace ^Throwable err))

(defn- render-error-frame!
  "Draw a red error banner covering the top of the window. Safe enough to call
   from the frame catch block: it brings its own GL state and falls back to a
   plain clear+swap if anything fails."
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

(defn- render-frame-once [window world-atom camera-atom config-atom frame-atom time-atom last-t-atom keys-atom]
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
                          (if lt (min 0.1 (- now lt)) 0.016))]
          (sync-cursor-mode! window config-atom cfg)
          ;; The sim advances on its own thread (sim-loop); this thread only
          ;; renders the latest published world and enqueues input intents.
          (swap! frame-atom inc)
          (swap! time-atom + wall-dt)
          (let [w      @world-atom
                bodies (bodies-fn w)
                ;; Use the live framebuffer size (not the logical window size) so the
                ;; scene fills a resized or HiDPI window instead of one corner.
                wbuf   (int-array 1)
                hbuf   (int-array 1)
                _      (GLFW/glfwGetFramebufferSize window wbuf hbuf)
                fb-w   (max 1 (aget wbuf 0))
                fb-h   (max 1 (aget hbuf 0))
                cam-settings cfg
                _      (when (= :manual (:mode cam-settings))
                         (let [ks @keys-atom
                               input {:forward (cond (ks GLFW/GLFW_KEY_W) 1.0 (ks GLFW/GLFW_KEY_S) -1.0 :else 0.0)
                                      :right   (cond (ks GLFW/GLFW_KEY_D) 1.0 (ks GLFW/GLFW_KEY_A) -1.0 :else 0.0)}]
                           (when (or (not= 0.0 (:forward input)) (not= 0.0 (:right input)))
                             (let [velocity (cam/observer-move-velocity @camera-atom input cam-settings)]
                               (swap! world-atom player/update-observer #(player/drift % velocity wall-dt))
                               ;; In third-person mode the focus sphere travels with the mote.
                               (swap! world-atom player/update-observer
                                      (fn [o] (player/set-focus o (:position o) (:focus-radius o) (:focus-intensity o))))))))
                w      @world-atom
                _      (swap! camera-atom cam/update-camera-for-world w cam-settings)
                cam    @camera-atom
                ctx    (units/make-context cam {:width fb-w :height fb-h})
                ;; Live cursor in framebuffer pixels (scale window→fb for HiDPI), shared
                ;; by hover and click picking.
                [cur-sx cur-sy] (when-let [cur (:cursor cfg)]
                                  (let [winw (int-array 1) winh (int-array 1)
                                        _    (GLFW/glfwGetWindowSize window winw winh)]
                                    [(* (double (first cur)) (/ (double fb-w) (max 1 (aget winw 0))))
                                     (* (double (second cur)) (/ (double fb-h) (max 1 (aget winh 0))))]))
                ;; Top menu bar + open sub-view panel. Pure layout; :hits are click
                ;; targets and :regions are mouse-capture rects (bar + panel) that
                ;; suppress world hover/pick/focus so the shell doesn't leak into the sim.
                menu     (menu/menu-hud cfg w fb-w fb-h)
                over-menu? (boolean (and cur-sx (menu/over-regions? (:regions menu) cur-sx cur-sy)))
                ;; PASSIVE, free: in tracking modes the spark's attention follows the
                ;; mouse. In third-person manual mode the focus rides with the mote.
                _      (when (and cur-sx (not over-menu?) (not= :manual (:mode cam-settings)))
                         (let [wp (inspect/cursor->world ctx cur-sx cur-sy)]
                           (swap! world-atom player/update-observer
                                  (fn [o] (player/set-focus o wp (:focus-radius o) (:focus-intensity o))))))
                ;; PAID: resolve an ability request (key G / Shift+G / H / J) at the
                ;; spark avatar's position — spends agency, no-op if no observer or
                ;; unaffordable. The placed intervention then bends nearby bodies or
                ;; eases temperature.
                _      (when-let [ar (:action-request cfg)]
                         (when-let [obs (player/get-observer @world-atom)]
                           (swap! world-atom intervention/place (:kind ar) (:position obs)))
                         (swap! config-atom dissoc :action-request))
                ;; Resolve a pending click. A click on the menu bar / panel folds the
                ;; hit's :action into the config — unless it is a sim-side action
                ;; (Spark knobs), which is enqueued as a world intent so the sim
                ;; thread applies it between ticks. Otherwise it picks an entity in
                ;; the scene. pr coords are window pixels → scale to fb.
                _      (when-let [prn-val (:pick-request cfg)]
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
                             ;; Scene click: route through the SAME select action the
                             ;; explorer rows use, so a pick tethers the camera and a
                             ;; void-click releases it — one selection semantics.
                             (let [eid (inspect/pick-entity ctx bodies sx sy)]
                               (swap! config-atom #(-> % (dissoc :pick-request)
                                                       (menu/apply-action [:ui/select-entity eid])))))))
                ;; Selection survives only while its body still has a render shape;
                ;; once it merges/dissolves the selection clears itself (and releases
                ;; the camera tether).
                sel    (let [s (:selection @config-atom)]
                         (when (and s (inspect/selected-shape bodies s)) s))
                _      (when (and (:selection @config-atom) (nil? sel))
                         (swap! config-atom #(menu/apply-action % [:ui/select-entity nil])))
                ;; Approach floor for the scroll zoom: while tethered, the closest
                ;; orbit is a couple of body radii (render shapes carry render-unit
                ;; radii — no ECS read needed).
                _      (if-let [shape (and sel (inspect/selected-shape bodies sel))]
                         (swap! config-atom assoc :zoom-min
                                (cam/min-approach-distance (:radius shape)))
                         (when (:zoom-min cfg)
                           (swap! config-atom dissoc :zoom-min)))
                ;; Hover: faint halo on whatever the cursor is over (passive cue).
                hover    (when (and cur-sx (not over-menu?)) (inspect/pick-entity ctx bodies cur-sx cur-sy))
                overlay  (concat (when sel (inspect/selection-overlay-shapes ctx w sel bodies))
                                 (inspect/hover-overlay-shapes ctx bodies hover sel)
                                 (inspect/intervention-overlay-shapes ctx w))
                card     (when sel (inspect/inspector-card ctx w sel bodies))
                controls (render/controls-hud w fb-w fb-h)
                view-bar (render/view-bar-hud cfg cam fb-w fb-h)
                bodies   (if (seq overlay) (into (vec bodies) overlay) bodies)
                hud      (-> (vec (render/hud-rects-from-world w))
                             (into (:rects controls))
                             (into (:rects card))
                             (into (:rects view-bar))
                             (into (:rects menu)))
                hud-text (concat (render/hud-text-from-world w)
                                 (render/observer-hud-text w fb-w fb-h)
                                 (:text controls)
                                 (:text card)
                                 (:text view-bar)
                                 (:text menu))
                volume   (when (:volumetric? cfg true)
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
          (not (GLFW/glfwWindowShouldClose window)))))
    (catch Throwable t
      (let [tick (long (or (:tick @world-atom) 0))
            dump (dump-error-artifacts! @world-atom)]
        (log-frame-error! t tick dump)
        (swap! config-atom assoc :ui/error-state
               {:exception t :tick tick :timestamp (System/currentTimeMillis) :paths dump})
        (swap! service-state assoc :error t)
        (render-error-frame! window config-atom t tick))
      true)))

(defn- window-loop
  "The render thread. Receives the world only through `world-intents` — an
   IntentAtom whose deref reads the sim thread's latest published world and
   whose swap! enqueues intents. This thread never writes the world-atom."
  [world-intents camera-atom config-atom stop-atom]
  (try
    (render/init-glfw)
    (let [{:keys [width height]} @config-atom
          window     (render/create-window width height "Gates of Truth — Dev Window")
          frame-atom (atom 0)
          time-atom  (atom 0.0)
          last-t-atom (atom nil)
          ks       (atom {})]
      (swap! service-state assoc :window window)
      (GLFW/glfwSetInputMode window GLFW/GLFW_CURSOR GLFW/GLFW_CURSOR_DISABLED)
      (render/setup-input window camera-atom ks config-atom world-intents)
      (loop []
        (when (and (not @stop-atom)
                   (render-frame-once window world-intents camera-atom config-atom
                                      frame-atom time-atom last-t-atom ks))
          (recur))))
    (catch Throwable t
      (swap! service-state assoc :error t)
      (throw t))))

(defn start!
  "Start the dev window service on a daemon thread.
   Returns the service-state atom."
  ([world-atom]
   (start! world-atom {}))
  ([world-atom opts]
   (when @service-state
     (throw (IllegalStateException. "Dev window already running. Call stop! first.")))
   (let [width          (get opts :width 1280)
         height         (get opts :height 720)
         camera-atom    (atom (get opts :camera (cam/make-camera)))
         config-atom    (atom (merge (cam/default-camera-settings)
                                     {:width width :height height
                                      :body-program nil
                                      :line-program nil
                                      :sprite-program nil
                                      :hud-program nil
                                      :volume-program nil
                                      ;; volumetric ray-marched fog is the default look
                                      :volumetric? true
                                      :volume-res :medium
                                      :mesh nil
                                      :subdivisions 3}
                                     (select-keys opts [:width :height :subdivisions
                                                        :tick-fn :bodies-fn :volumetric? :volume-res
                                                        :volume-config
                                                        :sim-frame-interval :on-step])))
         stop-atom      (atom false)
         intent-queue   (java.util.concurrent.ConcurrentLinkedQueue.)
         world-intents  (IntentAtom. intent-queue world-atom)
         thread         (Thread. #(window-loop world-intents camera-atom config-atom stop-atom))
         sim-thread     (Thread. #(sim-loop world-atom intent-queue config-atom stop-atom))]
     (.setDaemon thread true)
     (.setName thread "gates-of-truth-dev-window")
     (.setDaemon sim-thread true)
     (.setName sim-thread "gates-of-truth-sim")
     (reset! service-state
             {:thread thread
              :sim-thread sim-thread
              :stop   stop-atom
              :world  world-atom
              :world-intents world-intents
              :camera camera-atom
              :config config-atom})
     (.start thread)
     (.start sim-thread)
     (println "Dev window thread started on" (.getName thread)
              "— sim thread on" (.getName sim-thread))
     service-state)))

(defn stop!
  "Signal the dev window service to shut down."
  []
  (when-let [stop (:stop @service-state)]
    (reset! stop true))
  (when-let [window (:window @service-state)]
    (GLFW/glfwSetWindowShouldClose window true))
  (when-let [thread (:thread @service-state)]
    (.join thread 5000))
  (when-let [sim-thread (:sim-thread @service-state)]
    (.join sim-thread 5000))
  (reset! service-state nil)
  (println "Dev window stopped."))

(defn reload-shaders!
  "Force the window thread to recompile shader programs on the next frame.
   Call this after editing `infra.render.shader` program vars from the REPL."
  []
  (sh/invalidate-all!)
  (when-let [config-atom (:config @service-state)]
    (swap! config-atom
           (fn [cfg]
             (assoc cfg :body-program nil :line-program nil :sprite-program nil
                    :hud-program nil :volume-program nil)))))

(defn reload-mesh!
  "Change the sphere subdivision level used for bodies."
  ([subdivisions]
   (when-let [config-atom (:config @service-state)]
     (swap! config-atom
            (fn [cfg]
              (when (not= subdivisions (:subdivisions cfg))
                (delete-mesh (:mesh cfg)))
              (assoc cfg :requested-subdivisions subdivisions))))))

(defn reset-camera!
  "Reset the camera and its settings to the default orbit position."
  []
  (when-let [camera-atom (:camera @service-state)]
    (reset! camera-atom (cam/make-camera)))
  (when-let [config-atom (:config @service-state)]
    (swap! config-atom merge (cam/default-camera-settings))))

(defn take-screenshot!
  "Request a screenshot and block until it has been written to `path`.
   The actual readback happens on the window thread, so this may take up
   to one frame plus file I/O time."
  [path]
  (when-let [config-atom (:config @service-state)]
    (let [result (promise)]
      (swap! config-atom assoc :screenshot-request {:path path :result result})
      @result
      path)))

(defn service-info
  "Return a read-only summary of the running service."
  []
  (when-let [s @service-state]
    {:running? true
     :thread   (.getName (:thread s))
     :world    (identical? (:world s) (some-> s :world deref))
     :camera   @(:camera s)
     :config   (select-keys @(:config s) [:width :height :subdivisions
                                          :mode :fit-margin :fit-percentile])}))
