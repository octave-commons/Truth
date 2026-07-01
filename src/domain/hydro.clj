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
   [law.field         :as lf]
   [law.stellar       :as ls]
   [domain.ecs.core   :as ecs]
   [domain.ecs.parallel :as par]
   [domain.ecs.tick   :as tick]
   [domain.ecs.components :as c]
   [domain.spatial.index :as idx]
   [shape.spatial     :as sp]))

(defn cubic-spline-dw-dq
  "Derivative dW/dq of the cubic spline (M4) kernel in 3D, dimensionless."
  [q]
  (cond
    (< (double q) 0.0)     0.0
    (<= (double q) 0.5)    (+ (* -12.0 (double q)) (* 18.0 (double q) (double q)))
    (<= (double q) 1.0)    (* -6.0 (Math/pow (- 1.0 (double q)) 2))
    :else                   0.0))

(defn cubic-spline-w
  "Dimensionless cubic spline (M4) kernel W(q). The 3D normalization factor
   8/(π h³) is applied separately in `kernel`."
  [q]
  (cond
    (< (double q) 0.0)  0.0
    (<= (double q) 0.5) (+ 1.0 (* -6.0 (double q) (double q)) (* 6.0 (double q) (double q) (double q)))
    (<= (double q) 1.0) (* 2.0 (Math/pow (- 1.0 (double q)) 3))
    :else               0.0))

(defn kernel
  "Cubic-spline SPH kernel W(r,h) in 3D. Units 1/volume; zero outside r > h
   and at h = 0. Integrates to 1 over a sphere of radius h."
  [r h]
  (let [r  (double r)
        hh (double h)]
    (if (or (zero? hh) (> r hh))
      0.0
      (* (/ 8.0 (* Math/PI (Math/pow hh 3)))
         (cubic-spline-w (/ r hh))))))

(defn kernel-gradient
  "Gradient ∇_i W(r_ij, h) of the cubic spline kernel. `r-ij` is the vector from
   particle j to particle i. The result points from j toward i and has units
   of 1/length⁴. Returns zero for r = 0 or r > h."
  [r-ij h]
  (let [r (sp/len r-ij)
        hh (double h)]
    (if (or (zero? r) (zero? hh))
      [0.0 0.0 0.0]
      (let [q (/ r hh)]
        (if (> q 1.0)
          [0.0 0.0 0.0]
          (let [dw-dq (cubic-spline-dw-dq q)
                ;; ∇W = (8/(π h⁴)) (dW/dq) (r_ij / r)
                factor (/ (* 8.0 dw-dq) (* Math/PI (Math/pow hh 4) r))]
            (sp/v* r-ij factor)))))))

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

   Pair smoothing length h_ij = (h_i + h_j)/2 = r_i + r_j, consistent with the
   per-particle smoothing length h_i = 2 r_i used in the density pass."
  [data neighbors]
  (let [{:keys [position density pressure]} data]
    (reduce
     (fn [acc n]
       (let [r-ij   (sp/v- position (:position n))
             h      (+ (or (:radius data) 1.0) (or (:radius n) 1.0))
             grad   (kernel-gradient r-ij h)
             term   (pressure-term density pressure
                                   (:density n) (:pressure n))
             contrib (sp/v* grad (* (double (:mass n)) term -1.0))]
         (sp/v+ acc contrib)))
     [0.0 0.0 0.0]
     neighbors)))

(defn sph-density
  "SPH density estimate ρ_i = Σ_j m_j W(r_ij, h). `data` is the central particle
   (needs :position, :radius, :mass); `neighbors` is a seq of neighbor maps with
   :mass and :position. The self-particle may be included; it contributes
   m_i W(0,h). Smoothing length h = 2 × particle radius."
  [data neighbors]
  (let [{:keys [position radius]} data
        h (* 2.0 (double (or radius 1.0)))]
    (reduce
     (fn [rho n]
       (let [r (sp/len (sp/v- position (:position n)))
             w (kernel r h)]
         (+ rho (* (double (:mass n)) w))))
     0.0
     neighbors)))

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
  (contains? #{:nebula :protostar} state))

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
          tree     (idx/build active)
          updates  (par/par-mapv
                    (fn [data]
                      (let [h        (* 2.0 (double (or (:radius data) 1.0)))
                            nbrs     (idx/within-radius tree (:position data) h)]
                        [(:eid data)
                         (pressure-gradient-acceleration data nbrs)]))
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
   from :phase0/spatial-tree (built once per tick by domain.spatial.index),
   filters query results to :nebula particles only. Writes ONLY accel.pressure."
  []
  {:id     :hydro
   :writes #{c/accel-pressure}
   :run    (fn [world]
             (let [eids     (ecs/entities-with world c/matter-state c/position
                                               c/density c/pressure c/mass c/radius)
                   all-data (mapv #(entity->hydro-data world %) eids)
                   active   (filterv #(hydro-active? (:state %)) all-data)
                   tree     (:phase0/spatial-tree world)
                   computed (par/par-mapv
                              (fn [data]
                                (let [h    (* 2.0 (double (or (:radius data) 1.0)))
                                      nbrs (->> (idx/within-radius tree (:position data) h)
                                                (filter #(= :nebula (:matter-state %))))]
                                  [(:eid data) (pressure-gradient-acceleration data nbrs)]))
                              active)
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

(defn- smoothing-length
  "Geometric SPH smoothing length for parcel `data` among `gas`: h = factor · d_nn,
   floored at `sph-h-min`. Falls back to the parcel's own 2·radius if isolated.
   `tree` is the neighbour index over `gas` (see `domain.spatial.index`); the
   nearest-neighbour distance comes from an octree branch-and-bound rather than a
   linear scan over `gas`."
  [data tree]
  (let [d     (idx/nearest-dist tree (:position data) (:eid data))
        r-own (* 2.0 (double (or (:radius data) sph-h-min)))]
    (if (Double/isInfinite d)
      r-own
      (max sph-h-min (* sph-h-factor d)))))

(defn density-system
  "SPH density pass: compute ρ_i = Σ_j m_j W for every `:nebula` particle from
   the current positions, then recompute pressure and the particle's adaptive
   radius from the ideal gas law. Runs before `hydro-system` so the
   pressure-gradient force sees a real, varying field rather than the fixed seed
   density. Resolved bodies (`:debris`, `:planet`, `:protostar`, `:star`) keep
   their existing body-density; they are not samples of the diffuse gas field.

   Reads the shared spatial tree from :phase0/spatial-tree and filters query
   results to :nebula particles only."
  [_dt]
  (fn [world]
    (let [eids     (ecs/entities-with world c/matter-state c/position c/density
                                      c/pressure c/mass c/radius c/temperature)
          all-data (mapv #(entity->hydro-data world %) eids)
          gas      (filterv #(= :nebula (:state %)) all-data)
          tree     (:phase0/spatial-tree world)
          updates  (par/par-mapv
                    (fn [data]
                      (let [h     (smoothing-length data tree)
                            nbrs  (->> (idx/within-radius tree (:position data) h)
                                       (filter #(= :nebula (:matter-state %))))
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

   Reads the shared spatial tree from :phase0/spatial-tree and filters query
   results to :nebula particles only."
  [world]
  (let [eids     (ecs/entities-with world c/matter-state c/position c/density
                                    c/pressure c/mass c/radius c/temperature)
        all-data (mapv #(entity->hydro-data world %) eids)
        gas      (filterv #(= :nebula (:state %)) all-data)
        tree     (:phase0/spatial-tree world)]
    (par/par-mapv
      (fn [data]
        (let [h    (smoothing-length data tree)
              nbrs (->> (idx/within-radius tree (:position data) h)
                        (filter #(= :nebula (:matter-state %))))
              rho  (sph-density (assoc data :radius (* 0.5 h)) nbrs)]
          [(:eid data) rho (* 0.5 h)]))
      gas)))

(defn sound-speed
  "Adiabatic sound speed c_s = √(γ P / ρ) for an ideal gas. m/s."
  [density pressure]
  (if (and (pos? (double density)) (pos? (double pressure)))
    (Math/sqrt (/ (* lf/gamma (double pressure)) (double density)))
    0.0))
