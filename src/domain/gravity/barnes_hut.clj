(ns domain.gravity.barnes-hut
  "Barnes–Hut n-body gravity in 3D, using an octree over AABBs.
   - build-tree: bodies -> tree
   - acceleration: G θ tree body -> vec3 acceleration on body."
  (:require
   [shape.spatial :as sp]))

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
    (let [bb   (-> (bounding-aabb-for-bodies bodies)
                   (update :aabb-min #(sp/v+ % [-1e-6 -1e-6 -1e-6]))
                   (update :aabb-max #(sp/v+ % [1e-6 1e-6 1e-6])))
          tree (reduce insert-body-into-node (empty-internal bb) bodies)]
      (propagate-mass tree))))

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

;; --- SoA-aware traversal ----------------------------------------------------

(defn- traverse-soa
  "Explicit-stack scalar Barnes–Hut traversal reading source bodies from SoA.

   Returns [ax ay az] for target coordinates (px,py,pz). `self-eid` is skipped
   at leaf nodes, as is any leaf body whose :id is not present in `id->idx`.
   Internal nodes use the node's aggregate :mass and :com. The tree itself is
   unchanged; only local stack and accumulator state is mutated."
  [G soft2 theta2 px py pz self-eid ^java.util.HashMap id->idx soa node]
  (if (nil? node)
    [0.0 0.0 0.0]
    (let [G      (double G)
          soft2  (double soft2)
          theta2 (double theta2)
          px     (double px)
          py     (double py)
          pz     (double pz)
          ^objects eids   (:eids soa)
          ^doubles mass   (:mass soa)
          ^doubles px-arr (:px soa)
          ^doubles py-arr (:py soa)
          ^doubles pz-arr (:pz soa)
          acc    (double-array 3)
          stack  (java.util.ArrayDeque.)]
      (.push stack node)
      (while (not (.isEmpty stack))
        (when-let [n (.pop stack)]
          (if (leaf-node? n)
            (doseq [body (:bodies n)]
              (let [bid (:id body)]
                (when (and (not= bid self-eid)
                           (.containsKey id->idx bid))
                  (let [idx   (int (.get id->idx bid))
                        bx    (aget px-arr idx)
                        by    (aget py-arr idx)
                        bz    (aget pz-arr idx)
                        dx    (- bx px)
                        dy    (- by py)
                        dz    (- bz pz)
                        d2    (+ (* dx dx) (* dy dy) (* dz dz) soft2)
                        inv-r (* d2 (Math/sqrt d2))
                        scale (if (pos? inv-r)
                                (/ (* G (aget mass idx)) inv-r)
                                0.0)]
                    (aset acc 0 (+ (aget acc 0) (* dx scale)))
                    (aset acc 1 (+ (aget acc 1) (* dy scale)))
                    (aset acc 2 (+ (aget acc 2) (* dz scale)))))))
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

(defn acceleration-for-soa
  "Gravitational acceleration for every entity in the SoA cache.

   Returns a map {eid [ax ay az]} computed by walking the Barnes–Hut tree once
   per target entity. Reads target positions and source positions/masses directly
   from the SoA arrays. `tree` must have been built from the same spatial items
   that produced `soa`. `self-id` is reserved for symmetry with `acceleration`
   and is ignored; each target skips its own eid."
  [G theta softening tree soa self-id]
  (let [soft2   (* (double softening) (double softening))
        theta2  (* (double theta) (double theta))
        eids    (:eids soa)
        ^doubles px-arr (:px soa)
        ^doubles py-arr (:py soa)
        ^doubles pz-arr (:pz soa)
        n       (:n soa)
        id->idx (java.util.HashMap. (int n))]
    (dotimes [i n]
      (.put id->idx (nth eids i) (Integer/valueOf i)))
    (loop [i 0
           acc (transient {})]
      (if (< i n)
        (let [eid (nth eids i)
              px  (aget px-arr i)
              py  (aget py-arr i)
              pz  (aget pz-arr i)
              [ax ay az] (traverse-soa G soft2 theta2 px py pz eid id->idx soa tree)]
          (recur (inc i) (assoc! acc eid [ax ay az])))
        (persistent! acc)))))

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
