(ns infra.render.field
  "Pure froxel-field construction: SPH-kernel splatting of render-space gas
   samples into host float arrays. The splat weight is the same M4
   cubic-spline profile the simulation integrates (`domain.hydro/kernel-shape`),
   so the baked volume shows the structure the physics sees. No GL here —
   `infra.render` owns texture upload."
  (:require
   [clojure.math :as math] [domain.hydro :as hydro]
   [shape.spatial :as sp]))

(def default-volume-config
  "Default tuning knobs for the volumetric fog pipeline (see
   law.render/volume-config). :kappa/:emission-scale/:scatter-scale/:jitter
   are ray-march uniforms; :visual-h-scale and :visual-h-min inflate each
   sample's render-space support so adjacent parcels overlap into a continuous
   medium (support must exceed the ~4 ru inter-parcel spacing or the cloud
   reads as separate spheres); :splat-gain scales the splat weight.

    Eye-tuned 2026-07-07: lower kappa (0.045) keeps the cloud translucent
    rather than turning into an opaque skin, while a higher emissionScale
    (2.2) preserves the baked colour brightness. Scatter is multiplied by
    the cloud's own colour in the shader so stars tint the fog rather than
    bleaching it to white."
  {:kappa 0.045
   :emission-scale 2.2
   :scatter-scale 2.5
   :jitter 1.0
   :visual-h-scale 10.0
   :visual-h-min 4.0
   :splat-gain 1.0})

(defn density-norm
  "Map a physical density (kg/m³) to a [0,1] visual factor with a wide log
   dynamic range. The nebula spans roughly 1e-21 … 1e-12 kg/m³; this mapping
   makes a factor-of-1000 density contrast readable instead of clamping
   everything to the same narrow band."
  [rho]
  (let [log-rho (math/log10 (max 1e-30 (double (or rho 1e-18))))
        lo -21.0
        hi -12.0]
    (max 0.0 (min 1.0 (/ (- log-rho lo) (- hi lo))))))

(defn ionization-tint
  "Shift a temperature-derived RGB color toward blue-white plasma as the
    ionization fraction rises.  The shift is deliberately weak (max 10% at
    full ionization) so the cloud keeps its underlying colour rather than
    being bleached white.  Disk parcels skip this entirely in `gas-samples`."
  [[r g b] ion]
  (let [ion (double (or ion 0.0))]
    (if (pos? ion)
      (let [f (min 0.10 (* ion 0.10))]
        [(+ (* (- 1.0 f) r) (* f 0.7))
         (+ (* (- 1.0 f) g) (* f 0.8))
         (+ (* (- 1.0 f) b) (* f 1.0))])
      [r g b])))

(defn quantile
  "Value at fraction `q` (0..1) of `xs` via linear interpolation on the sorted
   order. Returns nil for an empty collection."
  [xs q]
  (let [v (vec (sort xs))
        n (count v)]
    (when (pos? n)
      (let [pos (* (double q) (dec n))
            lo  (int (math/floor pos))
            hi  (min (dec n) (inc lo))
            f   (- pos lo)]
        (+ (* (- 1.0 f) (double (v lo))) (* f (double (v hi))))))))

(defn cull-gas-outliers
  "Drop gas parcels flung far outside the bulk of the medium. The froxel texture
   is a fixed-resolution grid over the AABB of all parcels, so a single parcel
   thrown into deep space balloons the box and collapses the whole nebula into a
   handful of voxels (the LOD 'behaves very poorly'). We measure distance from a
   component-wise median centre (robust to outliers) and keep parcels within a
   generous multiple of the 95th-percentile radius, so real structure survives
   but escapees can't stretch the bounds. Culled parcels simply don't contribute
   to the fog — they're negligible visually anyway. No-op below a floor count."
  [pts]
  (if (< (count pts) 8)
    pts
    (let [ps     (mapv :p pts)
          centre (mapv (fn [axis] (quantile (map #(nth % axis) ps) 0.5)) [0 1 2])
          dists  (mapv #(sp/dist centre %) ps)
          scale  (or (quantile dists 0.95) 0.0)
          thresh (* 4.0 scale)]
      (if (pos? thresh)
        (filterv #(<= (sp/dist centre (:p %)) thresh) pts)
        pts))))

(defn splat-bounds
  "Render-space AABB covering every sample's full kernel support (p ± h),
   padded so elongated clouds aren't clipped at the box faces. Returns
   [box-min box-max], or nil when there are no samples."
  [pts]
  (when (seq pts)
    (let [[bmn0 bmx0] (reduce (fn [[mn mx] {:keys [p h]}]
                                [(mapv #(min %1 (- %2 h)) mn p)
                                 (mapv #(max %1 (+ %2 h)) mx p)])
                              [[1e30 1e30 1e30] [-1e30 -1e30 -1e30]] pts)
          pad (mapv #(max 0.5 (* 0.06 (- %1 %2))) bmx0 bmn0)]
      [(mapv - bmn0 pad) (mapv + bmx0 pad)])))

(defn- sample-voxel-range
  "Clamp the per-axis index range [lox hix loy hiy loz hiz] for one sample."
  [R bmn cs p h]
  (let [vidx (fn [coord mn c]
               (int (math/floor (/ (- (double coord) (double mn))
                                   (double c)))))
        [px py pz] p
        [csx csy csz] cs]
    [(max 0 (vidx (- px h) (bmn 0) csx))
     (min (dec R) (vidx (+ px h) (bmn 0) csx))
     (max 0 (vidx (- py h) (bmn 1) csy))
     (min (dec R) (vidx (+ py h) (bmn 1) csy))
     (max 0 (vidx (- pz h) (bmn 2) csz))
     (min (dec R) (vidx (+ pz h) (bmn 2) csz))]))

(defn- splat-sample!
  "Accumulate one gas sample into `data` over its local voxel footprint."
  [^floats data R idx bmn cs gain {:keys [p h col dens]}]
  (when (pos? (double dens))
    (let [[px py pz] p
          h    (double h)
          hh2  (* h h)
          dens (double dens)
          [r g b] col
          [csx csy csz] cs
          [lox hix loy hiy loz hiz] (sample-voxel-range R bmn cs p h)]
      (doseq [z (range loz (inc hiz))
              y (range loy (inc hiy))
              x (range lox (inc hix))
              :let [vcz (+ (double (bmn 2)) (* (+ z 0.5) csz))
                    vcy (+ (double (bmn 1)) (* (+ y 0.5) csy))
                    vcx (+ (double (bmn 0)) (* (+ x 0.5) csx))
                    dx  (- vcx px)
                    dy  (- vcy py)
                    dz  (- vcz pz)
                    d2  (+ (* dx dx) (* dy dy) (* dz dz))]
              :when (< d2 hh2)]
        (let [w  (* gain (hydro/kernel-shape d2 h))
              wd (* w dens)
              i  (idx x y z)]
          (aset data i       (float (+ (aget data i)       (* wd (double r)))))
          (aset data (inc i) (float (+ (aget data (inc i)) (* wd (double g)))))
          (aset data (+ i 2) (float (+ (aget data (+ i 2)) (* wd (double b)))))
          (aset data (+ i 3) (float (+ (aget data (+ i 3)) wd))))))))

(defn splat!
  "Accumulate every gas sample into the RGBA host array `data` (rgb = emission,
   a = density) over an R³ voxel grid spanning [bmn, bmx]. Each sample is
   weighted by `gain · kernel-shape(r², h) · dens` at the voxel center — the
   simulation's own M4 falloff at a caller-chosen amplitude. Mutates and
   returns `data`.

   Accepts a single options map: {:data :res :pts :bmn :bmx :gain}."
  [{:keys [^floats data res pts bmn bmx gain]}]
  (let [R    (int res)
        gain (double gain)
        span (mapv - bmx bmn)
        cs   (mapv #(/ (double %) R) span)
        idx  (fn [x y z] (* 4 (+ x (* R (+ y (* R z))))))]
    (doseq [pt pts]
      (splat-sample! data R idx bmn cs gain pt))
    data))

(defn splat-field
  "Bake gas samples into a freshly allocated RGBA float array over their padded
   AABB: {:data :res :box-min :box-max}, or nil when there is no gas. The pure
   headless core of the froxel pipeline — `infra.render` uploads :data into the
   persistent 3D texture."
  [pts res gain]
  (when-let [[bmn bmx] (splat-bounds pts)]
    (let [R (int res)
          data (float-array (* 4 R R R))]
      (splat! {:data data :res R :pts pts :bmn bmn :bmx bmx :gain gain})
      {:data data :res R :box-min bmn :box-max bmx})))
