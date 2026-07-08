(ns domain.orbital.kepler
  "Two-body Kepler orbit utilities."
  (:require [clojure.math :as math]))

(def ^:const two-pi (* 2.0 math/PI))

(defn kepler-period
  "Orbital period T = 2π √(a³/GM)."
  [^double a ^double GM]
  (* two-pi (math/sqrt (/ (* a a a) GM))))

(defn mean-anomaly
  "Mean anomaly M(t) = 2π(t - t0)/T, wrapped to [0, 2π)."
  [^double t ^double t0 ^double T]
  (mod (* two-pi (/ (- t t0) T)) two-pi))

(defn eccentric-anomaly
  "Solve Kepler's equation M = E - e*sin(E) by Newton–Raphson."
  ([^double M ^double e]
   (eccentric-anomaly M e 1e-10 50))
  ([^double M ^double e ^double tol ^long max-iter]
   (loop [E M, i 0]
     (let [dE (/ (- E (* e (math/sin E)) M)
                 (- 1.0 (* e (math/cos E))))]
       (cond
         (< (abs dE) tol) E
         (>= i max-iter)
         (throw (ex-info "eccentric-anomaly: no convergence"
                         {:M M :e e :E E :i i}))
         :else (recur (- E dE) (inc i)))))))

(defn true-anomaly
  "True anomaly ν from eccentric anomaly E and eccentricity e."
  [^double E ^double e]
  (* 2.0 (math/atan2
          (* (math/sqrt (+ 1.0 e)) (math/sin (* E 0.5)))
          (* (math/sqrt (- 1.0 e)) (math/cos (* E 0.5))))))

(defn orbital-state
  "Compute position and velocity in the orbital plane from classic elements."
  [{:keys [^double a ^double e ^double i
           ^double Ω ^double ω ^double t0 ^double GM]}
   ^double t]
  (let [T   (kepler-period a GM)
        M   (mean-anomaly t t0 T)
        E   (eccentric-anomaly M e)
        ν   (true-anomaly E e)
        r   (/ (* a (- 1.0 (* e e)))
               (+ 1.0 (* e (math/cos ν))))
        px  (* r (math/cos ν))
        py  (* r (math/sin ν))
        h   (math/sqrt (* GM a (- 1.0 (* e e))))
        vx  (/ (* (- (math/sin ν)) GM) h)
        vy  (/ (* (+ e (math/cos ν)) GM) h)
        cΩ  (math/cos Ω) sΩ (math/sin Ω)
        cω  (math/cos ω) sω (math/sin ω)
        ci  (math/cos i)  si (math/sin i)
        Rxx (- (* cΩ cω) (* sΩ sω ci))
        Rxy (- (- (* cΩ sω)) (* sΩ cω ci))
        Ryx (+ (* sΩ cω) (* cΩ sω ci))
        Ryy (+ (- (* sΩ sω)) (* cΩ cω ci))
        Rzx (* sω si)
        Rzy (* cω si)]
    {:position [(+ (* Rxx px) (* Rxy py))
                (+ (* Ryx px) (* Ryy py))
                (+ (* Rzx px) (* Rzy py))]
     :velocity [(+ (* Rxx vx) (* Rxy vy))
                (+ (* Ryx vx) (* Ryy vy))
                (+ (* Rzx vx) (* Rzy vy))]}))
