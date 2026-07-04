(ns domain.hydro
  "Hydrodynamics on the N-body clump substrate.

   The clumps are Lagrangian gas parcels; each particle is a moving sample of a
   continuous fluid field. Density is computed with the standard SPH sum
   ρ_i = Σ_j m_j W(r_ij, h), and pressure gradients are estimated with the
   antisymmetric SPH pressure-gradient formula:

       a_i = − Σ_j m_j (P_i/ρ_i² + P_j/ρ_j²) ∇_i W(r_ij, h_ij)

   The cubic-spline (M4) kernel is used; the formulation conserves linear and
   angular momentum exactly because the pairwise force is antisymmetric.
   Pure data transformation; no IO."
  (:require
   [clojure.math      :as math]
   [law.field         :as lf]
   [law.stellar       :as ls]
   [domain.ecs.core   :as ecs]
   [domain.ecs.parallel :as par]
   [domain.ecs.tick   :as tick]
   [domain.ecs.components :as c]
   [domain.profile    :as profile]
   [domain.spatial.index :as idx]))

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
        (* (/ 8.0 Math/PI) inv-h3 (cubic-spline-w q))))))

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
       (let [r   (Math/sqrt r2)
             q   (/ r hh)
             dw-dq  (cubic-spline-dw-dq q)
             inv-h  (/ 1.0 hh)
             inv-h4 (* inv-h inv-h inv-h inv-h)
             factor (* (/ 8.0 Math/PI) inv-h4 (/ dw-dq r))]
         [(* rx factor) (* ry factor) (* rz factor)])))))

(defn pressure-term
  "Symmetric SPH pressure term P_i/ρ_i² + P_j/ρ_j²."
  [density pressure other-density other-pressure]
  (if (and (pos? (double density)) (pos? (double other-density)))
    (+ (/ (double pressure) (* density density))
       (/ (double other-pressure) (* other-density other-density)))
    0.0))

(defn pressure-gradient-acceleration
  "SPH pressure-gradient acceleration for one particle. `neighbors` is a seq of
   maps with :mass :position :density :pressure :radius. The self-particle may
   be included; its contribution is zero because the kernel gradient vanishes
   at r = 0.

   The density pass uses a geometric smoothing length h = sph-h-factor · d_nn
   (see `smoothing-length` and `sph-h-factor`). The pressure-gradient pass uses
   the pair smoothing length h_ij = r_i + r_j, the sum of the two particle radii.

   If `gradients` is supplied it must be the same length as `neighbors` and
   contain the pre-computed ∇_i W(r_ij, h_ij) vectors; otherwise the gradient is
   recomputed for each neighbor."
  ([data neighbors]
   (pressure-gradient-acceleration data neighbors nil))
  ([data neighbors gradients]
   (let [[px py pz] (:position data)
         px (double px)
         py (double py)
         pz (double pz)
         density (double (:density data))
         pressure (double (:pressure data))
         r-self (double (or (:radius data) 1.0))]
     (reduce-kv
      (fn [[ax ay az] idx n]
        (let [np (:position n)
              nx (double (nth np 0))
              ny (double (nth np 1))
              nz (double (nth np 2))
              rx (- px nx)
              ry (- py ny)
              rz (- pz nz)
              h (+ r-self (double (or (:radius n) 1.0)))
              h2 (* h h)
              r2 (+ (* rx rx) (* ry ry) (* rz rz))
              [gx gy gz] (if gradients
                           (nth gradients idx)
                           (if (>= r2 h2)
                             [0.0 0.0 0.0]
                             (kernel-gradient [rx ry rz] r2 h)))
              term (pressure-term density pressure
                                  (double (:density n)) (double (:pressure n)))
              scale (* (double (:mass n)) term -1.0)]
          [(+ ax (* gx scale))
           (+ ay (* gy scale))
           (+ az (* gz scale))]))
      [0.0 0.0 0.0]
      (vec neighbors)))))

(defn sph-density
  "SPH density estimate ρ_i = Σ_j m_j W(r_ij, h). `data` is the central particle
   (needs :position, :radius, :mass); `neighbors` is a seq of neighbor maps with
   :mass and :position. The self-particle may be included; it contributes
   m_i W(0,h). Smoothing length h = 2 × particle radius."
  [data neighbors]
  (let [[px py pz] (:position data)
        px (double px)
        py (double py)
        pz (double pz)
        h (* 2.0 (double (or (:radius data) 1.0)))
        h2 (* h h)]
    (reduce
     (fn [rho n]
       (let [np (:position n)
             dx (- px (double (nth np 0)))
             dy (- py (double (nth np 1)))
             dz (- pz (double (nth np 2)))
             r2 (+ (* dx dx) (* dy dy) (* dz dz))]
         (if (>= r2 h2)
           rho
           (+ rho (* (double (:mass n)) (kernel-r2 r2 h))))))
     0.0
     neighbors)))

(defn- sph-density-from-cache
  "SPH density ρ = Σ_j m_j W(r²_j, h) computed directly from a neighbor-cache
   entry's neighbor vector: each neighbor's cached `:r2` (same arithmetic as a
   fresh query) is reused and non-hydro-active / out-of-kernel neighbors are
   skipped inline, so no filtered vector is allocated and no distance is
   recomputed. Result is identical to filtering the entry and calling
   `sph-density`."
  [neighbors h]
  (let [h   (double h)
        hh2 (* h h)]
    (reduce (fn [rho n]
              (let [r2 (double (:r2 n))]
                (if (and (< r2 hh2)
                         (lf/hydro-em-active? (:matter-state n)))
                  (+ (double rho) (* (double (:mass n)) (kernel-r2 r2 h)))
                  rho)))
            0.0
            neighbors)))

(defn- pressure-gradient-acceleration-from-cache
  "SPH pressure-gradient acceleration computed directly from a neighbor-cache
   entry's neighbor vector, using each neighbor's cached `:r2` (inline range /
   state filter) and precomputed `:gradient-pressure`. Identical to filtering
   the entry and calling `pressure-gradient-acceleration` with the matching
   gradients, without allocating the filtered neighbor and gradient vectors."
  [data neighbors hh2]
  (let [density  (double (:density data))
        pressure (double (:pressure data))
        hh2      (double hh2)]
    (reduce
     (fn [[ax ay az :as acc] n]
       (if-not (and (<= (double (:r2 n)) hh2)
                    (lf/hydro-em-active? (:matter-state n)))
         acc
         (let [[gx gy gz] (:gradient-pressure n)
               term  (pressure-term density pressure
                                    (double (:density n)) (double (:pressure n)))
               scale (* (double (:mass n)) term -1.0)]
           [(+ (double ax) (* (double gx) scale))
            (+ (double ay) (* (double gy) scale))
            (+ (double az) (* (double gz) scale))])))
     [0.0 0.0 0.0]
     neighbors)))

(defn- cache-neighbors-and-gradients
  "Return [neighbors gradients] for `data` using the transient neighbor cache
   when present, otherwise query the spatial index. `radius-fn` produces the
   query radius from the particle data; `state-pred` filters neighbors by matter
   state. The returned `gradients` is nil when the cache is not used."
  [world data radius-fn state-pred gradient-key]
  (let [h (double (radius-fn data))]
    (if-let [entry (get-in world [:genesis/neighbor-cache (:eid data)])]
      (let [pos (:position data)
            hh2 (* h h)
            nbrs (filterv #(and (state-pred (:matter-state %))
                                (<= (double (:r2 %)) hh2))
                          (:neighbors entry))
            grads (mapv gradient-key nbrs)]
        [nbrs grads])
      [(idx/within-radius (:genesis/spatial-tree world) (:position data) h
                          #(state-pred (:matter-state %))) nil])))

(defn- entity->hydro-data
  "Project an ECS entity into the map the SPH functions expect."
  [world eid]
  {:eid         eid
   :position    (ecs/get-component world eid c/position)
   :velocity    (ecs/get-component world eid c/velocity)
   :mass        (ecs/get-component world eid c/mass)
   :radius      (ecs/get-component world eid c/radius)
   :density     (ecs/get-component world eid c/density)
   :pressure    (ecs/get-component world eid c/pressure)
   :temperature (ecs/get-component world eid c/temperature)
   :state       (ecs/get-component world eid c/matter-state)})

(defn- hydro-active?
  "Pressure-gradient dynamics matter for diffuse and contracting gas, not for
   solid debris or fusion-supported stars."
  [state]
  (lf/hydro-em-active? state))

(defn hydro-system
  "Compute the pressure-gradient acceleration a = −∇p/ρ for every hydro-active
   clump and store it on `c/hydro-accel`. This acceleration is consumed by
   `domain.orbital.system` during the same tick.

   Any entity that currently carries `c/hydro-accel` but is no longer
   hydro-active (e.g. a merged clump that became :debris or :planet) has its
   acceleration removed, so stale pressure forces do not leak into resolved
   bodies."
  [_dt]

  (fn [world]
    (let [eids     (ecs/entities-with world c/matter-state c/position c/density
                                      c/pressure c/mass c/radius)
          all-data (mapv #(entity->hydro-data world %) eids)
          active   (filterv #(hydro-active? (:state %)) all-data)
          stale    (ecs/entities-with world c/hydro-accel)
          cleared  (reduce (fn [w eid]
                             (if (some #(= eid (:eid %)) active)
                               w
                               (ecs/remove-component w eid c/hydro-accel)))
                           world
                           stale)
          updates  (par/par-mapv
                    (fn [data]
                      (let [radius-fn #(* 2.0 (double (or (:radius %) 1.0)))
                            [nbrs grads] (cache-neighbors-and-gradients
                                          world data radius-fn hydro-active?
                                          :gradient-pressure)]
                        [(:eid data)
                         (pressure-gradient-acceleration data nbrs grads)]))
                    active)]
      (reduce (fn [w [eid a]]
                (if (lf/finite-vec3? a)
                  (ecs/put-component w eid c/hydro-accel a)
                  w))
              cleared
              updates))))

(defn pressure-acceleration
  "Double-buffer write-set system: SPH pressure-gradient acceleration a = −∇p/ρ
   for every hydro-active clump → `accel.pressure`. Reads the shared spatial tree
   from :genesis/spatial-tree (built once per tick by domain.spatial.index),
   filters query results to hydro-active neighbors (`:nebula` and `:protostar`).
   Writes ONLY accel.pressure."
  []
  {:id     :hydro
   :writes #{c/accel-pressure}
   :run    (fn [world]
             (let [eids     (ecs/entities-with world c/matter-state c/position
                                               c/density c/pressure c/mass c/radius)
                   all-data (mapv #(entity->hydro-data world %) eids)
                   active   (filterv #(hydro-active? (:state %)) all-data)
                   computed (profile/profile-section
                             world :hydro/compute
                             (fn [_world]
                               (par/par-mapv
                                (fn [data]
                                  (let [h (* 2.0 (double (or (:radius data) 1.0)))]
                                    (if-let [entry (get-in world [:genesis/neighbor-cache (:eid data)])]
                                      [(:eid data)
                                       (pressure-gradient-acceleration-from-cache
                                        data (:neighbors entry) (* h h))]
                                      (let [nbrs (idx/within-radius
                                                  (:genesis/spatial-tree world) (:position data) h
                                                  #(hydro-active? (:matter-state %)))]
                                        [(:eid data)
                                         (pressure-gradient-acceleration data nbrs nil)]))))
                                active)))
                   cell     (reduce (fn [m [eid a]]
                                      (if (lf/finite-vec3? a) (assoc m eid a) m))
                                    {} computed)]
               (tick/contribution-write-set
                c/accel-pressure cell
                (keys (get-in world [:components c/accel-pressure])))))})

(def ^:const sph-h-factor
  "SPH smoothing length as a multiple of a parcel's distance to its NEAREST
   neighbour: h = sph-h-factor · d_nn. The smoothing length is GEOMETRIC (set by
   neighbour spacing, not by density), so it is unconditionally stable — there is
   no ρ→h→ρ feedback and hence no h→0 / ρ→∞ runaway (the bug a density-based
   adaptive radius caused). It is also responsive: as the cloud collapses, d_nn
   shrinks, h shrinks, and the SPH density rises ∝ 1/h³ — so a real collapse (not
   an iteration artifact) carries dense regions across the condensation gate. The
   small factor keeps a diffuse parcel's self-density a few× below the gate, so
   condensation needs a genuine ~1.5–2× local compression." 0.013)

(def ^:const sph-h-min
  "Absolute floor on the smoothing length (m), a final guard so a coincident pair
   cannot produce an infinite density." 1.0e9)

(defn smoothing-length-from-dist
  "Geometric SPH smoothing length for parcel `data` given its nearest-neighbour
   distance `d`: h = factor · d, floored at `sph-h-min`. Falls back to the
   parcel's own 2·radius when `d` is infinite (isolated parcel)."
  [data d]
  (if (Double/isInfinite (double d))
    (* 2.0 (double (or (:radius data) sph-h-min)))
    (max sph-h-min (* sph-h-factor (double d)))))

(defn smoothing-length
  "Geometric SPH smoothing length for parcel `data`: h = factor · d_nn,
   floored at `sph-h-min`. Falls back to the parcel's own 2·radius if isolated.
   Uses the world's Barnes–Hut tree for the nearest-neighbour distance."
  [data world]
  (smoothing-length-from-dist data (idx/query-nearest-dist world (:position data) (:eid data))))

(defn density-system
  "SPH density pass: compute ρ_i = Σ_j m_j W for every `:nebula` particle from
   the current positions, then recompute pressure and the particle's adaptive
   radius from the ideal gas law. Runs before `hydro-system` so the
   pressure-gradient force sees a real, varying field rather than the fixed seed
   density. Resolved bodies (`:debris`, `:planet`, `:star`) keep their existing
   body-density; contracting `:protostar` neighbors still contribute mass to the
   SPH sums of nearby gas parcels.

   Reads the shared spatial tree from :genesis/spatial-tree and filters query
   results to hydro-active neighbors (`:nebula` and `:protostar`)."
  [_dt]
  (fn [world]
    (let [eids     (ecs/entities-with world c/matter-state c/position c/density
                                      c/pressure c/mass c/radius c/temperature)
          all-data (mapv #(entity->hydro-data world %) eids)
          gas      (filterv #(= :nebula (:state %)) all-data)
          updates  (par/par-mapv
                    (fn [data]
                      (let [entry (get-in world [:genesis/neighbor-cache (:eid data)])
                            h     (if entry
                                    (:h entry)
                                    (smoothing-length data world))
                            hh2   (* h h)
                            nbrs  (if entry
                                    (filterv #(and (hydro-active? (:matter-state %))
                                                   (<= (double (:r2 %)) hh2))
                                             (:neighbors entry))
                                    (idx/within-radius (:genesis/spatial-tree world) (:position data) h
                                                       #(hydro-active? (:matter-state %))))
                            rho   (sph-density (assoc data :radius (* 0.5 h)) nbrs)
                            press (ls/ideal-gas-pressure rho (:temperature data))]
                        [(:eid data) rho press (* 0.5 h)]))
                    gas)]
      (reduce (fn [w [eid rho press r']]
                (if (and (lf/finite-number? rho) (lf/finite-number? press)
                         (pos? rho) (pos? press) (lf/finite-number? r') (pos? r'))
                  (-> w
                      (ecs/put-component eid c/density rho)
                      (ecs/put-component eid c/pressure press)
                      (ecs/put-component eid c/radius r'))
                  w))
              world
              updates))))

(defn gas-structure
  "The GAS branch of the Structure owner: for every diffuse `:nebula` parcel,
   the SPH density ρ_i = Σ_j m_j W at a GEOMETRIC smoothing length h = factor·d_nn
   (see `smoothing-length`). Returns `[[eid density radius] ...]` with radius = h/2.
   Pure; reuses the same SPH machinery as the legacy `density-system` (which stays
   for the sequential path). For gas, density is primary (estimated from
   neighbours) and the radius is the smoothing length it implies.

   Reads the shared spatial tree from :genesis/spatial-tree and filters query
   results to hydro-active neighbors (`:nebula` and `:protostar`)."
  [world]
  (let [eids     (ecs/entities-with world c/matter-state c/position c/density
                                    c/pressure c/mass c/radius c/temperature)
        all-data (mapv #(entity->hydro-data world %) eids)
        gas      (filterv #(= :nebula (:state %)) all-data)]
    (par/par-mapv
     (fn [data]
       (if-let [entry (get-in world [:genesis/neighbor-cache (:eid data)])]
         (let [h (:h entry)]
           [(:eid data) (sph-density-from-cache (:neighbors entry) h) (* 0.5 h)])
         (let [h    (smoothing-length data world)
               nbrs (idx/within-radius (:genesis/spatial-tree world) (:position data) h
                                       #(hydro-active? (:matter-state %)))
               rho  (sph-density (assoc data :radius (* 0.5 h)) nbrs)]
           [(:eid data) rho (* 0.5 h)])))
     gas)))

(defn sound-speed
  "Adiabatic sound speed c_s = √(γ P / ρ) for an ideal gas. m/s."
  [density pressure]
  (if (and (pos? (double density)) (pos? (double pressure)))
    (Math/sqrt (/ (* lf/gamma (double pressure)) (double density)))
    0.0))
