(ns domain.debris
  "The debris sink (spec: docs/specs/perf-60fps-parallel-tick.md, Fix 6).

   `:debris` is a real condensed population (classifier: cooled sub-stellar
   nebula → planetesimal) that otherwise leaves the world only via literal
   collision or sink capture — so late-game N grows without bound and the
   gravity/N-body cost with it. This system reaps ESCAPERS ONLY, and it is
   mass-honest: a reaped body is one that has, physically, left the system.

   A `:debris` body is escaped when, on the same snapshot,
     (a) it is farther than `escape-distance-factor ×` the system's
         mass-weighted RMS radius from the centre of mass (the guard for the
         violent phase, while the mass is spread; once a star holds the mass
         the RMS shrinks and this gate weakens by design), AND
     (b) it is unbound: ½·v_rel² > G·M_total / r  (v_rel against the COM
         frame velocity), AND
     (c) it is RECEDING: (r_rel · v_rel) > 0. An incoming or tangential body,
         however fast, may still interact — only an outbound unbound body is
         physically gone.

   Emits `c/consumed-escape` (sole writer); `materialize-lifecycle` reaps the
   marked bodies at world-construction and records one aggregated
   `:event/body-escape` per tick in the ledger, so the books stay auditable.

   Bound debris is NEVER reaped, no matter how far out (a circular orbit at
   any radius fails (b)). A pure snapshot-reading fan-out emitter."
  (:require
   [domain.ecs.core       :as ecs]
   [domain.ecs.components :as c]
   [shape.spatial         :as sp]))

(def ^:const escape-distance-factor
  "How many mass-weighted RMS radii from the COM a debris body must be before
   it is even considered for reaping. Generous: a factor-10 apoapsis is well
   past anything the formation dynamically revisits."
  10.0)

(defn- system-frame
  "Total mass, COM position, COM velocity, and mass-weighted RMS radius of the
   whole snapshot — the reference frame escape is judged against."
  [world eids]
  (let [[m px py pz vx vy vz]
        (reduce (fn [[m px py pz vx vy vz] eid]
                  (let [em (double (or (ecs/get-component world eid c/mass) 0.0))
                        [x y z]    (ecs/get-component world eid c/position)
                        [wx wy wz] (or (ecs/get-component world eid c/velocity) [0.0 0.0 0.0])]
                    [(+ m em)
                     (+ px (* em (double x))) (+ py (* em (double y))) (+ pz (* em (double z)))
                     (+ vx (* em (double wx))) (+ vy (* em (double wy))) (+ vz (* em (double wz)))]))
                [0.0 0.0 0.0 0.0 0.0 0.0 0.0]
                eids)]
    (if (pos? m)
      (let [com  [(/ px m) (/ py m) (/ pz m)]
            vcom [(/ vx m) (/ vy m) (/ vz m)]
            r2   (reduce (fn [acc eid]
                           (let [em (double (or (ecs/get-component world eid c/mass) 0.0))
                                 d  (sp/dist (ecs/get-component world eid c/position) com)]
                             (+ acc (* em d d))))
                         0.0 eids)]
        {:mass m :com com :vcom vcom :rms (Math/sqrt (/ r2 m))})
      {:mass 0.0 :com [0.0 0.0 0.0] :vcom [0.0 0.0 0.0] :rms 0.0})))

(defn escaped?
  "True when a body at `pos`/`vel` is past the distance gate, unbound from the
   system frame, AND receding. Pure."
  [G {:keys [mass com vcom rms]} pos vel]
  (let [r-rel (sp/v- pos com)
        r     (sp/len r-rel)
        v-rel (sp/v- vel vcom)]
    (and (pos? rms)
         (> r (* escape-distance-factor rms))
         (pos? (sp/dot r-rel v-rel))
         (> (* 0.5 (sp/dot v-rel v-rel))
            (/ (* (double G) (double mass)) r)))))

(defn debris-reaper-system
  "Write-set system (sole writer of c/consumed-escape): mark unbound `:debris`
   bodies beyond the escape distance for reaping."
  []
  {:id     :debris-reaper
   :writes #{c/consumed-escape}
   :run    (fn [world]
             (let [eids  (ecs/entities-with world c/matter-state c/position c/mass c/velocity)
                   G     (double (or (:sim/G world) 6.674e-11))
                   frame (system-frame world eids)
                   gone  (into {}
                               (keep (fn [eid]
                                       (when (and (= :debris (ecs/get-component world eid c/matter-state))
                                                  (escaped? G frame
                                                            (ecs/get-component world eid c/position)
                                                            (or (ecs/get-component world eid c/velocity)
                                                                [0.0 0.0 0.0])))
                                         [eid true])))
                               eids)]
               (if (seq gone) {c/consumed-escape gone} {})))})
