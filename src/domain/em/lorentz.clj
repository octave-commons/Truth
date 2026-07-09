(ns domain.em.lorentz
  "Lorentz force, magnetic braking, and curl estimates for Phase 0.

   The substrate is N-body: each resolved clump carries a single magnetic field
   vector (component `c/b-field`). The functions here are the per-body reductions
   of the Lorentz force and magnetic-braking torque.

   - Lorentz force      : (∇ × B) × B / μ₀, applied to velocities via the
                          orbital integrator (a = f/ρ).
   - magnetic braking   : poloidal field threading a rotating clump exerts a
                          torque that transports angular momentum outward.
   - curl estimate      : SPH-like estimate of (∇ × B) from neighbor b-fields.

   All formulas are SI (see law.field). Pure data transformation; no IO."
  (:require
   [clojure.math :as math]
   [law.field :as lf]
   [domain.hydro :as hydro]
   [domain.ecs.core :as ecs]
   [domain.ecs.parallel :as par]
   [domain.ecs.tick :as tick]
   [domain.ecs.components :as c]
   [domain.profile :as profile]
   [domain.spatial.index :as idx]
   [domain.em.field :as field]
   [shape.spatial :as sp]))

;; --- Pure curl / Lorentz physics -------------------------------------------

(defn- curl-neighbor-contribution
  "Single neighbor contribution to the SPH curl estimate. `state` is the central
   particle state map; `n` is the neighbor map with :b-field, :mass, :density,
   :position and :radius."
  [state n]
  (let [{[px py pz] :position
         [bx by bz] :b-field
         r-c        :radius} state
        np (:position n)
        nx (double (nth np 0))
        ny (double (nth np 1))
        nz (double (nth np 2))
        rx (- px nx)
        ry (- py ny)
        rz (- pz nz)
        r2 (+ (* rx rx) (* ry ry) (* rz rz))
        h  (* 0.5 (+ (double (or r-c 1.0)) (double (or (:radius n) 1.0))))
        [gx gy gz] (if (>= r2 (* h h))
                     [0.0 0.0 0.0]
                     (hydro/kernel-gradient [rx ry rz] r2 h))
        [bnx bny bnz] (:b-field n)
        dbx (- bx (double bnx))
        dby (- by (double bny))
        dbz (- bz (double bnz))
        cxj (- (* dby gz) (* dbz gy))
        cyj (- (* dbz gx) (* dbx gz))
        czj (- (* dbx gy) (* dby gx))
        factor (/ (double (:mass n 1.0))
                  (double (:density n 1.0)))]
    [(* cxj factor) (* cyj factor) (* czj factor)]))

(defn curl-estimate
  "Estimate (∇ × B) at a clump from neighboring b-field vectors using an SPH-like
   curl formula. Returns a vector in T/m. Zero neighbors → zero curl.

   `state` is a map with :b-field, :density, :position, :radius and :neighbors.
   Optional :gradients aligns with :neighbors and contains pre-computed
   ∇_i W vectors. Uses the symmetric SPH curl:
   (∇ × B)_i = Σ_j m_j/ρ_j (B_i - B_j) × ∇_i W_ij."
  [{:keys [b-field density position neighbors gradients] :as state}]
  (if (or (not (lf/finite-vec3? b-field))
          (not (pos? (double density))))
    [0.0 0.0 0.0]
    (let [state (assoc state
                       :density (double density)
                       :position (mapv double position)
                       :b-field (mapv double b-field))]
      (reduce-kv
       (fn [acc idx n]
         (if-not (lf/finite-vec3? (:b-field n))
           acc
           (let [grad (when gradients (nth gradients idx))
                 n' (if grad (assoc n :gradient-curl grad) n)]
             (mapv + acc (curl-neighbor-contribution state n')))))
       [0.0 0.0 0.0]
       (vec neighbors)))))

(defn lorentz-force-density
  "Lorentz force density f = (∇ × B) × B / μ₀  (SI). N/m³. Always perpendicular
   to B. Uses the SPH curl estimate above on the N-body substrate."
  [b-field curl-b]
  (if (and (lf/finite-vec3? b-field) (lf/finite-vec3? curl-b))
    (let [cross (sp/cross curl-b b-field)]
      (sp/v* cross (/ 1.0 lf/mu-0)))
    [0.0 0.0 0.0]))

(defn lorentz-acceleration
  "Lorentz acceleration a = f/ρ = (∇ × B) × B / (μ₀ ρ)  (SI). m/s²."
  [b-field curl-b density]
  (if (pos? (double density))
    (sp/v* (lorentz-force-density b-field curl-b) (/ 1.0 (double density)))
    [0.0 0.0 0.0]))

(defn magnetic-torque
  "Torque density τ = r × f about the origin, where f is the Lorentz force
   density. N/m."
  [position lorentz-force]
  (sp/cross position lorentz-force))

(def ^:const braking-fraction-per-time
  "Cap on magnetic-braking angular-momentum loss, as a fraction of L removed per
   second of SIM-TIME (so the per-step cap is this × dt). ~1/τ_brake with a
   braking timescale τ_brake ≈ 1e14 s (free-fall scale of a molecular cloud);
   gentle enough that the cloud's spin survives the collapse rather than being
   braked away in the first seconds of real time." 1.0e-14)

(defn magnetic-braking-torque
  "Compute the magnetic braking torque on a rotating clump: τ along the rotation
   axis. The field is assumed to be primarily poloidal (threading the rotation
   axis); differential rotation wraps it into a toroidal component whose tension
   brakes the spin. This is a phenomenological per-body reduction of the full
   MHD braking torque.

   Returns the angular momentum REMOVED this step (a vector aligned with
   `rotation-axis`), proportional to B² ρ^(-1/2) r³ ω · dt — the characteristic
   Alfvén-wave torque integrated over the timestep `dt`. Pacing by sim-time (× dt)
   rather than a per-tick fraction is essential now the tick rate is a fixed
   60 Hz: a per-tick cap would shed angular momentum ~38× faster than the old
   variable cadence, braking the cloud's rotation away in seconds."
  [{:keys [mass radius density b-field angular-momentum rotation-axis ionization]} dt]
  (if (and (pos? (double mass))
           (pos? (double radius))
           (pos? (double density))
           (lf/finite-vec3? b-field)
           (lf/finite-vec3? angular-momentum))
    (let [B2   (sp/len2 b-field)
          omega (if (and rotation-axis (lf/finite-vec3? rotation-axis))
                  (sp/dot angular-momentum rotation-axis)
                  (sp/len angular-momentum))
          ion  (double (or ionization 1.0))
          base (* B2 (math/pow (double radius) 3) (Math/abs omega) ion)
          denom (* lf/mu-0 (math/sqrt (double density)))
          tau-raw  (if (pos? denom) (/ base denom) 0.0)
          dL-raw   (* tau-raw (double dt))
          L-mag    (sp/len angular-momentum)
          dL-max   (* braking-fraction-per-time L-mag (double dt))
          dL       (min dL-raw dL-max)
          axis (or rotation-axis [0.0 0.0 1.0])
          sign (- (if (pos? omega) 1.0 -1.0))]
      (sp/v* axis (* sign dL)))
    [0.0 0.0 0.0]))

(defn capped-lorentz-acceleration
  "Lorentz acceleration a = (∇×B)×B/(μ₀ρ), computed only when magnetic pressure
   or tension is locally significant (see `law.field/mhd-regime?`). The magnitude
   is capped at the Alfvén limit v_A² / R so the force cannot accelerate a parcel
   past the characteristic magnetic scale in one step."
  [data curl-b]
  (let [b       (:b-field data)
        rho     (:density data)
        r       (double (or (:radius data) 1.0))
        v       (sp/len (or (:velocity data) [0.0 0.0 0.0]))
        ion     (double (or (:ionization data) 1.0))]
    (if (lf/mhd-regime? (:pressure data) b v rho)
      (let [a   (sp/v* (lorentz-acceleration b curl-b rho) ion)
            cap (lf/lorentz-acceleration-cap b rho r)]
        (if (pos? cap)
          (let [mag (sp/len a)]
            (if (> mag cap)
              (sp/v* a (/ cap mag))
              a))
          a))
      [0.0 0.0 0.0])))

;; --- ECS helpers ------------------------------------------------------------

(defn- em-active?
  "EM force/torque dynamics matter for diffuse and contracting gas."
  [state]
  (lf/hydro-em-active? state))

(defn- em-active-neighbor?
  "Predicate passed to spatial queries: keep only hydro/EM-active neighbors."
  [n]
  (em-active? (:matter-state n)))

(defn- entity->em-data
  "Project an ECS entity into the map the SPH/Lorentz functions expect."
  [world eid]
  {:eid      eid
   :position (ecs/get-component world eid c/position)
   :velocity (ecs/get-component world eid c/velocity)
   :mass     (ecs/get-component world eid c/mass)
   :radius   (ecs/get-component world eid c/radius)
   :density  (ecs/get-component world eid c/density)
   :pressure (ecs/get-component world eid c/pressure)
   :b-field  (ecs/get-component world eid c/b-field)
   :angular-momentum (ecs/get-component world eid c/angular-momentum)
   :rotation-axis    (ecs/get-component world eid c/rotation-axis)
   :state    (ecs/get-component world eid c/matter-state)})

(defn- lorentz-entity-data
  "Project an ECS entity into the compact map the Lorentz acceleration system
   needs. Required components come from `comps`; optional components are read
   once from pre-fetched component tables in `opt`."
  [eid comps opt]
  {:eid      eid
   :position (comps c/position)
   :velocity (get (opt c/velocity) eid)
   :mass     (get (opt c/mass) eid)
   :radius   (comps c/radius)
   :density  (comps c/density)
   :pressure (get (opt c/pressure) eid)
   :b-field  (comps c/b-field)
   :angular-momentum (comps c/angular-momentum)
   :rotation-axis    (get (opt c/rotation-axis) eid)
   :ionization       (get (opt c/ionization-fraction) eid 1.0)
   :neighbor-cache   (get (opt c/neighbor-cache) eid)
   :state    (comps c/matter-state)})

(defn- build-active-lorentz-data
  "Build a vector of EM-active entity data maps for the Lorentz acceleration
   system. Required components are fetched in one `ecs/all-of` pass; optional
   components are read from pre-fetched component tables so the hot loop never
   calls `ecs/get-component`."
  [world]
  (let [required (ecs/all-of world c/b-field c/radius c/position c/density
                             c/angular-momentum c/matter-state)
         opt-tables {c/velocity      (get-in world [:components c/velocity])
                     c/mass          (get-in world [:components c/mass])
                     c/pressure      (get-in world [:components c/pressure])
                     c/rotation-axis (get-in world [:components c/rotation-axis])
                     c/ionization-fraction (get-in world [:components c/ionization-fraction])
                     c/neighbor-cache (get-in world [:components c/neighbor-cache])}]
    (persistent!
     (reduce (fn [acc [eid comps]]
               (if (em-active? (comps c/matter-state))
                 (conj! acc (lorentz-entity-data eid comps opt-tables))
                 acc))
             (transient [])
             required))))

(defn- em-neighbors-and-curl-gradients
  "Return [neighbors curl-gradients] for `data`, using the transient neighbor
   cache when present. `h` is the query radius. gradients is nil on fallback."
  [world data h]
  (if-let [entry (ecs/get-component world (:eid data) c/neighbor-cache)]
    (let [hh2 (* h h)
          nbrs (filterv #(and (em-active? (:matter-state %))
                              (<= (double (:r2 %)) hh2))
                        (:neighbors entry))]
      [nbrs (mapv :gradient-curl nbrs)])
    [(idx/within-radius (:genesis/spatial-tree world) (:position data) h em-active-neighbor?) nil]))

(defn- curl-cached-neighbor-contribution
  "Single cached-neighbor contribution to the SPH curl estimate. `state` is the
   central particle state map; `n` is a cached neighbor map with :b-field, :mass,
   :density, :position, :r2 and :gradient-curl."
  [state n]
  (let [[bx by bz] (:b-field state)
        [gx gy gz] (:gradient-curl n)
        [bnx bny bnz] (:b-field n)
        dbx (- bx (double bnx))
        dby (- by (double bny))
        dbz (- bz (double bnz))
        cxj (- (* dby gz) (* dbz gy))
        cyj (- (* dbz gx) (* dbx gz))
        czj (- (* dbx gy) (* dby gx))
        factor (/ (double (:mass n 1.0))
                  (double (:density n 1.0)))]
    [(* cxj factor) (* cyj factor) (* czj factor)]))

(defn- gradient-non-zero?
  "True if `grad` is a non-zero 3-vector. Used to skip neighbors whose
   pre-computed curl gradient lies outside the kernel support."
  [grad]
  (boolean
   (when-let [[gx gy gz] grad]
     (or (not (zero? gx)) (not (zero? gy)) (not (zero? gz))))))

(defn curl-estimate-from-cache
  "Estimate (∇ × B) for the Lorentz acceleration system using a pre-built
   neighbor-cache entry. Walks the cached neighbors once, skipping particles
   that are not EM-active, lie outside the EM smoothing radius `h`, or carry a
   zero curl gradient (i.e. lie outside the per-neighbor kernel support), and
   uses the pre-computed `:gradient-curl` for the rest. Avoids allocating a
   filtered neighbor vector or a separate gradients vector.

   `state` is a map with :b-field, :density, :position and :neighbors. `h` is the
   EM smoothing radius."
  [{:keys [b-field density position neighbors] :as state} h]
  (if (or (not (lf/finite-vec3? b-field))
          (not (pos? (double density))))
    [0.0 0.0 0.0]
    (let [state (assoc state
                       :density (double density)
                       :position (mapv double position)
                       :b-field (mapv double b-field))
          hh2 (* (double h) (double h))]
      (reduce-kv
       (fn [acc _idx n]
         (if-not (and (em-active? (:matter-state n))
                      (<= (double (:r2 n)) hh2)
                      (gradient-non-zero? (:gradient-curl n))
                      (lf/finite-vec3? (:b-field n)))
           acc
           (mapv + acc (curl-cached-neighbor-contribution state n))))
       [0.0 0.0 0.0]
       neighbors))))

;; --- Legacy in-place system -------------------------------------------------

(defn- em-lorentz-braking-cell
  "Compute [eid hydro-accel angular-momentum spin] for one EM-active entity in the
   legacy em-system. `data` is the entity map from `entity->em-data`."
  [dt world data]
  (let [eid (:eid data)
        h (* 2.0 (double (or (:radius data) 1.0)))
        [nbrs grads] (em-neighbors-and-curl-gradients world data h)
         curl-b (curl-estimate {:b-field (:b-field data)
                                :density (:density data)
                                :position (:position data)
                                :radius (:radius data)
                                :neighbors nbrs
                                :gradients grads})
        lorentz (capped-lorentz-acceleration data curl-b)
        torque (magnetic-braking-torque data dt)]
    [eid lorentz torque]))

(defn- apply-lorentz-braking
  "Apply Lorentz acceleration and magnetic braking torque to a single entity."
  [world [eid a torque]]
  (let [L (ecs/get-component world eid c/angular-momentum)
        new-L (if (and (lf/finite-vec3? L) (lf/finite-vec3? torque))
                (sp/v+ L torque)
                L)
        mass (ecs/get-component world eid c/mass)
        radius (ecs/get-component world eid c/radius)
        new-spin (let [I (* 0.4 mass radius radius)]
                   (if (pos? I)
                     (sp/v* new-L (/ 1.0 I))
                     [0.0 0.0 0.0]))
        w' (if (lf/finite-vec3? a)
             (ecs/put-component world eid c/hydro-accel
                                (sp/v+ (or (ecs/get-component world eid c/hydro-accel)
                                           [0.0 0.0 0.0])
                                       a))
             world)]
    (if new-L
      (-> w'
          (ecs/put-component eid c/angular-momentum new-L)
          (ecs/put-component eid c/spin new-spin))
      w')))

(defn- em-decay-cell
  "Resistively decay the b-field for one entity. Returns [eid b-field] or nil."
  [world dt eid]
  (let [b (field/resistive-decay (ecs/get-component world eid c/b-field)
                                 (ecs/get-component world eid c/radius)
                                 dt)]
    (when (lf/bounded-b-field? b)
      [eid b])))

(defn em-system
  "The EM tick step. Computes:
     1. Lorentz acceleration a = (∇×B)×B / (μ₀ ρ) stored on c/hydro-accel so
        the orbital integrator applies it alongside gravity and hydro.
     2. Magnetic braking torque applied to c/angular-momentum and c/spin.
     3. Resistive flux decay applied to c/b-field.

   Diffuse clumps keep their field essentially unchanged; dense cores slowly
   shed flux — the design's non-ideal hook.

   Reads the shared spatial tree from :genesis/spatial-tree and filters query
   results to EM-active entities."
  [dt]
  (fn [world]
    (let [eids (ecs/entities-with world c/b-field c/radius c/position
                                  c/density c/angular-momentum)
          all-data (mapv #(entity->em-data world %) eids)
          active (filterv #(em-active? (:state %)) all-data)
          updates1 (par/par-mapv #(em-lorentz-braking-cell dt world %) active)
          world1 (reduce apply-lorentz-braking world updates1)
          updates2 (par/par-mapv #(em-decay-cell world1 dt %) eids)]
      (reduce (fn [w [eid b]]
                (if (lf/bounded-b-field? b)
                  (ecs/put-component w eid c/b-field b)
                  w))
              world1
              updates2))))

;; --- Double-buffered write-set system --------------------------------------

(defn- lorentz-acceleration-cell
  "Compute [eid accel-lorentz torque-em] for one EM-active entity."
  [dt data tree]
  (let [eid    (:eid data)
        radius (double (or (:radius data) 1.0))
        h      (* 2.0 radius)
         curl-b (if-let [entry (:neighbor-cache data)]
                  (curl-estimate-from-cache
                   {:b-field (:b-field data)
                    :density (:density data)
                    :position (:position data)
                    :radius (:radius data)
                    :neighbors (:neighbors entry)}
                   h)
                  (if tree
                    (let [nbrs (idx/within-radius
                                tree (:position data) h
                                em-active-neighbor?)]
                      (curl-estimate
                       {:b-field (:b-field data)
                        :density (:density data)
                        :position (:position data)
                        :radius (:radius data)
                        :neighbors nbrs
                        :gradients nil}))
                    [0.0 0.0 0.0]))
        accel  (let [[cx cy cz] curl-b]
                 (if (and (zero? cx) (zero? cy) (zero? cz))
                   [0.0 0.0 0.0]
                   (capped-lorentz-acceleration data curl-b)))
        torque (magnetic-braking-torque data dt)]
    [eid accel torque]))

(defn lorentz-acceleration-system
  "Double-buffer write-set system: Lorentz acceleration a = (∇×B)×B/(μ₀ρ) and
   magnetic-braking torque ΔL for every EM-active clump. Reads the shared spatial
   tree from :genesis/spatial-tree (built once per tick by domain.spatial.index),
   filters query results to EM-active entities. Writes accel.lorentz and
   torque.em; the integrator owns angular-momentum/spin and adds the torque."
  [dt]
  {:id     :em-lorentz
   :writes #{c/accel-lorentz c/torque-em}
   :run    (fn [world]
              (let [active    (build-active-lorentz-data world)
                    tree      (:genesis/spatial-tree world)
                    [computed dt-compute] (profile/timing
                                           #(par/par-mapv
                                             (fn [data]
                                               (lorentz-acceleration-cell dt data tree))
                                             active))
                   accel-cell  (transient {})
                   torque-cell (transient {})
                   _           (doseq [[eid a t] computed]
                                 (when (lf/finite-vec3? a) (assoc! accel-cell eid a))
                                 (when (lf/finite-vec3? t) (assoc! torque-cell eid t)))
                   ws          (merge (tick/contribution-write-set
                                       c/accel-lorentz (persistent! accel-cell)
                                       (keys (get-in world [:components c/accel-lorentz])))
                                      (tick/contribution-write-set
                                       c/torque-em (persistent! torque-cell)
                                       (keys (get-in world [:components c/torque-em]))))]
               (if (:genesis/profile-subsystems? world)
                 (assoc ws :genesis/_profile (merge-with +
                                                         (or (:genesis/_profile ws) {})
                                                         {:em-lorentz/compute (double dt-compute)}))
                 ws)))})
