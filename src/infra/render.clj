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
    (org.lwjgl.opengl GL GL11 GL12 GL13 GL15 GL20 GL30)
    (org.lwjgl.stb STBImageWrite STBEasyFont)
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

(defn- oblate-scale-matrix [a c]
  "Scale matrix for an oblate spheroid with equatorial radius a and polar radius c."
  (float-array [(double a) 0.0      0.0      0.0
                0.0      (double a) 0.0      0.0
                0.0      0.0      (double c) 0.0
                0.0      0.0      0.0        1.0]))

(defn- rotation-align-z [axis]
  "Rotation matrix (column-major) that aligns the mesh z-axis with `axis`."
  (let [n (normalize axis)
        helper (if (< (Math/abs (nth n 2)) 0.9) [0.0 0.0 1.0] [1.0 0.0 0.0])
        x (normalize (cross helper n))
        y (cross n x)
        [x0 x1 x2] x
        [y0 y1 y2] y
        [n0 n1 n2] n]
    (float-array [x0 x1 x2 0.0
                  y0 y1 y2 0.0
                  n0 n1 n2 0.0
                  0.0 0.0 0.0 1.0])))

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

(defn- model-matrix
  ([position radius]
   (mat4* (translation-matrix position) (scale-matrix radius)))
  ([position radius oblateness rotation-axis]
   (let [a (double radius)
         c (* a (double (or oblateness 1.0)))
         R (if (and rotation-axis (not= 1.0 c a))
             (rotation-align-z rotation-axis)
             (float-array [1.0 0.0 0.0 0.0
                           0.0 1.0 0.0 0.0
                           0.0 0.0 1.0 0.0
                           0.0 0.0 0.0 1.0]))]
     (mat4* (mat4* (translation-matrix position) R)
            (oblate-scale-matrix a c)))))

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
   layout(location = 3) in float aDensity;
   out vec3 vColor;
   out float vDensity;
   uniform mat4 view;
   uniform mat4 projection;
   uniform vec3 cameraPos;
   void main() {
     vColor = aColor;
     vDensity = aDensity;
     gl_Position = projection * view * vec4(aPos, 1.0);
     float dist = length(cameraPos - aPos);
     gl_PointSize = clamp(aSize / (1.0 + dist * 0.005), 2.0, 200.0);
   }")

(def ^:private particle-fragment-shader
  "#version 330 core
   in vec3 vColor;
   in float vDensity;
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
     float dens = clamp(vDensity, 0.0, 1.0);
     // Tighter, brighter core where density is high; softer, fainter tail where
     // the sample represents a larger low-density volume.
     float sigma = mix(0.32, 0.12, dens);
     float alpha = exp(-(dist * dist) / (2.0 * sigma * sigma));
     float n = noise(coord * 8.0 + time * 0.3);
     alpha *= (0.04 + 1.8 * pow(dens, 1.5)) * (0.35 + 0.65 * n);
     if (alpha < 0.003) discard;
     // Pull colour toward white as density rises so dense cores read hotter,
     // while keeping the base fog desaturated to avoid additive clipping.
     vec3 color = mix(vColor * 0.55, vec3(1.0), 0.28 * dens);
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

;; --- HUD overlay (2D, screen-space) -----------------------------------------
;; Filled rectangles given directly in normalized device coordinates [-1,1], so
;; the coherence bar and focus indicator sit fixed on screen regardless of camera.

(def ^:private hud-vertex-shader
  "#version 330 core
   layout(location = 0) in vec2 aPos;
   void main() { gl_Position = vec4(aPos, 0.0, 1.0); }")

(def ^:private hud-fragment-shader
  "#version 330 core
   out vec4 FragColor;
   uniform vec4 hudColor;
   void main() { FragColor = hudColor; }")

(defn create-hud-program []
  (println "Compiling HUD shaders...")
  (link-program (compile-shader hud-vertex-shader GL20/GL_VERTEX_SHADER)
                (compile-shader hud-fragment-shader GL20/GL_FRAGMENT_SHADER)))

(defn- hud-quad-floats [x0 y0 x1 y1]
  (float-array [x0 y0  x1 y0  x1 y1   x0 y0  x1 y1  x0 y1]))

(defn render-hud
  "Draw a list of HUD rectangles. Each rect is {:x0 :y0 :x1 :y1 :color [r g b a]}
   in NDC. No-op without a program or rects."
  [hud-program rects]
  (when (and hud-program (pos? (int hud-program)) (seq rects))
    (GL20/glUseProgram hud-program)
    (GL11/glEnable GL11/GL_BLEND)
    (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)
    (GL11/glDepthMask false)
    (let [loc (GL20/glGetUniformLocation hud-program "hudColor")]
      (doseq [{:keys [x0 y0 x1 y1 color]} rects]
        (let [[r g b a] color
              data (hud-quad-floats x0 y0 x1 y1)
              fb   (BufferUtils/createFloatBuffer (count data))
              vao  (GL30/glGenVertexArrays)
              vbo  (GL15/glGenBuffers)]
          (doseq [f data] (.put fb (float f)))
          (.flip fb)
          (GL30/glBindVertexArray vao)
          (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
          (GL15/glBufferData GL15/GL_ARRAY_BUFFER fb GL15/GL_STATIC_DRAW)
          (GL20/glVertexAttribPointer 0 2 GL11/GL_FLOAT false 0 0)
          (GL20/glEnableVertexAttribArray 0)
          (GL20/glUniform4f loc (float r) (float g) (float b) (float (or a 1.0)))
          (GL11/glDrawArrays GL11/GL_TRIANGLES 0 6)
          (GL30/glBindVertexArray 0)
          (GL15/glDeleteBuffers vbo)
          (GL30/glDeleteVertexArrays vao))))
    (GL11/glDepthMask true)
    (GL11/glDisable GL11/GL_BLEND)
    (GL20/glUseProgram 0)))

;; --- HUD text (STBEasyFont → NDC triangles, drawn by the HUD program) -------
;; STBEasyFont rasterizes ASCII into pixel-space quads (16 bytes/vertex: x,y,z
;; float + 4 colour bytes; 4 verts/quad). We keep only x,y, magnify by `scale`,
;; offset to a top-left pixel origin, convert to NDC, and feed triangles through
;; the same solid-colour HUD program — no extra shader, no font atlas, no deps
;; beyond lwjgl-stb (already present).

(defn- text->ndc-tris
  "Triangulate one line of `text` into a float-array of NDC (x,y) pairs.
   `x`/`y` are the line's top-left pixel origin, `scale` magnifies the ~7px base
   font, `w`/`h` are the framebuffer size. Returns [float-array vertex-count]."
  [^CharSequence text x y scale w h]
  (let [buf   (BufferUtils/createByteBuffer (max 4096 (* (count text) 400)))
        ^ByteBuffer no-color nil
        quads (STBEasyFont/stb_easy_font_print
                (float 0.0) (float 0.0) text no-color buf)
        ^java.nio.FloatBuffer fb (.asFloatBuffer buf)
        out   (float-array (* quads 12))
        ndcx  (fn ^double [^double px] (- (/ (* 2.0 px) w) 1.0))
        ndcy  (fn ^double [^double py] (- 1.0 (/ (* 2.0 py) h)))]
    (dotimes [q quads]
      (let [b  (* q 16)
            px (fn ^double [i] (ndcx (+ x (* scale (.get fb (int (+ b (* i 4))))))))
            py (fn ^double [i] (ndcy (+ y (* scale (.get fb (int (+ b (* i 4) 1)))))))
            x0 (px 0) y0 (py 0) x1 (px 1) y1 (py 1)
            x2 (px 2) y2 (py 2) x3 (px 3) y3 (py 3)
            o  (* q 12)]
        (aset out (+ o 0) (float x0))  (aset out (+ o 1) (float y0))
        (aset out (+ o 2) (float x1))  (aset out (+ o 3) (float y1))
        (aset out (+ o 4) (float x2))  (aset out (+ o 5) (float y2))
        (aset out (+ o 6) (float x0))  (aset out (+ o 7) (float y0))
        (aset out (+ o 8) (float x2))  (aset out (+ o 9) (float y2))
        (aset out (+ o 10) (float x3)) (aset out (+ o 11) (float y3))))
    [out (* quads 6)]))

(defn render-text
  "Draw HUD text lines via the solid-colour HUD program. Each line is
   {:text :x :y :color [r g b a] :scale} with a top-left pixel origin.
   No-op without a program or lines."
  [hud-program lines width height]
  (when (and hud-program (pos? (int hud-program)) (seq lines))
    (GL20/glUseProgram hud-program)
    (GL11/glEnable GL11/GL_BLEND)
    (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)
    (GL11/glDepthMask false)
    (let [loc (GL20/glGetUniformLocation hud-program "hudColor")]
      (doseq [{:keys [text x y color scale] :or {scale 2.0 color [1.0 1.0 1.0 1.0]}} lines]
        (when (seq text)
          (let [[verts n] (text->ndc-tris text (double x) (double y) (double scale)
                                          (double width) (double height))
                fb  (BufferUtils/createFloatBuffer (alength verts))
                vao (GL30/glGenVertexArrays)
                vbo (GL15/glGenBuffers)
                [r g b a] color]
            (.put fb verts)
            (.flip fb)
            (GL30/glBindVertexArray vao)
            (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
            (GL15/glBufferData GL15/GL_ARRAY_BUFFER fb GL15/GL_STATIC_DRAW)
            (GL20/glVertexAttribPointer 0 2 GL11/GL_FLOAT false 0 0)
            (GL20/glEnableVertexAttribArray 0)
            (GL20/glUniform4f loc (float r) (float g) (float b) (float (or a 1.0)))
            (GL11/glDrawArrays GL11/GL_TRIANGLES 0 n)
            (GL30/glBindVertexArray 0)
            (GL15/glDeleteBuffers vbo)
            (GL30/glDeleteVertexArrays vao)))))
    (GL11/glDepthMask true)
    (GL11/glDisable GL11/GL_BLEND)
    (GL20/glUseProgram 0)))

(defn- format-elapsed
  "Human astronomical duration from elapsed simulation seconds."
  [sim-seconds]
  (let [yr (/ (double (or sim-seconds 0.0)) phase0/seconds-per-year)]
    (cond
      (< yr 1.0e3) (format "%.0f yr" yr)
      (< yr 1.0e6) (format "%.1f kyr" (/ yr 1.0e3))
      (< yr 1.0e9) (format "%.2f Myr" (/ yr 1.0e6))
      :else        (format "%.2f Gyr" (/ yr 1.0e9)))))

(defn- format-rate
  "Human clock rate from years-of-sim advanced per real second."
  [rate-yr]
  (let [r (double (or rate-yr 0.0))]
    (cond
      (>= r 1.0e3) (format "%.0f kyr/s" (/ r 1.0e3))
      (>= r 1.0)   (format "%.0f yr/s" r)
      :else        (format "%.1f yr/s" r))))

(defn- phase-label
  "Player-facing name for a detected formation phase."
  [phase]
  (case phase
    :phase-0/nebula-collapse "Nebula collapsing"
    :phase-0/protostar       "Protostar forming"
    :phase-0/ignition        "Ignition"
    :phase-0/accretion       "Accretion"
    :phase-0/planets-formed  "Planets formed"
    :phase-0/dispersed       "Dispersed"
    :initializing            "Initializing"
    (when phase (name phase))))

(defn hud-text-from-world
  "Top-left stats panel for a Phase 0 world: the adaptive clock (elapsed
   sim-time, current rate, phase) plus total mass, temperature, and body counts.
   Reads the per-tick `:phase0/stats` cache. Empty for non-phase0/bare worlds."
  [world]
  (if-let [rate-yr (:phase0/rate-yr world)]
    (let [{:keys [total-mass-msun avg-temp peak-temp
                  body-count resolved-count star-count planet-count]
           :or   {total-mass-msun 0.0 avg-temp 0.0 peak-temp 0.0
                  body-count 0 resolved-count 0 star-count 0 planet-count 0}}
          (:phase0/stats world)
          lines [(format "%s   %s"
                         (format-elapsed (:phase0/sim-time world))
                         (phase-label (:phase0/phase world)))
                 (format "clock  %s" (format-rate rate-yr))
                 (format "mass   %.3f Msun" (double total-mass-msun))
                 (format "temp   %.0f K  (peak %.0f K)"
                         (double avg-temp) (double peak-temp))
                 (format "bodies %d  resolved %d  stars %d  planets %d"
                         (int body-count) (int resolved-count)
                         (int star-count) (int planet-count))]]
      (map-indexed (fn [i s]
                     {:text s :x 16.0 :y (+ 24.0 (* i 22.0))
                      :scale 2.2 :color [0.86 0.94 1.0 0.95]})
                   lines))
    []))

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

(defn- particle->floats [{:keys [position color size density]}]
  "Pack a particle into interleaved floats: position 3, color 3, size 1,
   density 1. Density defaults to 1.0 and is used by the nebula shader to
   modulate alpha and emission."
  (let [[x y z] position
        [r g b] color]
    [(float x) (float y) (float z)
     (float r) (float g) (float b)
     (float size)
     (float (or density 1.0))]))

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
  "Upload an interleaved particle buffer (position 3, color 3, size 1, density 1)."
  [{:keys [buffer count]}]
  (let [vao (GL30/glGenVertexArrays)
        vbo (GL15/glGenBuffers)
        stride (* 8 4)]
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
    ;; density
    (GL20/glVertexAttribPointer 3 1 GL11/GL_FLOAT false stride (* 7 4))
    (GL20/glEnableVertexAttribArray 3)
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

(defn- move-focus-by
  "Shift the observer's focus volume by `dpos` (world metres)."
  [world dpos]
  (if-let [obs (player/get-observer world)]
    (phase0/handle-input world :move-focus (sp/v+ (:focus-position obs) dpos))
    world))

(defn- player-key
  "Map a key press to a focus / drift / release action on the world's observer.
   Arrows drift the focus volume, , / . narrow / widen it, Space releases the
   spark to drift toward the system. This is the player's interaction language."
  [world-atom key]
  (let [step 3.0e15]
    (condp = key
      GLFW/GLFW_KEY_LEFT   (swap! world-atom move-focus-by [(- step) 0.0 0.0])
      GLFW/GLFW_KEY_RIGHT  (swap! world-atom move-focus-by [step 0.0 0.0])
      GLFW/GLFW_KEY_UP     (swap! world-atom move-focus-by [0.0 0.0 (- step)])
      GLFW/GLFW_KEY_DOWN   (swap! world-atom move-focus-by [0.0 0.0 step])
      GLFW/GLFW_KEY_COMMA  (swap! world-atom phase0/handle-input :narrow-focus)
      GLFW/GLFW_KEY_PERIOD (swap! world-atom phase0/handle-input :widen-focus)
      GLFW/GLFW_KEY_SPACE  (swap! world-atom phase0/handle-input :release)
      nil)))

(defn setup-input
  "Install GLFW input callbacks. With a `world-atom`, also wires the player's
   focus controls (arrows / , . / Space) onto the world's observer."
  ([window camera-atom keys-atom config-atom]
   (setup-input window camera-atom keys-atom config-atom nil))
  ([window camera-atom keys-atom config-atom world-atom]
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
           (println "Camera reset"))
         ;; Player focus / drift / release controls
         (when (and world-atom (= action GLFW/GLFW_PRESS))
           (player-key world-atom key)))))
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
                      update-camera-position))))))))

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

;; --- Physics-coupled size and material colour -------------------------------
;; Visual radius and colour are DERIVED from a body's physical state, not chosen
;; per matter-state. Two consequences the design requires: (1) the diffuse cloud
;; reads larger than the compact bodies it spawns, because bodies map to a small
;; render radius while the gas spans the whole view; (2) a body's colour tracks
;; its composition (until it is hot enough to glow), so as later chemistry
;; differentiates worlds their appearance diverges with no special-casing.

(def ^:const render-radius-ref
  "Physical radius (m) that maps to the minimum visible render size. A small gas
   clump sits here; planets and stars rise above it by log-compression."
  3.0e13)

(defn phys->render-radius
  "Map a physical radius (m) to a render-unit radius, log-compressed and clamped
   so a ~5-order span (gas clump → giant planet) stays legible while preserving
   order: bigger physical body → bigger on screen. Bodies stay small relative to
   the cloud on purpose — that is the real size relationship."
  [r-phys]
  (let [r (double (or r-phys 0.0))]
    (if (pos? r)
      (-> (+ 0.18 (* 0.42 (Math/log10 (/ r render-radius-ref))))
          (max 0.18) (min 6.0))
      0.18)))

(defn composition->material-color
  "Base material colour from bulk composition (mass fractions): hydrogen/helium
   gas reads pale tan, metal/rock-rich matter warm grey-brown, and an icy/volatile
   fraction cold blue-white. Primordial gas is mostly tan; differentiated rocky or
   icy worlds shift toward rock/ice as their composition diverges."
  [comp]
  (let [c      (or comp {})
        metals (double (get c :metals 0.0))
        ice    (double (+ (double (get c :ice 0.0))
                          (double (get c :H2O 0.0))
                          (double (get c :volatiles 0.0))))
        gas    (max 0.0 (- 1.0 metals ice))
        rock-c [0.62 0.50 0.40]
        ice-c  [0.75 0.85 0.95]
        gas-c  [0.85 0.80 0.62]]
    (mapv (fn [i] (+ (* gas (nth gas-c i)) (* metals (nth rock-c i)) (* ice (nth ice-c i))))
          [0 1 2])))

(defn body-render-color
  "Surface colour of a resolved body: its composition (material) colour when
   cold, crossfading to its thermal blackbody colour as it heats past ~1000 K.
   A cold rocky world shows rock; an incandescent one glows by temperature."
  [temp comp]
  (let [mat (composition->material-color comp)
        th  (temp-color temp)
        t   (double (or temp 10.0))
        f   (max 0.0 (min 1.0 (/ (- (Math/log10 (max 1.0 t)) 2.7) 2.3)))]
    (mapv (fn [m h] (+ (* (- 1.0 f) m) (* f h))) mat th)))

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

(defn- nebula-density-norm
  "Map a physical density (kg/m³) to a [0,1] visual factor with a wide log
   dynamic range. The nebula spans roughly 1e-21 … 1e-12 kg/m³; this mapping
   makes a factor-of-1000 density contrast readable instead of clamping
   everything to the same narrow band."
  [rho]
  (let [log-rho (Math/log10 (max 1e-30 (double (or rho 1e-18))))
        lo -21.0
        hi -12.0]
    (max 0.0 (min 1.0 (/ (- log-rho lo) (- hi lo))))))

(defn- fog-particle-size
  "Screen-space particle size for a fog sample. Lower-density samples represent
   a larger sampled volume so they read bigger and fainter; higher-density cores
   read smaller and brighter. `support` is the SPH smoothing/support radius in
   render units."
  [support density-norm rng]
  (let [s (double (or support 1.0))
        d (double (or density-norm 0.5))
        base (* s (+ 6.0 (* 16.0 (- 1.0 d))))]
    (max 2.0 (+ base (* 4.0 (.nextDouble rng))))))

(defn- nebula-fog*
  "Actual particle cloud generation; split out so `nebula-fog` can cache."
  [{:keys [center extent color density support]} count seed]
  (let [[cx cy cz] center
        rng (java.util.Random. (long (or seed 1)))
        dens (double (or density 0.5))
        sup  (double (or support extent 1.0))]
    (mapv
     (fn [_]
       (let [theta (* 2 Math/PI (.nextDouble rng))
             phi   (Math/acos (- (* 2 (.nextDouble rng)) 1))
             r     (* (double extent) (Math/sqrt (.nextDouble rng)))]
         {:position [(+ cx (* r (Math/sin phi) (Math/cos theta)))
                     (+ cy (* r (Math/sin phi) (Math/sin theta)))
                     (+ cz (* r (Math/cos phi)))]
          :color color
          :size  (fog-particle-size sup dens rng)
          :density (float dens)
          :render-mode :particle}))
     (range count))))

(def ^:private particle-cache
  "Cache keyed by [eid seed count] → immutable particle cloud. The cloud is
   deterministic, so it can be reused every frame for the same entity unless
   render-time inputs (extent/support/color/density) change. The cache entry
   stores the inputs used to build it; a lookup validates them."
  (atom {}))

(defn- particle-cache-key
  "Stable key for a nebula clump's cached particle cloud."
  [eid seed count]
  [eid seed count])

(defn- cache-match? [cached params]
  (= (select-keys cached [:center :extent :support :color :density])
     (select-keys params [:center :extent :support :color :density])))

(defn nebula-fog
  "Soft fog puffs through a clump, DETERMINISTIC in `seed` so the cloud is stable
   frame-to-frame (no per-frame RNG → no shimmer). center/extent in render units.
   `support` is the SPH smoothing length / sampled area in render units; it
   drives the particle size. `density` is a [0,1] normalized density factor that
   drives opacity and brightness.

   Generated clouds are cached per-entity; reusing them saves the mapv + RNG cost
   when the same entity is projected again next frame with unchanged inputs."
  [{:keys [center extent color count seed density support]}]
  (let [eid   (long (or seed 1))
        ckey  (particle-cache-key eid seed count)
        params {:center center :extent extent :support support
                :color color :density density}]
    (if-let [cached (get @particle-cache ckey)]
      (if (cache-match? cached params)
        (:particles cached)
        (let [fresh (nebula-fog* params count seed)]
          (swap! particle-cache assoc ckey (assoc params :particles fresh))
          fresh))
      (let [fresh (nebula-fog* params count seed)]
        (swap! particle-cache assoc ckey (assoc params :particles fresh))
        fresh))))

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

;; --- Player spark + focus reticle (the interactive overlay) ------------------

(defn- ring-segments
  "Line-segment endpoints approximating a circle of `radius` render units in the
   xy-plane at `center`, as :line render shapes — the player's focus reticle."
  [center radius color n]
  (let [[cx cy cz] center]
    (vec (mapcat
           (fn [i]
             (let [a0 (* 2.0 Math/PI (/ (double i) n))
                   a1 (* 2.0 Math/PI (/ (double (inc i)) n))]
               [{:position [(+ cx (* radius (Math/cos a0))) (+ cy (* radius (Math/sin a0))) cz]
                 :color color :size 1.0 :render-mode :line}
                {:position [(+ cx (* radius (Math/cos a1))) (+ cy (* radius (Math/sin a1))) cz]
                 :color color :size 1.0 :render-mode :line}]))
           (range n)))))

(defn coherence-color
  "Reticle colour for each decoherence state: teal when highly coherent, warming
   to red as the spark fades — so the player reads their own coherence at a glance."
  [state]
  (case state
    :highly-coherent [0.40 1.00 0.75]
    :coherent        [0.45 0.85 1.00]
    :wavering        [1.00 0.90 0.40]
    :fading          [1.00 0.55 0.30]
    :dissolved       [0.65 0.30 0.30]
    [0.70 0.90 1.00]))

(defn player-overlay-shapes
  "Render shapes for the player's spark and focus volume: a bright point at the
   observer position and a reticle ring at the focus, tinted by coherence. Empty
   when the world has no observer (e.g. bare test worlds)."
  [world scale]
  (if-let [obs (player/get-observer world)]
    (let [scl   (fn [p] (mapv #(/ (double %) scale) p))
          fpos  (scl (:focus-position obs))
          fr    (/ (double (:focus-radius obs)) scale)
          spark (scl (:position obs))
          col   (coherence-color (player/decoherence-state obs))]
      (into [{:position spark :color [0.85 0.96 1.0]
              :size (+ 28.0 (* 44.0 (double (:focus-intensity obs 0.5))))
              :render-mode :particle}]
            (ring-segments fpos (max 0.5 fr) col 48)))
    []))

(defn hud-rects-from-world
  "HUD rectangles (NDC) for the observer: a coherence track + fill (tinted by
   decoherence state) bottom-left, and a thin focus-intensity bar above it.
   Empty when there is no observer."
  [world]
  (if-let [obs (player/get-observer world)]
    (let [coh  (double (or (:coherence obs) 0.0))
          mx   (double (or (:max-coherence obs) 1.0))
          frac (max 0.0 (min 1.0 (/ coh (max 1e-9 mx))))
          fi   (double (or (:focus-intensity obs) 0.5))
          col  (conj (coherence-color (player/decoherence-state obs)) 0.92)
          x0 -0.96 x1 -0.46 y0 -0.93 y1 -0.89]
      [{:x0 x0 :y0 y0 :x1 x1 :y1 y1 :color [0.10 0.10 0.16 0.65]}                 ;; coherence track
       {:x0 x0 :y0 y0 :x1 (+ x0 (* (- x1 x0) frac)) :y1 y1 :color col}            ;; coherence fill
       {:x0 x0 :y0 -0.875 :x1 (+ x0 (* (- x1 x0) fi)) :y1 -0.86                   ;; focus intensity
        :color [0.70 0.86 1.0 0.85]}])
    []))

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
     (into
      (player-overlay-shapes world scale)
      (mapcat
       (fn [eid]
         (let [state   (ecs/get-component world eid c/matter-state)
               [x y z] (ecs/get-component world eid c/position)
               center  [(/ x scale) (/ y scale) (/ z scale)]
               temp    (ecs/get-component world eid c/temperature)
               comp    (ecs/get-component world eid c/composition)
               r-phys  (ecs/get-component world eid c/radius)
               color   (body-render-color temp comp)
               ob      (or (ecs/get-component world eid c/oblateness) 1.0)
               axis    (or (ecs/get-component world eid c/rotation-axis) [0.0 0.0 1.0])]
            (case state
              :nebula
              ;; Diffuse gas is a volumetric cloud of additive samples, not a
              ;; single point. Each sample's size matches its SPH smoothing
              ;; length (the area it represents), while its opacity and colour
              ;; are driven by local density so overdense filaments read
              ;; brighter and tighter than the diffuse background.
              (let [rho      (or (ecs/get-component world eid c/density) 1e-18)
                    render-r (phys->render-radius r-phys)
                    extent   (* render-r (Math/pow (+ 1.0 (Math/log10 (max 1.0 (/ r-phys 3e13)))) 0.5))
                    dens-norm (nebula-density-norm rho)]
                (nebula-fog {:center   center
                             :extent   extent
                             :support  (* 2.0 render-r)
                             :color    (temp-color temp)
                             :count    (fog-sample-count render-r focus)
                             :seed     eid
                             :density  dens-norm}))

              :star
              ;; The photosphere is sub-pixel at this scale; a star's apparent
              ;; size IS its luminosity. Render a small bright core sized by
              ;; log-luminosity, wrapped in a volumetric corona + field line.
              (let [lum    (double (or (ecs/get-component world eid c/luminosity) 1.0e26))
                    core-r (-> (+ 0.6 (* 0.28 (Math/log10 (/ (max 1.0 lum) 1.0e26))))
                               (max 0.6) (min 3.0))
                    body   {:entity      eid
                            :position    center
                            :radius      core-r
                            :color       [1.0 0.93 0.82]
                            :kind        state
                            :oblateness  ob
                            :rotation-axis axis
                            :render-mode :body}]
                (concat
                 [body]
                 (nebula-fog {:center  center
                              :extent  (* core-r 3.0)
                              :support (* 2.0 core-r)
                              :color   [0.85 0.80 0.55]
                              :count   70
                              :seed    eid
                              :density 0.7})
                 (field-line center core-r (ecs/get-component world eid c/b-field))))

              :protostar
              ;; A contracting core: render radius follows the physical radius
              ;; (log-compressed) so it shrinks smoothly as it collapses, glowing
              ;; by temperature, shrouded in fog + field lines.
              (let [render-r (phys->render-radius r-phys)
                    rho      (or (ecs/get-component world eid c/density) 1e-15)]
                (concat
                 [{:entity      eid
                   :position    center
                   :radius      (* render-r (Math/pow ob (/ 1.0 3.0)))
                   :color       color
                   :kind        state
                   :oblateness  ob
                   :rotation-axis axis
                   :render-mode :body}]
                 (nebula-fog {:center  center
                              :extent  (* render-r 2.0)
                              :support (* 2.0 render-r)
                              :color   color
                              :count   (fog-sample-count render-r focus)
                              :seed    eid
                              :density (nebula-density-norm rho)})
                 (field-line center render-r (ecs/get-component world eid c/b-field))))

              ;; :planet :debris → shaded body sized by physical radius, coloured
              ;; by composition crossfading to thermal glow.
              [{:entity      eid
                :position    center
                :radius      (phys->render-radius r-phys)
                :color       color
                :kind        state
                :oblateness  ob
                :rotation-axis axis
                :render-mode :body}])))
        (ecs/entities-with world c/position c/matter-state))))))

(def ^:private phase0-bodies-cache
  "Per-frame cache for `phase0-bodies-from-world`. Rendering is the expensive
   part of the dev loop; this cache lets unchanged frames reuse the shape list.
   The cache key includes the world identity so two different worlds at the same
   tick do not collide."
  (atom {}))

(defn- phase0-render-cache-key
  "Cache key: world identity + tick + view scale."
  [world scale]
  [(System/identityHashCode world) (:tick world) scale])

(defn- phase0-bodies-from-world*
  "Uncached render projection; see `phase0-bodies-from-world`."
  [world scale]
  (let [focus (player-focus-level world)]
    (into
     (player-overlay-shapes world scale)
     (mapcat
      (fn [eid]
        (let [state   (ecs/get-component world eid c/matter-state)
              [x y z] (ecs/get-component world eid c/position)
              center  [(/ x scale) (/ y scale) (/ z scale)]
              temp    (ecs/get-component world eid c/temperature)
              comp    (ecs/get-component world eid c/composition)
              r-phys  (ecs/get-component world eid c/radius)
              color   (body-render-color temp comp)
              ob      (or (ecs/get-component world eid c/oblateness) 1.0)
              axis    (or (ecs/get-component world eid c/rotation-axis) [0.0 0.0 1.0])]
          (case state
            :nebula
            ;; Diffuse gas is a volumetric cloud of additive samples, not a
            ;; single point. Each sample's size matches its SPH smoothing
            ;; length (the area it represents), while its opacity and colour
            ;; are driven by local density so overdense filaments read
            ;; brighter and tighter than the diffuse background.
            (let [rho      (or (ecs/get-component world eid c/density) 1e-18)
                  render-r (phys->render-radius r-phys)
                  extent   (* render-r (Math/pow (+ 1.0 (Math/log10 (max 1.0 (/ r-phys 3e13)))) 0.5))
                  dens-norm (nebula-density-norm rho)]
              (nebula-fog {:center   center
                           :extent   extent
                           :support  (* 2.0 render-r)
                           :color    (temp-color temp)
                           :count    (fog-sample-count render-r focus)
                           :seed     eid
                           :density  dens-norm}))

            :star
            ;; The photosphere is sub-pixel at this scale; a star's apparent
            ;; size IS its luminosity. Render a small bright core sized by
            ;; log-luminosity, wrapped in a volumetric corona + field line.
            (let [lum    (double (or (ecs/get-component world eid c/luminosity) 1.0e26))
                  core-r (-> (+ 0.6 (* 0.28 (Math/log10 (/ (max 1.0 lum) 1.0e26))))
                             (max 0.6) (min 3.0))
                  body   {:entity      eid
                          :position    center
                          :radius      core-r
                          :color       [1.0 0.93 0.82]
                          :kind        state
                          :oblateness  ob
                          :rotation-axis axis
                          :render-mode :body}]
              (concat
               [body]
               (nebula-fog {:center  center
                            :extent  (* core-r 3.0)
                            :support (* 2.0 core-r)
                            :color   [0.85 0.80 0.55]
                            :count   70
                            :seed    eid
                            :density 0.7})
               (field-line center core-r (ecs/get-component world eid c/b-field))))

            :protostar
            ;; A contracting core: render radius follows the physical radius
            ;; (log-compressed) so it shrinks smoothly as it collapses, glowing
            ;; by temperature, shrouded in fog + field lines.
            (let [render-r (phys->render-radius r-phys)
                  rho      (or (ecs/get-component world eid c/density) 1e-15)]
              (concat
               [{:entity      eid
                 :position    center
                 :radius      (* render-r (Math/pow ob (/ 1.0 3.0)))
                 :color       color
                 :kind        state
                 :oblateness  ob
                 :rotation-axis axis
                 :render-mode :body}]
               (nebula-fog {:center  center
                            :extent  (* render-r 2.0)
                            :support (* 2.0 render-r)
                            :color   color
                            :count   (fog-sample-count render-r focus)
                            :seed    eid
                            :density (nebula-density-norm rho)})
               (field-line center render-r (ecs/get-component world eid c/b-field))))

            ;; :planet :debris → shaded body sized by physical radius, coloured
            ;; by composition crossfading to thermal glow.
            [{:entity      eid
              :position    center
              :radius      (phys->render-radius r-phys)
              :color       color
              :kind        state
              :oblateness  ob
              :rotation-axis axis
              :render-mode :body}])))
      (ecs/entities-with world c/position c/matter-state)))))

(defn phase0-bodies-from-world
  "Project Phase 0 ECS matter entities into stylized, view-scaled render shapes,
   coloured by TEMPERATURE so the thermal field is visible:
     :nebula    → one soft fog puff (the diffuse cloud)
     :protostar → a compact bright cloud + a magnetic field line (contracting core)
     :star/:planet/:debris → a shaded body
   Per-entity jitter is deterministic, so nothing shimmers between frames.

   This is the ONLY Phase 0 render projection — one ECS world behind it.

   The projection is cached per [tick scale] so consecutive render frames that
   see the same world do not rebuild hundreds of fog particles."
  ([world] (phase0-bodies-from-world world phase0-view-scale))
   ([world scale]
     (let [ckey (phase0-render-cache-key world scale)]
       (if-let [cached (get @phase0-bodies-cache ckey)]
         cached
         (let [bodies (phase0-bodies-from-world* world scale)]
           (reset! phase0-bodies-cache {ckey bodies})
           bodies)))))

;; ---------------------------------------------------------------------------
;; Volumetric fog — ray-marched participating medium (design: docs fog notes)
;;
;; The SPH particle field IS a continuous density field ρ(x)=Σ m_i W(|x−x_i|,h_i).
;; Each frame we bake that field (plus a temperature-tinted emission colour) into
;; a 3D froxel texture covering the gas, then ray-march it on a fullscreen quad:
;; for every pixel we integrate the volume-rendering equation along the view ray
;;   C = ∫ T(t)·σ·(L_emit + L_scatter) dt,  T(t)=exp(−∫σ ds)  (Beer–Lambert)
;; front-to-back. Hot cores/stars are point lights whose in-scattered light is
;; attenuated by a short shadow march through the same texture — so light from
;; the stars and hot gas visibly fills and shafts through the cloud.
;; ---------------------------------------------------------------------------

(def ^:private volume-vertex-shader
  "#version 330 core
   layout(location=0) in vec2 aPos;
   out vec2 vNdc;
   void main(){ vNdc = aPos; gl_Position = vec4(aPos, 0.0, 1.0); }")

(def ^:private volume-fragment-shader
  "#version 330 core
   in vec2 vNdc;
   out vec4 FragColor;

   uniform vec3  camPos, camRight, camUp, camFwd;
   uniform float tanHalfFov, aspect;
   uniform vec3  boxMin, boxMax;
   uniform sampler3D volume;          // rgb = emission (density-weighted), a = density
   uniform float kappa;               // extinction per density unit per render-unit
   uniform float emissionScale;
   uniform float scatterScale;
   uniform float jitter;              // dither amount to hide step banding
   uniform int   numLights;
   uniform vec3  lightPos[8];
   uniform vec3  lightColor[8];
   uniform float lightIntensity[8];

   float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9898,78.233)))*43758.5453); }

   bool intersectBox(vec3 ro, vec3 rd, out float t0, out float t1){
     vec3 inv = 1.0/rd;
     vec3 a = (boxMin-ro)*inv, b = (boxMax-ro)*inv;
     vec3 tmn = min(a,b), tmx = max(a,b);
     t0 = max(max(tmn.x,tmn.y),tmn.z);
     t1 = min(min(tmx.x,tmx.y),tmx.z);
     return t1 > max(t0,0.0);
   }
   vec3 uvw(vec3 p){ return (p-boxMin)/(boxMax-boxMin); }

   void main(){
     vec3 ro = camPos;
     vec3 rd = normalize(camFwd
                 + vNdc.x*aspect*tanHalfFov*camRight
                 + vNdc.y*tanHalfFov*camUp);
     float t0,t1;
     if(!intersectBox(ro,rd,t0,t1)){ discard; }
     t0 = max(t0,0.0);
     float span = t1 - t0;
     int steps = clamp(int(span/(length(boxMax-boxMin)/96.0)), 16, 192);
     float dt = span/float(steps);
     float j = jitter*hash(gl_FragCoord.xy);

     float T = 1.0;
     vec3  C = vec3(0.0);
     for(int i=0;i<steps;i++){
       float t = t0 + (float(i)+j)*dt;
       vec3 p = ro + rd*t;
       vec4 s = texture(volume, uvw(p));
       float dens = s.a;
       if(dens > 0.0008){
         float sigma = kappa*dens;
         float a = 1.0 - exp(-sigma*dt);
         vec3 scat = vec3(0.0);
         for(int l=0;l<numLights;l++){
           vec3 d = lightPos[l]-p;
           float r2 = dot(d,d)+0.02;
           // short shadow march toward the light: god-ray occlusion
           float Ts = 1.0;
           vec3 ld = d/sqrt(r2);
           float sdt = length(boxMax-boxMin)/28.0;
           for(int k=0;k<5;k++){
             vec3 sp = p + ld*(float(k)+0.5)*sdt;
             Ts *= exp(-kappa*texture(volume, uvw(sp)).a*sdt);
           }
           scat += lightColor[l]*(lightIntensity[l]/r2)*Ts;
         }
         vec3 L = emissionScale*s.rgb + scatterScale*dens*scat;
         C += T * a * L;
         T *= (1.0-a);
         if(T < 0.01) break;
       }
     }
     FragColor = vec4(C, 1.0-T);   // premultiplied-alpha over the scene
   }")

(defn create-volume-program []
  (println "Compiling volume shaders...")
  (link-program (compile-shader volume-vertex-shader GL20/GL_VERTEX_SHADER)
                (compile-shader volume-fragment-shader GL20/GL_FRAGMENT_SHADER)))

(defn- fullscreen-quad-vao
  "A unit fullscreen quad (two triangles) in NDC, attribute location 0."
  []
  (let [verts (float-array [-1.0 -1.0,  1.0 -1.0,  1.0  1.0,
                            -1.0 -1.0,  1.0  1.0, -1.0  1.0])
        vao (GL30/glGenVertexArrays)
        vbo (GL15/glGenBuffers)
        buf (doto (BufferUtils/createFloatBuffer (count verts)) (.put verts) (.flip))]
    (GL30/glBindVertexArray vao)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
    (GL15/glBufferData GL15/GL_ARRAY_BUFFER buf GL15/GL_STATIC_DRAW)
    (GL20/glEnableVertexAttribArray 0)
    (GL20/glVertexAttribPointer 0 2 GL11/GL_FLOAT false (* 2 Float/BYTES) 0)
    (GL30/glBindVertexArray 0)
    {:vao vao :vbo vbo}))

(defn- gas-points
  "Volumetric gas samples in RENDER space: position, render-space smoothing
   radius, density-driven opacity, and temperature-tinted emission colour.
   Only diffuse/contracting matter (:nebula, :protostar) is part of the medium —
   solid bodies and stars are drawn separately."
  [world scale]
  (->> (ecs/entities-with world c/position c/matter-state c/density c/radius)
       (keep (fn [eid]
               (let [st (ecs/get-component world eid c/matter-state)]
                 (when (#{:nebula :protostar} st)
                   (let [[x y z] (ecs/get-component world eid c/position)
                         rho   (double (or (ecs/get-component world eid c/density) 1e-18))
                         temp  (ecs/get-component world eid c/temperature)
                         rphys (double (or (ecs/get-component world eid c/radius) render-radius-ref))]
                     {:p   [(/ (double x) scale) (/ (double y) scale) (/ (double z) scale)]
                      :h   (max 0.5 (* 2.2 (phys->render-radius rphys)))
                      :col (temp-color temp)
                      :dens (max 0.0 (nebula-density-norm rho))})))))
       vec))

(defn build-volume-texture
  "Bake the gas field into an RxRxR RGBA16F 3D texture (rgb=emission, a=density)
   covering the gas bounding box in render space. Returns {:tex :box-min :box-max}
   or nil when there is no gas. Splats each SPH sample with a smooth radial kernel
   — the same field the simulation integrates, sampled onto a grid the GPU can
   trilinearly interpolate during the march."
  [world scale res]
  (let [pts (gas-points world scale)]
    (when (seq pts)
      (let [R    (int res)
            ;; bounding box over p ± h, padded
            ext  (reduce (fn [[mn mx] {:keys [p h]}]
                           [(mapv #(min %1 (- %2 h)) mn p)
                            (mapv #(max %1 (+ %2 h)) mx p)])
                         [[1e30 1e30 1e30] [-1e30 -1e30 -1e30]] pts)
            [bmn0 bmx0] ext
            pad  (mapv #(max 0.5 (* 0.06 (- %1 %2))) bmx0 bmn0)
            bmn  (mapv - bmn0 pad)
            bmx  (mapv + bmx0 pad)
            span (mapv - bmx bmn)
            cs   (mapv #(/ (double %) R) span)
            data (float-array (* 4 R R R))
            idx  (fn [x y z] (* 4 (+ x (* R (+ y (* R z))))))]
        (doseq [{:keys [p h col dens]} pts]
          (when (pos? dens)
            (let [[px py pz] p
                  [csx csy csz] cs
                  [r g b] col
                  vidx (fn [coord mn cs] (int (Math/floor (/ (- coord mn) cs))))
                  lox (max 0 (vidx (- px h) (bmn 0) csx))
                  hix (min (dec R) (vidx (+ px h) (bmn 0) csx))
                  loy (max 0 (vidx (- py h) (bmn 1) csy))
                  hiy (min (dec R) (vidx (+ py h) (bmn 1) csy))
                  loz (max 0 (vidx (- pz h) (bmn 2) csz))
                  hiz (min (dec R) (vidx (+ pz h) (bmn 2) csz))
                  ih2 (/ 1.0 (* h h))]
              (loop [z loz]
                (when (<= z hiz)
                  (let [vcz (+ (nth bmn 2) (* (+ z 0.5) csz))
                        dz (- vcz pz)]
                    (loop [y loy]
                      (when (<= y hiy)
                        (let [vcy (+ (nth bmn 1) (* (+ y 0.5) csy))
                              dy (- vcy py)]
                          (loop [x lox]
                            (when (<= x hix)
                              (let [vcx (+ (nth bmn 0) (* (+ x 0.5) csx))
                                    dx (- vcx px)
                                    d2 (+ (* dx dx) (* dy dy) (* dz dz))
                                    q  (* d2 ih2)]
                                (when (< q 1.0)
                                  (let [w  (let [u (- 1.0 q)] (* u u))
                                        wd (* w dens)
                                        i  (idx x y z)]
                                    (aset data i        (float (+ (aget data i)        (* wd r))))
                                    (aset data (+ i 1)  (float (+ (aget data (+ i 1))  (* wd g))))
                                    (aset data (+ i 2)  (float (+ (aget data (+ i 2))  (* wd b))))
                                    (aset data (+ i 3)  (float (+ (aget data (+ i 3))  wd)))))
                                (recur (inc x)))))
                          (recur (inc y)))))
                    (recur (inc z))))))))
        (let [tex (GL11/glGenTextures)
              buf (doto (BufferUtils/createFloatBuffer (alength data)) (.put data) (.flip))]
          (GL13/glActiveTexture GL13/GL_TEXTURE0)
          (GL11/glBindTexture GL12/GL_TEXTURE_3D tex)
          (GL12/glTexImage3D GL12/GL_TEXTURE_3D 0 GL30/GL_RGBA16F R R R 0
                             GL11/GL_RGBA GL11/GL_FLOAT buf)
          (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
          (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
          (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
          (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
          (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL12/GL_TEXTURE_WRAP_R GL12/GL_CLAMP_TO_EDGE)
          (GL11/glBindTexture GL12/GL_TEXTURE_3D 0)
          {:tex tex :box-min bmn :box-max bmx})))))

(defn volume-lights
  "Up to 8 brightest hot bodies (stars + hot cores) as point lights in render
   space — the sources whose light scatters through the medium."
  [world scale]
  (->> (ecs/entities-with world c/position c/matter-state c/temperature)
       (keep (fn [eid]
               (let [t (double (or (ecs/get-component world eid c/temperature) 0.0))]
                 (when (> t 2500.0)
                   (let [[x y z] (ecs/get-component world eid c/position)]
                     {:pos [(/ (double x) scale) (/ (double y) scale) (/ (double z) scale)]
                      :col (temp-color t)
                      :temp t
                      :intensity (min 60.0 (* 6.0 (max 0.0 (- (Math/log10 t) 3.0))))})))))
       (sort-by :temp >)
       (take 8)
       vec))

(defn render-volume
  "Ray-march pass: composite the baked gas volume over the current scene with
   premultiplied-alpha blending. `volume` is {:program :tex :box-min :box-max
   :lights [...]}; camera basis is derived from the camera position/target."
  [{:keys [program tex box-min box-max lights]} quad-vao camera width height]
  (when (and program tex)
    (let [fwd   (normalize (sp/v- (:target camera) (:position camera)))
          right (normalize (cross fwd [0.0 1.0 0.0]))
          up    (cross right fwd)
          fov   60.0
          thf   (Math/tan (deg->rad (/ fov 2.0)))
          aspect (/ (double width) (double height))
          loc   (fn [n] (GL20/glGetUniformLocation program n))
          set3  (fn [n [a b c]] (GL20/glUniform3f (loc n) (float a) (float b) (float c)))]
      (GL20/glUseProgram program)
      (set3 "camPos" (:position camera))
      (set3 "camRight" right) (set3 "camUp" up) (set3 "camFwd" fwd)
      (GL20/glUniform1f (loc "tanHalfFov") (float thf))
      (GL20/glUniform1f (loc "aspect") (float aspect))
      (set3 "boxMin" box-min) (set3 "boxMax" box-max)
      (GL20/glUniform1f (loc "kappa") (float 6.0))
      (GL20/glUniform1f (loc "emissionScale") (float 0.9))
      (GL20/glUniform1f (loc "scatterScale") (float 1.6))
      (GL20/glUniform1f (loc "jitter") (float 1.0))
      (GL20/glUniform1i (loc "numLights") (int (count lights)))
      (dotimes [i (count lights)]
        (let [{:keys [pos col intensity]} (nth lights i)]
          (set3 (format "lightPos[%d]" i) pos)
          (set3 (format "lightColor[%d]" i) col)
          (GL20/glUniform1f (loc (format "lightIntensity[%d]" i)) (float intensity))))
      (GL13/glActiveTexture GL13/GL_TEXTURE0)
      (GL11/glBindTexture GL12/GL_TEXTURE_3D tex)
      (GL20/glUniform1i (loc "volume") (int 0))
      (GL11/glEnable GL11/GL_BLEND)
      (GL11/glBlendFunc GL11/GL_ONE GL11/GL_ONE_MINUS_SRC_ALPHA) ; premultiplied
      (GL11/glDepthMask false)
      (GL30/glBindVertexArray quad-vao)
      (GL11/glDrawArrays GL11/GL_TRIANGLES 0 6)
      (GL30/glBindVertexArray 0)
      (GL11/glDepthMask true)
      (GL11/glBindTexture GL12/GL_TEXTURE_3D 0))))

(defn render-scene
  "Render a frame with volumetric fog particles and glowing 3D massive bodies.
   `bodies` is a sequence of render maps; `:render-mode` may be `:particle`
   (soft fog puff) or `:body` (shaded sphere). Default is `:body`."
  [{:keys [body-program particle-program line-program hud-program hud hud-text volume]} mesh-world camera width height bodies time]
  ;; Match the GL viewport to the actual draw surface every frame. Without this
  ;; the viewport keeps its context-creation size, so a HiDPI/resized window
  ;; (framebuffer larger than the logical 1280×720) draws only into the
  ;; bottom-left corner. `width`/`height` are the real framebuffer pixels.
  (GL11/glViewport 0 0 (int width) (int height))
  (GL11/glEnable GL11/GL_DEPTH_TEST)
  (GL11/glClearColor 0.02 0.02 0.05 1.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (let [proj (perspective 60.0 (/ width (float height)) 0.1 10000.0)
        view (look-at (:position camera) (:target camera) (sp/vec3 0.0 1.0 0.0))
        ;; ray-marched volume replaces the sprite fog when supplied
        particles (if volume [] (filterv #(= :particle (:render-mode %)) bodies))
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
          (let [model (if-let [ob (:oblateness body)]
                        (model-matrix (:position body)
                                      (max 0.5 (:radius body))
                                      ob
                                      (:rotation-axis body))
                        (model-matrix (:position body) (max 0.5 (:radius body))))
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
    ;; ---- pass 2b: ray-marched volumetric fog (over the scene) ----
    (when volume
      (let [{:keys [vao] :as quad} (fullscreen-quad-vao)]
        (render-volume volume vao camera width height)
        (GL15/glDeleteBuffers (:vbo quad))
        (GL30/glDeleteVertexArrays vao)))
    ;; ---- pass 3: 2D HUD overlay (coherence, focus) + stats/clock text ----
    (render-hud hud-program hud)
    (render-text hud-program hud-text width height)
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
  ([world-atom path {:keys [tick-fn bodies-fn camera camera-mode volumetric? volume-res]}]
   (println "Rendering offscreen frame to" path)
   (init-glfw)
   (let [width   1280
         height  720
         window  (create-offscreen-window width height)
         body-program     (create-program)
         particle-program (create-particle-program)
         line-program     (create-line-program)
         hud-program      (create-hud-program)
         volume-program   (when volumetric? (create-volume-program))
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
           ;; Frame the whole system: snap an auto-fit camera to the world unless
           ;; the caller supplied one explicitly.
           camera  (or camera
                       (if phase0?
                         (update-camera-for-world
                           (make-camera 60.0) w
                           (assoc (default-camera-settings)
                                  :mode (or camera-mode :fit-all) :smoothing 1.0))
                         (make-camera)))
           bodies (bodies-fn w)
           hud      (when phase0? (hud-rects-from-world w))
           hud-text (when phase0? (hud-text-from-world w))
           volume   (when volume-program
                      (when-let [vt (build-volume-texture w phase0-view-scale
                                                          (int (or volume-res 96)))]
                        (assoc vt :program volume-program
                                  :lights (volume-lights w phase0-view-scale))))]
       (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER (:fbo fbo))
       (render-scene {:body-program body-program :particle-program particle-program
                      :line-program line-program :hud-program hud-program
                      :hud hud :hud-text hud-text :volume volume}
                     mesh camera width height bodies 0.0)
       (when (:tex volume) (GL11/glDeleteTextures (int (:tex volume)))))
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