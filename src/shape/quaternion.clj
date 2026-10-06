(ns shape.quaternion
  "Scalar-first Hamilton rotations mapping body axes to world axes."
  (:require
   [clojure.math :as math]
   [law.rotation :as law]
   [shape.spatial :as sp]))

(defn- multiply
  "Hamilton product of scalar-first quaternions."
  [[aw ax ay az] [bw bx by bz]]
  [(- (* aw bw) (* ax bx) (* ay by) (* az bz))
   (+ (* aw bx) (* ax bw) (* ay bz) (- (* az by)))
   (+ (* aw by) (- (* ax bz)) (* ay bw) (* az bx))
   (+ (* aw bz) (* ax by) (- (* ay bx)) (* az bw))])

(defn advance
  "Advance a body-to-world quaternion at constant world angular rate for dt.

   Enforces law.rotation/rotation-input?. World-axis angular velocity means
   the exponential increment multiplies on the left (Solà 2017 §4.5.1).
   Normalization removes roundoff; it never repairs invalid input state."
  [orientation angular-velocity dt]
  (when-not (law/rotation-input? {:orientation orientation
                                  :angular-velocity angular-velocity :dt dt})
    (throw (ex-info "Invalid quaternion integration input"
                    {:orientation orientation :angular-velocity angular-velocity :dt dt})))
  (let [[x y z] angular-velocity
        speed (math/hypot x (math/hypot y z))]
    (when-not (Double/isFinite speed)
      (throw (ex-info "Angular speed exceeds numerical range" {:angular-velocity angular-velocity})))
    (if (or (zero? speed) (zero? dt))
      orientation
      (let [;; Reduce time by the quaternion's 4π period BEFORE multiplication:
            ;; a finite cosmological dt must not overflow the half-angle.
            period (/ (* 4.0 math/PI) speed)
            reduced-dt (if (Double/isFinite period) (rem (double dt) period) (double dt))
            half-angle (* speed reduced-dt 0.5)
            axis (mapv #(/ % speed) angular-velocity)
            delta (into [(math/cos half-angle)] (sp/v* axis (math/sin half-angle)))
            result (multiply delta orientation)
            norm (math/sqrt (reduce + (map #(* % %) result)))]
        (mapv #(/ % norm) result)))))
