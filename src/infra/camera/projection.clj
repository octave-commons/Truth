(ns infra.camera.projection
  "Projection and view math for the Gates of Truth orbital camera.

   Low-level helpers: angle conversion, vector normalization, cross product, and
   column-major 4x4 perspective / look-at matrices. Also owns the Phase 0 view
   scale that maps simulation metres to render units.

   Everything here is pure: no ECS, no I/O, no mutable state."
  (:require
   [clojure.math :as math]
   [shape.spatial :as sp]))

;; ---------------------------------------------------------------------------
;; View scale
;; ---------------------------------------------------------------------------

(def ^:const phase0-view-scale
  "World metres per render unit for the Phase 0 view."
  1.0e15)

;; ---------------------------------------------------------------------------
;; Math helpers
;; ---------------------------------------------------------------------------

(defn deg->rad
  "Convert degrees to radians."
  [d]
  (* d (/ math/PI 180.0)))

(defn normalize
  "Return a unit vector in the direction of `v`; default to +z for the zero
   vector."
  [v]
  (let [l (sp/len v)]
    (if (pos? l)
      (sp/v* v (/ 1.0 l))
      [0.0 0.0 1.0])))

(defn cross
  "Cross product of two 3-vectors."
  [a b]
  (sp/cross a b))

(defn perspective
  "Build a column-major 4x4 perspective projection matrix."
  [fov-deg aspect near far]
  (let [f (/ 1.0 (math/tan (/ (deg->rad fov-deg) 2.0)))
        nf (/ 1.0 (- near far))]
    (float-array [(/ f aspect) 0.0 0.0 0.0
                  0.0 f 0.0 0.0
                  0.0 0.0 (* (+ far near) nf) -1.0
                  0.0 0.0 (* 2.0 far near nf) 0.0])))

(defn look-at
  "Build a column-major 4x4 view matrix that looks from `eye` toward `center`
   with `up` as the world up direction."
  [eye center up]
  (let [f (normalize (sp/v- center eye))
        s (normalize (cross f up))
        u (cross s f)]
    (float-array [(nth s 0) (nth u 0) (- (nth f 0)) 0.0
                  (nth s 1) (nth u 1) (- (nth f 1)) 0.0
                  (nth s 2) (nth u 2) (- (nth f 2)) 0.0
                  (- (sp/dot s eye)) (- (sp/dot u eye)) (sp/dot f eye) 1.0])))
