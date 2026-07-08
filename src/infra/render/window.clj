(ns infra.render.window
  "GLFW bootstrap, window loop, and off-screen capture for the dev renderer.
   All context lifecycle and file output live here; scene drawing is delegated
   to `infra.render.scene`."
  (:require
   [infra.camera :as cam]
   [infra.render.shader :as sh]
   [infra.render.mesh :as rmesh]
   [infra.render.input :as rinput]
   [infra.render.scene :as rscene]
   [infra.render.volume :as rvolume]
   [infra.render.hud :as rhud]
   [infra.render.units :as units]
   [domain.orbital.system :as orbital]
   [domain.arc :as arc])
  (:import
   (org.lwjgl.glfw GLFW Callbacks GLFWErrorCallback)
   (org.lwjgl.opengl GL GL11 GL30)
   (org.lwjgl.stb STBImageWrite)
   (org.lwjgl BufferUtils)
   (org.lwjgl.system MemoryUtil)
   (java.nio ByteBuffer)))

(defn create-program
  "Backward-compatible body program constructor."
  []
  (sh/program-id :body))

(defn create-particle-program
  "Backward-compatible particle program constructor."
  []
  (sh/program-id :particle))

(defn create-sprite-program
  "Backward-compatible sprite LOD program constructor."
  []
  (sh/program-id :sprite))

(defn create-line-program
  "Backward-compatible line program constructor."
  []
  (sh/program-id :line))

(defn create-hud-program
  "Backward-compatible HUD program constructor."
  []
  (sh/program-id :hud))

(defn init-glfw
  "Initialize GLFW and set core-profile OpenGL hints."
  []
  (GLFW/glfwSetErrorCallback (GLFWErrorCallback/createPrint System/err))
  (when-not (GLFW/glfwInit)
    (throw (RuntimeException. "Failed to initialize GLFW")))
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_CORE_PROFILE)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_FORWARD_COMPAT GL11/GL_TRUE))

(defn create-window
  "Create a visible GLFW window and make its context current."
  [width height title]
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_TRUE)
  (let [window (GLFW/glfwCreateWindow width height title MemoryUtil/NULL MemoryUtil/NULL)]
    (when (= window MemoryUtil/NULL)
      (throw (RuntimeException. "Failed to create GLFW window")))
    (GLFW/glfwMakeContextCurrent window)
    (GLFW/glfwSwapInterval 1)
    (GL/createCapabilities)
    window))

(defn- create-offscreen-window [width height]
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  (let [window (GLFW/glfwCreateWindow width height "offscreen" MemoryUtil/NULL MemoryUtil/NULL)]
    (when (= window MemoryUtil/NULL)
      (throw (RuntimeException. "Failed to create offscreen GLFW window")))
    (GLFW/glfwMakeContextCurrent window)
    (GL/createCapabilities)
    window))

(defn- create-fbo [width height]
  (let [fbo     (GL30/glGenFramebuffers)
        color   (GL11/glGenTextures)
        depth   (GL30/glGenRenderbuffers)]
    (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D color)
    (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA width height 0 GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE nil)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
    (GL30/glFramebufferTexture2D GL30/GL_FRAMEBUFFER GL30/GL_COLOR_ATTACHMENT0 GL11/GL_TEXTURE_2D color 0)
    (GL30/glBindRenderbuffer GL30/GL_RENDERBUFFER depth)
    (GL30/glRenderbufferStorage GL30/GL_RENDERBUFFER GL30/GL_DEPTH_COMPONENT24 width height)
    (GL30/glFramebufferRenderbuffer GL30/GL_FRAMEBUFFER GL30/GL_DEPTH_ATTACHMENT GL30/GL_RENDERBUFFER depth)
    (when (not= GL30/GL_FRAMEBUFFER_COMPLETE (GL30/glCheckFramebufferStatus GL30/GL_FRAMEBUFFER))
      (throw (RuntimeException. "Framebuffer incomplete")))
    (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
    {:fbo fbo :color color :depth depth}))

(defn- read-pixels [width height]
  (let [buf (ByteBuffer/allocateDirect (* width height 4))]
    (GL11/glReadPixels 0 0 width height GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buf)
    buf))

(defn- flip-rgba-vertical [^ByteBuffer buf width height]
  (let [row-len (* width 4)
        flipped (BufferUtils/createByteBuffer (* width height 4))]
    (doseq [row (range (dec height) -1 -1)]
      (let [src-row (.duplicate buf)]
        (.position src-row (* row row-len))
        (.limit src-row (+ (* row row-len) row-len))
        (.put flipped src-row)))
    (.flip flipped)
    flipped))

(defn- phase0-world? [world]
  (contains? world :genesis/sim-time))

(defn- resolve-tick-fn [phase0? tick-fn]
  (or tick-fn
      (if phase0?
        arc/tick-genesis
        (orbital/orbital-system 6.674e-11 0.5 0.5))))

(defn- resolve-bodies-fn [phase0? bodies-fn]
  (or bodies-fn
      (if phase0?
        rscene/phase0-bodies-from-world
        rscene/bodies-from-world)))

(defn- default-camera [phase0? world camera-mode]
  (if phase0?
    (cam/update-camera-for-world
     (cam/make-camera 60.0) world
     (assoc (cam/default-camera-settings)
            :mode (or camera-mode :fit-all) :smoothing 1.0))
    (cam/make-camera)))

(defn- resolve-camera [phase0? world camera camera-mode]
  (or camera (default-camera phase0? world camera-mode)))

(defn- make-programs []
  {:body     (create-program)
   :particle (create-particle-program)
   :sprite   (create-sprite-program)
   :line     (create-line-program)
   :hud      (create-hud-program)
   :volume-program (rvolume/create-volume-program)})

(defn- render-offscreen-scene
  [mesh camera width height {:keys [body line hud sprite particle]} fbo
   bodies hud-rects hud-text volume]
  (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER (:fbo fbo))
  (rscene/render-scene {:body-program body :line-program line :hud-program hud
                        :sprite-program sprite :particle-program particle
                        :hud hud-rects :hud-text hud-text :volume volume
                        :mesh-world mesh :camera camera :width width :height height
                        :bodies bodies :t 0.0})
  (rvolume/delete-volume volume))

(defn- write-png-flipped [path width height]
  (let [pixels  (read-pixels width height)
        flipped (flip-rgba-vertical pixels width height)]
    (STBImageWrite/stbi_write_png path width height 4 flipped (* width 4))))

(defn- cleanup-offscreen [window]
  (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
  (rvolume/reset-volume-cache!)
  (GLFW/glfwDestroyWindow window))

;; Suppressed: public API name `render-to-file` is a conversion-function
;; by Splint's heuristic but renaming it would break callers and external docs.
#_{:splint/disable [naming/conversion-functions]}
(defn render-to-file
  "Render the current world to a PNG file using an offscreen OpenGL context.
   Returns the path of the written image. Auto-detects Phase 0 worlds."
  ([world-atom path]
   (render-to-file world-atom path {}))
  ([world-atom path {:keys [tick-fn bodies-fn camera camera-mode volume-res volume-config]}]
   (println "Rendering offscreen frame to" path)
   (init-glfw)
   (let [width     1280
         height    720
         window    (create-offscreen-window width height)
         _         (sh/invalidate-all!)
         _         (sh/ensure-builtins!)
         _         (rvolume/reset-volume-cache!)
         programs  (make-programs)
         mesh      (rmesh/upload-mesh (rmesh/make-sphere-mesh 3))
         fbo       (create-fbo width height)
         w0        @world-atom
         phase0?   (phase0-world? w0)
         tick-fn   (resolve-tick-fn phase0? tick-fn)
         bodies-fn (resolve-bodies-fn phase0? bodies-fn)
         w         (swap! world-atom tick-fn)
         camera    (resolve-camera phase0? w camera camera-mode)
         ctx       (units/make-context camera {:width width :height height})
         bodies    (bodies-fn w)
         hud       (when phase0? (rscene/hud-rects-from-world w))
         hud-text  (when phase0? (rhud/hud-text-from-world w))
         volume    (rvolume/frame-volume {:ctx ctx :world w :program (:volume-program programs) :res (or volume-res :medium) :cfg volume-config})]
     (render-offscreen-scene mesh camera width height programs fbo bodies hud hud-text volume)
     (GL11/glFlush)
     (write-png-flipped path width height)
     (cleanup-offscreen window)
     path)))

(defn- render-one-frame!
  "Render one frame of the legacy single-threaded window loop."
  [window world-atom camera config-atom body-program line-program sprite-program particle-program mesh]
  (GLFW/glfwPollEvents)
  (swap! world-atom (orbital/orbital-system 6.674e-11 0.5 0.5))
  (swap! camera cam/update-camera-for-world @world-atom @config-atom)
  (let [bodies (rscene/bodies-from-world @world-atom)]
    (rscene/render-scene {:body-program body-program
                          :line-program line-program
                          :sprite-program sprite-program
                          :particle-program particle-program
                          :mesh-world mesh
                          :camera @camera
                          :width 1280
                          :height 720
                          :bodies bodies
                          :t 0.0}))
  (GLFW/glfwSwapBuffers window)
  (Thread/sleep 16))

(defn run-window
  "Legacy single-threaded window loop. Ticks a gravity demo and renders it."
  [world-atom]
  (println "Initializing GLFW...")
  (init-glfw)
  (let [width          1280
        height         720
        window         (create-window width height "Gates of Truth — 3D View")
        camera         (atom (cam/make-camera))
        ks             (atom {})
        body-program   (create-program)
        particle-program (create-particle-program)
        line-program   (create-line-program)
        sprite-program (create-sprite-program)
        sphere         (rmesh/make-sphere-mesh 2)
        mesh           (rmesh/upload-mesh sphere)
        config-atom    (atom (cam/default-camera-settings))]
    (println "Window created, entering render loop...")
    (rinput/setup-input {:window window :camera-atom camera :keys-atom ks :config-atom config-atom})
    (while (not (GLFW/glfwWindowShouldClose window))
      (render-one-frame! window world-atom camera config-atom body-program line-program sprite-program particle-program mesh))
    (println "Shutting down renderer...")
    (GLFW/glfwDestroyWindow window)
    (Callbacks/glfwFreeCallbacks window)
    (GLFW/glfwTerminate)
    (GLFW/glfwSetErrorCallback nil)))
