(ns domain.spatial.index
  "Reusable spatial neighbour index over point-like items, built on the
   Barnes–Hut octree (`domain.gravity.barnes-hut`).

   The SPH (hydro) and Lorentz (em) passes need, for every particle, the set of
   particles within a smoothing length and — for the geometric smoothing length —
   the distance to the nearest neighbour. Done by brute force that is O(N²): each
   particle linearly scans every other. This index builds ONE octree over the
   particle set (O(N log N)) and answers radius and nearest-neighbour queries by
   pruning octree nodes whose AABB is already farther than the query reach, so the
   whole pass is O(N log N) amortised instead of O(N²).

   Each `item` is a map carrying at least `:position` and `:mass` (the octree uses
   mass for its centre-of-mass aggregation; the queries here ignore it) and an
   `:eid` used to exclude the query particle itself from its own neighbour set.
   Queries return the original item maps unchanged, so callers read whatever extra
   keys they projected onto them (`:density`, `:pressure`, `:b-field`, …).

   The returned neighbour set is IDENTICAL to the brute-force `(<= dist cutoff)`
   filter (radius queries are inclusive and include the self-particle, just like
   the scans they replace), so swapping the index in changes performance only,
   not results."
  (:require
    [domain.ecs.core          :as ecs]
    [domain.ecs.components    :as c]
    [domain.gravity.barnes-hut :as bh]
    [shape.spatial :as sp]))

(defn build
  "Build a neighbour index from a seq of item maps (each needs :position, :mass).
   Returns nil for an empty collection."
  [items]
  (bh/build-tree items))

(defn spatial-index
  "Build one Barnes–Hut octree from ALL entities with position+mass and store it
   on the world at :phase0/spatial-tree. Runs before the parallel fan-out so
   every consumer (gravity, SPH, EM, collision) reads the same tree.

   Consumers filter query results by :matter-state as needed:
     - gravity: all bodies (no filter needed)
     - hydro: only :nebula
     - em: only entities with :b-field
     - collision: only non-:nebula (resolved bodies)"
  [world]
  (let [items (->> (ecs/all-of world c/position c/mass c/radius)
                   (filter (fn [[_eid comps]] (some? (comps c/radius))))
                   (mapv (fn [[eid comps]]
                           {:id           eid
                            :position     (comps c/position)
                            :mass         (comps c/mass)
                            :radius       (comps c/radius)
                            :matter-state (ecs/get-component world eid c/matter-state)
                            :density      (ecs/get-component world eid c/density)
                            :pressure     (ecs/get-component world eid c/pressure)
                            :b-field      (ecs/get-component world eid c/b-field)})))
        tree  (bh/build-tree items)]
    (assoc world :phase0/spatial-tree tree)))

(defn- point-aabb-dist2
  "Squared distance from point `p` to axis-aligned box `bb` (0 if inside)."
  [bb [px py pz]]
  (let [[ax ay az] (:aabb-min bb)
        [bx by bz] (:aabb-max bb)
        dx (cond (< px ax) (- ax px) (> px bx) (- px bx) :else 0.0)
        dy (cond (< py ay) (- ay py) (> py by) (- py by) :else 0.0)
        dz (cond (< pz az) (- az pz) (> pz bz) (- pz bz) :else 0.0)]
    (+ (* dx dx) (* dy dy) (* dz dz))))

(defn within-radius
  "Every item within distance `r` of `pos` (inclusive). Includes an item AT `pos`
   (the self-particle); callers that must exclude self filter by `:eid`. Prunes
   any octree node whose AABB is farther than `r`."
  [tree pos r]
  (let [r2 (* (double r) (double r))]
    (letfn [(walk [node acc]
              (if (or (nil? node)
                      (> (point-aabb-dist2 (:aabb node) pos) r2))
                acc
                (if (bh/leaf-node? node)
                  (reduce (fn [a b]
                            (if (<= (sp/len2 (sp/v- pos (:position b))) r2)
                              (conj a b)
                              a))
                          acc (:bodies node))
                  (reduce (fn [a child] (walk child a)) acc (:children node)))))]
      (walk tree []))))

(defn nearest-dist
  "Distance from `pos` to the nearest item whose `:id` differs from `self-eid`.
   ##Inf when the tree holds no other item. Branch-and-bound on the octree:
   a node is visited only while its AABB could still hold something closer than
   the best distance found so far, and children are descended nearest-first so the
   bound tightens quickly."
  [tree pos self-eid]
  (let [best (volatile! Double/POSITIVE_INFINITY)]
    (letfn [(walk [node]
              (when (and node (< (point-aabb-dist2 (:aabb node) pos) (double @best)))
                (if (bh/leaf-node? node)
                  (doseq [b (:bodies node)]
                    (when (not= (:id b) self-eid)
                      (let [d2 (sp/len2 (sp/v- pos (:position b)))]
                        (when (< d2 (double @best)) (vreset! best d2)))))
                  (doseq [c (sort-by (fn [child]
                                       (if child
                                         (point-aabb-dist2 (:aabb child) pos)
                                         Double/POSITIVE_INFINITY))
                                     (:children node))]
                    (walk c)))))]
      (walk tree)
      (Math/sqrt (double @best)))))
