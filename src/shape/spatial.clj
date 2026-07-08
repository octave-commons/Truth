(ns shape.spatial
  "Domain-agnostic 3D spatial primitives..."
  (:require [clojure.math :as math])
  (:refer-clojure :exclude [contains?]))

;; --- vec3 -------------------------------------------------------------------

(defn vec3
  "Construct a 3-vector of doubles, defaulting omitted components to 0.0."
  (^clojure.lang.IPersistentVector [] [0.0 0.0 0.0])
  (^clojure.lang.IPersistentVector [x] [(double x) 0.0 0.0])
  (^clojure.lang.IPersistentVector [x y] [(double x) (double y) 0.0])
  (^clojure.lang.IPersistentVector [x y z] [(double x) (double y) (double z)]))

(defn v+
  "Componentwise addition of two vec3s."
  [[ax ay az] [bx by bz]]
  [(+ (double ax) (double bx))
   (+ (double ay) (double by))
   (+ (double az) (double bz))])

(defn v-
  "Componentwise subtraction: a - b."
  [[ax ay az] [bx by bz]]
  [(- (double ax) (double bx))
   (- (double ay) (double by))
   (- (double az) (double bz))])

(defn v*
  "Scale vec3 by scalar s."
  [[ax ay az] s]
  (let [s (double s)]
    [(* ax s) (* ay s) (* az s)]))

(defn dot
  "Dot product of two vec3s."
  [[ax ay az] [bx by bz]]
  (+ (* (double ax) (double bx))
     (* (double ay) (double by))
     (* (double az) (double bz))))

(defn len2
  "Squared length of vec3."
  [v]
  (dot v v))

(defn len
  "Euclidean length of vec3."
  [v]
  (math/sqrt (len2 v)))

(defn dist
  "Distance between two vec3s."
  [a b]
  (len (v- b a)))

(defn cross
  "Cross product of two vec3s."
  [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by))
   (- (* az bx) (* ax bz))
   (- (* ax by) (* ay bx))])

;; --- AABB -------------------------------------------------------------------

;; Intentional: AABB is the conventional acronym for axis-aligned bounding box
;; used throughout graphics and spatial code; renaming to Aabb would break
;; references and read against domain convention.
#_{:splint/disable [naming/record-name]}
(defrecord AABB [aabb-min aabb-max])

(defn aabb
  "Construct an AABB from min and max vec3s."
  [aabb-min-v aabb-max-v]
  (->AABB aabb-min-v aabb-max-v))

(defn aabb-from-points
  "Smallest AABB containing all given points (vec3s)."
  [points]
  (assert (seq points) "aabb-from-points: need at least one point")
  (let [[[x0 y0 z0] & more] points]
    (loop [min-x (double x0)
           min-y (double y0)
           min-z (double z0)
           max-x (double x0)
           max-y (double y0)
           max-z (double z0)
           ps   more]
      (if-let [[x y z] (first ps)]
        (recur (min min-x (double x))
               (min min-y (double y))
               (min min-z (double z))
               (max max-x (double x))
               (max max-y (double y))
               (max max-z (double z))
               (next ps))
        (->AABB [min-x min-y min-z] [max-x max-y max-z])))))

(defn aabb-include
  "Extend AABB to include point p."
  [^AABB bb [x y z]]
  (let [[min-x min-y min-z] (:aabb-min bb)
        [max-x max-y max-z] (:aabb-max bb)]
    (->AABB [(min min-x (double x))
             (min min-y (double y))
             (min min-z (double z))]
            [(max max-x (double x))
             (max max-y (double y))
             (max max-z (double z))])))

(defn contains?
  "Does the AABB contain point p (inclusive)?"
  [^AABB bb [x y z]]
  (let [[min-x min-y min-z] (:aabb-min bb)
        [max-x max-y max-z] (:aabb-max bb)
        x (double x) y (double y) z (double z)]
    (and (<= min-x x max-x)
         (<= min-y y max-y)
         (<= min-z z max-z))))

(defn center
  "Center of the AABB."
  [^AABB bb]
  (let [[min-x min-y min-z] (:aabb-min bb)
        [max-x max-y max-z] (:aabb-max bb)]
    [(/ (+ min-x max-x) 2.0)
     (/ (+ min-y max-y) 2.0)
     (/ (+ min-z max-z) 2.0)]))

(defn extent
  "Size of the AABB along each axis (max - min)."
  [^AABB bb]
  (v- (:aabb-max bb) (:aabb-min bb)))

(defn max-side
  "Largest side length of the AABB, used as size s in Barnes–Hut criterion."
  [^AABB bb]
  (let [[sx sy sz] (extent bb)]
    (max (abs (double sx))
         (abs (double sy))
         (abs (double sz)))))

;; --- Octants ----------------------------------------------------------------

(defn octant
  "Classify point p into an octant of AABB bb relative to its center.
   Points exactly on a plane are treated as positive (>=) along that axis
   to keep classification deterministic."
  [^AABB bb [x y z]]
  (let [[cx cy cz] (center bb)
        px? (>= (double x) (double cx))
        py? (>= (double y) (double cy))
        pz? (>= (double z) (double cz))]
    (cond
      (and px? py? pz?) :octant/ppp
      (and px? py? (not pz?)) :octant/ppm
      (and px? (not py?) pz?) :octant/pmp
      (and px? (not py?) (not pz?)) :octant/pmm
      (and (not px?) py? pz?) :octant/mpp
      (and (not px?) py? (not pz?)) :octant/mpm
      (and (not px?) (not py?) pz?) :octant/mmp
      :else :octant/mmm)))

(defn child-aabb
  "Given parent AABB and an octant keyword, return the child's AABB."
  [^AABB bb oct]
  (let [[min-x min-y min-z] (:aabb-min bb)
        [max-x max-y max-z] (:aabb-max bb)
        [cx cy cz]          (center bb)]
    (case oct
      :octant/ppp (->AABB [cx cy cz] [max-x max-y max-z])
      :octant/ppm (->AABB [cx cy min-z] [max-x max-y cz])
      :octant/pmp (->AABB [cx min-y cz] [max-x cy max-z])
      :octant/pmm (->AABB [cx min-y min-z] [max-x cy cz])
      :octant/mpp (->AABB [min-x cy cz] [cx max-y max-z])
      :octant/mpm (->AABB [min-x cy min-z] [cx max-y cz])
      :octant/mmp (->AABB [min-x min-y cz] [cx cy max-z])
      :octant/mmm (->AABB [min-x min-y min-z] [cx cy cz]))))

;; --- Bodies -----------------------------------------------------------------

(defrecord Body
           [id mass radius kind position velocity])

(defn ->body
  "Construct a Body from a map with keys:
   :id, :mass, :radius, :kind, :position, :velocity."
  [{:keys [id mass radius kind position velocity] :as m}]
  (when-not (and id mass radius kind position velocity)
    (throw (ex-info "Body requires :id, :mass, :radius, :kind, :position, :velocity"
                    {:kind ::invalid-body :body m})))
  (->Body id (double mass) (double radius) kind position velocity))
