(ns infra.render.phase0-renderer
  "LWJGL 3D renderer for Phase 0: Stellar Nebula visualization.
   Renders nebula clouds, forming stars, accretion disks, and the player sprite."
  (:require
   [shape.spatial         :as sp]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components  :as c]
   [domain.player         :as player])
  (:import
   [org.lwjgl.opengl GL46 GL11 GL15 GL20 GL30]
   [org.lwjgl.system MemoryStack]
   [java.nio FloatBuffer IntBuffer]))

;; --- Shader Sources ---

(def vertex-shader-source
  "#version 330 core
   layout (location = 0) in vec3 aPos;
   layout (location = 1) in vec3 aColor;
   layout (location = 2) in float aSize;
   
   out vec3 fragColor;
   out float pointSize;
   
   uniform mat4 projection;
   uniform mat4 view;
   uniform vec3 cameraPos;
   
   void main() {
       vec4 worldPos = vec4(aPos, 1.0);
       gl_Position = projection * view * worldPos;
       
       // Point size based on distance and intrinsic size
       float distance = length(cameraPos - aPos);
       pointSize = aSize / (1.0 + distance * 0.000001);
       gl_PointSize = clamp(pointSize, 1.0, 100.0);
       
       fragColor = aColor;
   }")

(def fragment-shader-source
  "#version 330 core
   in vec3 fragColor;
   in float pointSize;
   out vec4 FragColor;
   
   uniform float time;
   uniform float glow;
   
   void main() {
       // Soft circle sprite
       vec2 coord = gl_PointCoord - vec2(0.5);
       float dist = length(coord);
       
       if (dist > 0.5) discard;
       
       float alpha = 1.0 - smoothstep(0.0, 0.5, dist);
       alpha *= glow;
       
       // Add subtle pulsing
       float pulse = 1.0 + 0.1 * sin(time * 2.0);
       
       FragColor = vec4(fragColor * pulse, alpha);
   }")

(def nebula-shader-source
  "#version 330 core
   in vec3 fragColor;
   out vec4 FragColor;
   
   uniform float time;
   uniform float density;
   uniform vec3 nebulaCenter;
   
   // Simple noise function
   float noise(vec3 p) {
       return fract(sin(dot(p, vec3(12.9898, 78.233, 45.543))) * 43758.5453);
   }
   
   void main() {
       vec2 coord = gl_PointCoord - vec2(0.5);
       float dist = length(coord);
       
       if (dist > 0.5) discard;
       
       // Nebula effect with noise
       float n = noise(vec3(coord * 10.0, time * 0.1));
       float alpha = (1.0 - dist * 2.0) * density * (0.5 + 0.5 * n);
       
       vec3 color = fragColor * (1.0 + 0.3 * n);
       FragColor = vec4(color, alpha);
   }")

;; --- Shader Compilation ---

(defn compile-shader
  "Compile a shader from source"
  [source shader-type]
  (let [shader (GL20/glCreateShader shader-type)]
    (GL20/glShaderSource shader source)
    (GL20/glCompileShader shader)
    (when (= 0 (GL20/glGetShaderi shader GL20/GL_COMPILE_STATUS))
      (throw (Exception. (str "Shader compilation failed: " 
                             (GL20/glGetShaderInfoLog shader 1024)))))
    shader))

(defn create-shader-program
  "Create shader program from vertex and fragment sources"
  [vertex-source fragment-source]
  (let [vertex-shader (compile-shader vertex-source GL20/GL_VERTEX_SHADER)
        fragment-shader (compile-shader fragment-source GL20/GL_FRAGMENT_SHADER)
        program (GL20/glCreateProgram)]
    (GL20/glAttachShader program vertex-shader)
    (GL20/glAttachShader program fragment-shader)
    (GL20/glLinkProgram program)
    (when (= 0 (GL20/glGetProgrami program GL20/GL_LINK_STATUS))
      (throw (Exception. (str "Program linking failed: "
                             (GL20/glGetProgramInfoLog program 1024)))))
    (GL20/glDeleteShader vertex-shader)
    (GL20/glDeleteShader fragment-shader)
    program))

;; --- Geometry Generation ---

(defn nebula-particles
  "Generate particle data for nebula cloud"
  [{:keys [center extent density composition focus-level]}]
  (let [num-particles (int (* 1000 (+ 0.1 focus-level)))
        particles (for [_ (range num-particles)]
                   (let [theta (* 2 Math/PI (rand))
                         phi (Math/acos (- (* 2 (rand)) 1))
                         r (* extent (Math/pow (rand) 0.5))
                         x (+ (first center) (* r (Math/sin phi) (Math/cos theta)))
                         y (+ (second center) (* r (Math/sin phi) (Math/sin theta)))
                         z (+ (nth center 2) (* r (Math/cos phi)))
                         ;; Color based on composition
                         h-frac (get composition :H 0.75)
                         he-frac (get composition :He 0.24)]
                     {:position [x y z]
                      :color [(* 0.8 h-frac)  ;; Reddish for hydrogen
                             (* 0.6 he-frac)  ;; Greenish for helium
                             0.9]             ;; Bluish overall
                      :size (* 100 (rand))}))]
    particles))

(defn body-to-particle
  "Convert a stellar body to particle data"
  [{:keys [position radius temperature state luminosity]}]
  (let [temp-color (cond
                    (> temperature 10000) [0.8 0.8 1.0]  ;; Blue-white
                    (> temperature 5000) [1.0 1.0 0.8]   ;; Yellow-white
                    (> temperature 3000) [1.0 0.8 0.6]   ;; Orange
                    :else [0.8 0.4 0.4])                 ;; Red
        size (Math/log10 (+ 1 radius))
        glow (if (> luminosity 0) 
              (Math/log10 (+ 1 luminosity))
              1.0)]
    {:position position
     :color (mapv #(* % glow) temp-color)
     :size size}))

(defn sprite-particle
  "Generate particle data for player sprite"
  [{:keys [position coherence focus-intensity]}]
  {:position position
   :color [(* coherence 0.9)
          (* coherence focus-intensity)
          coherence]
   :size (* 50 coherence)})

;; --- Buffer Management ---

(defn create-vao
  "Create Vertex Array Object with particle data"
  [particles]
  (let [vao (GL30/glGenVertexArrays)
        vbo (GL15/glGenBuffers)
        num-particles (count particles)
        ;; Flatten particle data
        positions (float-array (mapcat :position particles))
        colors (float-array (mapcat :color particles))
        sizes (float-array (map :size particles))]
    
    (GL30/glBindVertexArray vao)
    
    ;; Position buffer
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
    (GL15/glBufferData GL15/GL_ARRAY_BUFFER positions GL15/GL_STATIC_DRAW)
    (GL20/glVertexAttribPointer 0 3 GL11/GL_FLOAT false 0 0)
    (GL20/glEnableVertexAttribArray 0)
    
    ;; Color buffer
    (let [color-vbo (GL15/glGenBuffers)]
      (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER color-vbo)
      (GL15/glBufferData GL15/GL_ARRAY_BUFFER colors GL15/GL_STATIC_DRAW)
      (GL20/glVertexAttribPointer 1 3 GL11/GL_FLOAT false 0 0)
      (GL20/glEnableVertexAttribArray 1))
    
    ;; Size buffer
    (let [size-vbo (GL15/glGenBuffers)]
      (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER size-vbo)
      (GL15/glBufferData GL15/GL_ARRAY_BUFFER sizes GL15/GL_STATIC_DRAW)
      (GL20/glVertexAttribPointer 2 1 GL11/GL_FLOAT false 0 0)
      (GL20/glEnableVertexAttribArray 2))
    
    (GL30/glBindVertexArray 0)
    
    {:vao vao :count num-particles}))

;; --- Matrix Operations ---

(defn perspective-matrix
  "Create perspective projection matrix"
  [fov aspect near far]
  (let [f (/ 1.0 (Math/tan (/ (* fov Math/PI) 360.0)))
        nf (/ 1.0 (- near far))]
    (float-array
     [(/  f aspect) 0 0 0
      0 f 0 0
      0 0 (* (+ far near) nf) (* 2 far near nf)
      0 0 -1 0])))

(defn normalize-vec
  "Normalize a vector"
  [v]
  (let [length (sp/len v)]
    (if (> length 0)
      (sp/v* v (/ 1.0 length))
      v)))

(defn cross-vec
  "Cross product of two vectors"
  [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by))
   (- (* az bx) (* ax bz))
   (- (* ax by) (* ay bx))])

(defn look-at-matrix
  "Create look-at view matrix"
  [eye center up]
  (let [f (normalize-vec (sp/v- center eye))
        s (normalize-vec (cross-vec f up))
        u (cross-vec s f)]
    (float-array
     [(first s) (second s) (nth s 2) (- (sp/dot s eye))
      (first u) (second u) (nth u 2) (- (sp/dot u eye))
      (- (first f)) (- (second f)) (- (nth f 2)) (sp/dot f eye)
      0 0 0 1])))

;; --- Main Renderer ---

(defrecord Phase0Renderer
  [shader-program
   nebula-program
   projection-matrix
   view-matrix
   camera-position
   time])

(defn create-renderer
  "Initialize the Phase 0 renderer"
  [width height]
  (let [shader-program (create-shader-program vertex-shader-source 
                                              fragment-shader-source)
        nebula-program (create-shader-program vertex-shader-source
                                             nebula-shader-source)
        projection (perspective-matrix 60.0 (/ width height) 1e10 1e20)]
    (->Phase0Renderer
     shader-program
     nebula-program
     projection
     (look-at-matrix [0 0 1e18] [0 0 0] [0 1 0])
     [0 0 1e18]
     0.0)))

(defn update-camera
  "Update renderer camera position"
  [renderer camera-pos look-at]
  (assoc renderer
         :camera-position camera-pos
         :view-matrix (look-at-matrix camera-pos look-at [0 1 0])))

(defn world->body-particles
  "Project every resolved matter entity in the ECS world into a render particle."
  [world]
  (->> (ecs/entities-with world c/matter-state c/position)
       (map (fn [eid]
              (body-to-particle
               {:position    (ecs/get-component world eid c/position)
                :radius      (or (ecs/get-component world eid c/radius) 1.0)
                :temperature (or (ecs/get-component world eid c/temperature) 3.0)
                :state       (ecs/get-component world eid c/matter-state)
                :luminosity  (or (ecs/get-component world eid c/luminosity) 0.0)})))))

(defn render-frame
  "Render one frame of Phase 0 from the ECS world."
  [{:keys [shader-program projection-matrix view-matrix
           camera-position time] :as renderer}
   world]

  ;; Clear and setup
  (GL11/glClearColor 0.01 0.01 0.02 1.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (GL11/glEnable GL11/GL_BLEND)
  (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)
  (GL11/glEnable GL46/GL_PROGRAM_POINT_SIZE)

  ;; Render stellar bodies
  (GL20/glUseProgram shader-program)
  (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation shader-program "projection")
                          false projection-matrix)
  (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation shader-program "view")
                          false view-matrix)
  (GL20/glUniform3fv (GL20/glGetUniformLocation shader-program "cameraPos")
                     (float-array camera-position))
  (GL20/glUniform1f (GL20/glGetUniformLocation shader-program "time") time)
  (GL20/glUniform1f (GL20/glGetUniformLocation shader-program "glow") 1.0)
  (let [particles (world->body-particles world)
        {:keys [vao count]} (create-vao particles)]
    (GL30/glBindVertexArray vao)
    (GL11/glDrawArrays GL11/GL_POINTS 0 count)
    (GL30/glBindVertexArray 0))

  ;; Render player sprite
  (when-let [obs (player/get-observer world)]
    (GL20/glUniform1f (GL20/glGetUniformLocation shader-program "glow") 2.0)
    (let [{:keys [vao count]} (create-vao [(sprite-particle obs)])]
      (GL30/glBindVertexArray vao)
      (GL11/glDrawArrays GL11/GL_POINTS 0 count)
      (GL30/glBindVertexArray 0)))
  
  ;; Update time
  (assoc renderer :time (+ time 0.016)))