(ns domain.player.influence
  "Observer halo influence and acceleration system."
  (:require
   [law.stellar :as law]
   [shape.spatial :as sp]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.tick :as tick]
   [domain.physics.cache :as pcache]
   [domain.player.state :as state]))

(def default-halo-mass-factor "Halo mass at FULL coherence and focus intensity, as a multiple of the seeded\n   cloud's mass. Halo mass = factor · coherence · focus-intensity · cloud-mass,\n   so at spawn defaults (0.8, 0.5) the spark weighs ~0.8 cloud masses — felt\n   everywhere, dominant nowhere. Live knob: :genesis/observer-halo-mass-factor\n   (Spark menu panel); 0.0 disables the halo entirely." 2.0)

(def default-influence-dv-cap "Per-tick Δv ceiling for influence fields, as a multiple of the cloud's virial\n   speed. A dt-robustness BACKSTOP (a Myr-scale step across a concentrated halo\n   must not teleport parcels), not the design lever — at sane knob values the\n   halo field stays far below it. Live knob: :genesis/influence-dv-cap." 1.0)

(def ^{:const true} halo-reach-factor "Influence cutoff in scale radii. Beyond 3a the Plummer pull is under 10% of\n   peak; cutting there keeps the write-set sparse and auto-clearing." 3.0)

(defn influence-reference "Reference scales every influence field (observer halo, warp wells) shares,\n   read off the world with the seeded-cloud defaults: `:ref-mass`, the cloud\n   mass that halo mass factors multiply, and `:dv-cap` (m/s), the per-tick Δv\n   ceiling — the cloud's virial speed × :genesis/influence-dv-cap." [world] (let [m (double (or (:genesis/nebula-mass world) 4.0E30)) r (double (or (:genesis/nebula-radius world) 2.0E16)) cap (double (or (:genesis/influence-dv-cap world) default-influence-dv-cap))] {:ref-mass m, :dv-cap (* cap (law/virial-speed m r))}))

(defn halo-mass "The observer halo's gravitating mass (kg): mass-factor · coherence ·\n   focus-intensity · ref-mass. Coherence is the live scaling — the spark's\n   gravitational presence grows and fades with its clarity." [{:keys [coherence focus-intensity]} mass-factor ref-mass] (* (double mass-factor) (double (or coherence 0.0)) (double (or focus-intensity 0.0)) (double ref-mass)))

(defn observer-acceleration "Acceleration the observer's halo exerts on a body at `body-pos`: a Plummer\n   pull toward the focus (law.stellar/plummer-acceleration) with scale radius\n   :focus-radius and mass from `halo-mass`, capped so |Δv| = |a|·dt never\n   exceeds `:dv-cap` — the dt backstop. Nil outside `halo-reach-factor` scale\n   radii, at the exact centre, or when the halo mass is zero." [obs body-pos dt {:keys [ref-mass mass-factor dv-cap]}] (let [scale (double (or (:focus-radius obs) 0.0)) M (halo-mass obs mass-factor ref-mass) d (sp/v- (:focus-position obs) body-pos) dist (sp/len d)] (when (and (pos? M) (pos? scale) (pos? dist) (< dist (* halo-reach-factor scale))) (let [g (-> (law/plummer-acceleration M scale dist) (min (/ (double dv-cap) (max 1.0 (double dt)))))] (when (pos? g) (sp/v* d (/ g dist)))))))

(defn observer-acceleration-system "Write-set system (sole writer of :component/accel.observer): the halo pull\n   toward the focus for every body within reach — the spark as a large, diffuse\n   centre of gravity, 'reality condenses where you look.' A pure snapshot-\n   reading fan-out emitter; the integrator sums accel.observer like any other\n   force. Reads the observer focus from the snapshot (set by last tick's\n   observer-system / player input — one-tick lag, accepted) and auto-clears the\n   contribution from bodies that have drifted out of reach. Set\n   :genesis/observer-halo-mass-factor to 0.0 to disable.\n\n   The observer entity itself is EXCLUDED: since spark-redesign card 4 the\n   spark is a first-class body (position+mass columns) and would otherwise\n   meet the emitter's own query — a body does not pull itself (Barnes–Hut\n   excludes self the same way)." [] {:id :observer-accel, :writes #{c/accel-observer}, :run (fn [world] (let [obs (state/get-observer world) obs-eid (state/observer-entity world) kf (double (or (:genesis/observer-halo-mass-factor world) default-halo-mass-factor))] (if (or (nil? obs) (not (pos? kf))) {c/accel-observer {}} (let [dt (double (or (:sim/dt world) 1.0E12)) reference (assoc (influence-reference world) :mass-factor kf) pos-of (pcache/predicted-position-fn world) cell (into {} (keep (fn [eid] (when (not= eid obs-eid) (when-let [a (observer-acceleration obs (pos-of eid) dt reference)] [eid a])))) (ecs/entities-with world c/position c/mass))] (tick/contribution-write-set c/accel-observer cell (keys (get-in world [:components c/accel-observer])))))))})
