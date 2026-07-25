(ns domain.gravity.barnes-hut.tree
  "Barnes–Hut octree construction in 3D over AABBs.
   - build-tree: bodies -> tree
   - build-tree-from-soa: SoA physics cache -> tree"
  (:require
   [shape.spatial :as sp]))

;; --- Node representation ----------------------------------------------------

(def ^:private min-aabb-size 1.0e-9)

(def ^:private max-insert-depth
  "Recursion-depth ceiling for octree insertion, independent of AABB size.
   Insertion normally stops subdividing once the node AABB shrinks below
   `min-aabb-size` — but a NaN or Infinite body position poisons that AABB
   (via `sp/center`/`sp/max-side`) so it never satisfies `< min-aabb-size`,
   turning the split into unbounded recursion and a StackOverflowError with
   no diagnostic content. 128 is far beyond the ~70 levels a legitimate
   double-precision domain (any finite span down to `min-aabb-size`) ever
   needs, so hitting it always means degenerate input, not a real tree."
  128)

(defn- safe-com
  "Compute center-of-mass from a weighted sum and total mass, returning the AABB
   center when the total mass is non-positive. Guards against the reciprocal of a
   tiny total mass overflowing to Infinity (a body ablated below ~1e-308 kg makes
   1.0/total overflow, which previously poisoned the COM and produced NaN
   accelerations in the Barnes–Hut walker)."
  [weighted-sum total bb]
  (if (pos? total)
    (let [inv-total (/ 1.0 total)]
      (if (Double/isFinite inv-total)
        (sp/v* weighted-sum inv-total)
        (sp/center bb)))
    (sp/center bb)))

(defn- leaf-node
  [bb body-or-bodies]
  (let [bodies (if (sequential? body-or-bodies)
                 (vec body-or-bodies)
                 [body-or-bodies])
        total  (double (reduce + (map :mass bodies)))]
    {:type   :leaf
     :aabb   bb
     :aabb-side (sp/max-side bb)
     :bodies bodies
     :mass   total
     ;; Largest body radius in this node, for fixed-radius neighbour/overlap
     ;; queries (e.g. collision broad-phase). 0.0 when bodies carry no :radius
     ;; (gravity, which ignores this field).
     :max-radius (double (reduce max 0.0 (map #(double (or (:radius %) 0.0)) bodies)))
     ;; Largest member softening length ε (law/body-softening), for the
     ;; per-pair dead-zone/softening at node acceptance. -1.0 = no member
     ;; carries species ε → the traversal's legacy scalar softening applies.
     :max-eps (double (reduce max -1.0 (map #(double (or (:eps %) -1.0)) bodies)))
     :com    (safe-com (reduce (fn [acc b]
                                 (sp/v+ acc (sp/v* (:position b) (:mass b))))
                               (sp/vec3 0.0 0.0 0.0)
                               bodies)
                       total
                       bb)}))

(defn- _internal-node [bb children mass com]
  {:type     :internal
   :aabb     bb
   :aabb-side (sp/max-side bb)
   :children children
   :mass     mass
   :com      com})

(defn- node-max-radius [node] (if node (double (or (:max-radius node) 0.0)) 0.0))
(defn- node-max-eps [node] (if node (double (or (:max-eps node) -1.0)) -1.0))

(defn internal-node?
  "True if `node` is an internal Barnes-Hut tree node."
  [node] (= (:type node) :internal))

(defn leaf-node?
  "True if `node` is a leaf Barnes-Hut tree node."
  [node] (= (:type node) :leaf))

(defn- bounding-aabb-for-bodies
  [bodies]
  (sp/aabb-from-points (map :position bodies)))

(defn- assert-finite-positions!
  "Throws immediately, naming the offending body id/position, when any body
   has a NaN or Infinite position component. Left unchecked, such a position
   poisons the root AABB (min/max/center all propagate the NaN/Infinity), so
   `sp/max-side` on it is never `< min-aabb-size` — the depth cap in
   `insert-body-into-node` is what actually stops the recursion, but without
   this check the resulting crash (or degenerate leaf) gives no clue which
   body caused it."
  [bodies]
  (when-let [bad (first (remove (fn [b]
                                  (every? #(Double/isFinite (double %)) (:position b)))
                                bodies))]
    (throw (ex-info "barnes-hut: non-finite body position"
                    {:kind :domain.gravity.barnes-hut/non-finite-position
                     :id (:id bad)
                     :position (:position bad)}))))

(def ^:private all-octants
  [:octant/ppp :octant/ppm :octant/pmp :octant/pmm
   :octant/mpp :octant/mpm :octant/mmp :octant/mmm])

(def ^:private octant-index
  (zipmap all-octants (range 8)))

(defn- empty-internal
  [bb]
  {:type     :internal
   :aabb     bb
   :aabb-side (sp/max-side bb)
   :children (vec (repeat 8 nil))
   :mass     0.0
   :max-radius 0.0
   :max-eps -1.0
   :com      (sp/vec3 0.0 0.0 0.0)})

(defn- child-leaf-bb
  "The AABB the FIRST body inserted into an empty child of `bb` at `oct` gets:
   the child octant, padded to `min-aabb-size` when it would otherwise be
   degenerate.

   ONE definition of that padding rule, used by both the serial insert path
   (`insert-body-into-node`'s nil-child branch) and the parallel root dispatch
   (`build-tree-parallel`). The two must agree or the parallel build stops
   producing a tree equal to the serial one — which is exactly what
   `build-tree-parallel`'s docstring promises."
  [bb oct]
  (let [child-bb (sp/child-aabb bb oct)
        pad      [min-aabb-size min-aabb-size min-aabb-size]]
    (if (< (sp/max-side child-bb) min-aabb-size)
      (sp/aabb (sp/v- (:aabb-min child-bb) pad)
               (sp/v+ (:aabb-max child-bb) pad))
      child-bb)))

(defn- insert-body-into-node
  "Insert body into node, subdividing as needed."
  ([node body] (insert-body-into-node node body 0))
  ([node body depth]
   (let [pos (:position body)]
     (cond
       (nil? node)
       (let [pad [min-aabb-size min-aabb-size min-aabb-size]]
         (leaf-node (sp/aabb (sp/v- pos pad) (sp/v+ pos pad)) body))

       (leaf-node? node)
       (let [bb     (:aabb node)
             bodies (:bodies node)
             all    (conj bodies body)]
         (if (or (>= depth max-insert-depth) (< (sp/max-side bb) min-aabb-size))
           (leaf-node bb all)
           (let [internal  (empty-internal bb)
                 internal' (reduce #(insert-body-into-node %1 %2 (inc depth)) internal bodies)]
             (insert-body-into-node internal' body (inc depth)))))

       (internal-node? node)
       (let [bb       (:aabb node)
             oct      (sp/octant bb pos)
             idx      (octant-index oct)
             child    (get (:children node) idx)
             child'   (if (nil? child)
                        (leaf-node (child-leaf-bb bb oct) body)
                        (insert-body-into-node child body (inc depth)))]
         (assoc-in node [:children idx] child'))

       :else
       (throw (ex-info "insert-body-into-node: unknown node type"
                       {:kind ::unknown-node-type :node node}))))))

(defn- node-mass [node] (if node (double (:mass node)) 0.0))
(defn- node-com  [node] (if node (:com node) (sp/vec3 0.0 0.0 0.0)))

(defn- aggregated-node-fields
  "The mass/COM/bound fields an internal node takes from its `children` inside
   bounding box `bb`: total mass, mass-weighted centre of mass, and the max
   radius / softening over the subtree.

   ONE definition, walking the children in ONE order. `propagate-mass` (serial)
   and `build-tree-parallel` (parallel root) both fold it, which is what makes
   `build-tree-parallel`'s equality promise hold — the aggregation appeared
   verbatim in both and drifting either one would break the equality silently
   (jscpd; card kanban/tasks/static-analysis-jscpd-src-extractions.md)."
  [children bb]
  (let [total-mass (reduce #(+ %1 (node-mass %2)) 0.0 children)]
    {:children   children
     :mass       total-mass
     :aabb-side  (sp/max-side bb)
     :max-radius (reduce #(max %1 (node-max-radius %2)) 0.0 children)
     :max-eps    (reduce #(max %1 (node-max-eps %2)) -1.0 children)
     :com        (safe-com (reduce (fn [acc child]
                                     (sp/v+ acc (sp/v* (node-com child)
                                                       (node-mass child))))
                                   (sp/vec3 0.0 0.0 0.0)
                                   children)
                           total-mass
                           bb)}))

(defn propagate-mass
  "Walk tree bottom-up, computing total mass and center of mass."
  [node]
  (cond
    (nil? node) nil

    (leaf-node? node)
    (leaf-node (:aabb node) (:bodies node))

    (internal-node? node)
    (merge node (aggregated-node-fields (mapv propagate-mass (:children node))
                                        (:aabb node)))

    :else
    (throw (ex-info "propagate-mass: unknown node type"
                    {:node node}))))

(def ^:private parallel-build-threshold
  "Body count above which the octree is built by partitioning the bodies into
   the root's eight octants and building + propagating each subtree on its own
   thread. Below it the serial insert loop wins (future overhead)."
  512)

(defn- build-tree-parallel
  "Build the octree by partitioning `bodies` (order-preserving) into the root's
   eight octants and building each subtree in a future. Produces a tree EQUAL
   to the serial `reduce insert-body-into-node` + `propagate-mass` build: each
   octant's insertion sequence is the original body order restricted to that
   octant (which is exactly what the serial root dispatch does), and the root
   aggregation below mirrors `propagate-mass`'s internal-node branch, walking
   the children in the same order."
  [bb bodies]
  (let [root      (empty-internal bb)
        groups    (reduce (fn [gs b]
                            (let [i (octant-index (sp/octant bb (:position b)))]
                              (update gs i conj b)))
                          (vec (repeat 8 []))
                          bodies)
        futs      (mapv (fn [oct grp]
                          (future
                            (when (seq grp)
                              (propagate-mass
                               (reduce insert-body-into-node
                                       (leaf-node (child-leaf-bb bb oct) (first grp))
                                       (rest grp))))))
                        all-octants groups)
        children' (mapv deref futs)]
    (merge root (aggregated-node-fields children' bb))))

(defn build-tree
  "Build a Barnes–Hut octree from a seq of Body records."
  [bodies]
  (assert-finite-positions! bodies)
  (cond
    (empty? bodies) nil
    (= 1 (count bodies))
    (let [b (first bodies)
          pad [min-aabb-size min-aabb-size min-aabb-size]]
      (leaf-node (sp/aabb (sp/v- (:position b) pad)
                          (sp/v+ (:position b) pad))
                 b))
    :else
    (let [bb (-> (bounding-aabb-for-bodies bodies)
                 (update :aabb-min #(sp/v+ % [-1e-6 -1e-6 -1e-6]))
                 (update :aabb-max #(sp/v+ % [1e-6 1e-6 1e-6])))]
      (if (>= (count bodies) parallel-build-threshold)
        (build-tree-parallel bb bodies)
        (propagate-mass (reduce insert-body-into-node (empty-internal bb) bodies))))))

;; --- SoA-aware tree build ---------------------------------------------------

(defn- leaf-node-idx
  "Leaf node carrying integer indices into a SoA array rather than body maps.
   Mass/COM accumulation uses the same vector operations as `leaf-node` so the
   SoA path is bit-identical to the body-map path for the same positions.
   `eps-arr` is the per-entity softening array (nullable: caches built without
   species ε mark :max-eps -1.0 → legacy scalar softening at traversal)."
  [bb idxs ^doubles mass-arr ^doubles px-arr ^doubles py-arr ^doubles pz-arr ^doubles eps-arr]
  (let [idxs (vec idxs)
        bodies (mapv (fn [i]
                       (let [ii (int i)]
                         {:mass (aget mass-arr ii)
                          :position [(aget px-arr ii)
                                     (aget py-arr ii)
                                     (aget pz-arr ii)]}))
                     idxs)
        total (double (reduce + (map :mass bodies)))]
    {:type   :leaf
     :aabb   bb
     :aabb-side (sp/max-side bb)
     :idxs   idxs
     :mass   total
     :max-eps (if eps-arr
                (double (reduce (fn [m i] (max m (aget eps-arr (int i)))) -1.0 idxs))
                -1.0)
     :com    (safe-com (reduce (fn [acc b]
                                 (sp/v+ acc (sp/v* (:position b) (:mass b))))
                               (sp/vec3 0.0 0.0 0.0)
                               bodies)
                       total
                       bb)}))

(defn- bounding-aabb-for-soa
  "AABB around all positions in the SoA arrays (0..n-1)."
  [^doubles px-arr ^doubles py-arr ^doubles pz-arr n]
  (loop [i 0
         minx Double/POSITIVE_INFINITY
         miny Double/POSITIVE_INFINITY
         minz Double/POSITIVE_INFINITY
         maxx Double/NEGATIVE_INFINITY
         maxy Double/NEGATIVE_INFINITY
         maxz Double/NEGATIVE_INFINITY]
    (if (< i n)
      (let [x (aget px-arr i)
            y (aget py-arr i)
            z (aget pz-arr i)]
        (recur (inc i)
               (min minx x) (min miny y) (min minz z)
               (max maxx x) (max maxy y) (max maxz z)))
      (sp/aabb [minx miny minz] [maxx maxy maxz]))))

(defn- assert-finite-soa-positions!
  "SoA counterpart of `assert-finite-positions!` — scans the position arrays
   directly and names the offending eid/index, since the SoA path never
   builds body maps."
  [soa ^doubles px-arr ^doubles py-arr ^doubles pz-arr n]
  (dotimes [i n]
    (let [x (aget px-arr i) y (aget py-arr i) z (aget pz-arr i)]
      (when-not (and (Double/isFinite x) (Double/isFinite y) (Double/isFinite z))
        (throw (ex-info "barnes-hut: non-finite body position in SoA cache"
                        {:kind :domain.gravity.barnes-hut/non-finite-position
                         :eid (nth (:eids soa) i)
                         :index i
                         :position [x y z]}))))))

(defn- insert-idx-into-node
  "Insert index `idx` into node using position from SoA arrays."
  ([node idx px-arr py-arr pz-arr mass-arr eps-arr]
   (insert-idx-into-node node idx px-arr py-arr pz-arr mass-arr eps-arr 0))
  ([node idx ^doubles px-arr ^doubles py-arr ^doubles pz-arr ^doubles mass-arr ^doubles eps-arr depth]
   (let [pos [(aget px-arr (int idx))
              (aget py-arr (int idx))
              (aget pz-arr (int idx))]]
     (cond
       (nil? node)
       (let [pad [min-aabb-size min-aabb-size min-aabb-size]]
         (leaf-node-idx (sp/aabb (sp/v- pos pad) (sp/v+ pos pad))
                        [idx] mass-arr px-arr py-arr pz-arr eps-arr))

       (leaf-node? node)
       (let [bb   (:aabb node)
             idxs (:idxs node)
             all  (conj idxs idx)]
         (if (or (>= depth max-insert-depth) (< (sp/max-side bb) min-aabb-size))
           (leaf-node-idx bb all mass-arr px-arr py-arr pz-arr eps-arr)
           (let [internal  (empty-internal bb)
                 internal' (reduce #(insert-idx-into-node %1 %2 px-arr py-arr pz-arr mass-arr eps-arr (inc depth))
                                   internal idxs)]
             (insert-idx-into-node internal' idx px-arr py-arr pz-arr mass-arr eps-arr (inc depth)))))

       (internal-node? node)
       (let [bb       (:aabb node)
             oct      (sp/octant bb pos)
             oidx     (octant-index oct)
             child    (get (:children node) oidx)
             child-bb (sp/child-aabb bb oct)
             child'   (if (nil? child)
                        (let [pad      [min-aabb-size min-aabb-size min-aabb-size]
                              child-bb (if (< (sp/max-side child-bb) min-aabb-size)
                                         (sp/aabb (sp/v- (:aabb-min child-bb) pad)
                                                  (sp/v+ (:aabb-max child-bb) pad))
                                         child-bb)]
                          (leaf-node-idx child-bb [idx] mass-arr px-arr py-arr pz-arr eps-arr))
                        (insert-idx-into-node child idx px-arr py-arr pz-arr mass-arr eps-arr (inc depth)))]
         (assoc-in node [:children oidx] child'))

       :else
       (throw (ex-info "insert-idx-into-node: unknown node type"
                       {:kind ::unknown-node-type :node node}))))))

(defn- propagate-mass-idx
  "Recompute mass/COM/ε-max for a tree whose leaves carry SoA indices."
  [node ^doubles mass-arr ^doubles px-arr ^doubles py-arr ^doubles pz-arr ^doubles eps-arr]
  (cond
    (nil? node) nil

    (leaf-node? node)
    (leaf-node-idx (:aabb node) (:idxs node) mass-arr px-arr py-arr pz-arr eps-arr)

    (internal-node? node)
    (let [children'  (mapv #(propagate-mass-idx % mass-arr px-arr py-arr pz-arr eps-arr)
                           (:children node))
          total-mass (reduce #(+ %1 (node-mass %2)) 0.0 children')
          max-eps    (reduce #(max %1 (node-max-eps %2)) -1.0 children')
          com        (safe-com (reduce (fn [acc child]
                                         (sp/v+ acc (sp/v* (node-com child)
                                                           (node-mass child))))
                                       (sp/vec3 0.0 0.0 0.0)
                                       children')
                               total-mass
                               (:aabb node))]
      (assoc node
             :children children'
             :mass     total-mass
             :aabb-side (sp/max-side (:aabb node))
             :max-eps max-eps
             :com      com))

    :else
    (throw (ex-info "propagate-mass-idx: unknown node type"
                    {:node node}))))

(defn- build-tree-parallel-idx
  "Parallel SoA tree build: partition indices by root octant and build each
   subtree in a future."
  [bb idxs ^doubles mass-arr ^doubles px-arr ^doubles py-arr ^doubles pz-arr ^doubles eps-arr]
  (let [root   (empty-internal bb)
        groups (reduce (fn [gs i]
                         (let [pos [(aget px-arr (int i))
                                    (aget py-arr (int i))
                                    (aget pz-arr (int i))]
                               o (octant-index (sp/octant bb pos))]
                           (update gs o conj i)))
                       (vec (repeat 8 []))
                       idxs)
        futs   (mapv (fn [oct grp]
                       (future
                         (when (seq grp)
                           (propagate-mass-idx
                            (reduce #(insert-idx-into-node %1 %2 px-arr py-arr pz-arr mass-arr eps-arr)
                                    (leaf-node-idx (child-leaf-bb bb oct)
                                                   [(first grp)] mass-arr px-arr py-arr pz-arr eps-arr)
                                    (rest grp))
                            mass-arr px-arr py-arr pz-arr eps-arr))))
                     all-octants groups)
        children' (mapv deref futs)
        total-mass (reduce #(+ %1 (node-mass %2)) 0.0 children')
        max-eps    (reduce #(max %1 (node-max-eps %2)) -1.0 children')
        com        (safe-com (reduce (fn [acc child]
                                       (sp/v+ acc (sp/v* (node-com child)
                                                         (node-mass child))))
                                     (sp/vec3 0.0 0.0 0.0)
                                     children')
                             total-mass
                             bb)]
    (assoc root
           :children  children'
           :mass      total-mass
           :aabb-side (sp/max-side bb)
           :max-eps max-eps
           :com       com)))

(defn build-tree-from-soa
  "Build a Barnes–Hut octree directly from the `:genesis/physics-soa` arrays.
   Leaves store integer indices into the arrays instead of body maps, avoiding
   the intermediate body vector allocation of the body-map path
   (kanban/tasks/perf-60fps-parallel-tick.md)."
  [soa]
  (let [^doubles px-arr (or (:px-pred soa) (:px soa))
        ^doubles py-arr (or (:py-pred soa) (:py soa))
        ^doubles pz-arr (or (:pz-pred soa) (:pz soa))
        ^doubles mass-arr (:mass soa)
        ^doubles eps-arr (:eps soa)
        n (:n soa)
        idxs (range n)]
    (assert-finite-soa-positions! soa px-arr py-arr pz-arr n)
    (cond
      (zero? n) nil
      (= 1 n) (leaf-node-idx (sp/aabb [(aget px-arr 0) (aget py-arr 0) (aget pz-arr 0)]
                                      [(aget px-arr 0) (aget py-arr 0) (aget pz-arr 0)])
                             [0] mass-arr px-arr py-arr pz-arr eps-arr)
      :else
      (let [bb (-> (bounding-aabb-for-soa px-arr py-arr pz-arr n)
                   (update :aabb-min #(sp/v+ % [-1e-6 -1e-6 -1e-6]))
                   (update :aabb-max #(sp/v+ % [1e-6 1e-6 1e-6])))]
        (if (>= n parallel-build-threshold)
          (build-tree-parallel-idx bb idxs mass-arr px-arr py-arr pz-arr eps-arr)
          (propagate-mass-idx
           (reduce #(insert-idx-into-node %1 %2 px-arr py-arr pz-arr mass-arr eps-arr)
                   (empty-internal bb)
                   idxs)
           mass-arr px-arr py-arr pz-arr eps-arr))))))
