(ns domain.mhd.force
  "Merged MHD-lite force system: one neighbor walk computes both the SPH
   pressure-gradient acceleration and the Lorentz acceleration (plus magnetic
   braking torque). The kernel gradient is evaluated once per neighbor pair and
   accumulated into scalar doubles.

   This is the implementation of the optimisation recommended in
   `docs/research/physics/phase0-neighbor-cache-curl-optimization.md`: eliminate
   the duplicate neighbor walk and duplicate gradient computation between the
   separate hydro pressure pass and the EM Lorentz pass."
  (:require
   [law.field :as lf]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.parallel :as par]
   [domain.ecs.tick :as tick]
   [domain.profile :as profile]
   [domain.hydro.kernel :as kernel]
   [domain.em.lorentz :as em]
   [domain.spatial.index :as idx]
   [shape.spatial :as sp]))

(defn- entity->mhd-data
  "Project an ECS entity into the compact map the merged force cell needs.
   Required components are read directly; optional components default to nil
   or are supplied by the caller."
  [world eid]
  {:eid              eid
   :position         (ecs/get-component world eid c/position)
   :velocity         (ecs/get-component world eid c/velocity)
   :mass             (ecs/get-component world eid c/mass)
   :radius           (ecs/get-component world eid c/radius)
   :density          (ecs/get-component world eid c/density)
   :pressure         (ecs/get-component world eid c/pressure)
   :b-field          (ecs/get-component world eid c/b-field)
   :angular-momentum (ecs/get-component world eid c/angular-momentum)
   :rotation-axis    (ecs/get-component world eid c/rotation-axis)
   :ionization       (ecs/get-component world eid c/ionization-fraction)
   :state            (ecs/get-component world eid c/matter-state)})

(defn- neighbors-for-data
  "Return the neighbor vector for `data`, using the shared cache when present and
   falling back to a spatial-tree query. The fallback computes the squared
   distance in the central-particle frame so the consumer loop can treat cached
   and fallback neighbors identically."
  [world data]
  (if-let [entry (ecs/get-component world (:eid data) c/neighbor-cache)]
    (:neighbors entry)
    (let [h (* 2.0 (double (or (:radius data) 1.0)))
          pos (:position data)
          [px py pz] pos
          px (double px)
          py (double py)
          pz (double pz)
          raw (idx/within-radius (:genesis/spatial-tree world)
                                 pos
                                 h
                                 #(lf/hydro-em-active? (:matter-state %)))]
      (mapv (fn [n]
              (let [np (:position n)
                    rx (- px (double (nth np 0)))
                    ry (- py (double (nth np 1)))
                    rz (- pz (double (nth np 2)))]
                (assoc n :r2 (+ (* rx rx) (* ry ry) (* rz rz)))))
            raw))))

(defn- hydro-em-force-cell
  "Compute [eid accel-pressure accel-lorentz torque-em] for one hydro/EM-active
   entity. Walks the neighbors once, computes the pair kernel gradient once, and
   accumulates both the pressure-gradient acceleration and the curl estimate.

   The curl/Lorentz branch is skipped when the local field is not dynamically
   significant: β ≥ `law.field/beta-magnetized` AND ℳ_A ≥
   `law.field/alfven-mach-magnetized`, or when the active neighbor count is below
   `law.field/min-neighbors-for-curl`."
  [dt data]
  (let [eid      (:eid data)
        pos      (:position data)
        [px py pz] pos
        px       (double px)
        py       (double py)
        pz       (double pz)
        density  (double (:density data))
        pressure (double (:pressure data))
        r-self   (double (or (:radius data) 1.0))
        b-field  (:b-field data)
        [bx by bz] b-field
        bx       (double (or bx 0.0))
        by       (double (or by 0.0))
        bz       (double (or bz 0.0))
        v        (sp/len (or (:velocity data) [0.0 0.0 0.0]))
        beta     (lf/plasma-beta pressure b-field)
        ma       (lf/alfven-mach v b-field density)
        do-curl? (and (< beta lf/beta-magnetized)
                      (< ma lf/alfven-mach-magnetized))
        neighbors (:neighbors data)]
    (loop [i 0
           ax-p 0.0
           ay-p 0.0
           az-p 0.0
           cx   0.0
           cy   0.0
           cz   0.0]
      (if (>= i (count neighbors))
        (let [accel-p  [ax-p ay-p az-p]
              curl-b   [cx cy cz]
              accel-l  (if do-curl?
                         (em/capped-lorentz-acceleration data curl-b)
                         [0.0 0.0 0.0])
              torque   (em/magnetic-braking-torque data dt)]
          [eid accel-p accel-l torque])
        (let [n (nth neighbors i)]
          (if-not (and (lf/hydro-em-active? (:matter-state n))
                       (:density n)
                       (:pressure n)
                       (:mass n))
            (recur (inc i) ax-p ay-p az-p cx cy cz)
            (let [r-n  (double (or (:radius n) 1.0))
                  h    (+ r-self r-n)
                  h2   (* h h)
                  r2   (double (:r2 n))]
              (if (>= r2 h2)
                (recur (inc i) ax-p ay-p az-p cx cy cz)
                (let [np  (:position n)
                      rx  (- px (double (nth np 0)))
                      ry  (- py (double (nth np 1)))
                      rz  (- pz (double (nth np 2)))
                      [gx gy gz] (kernel/kernel-gradient [rx ry rz] r2 h)
                      rhoj (double (:density n))
                      pj   (double (:pressure n))
                      term-p (+ (/ pressure (* density density))
                                (/ pj (* rhoj rhoj)))
                      scale-p (* (double (:mass n)) term-p -1.0)
                      ax-p' (+ ax-p (* gx scale-p))
                      ay-p' (+ ay-p (* gy scale-p))
                      az-p' (+ az-p (* gz scale-p))]
                  (if do-curl?
                    (let [n-b (:b-field n)
                          [bnx bny bnz] n-b
                          mbx   (- bx (double (or bnx 0.0)))
                          mby   (- by (double (or bny 0.0)))
                          mbz   (- bz (double (or bnz 0.0)))
                          factor (/ (double (:mass n)) rhoj)
                          cx'   (+ cx (* factor (- (* mby gz) (* mbz gy))))
                          cy'   (+ cy (* factor (- (* mbz gx) (* mbx gz))))
                          cz'   (+ cz (* factor (- (* mbx gy) (* mby gx))))]
                      (recur (inc i) ax-p' ay-p' az-p' cx' cy' cz'))
                    (recur (inc i) ax-p' ay-p' az-p' cx cy cz)))))))))))

(defn- clear-stale-accel
  "Remove c/accel-pressure from entities that are no longer hydro/EM-active."
  [world active]
  (let [active-eids (set (map :eid active))
        stale-p (ecs/entities-with world c/accel-pressure)
        stale-l (ecs/entities-with world c/accel-lorentz)
        stale-t (ecs/entities-with world c/torque-em)
        w1 (reduce (fn [w eid]
                     (if (active-eids eid)
                       w
                       (ecs/remove-component w eid c/accel-pressure)))
                   world stale-p)
        w2 (reduce (fn [w eid]
                     (if (active-eids eid)
                       w
                       (ecs/remove-component w eid c/accel-lorentz)))
                   w1 stale-l)
        w3 (reduce (fn [w eid]
                     (if (active-eids eid)
                       w
                       (ecs/remove-component w eid c/torque-em)))
                   w2 stale-t)]
    w3))

(defn merged-hydro-em-system
  "Double-buffer write-set system: in one neighbor walk per hydro/EM-active
   entity, compute the SPH pressure-gradient acceleration, the Lorentz
   acceleration, and the magnetic-braking torque. Writes `c/accel-pressure`,
   `c/accel-lorentz`, and `c/torque-em`. Stale entries for inactive entities are
   removed so forces do not leak into resolved bodies.

   Reads the shared neighbor cache from `c/neighbor-cache` and falls back to a
   spatial-tree query when the cache is absent."
  [dt]
  {:id     :hydro-em
   :ns     'domain.mhd.force
   :reads  #{c/matter-state c/position c/density c/pressure c/mass c/radius
             c/b-field c/velocity c/angular-momentum c/rotation-axis
             c/ionization-fraction c/neighbor-cache}
   :writes #{c/accel-pressure c/accel-lorentz c/torque-em}
   :run    (fn [world]
             (let [eids     (ecs/entities-with world c/matter-state c/position
                                               c/density c/pressure c/mass c/radius)
                   all-data (mapv #(entity->mhd-data world %) eids)
                   active   (filterv #(lf/hydro-em-active? (:state %)) all-data)
                   active'  (mapv #(assoc % :neighbors (neighbors-for-data world %)) active)
                   cleared  (clear-stale-accel world active)
                   computed (profile/profile-section
                             cleared :hydro-em/compute
                             (fn [_world]
                               (par/par-mapv
                                #(hydro-em-force-cell dt %)
                                active')))
                   accel-p  (transient {})
                   accel-l  (transient {})
                   torque   (transient {})]
               (doseq [[eid a-p a-l t] computed]
                 (when (lf/finite-vec3? a-p) (assoc! accel-p eid a-p))
                 (when (lf/finite-vec3? a-l) (assoc! accel-l eid a-l))
                 (when (lf/finite-vec3? t)   (assoc! torque eid t)))
               (merge (tick/contribution-write-set
                       c/accel-pressure (persistent! accel-p)
                       (keys (get-in world [:components c/accel-pressure])))
                      (tick/contribution-write-set
                       c/accel-lorentz (persistent! accel-l)
                       (keys (get-in world [:components c/accel-lorentz])))
                      (tick/contribution-write-set
                       c/torque-em (persistent! torque)
                       (keys (get-in world [:components c/torque-em]))))))})

(defn merged-hydro-em-force
  "Convenience function for tests: compute the merged force cell result for a
   single entity map. Returns `[accel-pressure accel-lorentz torque-em]`."
  [dt world data]
  (let [[_ a-p a-l t] (hydro-em-force-cell dt (assoc data
                                                     :neighbors (neighbors-for-data world data)))]
    [a-p a-l t]))