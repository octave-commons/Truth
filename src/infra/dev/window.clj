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
         (fn [{:keys [body-program particle-program line-program hud-program mesh subdivisions requested-subdivisions] :as cfg}]
           (let [subdivisions (or requested-subdivisions subdivisions 2)
                 cfg          (assoc cfg :subdivisions subdivisions)]
             (cond-> cfg
               (nil? body-program)
               (assoc :body-program (render/create-program))

               (nil? particle-program)
               (assoc :particle-program (render/create-particle-program))

               (nil? line-program)
               (assoc :line-program (render/create-line-program))

               (nil? hud-program)
               (assoc :hud-program (render/create-hud-program))

               (or (nil? mesh)
                   (not= subdivisions requested-subdivisions))
               (assoc :mesh (render/upload-mesh (render/make-sphere-mesh subdivisions))
                      :requested-subdivisions nil))))))

(defn- handle-screenshot-request [world-atom config-atom]
  (when-let [{:keys [path result]} (:screenshot-request @config-atom)]
    (try
      (render/render-to-file world-atom path)
      (deliver result {:ok true :path path})
      (catch Throwable t
        (deliver result {:error t}))
      (finally
        (swap! config-atom dissoc :screenshot-request)))))

(def ^:private default-tick-fn
  "Fallback per-tick world advance: pure gravity (the Sun/Earth/Moon demo)."
  (fn [w] ((orbital/orbital-system 6.674e-11 0.5 0.5) w)))

(defn- advance-sim!
  "Advance the simulation toward its target wall-clock rate over `wall-dt` real
   seconds, mutating `world-atom`.

   Phase 0 reports `:phase0/time-scale` (sim-seconds per real second) and an
   adaptive `:sim/dt`; both shrink as the system gains complexity, so the clock
   dilates and the steps refine together. We accumulate the sim-seconds owed and
   run as many `dt`-sized ticks as fit, capped per frame (and the accumulator
   clamped) so a stall or a tier change can never trigger a runaway burst.

   Worlds without a rate (e.g. the bare gravity demo) fall back to the original
   fixed `:sim-frame-interval` frame-skip."
  [world-atom config-atom frame-atom accum-atom wall-dt]
  (let [cfg     @config-atom
        tick-fn (:tick-fn cfg default-tick-fn)
        on-step (:on-step cfg identity)
        w       @world-atom]
    (if-let [rate (:phase0/time-scale w)]
      (let [dt  (double (or (:sim/dt w) 1.0e12))
            cap (long (:max-steps-per-frame cfg 8))]
        (swap! accum-atom #(min (* dt cap) (+ (double %) (* (double wall-dt) (double rate)))))
        (loop [n 0]
          (when (and (< n cap) (>= (double @accum-atom) dt))
            (swap! accum-atom - dt)
            (swap! world-atom (fn [w] (on-step (tick-fn w))))
            (recur (inc n)))))
      (let [interval (:sim-frame-interval cfg 1)]
        (when (zero? (mod @frame-atom interval))
          (swap! world-atom (fn [w] (on-step (tick-fn w)))))))))

(defn- render-frame-once [window world-atom camera-atom config-atom frame-atom time-atom accum-atom last-t-atom]
  (ensure-resources config-atom)
  (GLFW/glfwPollEvents)
  (let [cfg       @config-atom
        bodies-fn (:bodies-fn cfg render/bodies-from-world)
        now       (GLFW/glfwGetTime)
        wall-dt   (let [lt @last-t-atom]
                    (reset! last-t-atom now)
                    (if lt (min 0.1 (- now lt)) 0.016))]
    (advance-sim! world-atom config-atom frame-atom accum-atom wall-dt)
    (swap! frame-atom inc)
    (swap! time-atom + wall-dt)
    (let [bodies (bodies-fn @world-atom)
          ;; Use the live framebuffer size (not the logical window size) so the
          ;; scene fills a resized or HiDPI window instead of one corner.
          wbuf   (int-array 1)
          hbuf   (int-array 1)
          _      (GLFW/glfwGetFramebufferSize window wbuf hbuf)
          fb-w   (max 1 (aget wbuf 0))
          fb-h   (max 1 (aget hbuf 0))]
      (swap! camera-atom render/update-camera-for-world @world-atom (:camera-settings cfg (render/default-camera-settings)))
      (render/render-scene {:body-program (:body-program cfg)
                            :particle-program (:particle-program cfg)
                            :line-program (:line-program cfg)
                            :hud-program (:hud-program cfg)
                            :hud (render/hud-rects-from-world @world-atom)
                            :hud-text (render/hud-text-from-world @world-atom)}
                           (:mesh cfg)
                           @camera-atom
                           fb-w fb-h
                           bodies
                           @time-atom)))
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
          accum-atom (atom 0.0)
          last-t-atom (atom nil)
          keys       (atom {})]
      (swap! service-state assoc :window window)
      (render/setup-input window camera-atom keys config-atom world-atom)
      (loop []
        (when (and (not @stop-atom)
                   (render-frame-once window world-atom camera-atom config-atom
                                      frame-atom time-atom accum-atom last-t-atom))
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
                                       :particle-program nil
                                       :line-program nil
                                       :mesh nil
                                       :subdivisions 3}
                                      (select-keys opts [:width :height :subdivisions
                                                         :tick-fn :bodies-fn
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
             (doseq [p [:body-program :particle-program :line-program :hud-program]]
               (delete-program (get cfg p)))
             (assoc cfg :body-program nil :particle-program nil :line-program nil :hud-program nil)))))

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
