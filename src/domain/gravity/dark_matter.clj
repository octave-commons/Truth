(ns domain.gravity.dark-matter
  "The static dark-matter halo — spark-redesign card 1
   (kanban/tasks/dark-matter-static-halo.md). A STATIC background gravitational
   potential: a very massive, large-scale-radius Plummer sphere centred on the
   world origin `[0 0 0]`. It does not collapse, move, spawn an entity, or
   render — it is a pure field, deepening the well so bodies keep their
   infall momentum bound instead of flinging past the system edge with it.

   Centring at the origin IS centring on the barycenter: the integrator
   subtracts the one-tick-stale COM `:genesis/frame-offset`
   (`domain.spatial.index/spatial-index`, `domain.integrator.kinematics`) from
   every position every tick, pinning the centre of mass at `[0 0 0]`. No
   separate COM tracker is built here.

   Reuses `law.stellar/plummer-acceleration` — the same primitive the observer
   halo (`domain.player.influence`) and the player's paid warp wells
   (`domain.intervention`) already use — so the field shape (zero at centre,
   peak pull at a/√2, Keplerian fade beyond) is the established house style.
   A single fan-out emitter, sole writer of `:component/accel.dark-matter`;
   the motion integrator sums it like every other force channel."
  (:require
   [law.stellar    :as law]
   [shape.spatial  :as sp]
   [domain.ecs.core       :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.tick       :as tick]))

(def ^:private zero3 [0.0 0.0 0.0])

(defn halo-mass
  "Static halo mass (kg): `:genesis/dark-matter-mass-factor` × world's
   `:genesis/nebula-mass`, defaulting to `law/default-dark-matter-mass-factor`
   (deliberately MORE massive than the nebula per owner decision). Set the
   world key to `0.0` to disable the halo entirely."
  [world]
  (let [factor (double (or (:genesis/dark-matter-mass-factor world)
                           law/default-dark-matter-mass-factor))
        neb-mass (double (or (:genesis/nebula-mass world) 0.0))]
    (* factor neb-mass)))

(defn halo-scale-radius
  "Static halo Plummer scale radius (m): `:genesis/dark-matter-scale-factor` ×
   world's `:genesis/nebula-radius`, defaulting to
   `law/default-dark-matter-scale-factor` (~half the initial nebula radius)."
  [world]
  (let [factor (double (or (:genesis/dark-matter-scale-factor world)
                           law/default-dark-matter-scale-factor))
        neb-r (double (or (:genesis/nebula-radius world) 0.0))]
    (* factor neb-r)))

(defn dark-matter-acceleration
  "Acceleration vector (m/s²) the static halo of mass `M` and scale radius `a`
   exerts on a body at `pos`, pulling toward the world origin. Nil at the
   origin itself or when `M`/`a` is non-positive."
  [M a pos]
  (let [dist (sp/len pos)]
    (when (and (pos? (double M)) (pos? (double a)) (pos? dist))
      (let [g (law/plummer-acceleration M a dist)]
        (when (pos? g)
          (sp/v* pos (/ (- g) dist)))))))

(defn dark-matter-acceleration-system
  "Write-set system (sole writer of `c/accel-dark-matter`): the static
   background halo's Plummer pull toward the origin for every body carrying a
   position. A pure fan-out emitter reading only the frozen snapshot's
   positions — no collapse, no motion, no rendering. Uses
   `tick/contribution-write-set` against the prior tick's carried entities, so
   a body that has left the field (or the halo being live-tuned down to zero)
   has its stale contribution explicitly cleared rather than left to linger —
   the same auto-clearing guarantee `:warp`/`:observer-accel` document, made
   to actually hold across the disabled branch too."
  []
  {:id     :dark-matter
   :writes #{c/accel-dark-matter}
   :run    (fn [world]
             (let [M     (halo-mass world)
                   a     (halo-scale-radius world)
                   prior (keys (get-in world [:components c/accel-dark-matter]))
                   cell  (if (or (not (pos? M)) (not (pos? a)))
                           {}
                           (into {}
                                 (keep (fn [eid]
                                         (when-let [acc (dark-matter-acceleration
                                                         M a
                                                         (or (ecs/get-component world eid c/position) zero3))]
                                           [eid acc])))
                                 (ecs/entities-with world c/position)))]
               (tick/contribution-write-set c/accel-dark-matter cell prior)))})
