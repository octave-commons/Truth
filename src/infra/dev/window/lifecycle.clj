(ns infra.dev.window.lifecycle
  "Lifecycle and public API for the live dev window service.

   Owns the global `service-state` atom, starts/stops the daemon window and sim
   threads, and exposes the REPL-facing helpers (reload-shaders!, reset-camera!,
   take-screenshot!, etc.). The per-frame logic lives in `infra.dev.window.loop`."
  (:require
   [infra.dev.window.loop :as loop]
   [infra.camera :as cam]
   [infra.render.shader :as sh])
  (:import
   (org.lwjgl.glfw GLFW)))

(defonce service-state
  (atom nil))

(defn- validate-not-running! []
  (when @service-state
    (throw (IllegalStateException.
            "Dev window already running. Call stop! first."))))

(defn- initial-config
  [opts width height]
  (atom (merge (cam/default-camera-settings)
               {:width width
                :height height
                :body-program nil
                :line-program nil
                :sprite-program nil
                :hud-program nil
                :volume-program nil
                :particle-program nil
                ;; volumetric ray-marched fog is the default look
                :volumetric? true
                :volume-res :medium
                :mesh nil
                :subdivisions 3}
               (select-keys opts [:width :height :subdivisions
                                  :tick-fn :bodies-fn :volumetric? :volume-res
                                  :volume-config
                                  :sim-frame-interval :on-step]))))

(defn- make-service-threads
  [world-intents camera-atom config-atom stop-atom world-atom intent-queue]
  {:window (doto (Thread. #(loop/window-loop {:world-intents world-intents
                                              :camera-atom camera-atom
                                              :config-atom config-atom
                                              :stop-atom stop-atom
                                              :service-state service-state}))
             (.setDaemon true)
             (.setName "gates-of-truth-dev-window"))
   :sim    (doto (Thread. #(loop/sim-loop {:world-atom world-atom
                                           :intent-queue intent-queue
                                           :config-atom config-atom
                                           :stop-atom stop-atom
                                           :service-state service-state}))
             (.setDaemon true)
             (.setName "gates-of-truth-sim"))})

(defn- launch-service!
  [state-atom threads world-atom world-intents camera-atom config-atom stop-atom]
  (reset! state-atom
          {:thread        (:window threads)
           :sim-thread    (:sim threads)
           :stop          stop-atom
           :world         world-atom
           :world-intents world-intents
           :camera        camera-atom
           :config        config-atom})
  (.start (:window threads))
  (.start (:sim threads))
  (println "Dev window thread started on" (.getName (:window threads))
           "— sim thread on" (.getName (:sim threads)))
  state-atom)

(defn start!
  "Start the dev window service on a daemon thread.
   Returns the service-state atom."
  ([world-atom]
   (start! world-atom {}))
  ([world-atom opts]
   (validate-not-running!)
   (let [width         (get opts :width 1280)
         height        (get opts :height 720)
         camera-atom   (atom (get opts :camera (cam/make-camera)))
         config-atom   (initial-config opts width height)
         stop-atom     (atom false)
         intent-queue  (java.util.concurrent.ConcurrentLinkedQueue.)
         world-intents (loop/->IntentAtom intent-queue world-atom)
         threads       (make-service-threads world-intents camera-atom config-atom
                                             stop-atom world-atom intent-queue)]
     (launch-service! service-state threads world-atom world-intents
                      camera-atom config-atom stop-atom))))

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
                    :hud-program nil :volume-program nil :particle-program nil)))))

(defn reload-mesh!
  "Change the sphere subdivision level used for bodies."
  ([subdivisions]
   (when-let [config-atom (:config @service-state)]
     (swap! config-atom
            (fn [cfg]
              (when (not= subdivisions (:subdivisions cfg))
                (loop/delete-mesh (:mesh cfg)))
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
