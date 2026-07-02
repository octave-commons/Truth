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

(declare build-grid grid-within-radius grid-nearest-dist)

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
        tree  (bh/build-tree items)
        grid  (when (seq items)
                (let [aabb    (sp/aabb-from-points (map :position items))
                      side    (max (sp/max-side aabb) 1.0)
                      n       (count items)
                      cell-size (/ side (Math/pow n (/ 1.0 3.0)))]
                  (build-grid items cell-size)))]
    (assoc world
           :phase0/spatial-tree tree
           :phase0/spatial-grid grid
           :phase0/spatial-items items)))

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
   any octree node whose AABB is farther than `r`.

   `pred` optionally filters bodies before the distance check (e.g. by matter-state);
   this avoids constructing an intermediate seq of all bodies in the radius."
  ([tree pos r]
   (within-radius tree pos r (constantly true)))
  ([tree pos r pred]
   (let [r2 (* (double r) (double r))
         [px py pz] pos]
     (letfn [(walk [node acc]
               (if (or (nil? node)
                       (> (point-aabb-dist2 (:aabb node) pos) r2))
                 acc
                 (if (bh/leaf-node? node)
                   (reduce (fn [a b]
                             (if (and (pred b)
                                      (let [bp (:position b)
                                            dx (- px (double (nth bp 0)))
                                            dy (- py (double (nth bp 1)))
                                            dz (- pz (double (nth bp 2)))]
                                        (<= (+ (* dx dx) (* dy dy) (* dz dz)) r2)))
                               (conj a b)
                               a))
                           acc (:bodies node))
                   (reduce (fn [a child] (walk child a)) acc (:children node)))))]
       (walk tree [])))))

(defn nearest-dist
  "Distance from `pos` to the nearest item whose `:id` differs from `self-eid`.
   ##Inf when the tree holds no other item. Branch-and-bound on the octree:
   a node is visited only while its AABB could still hold something closer than
   the best distance found so far. Children are visited unsorted; the sort cost
   usually outweighs the benefit for the smoothing-length queries SPH issues."
  [tree pos self-eid]
  (let [best (volatile! Double/POSITIVE_INFINITY)
        [px py pz] pos]
    (letfn [(walk [node]
              (when (and node (< (point-aabb-dist2 (:aabb node) pos) (double @best)))
                (if (bh/leaf-node? node)
                  (doseq [b (:bodies node)]
                    (when (not= (:id b) self-eid)
                      (let [bp (:position b)
                            dx (- px (double (nth bp 0)))
                            dy (- py (double (nth bp 1)))
                            dz (- pz (double (nth bp 2)))
                            d2 (+ (* dx dx) (* dy dy) (* dz dz))]
                        (when (< d2 (double @best)) (vreset! best d2)))))
                  (doseq [c (:children node)]
                    (walk c)))))]
      (walk tree)
      (Math/sqrt (double @best)))))

;; --- Unified query wrappers ------------------------------------------------

(defn query-within-radius
  "Neighbors within distance `r` of `pos`, optionally filtered by `pred`.
   Uses the uniform grid if available on `world`, otherwise the Barnes–Hut tree."
  ([world pos r]
   (query-within-radius world pos r (constantly true)))
  ([world pos r pred]
   (if-let [grid (:phase0/spatial-grid world)]
     (grid-within-radius grid pos r pred)
     (within-radius (:phase0/spatial-tree world) pos r pred))))

(defn query-nearest-dist
  "Nearest-neighbor distance from `pos` excluding `self-eid`.
   The Barnes–Hut tree is kept for nearest-neighbor queries: its branch-and-bound
   traversal is consistently faster than expanding uniform-grid shells, especially
   when the smoothing-length regime needs a single nearest distance rather than a
   full neighbor set. Falls back to the uniform grid if no tree exists; returns
   ##Inf when neither index is available."
  [world pos self-eid]
  (if-let [tree (:phase0/spatial-tree world)]
    (nearest-dist tree pos self-eid)
    (if-let [grid (:phase0/spatial-grid world)]
      (grid-nearest-dist grid pos self-eid)
      Double/POSITIVE_INFINITY)))

;; --- Uniform grid (for short-range SPH/EM queries) -------------------------

(defn- item-key
  "Grid cell coordinates for `pos` given cell size `cs`."
  [cs pos]
  (let [[x y z] pos
        f (fn [v] (long (Math/floor (/ (double v) cs))))]
    [(f x) (f y) (f z)]))

(defn build-grid
  "Build a uniform 3D grid from `items` (maps with :position and :id) using the
   supplied `cell-size`. Returns a map with `:cell-size`, `:cells` {[i j k] [items]},
   and `:items`. Empty input returns a grid with no cells."
  [items cell-size]
  (let [cs (double cell-size)]
    {:cell-size cs
     :cells (reduce (fn [m item]
                      (update m (item-key cs (:position item))
                              (fnil conj []) item))
                    {}
                    items)
     :items items}))

(defn grid-within-radius
  "Every item within distance `r` of `pos` (inclusive) from a uniform `grid`.
   `pred` optionally filters items before the distance check. Includes self if
   self is within the radius; callers exclude by `:id` as needed."
  ([grid pos r]
   (grid-within-radius grid pos r (constantly true)))
  ([grid pos r pred]
   (let [cs (:cell-size grid)
         r2 (* (double r) (double r))
         [px py pz] pos
         [ix iy iz] (item-key cs pos)
         k (max 0 (long (Math/ceil (/ (double r) cs))))]
     (loop [dx (- k) acc []]
       (if (> dx k)
         acc
         (recur (inc dx)
                (loop [dy (- k) acc acc]
                  (if (> dy k)
                    acc
                    (recur (inc dy)
                           (loop [dz (- k) acc acc]
                             (if (> dz k)
                               acc
                               (recur (inc dz)
                                      (reduce (fn [a b]
                                                (if (pred b)
                                                  (let [bp (:position b)
                                                        x (- px (double (nth bp 0)))
                                                        y (- py (double (nth bp 1)))
                                                        z (- pz (double (nth bp 2)))]
                                                    (if (<= (+ (* x x) (* y y) (* z z)) r2)
                                                      (conj a b)
                                                      a))
                                                  a))
                                              acc
                                              (get-in grid [:cells [(+ ix dx) (+ iy dy) (+ iz dz)]]))))))))))))))

(defn grid-nearest-dist
  "Distance from `pos` to the nearest item whose `:id` differs from `self-eid`
   in a uniform `grid`. Expands the search cell-by-cell until a neighbor is found
   or the grid is exhausted; returns ##Inf when no other item exists."
  [grid pos self-eid]
  (let [cs (:cell-size grid)
        [px py pz] pos
        [ix iy iz] (item-key cs pos)
        best (volatile! Double/POSITIVE_INFINITY)]
    (loop [k 0]
      (when (<= k 50)
        (let [min-dist-at-k (if (zero? k) 0.0 (* (dec k) cs))]
          (when (or (zero? k) (< min-dist-at-k (Math/sqrt (double @best))))
            ;; enumerate shell at Chebyshev distance k
            (doseq [dx (range (- k) (inc k))
                    dy (range (- k) (inc k))
                    dz (range (- k) (inc k))
                    :when (or (zero? k)
                              (== k (max (Math/abs dx) (Math/abs dy) (Math/abs dz))))]
              (doseq [b (get-in grid [:cells [(+ ix dx) (+ iy dy) (+ iz dz)]])]
                (when (not= (:id b) self-eid)
                  (let [bp (:position b)
                        x (- px (double (nth bp 0)))
                        y (- py (double (nth bp 1)))
                        z (- pz (double (nth bp 2)))
                        d2 (+ (* x x) (* y y) (* z z))]
                    (when (< d2 (double @best)) (vreset! best d2))))))
            (recur (inc k))))))
    (Math/sqrt (double @best))))

