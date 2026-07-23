(ns law.spark
  "Tuned constants and pure resolve interpolation for the spark as a real,
   gravity-bound ECS body (spark-redesign card 4 —
   kanban/tasks/spark-as-gravity-bound-body.md).

   The spark resolves from large+diffuse to small+dense+body-like as the
   system's `:genesis/formation-progress` (domain.genesis.summary) climbs 0->1.
   Both curves are pure monotonic functions of progress: the metric plateaus
   while mass climbs through intermediate matter-states and steps at
   `:star`/`:planet` promotions, and a plain lerp tolerates that by
   construction — same input, same output; no oscillation.

   Every default is world-overridable for live tuning via the documented
   `:genesis/spark-*` world keys (the dark-matter halo precedent,
   kanban/tasks/dark-matter-static-halo.md).")

(def ^:const default-final-mass
  "Spark mass (kg) at full formation (progress = 1): a small moonlet — heavy
   enough to be a real body in the Barnes–Hut sums, light enough that a
   planet's well can capture it as a satellite. The PRE-FORMATION mass
   (progress = 0) is exactly 0: a test particle contributes nothing to
   gravity sums yet is still accelerated by every field (Barnes–Hut,
   dark-matter halo), so the unformed spark is moved by physics for free.
   World-overridable via `:genesis/spark-final-mass`."
  1.0e20)

(def ^:const default-initial-radius
  "Spark radius (m) at progress = 0 — the unresolved spark's diffuse extent,
   condensed-core scale. Large enough to read as a cloud-self, small enough
   not to poison the collision broad phase's per-node `:max-radius` (the
   spark never collides — it carries no `c/matter-state` — but it rides the
   shared spatial tree). World-overridable via
   `:genesis/spark-initial-radius`."
  1.0e12)

(def ^:const default-final-radius
  "Spark radius (m) at progress = 1 — a ~400 km moonlet, consistent with
   `default-final-mass` at icy-rubble density. World-overridable via
   `:genesis/spark-final-radius`."
  4.0e5)

(defn- clamp01
  "Clamp `x` into [0,1]."
  [x]
  (-> (double (or x 0.0)) (max 0.0) (min 1.0)))

(defn spark-mass
  "Spark mass (kg) at formation `progress` in [0,1]: a linear rise from
   exactly 0 (pre-formation — see `default-final-mass`) to `final-mass`."
  [progress final-mass]
  (* (clamp01 progress) (double final-mass)))

(defn spark-radius
  "Spark radius (m) at formation `progress` in [0,1]: a linear shrink from
   `initial-radius` (diffuse) to `final-radius` (body-like)."
  [progress initial-radius final-radius]
  (let [p (clamp01 progress)]
    (+ (double initial-radius)
       (* p (- (double final-radius) (double initial-radius))))))
