(ns infra.render.scene.setup
  "OpenGL scene setup and draw-call orchestration.

   Builds camera matrices, partitions renderables, and dispatches the volume,
   particle, line, solid-body, sprite, and HUD passes."
  (:require
   [infra.camera :as cam]
   [infra.render.math :as rmath]
   [infra.render.mesh :as rmesh]
   [infra.render.color :as rcolor]
   [infra.render.hud :as rhud]
   [infra.render.volume :as rvolume]
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

(defn- camera-matrices
  "Projection and view matrices for the current camera."
  [camera width height]
  (let [near (max 1.0e-8 (min 0.1 (* 0.2 (double (or (:distance camera) 50.0)))))
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
    (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation particle-program "projection") false proj)
    (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation particle-program "view") false view)
    (let [[cx cy cz] (:position camera)]
      (GL20/glUniform3f (GL20/glGetUniformLocation particle-program "cameraPos") (float cx) (float cy) (float cz)))
    (GL20/glUniform1f (GL20/glGetUniformLocation particle-program "time") (float t))
    (GL11/glEnable GL11/GL_BLEND)
    (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)
    (GL11/glDepthMask false)
    (GL32/glEnable GL32/GL_PROGRAM_POINT_SIZE)
    (let [pm (rmesh/upload-particle-mesh (rmesh/make-particle-mesh particles))]
      (GL30/glBindVertexArray (:vao pm))
      (GL11/glDrawArrays GL11/GL_POINTS 0 (:count pm))
      (GL30/glBindVertexArray 0)
      (GL15/glDeleteBuffers (:vbo pm))
      (GL30/glDeleteVertexArrays (:vao pm)))
    (GL32/glDisable GL32/GL_PROGRAM_POINT_SIZE)
    (GL11/glDepthMask true)
    (GL11/glDisable GL11/GL_BLEND)))

(defn- render-lines-pass
  "Field line and reticle line pass."
  [line-program proj view lines]
  (when (and line-program (pos? (int line-program)) (seq lines))
    (GL20/glUseProgram line-program)
    (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation line-program "projection") false proj)
    (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation line-program "view") false view)
    (GL11/glEnable GL11/GL_BLEND)
    (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)
    (GL11/glDepthMask false)
    (GL11/glLineWidth 1.5)
    (let [lm (rmesh/upload-particle-mesh (rmesh/make-particle-mesh lines))]
      (GL30/glBindVertexArray (:vao lm))
      (GL11/glDrawArrays GL11/GL_LINES 0 (:count lm))
      (GL30/glBindVertexArray 0)
      (GL15/glDeleteBuffers (:vbo lm))
      (GL30/glDeleteVertexArrays (:vao lm)))
    (GL11/glDepthMask true)
    (GL11/glDisable GL11/GL_BLEND)))

(defn- render-solids-pass
  "Solid body sphere pass."
  [body-program mesh-world proj view camera solids]
  (GL11/glDisable GL11/GL_BLEND)
  (when (seq solids)
    (GL20/glUseProgram body-program)
    (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation body-program "projection") false proj)
    (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation body-program "view") false view)
    (let [cam-pos (:position camera)
          [cx cy cz] cam-pos
          cam-loc (GL20/glGetUniformLocation body-program "cameraPos")]
      (GL20/glUniform3f cam-loc (float cx) (float cy) (float cz))
      (GL30/glBindVertexArray (:vao mesh-world))
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
          (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation body-program "model") false model)
          (GL20/glUniform3f (GL20/glGetUniformLocation body-program "color") (float r) (float g) (float b))
          (GL20/glUniform3f (GL20/glGetUniformLocation body-program "accent") (float ar) (float ag) (float ab))
          (GL20/glUniform1f (GL20/glGetUniformLocation body-program "glow") (float glow))
          (GL20/glUniform1f (GL20/glGetUniformLocation body-program "seed") (float (or (:seed body) 0.0)))
          (GL20/glUniform1i (GL20/glGetUniformLocation body-program "surfaceType") (int (or (:surface body) 0)))
          (GL11/glDrawArrays GL11/GL_TRIANGLES 0 (:count mesh-world))))
      (GL30/glBindVertexArray 0))))

(defn- render-sprites-pass
  "Point-sprite LOD fallback for distant bodies."
  [sprite-program proj view sprites]
  (when (and sprite-program (pos? (int sprite-program)) (seq sprites))
    (GL20/glUseProgram sprite-program)
    (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation sprite-program "projection") false proj)
    (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation sprite-program "view") false view)
    (GL11/glEnable GL11/GL_BLEND)
    (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)
    (GL11/glDepthMask false)
    (GL32/glEnable GL32/GL_PROGRAM_POINT_SIZE)
    (let [sm (rmesh/upload-sprite-mesh (rmesh/make-sprite-mesh sprites))]
      (GL30/glBindVertexArray (:vao sm))
      (GL11/glDrawArrays GL11/GL_POINTS 0 (:count sm))
      (GL30/glBindVertexArray 0)
      (GL15/glDeleteBuffers (:vbo sm))
      (GL30/glDeleteVertexArrays (:vao sm)))
    (GL32/glDisable GL32/GL_PROGRAM_POINT_SIZE)
    (GL11/glDepthMask true)
    (GL11/glDisable GL11/GL_BLEND)))

(defn- render-hud-pass
  "2D HUD overlay pass."
  [hud-program hud hud-text width height]
  (rhud/render-hud hud-program hud)
  (rhud/render-text hud-program hud-text width height)
  (GL20/glUseProgram 0)
  (GL11/glDisable GL11/GL_BLEND))

(defn render-scene
  "Render a frame with volumetric fog particles and glowing 3D massive bodies.
   `bodies` is a sequence of render maps; `:render-mode` may be :particle,
   :body, :line, or :sprite."
  [{:keys [body-program line-program sprite-program particle-program hud-program hud hud-text volume
           mesh-world camera width height bodies t]}]
  (render-scene-setup width height)
  (let [{:keys [projection view]} (camera-matrices camera width height)
        {:keys [particles lines solids sprites]} (partition-renderables bodies camera width height)]
    (render-volume-pass volume camera width height)
    (render-particles-pass particle-program projection view camera t particles)
    (render-lines-pass line-program projection view lines)
    (render-solids-pass body-program mesh-world projection view camera solids)
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
