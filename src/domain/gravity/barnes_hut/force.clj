(ns domain.gravity.barnes-hut.force
  "Barnes–Hut gravitational force evaluation in 3D.
   - acceleration: G θ tree body -> vec3 acceleration on body
   - acceleration-for-soa: G θ softening cutoff soa self-id -> {eid [ax ay az]}"
  (:require
   [clojure.math :as math]
   [domain.ecs.parallel :as par]
   [domain.gravity.barnes-hut.tree :as tree]))

;; --- Acceleration evaluation ------------------------------------------------

(def ^:const default-theta 0.5)
(def ^:const default-softening 1e-4)
;; Plummer softening length. Tiny by default (point masses), but a
;; self-gravitating gas cloud must pass a softening comparable to the
;; inter-particle spacing, or close encounters fling particles to infinity
;; (the "jitter"/ejection you see). Callers pass it via `acceleration`.

(defn- traverse-soa-idx
  "Explicit-stack Barnes-Hut traversal reading source bodies from SoA arrays
   via leaf indices. No id->idx map or body maps are allocated. Pairs closer
   than the cutoff radius contribute zero acceleration."
  [G soft2 cutoff2 theta2 px py pz self-idx ^doubles mass-arr
   ^doubles px-arr ^doubles py-arr ^doubles pz-arr node]
  (if (nil? node)
    [0.0 0.0 0.0]
    (let [G       (double G)
          soft2   (double soft2)
          cutoff2 (double cutoff2)
          theta2  (double theta2)
          px      (double px)
          py      (double py)
          pz      (double pz)
          acc     (double-array 3)
          stack   (java.util.ArrayDeque.)]
      (.push stack node)
      (while (not (.isEmpty stack))
        (when-let [n (.pop stack)]
          (if (tree/leaf-node? n)
            (doseq [idx (:idxs n)
                    :when (not= idx self-idx)]
              (let [ii (int idx)
                    bx (aget px-arr ii)
                    by (aget py-arr ii)
                    bz (aget pz-arr ii)
                    dx (- bx px)
                    dy (- by py)
                    dz (- bz pz)
                    raw-d2 (+ (* dx dx) (* dy dy) (* dz dz))]
                (when (>= raw-d2 cutoff2)
                  (let [d2    (+ raw-d2 soft2)
                        inv-r (* d2 (math/sqrt d2))
                        scale (if (pos? inv-r)
                                (/ (* G (aget mass-arr ii)) inv-r)
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
                (when (>= d2 cutoff2)
                  (let [d2s   (+ d2 soft2)
                        inv-r (* d2s (math/sqrt d2s))
                        scale (if (pos? inv-r)
                                (/ (* G (double (:mass n))) inv-r)
                                0.0)]
                    (aset acc 0 (+ (aget acc 0) (* dx scale)))
                    (aset acc 1 (+ (aget acc 1) (* dy scale)))
                    (aset acc 2 (+ (aget acc 2) (* dz scale)))))
                (doseq [child (reverse (:children n))
                        :when child]
                  (.push stack child)))))))
      [(aget acc 0) (aget acc 1) (aget acc 2)])))

(defn- traverse-fast
  "Scalar-accumulating Barnes–Hut traversal.

   Avoids per-node vector allocation and sqrt by keeping acceleration as three
   local doubles and comparing s² < θ²·d² at internal nodes. Pairs closer than
   the cutoff radius contribute zero acceleration, creating a gravitational
   dead zone around each body to suppress close-encounter numerical flings."
  [G soft2 cutoff2 theta2 px py pz self-id accx accy accz node]
  (if (nil? node)
    [accx accy accz]
    (if (tree/leaf-node? node)
      (let [[ax ay az]
            (reduce (fn [[ax ay az] body]
                      (if (= (:id body) self-id)
                        [ax ay az]
                        (let [bpos (:position body)
                              dx (- (double (nth bpos 0)) px)
                              dy (- (double (nth bpos 1)) py)
                              dz (- (double (nth bpos 2)) pz)
                              raw-d2 (+ (* dx dx) (* dy dy) (* dz dz))]
                          (if (< raw-d2 cutoff2)
                            [ax ay az]
                            (let [d2 (+ raw-d2 soft2)
                                  inv-r (* d2 (math/sqrt d2))
                                  scale (if (pos? inv-r)
                                          (/ (* (double G) (double (:mass body))) inv-r)
                                          0.0)]
                              [(+ ax (* dx scale))
                               (+ ay (* dy scale))
                               (+ az (* dz scale))])))))
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
          (if (< d2 cutoff2)
            [accx accy accz]
            (let [inv-r (* (+ d2 soft2) (math/sqrt (+ d2 soft2)))
                  scale (if (pos? inv-r)
                          (/ (* (double G) (double (:mass node))) inv-r)
                          0.0)]
              [(+ accx (* dx scale))
               (+ accy (* dy scale))
               (+ accz (* dz scale))]))
          (loop [children (:children node)
                 ax accx ay accy az accz]
            (if (seq children)
              (let [[nx ny nz] (traverse-fast G soft2 cutoff2 theta2 px py pz self-id ax ay az (first children))]
                (recur (rest children) nx ny nz))
              [ax ay az])))))))

(defn acceleration-for-soa
  "Gravitational acceleration for every entity in the SoA cache.

   Returns a map {eid [ax ay az]}. Builds an index-leaf Barnes–Hut tree
   directly from the SoA arrays (`tree/build-tree-from-soa`) and walks it once per
   target with the explicit-stack index traversal, reading source positions and
   masses straight from the arrays — no intermediate body maps or eid→index
   lookups are allocated (kanban/tasks/perf-60fps-parallel-tick.md).
   Drift-predicted arrays (:px-pred …) are preferred when present, so tree
   structure, multipole centroids, leaf sources, and targets all sit at the
   predicted positions. `self-id` is reserved for symmetry with `acceleration`
   and is ignored; each target skips its own index.

   Accepts a single options map: {:G :theta :softening :cutoff :soa :self-id}."
  [{:keys [G theta softening cutoff soa _self-id]
    :or   {theta default-theta softening default-softening cutoff 0.0}}]
  (let [soft2   (* (double softening) (double softening))
        cutoff2 (* (double cutoff) (double cutoff))
        theta2  (* (double theta) (double theta))
        eids    (:eids soa)
        ^doubles mass-arr (:mass soa)
        ^doubles px-arr (or (:px-pred soa) (:px soa))
        ^doubles py-arr (or (:py-pred soa) (:py soa))
        ^doubles pz-arr (or (:pz-pred soa) (:pz soa))
        n       (long (:n soa))
        tree    (tree/build-tree-from-soa soa)]
    (into {}
          (par/par-mapv
           (fn [i]
             (let [ii (int i)]
               [(nth eids ii)
                (traverse-soa-idx G soft2 cutoff2 theta2
                                  (aget px-arr ii) (aget py-arr ii) (aget pz-arr ii)
                                  ii mass-arr px-arr py-arr pz-arr tree)]))
           (range n)))))

(defn acceleration
  "Compute gravitational acceleration on `body` from all bodies in `tree`.

   G        — gravitational constant
   theta    — Barnes–Hut opening angle (default 0.5)
   softening — Plummer softening length (default tiny; pass cloud spacing)
   cutoff   — gravitational dead-zone radius; pairs closer than this contribute
              zero acceleration (default 0.0, no dead zone).

   Accepts a single options map: {:G :theta :softening :cutoff :tree :body}."
  [{:keys [G theta softening cutoff tree body]
    :or   {theta default-theta softening default-softening cutoff 0.0}}]
  (let [pos   (:position body)
        [px py pz] pos
        soft2 (* (double softening) (double softening))
        cutoff2 (* (double cutoff) (double cutoff))
        theta2 (* (double theta) (double theta))
        [ax ay az] (traverse-fast G soft2 cutoff2 theta2 px py pz (:id body) 0.0 0.0 0.0 tree)]
    [ax ay az]))
