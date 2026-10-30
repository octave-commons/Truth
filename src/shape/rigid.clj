(ns shape.rigid
  "Quaternion geometry and torque-free rotation of a fixed inertia tensor."
  (:require [shape.spatial :as sp]))

(defn matvec
  "Multiply a 3x3 matrix by a vector."
  [matrix v]
  (mapv #(sp/dot % v) matrix))

(defn inverse
  "Invert a nonsingular 3x3 tensor, rejecting unresolved rotation axes."
  [[[a b c] [d e f] [g h i]]]
  (let [det (+ (* a (- (* e i) (* f h))) (* (- b) (- (* d i) (* f g))) (* c (- (* d h) (* e g))))]
    (when-not (and (Double/isFinite (double det)) (pos? det))
      (throw (ex-info "Singular or invalid material inertia" {:determinant det})))
    (mapv #(mapv (fn [x] (/ x det)) %)
          [[(- (* e i) (* f h)) (- (* c h) (* b i)) (- (* b f) (* c e))]
           [(- (* f g) (* d i)) (- (* a i) (* c g)) (- (* c d) (* a f))]
           [(- (* d h) (* e g)) (- (* b g) (* a h)) (- (* a e) (* b d))]])))

(defn conjugate
  "Inverse of a unit quaternion."
  [[w x y z]] [w (- x) (- y) (- z)])

(defn- multiply [[a b c d] [e f g h]]
  [(- (* a e) (* b f) (* c g) (* d h))
   (+ (* a f) (* b e) (* c h) (- (* d g)))
   (+ (* a g) (- (* b h)) (* c e) (* d f))
   (+ (* a h) (* b g) (- (* c f)) (* d e))])

(defn- normalize [q]
  (let [norm (Math/sqrt (reduce + (map #(* % %) q)))]
    (when-not (and (Double/isFinite norm) (pos? norm))
      (throw (ex-info "Invalid orientation" {:quaternion q})))
    (mapv #(/ % norm) q)))

(defn rotate
  "Rotate a vector by a unit quaternion."
  [q [x y z]]
  (subvec (multiply (multiply q [0.0 x y z]) (conjugate q)) 1))

(defn inertia
  "Point-carrier inertia about the supplied mass center."
  [items center]
  (reduce (fn [tensor {:keys [p m]}]
            (let [r (sp/v- p center) r2 (sp/dot r r)]
              (mapv (fn [i] (mapv (fn [j]
                                    (+ (get-in tensor [i j])
                                       (* m (- (if (= i j) r2 0.0) (* (r i) (r j))))))
                                  (range 3))) (range 3))))
          [[0.0 0.0 0.0] [0.0 0.0 0.0] [0.0 0.0 0.0]] items))

(defn angular-velocity
  "World-frame angular velocity from fixed world angular momentum."
  [q world-l inverse-inertia]
  (rotate q (matvec inverse-inertia (rotate (conjugate q) world-l))))

(defn step
  "Advance torque-free orientation by normalized RK4; angular momentum stays fixed."
  [q world-l inverse-inertia dt]
  (let [derivative (fn [q]
                     (let [q (normalize q)
                           body-omega (matvec inverse-inertia (rotate (conjugate q) world-l))]
                       (mapv #(* 0.5 %) (multiply q (into [0.0] body-omega)))))
        shift (fn [q k factor] (mapv + q (mapv #(* factor %) k)))
        k1 (derivative q) k2 (derivative (shift q k1 (* 0.5 dt)))
        k3 (derivative (shift q k2 (* 0.5 dt))) k4 (derivative (shift q k3 dt))]
    (normalize (mapv (fn [q a b c d] (+ q (* (/ dt 6.0) (+ a (* 2 b) (* 2 c) d)))) q k1 k2 k3 k4))))
