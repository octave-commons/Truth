(ns infra.render
  "Minimal LWJGL + OpenGL renderer for Gates of Truth.
   Renders ECS bodies as wireframe spheres with a controllable camera."
  (:require
    [domain.ecs.core :as ecs]
    [domain.ecs.components :as c]
    [domain.orbital.system :as orbital]
    [domain.phase0 :as phase0]
    [domain.player :as player]
    [domain.em :as em]
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
     vec3 surface = color * (0.15 + 0.35 * diff);
     vec3 glowColor = color * glow * (0.8 + 0.6 * fresnel);
     FragColor = vec4(surface + glowColor, 1.0);
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

(def ^:private line-vertex-shader
  "Pass-through line shader for magnetic field lines. No point-sprite logic, so
   it is safe for GL_LINES (gl_PointCoord is undefined for lines)."
  "#version 330 core
   layout(location = 0) in vec3 aPos;
   layout(location = 1) in vec3 aColor;
   out vec3 vColor;
   uniform mat4 view;
   uniform mat4 projection;
   void main() {
     vColor = aColor;
     gl_Position = projection * view * vec4(aPos, 1.0);
   }")

(def ^:private line-fragment-shader
  "#version 330 core
   in vec3 vColor;
   out vec4 FragColor;
   void main() { FragColor = vec4(vColor, 0.85); }")

(defn create-line-program []
  (println "Compiling line shaders...")
  (link-program (compile-shader line-vertex-shader GL20/GL_VERTEX_SHADER)
                (compile-shader line-fragment-shader GL20/GL_FRAGMENT_SHADER)))

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
;; Camera behaviour
;; ---------------------------------------------------------------------------

(def ^:const phase0-view-scale
  "World metres per render unit for the Phase 0 view."
  1.0e15)

(def ^:private camera-modes [:manual :track-largest-cluster :fit-all])

(defn default-camera-settings
  "Default in-game camera configuration. Mutate the window config's
   :camera-settings entry from the REPL, or use the key bindings in the dev
   window."
  []
  {:mode :track-largest-cluster
   :fit-margin 1.6
   :smoothing 0.06
   :fit-percentile 0.90
   :manual-yaw -90.0
   :manual-pitch -20.0})

(defn cycle-camera-mode
  "Advance to the next camera mode."
  [settings]
  (let [i (.indexOf camera-modes (:mode settings))
        n (count camera-modes)
        next-i (mod (inc i) n)]
    (assoc settings :mode (nth camera-modes next-i))))

(defn adjust-fit-margin
  "Scale the fit margin by `factor`, clamped to a sensible range."
  [settings factor]
  (update settings :fit-margin #(max 1.0 (min 4.0 (* % factor)))))

(defn- lerp "Linear interpolation between scalars a and b by t." [a b t]
  (+ a (* (- b a) t)))

(defn- vlerp "Component-wise lerp between 3-vectors a and b by t." [a b t]
  (mapv #(lerp %1 %2 t) a b))

(defn- vdist "Euclidean distance between two 3-vectors." [a b]
  (Math/sqrt (apply + (map #(* (- %1 %2) (- %1 %2)) a b))))

(defn- weighted-centroid
  "Mass-weighted centroid of [[position mass] ...] in render units."
  [bodies]
  (let [[sx sy sz m]
        (reduce (fn [[ax ay az am] [[x y z] m]]
                  [(+ ax (* x m)) (+ ay (* y m)) (+ az (* z m)) (+ am m)])
                [0.0 0.0 0.0 0.0] bodies)]
    (if (pos? m)
      [(/ sx m) (/ sy m) (/ sz m)]
      [0.0 0.0 0.0])))

(defn- bounding-radius
  "Radius of a sphere centered at `center` that contains all bodies."
  [center bodies]
  (if (seq bodies)
    (reduce max 0.0 (map #(vdist center (first %)) bodies))
    0.0))

(defn- bodies->render
  "Project ECS bodies into [[render-position mass] ...]."
  [world scale]
  (->> (ecs/all-of world c/position c/mass)
       (mapv (fn [[_ comps]]
               [(mapv #(/ (double %) scale) (comps c/position))
                (double (comps c/mass))]))))

(defn largest-mass-cluster
  "Find the densest mass cluster using a uniform grid. Returns
   {:center [x y z] :radius r :mass m} in render units.

   `cell-size` controls the clustering scale; pass a value comparable to the
   desired cluster radius (e.g. a few times the typical body separation)."
  [bodies cell-size]
  (if (empty? bodies)
    {:center [0.0 0.0 0.0] :radius 0.0 :mass 0.0}
    (let [cell (fn [[x y z]]
                 [(long (Math/floor (/ (double x) cell-size)))
                  (long (Math/floor (/ (double y) cell-size)))
                  (long (Math/floor (/ (double z) cell-size)))])
          grid (group-by (fn [[pos _]] (cell pos)) bodies)
          ;; include a body in the winning cell and its 26 neighbours so the
          ;; cluster does not get sliced at grid boundaries
          [win-cell _] (apply max-key #(reduce + 0.0 (map second (val %))) grid)
          [wx wy wz] win-cell
          neighbours (for [dx [-1 0 1] dy [-1 0 1] dz [-1 0 1]]
                       [(+ wx dx) (+ wy dy) (+ wz dz)])
          cluster-bodies (mapcat #(get grid %) neighbours)
          center (weighted-centroid cluster-bodies)
          radius (bounding-radius center cluster-bodies)
          total-mass (reduce + 0.0 (map second cluster-bodies))]
      {:center center :radius radius :mass total-mass})))

(defn fit-all-bounds
  "Bounding sphere containing `percentile` of bodies by distance from the
   overall centroid. Returns {:center [x y z] :radius r} in render units."
  [bodies percentile]
  (if (empty? bodies)
    {:center [0.0 0.0 0.0] :radius 0.0}
    (let [center (weighted-centroid bodies)
          rs     (sort (map #(vdist center (first %)) bodies))
          idx    (int (* (count rs) percentile))
          radius (nth rs (min idx (dec (count rs))))]
      {:center center :radius radius})))

(defn distance-for-radius
  "Orbit distance (render units) needed to fit a sphere of `radius` with the
   given field-of-view and margin."
  [radius fov-deg margin]
  (let [fov (deg->rad fov-deg)]
    (* radius margin (/ 1.0 (Math/tan (/ fov 2.0))))))

(defn update-camera-for-world
  "Update `camera` based on the world and camera settings. Pure: returns a new
   Camera. In :manual mode the camera is unchanged except for position recalc."
  [camera world settings]
  (case (:mode settings :track-largest-cluster)
    :manual
    (-> camera
        (assoc :yaw (double (:manual-yaw settings -90.0))
               :pitch (double (:manual-pitch settings -20.0)))
        update-camera-position)

    :track-largest-cluster
    (let [bodies (bodies->render world phase0-view-scale)
          ;; cell size ~ a few render units; with view-scale 1e15 this frames
          ;; the local star-forming region rather than the whole cloud.
          cluster (largest-mass-cluster bodies 8.0)
          target  (:center cluster [0.0 0.0 0.0])
          radius  (max 5.0 (:radius cluster))
          desired-dist (distance-for-radius radius 60.0 (:fit-margin settings 1.6))
          t       (double (:smoothing settings 0.06))
          target' (vlerp (:target camera [0.0 0.0 0.0]) target t)
          dist'   (lerp (:distance camera) desired-dist t)]
      (-> camera
          (assoc :target target'
                 :distance dist'
                 :yaw (double (:manual-yaw settings -90.0))
                 :pitch (double (:manual-pitch settings -20.0)))
          update-camera-position))

    :fit-all
    (let [bodies (bodies->render world phase0-view-scale)
          bounds (fit-all-bounds bodies (:fit-percentile settings 0.90))
          target (:center bounds [0.0 0.0 0.0])
          radius (max 10.0 (:radius bounds))
          desired-dist (distance-for-radius radius 60.0 (:fit-margin settings 1.6))
          t      (double (:smoothing settings 0.06))
          target' (vlerp (:target camera [0.0 0.0 0.0]) target t)
          dist'   (lerp (:distance camera) desired-dist t)]
      (-> camera
          (assoc :target target'
                 :distance dist'
                 :yaw (double (:manual-yaw settings -90.0))
                 :pitch (double (:manual-pitch settings -20.0)))
          update-camera-position))))

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

(defn setup-input [window camera-atom keys-atom config-atom]
  (GLFW/glfwSetKeyCallback
    window
    (proxy [GLFWKeyCallback] []
      (invoke [window key scancode action mods]
        (when (= action GLFW/GLFW_PRESS)
          (swap! keys-atom assoc key true))
        (when (= action GLFW/GLFW_RELEASE)
          (swap! keys-atom dissoc key))
        (when (and (= key GLFW/GLFW_KEY_ESCAPE) (= action GLFW/GLFW_PRESS))
          (GLFW/glfwSetWindowShouldClose window true))
        ;; Camera mode controls
        (when (and (= key GLFW/GLFW_KEY_C) (= action GLFW/GLFW_PRESS))
          (swap! config-atom cycle-camera-mode)
          (println "Camera mode:" (:mode @config-atom)))
        (when (and (= key GLFW/GLFW_KEY_LEFT_BRACKET) (= action GLFW/GLFW_PRESS))
          (swap! config-atom adjust-fit-margin 0.9)
          (println "Fit margin:" (:fit-margin @config-atom)))
        (when (and (= key GLFW/GLFW_KEY_RIGHT_BRACKET) (= action GLFW/GLFW_PRESS))
          (swap! config-atom adjust-fit-margin 1.1)
          (println "Fit margin:" (:fit-margin @config-atom)))
        (when (and (= key GLFW/GLFW_KEY_R) (= action GLFW/GLFW_PRESS))
          (reset! camera-atom (make-camera))
          (reset! config-atom (default-camera-settings))
          (println "Camera reset")))))
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

(defn- matter-visual-radius [state]
  (case state
    :star 2.0 :protostar 1.5 :planet 1.0 :debris 0.5 :nebula 0.4
    1.0))

(def regime-tint
  "Per-regime colour multiplier (kept for regime-view tooling and tests)."
  {:gravitationally-unstable [1.30 0.85 0.65]
   :mhd-dominated            [0.70 0.75 1.35]
   :gravity-hydro            [1.00 1.00 1.00]})

(defn tint-color
  "Multiply [r g b] by a regime tint, clamped to [0,1]."
  [color regime]
  (let [t (get regime-tint regime [1.0 1.0 1.0])]
    (mapv (fn [c m] (max 0.0 (min 1.0 (* (double c) (double m))))) color t)))

(def ^:private temp-stops
  ;; [x r g b] — colour ramp keyed on normalized log-temperature
  ;; 10 K … 1e8 K covers diffuse gas through hot stars
  [[0.0  0.20 0.15 0.55]    ; ~10 K  cold diffuse gas: dim blue-violet
   [0.18 0.55 0.35 0.75]    ; ~100 K warming: violet
   [0.35 0.90 0.30 0.55]    ; ~1e3 K: magenta
   [0.52 1.0  0.55 0.20]    ; ~1e4 K: orange
   [0.68 1.0  0.90 0.55]    ; ~1e5 K: yellow-white
   [0.82 1.0  0.95 0.90]    ; ~1e6 K: white
   [0.92 0.80 0.85 1.0]     ; ~1e7 K: pale blue
   [1.0  0.55 0.70 1.0]])   ; ~1e8 K: deep blue (stellar core)

(defn temp-color
  "Temperature (K) → RGB on a cold-violet → warm → white → hot-blue ramp,
   log-scaled over ~10 K … 1e8 K. Stars live at the blue-white end, hot debris
   in orange-yellow, cold nebula gas in violet-blue."
  [t]
  (let [x (max 0.0 (min 1.0 (/ (- (Math/log10 (max 1.0 (double (or t 10.0)))) 1.0) 7.0)))]
    (loop [stops temp-stops]
      (let [[x0 r0 g0 b0] (first stops)
            nxt           (second stops)]
        (cond
          (nil? nxt)          [r0 g0 b0]
          (> x (first nxt))   (recur (rest stops))
          :else (let [[x1 r1 g1 b1] nxt
                      f (/ (- x x0) (max 1e-9 (- x1 x0)))]
                  [(+ r0 (* (- r1 r0) f))
                   (+ g0 (* (- g1 g0) f))
                   (+ b0 (* (- b1 b0) f))]))))))

(defn- hash01
  "Deterministic [0,1) value from an integer key — for stable, non-shimmering
   per-entity jitter (same entity → same value every frame)."
  [n]
  (/ (double (mod (* (+ 1 (long n)) 2654435761) 1000003)) 1000003.0))

(defn- player-focus-level
  "Observer attention in 0..1, used to scale the fog sample budget."
  [world]
  (if-let [obs (player/get-observer world)]
    (max 0.0 (min 1.0 (* (:coherence obs 0.5) (:focus-intensity obs 0.5))))
    0.5))

(defn- fog-sample-count [extent focus]
  (int (* 120 (+ 0.3 focus) (Math/log10 (+ 10.0 (double extent))))))

(defn nebula-fog
  "Soft fog puffs through a clump, DETERMINISTIC in `seed` so the cloud is stable
   frame-to-frame (no per-frame RNG → no shimmer). center/extent in render units."
  [{:keys [center extent color count seed]}]
  (let [[cx cy cz] center
        rng (java.util.Random. (long (or seed 1)))]
    (mapv
     (fn [_]
       (let [theta (* 2 Math/PI (.nextDouble rng))
             phi   (Math/acos (- (* 2 (.nextDouble rng)) 1))
             r     (* (double extent) (Math/sqrt (.nextDouble rng)))]
         {:position [(+ cx (* r (Math/sin phi) (Math/cos theta)))
                     (+ cy (* r (Math/sin phi) (Math/sin theta)))
                     (+ cz (* r (Math/cos phi)))]
          :color color
          :size (+ 30.0 (* 40.0 (.nextDouble rng)))
          :render-mode :particle}))
     (range count))))

(defn field-line
  "Two endpoints for a clump's magnetic field line in render units. Brightness
   rises with field magnitude relative to the seed field, so a collapsing core's
   flux-frozen amplification reads as a brightening line. nil when no field."
  [center extent b-field]
  (when (and b-field (pos? (sp/len b-field)))
    (let [mag  (sp/len b-field)
          dir  (sp/v* b-field (/ 1.0 mag))
          half (sp/v* dir (* (double extent) 1.6))
          p0   (sp/v- center half)
          p1   (sp/v+ center half)
          glow (max 0.3 (min 1.0 (+ 0.3 (* 0.25 (Math/log10
                                                  (+ 1.0 (/ mag em/default-nebula-field)))))))
          color [(* 0.45 glow) (* 0.85 glow) (* 1.0 glow)]]
      [{:position (vec p0) :color color :size 1.0 :render-mode :line}
       {:position (vec p1) :color color :size 1.0 :render-mode :line}])))

(defn phase0-bodies-from-world
  "Project Phase 0 ECS matter entities into stylized, view-scaled render shapes,
   coloured by TEMPERATURE so the thermal field is visible:
     :nebula    → one soft fog puff (the diffuse cloud)
     :protostar → a compact bright cloud + a magnetic field line (contracting core)
     :star/:planet/:debris → a shaded body
   Per-entity jitter is deterministic, so nothing shimmers between frames.

   This is the ONLY Phase 0 render projection — one ECS world behind it."
  ([world] (phase0-bodies-from-world world phase0-view-scale))
  ([world scale]
   (let [focus (player-focus-level world)]
     (vec
      (mapcat
       (fn [eid]
         (let [state   (ecs/get-component world eid c/matter-state)
               [x y z] (ecs/get-component world eid c/position)
               center  [(/ x scale) (/ y scale) (/ z scale)]
               color   (temp-color (ecs/get-component world eid c/temperature))]
            (case state
              :nebula
              [{:position center :color color
                :size (+ 16.0 (* 24.0 (hash01 eid)))
                :render-mode :particle}]

              :star
              (let [extent (max 0.8 (/ (or (ecs/get-component world eid c/radius) scale)
                                      scale))
                    body   {:entity      eid
                            :position    center
                            :radius      3.0
                            :color       [1.0 0.95 0.8]
                            :kind        state
                            :render-mode :body}]
                ;; central photosphere + volumetric corona puffs + field line
                (concat
                 [body]
                 (nebula-fog {:center center :extent (* extent 6.0)
                              :color [0.60 0.75 1.0] :count 120 :seed eid})
                 (field-line center extent (ecs/get-component world eid c/b-field))))

              :protostar
              (let [extent (max 0.4 (/ (or (ecs/get-component world eid c/radius) scale)
                                       scale))]
                (concat
                 (nebula-fog {:center center :extent extent :color color
                              :count  (fog-sample-count extent focus) :seed eid})
                 (field-line center extent (ecs/get-component world eid c/b-field))))

              ;; :planet :debris → shaded body, coloured by temperature
              [{:entity      eid
                :position    center
                :radius      (matter-visual-radius state)
                :color       (mapv #(min 1.0 (+ 0.15 (* 0.85 (double %)))) color)
                :kind        state
                :render-mode :body}])))
       (ecs/entities-with world c/position c/matter-state))))))

(defn render-scene
  "Render a frame with volumetric fog particles and glowing 3D massive bodies.
   `bodies` is a sequence of render maps; `:render-mode` may be `:particle`
   (soft fog puff) or `:body` (shaded sphere). Default is `:body`."
  [{:keys [body-program particle-program line-program]} mesh-world camera width height bodies time]
  (GL11/glEnable GL11/GL_DEPTH_TEST)
  (GL11/glClearColor 0.02 0.02 0.05 1.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (let [proj (perspective 60.0 (/ width (float height)) 0.1 10000.0)
        view (look-at (:position camera) (:target camera) (sp/vec3 0.0 1.0 0.0))
        particles (filterv #(= :particle (:render-mode %)) bodies)
        lines     (filterv #(= :line (:render-mode %)) bodies)
        bodies    (remove #(#{:particle :line} (:render-mode %)) bodies)]
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
    ;; ---- pass 1b: magnetic field lines (alpha, over the fog) ----
    (when (and line-program (pos? (int line-program)) (seq lines))
      (GL20/glUseProgram line-program)
      (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation line-program "projection") false proj)
      (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation line-program "view") false view)
      (GL11/glEnable GL11/GL_BLEND)
      (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)
      (GL11/glDepthMask false)
      (GL11/glLineWidth 1.5)
      (let [lm (upload-particle-mesh (make-particle-mesh lines))]
        (GL30/glBindVertexArray (:vao lm))
        (GL11/glDrawArrays GL11/GL_LINES 0 (:count lm))
        (GL30/glBindVertexArray 0)
        (GL15/glDeleteBuffers (:vbo lm))
        (GL30/glDeleteVertexArrays (:vao lm)))
      (GL11/glDepthMask true)
      (GL11/glDisable GL11/GL_BLEND))
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
                    :star 2.0
                    :protostar 1.0
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
         line-program     (create-line-program)
         sphere  (make-sphere-mesh 3)
         mesh    (upload-mesh sphere)
         fbo     (create-fbo width height)]
     (let [w @world-atom
           phase0?   (contains? w :phase0/phase)
           tick-fn   (or tick-fn
                         (if phase0?
                           phase0/tick-world
                           (orbital/orbital-system 6.674e-11 0.5 0.5)))
           bodies-fn (or bodies-fn
                         (if phase0?
                           phase0-bodies-from-world
                           bodies-from-world))
           w (swap! world-atom tick-fn)
           camera  (if phase0? (make-camera 60.0) (make-camera))
           bodies (bodies-fn w)]
       (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER (:fbo fbo))
       (render-scene {:body-program body-program :particle-program particle-program
                      :line-program line-program}
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
  (let [width          1280
        height         720
        window         (create-window width height "Gates of Truth — 3D View")
        camera         (atom (make-camera))
        keys           (atom {})
        body-program   (create-program)
        particle-program (create-particle-program)
        sphere         (make-sphere-mesh 2)
        mesh           (upload-mesh sphere)
        config-atom    (atom (default-camera-settings))]
    (println "Window created, entering render loop...")
    (setup-input window camera keys config-atom)
    (loop []
      (when (not (GLFW/glfwWindowShouldClose window))
        (GLFW/glfwPollEvents)
        ;; Simulate one tick per frame
        (swap! world-atom (fn [w] ((orbital/orbital-system 6.674e-11 0.5 0.5) w)))
        (swap! camera update-camera-for-world @world-atom @config-atom)
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