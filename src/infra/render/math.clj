(ns infra.render.math
  "Renderer-specific column-major GL matrix helpers.
   These are render-space utilities; generic vector math stays in
   `shape.spatial` and camera transforms in `infra.camera`."
  (:require
   [infra.camera :as cam]))

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
  ;; Scale matrix for an oblate spheroid with equatorial radius a and polar radius c.
  (float-array [(double a) 0.0      0.0      0.0
                0.0      (double a) 0.0      0.0
                0.0      0.0      (double c) 0.0
                0.0      0.0      0.0        1.0]))

(defn- rotation-align-z [axis]
  ;; Rotation matrix (column-major) that aligns the mesh z-axis with `axis`.
  (let [n (cam/normalize axis)
        helper (if (< (abs (nth n 2)) 0.9) [0.0 0.0 1.0] [1.0 0.0 0.0])
        x (cam/normalize (cam/cross helper n))
        y (cam/cross n x)
        [x0 x1 x2] x
        [y0 y1 y2] y
        [n0 n1 n2] n]
    (float-array [x0 x1 x2 0.0
                  y0 y1 y2 0.0
                  n0 n1 n2 0.0
                  0.0 0.0 0.0 1.0])))

(defn- mat4*
  "Column-major 4x4 matrix product A·B. Both operands and the result are stored
   column-major (arr[col*4+row]), matching how the matrices are built here and
   handed to GL with transpose=false. (A·B)[r][c] = Σ_k A[r][k]·B[k][c], so
   out[col*4+row] = Σ_k a[k*4+row]·b[col*4+k]."
  [a b]
  (let [out (float-array 16)]
    (doseq [i (range 4)      ;; i = column of the result
            j (range 4)]     ;; j = row of the result
      (aset out (+ (* i 4) j)
            (float (+ (* (aget a j) (aget b (* i 4)))
                      (* (aget a (+ 4  j)) (aget b (inc (* i 4))))
                      (* (aget a (+ 8  j)) (aget b (+ (* i 4) 2)))
                      (* (aget a (+ 12 j)) (aget b (+ (* i 4) 3)))))))
    out))

(defn model-matrix
  "Build a column-major model matrix from `position` and `radius`.
   The 4-arity form builds an oblate spheroid using `oblateness` and
   `rotation-axis`."
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
