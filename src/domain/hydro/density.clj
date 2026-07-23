(ns domain.hydro.density
  "SPH density pass and adaptive smoothing length."
  (:require
   [law.field :as lf]
   [law.stellar :as ls]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.parallel :as par]
   [domain.hydro.common :as common]
   [domain.hydro.kernel :as kernel]
   [domain.spatial.index :as idx]))

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
         (if (or (>= r2 h2) (not (:mass n)))
           rho
           (+ rho (* (double (:mass n)) (kernel/kernel-r2 r2 h))))))
     0.0
     neighbors)))

(defn sph-density-from-cache
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
                          (lf/hydro-em-active? (:matter-state n))
                          (:mass n))
                   (+ (double rho) (* (double (:mass n)) (kernel/kernel-r2 r2 h)))
                   rho)))
            0.0
            neighbors)))

(defn smoothing-length-from-dist
  "Geometric SPH smoothing length for parcel `data` given its nearest-neighbour
   distance `d`: h = factor · d, floored at `sph-h-min`. Falls back to the
   parcel's own 2·radius when `d` is infinite (isolated parcel)."
  [data d]
  (if (Double/isInfinite (double d))
    (* 2.0 (double (or (:radius data) common/sph-h-min)))
    (max common/sph-h-min (* common/sph-h-factor (double d)))))

(defn smoothing-length
  "Geometric SPH smoothing length for parcel `data`: h = factor · d_nn,
   floored at `sph-h-min`. Falls back to the parcel's own 2·radius if isolated.
   Uses the world's Barnes–Hut tree for the nearest-neighbour distance."
  [data world]
  (smoothing-length-from-dist data (idx/query-nearest-dist world (:position data) (:eid data))))

(defn- density-neighbors
  "Return neighbors for `data` within smoothing length squared `hh2`, using the
   cache when present."
  [world data h]
  (let [hh2 (* h h)]
    (if-let [entry (ecs/get-component world (:eid data) c/neighbor-cache)]
      (filterv #(and (common/hydro-active? (:matter-state %))
                     (<= (double (:r2 %)) hh2))
               (:neighbors entry))
      (idx/within-radius (:genesis/spatial-tree world) (:position data) h
                         #(common/hydro-active? (:matter-state %))))))

(defn- density-update
  "Compute `[eid rho press r']` for one gas parcel."
  [world data]
  (let [entry (ecs/get-component world (:eid data) c/neighbor-cache)
        h (if entry (:h entry) (smoothing-length data world))
        nbrs (density-neighbors world data h)
        rho (sph-density (assoc data :radius (* 0.5 h)) nbrs)
        press (ls/ideal-gas-pressure rho (:temperature data))]
    [(:eid data) rho press (* 0.5 h)]))

(defn- apply-density-update
  "Apply one density update to `world` if the values are finite and positive."
  [w [eid rho press r']]
  (if (and (lf/finite-number? rho) (lf/finite-number? press)
           (pos? rho) (pos? press) (lf/finite-number? r') (pos? r'))
    (-> w
        (ecs/put-component eid c/density rho)
        (ecs/put-component eid c/pressure press)
        (ecs/put-component eid c/radius r'))
    w))

(defn density-system
  "SPH density pass: compute ρ_i = Σ_j m_j W for every `:nebula` particle from
   the current positions, then recompute pressure and the particle's adaptive
   radius from the ideal gas law. Runs before `hydro-system` so the
   pressure-gradient force sees a real, varying field rather than the fixed seed
   density. Resolved bodies keep their existing body-density; contracting
   `:protostar` neighbors still contribute mass to the SPH sums of nearby gas
   parcels.

   Reads the shared spatial tree from :genesis/spatial-tree and filters query
   results to hydro-active neighbors (`:nebula` and `:protostar`)."
  [_dt]
  (fn [world]
    (let [eids (ecs/entities-with world c/matter-state c/position c/density
                                  c/pressure c/mass c/radius c/temperature)
          all-data (mapv #(common/entity->hydro-data world %) eids)
          gas (filterv #(= :nebula (:state %)) all-data)
          updates (par/par-mapv #(density-update world %) gas)]
      (reduce apply-density-update world updates))))

(defn gas-structure
  "The GAS branch of the Structure owner: for every diffuse `:nebula` parcel,
    the SPH density ρ_i = Σ_j m_j W at a GEOMETRIC smoothing length h = factor·d_nn
   (see `smoothing-length`). Returns `[[eid density radius] ...]` with radius = h/2.
   Pure; reuses the same SPH machinery as the legacy `density-system` (which stays
   for the sequential path). For gas, density is primary (estimated from
   neighbours) and the radius is the smoothing length it implies.

    Hot path: the shared pair walk (domain.physics.cache.neighbor) already
   summed the density into the entry's staleness-budgeted `:density-estimate`
   (law.field/density-stale-* knobs; the estimate may lag the current geometry
   within that documented budget). This fn reads it in O(1) — no second
   neighbor walk. Entries without an estimate (hand-built, legacy) fall back
   to `sph-density-from-cache`; absent a cache entry the tree-query fallback
   computes both from scratch."
  [world]
  (let [eids     (ecs/entities-with world c/matter-state c/position c/density
                                    c/pressure c/mass c/radius c/temperature)
        all-data (mapv #(common/entity->hydro-data world %) eids)
        gas      (filterv #(= :nebula (:state %)) all-data)]
    (par/par-mapv
     (fn [data]
       (if-let [entry (ecs/get-component world (:eid data) c/neighbor-cache)]
         (let [h (:h entry)]
           [(:eid data)
            (or (:density-estimate entry)
                (sph-density-from-cache (:neighbors entry) h))
            (* 0.5 h)])
         (let [h    (smoothing-length data world)
               nbrs (idx/within-radius (:genesis/spatial-tree world) (:position data) h
                                       #(common/hydro-active? (:matter-state %)))
               rho  (sph-density (assoc data :radius (* 0.5 h)) nbrs)]
           [(:eid data) rho (* 0.5 h)])))
     gas)))
