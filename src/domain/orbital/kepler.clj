(ns domain.orbital.kepler
  "Two-body Kepler orbit utilities.")

(def ^:const two-pi (* 2.0 Math/PI))

(defn kepler-period
  "Orbital period T = 2π √(a³/GM)."
  [^double a ^double GM]
  (* two-pi (Math/sqrt (/ (* a a a) GM))))

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
     (let [dE (/ (- E (* e (Math/sin E)) M)
                 (- 1.0 (* e (Math/cos E))))]
       (cond
         (< (Math/abs dE) tol) E
         (>= i max-iter)
         (throw (ex-info "eccentric-anomaly: no convergence"
                         {:M M :e e :E E :i i}))
         :else (recur (- E dE) (inc i)))))))

(defn true-anomaly
  "True anomaly ν from eccentric anomaly E and eccentricity e."
  [^double E ^double e]
  (* 2.0 (Math/atan2
           (* (Math/sqrt (+ 1.0 e)) (Math/sin (* E 0.5)))
           (* (Math/sqrt (- 1.0 e)) (Math/cos (* E 0.5))))))

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
               (+ 1.0 (* e (Math/cos ν))))
        px  (* r (Math/cos ν))
        py  (* r (Math/sin ν))
        h   (Math/sqrt (* GM a (- 1.0 (* e e))))
        vx  (/ (* (- (Math/sin ν)) GM) h)
        vy  (/ (* (+ e (Math/cos ν)) GM) h)
        cΩ  (Math/cos Ω) sΩ (Math/sin Ω)
        cω  (Math/cos ω) sω (Math/sin ω)
        ci  (Math/cos i)  si (Math/sin i)
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
