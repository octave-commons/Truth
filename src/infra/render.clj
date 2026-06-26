(ns infra.render
  "Minimal LWJGL + OpenGL renderer for Gates of Truth.
   Renders ECS bodies as wireframe spheres with a controllable camera."
  (:require
    [domain.ecs.core :as ecs]
    [domain.ecs.components :as c]
    [domain.orbital.system :as orbital]
    [domain.particles.phase0 :as pphase0]
    [shape.spatial :as sp])
  (:import
    (org.lwjgl.glfw GLFW Callbacks GLFWErrorCallback GLFWKeyCallback GLFWCursorPosCallback GLFWScrollCallback)
    (org.lwjgl.opengl GL GL11 GL15 GL20 GL30)
    (org.lwjgl.stb STBImageWrite)
    (org.lwjgl.system MemoryUtil)
    (org.lwjgl BufferUtils)
    (java.nio ByteBuffer)))

;; ---------------------------------------------------------------------------
;; Math helpers
;; ---------------------------------------------------------------------------

(defn- deg->rad [d] (* d (/ Math/PI 180.0)))

(defn- normalize [[x y z]]
  (let [len (Math/sqrt (+ (* x x) (* y y) (* z z)))]
    (if (zero? len)
      [0.0 0.0 1.0]
      [(/ x len) (/ y len) (/ z len)])))

(defn- cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by))
   (- (* az bx) (* ax bz))
   (- (* ax by) (* ay bx))])

(defn- perspective [fov-deg aspect near far]
  (let [f (/ 1.0 (Math/tan (/ (deg->rad fov-deg) 2.0)))
        nf (/ 1.0 (- near far))]
    (float-array [(/ f aspect) 0.0 0.0 0.0
                  0.0 f 0.0 0.0
                  0.0 0.0 (* (+ far near) nf) -1.0
                  0.0 0.0 (* 2.0 far near nf) 0.0])))

(defn- look-at [eye center up]
  (let [f (normalize (mapv - center eye))
        s (normalize (cross f up))
        u (cross s f)]
    (float-array [(nth s 0) (nth u 0) (- (nth f 0)) 0.0
                  (nth s 1) (nth u 1) (- (nth f 1)) 0.0
                  (nth s 2) (nth u 2) (- (nth f 2)) 0.0
                  (- (sp/dot s eye)) (- (sp/dot u eye)) (sp/dot f eye) 1.0])))

(defn- translation-matrix [[x y z]]
  (float-array [1.0 0.0 0.0 0.0
                0.0 1.0 0.0 0.0
                0.0 0.0 1.0 0.0
                x   y   z   1.0]))

(defn- scale-matrix [s]
  (float-array [s   0.0 0.0 0.0
                0.0 s   0.0 0.0
                0.0 0.0 s   0.0
                0.0 0.0 0.0 1.0]))

(defn- mat4* [a b]
  (let [out (float-array 16)]
    (doseq [i (range 4)
            j (range 4)]
      (aset out (+ (* i 4) j)
            (float (+ (* (aget a (+ (* i 4) 0)) (aget b (+ (* 0 4) j)))
                      (* (aget a (+ (* i 4) 1)) (aget b (+ (* 1 4) j)))
                      (* (aget a (+ (* i 4) 2)) (aget b (+ (* 2 4) j)))
                      (* (aget a (+ (* i 4) 3)) (aget b (+ (* 3 4) j)))))))
    out))

(defn- model-matrix [position radius]
  (mat4* (translation-matrix position) (scale-matrix radius)))

;; ---------------------------------------------------------------------------
;; Shader
;; ---------------------------------------------------------------------------

(def ^:private body-vertex-shader
  "#version 330 core
   layout(location = 0) in vec3 aPos;
   out vec3 vNormal;
   out vec3 vWorldPos;
   uniform mat4 model;
   uniform mat4 view;
   uniform mat4 projection;
   void main() {
     vNormal = mat3(transpose(inverse(model))) * aPos;
     vec4 worldPos = model * vec4(aPos, 1.0);
     vWorldPos = worldPos.xyz;
     gl_Position = projection * view * worldPos;
   }")

(def ^:private body-fragment-shader
  "#version 330 core
   in vec3 vNormal;
   in vec3 vWorldPos;
   out vec4 FragColor;
   uniform vec3 color;
   uniform vec3 cameraPos;
   uniform float glow;
   void main() {
     vec3 N = normalize(vNormal);
     vec3 V = normalize(cameraPos - vWorldPos);
     float diff = max(dot(N, V), 0.0);
     float fresnel = pow(1.0 - abs(dot(N, V)), 2.0);
     vec3 surface = color * (0.15 + 0.55 * diff);
     vec3 glowColor = color * glow * 0.6;
     FragColor = vec4(surface + glowColor * fresnel, 1.0);
   }")

(def ^:private particle-vertex-shader
  "#version 330 core
   layout(location = 0) in vec3 aPos;
   layout(location = 1) in vec3 aColor;
   layout(location = 2) in float aSize;
   out vec3 vColor;
   uniform mat4 view;
   uniform mat4 projection;
   uniform vec3 cameraPos;
   void main() {
     vColor = aColor;
     gl_Position = projection * view * vec4(aPos, 1.0);
     float dist = length(cameraPos - aPos);
     gl_PointSize = clamp(aSize / (1.0 + dist * 0.005), 2.0, 200.0);
   }")

(def ^:private particle-fragment-shader
  "#version 330 core
   in vec3 vColor;
   out vec4 FragColor;
   uniform float time;
   float hash(vec2 p) {
     return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
   }
   float noise(vec2 p) {
     vec2 i = floor(p);
     vec2 f = fract(p);
     f = f * f * (3.0 - 2.0 * f);
     float a = hash(i);
     float b = hash(i + vec2(1.0, 0.0));
     float c = hash(i + vec2(0.0, 1.0));
     float d = hash(i + vec2(1.0, 1.0));
     return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
   }
   void main() {
     vec2 coord = gl_PointCoord - vec2(0.5);
     float dist = length(coord);
     if (dist > 0.5) discard;
     float alpha = 1.0 - smoothstep(0.0, 0.5, dist);
     float n = noise(coord * 8.0 + time * 0.3);
     alpha *= (0.6 + 0.4 * n);
     float core = 1.0 - smoothstep(0.0, 0.18, dist);
     vec3 color = vColor * (0.3 + 0.7 * core);
     FragColor = vec4(color * alpha, alpha);
   }")

(defn compile-shader [source type]
  (let [id (GL20/glCreateShader type)]
    (GL20/glShaderSource id source)
    (GL20/glCompileShader id)
    (when (zero? (GL20/glGetShaderi id GL20/GL_COMPILE_STATUS))
      (throw (ex-info "Shader compile failed"
                      {:log (GL20/glGetShaderInfoLog id)})))
    id))

(defn- link-program [vs fs]
  (let [program (GL20/glCreateProgram)]
    (GL20/glAttachShader program vs)
    (GL20/glAttachShader program fs)
    (GL20/glLinkProgram program)
    (when (zero? (GL20/glGetProgrami program GL20/GL_LINK_STATUS))
      (throw (ex-info "Program link failed"
                      {:log (GL20/glGetProgramInfoLog program)})))
    (GL20/glDeleteShader vs)
    (GL20/glDeleteShader fs)
    program))

(defn create-program []
  (println "Compiling body shaders...")
  (link-program (compile-shader body-vertex-shader GL20/GL_VERTEX_SHADER)
                (compile-shader body-fragment-shader GL20/GL_FRAGMENT_SHADER)))

(defn create-particle-program []
  (println "Compiling particle shaders...")
  (link-program (compile-shader particle-vertex-shader GL20/GL_VERTEX_SHADER)
                (compile-shader particle-fragment-shader GL20/GL_FRAGMENT_SHADER)))

;; ---------------------------------------------------------------------------
;; Sphere mesh
;; ---------------------------------------------------------------------------

(defn- subdivide-icosahedron []
  (let [t     (/ (+ 1.0 (Math/sqrt 5.0)) 2.0)
        verts [[-1.0 t 0.0] [1.0 t 0.0] [-1.0 (- t) 0.0] [1.0 (- t) 0.0]
               [0.0 -1.0 t] [0.0 1.0 t] [0.0 -1.0 (- t)] [0.0 1.0 (- t)]
               [t 0.0 -1.0] [t 0.0 1.0] [(- t) 0.0 -1.0] [(- t) 0.0 1.0]]
        faces [[0 11 5] [0 5 1] [0 1 7] [0 7 10] [0 10 11]
               [1 5 9] [5 11 4] [11 10 2] [10 7 6] [7 1 8]
               [3 9 4] [3 4 2] [3 2 6] [3 6 8] [3 8 9]
               [4 9 5] [2 4 11] [6 2 10] [8 6 7] [9 8 1]]]
    {:verts (mapv normalize verts) :faces faces}))

(defn- midpoint [a b]
  (normalize (mapv + a b)))

(defn- refine-icosahedron [{:keys [verts faces]} times]
  (loop [verts verts faces faces n 0]
    (if (>= n times)
      {:verts verts :faces faces}
      (let [verts-atom (atom verts)
            mid-cache (atom {})
            get-mid (fn [i j]
                      (let [key (sort [i j])]
                        (or (@mid-cache key)
                            (let [idx (count @verts-atom)
                                  m   (midpoint (nth verts i) (nth verts j))]
                              (swap! mid-cache assoc key idx)
                              (swap! verts-atom conj m)
                              idx))))
            new-faces (vec (mapcat (fn [[i j k]]
                                     (let [a (get-mid i j)
                                           b (get-mid j k)
                                           c (get-mid k i)]
                                       [[i a c] [j b a] [k c b] [a b c]]))
                                   faces))]
        (recur @verts-atom new-faces (inc n))))))

(defn make-sphere-mesh [subdivisions]
  (let [{:keys [verts faces]} (refine-icosahedron (subdivide-icosahedron) subdivisions)
        face-verts (mapcat (fn [[i j k]] [(nth verts i) (nth verts j) (nth verts k)]) faces)
        fb         (BufferUtils/createFloatBuffer (* 3 (count faces) 3))]
    (doseq [[x y z] face-verts]
      (.put fb (float x)) (.put fb (float y)) (.put fb (float z)))
    (.flip fb)
    {:buffer fb
     :vertex-count (* 3 (count faces))}))

(defn upload-mesh [{:keys [buffer vertex-count]}]
  (let [vao (GL30/glGenVertexArrays)
        vbo (GL15/glGenBuffers)]
    (GL30/glBindVertexArray vao)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
    (GL15/glBufferData GL15/GL_ARRAY_BUFFER buffer GL15/GL_STATIC_DRAW)
    (GL20/glVertexAttribPointer 0 3 GL11/GL_FLOAT false 0 0)
    (GL20/glEnableVertexAttribArray 0)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
    (GL30/glBindVertexArray 0)
    {:vao vao :vbo vbo :count vertex-count}))

(defn- particle->floats [{:keys [position color size]}]
  (let [[x y z] position
        [r g b] color]
    [(float x) (float y) (float z)
     (float r) (float g) (float b)
     (float size)]))

(defn make-particle-mesh
  "Create a GPU buffer from a seq of particle maps. Each particle must have
   :position [x y z], :color [r g b], and :size."
  [particles]
  (let [data (float-array (mapcat particle->floats particles))
        fb   (BufferUtils/createFloatBuffer (count data))]
    (doseq [f data] (.put fb f))
    (.flip fb)
    {:buffer fb
     :count  (count particles)}))

(defn upload-particle-mesh
  "Upload an interleaved particle buffer (position 3, color 3, size 1)."
  [{:keys [buffer count]}]
  (let [vao (GL30/glGenVertexArrays)
        vbo (GL15/glGenBuffers)
        stride (* 7 4)]
    (GL30/glBindVertexArray vao)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
    (GL15/glBufferData GL15/GL_ARRAY_BUFFER buffer GL15/GL_STATIC_DRAW)
    ;; position
    (GL20/glVertexAttribPointer 0 3 GL11/GL_FLOAT false stride 0)
    (GL20/glEnableVertexAttribArray 0)
    ;; color
    (GL20/glVertexAttribPointer 1 3 GL11/GL_FLOAT false stride (* 3 4))
    (GL20/glEnableVertexAttribArray 1)
    ;; size
    (GL20/glVertexAttribPointer 2 1 GL11/GL_FLOAT false stride (* 6 4))
    (GL20/glEnableVertexAttribArray 2)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
    (GL30/glBindVertexArray 0)
    {:vao vao :vbo vbo :count count}))

;; ---------------------------------------------------------------------------
;; Renderer state
;; ---------------------------------------------------------------------------

(defrecord Camera
  [position yaw pitch distance target]
  )

(defn make-camera
  ([] (make-camera 50.0))
  ([distance]
   (->Camera (sp/vec3 0.0 (* distance 0.33) distance) -90.0 -20.0 distance (sp/vec3 0.0 0.0 0.0))))

(defn camera-forward [camera]
  (let [pitch-rad (deg->rad (:pitch camera))]
    [(Math/cos pitch-rad) (Math/sin pitch-rad) (Math/sin pitch-rad)]))

(defn update-camera-position [camera]
  (let [yaw-rad   (deg->rad (:yaw camera))
        pitch-rad (deg->rad (:pitch camera))
        d         (:distance camera)
        [tx ty tz] (:target camera)
        x (+ tx (* d (Math/cos pitch-rad) (Math/cos yaw-rad)))
        y (+ ty (* d (Math/sin pitch-rad)))
        z (+ tz (* d (Math/cos pitch-rad) (Math/sin yaw-rad)))]
    (assoc camera :position (sp/vec3 x y z))))

;; ---------------------------------------------------------------------------
;; Window + loop
;; ---------------------------------------------------------------------------

(defn init-glfw []
  (GLFW/glfwSetErrorCallback (GLFWErrorCallback/createPrint System/err))
  (when (not (GLFW/glfwInit))
    (throw (RuntimeException. "Failed to initialize GLFW")))
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_CORE_PROFILE)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_FORWARD_COMPAT GL11/GL_TRUE))

(defn create-window [width height title]
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_TRUE)
  (let [window (GLFW/glfwCreateWindow width height title MemoryUtil/NULL MemoryUtil/NULL)]
    (when (= window MemoryUtil/NULL)
      (throw (RuntimeException. "Failed to create GLFW window")))
    (GLFW/glfwMakeContextCurrent window)
    (GLFW/glfwSwapInterval 1)
    (GL/createCapabilities)
    window))

(defn setup-input [window camera-atom keys-atom]
  (GLFW/glfwSetKeyCallback
    window
    (proxy [GLFWKeyCallback] []
      (invoke [window key scancode action mods]
        (when (= action GLFW/GLFW_PRESS)
          (swap! keys-atom assoc key true))
        (when (= action GLFW/GLFW_RELEASE)
          (swap! keys-atom dissoc key))
        (when (and (= key GLFW/GLFW_KEY_ESCAPE) (= action GLFW/GLFW_PRESS))
          (GLFW/glfwSetWindowShouldClose window true)))))
  (let [last-pos (atom [0.0 0.0])
        first    (atom true)]
    (GLFW/glfwSetCursorPosCallback
      window
      (proxy [GLFWCursorPosCallback] []
        (invoke [window x y]
          (if @first
            (do (reset! last-pos [x y]) (reset! first false))
            (let [[lx ly] @last-pos
                  dx (- x lx)
                  dy (- y ly)]
              (reset! last-pos [x y])
              (when (= (GLFW/glfwGetMouseButton window GLFW/GLFW_MOUSE_BUTTON_LEFT) GLFW/GLFW_PRESS)
                (swap! camera-atom
                       (fn [c]
                         (-> c
                             (update :yaw #(+ % (* dx 0.2)))
                             (update :pitch #(max -89.0 (min 89.0 (- % (* dy 0.2)))))
                             update-camera-position))))))))))
  (GLFW/glfwSetScrollCallback
    window
    (proxy [GLFWScrollCallback] []
      (invoke [window xoffset yoffset]
        (swap! camera-atom
               (fn [c]
                 (-> c
                     (update :distance #(max 10.0 (min 2000.0 (- % (* yoffset 10.0)))))
                     update-camera-position)))))))

(defn bodies-from-world [world]
  (map (fn [[eid comps]]
         {:entity eid
          :position (comps c/position)
          :radius   (comps c/radius)
          :kind     (comps c/body-kind)})
       (ecs/all-of world c/position c/radius c/body-kind)))

(defn- body-color [kind]
  (case kind
    :body/star   [1.0 0.9 0.2]
    :body/planet [0.2 0.5 1.0]
    :body/debris [0.6 0.6 0.6]
    :body/moon   [0.8 0.8 0.8]
    :body/person [1.0 0.2 0.2]
    [0.7 0.7 0.7]))

;; --- Phase 0 projection -----------------------------------------------------
;; Phase 0 lives at astronomical scale (~1e17 m) with raw radii that range over
;; six orders of magnitude. We project it into a stylized, view-scaled space so
;; a forming solar system reads clearly: gas points, a brightening protostar,
;; an ignited star, and planets settling out.

(def ^:const phase0-view-scale
  "World metres per render unit for the Phase 0 view."
  1.0e15)

(defn- matter-color [state]
  (case state
    :star      [1.0 0.92 0.25]
    :protostar [1.0 0.55 0.15]
    :planet    [0.25 0.5 1.0]
    :nebula    [0.45 0.35 0.65]
    :debris    [0.6 0.6 0.6]
    [0.7 0.7 0.7]))

(defn- matter-visual-radius [state]
  (case state
    :star 2.0 :protostar 1.5 :planet 1.0 :debris 0.5 :nebula 0.4
    1.0))

(defn phase0-bodies-from-world
  "Project Phase 0 matter entities into stylized, view-scaled render bodies."
  ([world] (phase0-bodies-from-world world phase0-view-scale))
  ([world scale]
   (map (fn [[eid comps]]
          (let [state   (comps c/matter-state)
                [x y z] (comps c/position)]
            {:entity   eid
             :position [(/ x scale) (/ y scale) (/ z scale)]
             :radius   (matter-visual-radius state)
             :color    (matter-color state)
             :kind     state}))
        (ecs/all-of world c/position c/matter-state))))

(defn particle-phase0-bodies-from-world
  "Render bodies for the particle-field Phase 0: thousands of gas particles
   plus any resolved protostar / star / planet sinks promoted from the field.
   Positions are in natural units (cloud radius ~10), so the view scale is much
   smaller than the resolved-body Phase 0 projection."
  ([world] (particle-phase0-bodies-from-world world 1.5))
  ([world scale]
   (let [particles (for [p (pphase0/particle-bodies world)
                         :let [[x y z] (:position p)]]
                     (assoc p
                            :position [(/ x scale) (/ y scale) (/ z scale)]
                            :size     (/ (:size p) scale)
                            :render-mode :particle))
         resolved (map (fn [[eid comps]]
                         (let [state   (comps c/matter-state)
                               [x y z] (comps c/position)]
                           {:entity      eid
                            :position    [(/ x scale) (/ y scale) (/ z scale)]
                            :radius      (matter-visual-radius state)
                            :color       (matter-color state)
                            :kind        state
                            :render-mode :body}))
                       (ecs/all-of world c/position c/matter-state))]
     (vec (concat particles resolved)))))

(defn render-scene
  "Render a frame with volumetric fog particles and glowing 3D massive bodies.
   `bodies` is a sequence of render maps; `:render-mode` may be `:particle`
   (soft fog puff) or `:body` (shaded sphere). Default is `:body`."
  [{:keys [body-program particle-program]} mesh-world camera width height bodies time]
  (GL11/glEnable GL11/GL_DEPTH_TEST)
  (GL11/glClearColor 0.02 0.02 0.05 1.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (let [proj (perspective 60.0 (/ width (float height)) 0.1 10000.0)
        view (look-at (:position camera) (:target camera) (sp/vec3 0.0 1.0 0.0))
        particles (filterv #(= :particle (:render-mode %)) bodies)
        bodies    (remove #(= :particle (:render-mode %)) bodies)]
    ;; ---- pass 1: volumetric fog particles (additive, soft depth) ----
    (when (seq particles)
      (GL20/glUseProgram particle-program)
      (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation particle-program "projection") false proj)
      (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation particle-program "view") false view)
      (let [cam-pos (:position camera)
            [cx cy cz] cam-pos]
        (GL20/glUniform3f (GL20/glGetUniformLocation particle-program "cameraPos")
                          (float cx) (float cy) (float cz))
        (GL20/glUniform1f (GL20/glGetUniformLocation particle-program "time") (float time)))
      (GL11/glEnable GL11/GL_BLEND)
      (GL11/glBlendFunc GL11/GL_ONE GL11/GL_ONE)
      (GL11/glEnable 0x8642) ; GL_PROGRAM_POINT_SIZE
      (GL11/glDepthMask false)
      (let [pm (upload-particle-mesh (make-particle-mesh particles))]
        (GL30/glBindVertexArray (:vao pm))
        (GL11/glDrawArrays GL11/GL_POINTS 0 (:count pm))
        (GL30/glBindVertexArray 0)
        (GL15/glDeleteBuffers (:vbo pm))
        (GL30/glDeleteVertexArrays (:vao pm)))
      (GL11/glDepthMask true)
      (GL11/glDisable 0x8642)) ; GL_PROGRAM_POINT_SIZE
    ;; ---- pass 2: massive bodies as shaded 3D volumes ----
    (GL11/glDisable GL11/GL_BLEND)
    (when (seq bodies)
      (GL20/glUseProgram body-program)
      (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation body-program "projection") false proj)
      (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation body-program "view") false view)
      (let [cam-pos (:position camera)
            [cx cy cz] cam-pos
            cam-loc (GL20/glGetUniformLocation body-program "cameraPos")]
        (GL20/glUniform3f cam-loc (float cx) (float cy) (float cz))
        (GL30/glBindVertexArray (:vao mesh-world))
        (doseq [body bodies]
          (let [model (model-matrix (:position body) (max 0.5 (:radius body)))
                [r g b] (or (:color body) (body-color (:kind body)))
                glow (case (:kind body)
                       :star 0.8
                       :protostar 0.5
                       :planet 0.2
                       0.1)]
            (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation body-program "model") false model)
            (GL20/glUniform3f (GL20/glGetUniformLocation body-program "color") (float r) (float g) (float b))
            (GL20/glUniform1f (GL20/glGetUniformLocation body-program "glow") (float glow))
            (GL11/glDrawArrays GL11/GL_TRIANGLES 0 (:count mesh-world))))
        (GL30/glBindVertexArray 0)))
    (GL20/glUseProgram 0)
    (GL11/glDisable GL11/GL_BLEND)))

(defn render-bodies
  "Backward-compatible single-pass renderer for solid-color spheres.
   Prefer `render-scene` for particle fog + volume bodies."
  [program mesh-world camera width height bodies]
  (render-scene {:body-program program :particle-program 0}
                mesh-world camera width height
                (remove #(= :particle (:render-mode %)) bodies)
                0.0))

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
    ;; color texture
    (GL11/glBindTexture GL11/GL_TEXTURE_2D color)
    (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA width height 0 GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE nil)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
    (GL30/glFramebufferTexture2D GL30/GL_FRAMEBUFFER GL30/GL_COLOR_ATTACHMENT0 GL11/GL_TEXTURE_2D color 0)
    ;; depth renderbuffer
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

(defn render-to-file
  "Render the current world to a PNG file using an offscreen OpenGL context.
   Returns the path of the written image. Auto-detects Phase 0 worlds."
  ([world-atom path]
   (render-to-file world-atom path {}))
  ([world-atom path {:keys [tick-fn bodies-fn]}]
   (println "Rendering offscreen frame to" path)
   (init-glfw)
   (let [width   1280
         height  720
         window  (create-offscreen-window width height)
         body-program     (create-program)
         particle-program (create-particle-program)
         sphere  (make-sphere-mesh 3)
         mesh    (upload-mesh sphere)
         fbo     (create-fbo width height)]
     (let [w @world-atom
           tick-fn   (or tick-fn
                           (if (= :particle (:phase0/mode w))
                             pphase0/tick-world
                             (orbital/orbital-system 6.674e-11 0.5 0.5)))
           bodies-fn (or bodies-fn
                         (if (= :particle (:phase0/mode w))
                           particle-phase0-bodies-from-world
                           bodies-from-world))
           w (swap! world-atom tick-fn)
           camera  (if (= :particle (:phase0/mode w)) (make-camera 35.0) (make-camera))
           bodies (bodies-fn w)]
       (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER (:fbo fbo))
       (render-scene {:body-program body-program :particle-program particle-program}
                     mesh camera width height bodies 0.0))
     (GL11/glFlush)
     (let [pixels  (read-pixels width height)
           flipped (flip-rgba-vertical pixels width height)]
       (STBImageWrite/stbi_write_png path width height 4 flipped (* width 4)))
     (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
     (GLFW/glfwDestroyWindow window)
     (GLFW/glfwTerminate)
     (GLFW/glfwSetErrorCallback nil)
     path)))

(defn run-window [world-atom]
  (println "Initializing GLFW...")
  (init-glfw)
  (let [width  1280
        height 720
        window (create-window width height "Gates of Truth — 3D View")
        camera (atom (make-camera))
        keys   (atom {})
        body-program     (create-program)
        particle-program (create-particle-program)
        sphere (make-sphere-mesh 2)
        mesh   (upload-mesh sphere)]
    (println "Window created, entering render loop...")
    (setup-input window camera keys)
    (loop []
      (when (not (GLFW/glfwWindowShouldClose window))
        (GLFW/glfwPollEvents)
        ;; Simulate one tick per frame
        (swap! world-atom (fn [w] ((orbital/orbital-system 6.674e-11 0.5 0.5) w)))
        (let [bodies (bodies-from-world @world-atom)]
          (render-scene {:body-program body-program :particle-program particle-program}
                        mesh @camera width height bodies 0.0))
        (GLFW/glfwSwapBuffers window)
        (Thread/sleep 16)
        (recur)))
    (println "Shutting down renderer...")
    (GLFW/glfwDestroyWindow window)
    (Callbacks/glfwFreeCallbacks window)
    (GLFW/glfwTerminate)
    (GLFW/glfwSetErrorCallback nil)))
