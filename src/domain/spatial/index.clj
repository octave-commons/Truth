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
   [clojure.math :as math] [domain.ecs.core          :as ecs]
   [domain.ecs.components    :as c]
   [domain.ecs.parallel      :as par]
   [domain.gravity.barnes-hut :as bh]
   [shape.spatial :as sp]))

(declare build-grid grid-within-radius grid-nearest grid-nearest-dist)

(defn build
  "Build a neighbour index from a seq of item maps (each needs :position, :mass).
   Returns nil for an empty collection."
  [items]
  (bh/build-tree items))

(defn- com-from-items
  "Mass-weighted centre of mass of spatial `items`, using the same arithmetic
   and accumulation order as `domain.genesis/center-of-mass`. The items vector
   is in eid order, so the result is identical to the serial ECS walk."
  [items]
  (if (seq items)
    (let [[sx sy sz sm]
          (reduce (fn [[ax ay az am] item]
                    (let [[x y z] (:position item)
                          m (double (:mass item))]
                      [(+ ax (* (double x) m))
                       (+ ay (* (double y) m))
                       (+ az (* (double z) m))
                       (+ am m)]))
                  [0.0 0.0 0.0 0.0]
                  items)]
      (if (pos? sm) [(/ sx sm) (/ sy sm) (/ sz sm)] [0.0 0.0 0.0]))
    [0.0 0.0 0.0]))

(defn- build-spatial-item
  "Project one entity into a spatial-index item."
  [world eid]
  (when (some? (ecs/get-component world eid c/radius))
    {:id           eid
     :position     (ecs/get-component world eid c/position)
     :mass         (ecs/get-component world eid c/mass)
     :radius       (ecs/get-component world eid c/radius)
     :matter-state (ecs/get-component world eid c/matter-state)
     :density      (ecs/get-component world eid c/density)
     :pressure     (ecs/get-component world eid c/pressure)
     :b-field      (ecs/get-component world eid c/b-field)}))

(defn- build-grid-from-items
  "Build a uniform grid from `items` when non-empty."
  [items]
  (when (seq items)
    (let [aabb (sp/aabb-from-points (map :position items))
          side (max (sp/max-side aabb) 1.0)
          n (count items)
          cell-size (/ side (math/pow n (/ 1.0 3.0)))]
      (build-grid items cell-size))))

(defn spatial-index
  "Build one Barnes-Hut octree from ALL entities with position+mass and store it
   on the world at :genesis/spatial-tree. Also computes the snapshot's centre
   of mass and builds a uniform grid at :genesis/spatial-grid. Consumers filter
   query results by :matter-state as needed."
  [world]
  (let [items (->> (ecs/entities-with world c/position c/mass c/radius)
                   (par/par-mapv #(build-spatial-item world %))
                   (filterv some?))
        com (com-from-items items)
        treef (future (bh/build-tree items))
        grid (build-grid-from-items items)
        tree @treef]
    (assoc world
           :genesis/spatial-tree tree
           :genesis/spatial-grid grid
           :genesis/spatial-items items
           :genesis/frame-offset com)))

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

(defn nearest
  "`[distance id]` of the nearest item whose `:id` differs from `self-eid`.
   `[##Inf nil]` when the tree holds no other item. Branch-and-bound on the
   octree: a node is visited only while its AABB could still hold something
   closer than the best distance found so far. Children are visited unsorted;
   the sort cost usually outweighs the benefit for the smoothing-length queries
   SPH issues."
  [tree pos self-eid]
  (let [best    (volatile! Double/POSITIVE_INFINITY)
        best-id (volatile! nil)
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
                        (when (< d2 (double @best))
                          (vreset! best d2)
                          (vreset! best-id (:id b))))))
                  (doseq [c (:children node)]
                    (walk c)))))]
      (walk tree)
      [(math/sqrt (double @best)) @best-id])))

(defn nearest-dist
  "Distance from `pos` to the nearest item whose `:id` differs from `self-eid`.
   ##Inf when the tree holds no other item. See `nearest`."
  [tree pos self-eid]
  (first (nearest tree pos self-eid)))

;; --- Unified query wrappers ------------------------------------------------

(defn query-within-radius
  "Neighbors within distance `r` of `pos`, optionally filtered by `pred`.
   Uses the uniform grid if available on `world`, otherwise the Barnes–Hut tree."
  ([world pos r]
   (query-within-radius world pos r (constantly true)))
  ([world pos r pred]
   (if-let [grid (:genesis/spatial-grid world)]
     (grid-within-radius grid pos r pred)
     (within-radius (:genesis/spatial-tree world) pos r pred))))

(defn query-nearest
  "`[distance id]` of the nearest neighbor from `pos` excluding `self-eid`.
   The Barnes–Hut tree is kept for nearest-neighbor queries: its branch-and-bound
   traversal is consistently faster than expanding uniform-grid shells, especially
   when the smoothing-length regime needs a single nearest distance rather than a
   full neighbor set. Falls back to the uniform grid if no tree exists; returns
   `[##Inf nil]` when neither index is available."
  [world pos self-eid]
  (if-let [tree (:genesis/spatial-tree world)]
    (nearest tree pos self-eid)
    (if-let [grid (:genesis/spatial-grid world)]
      (grid-nearest grid pos self-eid)
      [Double/POSITIVE_INFINITY nil])))

(defn query-nearest-dist
  "Nearest-neighbor distance from `pos` excluding `self-eid`. See `query-nearest`."
  [world pos self-eid]
  (first (query-nearest world pos self-eid)))

;; --- Uniform grid (for short-range SPH/EM queries) -------------------------

(defn- item-key
  "Grid cell coordinates for `pos` given cell size `cs`."
  [cs pos]
  (let [[x y z] pos
        f (fn [v] (long (math/floor (/ (double v) cs))))]
    [(f x) (f y) (f z)]))

(defn build-grid
  "Build a uniform 3D grid from `items` (maps with :position and :id) using the
   supplied `cell-size`. Returns a map with `:cell-size`, `:cells` {[i j k] [items]},
   `:items`, and the inclusive occupied cell-index bounds `:cell-min`/`:cell-max`
   ([i j k] each). Empty input returns a grid with no cells and nil bounds.

   The bounds let `grid-within-radius` clamp its cell walk to occupied space so a
   huge query radius never iterates empty cells — while keeping the exact walk
   order, hence bit-identical results."
  [items cell-size]
  (let [cs (double cell-size)
        cells (reduce (fn [m item]
                        (update m (item-key cs (:position item))
                                (fnil conj []) item))
                      {}
                      items)
        ks (keys cells)]
    {:cell-size cs
     :cells cells
     :items items
     :cell-min (when (seq ks)
                 [(apply min (map #(nth % 0) ks))
                  (apply min (map #(nth % 1) ks))
                  (apply min (map #(nth % 2) ks))])
     :cell-max (when (seq ks)
                 [(apply max (map #(nth % 0) ks))
                  (apply max (map #(nth % 1) ks))
                  (apply max (map #(nth % 2) ks))])}))

(defn- item-within-radius?
  "True when `item` is within distance squared `r2` of `[px py pz]`."
  [r2 [px py pz] item]
  (let [bp (:position item)
        x (- px (double (nth bp 0)))
        y (- py (double (nth bp 1)))
        z (- pz (double (nth bp 2)))]
    (<= (+ (* x x) (* y y) (* z z)) r2)))

(defn- grid-clamp-range
  "Clamp the integer range [i-k, i+k] to the grid bounds [mn, mx]."
  [i k mn mx]
  [(max (- i k) mn) (min (+ i k) mx)])

(defn- collect-grid-range
  "Collect items in the clamped grid range within radius squared `r2` of `pos`."
  [cells r2 pos pred xlo xhi ylo yhi zlo zhi]
  (vec
   (for [x (range xlo (inc xhi))
         y (range ylo (inc yhi))
         z (range zlo (inc zhi))
         item (get cells [x y z])
         :when (and (pred item) (item-within-radius? r2 pos item))]
     item)))

(defn grid-within-radius
  "Every item within distance `r` of `pos` (inclusive) from a uniform `grid`.
   `pred` optionally filters items before the distance check. Includes self if
   self is within the radius; callers exclude by `:id` as needed."
  ([grid pos r]
   (grid-within-radius grid pos r (constantly true)))
  ([grid pos r pred]
   (let [cs (:cell-size grid)
         cells (:cells grid)
         cmin (:cell-min grid)
         cmax (:cell-max grid)]
     (if (or (nil? cmin) (empty? cells))
       []
       (let [r2 (* (double r) (double r))
             [ix iy iz] (item-key cs pos)
             k (max 0 (long (math/ceil (/ (double r) cs))))
             [xlo xhi] (grid-clamp-range ix k (nth cmin 0) (nth cmax 0))
             [ylo yhi] (grid-clamp-range iy k (nth cmin 1) (nth cmax 1))
             [zlo zhi] (grid-clamp-range iz k (nth cmin 2) (nth cmax 2))]
         (collect-grid-range cells r2 pos pred xlo xhi ylo yhi zlo zhi))))))

(defn- shell-indices
  "Generate cell indices on the Chebyshev shell of radius `k` around `[ix iy iz]`
   that fall inside the grid bounds `[xlo xhi] [ylo yhi] [zlo zhi]`."
  [ix iy iz k xlo xhi ylo yhi zlo zhi]
  (for [dx (range (- k) (inc k))
        dy (range (- k) (inc k))
        dz (range (- k) (inc k))
        :when (or (zero? k) (== k (max (abs dx) (abs dy) (abs dz))))
        :let [x (+ ix dx) y (+ iy dy) z (+ iz dz)]
        :when (and (<= xlo x xhi) (<= ylo y yhi) (<= zlo z zhi))]
    [x y z]))

(defn- update-nearest!
  "Update `best` and `best-id` volatiles if `body` is closer than the current best."
  [best best-id pos self-eid body]
  (when (not= (:id body) self-eid)
    (let [[px py pz] pos
          bp (:position body)
          dx (- px (double (nth bp 0)))
          dy (- py (double (nth bp 1)))
          dz (- pz (double (nth bp 2)))
          d2 (+ (* dx dx) (* dy dy) (* dz dz))]
      (when (< d2 (double @best))
        (vreset! best d2)
        (vreset! best-id (:id body))))))

(defn grid-nearest
  "`[distance id]` of the nearest item whose `:id` differs from `self-eid`
   in a uniform `grid`. Expands the search cell-by-cell until a neighbor is found
   or the grid is exhausted; returns `[##Inf nil]` when no other item exists."
  [grid pos self-eid]
  (let [cs (:cell-size grid)
        cells (:cells grid)
        [cmin-x cmin-y cmin-z] (:cell-min grid)
        [cmax-x cmax-y cmax-z] (:cell-max grid)
        [ix iy iz] (item-key cs pos)
        best (volatile! Double/POSITIVE_INFINITY)
        best-id (volatile! nil)
        max-k (max 0
                   (- ix cmin-x) (- cmax-x ix)
                   (- iy cmin-y) (- cmax-y iy)
                   (- iz cmin-z) (- cmax-z iz))]
    (loop [k 0]
      (when (<= k max-k)
        (let [min-dist-at-k (if (zero? k) 0.0 (* (dec k) cs))]
          (when (or (zero? k) (< min-dist-at-k (math/sqrt (double @best))))
            (let [xlo (max (- ix k) cmin-x) xhi (min (+ ix k) cmax-x)
                  ylo (max (- iy k) cmin-y) yhi (min (+ iy k) cmax-y)
                  zlo (max (- iz k) cmin-z) zhi (min (+ iz k) cmax-z)
                  covered? (and (== xlo cmin-x) (== xhi cmax-x)
                                (== ylo cmin-y) (== yhi cmax-y)
                                (== zlo cmin-z) (== zhi cmax-z))]
              (doseq [idx (shell-indices ix iy iz k xlo xhi ylo yhi zlo zhi)
                      body (get cells idx)]
                (update-nearest! best best-id pos self-eid body))
              (when-not (and covered? (nil? @best-id))
                (recur (inc k))))))))
    [(math/sqrt (double @best)) @best-id]))

(defn grid-nearest-dist
  "Distance from `pos` to the nearest item whose `:id` differs from `self-eid`
   in a uniform `grid`. See `grid-nearest`."
  [grid pos self-eid]
  (first (grid-nearest grid pos self-eid)))
