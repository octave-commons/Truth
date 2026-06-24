(ns domain.physics.collision
  "Broad-phase bounding-sphere collision detection system.
   Emits :event/collision events — does NOT mutate state directly.
   Response is handled by registered event handlers.

   Detection: two entities collide when
     dist(posA, posB) <= radiusA + radiusB

   This is O(n²) broad phase. For large n, replace with
   a BVH or spatial hash narrow phase — same event contract."
  (:require
    [domain.ecs.core       :as ecs]
    [domain.ecs.components :as c]
    [domain.ecs.event      :as event]
    [shape.spatial         :as sp]))

(defn- collidable-bodies
  "Project world into vec of [eid position radius] for all entities
   that have position, radius, and mass components."
  [world]
  (->> (ecs/all-of world c/position c/radius c/mass)
       (mapv (fn [[eid comps]]
               [eid (comps c/position) (double (comps c/radius))]))))

(defn- detect-pairs
  "Return seq of collision maps for overlapping pairs."
  [bodies]
  (let [n (count bodies)]
    (for [i (range n)
          j (range (inc i) n)
          :let [[eid-a pos-a rad-a] (nth bodies i)
                [eid-b pos-b rad-b] (nth bodies j)
                d (sp/dist pos-a pos-b)]
          :when (<= d (+ rad-a rad-b))]
      {:eid-a  eid-a :eid-b  eid-b
       :pos-a  pos-a :pos-b  pos-b
       :rad-a  rad-a :rad-b  rad-b
       :depth  (- (+ rad-a rad-b) d)
       :normal (let [r (sp/v- pos-b pos-a)
                     l (sp/len r)]
                 (if (< l 1e-12)
                   [1.0 0.0 0.0]
                   (sp/v* r (/ 1.0 l))))})))

(defn collision-detection-system
  "ECS system: detects bounding-sphere overlaps, emits :event/collision
   for each pair. No state mutation — all response is via handlers."
  [world]
  (let [bodies (collidable-bodies world)
        tick   (:tick world)
        pairs  (detect-pairs bodies)]
    (reduce (fn [w {:keys [eid-a eid-b pos-a pos-b
                            rad-a rad-b depth normal]}]
              (event/dispatch w
                (event/->event
                  {:tick     tick
                   :kind     :event/collision
                   :entities #{eid-a eid-b}
                   :payload  {:eid-a  eid-a
                              :eid-b  eid-b
                              :pos-a  pos-a
                              :pos-b  pos-b
                              :rad-a  rad-a
                              :rad-b  rad-b
                              :depth  depth
                              :normal normal}})))
            world
            pairs)))
