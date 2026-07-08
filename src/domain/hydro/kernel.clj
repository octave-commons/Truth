(ns domain.hydro.kernel
  "SPH cubic-spline kernel primitives."
  (:require
   [clojure.math :as math]))

(defn cubic-spline-dw-dq
  "Derivative dW/dq of the cubic spline (M4) kernel in 3D, dimensionless."
  [q]
  (let [q (double q)]
    (cond
      (< q 0.0)     0.0
      (<= q 0.5)    (+ (* -12.0 q) (* 18.0 q q))
      (<= q 1.0)    (let [omq (- 1.0 q)]
                      (* -6.0 omq omq))
      :else          0.0)))

(defn cubic-spline-w
  "Dimensionless cubic spline (M4) kernel W(q). The 3D normalization factor
   8/(π h³) is applied separately in `kernel`."
  [q]
  (let [q (double q)]
    (cond
      (< q 0.0)  0.0
      (<= q 0.5) (let [q2 (* q q)]
                   (+ 1.0 (* -6.0 q2) (* 6.0 q q2)))
      (<= q 1.0) (let [omq (- 1.0 q)]
                   (* 2.0 omq omq omq))
      :else      0.0)))

(defn kernel-shape
  "Dimensionless M4 cubic-spline falloff w(r², h) ∈ [0, 1].

   The SHAPE of the SPH kernel without the 8/(π h³) volume normalization:
   kernel-r2(r², h) = 8/(π h³) · kernel-shape(r², h). Peak is exactly 1 at
   r = 0; zero for r ≥ h or h ≤ 0. For consumers (e.g. the renderer's froxel
   splat) that need the physical falloff profile at a caller-chosen
   amplitude."
  [r2 h]
  (let [r2  (double r2)
        hh  (double h)
        hh2 (* hh hh)]
    (if (or (<= hh 0.0) (>= r2 hh2))
      0.0
      (cubic-spline-w (math/sqrt (/ r2 hh2))))))

(defn kernel-r2
  "Cubic-spline SPH kernel W(r²,h) in 3D. `r2` is the squared distance.
   Units 1/volume; zero outside r > h and at h = 0. Integrates to 1 over a
   sphere of radius h."
  [r2 h]
  (let [r2 (double r2)
        hh (double h)
        hh2 (* hh hh)]
    (if (or (zero? hh) (>= r2 hh2))
      0.0
      (let [inv-h  (/ 1.0 hh)
            inv-h3 (* inv-h inv-h inv-h)
            q      (math/sqrt (/ r2 hh2))]
        (* (/ 8.0 math/PI) inv-h3 (cubic-spline-w q))))))

(defn kernel
  "Cubic-spline SPH kernel W(r,h) in 3D. `r` is the distance.
   Units 1/volume; zero outside r > h and at h = 0. Integrates to 1 over a
   sphere of radius h. Thin wrapper over `kernel-r2`."
  [r h]
  (kernel-r2 (* (double r) (double r)) h))

(defn kernel-gradient
  "Gradient ∇_i W(r_ij, h) of the cubic spline kernel. Two arities:

   - (kernel-gradient r-ij h): `r-ij` is the vector from particle j to particle
     i; computes squared distance internally.
   - (kernel-gradient r-ij r2 h): uses the pre-computed squared distance `r2`.

   The result points from j toward i and has units of 1/length⁴. Returns zero
   for r = 0 or r >= h."
  ([r-ij h]
   (let [rx (double (nth r-ij 0))
         ry (double (nth r-ij 1))
         rz (double (nth r-ij 2))
         r2 (+ (* rx rx) (* ry ry) (* rz rz))]
     (kernel-gradient r-ij r2 h)))
  ([r-ij r2 h]
   (let [rx (double (nth r-ij 0))
         ry (double (nth r-ij 1))
         rz (double (nth r-ij 2))
         r2 (double r2)
         hh (double h)
         hh2 (* hh hh)]
     (if (or (zero? r2) (zero? hh) (>= r2 hh2))
       [0.0 0.0 0.0]
       (let [r   (math/sqrt r2)
             q   (/ r hh)
             dw-dq  (cubic-spline-dw-dq q)
             inv-h  (/ 1.0 hh)
             inv-h4 (* inv-h inv-h inv-h inv-h)
             factor (* (/ 8.0 math/PI) inv-h4 (/ dw-dq r))]
         [(* rx factor) (* ry factor) (* rz factor)])))))

(defn pressure-term
  "Symmetric SPH pressure term P_i/ρ_i² + P_j/ρ_j²."
  [density pressure other-density other-pressure]
  (if (and (pos? (double density)) (pos? (double other-density)))
    (+ (/ (double pressure) (* density density))
       (/ (double other-pressure) (* other-density other-density)))
    0.0))
