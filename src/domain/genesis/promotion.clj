(ns domain.genesis.promotion
  "Dual-representation promotion and demotion: focus collapses regional gas into\n   resolved entities and releases them back to regional when attention withdraws.\n   All conservation flows through the influence registry; this namespace only\n   changes matter-state / field-zone labels, never invents or destroys mass.\n   Respects the remnant-ladder invariant: a bound resolved body is NEVER\n   demoted back to :nebula; demotion is a zone-label change only."
  (:require
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.ecs.event :as event]
   [domain.player :as player]
   [shape.spatial :as sp]))

(def ^:private threshold-event-kinds
  "Event kinds that block demotion for the entity involved."
  #{:event/stellar-ignition
    :event/protostar-formation
    :event/planet-formation
    :event/condensed-core-formation
    :event/planetesimal-formation
    :event/gas-giant-formation
    :event/brown-dwarf-formation
    :event/collision
    :event/phase-transition
    :event/life-emergence
    :event/gate-discovery})

(defn- immediate-radius
  "Immediate focus radius from the observer's attention shell."
  [obs]
  (or (get-in obs [:attention-shell :immediate-r])
      (player/probability-collapse-radius obs)))

(defn- focus-position
  "Observer focus position."
  [obs]
  (or (:focus-position obs) [0.0 0.0 0.0]))

(defn- recent-threshold-entities
  "Entity ids involved in threshold events on the current tick."
  [world]
  (let [this-tick (:tick world)]
    (->> (event/events-since world this-tick)
         (filter #(= (:tick %) this-tick))
         (filter #(threshold-event-kinds (:kind %)))
         (mapcat #(seq (:entities %)))
         (set))))

(defn- promote-entity
  "Convert a :nebula parcel into a resolved :planetesimal body with the same\n   mass, velocity, angular momentum, and composition. Returns the updated world."
  [world eid]
  (let [resolved? (not= :nebula (ecs/get-component world eid c/matter-state))]
    (if resolved?
      world
      (-> world
          (ecs/put-component eid c/matter-state :planetesimal)
          (ecs/put-component eid c/body-kind :body/rocky)
          (ecs/put-component eid c/field-zone :immediate)))))

(defn promotion-system
  "World -> world transformer. Every :nebula parcel inside the observer's\n   immediate focus radius is promoted to a resolved :planetesimal (same mass,\n   velocity, angular momentum). Runs as a serial barrier after physics so it\n   can mutate matter-state without fan-out conflicts."
  [world]
  (if-let [obs (player/get-observer world)]
    (let [focus (focus-position obs)
          r     (immediate-radius obs)
          eids  (ecs/entities-with world c/matter-state c/position)]
      (reduce (fn [w eid]
                (if (= :nebula (ecs/get-component w eid c/matter-state))
                  (let [pos (ecs/get-component w eid c/position)]
                    (if (<= (sp/dist focus pos) r)
                      (promote-entity w eid)
                      w))
                  w))
              world
              eids))
    world))

(defn demotion-system
  "World -> world transformer. Resolved bodies that have left the immediate\n   focus zone and have no recent threshold events are moved to :regional.\n   Does NOT demote bound bodies back to :nebula (remnant-ladder invariant)."
  [world]
  (if-let [obs (player/get-observer world)]
    (let [focus (focus-position obs)
          r     (immediate-radius obs)
          blocked (recent-threshold-entities world)
          eids    (ecs/entities-with world c/matter-state c/position c/field-zone)]
      (reduce (fn [w eid]
                (if (and (= :immediate (ecs/get-component w eid c/field-zone))
                         (not (contains? blocked eid))
                         (> (sp/dist focus (ecs/get-component w eid c/position)) r))
                  (ecs/put-component w eid c/field-zone :regional)
                  w))
              world
              eids))
    world))

(defn focus-zone-systems
  "Run promotion then demotion in one serial barrier."
  [world]
  (-> world promotion-system demotion-system))
