(ns domain.physics.collision
  "Broad-phase bounding-sphere collision detection system.
   Emits :event/collision events — does NOT mutate state directly.
   Response is handled by registered event handlers.

   Detection is LITERAL overlap: two entities collide when their spheres actually
   intersect right now — dist ≤ radiusA + radiusB. No swept/continuous prediction
   (invalid over Phase 0's 31-kyr steps, where gravity bends every path), and no
   distance/gravitational capture (which would merge bodies that are NOT touching
   and place the merged body in the empty space between them — i.e. away from
   where the collision actually occurred). The merged body lands at the
   mass-weighted centroid of the two, which for touching bodies IS the contact
   point.

   Broad phase REUSES the Barnes–Hut octree already built each tick for gravity
   (`domain.gravity.barnes-hut`) rather than maintaining a second uniform grid:
   one spatial index, and it adapts to the clustered distribution (dense core +
   sparse halo) that a single grid cell-size cannot. Overlap queries prune any
   octree node whose AABB is farther than the query's reach."
  (:require
    [domain.ecs.core           :as ecs]
    [domain.ecs.components     :as c]
    [domain.ecs.event          :as event]
    [domain.gravity.barnes-hut :as bh]
    [shape.spatial             :as sp]))

(defn- collidable-bodies
  "Project world into vec of [eid position radius velocity mass] for all RESOLVED
   bodies (non-`:nebula` matter-state with position, radius, mass, velocity).

   Collision here is a LITERAL physical collision: two resolved bodies merge only
   when their actual photospheres/surfaces overlap, NOT when their gravitational
   feeding zones touch. A collapsed body's size is its own structural radius
   (`c/radius`), so two stars must really run into each other to merge — they no
   longer 'poof' together across an inflated 50×-smoothing feeding zone while
   still visibly far apart.

   Gas accretion onto a sink is a SEPARATE channel: `:nebula` parcels are not
   collidable (they resolve by Jeans condensation, or by falling into a sink's
   accretion radius — see `stellar/sink-formation-system`). Keeping the two
   channels distinct is the standard sink-particle split: gas accretes via the
   gravitational capture radius; bound bodies merge only on contact."
   [world]
   (->> (ecs/all-of world c/position c/radius c/mass c/velocity c/matter-state)
        (filter (fn [[_eid comps]] (not= :nebula (comps c/matter-state))))
        (mapv (fn [[eid comps]]
                [eid (comps c/position)
                 (double (comps c/radius))
                 (comps c/velocity)
                 (double (comps c/mass))]))))


(defn- pair-map [[eid-a pos-a rad-a _vel-a] [eid-b pos-b rad-b _vel-b] d]
  {:eid-a  eid-a :eid-b  eid-b
   :pos-a  pos-a :pos-b  pos-b
   :rad-a  rad-a :rad-b  rad-b
   :depth  (- (+ rad-a rad-b) d)
   :normal (let [r (sp/v- pos-b pos-a)
                 l (sp/len r)]
             (if (< l 1e-12)
               [1.0 0.0 0.0]
               (sp/v* r (/ 1.0 l))))})

(defn- point-aabb-dist2
  "Squared distance from point `p` to axis-aligned box `bb` (0 if inside)."
  [bb [px py pz]]
  (let [[ax ay az] (:min bb)
        [bx by bz] (:max bb)
        dx (cond (< px ax) (- ax px) (> px bx) (- px bx) :else 0.0)
        dy (cond (< py ay) (- ay py) (> py by) (- py by) :else 0.0)
        dz (cond (< pz az) (- az pz) (> pz bz) (- pz bz) :else 0.0)]
    (+ (* dx dx) (* dy dy) (* dz dz))))

(defn- collect-overlaps
  "Walk the Barnes–Hut octree, collecting every body whose sphere literally
   overlaps query body `q` (excluding itself). `reach` = q-radius + the largest
   body radius, so a node can be pruned the moment its AABB is farther than
   `reach` from q — no body inside it could reach q's sphere."
  [node q reach acc]
  (cond
    (nil? node) acc
    (> (point-aabb-dist2 (:aabb node) (:position q)) (* reach reach)) acc
    (bh/leaf-node? node)
    (reduce (fn [a b]
              (if (and (not= (:id b) (:id q))
                       (<= (sp/dist (:position q) (:position b))
                           (+ (double (:radius q)) (double (:radius b)))))
                (conj a b)
                a))
            acc (:bodies node))
    :else
    (reduce (fn [a child] (collect-overlaps child q reach a)) acc (:children node))))

(defn- detect-pairs
  "Return a seq of collision maps for every literally-overlapping pair, found by
   querying the Barnes–Hut octree. Each unordered pair is emitted once (guarded
   by eid-a < eid-b)."
  [bodies]
  (if (empty? bodies)
    []
    (let [recs  (mapv (fn [[eid pos r v m]]
                        {:id eid :position pos :radius (double r) :velocity v :mass (double m)})
                      bodies)
          max-r (reduce max 0.0 (map :radius recs))
          tree  (bh/build-tree recs)]
      (for [q recs
            o (collect-overlaps tree q (+ (double (:radius q)) max-r) [])
            :when (< (long (:id q)) (long (:id o)))]
        (pair-map [(:id q) (:position q) (:radius q) (:velocity q)]
                  [(:id o) (:position o) (:radius o) (:velocity o)]
                  (sp/dist (:position q) (:position o)))))))

(defn collision-detection-system
  "ECS system: detects literal sphere overlaps, emits :event/collision for each
   pair. No state mutation — all response is via handlers."
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
