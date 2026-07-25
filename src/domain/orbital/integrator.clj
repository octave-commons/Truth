(ns domain.orbital.integrator
  "Symplectic Leapfrog (Störmer–Verlet) integrator for n-body orbital mechanics.
   All positions and velocities are vec3 from shape.spatial.
   Requires a pure acceleration function (fn [body] -> vec3)."
  (:require
   [shape.spatial :as sp]))

(defn leapfrog-kick
  "Velocity half-step (kick): v_half = v + a * (dt/2)"
  [body accel-fn ^double dt]
  (let [a (accel-fn body)]
    (update body :velocity sp/v+ (sp/v* a (* dt 0.5)))))

(defn leapfrog-drift
  "Position full-step (drift): x_new = x + v * dt"
  [body ^double dt]
  (update body :position sp/v+ (sp/v* (:velocity body) dt)))

(defn leapfrog-step
  "Full Leapfrog step (kick-drift-kick)."
  [body accel-fn ^double dt]
  (-> body
      (leapfrog-kick accel-fn dt)
      (leapfrog-drift dt)
      (leapfrog-kick accel-fn dt)))

