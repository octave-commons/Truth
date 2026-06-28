(ns domain.pacing
  "The simulation clock: how much in-game time each tick advances.

   The tick RATE is FIXED — the game advances exactly `ticks-per-second` ticks per
   real second (one per rendered frame). What dilates with COMPLEXITY is `:sim/dt`,
   the in-game seconds each tick covers; the displayed wall-clock rate is DERIVED
   from it (`rate = dt · ticks-per-second`).

   `dt` tracks the BULK cloud's dynamical time: `dt = clamp(cfl-factor · t_dyn)`
   where `t_dyn = √(R³ / G·M)` and `R` is the radius enclosing `cloud-mass-fraction`
   of the mass. As the cloud collapses, t_dyn shrinks → the clock dilates, and
   because every body shares the contracting scale, none freeze. Using the bulk
   (90%-mass) radius — not peak temperature, not the half-mass radius — keeps a
   single hot/dominant central sink from collapsing the global step and freezing
   the rest of the cloud.

   Pure data: no IO, no ECS mutation. Reads positions/masses to size the cloud."
  (:require
   [law.stellar     :as law]
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
   1/1000 ⇒ ~1000 ticks per dynamical time: free-fall and orbital motion are
   resolved smoothly AND visibly — a diffuse cloud's collapse plays out over
   ~15–20 s at 60 Hz (a few % of the cloud radius of motion per real second),
   rather than crawling imperceptibly." 1.0e-3)

(def pacing-dt-max
  "Ceiling on the per-tick step. Caps how fast the diffuse cloud fast-forwards so
   the early evolution stays watchable rather than blinking past." 2.0e11)

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
            sorted (sort-by (fn [[p _]] (sp/dist com p)) items)
            target (* cloud-mass-fraction mtot)]
        (loop [acc 0.0 [[p m] & more] sorted]
          (let [acc' (+ acc (double m))]
            (if (or (>= acc' target) (nil? more))
              {:radius (sp/dist com p) :mass mtot}
              (recur acc' more))))))))

(defn dynamical-time
  "Free-fall/orbital timescale t_dyn = √(R³ / G·M). 0 for a degenerate scale."
  [radius mass]
  (let [r (double radius) m (double mass)]
    (if (and (pos? r) (pos? m))
      (Math/sqrt (/ (* r r r) (* law/G m)))
      0.0)))

(defn bulk-dynamical-time
  "Dynamical time of the world's bulk cloud (`cloud-scale`). Shrinks as the cloud
   contracts — the single signal that drives time dilation."
  [world]
  (let [{:keys [radius mass]} (cloud-scale world)]
    (dynamical-time radius mass)))

(defn pacing-for
  "Pacing from the cloud's bulk dynamical time `t-dyn` and bulk `radius`. The tick
   RATE is fixed (`ticks-per-second`); the per-tick step tracks the WHOLE cloud's
   collapse: `dt = clamp(cfl-factor · t-dyn, min, max)`. As the cloud contracts
   t-dyn shrinks, so the clock dilates and EVERY body — all sharing the
   contracting scale — stays resolved; a single hot protostar, which does not
   change the bulk scale, can never freeze the integration. Softening tracks the
   bulk radius. The displayed wall-clock rate is DERIVED as `rate = dt · tps`.
   Returns `{:rate :rate-yr :dt :softening}`."
  [t-dyn radius]
  (let [dt      (-> (* cfl-factor (double t-dyn))
                    (max pacing-dt-min) (min pacing-dt-max))
        soft    (-> (* soft-factor (double radius))
                    (max pacing-soft-min) (min pacing-soft-max))
        rate    (* dt ticks-per-second)
        rate-yr (/ rate seconds-per-year)]
    {:rate      rate
     :rate-yr   rate-yr
     :dt        dt
     :softening soft}))

(defn pace
  "Pacing for the world's CURRENT bulk state: one `cloud-scale` pass, derive the
   bulk dynamical time, and return `pacing-for`. The entry point the tick loop
   calls each step."
  [world]
  (let [{:keys [radius mass]} (cloud-scale world)]
    (pacing-for (dynamical-time radius mass) radius)))
