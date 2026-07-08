(ns infra.render.volume
  "Volumetric ray-marching for the nebula and protoplanetary disk.
   Builds a 3D froxel texture from SPH gas samples and composites it over the
   scene with a fullscreen ray-march pass."
  (:require
   [clojure.math :as math] [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.hydro :as hydro]
   [shape.spatial :as sp]
   [infra.camera :as cam]
   [infra.render.shader :as sh]
   [infra.render.field :as rfield]
   [infra.render.units :as units]
   [infra.render.color :as color])
  (:import
   (org.lwjgl.opengl GL11 GL12 GL13 GL15 GL20 GL30)
   (org.lwjgl BufferUtils)))

(defn create-volume-program
  "Backward-compatible volume ray-march program constructor."
  []
  (sh/program-id :volume))

(defn fullscreen-quad-vao
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

(defn render-samples
  "Volumetric gas samples in RENDER space, projected from the domain SPH gas
   field (`domain.hydro/gas-samples`). Gas and dust are rendered with different
   temperature meanings: a parcel with a dominant solid fraction (>50%) is
   treated as dust and coloured with `disk-temp-color`, and gets a density boost
   so the disk reads as a distinct, continuous structure. Gas parcels keep the
   nebula ramp and receive a weak ionization tint."
  [ctx world cfg]
  (->> (hydro/gas-samples world)
       (mapv (fn [{:keys [position smoothing-h density temperature ionization
                          _disc-tag solid-fraction]}]
               (let [solid    (double (or solid-fraction 0.0))
                     dust?    (> solid 0.5)
                     base-col (if dust?
                                (color/disk-temp-color temperature)
                                (color/gas-temp-color temperature))
                     dens     (max 0.0 (rfield/density-norm density))]
                 {:p    (units/world->render ctx position)
                  :h    (max (double (:visual-h-min cfg))
                             (* (double (:visual-h-scale cfg))
                                (units/phys->render-radius ctx (* 0.5 (double smoothing-h)))))
                  :col  (if dust?
                          base-col
                          (rfield/ionization-tint base-col ionization))
                  :dens (if dust?
                          (* 5.0 dens)
                          dens)})))))

;; Persistent froxel texture + scratch buffers, reused every frame.
(defonce ^:private volume-cache (atom nil))

(defn reset-volume-cache!
  "Drop the cached froxel texture. MUST be called when rendering in a fresh GL
   context (offscreen screenshots): the cached texture id belongs to the
   context that created it."
  []
  (reset! volume-cache nil))

(defn- volume-storage!
  "Get-or-create the persistent 3D texture + host arrays for resolution `res`."
  [res]
  (let [R (int res) c @volume-cache]
    (if (and c (= (:res c) R))
      c
      (let [tex  (GL11/glGenTextures)
            n    (* 4 R R R)
            data (float-array n)
            buf  (BufferUtils/createFloatBuffer n)]
        (GL13/glActiveTexture GL13/GL_TEXTURE0)
        (GL11/glBindTexture GL12/GL_TEXTURE_3D tex)
        (GL12/glTexImage3D GL12/GL_TEXTURE_3D 0 GL30/GL_RGBA16F R R R 0
                           GL11/GL_RGBA GL11/GL_FLOAT (BufferUtils/createFloatBuffer n))
        (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
        (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
        (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
        (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
        (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL12/GL_TEXTURE_WRAP_R GL12/GL_CLAMP_TO_EDGE)
        (GL11/glBindTexture GL12/GL_TEXTURE_3D 0)
        (reset! volume-cache {:res R :tex tex :data data :buf buf})))))

(defn build-volume-texture
  "Bake render-space gas samples into the persistent RxRxR RGBA16F 3D texture
   (rgb=emission, a=density) covering their bounding box. Returns
   {:tex :box-min :box-max} or nil when there is no gas."
  [pts res cfg]
  (let [pts (rfield/cull-gas-outliers pts)]
    (when-let [[bmn bmx] (rfield/splat-bounds pts)]
      (let [R     (int res)
            store (volume-storage! R)
            ^floats data (:data store)]
        (java.util.Arrays/fill data (float 0.0))
        (rfield/splat! {:data data :res R :pts pts :bmn bmn :bmx bmx :gain (:splat-gain cfg)})
        (let [^java.nio.FloatBuffer buf (:buf store)]
          (.clear buf) (.put buf data) (.flip buf)
          (GL13/glActiveTexture GL13/GL_TEXTURE0)
          (GL11/glBindTexture GL12/GL_TEXTURE_3D (:tex store))
          (GL12/glTexSubImage3D GL12/GL_TEXTURE_3D 0 0 0 0 R R R
                                GL11/GL_RGBA GL11/GL_FLOAT buf)
          (GL11/glBindTexture GL12/GL_TEXTURE_3D 0)
          {:tex (:tex store) :box-min bmn :box-max bmx})))))

(defn volume-lights
  "Up to 8 brightest hot bodies (stars + hot cores) as point lights in render
   space — the sources whose light scatters through the medium."
  [ctx world]
  (->> (ecs/entities-with world c/position c/matter-state c/temperature)
       (keep (fn [eid]
               (let [t (double (or (ecs/get-component world eid c/temperature) 0.0))]
                 (when (> t 2500.0)
                   (let [[x y z] (ecs/get-component world eid c/position)]
                     {:pos (units/world->render ctx [x y z])
                      :col (color/temp-color t)
                      :temp t
                      :intensity (min 60.0 (* 6.0 (max 0.0 (- (math/log10 t) 3.0))))})))))
       (sort-by :temp >)
       (take 8)
       vec))

(defn- camera-basis-for-volume
  "Camera basis + projection constants for the ray-march pass."
  [camera width height]
  (let [fwd   (cam/normalize (sp/v- (:target camera) (:position camera)))
        right (cam/normalize (cam/cross fwd [0.0 0.0 1.0]))
        up    (cam/cross right fwd)
        fov   60.0]
    {:fwd       fwd
     :right     right
     :up        up
     :tan-half  (math/tan (cam/deg->rad (/ fov 2.0)))
     :aspect    (/ (double width) (double height))}))

(defn- set-volume-uniforms
  [program camera basis box-min box-max config]
  (let [{:keys [kappa emission-scale scatter-scale jitter]}
        (merge rfield/default-volume-config config)
        loc  (fn [n] (GL20/glGetUniformLocation program n))
        set3 (fn [n [a b c]] (GL20/glUniform3f (loc n) (float a) (float b) (float c)))]
    (GL20/glUseProgram program)
    (set3 "camPos" (:position camera))
    (set3 "camRight" (:right basis))
    (set3 "camUp" (:up basis))
    (set3 "camFwd" (:fwd basis))
    (GL20/glUniform1f (loc "tanHalfFov") (float (:tan-half basis)))
    (GL20/glUniform1f (loc "aspect") (float (:aspect basis)))
    (set3 "boxMin" box-min)
    (set3 "boxMax" box-max)
    (GL20/glUniform1f (loc "kappa") (float kappa))
    (GL20/glUniform1f (loc "emissionScale") (float emission-scale))
    (GL20/glUniform1f (loc "scatterScale") (float scatter-scale))
    (GL20/glUniform1f (loc "jitter") (float jitter))))

(defn- set-volume-lights
  [program lights]
  (let [loc  (fn [n] (GL20/glGetUniformLocation program n))
        set3 (fn [n [a b c]] (GL20/glUniform3f (loc n) (float a) (float b) (float c)))]
    (GL20/glUniform1i (loc "numLights") (int (count lights)))
    (dotimes [i (count lights)]
      (let [{:keys [pos col intensity]} (nth lights i)]
        (set3 (format "lightPos[%d]" i) pos)
        (set3 (format "lightColor[%d]" i) col)
        (GL20/glUniform1f (loc (format "lightIntensity[%d]" i)) (float intensity))))))

(defn- bind-volume-quad
  [program tex quad-vao]
  (GL13/glActiveTexture GL13/GL_TEXTURE0)
  (GL11/glBindTexture GL12/GL_TEXTURE_3D tex)
  (GL20/glUniform1i (GL20/glGetUniformLocation program "volume") (int 0))
  (GL11/glEnable GL11/GL_BLEND)
  (GL11/glBlendFunc GL11/GL_ONE GL11/GL_ONE_MINUS_SRC_ALPHA)
  (GL11/glDepthMask false)
  (GL30/glBindVertexArray quad-vao)
  (GL11/glDrawArrays GL11/GL_TRIANGLES 0 6))

(defn- unbind-volume-quad
  []
  (GL30/glBindVertexArray 0)
  (GL11/glDepthMask true)
  (GL11/glBindTexture GL12/GL_TEXTURE_3D 0))

(defn render-volume
  "Ray-march pass: composite the baked gas volume over the current scene with
   premultiplied-alpha blending. `volume` is {:program :tex :box-min :box-max
   :lights [...] :config {...}}; camera basis is derived from the camera
   position/target."
  [{:keys [volume quad-vao camera width height]}]
  (let [{:keys [program tex box-min box-max lights config]} volume]
    (when (and program tex)
      (let [basis (camera-basis-for-volume camera width height)]
        (set-volume-uniforms program camera basis box-min box-max config)
        (set-volume-lights program lights)
        (bind-volume-quad program tex quad-vao)
        (unbind-volume-quad)))))

(defn froxel-resolution-for
  "Choose an adaptive froxel grid resolution from the gas sample count `n` and
   a user quality target."
  [n quality]
  (let [n (max 1 (int n))
        q (condp = quality :low 0.5 :high 1.5 :ultra 2.0 1.0)
        base (cond
               (<= n 100)  64
               (<= n 300)  48
               (<= n 600)  32
               :else       24)]
    (int (max 16 (min 128 (* base q))))))

(defn frame-volume
  "Build the per-frame volume descriptor (3D texture + lights + config) for
   the ray-march pass from the live world, or nil when there is no gas or no
   program."
  [{:keys [ctx world program res cfg]}]
  (when program
    (let [cfg     (merge rfield/default-volume-config cfg)
          samples (render-samples ctx world cfg)
          R       (if (keyword? res)
                    (froxel-resolution-for (count samples) res)
                    (int (or res 32)))]
      (when-let [vt (build-volume-texture samples R cfg)]
        (assoc vt :program program
               :lights (volume-lights ctx world)
               :config cfg)))))

(defn delete-volume
  "The froxel texture is persistent (reused across frames via volume-cache);
   nothing to free per frame. Kept for call-site symmetry."
  [_volume]
  nil)
