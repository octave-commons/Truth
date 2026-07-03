(ns gates-of-truth.bench.render
  "Graphics / renderer benchmarks.

   Measures the cost of projecting the ECS world into render shapes and of
   issuing the OpenGL draw calls. The goal is to separate rendering time from
   simulation time so we can answer: is the frame budget being eaten by the
   sim or by the GPU/CPU renderer?

   All rendering happens in an offscreen GLFW context so no visible window is
   needed and the benchmark can run headless."
  (:require
   [domain.genesis        :as genesis]
   [infra.render          :as render]
   [infra.render.units    :as units]
   [infra.camera          :as cam]
   [shape.spatial         :as sp])
  (:import
   (org.lwjgl.glfw GLFW)
   (org.lwjgl.opengl GL GL11 GL15 GL20 GL30)
   (org.lwjgl.system MemoryUtil)
   (org.lwjgl BufferUtils)))

;; ---------------------------------------------------------------------------
;; Offscreen GL setup (mirrors private helpers in infra.render)
;; ---------------------------------------------------------------------------

(defn- create-offscreen-window [width height]
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  (let [window (GLFW/glfwCreateWindow width height "bench" MemoryUtil/NULL MemoryUtil/NULL)]
    (when (= window MemoryUtil/NULL)
      (throw (RuntimeException. "Failed to create offscreen GLFW window")))
    (GLFW/glfwMakeContextCurrent window)
    (GL/createCapabilities)
    window))

(defn- create-fbo [width height]
  (let [fbo   (GL30/glGenFramebuffers)
        color (GL11/glGenTextures)
        depth (GL30/glGenRenderbuffers)]
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

(defn- delete-fbo [{:keys [fbo color depth]}]
  (GL30/glDeleteFramebuffers fbo)
  (GL11/glDeleteTextures color)
  (GL30/glDeleteRenderbuffers depth))

(defn- delete-mesh [{:keys [vao vbo]}]
  (GL30/glDeleteVertexArrays vao)
  (GL15/glDeleteBuffers vbo))

;; ---------------------------------------------------------------------------
;; Benchmark state
;; ---------------------------------------------------------------------------

(defn- make-render-state
  "Create and return all GL resources needed for a frame. The same resources are
   reused across benchmark iterations, matching the real dev window."
  [width height]
  (render/init-glfw)
  (let [window (create-offscreen-window width height)]
     {:window        window
      :width         width
      :height        height
      :body-program  (render/create-program)
      :line-program  (render/create-line-program)
      :sprite-program (render/create-sprite-program)
      :hud-program   (render/create-hud-program)
      :volume-program (render/create-volume-program)
      :mesh          (render/upload-mesh (render/make-sphere-mesh 2))
      :fbo           (create-fbo width height)}))

(defn- destroy-render-state [{:keys [window body-program line-program sprite-program
                                     hud-program volume-program mesh fbo]}]
  (render/delete-volume nil)
  (delete-mesh mesh)
  (delete-fbo fbo)
  (doseq [p [body-program line-program sprite-program hud-program volume-program]]
    (when (and p (pos? (int p)))
      (GL20/glDeleteProgram p)))
  (GLFW/glfwDestroyWindow window)
  (GLFW/glfwTerminate)
  (GLFW/glfwSetErrorCallback nil))

;; ---------------------------------------------------------------------------
;; Frame helpers
;; ---------------------------------------------------------------------------

(defn- camera-for-world [world]
  (cam/update-camera-for-world
   (cam/make-camera 60.0)
   world
   (assoc (cam/default-camera-settings)
          :mode :fit-all :smoothing 1.0)))

(defn- render-frame
  "Render one frame offscreen, including CPU projection and GPU draw.
   `volume?` true uses adaptive LOD froxels (the new default)."
  [rs world volume?]
  (let [camera (camera-for-world world)
        ctx    (units/make-context camera {:width (:width rs) :height (:height rs)})
        bodies (render/phase0-bodies-from-world world)
        volume (when volume?
                 (render/frame-volume ctx world (:volume-program rs) :medium))]
    (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER (:fbo (:fbo rs)))
    (render/render-scene {:body-program (:body-program rs)
                          :line-program (:line-program rs)
                          :sprite-program (:sprite-program rs)
                          :hud-program (:hud-program rs)
                          :hud (render/hud-rects-from-world world)
                          :hud-text (concat (render/hud-text-from-world world)
                                            (render/observer-hud-text world (:width rs) (:height rs)))
                          :volume volume}
                         (:mesh rs)
                         camera
                         (:width rs) (:height rs)
                         bodies
                         0.0)
    (render/delete-volume volume)
    (GL11/glFlush)
    (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
    nil))

;; ---------------------------------------------------------------------------
;; Benchmarks
;; ---------------------------------------------------------------------------

(defn run [quick-bench full-bench]
  (let [rs      (make-render-state 1280 720)
        w100    (genesis/create-world {:gas-count 100 :nebula-mass 4e29 :nebula-radius 1.0e16})
        w500    (genesis/create-world {:gas-count 500 :nebula-mass 2e30 :nebula-radius 1.5e16})
        w1000   (genesis/create-world {:gas-count 1000 :nebula-mass 4e30 :nebula-radius 2.0e16})]

    ;; --- CPU projection (cache cleared each iteration, as in real frame loop) ---
    (quick-bench "phase0-bodies-from-world (100 particles, cache miss)"
      (fn [] (render/clear-phase0-render-cache!)
            (render/phase0-bodies-from-world w100)))

    (quick-bench "phase0-bodies-from-world (500 particles, cache miss)"
      (fn [] (render/clear-phase0-render-cache!)
            (render/phase0-bodies-from-world w500)))

    (quick-bench "phase0-bodies-from-world (1000 particles, cache miss)"
      (fn [] (render/clear-phase0-render-cache!)
            (render/phase0-bodies-from-world w1000)))

    ;; --- Cached projection (same world twice) ---
    (quick-bench "phase0-bodies-from-world (500 particles, cached)"
      (fn [] (render/phase0-bodies-from-world w500)))

    ;; --- Camera update (also CPU, per-frame) ---
    (quick-bench "update-camera-for-world (500 particles)"
      (fn [] (camera-for-world w500)))

    ;; --- Full offscreen frame: adaptive LOD froxels (new default) ---
    (quick-bench "render-frame (100 particles, LOD froxels)"
      (fn [] (render-frame rs w100 true)))

    (quick-bench "render-frame (500 particles, LOD froxels)"
      (fn [] (render-frame rs w500 true)))

    (quick-bench "render-frame (1000 particles, LOD froxels)"
      (fn [] (render-frame rs w1000 true)))

    ;; --- Fixed froxel resolution scaling (500 particles) ---
    (println "\n  Froxel resolution scaling (500 particles):")
    (doseq [res [32 64 96 128]]
      (quick-bench (format "render-frame (500 particles, froxel res=%d)" res)
                 (fn [] (let [camera (camera-for-world w500)
                              ctx    (units/make-context camera {:width (:width rs) :height (:height rs)})
                              bodies (render/phase0-bodies-from-world w500)
                              volume (render/frame-volume ctx w500 (:volume-program rs) res)]
                 (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER (:fbo (:fbo rs)))
                 (render/render-scene {:body-program (:body-program rs)
                                        :line-program (:line-program rs)
                                        :sprite-program (:sprite-program rs)
                                        :hud-program (:hud-program rs)
                                        :hud []
                                        :hud-text []
                                        :volume volume}
                                       (:mesh rs)
                                       camera
                                       (:width rs) (:height rs)
                                       bodies
                                       0.0)
                 (render/delete-volume volume)
                 (GL11/glFlush)
                 (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
                 nil))))

    ;; --- Shape count ---
    (let [bodies (render/phase0-bodies-from-world w500)]
      (println (format "\n  Render shapes for 500-particle world: %d" (count bodies)))
      (println (format "    :body   %d" (count (filter #(= :body (:render-mode %)) bodies))))
      (println (format "    :particle %d" (count (filter #(= :particle (:render-mode %)) bodies))))
      (println (format "    :line   %d" (count (filter #(= :line (:render-mode %)) bodies)))))

    (println "\n  Render Budget Analysis:")
    (println "    Target: 16.6 ms for 60 Hz.")
    (println "    If render-frame < 16.6 ms and tick-world > 16.6 ms → sim is the bottleneck.")
    (println "    If render-frame > 16.6 ms → GPU/CPU renderer is the bottleneck.")
    (println "    Froxel resolution is now adaptive LOD; sprite fog has been removed.")

    (destroy-render-state rs)))
