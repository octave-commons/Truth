(ns domain.physics.cache.soa
  "Structure-of-Arrays physics cache for gravity and kinematics hot paths.

   See `domain.physics.cache` for the high-level design note."
  (:require
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [law.field :as lf]
   [law.stellar.orbital.dynamics :as law-dyn]
   [shape.spatial :as sp]))

(def ^:private pred-accel-sources
  "Mirror of the integrator's :velocity accumulate list
   (domain.integrator/influence-registry) for the drift prediction below.
   Kept literal here to avoid a require cycle; a new accel channel joins BOTH."
  [c/accel-gravity c/accel-pressure c/accel-lorentz c/accel-observer c/accel-warp])

(def ^:private pred-dv-sources
  "Mirror of the integrator's :velocity-delta accumulate list (impulses, raw)."
  [c/dv-flare])

(defn- sum-force-vectors
  "Sum 3-vectors from a sequence of component maps for `eid`."
  [eid maps]
  (reduce (fn [acc m]
            (if-let [v (get m eid)]
              (sp/v+ acc v)
              acc))
          [0.0 0.0 0.0] maps))

(defn- fill-physics-soa!
  "Fill SoA arrays from projected entities and force maps.
   Writes the disjoint index range [start, end).
   The :eps array is the per-entity softening from the species rule
   (law-dyn/body-softening): c/radius for resolved compact bodies,
   `world-soft` for gas/stateless entities (matter-state is looked up per
   entity from the world's component cell — entities MAY lack it; they are
   not projected out)."
  [all       ^objects eids ^doubles mass ^doubles radius ^doubles eps
   ^doubles px ^doubles py ^doubles pz
   ^doubles vx ^doubles vy ^doubles vz
   ^doubles pxp ^doubles pyp ^doubles pzp
   {:keys [dt ms-cell world-soft accel-maps dv-maps]} start end]
  (loop [i (long start)]
    (when (< i end)
      (let [[eid comps] (nth all i)
            [x y z] (comps c/position)
            [vx0 vy0 vz0] (comps c/velocity)
            [ax ay az] (sum-force-vectors eid accel-maps)
            [dvx dvy dvz] (sum-force-vectors eid dv-maps)
            rad (double (or (comps c/radius) 0.0))
            vpx (+ (double vx0) (* (double ax) dt) (double dvx))
            vpy (+ (double vy0) (* (double ay) dt) (double dvy))
            vpz (+ (double vz0) (* (double az) dt) (double dvz))]
        (aset eids i eid)
        (aset mass i (double (or (comps c/mass) 0.0)))
        (aset radius i rad)
        (aset eps i (law-dyn/body-softening (get ms-cell eid) rad world-soft))
        (aset px i (double x))
        (aset py i (double y))
        (aset pz i (double z))
        (aset vx i (double vx0))
        (aset vy i (double vy0))
        (aset vz i (double vz0))
        (aset pxp i (+ (double x) (* vpx dt)))
        (aset pyp i (+ (double y) (* vpy dt)))
        (aset pzp i (+ (double z) (* vpz dt))))
      (recur (inc i)))))

(defn- validate-physics-soa!
  "Throw if SoA validation is enabled and the cache fails the schema."
  [world soa]
  (when (and (not (false? (:genesis/validate-soa? world)))
             (not (lf/physics-soa? soa)))
    (throw (ex-info "Physics SoA cache failed validation" {}))))

;; See `build-physics-soa` docstring for detailed design notes.

(defn- make-physics-arrays
  "Create the empty primitive arrays for a physics SoA of size `n`."
  [n]
  {:eids (object-array n)
   :mass (double-array n)
   :radius (double-array n)
   :eps (double-array n)
   :px (double-array n)
   :py (double-array n)
   :pz (double-array n)
   :vx (double-array n)
   :vy (double-array n)
   :vz (double-array n)
   :px-pred (double-array n)
   :py-pred (double-array n)
   :pz-pred (double-array n)})

(defn- fill-physics-soa-arrays!
  "Fill `arrays` from `all` for the range [start, end)."
  [all arrays fill-env start end]
  (let [{:keys [eids mass radius eps px py pz vx vy vz
                px-pred py-pred pz-pred]} arrays]
    (fill-physics-soa! all eids mass radius eps px py pz vx vy vz
                       px-pred py-pred pz-pred
                       fill-env start end)))

(defn- fill-physics-soa-parallel!
  "Fill `arrays` in parallel chunks of 256 rows."
  [all n arrays fill-env]
  (let [chunk-size 256
        futs (mapv (fn [start]
                     (future
                       (fill-physics-soa-arrays!
                        all arrays fill-env
                        start (min n (+ start chunk-size)))))
                   (range 0 n chunk-size))]
    (run! deref futs)))

(defn- make-physics-soa-from-arrays
  "Construct the physics SoA map from populated `arrays`."
  [arrays n]
  (assoc arrays :n n
         :eids (vec (:eids arrays))))

(defn- build-physics-soa-data
  "Populate `arrays` from `world` and return a validated SoA map."
  [world all n arrays]
  (let [dt (double (or (:sim/dt world) 0.0))
        world-soft (double (or (:sim/softening world) 0.0))
        ms-cell (get-in world [:components c/matter-state])
        accel-maps (mapv #(get-in world [:components %] {}) pred-accel-sources)
        dv-maps (mapv #(get-in world [:components %] {}) pred-dv-sources)
        fill-env {:dt dt :ms-cell ms-cell :world-soft world-soft
                  :accel-maps accel-maps :dv-maps dv-maps}]
    (if (< n 512)
      (fill-physics-soa-arrays! all arrays fill-env 0 n)
      (fill-physics-soa-parallel! all n arrays fill-env))
    (let [soa (make-physics-soa-from-arrays arrays n)]
      (validate-physics-soa! world soa)
      soa)))

(defn build-physics-soa
  "Build and assoc a fresh `:genesis/physics-soa` SoA cache onto `world`.

   The cache covers every entity with position, velocity, mass, and radius,
   packing the dominant physics fields into primitive double arrays for the hot
   gravity and motion-integration kernels. It is rebuilt every tick from a single
   `ecs/all-of` projection. Also carries drift-predicted positions
   (:px-pred/:py-pred/:pz-pred) so force emitters evaluate at the position the
   kick will land on next tick, and the per-entity softening :eps from the
   species rule (law.stellar.orbital.dynamics/body-softening — c/radius for compact
   bodies, :sim/softening for gas; a world without :sim/softening softens its
   gas at 0, so production worlds must declare it). Validation runs by default
   but is skipped when `:genesis/validate-soa?` is explicitly false. The cache
   is transient world plumbing, not an ECS component."
  [world]
  (let [all (vec (ecs/all-of world c/position c/velocity c/mass c/radius))
        n (count all)
        arrays (make-physics-arrays n)
        soa (build-physics-soa-data world all n arrays)]
    (assoc world :genesis/physics-soa soa)))

(defn strip-physics-soa
  "Remove the transient `:genesis/physics-soa` from `world`."
  [world]
  (dissoc world :genesis/physics-soa))

(defn predicted-position-fn
  "Return `(fn [eid] position)` reading the drift-predicted position x̂ from
   the SoA when present, else the snapshot position.

   Force emitters MUST evaluate at x̂: their kick is applied by the integrator
   NEXT tick, to the drifted positions. Evaluating a position-dependent force
   at the snapshot position instead makes it one drift stale — harmless for
   slowly-varying fields, catastrophic for restoring forces whose oscillation
   period is a few ticks (observer pull, warp wells): a lagged spring is a
   spring with NEGATIVE damping, and it pumps the system instead of holding it
   (the dispersing-clump bug, spec Fix 5)."
  [world]
  (let [soa (:genesis/physics-soa world)]
    (if-let [^doubles pxs (and soa (:px-pred soa))]
      (let [^doubles pys (:py-pred soa)
            ^doubles pzs (:pz-pred soa)
            idx (into {} (map-indexed (fn [i e] [e i])) (:eids soa))]
        (fn [eid]
          (if-let [i (idx eid)]
            [(aget pxs (int i)) (aget pys (int i)) (aget pzs (int i))]
            (ecs/get-component world eid c/position))))
      (fn [eid] (ecs/get-component world eid c/position)))))
