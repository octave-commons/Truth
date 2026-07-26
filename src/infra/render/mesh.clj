(ns infra.render.mesh
  "Sphere, particle, and sprite mesh generation and upload.
   All OpenGL buffer operations happen here; the rest of the renderer works
   with pure mesh descriptors."
  (:require
   [clojure.math :as math] [infra.camera :as cam])
  (:import
   (org.lwjgl.opengl GL11 GL15 GL20 GL30)
   (org.lwjgl BufferUtils)))

(defn- subdivide-icosahedron []
  (let [t     (/ (+ 1.0 (math/sqrt 5.0)) 2.0)
        verts [[-1.0 t 0.0] [1.0 t 0.0] [-1.0 (- t) 0.0] [1.0 (- t) 0.0]
               [0.0 -1.0 t] [0.0 1.0 t] [0.0 -1.0 (- t)] [0.0 1.0 (- t)]
               [t 0.0 -1.0] [t 0.0 1.0] [(- t) 0.0 -1.0] [(- t) 0.0 1.0]]
        faces [[0 11 5] [0 5 1] [0 1 7] [0 7 10] [0 10 11]
               [1 5 9] [5 11 4] [11 10 2] [10 7 6] [7 1 8]
               [3 9 4] [3 4 2] [3 2 6] [3 6 8] [3 8 9]
               [4 9 5] [2 4 11] [6 2 10] [8 6 7] [9 8 1]]]
    {:verts (mapv cam/normalize verts) :faces faces}))

(defn- midpoint [a b]
  (cam/normalize (mapv + a b)))

(defn- refine-icosahedron [{:keys [verts faces]} times]
  (loop [verts verts faces faces n 0]
    (if (>= n times)
      {:verts verts :faces faces}
      (let [verts-atom (atom verts)
            mid-cache (atom {})
            get-mid (fn [i j]
                      (let [k (sort [i j])]
                        (or (@mid-cache k)
                            (let [idx (count @verts-atom)
                                  m   (midpoint (nth verts i) (nth verts j))]
                              (swap! mid-cache assoc k idx)
                              (swap! verts-atom conj m)
                              idx))))
            new-faces (vec (mapcat (fn [[i j k]]
                                     (let [a (get-mid i j)
                                           b (get-mid j k)
                                           c (get-mid k i)]
                                       [[i a c] [j b a] [k c b] [a b c]]))
                                   faces))]
        (recur @verts-atom new-faces (inc n))))))

(defn make-sphere-mesh
  "Create an icosahedron-based sphere mesh with `subdivisions` refinement
   passes. Returns {:buffer FloatBuffer :vertex-count n}."
  [subdivisions]
  (let [{:keys [verts faces]} (refine-icosahedron (subdivide-icosahedron) subdivisions)
        face-verts (mapcat (fn [[i j k]] [(nth verts i) (nth verts j) (nth verts k)]) faces)
        fb         (BufferUtils/createFloatBuffer (* 3 (count faces) 3))]
    (doseq [[x y z] face-verts]
      (.put fb (float x)) (.put fb (float y)) (.put fb (float z)))
    (.flip fb)
    {:buffer fb
     :vertex-count (* 3 (count faces))}))

(def ^:private unit-cube-verts
  "Corners of a cube centered at the origin with half-extent 1.0 (so scaling
   by `radius` in `infra.render.math/model-matrix` — the same convention
   sphere bodies use, radius = distance from centre to the unit mesh — yields
   a cube of edge `2 * radius`). [x y z] indexed 0-7 in the standard
   binary-corner order."
  (vec (for [x [-1.0 1.0] y [-1.0 1.0] z [-1.0 1.0]] [x y z])))

(def ^:private cube-faces
  "Two triangles per face covering the six axis-aligned faces of
   `unit-cube-verts` by corner index (bit2=x bit1=y bit0=z, matching the
   `for` nesting above). Face culling is never enabled for the body pass
   (see `infra.render.scene.setup/render-solids-pass`) and the shared body
   shader derives its pseudo-normal from the vertex position itself rather
   than a cross-product face normal, so winding is not load-bearing here —
   only full coverage of each face is."
  [[0 1 3] [0 3 2]    ;; -x face (corners 0 1 2 3)
   [4 6 7] [4 7 5]    ;; +x face (corners 4 5 6 7)
   [0 4 5] [0 5 1]    ;; -y face (corners 0 1 4 5)
   [2 3 7] [2 7 6]    ;; +y face (corners 2 3 6 7)
   [0 2 6] [0 6 4]    ;; -z face (corners 0 2 4 6)
   [1 5 7] [1 7 3]])  ;; +z face (corners 1 3 5 7)

(defn make-cube-mesh
  "Create a unit cube mesh (half-extent 1.0, 12 triangles, position-only
   vertices) in the same non-indexed `{:buffer :vertex-count}` shape
   `make-sphere-mesh` returns, so `upload-mesh` and the shared body shader
   (which derives its normal from the local vertex position) work unchanged
   for voxel cube instances (`infra.render.scene.voxel`)."
  []
  (let [face-verts (mapcat (fn [[i j k]]
                             [(nth unit-cube-verts i)
                              (nth unit-cube-verts j)
                              (nth unit-cube-verts k)])
                           cube-faces)
        fb (BufferUtils/createFloatBuffer (* 3 (count cube-faces) 3))]
    (doseq [[x y z] face-verts]
      (.put fb (float x)) (.put fb (float y)) (.put fb (float z)))
    (.flip fb)
    {:buffer fb
     :vertex-count (* 3 (count cube-faces))}))

(defn upload-mesh
  "Upload a sphere mesh buffer to the GPU and return {:vao :vbo :count}."
  [{:keys [buffer vertex-count]}]
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
  ;; Pack a particle into interleaved floats: position 3, color 3, size 1,
  ;; density 1. Density defaults to 1.0 and is used by the nebula shader to
  ;; modulate alpha and emission.
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
  [{:keys [buffer] :as m}]
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
    {:vao vao :vbo vbo :count (:count m)}))

(defn- sprite->floats [{:keys [position color size]}]
  ;; Pack a sprite proxy into interleaved floats: position 3, color 3, size 1.
  (let [[x y z] position
        [r g b] color]
    [(float x) (float y) (float z)
     (float r) (float g) (float b)
     (float size)]))

(defn make-sprite-mesh
  "Create a GPU buffer from a seq of sprite maps. Each sprite must have
   :position [x y z], :color [r g b], and :size (pixels)."
  [sprites]
  (let [data (float-array (mapcat sprite->floats sprites))
        fb   (BufferUtils/createFloatBuffer (count data))]
    (doseq [f data] (.put fb f))
    (.flip fb)
    {:buffer fb
     :count  (count sprites)}))

(defn upload-sprite-mesh
  "Upload an interleaved sprite buffer (position 3, color 3, size 1)."
  [{:keys [buffer] :as m}]
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
    {:vao vao :vbo vbo :count (:count m)}))

(defn subdivisions-for-screen-size
  "Adaptive icosahedron subdivisions for a body of `screen-diameter` pixels.
   More pixels → more triangles so close-up spheres stay smooth. The mesh is
   only rebuilt when the requested subdivision crosses an integer threshold."
  [screen-diameter]
  (let [d (double (or screen-diameter 0.0))]
    (cond
      (>= d 1024.0) 5
      (>= d 256.0)  4
      (>= d 64.0)   3
      (>= d 16.0)   2
      :else         1)))
