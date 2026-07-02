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
     LMB drag       orbit (manual modes)
     scroll         adjust distance"
  (:require
    [domain.orbital.system :as orbital]
    [domain.player         :as player]
    [domain.intervention   :as intervention]
    [infra.inspect         :as inspect]
    [infra.render          :as render])
  (:import
    (org.lwjgl.glfw GLFW)
    (org.lwjgl.opengl GL15 GL20 GL30)))

(defonce service-state
  (atom nil))

(defn- delete-program [program]
  (when (and program (pos? program))
    (GL20/glDeleteProgram program)))

(defn- delete-mesh [{:keys [vao vbo]}]
  (when (and vao (pos? vao))
    (GL30/glDeleteVertexArrays vao))
  (when (and vbo (pos? vbo))
    (GL15/glDeleteBuffers vbo)))

(defn- ensure-resources [config-atom]
  (swap! config-atom
         (fn [{:keys [body-program line-program hud-program mesh subdivisions requested-subdivisions] :as cfg}]
           (let [subdivisions (or requested-subdivisions subdivisions 2)
                 cfg          (assoc cfg :subdivisions subdivisions)]
             (cond-> cfg
               (nil? body-program)
               (assoc :body-program (render/create-program))

               (nil? line-program)
               (assoc :line-program (render/create-line-program))

               (nil? hud-program)
               (assoc :hud-program (render/create-hud-program))

               (and (:volumetric? cfg true) (nil? (:volume-program cfg)))
               (assoc :volume-program (render/create-volume-program))

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

(defn- advance-sim!
  "Advance the simulation one fixed tick per rendered frame (~60 Hz), mutating
   `world-atom`.

   The tick RATE is constant: the game always steps exactly once per frame. What
   dilates with complexity is `:sim/dt` — the in-game time each tick advances
   (see `phase0/pacing-for`) — so the clock slows while the frame rate holds
   steady. There is no accumulator and no per-frame catch-up burst.

   Worlds without phase-0 pacing (e.g. the bare gravity demo) fall back to the
   fixed `:sim-frame-interval` frame-skip."
  [world-atom config-atom frame-atom]
  (let [cfg     @config-atom
        tick-fn (:tick-fn cfg default-tick-fn)
        on-step (:on-step cfg identity)
        w       @world-atom]
    (if (:phase0/time-scale w)
      (swap! world-atom (fn [w] (on-step (tick-fn w))))
      (let [interval (:sim-frame-interval cfg 1)]
        (when (zero? (mod @frame-atom interval))
          (swap! world-atom (fn [w] (on-step (tick-fn w)))))))))

(defn- render-frame-once [window world-atom camera-atom config-atom frame-atom time-atom last-t-atom]
  (ensure-resources config-atom)
  (GLFW/glfwPollEvents)
  (let [cfg       @config-atom
        bodies-fn (:bodies-fn cfg render/bodies-from-world)
        now       (GLFW/glfwGetTime)
        wall-dt   (let [lt @last-t-atom]
                    (reset! last-t-atom now)
                    (if lt (min 0.1 (- now lt)) 0.016))]
    (advance-sim! world-atom config-atom frame-atom)
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
          _      (swap! camera-atom render/update-camera-for-world w (:camera-settings cfg (render/default-camera-settings)))
          cam    @camera-atom
          ;; Live cursor in framebuffer pixels (scale window→fb for HiDPI), shared
          ;; by mouse-follow, hover, and click picking.
          [cur-sx cur-sy] (when-let [cur (:cursor cfg)]
                            (let [winw (int-array 1) winh (int-array 1)
                                  _    (GLFW/glfwGetWindowSize window winw winh)]
                              [(* (double (first cur)) (/ (double fb-w) (max 1 (aget winw 0))))
                               (* (double (second cur)) (/ (double fb-h) (max 1 (aget winh 0))))]))
          ;; PASSIVE, free: the spark's attention follows the mouse. Sets the
          ;; observer focus to the cursor's world point (reticle lags one frame —
          ;; imperceptible). Keeps the existing gentle pull-toward-focus as the
          ;; free 'observation' nudge; strong warps will be a paid action.
          _      (when cur-sx
                   (let [wp (inspect/cursor->world cam cur-sx cur-sy fb-w fb-h render/phase0-view-scale)]
                     (swap! world-atom player/update-observer
                            (fn [o] (player/set-focus o wp (:focus-radius o) (:focus-intensity o))))))
          ;; PAID: resolve a warp request (key G / Shift+G) at the cursor — spends
          ;; agency, no-op if unaffordable. The warp then bends nearby bodies.
          _      (when-let [ar (:action-request cfg)]
                   (when cur-sx
                     (let [wp (inspect/cursor->world cam cur-sx cur-sy fb-w fb-h render/phase0-view-scale)]
                       (swap! world-atom intervention/place (:kind ar) wp)))
                   (swap! config-atom dissoc :action-request))
          ;; Resolve a pending click into a selected entity (picks against the same
          ;; shapes drawn this frame). pr coords are window pixels → scale to fb.
          _      (when-let [prn-val (:pick-request cfg)]
                   (let [winw (int-array 1) winh (int-array 1)
                         _    (GLFW/glfwGetWindowSize window winw winh)
                         sx   (* (double (:x prn-val)) (/ (double fb-w) (max 1 (aget winw 0))))
                         sy   (* (double (:y prn-val)) (/ (double fb-h) (max 1 (aget winh 0))))
                         eid  (inspect/pick-entity bodies cam sx sy fb-w fb-h)]
                     (swap! config-atom #(-> % (dissoc :pick-request) (assoc :selection eid)))))
          ;; Selection survives only while its body still has a render shape;
          ;; once it merges/dissolves the selection clears itself.
          sel    (let [s (:selection @config-atom)]
                   (when (and s (inspect/selected-shape bodies s)) s))
          _      (when (and (:selection @config-atom) (nil? sel))
                   (swap! config-atom dissoc :selection))
          ;; Hover: faint halo on whatever the cursor is over (passive cue).
          hover    (when cur-sx (inspect/pick-entity bodies cam cur-sx cur-sy fb-w fb-h))
          overlay  (concat (when sel (inspect/selection-overlay-shapes w sel bodies cam))
                           (inspect/hover-overlay-shapes bodies hover sel cam)
                           (inspect/intervention-overlay-shapes w cam render/phase0-view-scale))
          card     (when sel (inspect/inspector-card w sel bodies cam fb-w fb-h))
          controls (render/controls-hud w fb-w fb-h)
          bodies   (if (seq overlay) (into (vec bodies) overlay) bodies)
          hud      (-> (vec (render/hud-rects-from-world w))
                       (into (:rects controls))
                       (into (:rects card)))
          hud-text (concat (render/hud-text-from-world w)
                           (render/observer-hud-text w fb-w fb-h)
                           (:text controls)
                           (:text card))
          volume   (when (:volumetric? cfg true)
                     (render/frame-volume w (:volume-program cfg)
                                          (:volume-res cfg :medium)))]
      (render/render-scene {:body-program (:body-program cfg)
                            :line-program (:line-program cfg)
                            :hud-program (:hud-program cfg)
                            :hud hud
                            :hud-text hud-text
                            :volume volume}
                           (:mesh cfg)
                           cam
                           fb-w fb-h
                           bodies
                           @time-atom)
      (render/delete-volume volume)))
  (handle-screenshot-request world-atom config-atom)
  (GLFW/glfwSwapBuffers window)
  (Thread/sleep 16)
  (not (GLFW/glfwWindowShouldClose window)))

(defn- window-loop [world-atom camera-atom config-atom stop-atom]
  (try
    (render/init-glfw)
    (let [{:keys [width height]} @config-atom
          window     (render/create-window width height "Gates of Truth — Dev Window")
          frame-atom (atom 0)
          time-atom  (atom 0.0)
          last-t-atom (atom nil)
          ks       (atom {})]
      (swap! service-state assoc :window window)
      (render/setup-input window camera-atom ks config-atom world-atom)
      (loop []
        (when (and (not @stop-atom)
                   (render-frame-once window world-atom camera-atom config-atom
                                      frame-atom time-atom last-t-atom))
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
         camera-atom    (atom (get opts :camera (render/make-camera)))
         config-atom    (atom (merge (render/default-camera-settings)
                                     {:width width :height height
                                      :body-program nil
                                      :line-program nil
                                      :hud-program nil
                                      :volume-program nil
                                      ;; volumetric ray-marched fog is the default look
                                      :volumetric? true
                                      :volume-res :medium
                                      :mesh nil
                                      :subdivisions 3}
                                     (select-keys opts [:width :height :subdivisions
                                                        :tick-fn :bodies-fn :volumetric? :volume-res
                                                        :sim-frame-interval :on-step])))
         stop-atom      (atom false)
         thread         (Thread. #(window-loop world-atom camera-atom config-atom stop-atom))]
     (.setDaemon thread true)
     (.setName thread "gates-of-truth-dev-window")
     (reset! service-state
             {:thread thread
              :stop   stop-atom
              :world  world-atom
              :camera camera-atom
              :config config-atom})
     (.start thread)
     (println "Dev window thread started on" (.getName thread))
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
  (reset! service-state nil)
  (println "Dev window stopped."))

(defn reload-shaders!
  "Force the window thread to recompile both shader programs on the next frame.
   Call this after editing `infra.render` shader vars from the REPL."
  []
  (when-let [config-atom (:config @service-state)]
    (swap! config-atom
           (fn [cfg]
             (doseq [p [:body-program :line-program :hud-program :volume-program]]
               (delete-program (get cfg p)))
             (assoc cfg :body-program nil :line-program nil
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
    (reset! camera-atom (render/make-camera)))
  (when-let [config-atom (:config @service-state)]
    (swap! config-atom merge (render/default-camera-settings))))

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
