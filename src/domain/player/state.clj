(ns domain.player.state
  "Observer entity construction and ECS access."
  (:require
   [shape.spatial :as sp]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [law.spark :as law-spark]))

(defn create-observer
  "A fresh observer map — the spark's ATTENTION state only. Physical state
   (position/velocity/mass/radius/body-kind) lives in first-class ECS columns
   on the same entity (spark-redesign card 4): this map deliberately carries
   no `:position` key, so there is exactly one live position source — the
   `c/position` column. `:focus-position` stays here: it is a pure attention
   point (camera/HUD/halo targeting), not the body's location."
  [position]
  {:agency 0.0, :focus-radius 5.0E15, :time-witnessed 0.0, :last-tick 0, :focus-intensity 0.5, :coherence 0.8, :resolution 0.0, :id (java.util.UUID/randomUUID), :resonance-thresholds #{}, :focus-position position, :narrative-seeds {}, :resonance 0.0, :max-coherence 1.0, :resonance-events [] :attention-shell {:immediate-r 4.0E15 :regional-r 1.5E16}})

(defn observer-entity "The singleton observer entity id, or nil." [world] (first (ecs/entities-with world c/observer)))

(defn get-observer "The observer map from the world." [world] (when-let [eid (observer-entity world)] (ecs/get-component world eid c/observer)))

(defn put-observer "Replace the observer component in the world, returning the updated world." [world observer] (if-let [eid (observer-entity world)] (ecs/put-component world eid c/observer observer) world))

(defn update-observer "Apply f to the observer map in the world." [world f & args] (if-let [eid (observer-entity world)] (ecs/update-component world eid c/observer (fn* [p1__247#] (apply f p1__247# args))) world))

(defn observer-position
  "The spark's physical position in world metres — the single source of
   truth: the observer entity's `c/position` column, advanced by the same
   integrator kinematics as every other body. Nil when there is no observer
   or the column is absent."
  [world]
  (when-let [eid (observer-entity world)]
    (ecs/get-component world eid c/position)))

(defn spawn-observer
  "Spawn the singleton observer entity as a gravity-bound ECS body
   (spark-redesign card 4 — kanban/tasks/spark-as-gravity-bound-body.md):
   first-class `c/position`/`c/velocity`/`c/mass`/`c/radius`/`c/body-kind`
   `:spark` columns, so gravity (the spatial index + Barnes–Hut) and the
   integrator's kinematics pick it up with no special case. Deliberately NO
   `c/matter-state`/`c/accretion-radius`/`c/composition` — that auto-excludes
   the spark from collision, hydro, the classifier, sink-formation, and disc
   evolution, which all gate on matter-state.

   The columns are seeded at the progress-0 resolve values (mass exactly 0 —
   a test particle; radius the diffuse initial extent). The EXISTING mass
   and radius writers re-derive them every tick from
   `:genesis/formation-progress` via their `body-kind = :spark` branches
   (domain.integrator.core/mass-ws and domain.stellar.geometry/structure —
   component ownership is per-TYPE, so no new system may write them).
   Returns [world eid]."
  [world position]
  (let [[w eid] (ecs/spawn world)
        r0 (double (or (:genesis/spark-initial-radius world) law-spark/default-initial-radius))]
    [(ecs/put-components w eid {c/observer        (create-observer position)
                                c/narrative-state {:mood :anticipation :last-utterance-tick nil :topics #{}}
                                c/position        position
                                c/velocity        (sp/vec3 0 0 0)
                                c/mass            0.0
                                c/radius          r0
                                c/body-kind       :spark})
     eid]))

(defn repair-observer-columns
  "Load-time repair hook for pre-card-4 worlds (spark-redesign card 4 review).
   Call once after deserializing any world that may predate the spark becoming
   a gravity-bound ECS body: when the world has a `c/observer` entity whose
   first-class columns are missing, seed them — `c/position` from the legacy
   `:position` key inside the observer map (falling back to nothing when even
   that is absent), `c/velocity` zero, `c/mass` 0 (the explicit pre-formation
   test-particle mass), `c/radius` the diffuse initial extent, `c/body-kind`
   `:spark` — and strip the legacy `:position` shadow key from the map so no
   reader can find a second position source.

   Idempotent: on a healthy world every column is present and the map carries
   no `:position`, so this returns the world unchanged. There is currently NO
   load path in-repo (error dumps are write-only); this fn is the designated
   repair point any future world-load/restore must call."
  [world]
  (if-let [eid (observer-entity world)]
    (let [obs      (get-observer world)
          missing? (fn [ct] (nil? (ecs/get-component world eid ct)))
          r0       (double (or (:genesis/spark-initial-radius world)
                               law-spark/default-initial-radius))]
      (cond-> world
        (and (missing? c/position) (:position obs))
        (ecs/put-component eid c/position (:position obs))
        (missing? c/velocity)
        (ecs/put-component eid c/velocity (sp/vec3 0 0 0))
        (missing? c/mass)
        (ecs/put-component eid c/mass 0.0)
        (missing? c/radius)
        (ecs/put-component eid c/radius r0)
        (missing? c/body-kind)
        (ecs/put-component eid c/body-kind :spark)
        (contains? obs :position)
        (update-observer #(dissoc % :position))))
    world))
