(ns domain.gravity.barnes-hut
  "Barnes–Hut n-body gravity in 3D, using an octree over AABBs.
   - build-tree: bodies -> tree
   - acceleration: G θ tree body -> vec3 acceleration on body."
  (:require
   [shape.spatial :as sp]
   [domain.ecs.parallel :as par]))

;; --- Node representation ----------------------------------------------------

(def ^:private min-aabb-size 1.0e-9)

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
     :com    (if (pos? total)
               (sp/v* (reduce (fn [acc b]
                                (sp/v+ acc (sp/v* (:position b) (:mass b))))
                              (sp/vec3 0.0 0.0 0.0)
                              bodies)
                      (/ 1.0 total))
               (sp/center bb))}))

(defn- _internal-node [bb children mass com]
  {:type     :internal
   :aabb     bb
   :aabb-side (sp/max-side bb)
   :children children
   :mass     mass
   :com      com})

(defn- node-max-radius [node] (if node (double (or (:max-radius node) 0.0)) 0.0))

(defn internal-node? [node] (= (:type node) :internal))
(defn leaf-node?     [node] (= (:type node) :leaf))

;; --- Tree building ----------------------------------------------------------

(defn- bounding-aabb-for-bodies
  [bodies]
  (sp/aabb-from-points (map :position bodies)))

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
   :com      (sp/vec3 0.0 0.0 0.0)})

(defn- insert-body-into-node
  "Insert body into node, subdividing as needed."
  [node body]
  (let [pos (:position body)]
    (cond
      (nil? node)
      (let [pad [min-aabb-size min-aabb-size min-aabb-size]]
        (leaf-node (sp/aabb (sp/v- pos pad) (sp/v+ pos pad)) body))

      (leaf-node? node)
      (let [bb     (:aabb node)
            bodies (:bodies node)
            all    (conj bodies body)]
        (if (< (sp/max-side bb) min-aabb-size)
          (leaf-node bb all)
          (let [internal  (empty-internal bb)
                internal' (reduce insert-body-into-node internal bodies)]
            (insert-body-into-node internal' body))))

      (internal-node? node)
      (let [bb       (:aabb node)
            oct      (sp/octant bb pos)
            idx      (octant-index oct)
            child    (get (:children node) idx)
            child-bb (sp/child-aabb bb oct)
            child'   (if (nil? child)
                       (let [pad      [min-aabb-size min-aabb-size min-aabb-size]
                             child-bb (if (< (sp/max-side child-bb) min-aabb-size)
                                        (sp/aabb (sp/v- (:aabb-min child-bb) pad)
                                                 (sp/v+ (:aabb-max child-bb) pad))
                                        child-bb)]
                         (leaf-node child-bb body))
                       (insert-body-into-node child body))]
        (assoc node :children (assoc (:children node) idx child')))

      :else
      (throw (ex-info "insert-body-into-node: unknown node type"
                      {:kind ::unknown-node-type :node node})))))

(defn- node-mass [node] (if node (double (:mass node)) 0.0))
(defn- node-com  [node] (if node (:com node) (sp/vec3 0.0 0.0 0.0)))

(defn propagate-mass
  "Walk tree bottom-up, computing total mass and center of mass."
  [node]
  (cond
    (nil? node) nil

    (leaf-node? node)
    (leaf-node (:aabb node) (:bodies node))

    (internal-node? node)
    (let [children'  (mapv propagate-mass (:children node))
          total-mass (reduce #(+ %1 (node-mass %2)) 0.0 children')
          max-radius (reduce #(max %1 (node-max-radius %2)) 0.0 children')
          com        (if (zero? total-mass)
                       (sp/center (:aabb node))
                       (->> children'
                            (reduce (fn [acc child]
                                      (sp/v+ acc (sp/v* (node-com child)
                                                        (node-mass child))))
                                    (sp/vec3 0.0 0.0 0.0))
                            (#(sp/v* % (/ 1.0 total-mass)))))]
      (assoc node
             :children children'
             :mass     total-mass
             :aabb-side (sp/max-side (:aabb node))
             :max-radius max-radius
             :com      com))

    :else
    (throw (ex-info "propagate-mass: unknown node type"
                    {:node node}))))

(def ^:private parallel-build-threshold
  "Body count above which the octree is built by partitioning the bodies into
   the root's eight octants and building + propagating each subtree on its own
   thread. Below it the serial insert loop wins (future overhead)."
  512)

(defn- child-leaf-bb
  "The AABB the FIRST body inserted into an empty child of `bb` at `oct` gets —
   the same min-size padding rule as `insert-body-into-node`'s nil-child branch."
  [bb oct]
  (let [child-bb (sp/child-aabb bb oct)
        pad      [min-aabb-size min-aabb-size min-aabb-size]]
    (if (< (sp/max-side child-bb) min-aabb-size)
      (sp/aabb (sp/v- (:aabb-min child-bb) pad)
               (sp/v+ (:aabb-max child-bb) pad))
      child-bb)))

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
        children' (mapv deref futs)
        total-mass (reduce #(+ %1 (node-mass %2)) 0.0 children')
        max-radius (reduce #(max %1 (node-max-radius %2)) 0.0 children')
        com        (if (zero? total-mass)
                     (sp/center bb)
                     (->> children'
                          (reduce (fn [acc child]
                                    (sp/v+ acc (sp/v* (node-com child)
                                                      (node-mass child))))
                                  (sp/vec3 0.0 0.0 0.0))
                          (#(sp/v* % (/ 1.0 total-mass)))))]
    (assoc root
           :children  children'
           :mass      total-mass
           :aabb-side (sp/max-side bb)
           :max-radius max-radius
           :com       com)))

(defn build-tree
  "Build a Barnes–Hut octree from a seq of Body records."
  [bodies]
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

;; --- SoA-aware tree build + traversal ---------------------------------------

(defn- leaf-node-idx
  "Leaf node carrying integer indices into a SoA array rather than body maps.
   Mass/COM accumulation uses the same vector operations as `leaf-node` so the
   SoA path is bit-identical to the body-map path for the same positions."
  [bb idxs ^doubles mass-arr ^doubles px-arr ^doubles py-arr ^doubles pz-arr]
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
     :com    (if (pos? total)
               (sp/v* (reduce (fn [acc b]
                                (sp/v+ acc (sp/v* (:position b) (:mass b))))
                              (sp/vec3 0.0 0.0 0.0)
                              bodies)
                      (/ 1.0 total))
               (sp/center bb))}))

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

(defn- insert-idx-into-node
  "Insert index `idx` into node using position from SoA arrays."
  [node idx ^doubles px-arr ^doubles py-arr ^doubles pz-arr ^doubles mass-arr]
  (let [pos [(aget px-arr (int idx))
             (aget py-arr (int idx))
             (aget pz-arr (int idx))]]
    (cond
      (nil? node)
      (let [pad [min-aabb-size min-aabb-size min-aabb-size]]
        (leaf-node-idx (sp/aabb (sp/v- pos pad) (sp/v+ pos pad))
                       [idx] mass-arr px-arr py-arr pz-arr))

      (leaf-node? node)
      (let [bb   (:aabb node)
            idxs (:idxs node)
            all  (conj idxs idx)]
        (if (< (sp/max-side bb) min-aabb-size)
          (leaf-node-idx bb all mass-arr px-arr py-arr pz-arr)
          (let [internal  (empty-internal bb)
                internal' (reduce #(insert-idx-into-node %1 %2 px-arr py-arr pz-arr mass-arr)
                                  internal idxs)]
            (insert-idx-into-node internal' idx px-arr py-arr pz-arr mass-arr))))

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
                         (leaf-node-idx child-bb [idx] mass-arr px-arr py-arr pz-arr))
                       (insert-idx-into-node child idx px-arr py-arr pz-arr mass-arr))]
        (assoc node :children (assoc (:children node) oidx child')))

      :else
      (throw (ex-info "insert-idx-into-node: unknown node type"
                      {:kind ::unknown-node-type :node node})))))

(defn- propagate-mass-idx
  "Recompute mass/COM for a tree whose leaves carry SoA indices."
  [node ^doubles mass-arr ^doubles px-arr ^doubles py-arr ^doubles pz-arr]
  (cond
    (nil? node) nil

    (leaf-node? node)
    (leaf-node-idx (:aabb node) (:idxs node) mass-arr px-arr py-arr pz-arr)

    (internal-node? node)
    (let [children'  (mapv #(propagate-mass-idx % mass-arr px-arr py-arr pz-arr)
                           (:children node))
          total-mass (reduce #(+ %1 (node-mass %2)) 0.0 children')
          com        (if (zero? total-mass)
                       (sp/center (:aabb node))
                       (->> children'
                            (reduce (fn [acc child]
                                      (sp/v+ acc (sp/v* (node-com child)
                                                        (node-mass child))))
                                    (sp/vec3 0.0 0.0 0.0))
                            (#(sp/v* % (/ 1.0 total-mass)))))]
      (assoc node
             :children children'
             :mass     total-mass
             :aabb-side (sp/max-side (:aabb node))
             :com      com))

    :else
    (throw (ex-info "propagate-mass-idx: unknown node type"
                    {:node node}))))

(defn- build-tree-parallel-idx
  "Parallel SoA tree build: partition indices by root octant and build each
   subtree in a future."
  [bb idxs ^doubles mass-arr ^doubles px-arr ^doubles py-arr ^doubles pz-arr]
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
                            (reduce #(insert-idx-into-node %1 %2 px-arr py-arr pz-arr mass-arr)
                                    (leaf-node-idx (child-leaf-bb bb oct)
                                                   [(first grp)] mass-arr px-arr py-arr pz-arr)
                                    (rest grp))
                            mass-arr px-arr py-arr pz-arr))))
                     all-octants groups)
        children' (mapv deref futs)
        total-mass (reduce #(+ %1 (node-mass %2)) 0.0 children')
        com        (if (zero? total-mass)
                     (sp/center bb)
                     (->> children'
                          (reduce (fn [acc child]
                                    (sp/v+ acc (sp/v* (node-com child)
                                                      (node-mass child))))
                                  (sp/vec3 0.0 0.0 0.0))
                          (#(sp/v* % (/ 1.0 total-mass)))))]
    (assoc root
           :children  children'
           :mass      total-mass
           :aabb-side (sp/max-side bb)
           :com       com)))

(defn build-tree-from-soa
  "Build a Barnes–Hut octree directly from the `:genesis/physics-soa` arrays.
   Leaves store integer indices into the arrays instead of body maps, avoiding
   the intermediate body vector allocation of the body-map path
   (docs/specs/perf-60fps-parallel-tick.md)."
  [soa]
  (let [^doubles px-arr (or (:px-pred soa) (:px soa))
        ^doubles py-arr (or (:py-pred soa) (:py soa))
        ^doubles pz-arr (or (:pz-pred soa) (:pz soa))
        ^doubles mass-arr (:mass soa)
        n (:n soa)
        idxs (range n)]
    (cond
      (zero? n) nil
      (= 1 n) (leaf-node-idx (sp/aabb [(aget px-arr 0) (aget py-arr 0) (aget pz-arr 0)]
                                      [(aget px-arr 0) (aget py-arr 0) (aget pz-arr 0)])
                             [0] mass-arr px-arr py-arr pz-arr)
      :else
      (let [bb (-> (bounding-aabb-for-soa px-arr py-arr pz-arr n)
                   (update :aabb-min #(sp/v+ % [-1e-6 -1e-6 -1e-6]))
                   (update :aabb-max #(sp/v+ % [1e-6 1e-6 1e-6])))]
        (if (>= n parallel-build-threshold)
          (build-tree-parallel-idx bb idxs mass-arr px-arr py-arr pz-arr)
          (propagate-mass-idx
           (reduce #(insert-idx-into-node %1 %2 px-arr py-arr pz-arr mass-arr)
                   (empty-internal bb)
                   idxs)
           mass-arr px-arr py-arr pz-arr))))))

(defn- traverse-soa-idx
  "Explicit-stack Barnes-Hut traversal reading source bodies from SoA arrays
   via leaf indices. No id->idx map or body maps are allocated."
  [G soft2 theta2 px py pz self-idx ^doubles mass-arr
   ^doubles px-arr ^doubles py-arr ^doubles pz-arr node]
  (if (nil? node)
    [0.0 0.0 0.0]
    (let [G      (double G)
          soft2  (double soft2)
          theta2 (double theta2)
          px     (double px)
          py     (double py)
          pz     (double pz)
          acc    (double-array 3)
          stack  (java.util.ArrayDeque.)]
      (.push stack node)
      (while (not (.isEmpty stack))
        (when-let [n (.pop stack)]
          (if (leaf-node? n)
            (doseq [idx (:idxs n)
                    :when (not= idx self-idx)]
              (let [ii (int idx)
                    bx (aget px-arr ii)
                    by (aget py-arr ii)
                    bz (aget pz-arr ii)
                    dx (- bx px)
                    dy (- by py)
                    dz (- bz pz)
                    d2 (+ (* dx dx) (* dy dy) (* dz dz) soft2)
                    inv-r (* d2 (Math/sqrt d2))
                    scale (if (pos? inv-r)
                            (/ (* G (aget mass-arr ii)) inv-r)
                            0.0)]
                (aset acc 0 (+ (aget acc 0) (* dx scale)))
                (aset acc 1 (+ (aget acc 1) (* dy scale)))
                (aset acc 2 (+ (aget acc 2) (* dz scale)))))
            (let [com (:com n)
                  cx  (double (nth com 0))
                  cy  (double (nth com 1))
                  cz  (double (nth com 2))
                  dx  (- cx px)
                  dy  (- cy py)
                  dz  (- cz pz)
                  d2  (+ (* dx dx) (* dy dy) (* dz dz))
                  s   (double (:aabb-side n))
                  s2  (* s s)]
              (if (or (zero? d2) (< s2 (* theta2 d2)))
                (let [d2s   (+ d2 soft2)
                      inv-r (* d2s (Math/sqrt d2s))
                      scale (if (pos? inv-r)
                              (/ (* G (double (:mass n))) inv-r)
                              0.0)]
                  (aset acc 0 (+ (aget acc 0) (* dx scale)))
                  (aset acc 1 (+ (aget acc 1) (* dy scale)))
                  (aset acc 2 (+ (aget acc 2) (* dz scale))))
                (doseq [child (reverse (:children n))
                        :when child]
                  (.push stack child)))))))
      [(aget acc 0) (aget acc 1) (aget acc 2)])))
;; --- Acceleration evaluation ------------------------------------------------

(def ^:const default-theta 0.5)
(def ^:const default-softening 1e-4)
;; Plummer softening length. Tiny by default (point masses), but a
;; self-gravitating gas cloud must pass a softening comparable to the
;; inter-particle spacing, or close encounters fling particles to infinity
;; (the "jitter"/ejection you see). Callers pass it via `acceleration`.

(defn- traverse-fast
  "Scalar-accumulating Barnes–Hut traversal.

   Avoids per-node vector allocation and sqrt by keeping acceleration as three
   local doubles and comparing s² < θ²·d² at internal nodes."
  [G soft2 theta2 px py pz self-id accx accy accz node]
  (if (nil? node)
    [accx accy accz]
    (if (leaf-node? node)
      (let [[ax ay az]
            (reduce (fn [[ax ay az] body]
                      (if (= (:id body) self-id)
                        [ax ay az]
                        (let [bpos (:position body)
                              dx (- (double (nth bpos 0)) px)
                              dy (- (double (nth bpos 1)) py)
                              dz (- (double (nth bpos 2)) pz)
                              d2 (+ (* dx dx) (* dy dy) (* dz dz) soft2)
                              inv-r (* d2 (Math/sqrt d2))
                              scale (if (pos? inv-r)
                                      (/ (* (double G) (double (:mass body))) inv-r)
                                      0.0)]
                          [(+ ax (* dx scale))
                           (+ ay (* dy scale))
                           (+ az (* dz scale))])))
                    [accx accy accz]
                    (:bodies node))]
        [ax ay az])
      (let [com (:com node)
            [cx cy cz] com
            dx (- (double cx) px)
            dy (- (double cy) py)
            dz (- (double cz) pz)
            d2  (+ (* dx dx) (* dy dy) (* dz dz))
            s   (double (:aabb-side node))
            s2  (* s s)]
        (if (or (zero? d2) (< s2 (* theta2 d2)))
          (let [inv-r (* (+ d2 soft2) (Math/sqrt (+ d2 soft2)))
                scale (if (pos? inv-r)
                        (/ (* (double G) (double (:mass node))) inv-r)
                        0.0)]
            [(+ accx (* dx scale))
             (+ accy (* dy scale))
             (+ accz (* dz scale))])
          (loop [children (:children node)
                 ax accx ay accy az accz]
            (if (seq children)
              (let [[nx ny nz] (traverse-fast G soft2 theta2 px py pz self-id ax ay az (first children))]
                (recur (rest children) nx ny nz))
              [ax ay az])))))))

(defn- traverse-stack
  "Explicit-stack Barnes–Hut traversal with primitive scalar accumulators.

   Avoids the per-node vector allocation and recursive function-call overhead of
   `traverse-fast` by keeping a mutable `java.util.ArrayDeque` of pending nodes
   and three `double` accumulators in a small array. The tree is read-only; only
   local state is mutated."
  [G soft2 theta2 px py pz self-id tree]
  (if (nil? tree)
    [0.0 0.0 0.0]
    (let [G      (double G)
          soft2  (double soft2)
          theta2 (double theta2)
          px     (double px)
          py     (double py)
          pz     (double pz)
          acc    (double-array 3)
          stack  (java.util.ArrayDeque.)]
      (.push stack tree)
      (while (not (.isEmpty stack))
        (let [node (.pop stack)]
          (when node
            (if (leaf-node? node)
              (doseq [body (:bodies node)]
                (when (not= (:id body) self-id)
                  (let [bpos (:position body)
                        bx   (double (nth bpos 0))
                        by   (double (nth bpos 1))
                        bz   (double (nth bpos 2))
                        dx   (- bx px)
                        dy   (- by py)
                        dz   (- bz pz)
                        d2   (+ (* dx dx) (* dy dy) (* dz dz) soft2)
                        inv-r (* d2 (Math/sqrt d2))
                        scale (if (pos? inv-r)
                                (/ (* G (double (:mass body))) inv-r)
                                0.0)]
                    (aset acc 0 (+ (aget acc 0) (* dx scale)))
                    (aset acc 1 (+ (aget acc 1) (* dy scale)))
                    (aset acc 2 (+ (aget acc 2) (* dz scale))))))
              (let [com (:com node)
                    cx  (double (nth com 0))
                    cy  (double (nth com 1))
                    cz  (double (nth com 2))
                    dx  (- cx px)
                    dy  (- cy py)
                    dz  (- cz pz)
                    d2  (+ (* dx dx) (* dy dy) (* dz dz))
                    s   (double (:aabb-side node))
                    s2  (* s s)]
                (if (or (zero? d2) (< s2 (* theta2 d2)))
                  (let [d2s   (+ d2 soft2)
                        inv-r (* d2s (Math/sqrt d2s))
                        scale (if (pos? inv-r)
                                (/ (* G (double (:mass node))) inv-r)
                                0.0)]
                    (aset acc 0 (+ (aget acc 0) (* dx scale)))
                    (aset acc 1 (+ (aget acc 1) (* dy scale)))
                    (aset acc 2 (+ (aget acc 2) (* dz scale))))
                  (doseq [child (:children node)
                          :when child]
                    (.push stack child))))))))
      [(aget acc 0) (aget acc 1) (aget acc 2)])))

(defn acceleration-for-soa
  "Gravitational acceleration for every entity in the SoA cache.

   Returns a map {eid [ax ay az]}. Builds an index-leaf Barnes–Hut tree
   directly from the SoA arrays (`build-tree-from-soa`) and walks it once per
   target with the explicit-stack index traversal, reading source positions and
   masses straight from the arrays — no intermediate body maps or eid→index
   lookups are allocated (docs/specs/perf-60fps-parallel-tick.md).
   Drift-predicted arrays (:px-pred …) are preferred when present, so tree
   structure, multipole centroids, leaf sources, and targets all sit at the
   predicted positions. `self-id` is reserved for symmetry with `acceleration`
   and is ignored; each target skips its own index."
  [G theta softening soa _self-id]
  (let [soft2  (* (double softening) (double softening))
        theta2 (* (double theta) (double theta))
        eids   (:eids soa)
        ^doubles mass-arr (:mass soa)
        ^doubles px-arr (or (:px-pred soa) (:px soa))
        ^doubles py-arr (or (:py-pred soa) (:py soa))
        ^doubles pz-arr (or (:pz-pred soa) (:pz soa))
        n      (long (:n soa))
        tree   (build-tree-from-soa soa)]
    (into {}
          (par/par-mapv
           (fn [i]
             (let [ii (int i)]
               [(nth eids ii)
                (traverse-soa-idx G soft2 theta2
                                  (aget px-arr ii) (aget py-arr ii) (aget pz-arr ii)
                                  ii mass-arr px-arr py-arr pz-arr tree)]))
           (range n)))))

(defn acceleration
  "Compute gravitational acceleration on `body` from all bodies in `tree`.
   G        — gravitational constant
   theta    — Barnes–Hut opening angle (default 0.5)
   softening — Plummer softening length (default tiny; pass cloud spacing)"
  ([G tree body]
   (acceleration G default-theta default-softening tree body))
  ([G theta tree body]
   (acceleration G theta default-softening tree body))
  ([G theta softening tree body]
   (let [pos   (:position body)
         [px py pz] pos
         soft2 (* (double softening) (double softening))
         theta2 (* (double theta) (double theta))
         [ax ay az] (traverse-fast G soft2 theta2 px py pz (:id body) 0.0 0.0 0.0 tree)]
     [ax ay az])))
