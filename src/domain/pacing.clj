(ns domain.pacing
  "The simulation clock: how much in-game time each tick advances.

   The tick RATE is FIXED — the game advances exactly `ticks-per-second` ticks per
   real second (one per rendered frame). What dilates with COMPLEXITY is `:sim/dt`,
   the in-game seconds each tick covers; the displayed wall-clock rate is DERIVED
   from it (`rate = dt · ticks-per-second`).

   `dt` is bounded by two things: the BULK cloud's dynamical time (for gravitational
   stability) and the system's observable complexity (so articulated phases play
   out longer). The dynamical bound is `dt_dyn = clamp(cfl-factor · t_dyn)` where
   `t_dyn = √(R³ / G·M)` and `R` is the radius enclosing `cloud-mass-fraction` of
   the mass. The complexity bound is `dt_complexity = clamp(complexity-dt-cap)`.
   The effective step is `dt = min(dt_dyn, dt_complexity)`. As the cloud collapses
   `t_dyn` shrinks; as stars and planets form `complexity` rises; both slow the
   clock. Using the bulk (90%-mass) radius keeps a single hot/dominant central sink
   from collapsing the global step and freezing the rest of the cloud.

   Pure data: no IO, no ECS mutation. Reads positions/masses to size the cloud."
  (:require
   [clojure.math :as math] [law.stellar     :as law]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [shape.spatial   :as sp]))

(def seconds-per-year
  "Julian year in seconds — the unit the player's clock counts in."
  3.156e7)

(def ticks-per-second
  "Fixed simulation tick rate: the game advances exactly one tick per rendered
   frame at 60 Hz, always. Complexity changes `:sim/dt` (in-game seconds per
   tick), never the tick count." 60.0)

(def cfl-factor
  "Fraction of the cloud's bulk dynamical time taken as one integration step.
    1/10000 ⇒ ~10000 ticks per dynamical time: free-fall and orbital motion are
    resolved smoothly AND visibly — a diffuse cloud's collapse plays out over
    ~150–200 s at 60 Hz (a few % of the cloud radius of motion per real second),
    rather than crawling imperceptibly." 1.0e-4)

(def pacing-dt-max
  "Ceiling on the per-tick step. Caps how fast the diffuse cloud fast-forwards so
    the early evolution stays watchable rather than blinking past." 5.0e10)

(def pacing-dt-min
  "Floor on the per-tick step, for numerical sanity once the cloud is very
   compact." 1.0e7)

(def soft-factor
  "Gravitational softening as a fraction of the cloud's bulk radius. Comfortably
   above the (G·M·dt²)^(1/3) stability bound for `cfl-factor`, so the
   self-gravitating cloud stays bound as it contracts and the softening shrinks
   with it (finer force resolution as structure tightens)." 0.05)

(def pacing-soft-max 5.0e14)
(def pacing-soft-min 1.0e12)

(def cloud-mass-fraction
  "Mass fraction whose enclosing radius defines the cloud's bulk scale. Using the
   90%-mass radius (not the half-mass radius) keeps a single dominant central
   sink — a star with much of the mass — from collapsing the bulk scale to its
   own pinpoint and freezing the rest of the cloud." 0.9)

(defn cloud-scale
  "Bulk scale of the cloud: `:radius` is the radius (from the mass-weighted
   centre) enclosing `cloud-mass-fraction` of the total mass, and `:mass` is the
   total mass. The enclosed-mass radius is robust to a few ejected stragglers and
   to a tiny ultra-dense core, so it tracks the bulk's true extent — which is what
   sets the dynamical time the integrator must resolve. Pure."
  [world]
  (let [items (->> (ecs/all-of world c/position c/mass)
                   (mapv (fn [[_ comps]] [(comps c/position) (double (comps c/mass))])))
        mtot  (reduce + 0.0 (map second items))]
    (if (or (empty? items) (not (pos? mtot)))
      {:radius 0.0 :mass 0.0}
      (let [com    (sp/v* (reduce (fn [a [p m]] (sp/v+ a (sp/v* p m))) [0.0 0.0 0.0] items)
                          (/ 1.0 mtot))
            target (* cloud-mass-fraction mtot)
            ;; Precompute each item's distance ONCE and sort on the cached key.
            ;; `sort-by` re-evaluates its keyfn inside the comparator, so the
            ;; previous form paid O(N log N) sqrt+destructure calls per tick —
            ;; the most expensive line of the serial tick tail.
            dm     (sort-by first (mapv (fn [[p m]] [(sp/dist com p) m]) items))]
        (loop [acc 0.0 [[d m] & more] dm rmax 0.0]
          (let [acc'  (+ acc (double m))
                d     (double d)
                rmax' (if (> d rmax) d rmax)]
            (if (or (>= acc' target) (nil? more))
              {:radius rmax' :mass mtot}
              (recur acc' more rmax'))))))))

(defn dynamical-time
  "Free-fall/orbital timescale t_dyn = √(R³ / G·M). 0 for a degenerate scale."
  [radius mass]
  (let [r (double radius) m (double mass)]
    (if (and (pos? r) (pos? m))
      (math/sqrt (/ (* r r r) (* law/G m)))
      0.0)))

(defn bulk-dynamical-time
  "Dynamical time of the world's bulk cloud (`cloud-scale`). Shrinks as the cloud
   contracts — the single signal that drives time dilation."
  [world]
  (let [{:keys [radius mass]} (cloud-scale world)]
    (dynamical-time radius mass)))

(defn complexity-dt-cap
  "Maximum per-tick step allowed for a given observable `complexity`. Higher
   complexity slows the clock so that articulated phases (protostars, stars,
   planets) play out longer. `complexity=0` leaves the cap at `pacing-dt-max`;
   every point of complexity divides that ceiling by one more step. Pure."
  [complexity]
  (/ pacing-dt-max (max 1.0 (+ 1.0 (double complexity)))))

(defn pacing-for
  "Pacing from the cloud's bulk dynamical time `t-dyn`, bulk `radius`, and
   observable `complexity`. The tick RATE is fixed (`ticks-per-second`); the
   per-tick step is bounded by BOTH the CFL stability of the whole cloud's
   collapse and by the complexity of the bodies that have formed:

     dt-physics  = clamp(cfl-factor · t_dyn, min, max)
     dt-articulation = clamp(complexity-dt-cap(complexity), min, max)
     dt          = min(dt-physics, dt-articulation)

   As the cloud contracts `t_dyn` shrinks, and as stars/planets form `complexity`
   rises; both act together to slow the clock. Softening tracks the bulk radius.
   The displayed wall-clock rate is DERIVED as `rate = dt · tps`.
   Returns `{:rate :rate-yr :dt :softening}`."
  ([t-dyn radius]
   (pacing-for t-dyn radius 0.0))
  ([t-dyn radius complexity]
   (let [dt-physics  (-> (* cfl-factor (double t-dyn))
                         (max pacing-dt-min) (min pacing-dt-max))
         dt-articulation (-> (complexity-dt-cap complexity)
                             (max pacing-dt-min) (min pacing-dt-max))
         dt          (min dt-physics dt-articulation)
         soft        (-> (* soft-factor (double radius))
                         (max pacing-soft-min) (min pacing-soft-max))
         rate        (* dt ticks-per-second)
         rate-yr     (/ rate seconds-per-year)]
     {:rate      rate
      :rate-yr   rate-yr
      :dt        dt
      :softening soft})))

(defn pace
  "Pacing for the world's CURRENT bulk state and observable complexity. One
   `cloud-scale` pass derives the bulk dynamical time; `pacing-for` folds in
   complexity. The entry point the tick loop calls each step."
  ([world]
   (pace world 0.0))
  ([world complexity]
   (let [{:keys [radius mass]} (cloud-scale world)]
     (pacing-for (dynamical-time radius mass) radius complexity))))

;; --- Time slip --------------------------------------------------------------

(def time-slip-factor
  "Multiplier on the per-tick step while time is SLIPPING — the observer's
   attention has lapsed (low coherence) over a near-empty region, so the universe
   fast-forwards rather than crawling. At 20× a dead, unwatched cloud blinks ahead
   until something happens (or the player tightens focus) and coherence recovers."
  20.0)

(def pacing-dt-slip-max
  "Ceiling on the per-tick step WHILE time is slipping — well above the normal
   `pacing-dt-max` (so a stalled region can race ahead), but still bounded for
   numerical sanity. A slipping region is low-complexity by definition (few
   bodies), so the coarser step integrates safely." 4.0e12)

(defn with-time-slip
  "Rescale a pacing map for a time slip. When `slipping?`, the per-tick step `:dt`
   is boosted by `time-slip-factor` (capped at `pacing-dt-slip-max`) and the
   derived `:rate`/`:rate-yr` recomputed; `:softening` is unchanged (it tracks the
   bulk radius, not the clock). When not slipping, the map passes through. Either
   way `:time-slipping?` is flagged for the HUD. Pure — the slip DECISION
   (`player/time-slip-threshold?`) is made by the caller and passed in, so pacing
   stays free of any observer dependency."
  [{:keys [dt] :as pacing} slipping?]
  (if (and slipping? dt)
    (let [dt'  (min pacing-dt-slip-max (* time-slip-factor (double dt)))
          rate (* dt' ticks-per-second)]
      (assoc pacing
             :dt dt'
             :rate rate
             :rate-yr (/ rate seconds-per-year)
             :time-slipping? true))
    (assoc pacing :time-slipping? false)))
