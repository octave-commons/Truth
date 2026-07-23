(ns infra.render.scene.setup
  "OpenGL scene setup and draw-call orchestration.

   Builds camera matrices, partitions renderables, and dispatches the volume,
   particle, line, solid-body, sprite, and HUD passes. Supports a
   `:render-origin` so close-up bodies can be rendered in a camera-relative
   coordinate system, avoiding single-precision jitter when the camera and body
   are far from the world origin."
  (:require
   [infra.camera :as cam]
   [infra.render.math :as rmath]
   [infra.render.mesh :as rmesh]
   [infra.render.color :as rcolor]
   [infra.render.hud :as rhud]
   [infra.render.volume :as rvolume]
   [infra.render.passes :as passes]
   [infra.render.material :as material]
   [infra.render.scene.bodies :as bodies]
   [shape.spatial :as sp])
  (:import
   (org.lwjgl.opengl GL11 GL15 GL20 GL30 GL32)))

(defn- render-scene-setup
  "Clear the framebuffer and set the viewport / depth state."
  [width height]
  (GL11/glViewport 0 0 (int width) (int height))
  (GL11/glEnable GL11/GL_DEPTH_TEST)
  (GL11/glClearColor 0.02 0.02 0.05 1.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT)))

(defn scene-far-plane
  "Dynamic far plane for a frame. Tied to the orbit distance, capped at 10000.0
   so wide views still work, and floored at 100.0 so the projection stays well
   conditioned."
  [camera]
  (max 100.0 (min 10000.0 (* 1000.0 (double (or (:distance camera) 50.0))))))

(defn- shift-camera
  "Subtract `origin` from the camera's position and target so the scene can be
   rendered in a camera-relative coordinate system."
  [camera origin]
  (-> camera
      (update :position sp/v- origin)
      (update :target sp/v- origin)))

(defn- shift-bodies
  "Subtract `origin` from the :position of every render shape."
  [bodies origin]
  (mapv #(update % :position sp/v- origin) bodies))

(defn- shift-volume
  "Shift a volume descriptor's box and light positions by `origin`."
  [volume origin]
  (when volume
    (-> volume
        (update :box-min sp/v- origin)
        (update :box-max sp/v- origin)
        (update :lights (fn [lights]
                          (mapv #(update % :pos sp/v- origin) lights))))))

(defn- camera-matrices
  "Projection and view matrices for the current camera. The near plane is a
   small fraction of the orbit distance so tight approaches to true-scale bodies
   do not clip the front face; it is floored to avoid a degenerate projection."
  [camera width height]
  (let [near (max 1.0e-9 (min 0.05 (* 0.01 (double (or (:distance camera) 50.0)))))
        far  (scene-far-plane camera)]
    {:projection (cam/perspective 60.0 (/ width (float height)) near far)
     :view       (cam/look-at (:position camera) (:target camera) (sp/vec3 0.0 0.0 1.0))}))

(defn- partition-renderables
  "Split bodies into particles, lines, solid bodies, and sprite proxies."
  [bodies camera _width height]
  (let [particles (filterv #(= :particle (:render-mode %)) bodies)
        lines     (filterv #(= :line (:render-mode %)) bodies)
        solids    (remove #(#{:particle :line} (:render-mode %)) bodies)
        [solids-lod sprites] (bodies/classify-body-lod solids camera height nil)]
    {:particles particles
     :lines     lines
     :solids    solids-lod
     :sprites   sprites}))

(defn- render-volume-pass
  "Full-screen volumetric fog pass."
  [volume camera width height]
  (when volume
    (let [{:keys [vao] :as quad} (rvolume/fullscreen-quad-vao)]
      (rvolume/render-volume {:volume volume :quad-vao vao :camera camera :width width :height height})
      (GL15/glDeleteBuffers (:vbo quad))
      (GL30/glDeleteVertexArrays vao))))

(defn- render-particles-pass
  "Soft particle pass for fog puffs."
  [particle-program proj view camera t particles]
  (when (and particle-program (pos? (int particle-program)) (seq particles))
    (GL20/glUseProgram particle-program)
    (let [[cx cy cz] (:position camera)]
      (passes/bind-uniforms! particle-program
                             {:projection proj :view view
                              :cameraPos [cx cy cz] :time t}))
    (passes/set-blend! :alpha)
    (passes/set-depth-write! false)
    (GL32/glEnable GL32/GL_PROGRAM_POINT_SIZE)
    (let [pm (rmesh/upload-particle-mesh (rmesh/make-particle-mesh particles))]
      (GL30/glBindVertexArray (:vao pm))
      (GL11/glDrawArrays GL11/GL_POINTS 0 (:count pm))
      (GL30/glBindVertexArray 0)
      (GL15/glDeleteBuffers (:vbo pm))
      (GL30/glDeleteVertexArrays (:vao pm)))
    (GL32/glDisable GL32/GL_PROGRAM_POINT_SIZE)
    (passes/set-depth-write! true)
    (passes/set-blend! :none)))

(defn- render-lines-pass
  "Field line and reticle line pass."
  [line-program proj view lines]
  (when (and line-program (pos? (int line-program)) (seq lines))
    (GL20/glUseProgram line-program)
    (passes/bind-uniforms! line-program {:projection proj :view view})
    (passes/set-blend! :alpha)
    (passes/set-depth-write! false)
    (GL11/glLineWidth 1.5)
    (let [lm (rmesh/upload-particle-mesh (rmesh/make-particle-mesh lines))]
      (GL30/glBindVertexArray (:vao lm))
      (GL11/glDrawArrays GL11/GL_LINES 0 (:count lm))
      (GL30/glBindVertexArray 0)
      (GL15/glDeleteBuffers (:vbo lm))
      (GL30/glDeleteVertexArrays (:vao lm)))
    (passes/set-depth-write! true)
    (passes/set-blend! :none)))

(defn- body-material
  "Material record for the solid-sphere body pass: opaque, depth-tested,
   `projection`/`view`/`cameraPos` fixed for the frame; per-body `model`,
   `color`, `accent`, `glow`, `seed`, `surfaceType` are supplied per draw via
   `draw-material!`'s `extra-uniforms`."
  [body-program proj view camera]
  (let [[cx cy cz] (:position camera)]
    (material/material
     {:program body-program
      :uniforms {:projection proj :view view :cameraPos [cx cy cz]}
      :mesh nil ;; assigned per draw call below (world sphere mesh varies by LOD)
      :blend :none
      :depth {:write? true :test? true}})))

(defn- render-solids-pass
  "Solid body sphere pass. Each body draws as one instance of the shared body
   material with its own model matrix, color, and surface uniforms layered on
   top via `draw-material!`'s `extra-uniforms`."
  [body-program mesh-world proj view camera solids]
  (passes/set-blend! :none)
  (when (seq solids)
    (let [mat (assoc (body-material body-program proj view camera) :mesh mesh-world)]
      (doseq [body solids]
        (let [model (if-let [ob (:oblateness body)]
                      (rmath/model-matrix (:position body)
                                          (double (:radius body))
                                          ob
                                          (:rotation-axis body))
                      (rmath/model-matrix (:position body) (double (:radius body))))
              [r g b] (or (:color body) (rcolor/body-color (:kind body)))
              [ar ag ab] (or (:accent body) [0.0 0.0 0.0])
              glow (double (or (:glow body) 0.1))]
          (material/draw-material!
           mat
           {:model model :color [r g b] :accent [ar ag ab] :glow glow
            :seed (double (or (:seed body) 0.0))
            :surfaceType (int (or (:surface body) 0))})))
      (GL30/glBindVertexArray 0))))

(defn- render-sprites-pass
  "Point-sprite LOD fallback for distant bodies."
  [sprite-program proj view sprites]
  (when (and sprite-program (pos? (int sprite-program)) (seq sprites))
    (GL20/glUseProgram sprite-program)
    (passes/bind-uniforms! sprite-program {:projection proj :view view})
    (passes/set-blend! :alpha)
    (passes/set-depth-write! false)
    (GL32/glEnable GL32/GL_PROGRAM_POINT_SIZE)
    (let [sm (rmesh/upload-sprite-mesh (rmesh/make-sprite-mesh sprites))]
      (GL30/glBindVertexArray (:vao sm))
      (GL11/glDrawArrays GL11/GL_POINTS 0 (:count sm))
      (GL30/glBindVertexArray 0)
      (GL15/glDeleteBuffers (:vbo sm))
      (GL30/glDeleteVertexArrays (:vao sm)))
    (GL32/glDisable GL32/GL_PROGRAM_POINT_SIZE)
    (passes/set-depth-write! true)
    (passes/set-blend! :none)))

(defn- render-hud-pass
  "2D HUD overlay pass."
  [hud-program hud hud-text width height]
  (rhud/render-hud hud-program hud)
  (rhud/render-text hud-program hud-text width height)
  (GL20/glUseProgram 0)
  (passes/set-blend! :none))

(defn render-scene
  "Render a frame with volumetric fog particles and glowing 3D massive bodies.
   `bodies` is a sequence of render maps; `:render-mode` may be :particle,
   :body, :line, or :sprite. Optional `:render-origin` shifts the camera and all
   positions into a camera-relative coordinate system, fixing single-precision
   jitter when zoomed in on a body far from the world origin."
  [{:keys [body-program line-program sprite-program particle-program hud-program hud hud-text volume
           mesh-world camera width height bodies t render-origin]}]
  (render-scene-setup width height)
  (let [origin  (or render-origin [0.0 0.0 0.0])
        camera' (shift-camera camera origin)
        bodies' (shift-bodies bodies origin)
        volume' (shift-volume volume origin)
        {:keys [projection view]} (camera-matrices camera' width height)
        {:keys [particles lines solids sprites]} (partition-renderables bodies' camera' width height)]
    (render-volume-pass volume' camera' width height)
    (render-particles-pass particle-program projection view camera' t particles)
    (render-lines-pass line-program projection view lines)
    (render-solids-pass body-program mesh-world projection view camera' solids)
    (render-sprites-pass sprite-program projection view sprites)
    (render-hud-pass hud-program hud hud-text width height)))

(defn render-bodies
  "Backward-compatible single-pass renderer for solid-color spheres."
  [{:keys [program mesh-world camera width height bodies]}]
  (render-scene {:body-program program
                 :mesh-world mesh-world
                 :camera camera
                 :width width
                 :height height
                 :bodies (remove #(= :particle (:render-mode %)) bodies)
                 :t 0.0}))
