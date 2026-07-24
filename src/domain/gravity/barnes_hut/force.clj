(ns domain.gravity.barnes-hut.force
  "Barnes–Hut gravitational force evaluation in 3D.
   - acceleration: G θ tree body -> vec3 acceleration on body
   - acceleration-for-soa: G θ softening soa self-id -> {eid [ax ay az]}

   Per-pair softening (kanban/tasks/compact-pair-softening.md): every body
   carries a species softening ε (law.stellar.orbital/body-softening — c/radius
   for resolved compact bodies, the world :sim/softening for gas/stateless).
   Each interaction — leaf pair or accepted node — uses ε_pair = max(ε_target,
   ε_source) with dead-zone 0.1·ε_pair. Bodies/nodes WITHOUT species ε
   (hand-built fixtures) fall back to the scalar `softening` argument, which
   reproduces the legacy scalar kernel bit-for-bit for gas."
  (:require
   [clojure.math :as math]
   [domain.ecs.parallel :as par]
   [domain.gravity.barnes-hut.tree :as tree]
   [law.stellar.orbital :as law-orbital]))

;; --- Acceleration evaluation ------------------------------------------------

(def ^:const default-theta 0.5)
(def ^:const default-softening 1e-4)
;; Plummer softening length. Tiny by default (point masses), but a
;; self-gravitating gas cloud must pass a softening comparable to the
;; inter-particle spacing, or close encounters fling particles to infinity
;; (the "jitter"/ejection you see). Callers pass it via `acceleration`.

(def ^:private ^:const cutoff-fraction2
  "(0.1)² — the pair dead-zone is (softening-cutoff-fraction · ε_pair)²."
  (* law-orbital/softening-cutoff-fraction law-orbital/softening-cutoff-fraction))

(defn- pair-eps
  "ε_pair = max of two candidate ε values, where a negative entry means
   'no species data' and resolves to the legacy scalar `soft`."
  [eps-a eps-b soft]
  (max (if (neg? eps-a) soft eps-a)
       (if (neg? eps-b) soft eps-b)))

(defn- traverse-soa-idx
  "Explicit-stack Barnes-Hut traversal reading source bodies from SoA arrays
   via leaf indices. No id->idx map or body maps are allocated. Each pair or
   accepted node uses ε_pair = max(ε_target, ε_source) with dead-zone
   0.1·ε_pair; a source index or node with no species ε (eps-arr nil, or node
   :max-eps < 0) resolves to the legacy scalar `soft`."
  [G soft theta2 px py pz self-idx self-eps ^doubles mass-arr
   ^doubles px-arr ^doubles py-arr ^doubles pz-arr ^doubles eps-arr node]
  (if (nil? node)
    [0.0 0.0 0.0]
    (let [G       (double G)
          soft    (double soft)
          theta2  (double theta2)
          px      (double px)
          py      (double py)
          pz      (double pz)
          self-eps (double self-eps)
          acc     (double-array 3)
          stack   (java.util.ArrayDeque.)]
      (.push stack node)
      (while (not (.isEmpty stack))
        (when-let [n (.pop stack)]
          (if (tree/leaf-node? n)
            (doseq [idx (:idxs n)
                    :when (not= idx self-idx)]
              (let [ii (int idx)
                    e  (pair-eps self-eps (if eps-arr (aget eps-arr ii) -1.0) soft)
                    soft2   (* e e)
                    cutoff2 (* cutoff-fraction2 e e)
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
                (let [e       (pair-eps self-eps (double (or (:max-eps n) -1.0)) soft)
                      soft2   (* e e)
                      cutoff2 (* cutoff-fraction2 e e)]
                  (when (>= d2 cutoff2)
                    (let [d2s   (+ d2 soft2)
                          inv-r (* d2s (math/sqrt d2s))
                          scale (if (pos? inv-r)
                                  (/ (* G (double (:mass n))) inv-r)
                                  0.0)]
                      (aset acc 0 (+ (aget acc 0) (* dx scale)))
                      (aset acc 1 (+ (aget acc 1) (* dy scale)))
                      (aset acc 2 (+ (aget acc 2) (* dz scale))))))
                (doseq [child (reverse (:children n))
                        :when child]
                  (.push stack child)))))))
      [(aget acc 0) (aget acc 1) (aget acc 2)])))

(defn- traverse-fast
  "Scalar-accumulating Barnes–Hut traversal.

   Avoids per-node vector allocation and sqrt by keeping acceleration as three
   local doubles and comparing s² < θ²·d² at internal nodes. Each pair or
   accepted node uses ε_pair = max(ε_target, ε_source) with dead-zone
   0.1·ε_pair — the gravitational dead zone suppresses close-encounter
   numerical flings at each species' own scale. A body or node without species
   :eps resolves to the legacy scalar `soft`."
  [G soft theta2 px py pz self-id self-eps accx accy accz node]
  (if (nil? node)
    [accx accy accz]
    (if (tree/leaf-node? node)
      (let [[ax ay az]
            (reduce (fn [[ax ay az] body]
                      (if (= (:id body) self-id)
                        [ax ay az]
                        (let [body-eps (if-let [e (:eps body)] (double e) -1.0)
                              e   (pair-eps self-eps body-eps soft)
                              soft2   (* e e)
                              cutoff2 (* cutoff-fraction2 e e)
                              bpos (:position body)
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
          (let [e   (pair-eps self-eps (double (or (:max-eps node) -1.0)) soft)
                soft2   (* e e)
                cutoff2 (* cutoff-fraction2 e e)]
            (if (< d2 cutoff2)
              [accx accy accz]
              (let [inv-r (* (+ d2 soft2) (math/sqrt (+ d2 soft2)))
                    scale (if (pos? inv-r)
                            (/ (* (double G) (double (:mass node))) inv-r)
                            0.0)]
                [(+ accx (* dx scale))
                 (+ accy (* dy scale))
                 (+ accz (* dz scale))])))
          (loop [children (:children node)
                 ax accx ay accy az accz]
            (if (seq children)
              (let [[nx ny nz] (traverse-fast G soft theta2 px py pz self-id self-eps ax ay az (first children))]
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

   Per-pair softening (kanban/tasks/compact-pair-softening.md): when the cache
   carries the :eps array (production `build-physics-soa` always does), each
   interaction uses ε_pair = max(ε_target, ε_source) with dead-zone 0.1·ε_pair.
   A cache without :eps (hand-built fixtures) runs the legacy scalar kernel
   with dead-zone 0.1·`softening` — byte-identical for gas.

   Accepts a single options map: {:G :theta :softening :soa :self-id}."
  [{:keys [G theta softening soa _self-id]
    :or   {theta default-theta softening default-softening}}]
  (let [soft    (double softening)
        theta2  (* (double theta) (double theta))
        eids    (:eids soa)
        ^doubles mass-arr (:mass soa)
        ^doubles eps-arr (:eps soa)
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
                (traverse-soa-idx G soft theta2
                                  (aget px-arr ii) (aget py-arr ii) (aget pz-arr ii)
                                  ii (if eps-arr (aget eps-arr ii) -1.0)
                                  mass-arr px-arr py-arr pz-arr eps-arr tree)]))
           (range n)))))

(defn acceleration
  "Compute gravitational acceleration on `body` from all bodies in `tree`.

   G        — gravitational constant
   theta    — Barnes–Hut opening angle (default 0.5)
   softening — LEGACY scalar Plummer length, applied to any body or tree node
              that carries no species :eps (default tiny; pass the world cloud
              spacing). Bodies projected by domain.orbital.system carry :eps
              from law.stellar.orbital/body-softening; the pair rule
              ε_pair = max(ε_i, ε_j) with dead-zone 0.1·ε_pair then governs
              (kanban/tasks/compact-pair-softening.md).

   Accepts a single options map: {:G :theta :softening :tree :body}."
  [{:keys [G theta softening tree body]
    :or   {theta default-theta softening default-softening}}]
  (let [pos   (:position body)
        [px py pz] pos
        soft  (double softening)
        self-eps (if-let [e (:eps body)] (double e) -1.0)
        theta2 (* (double theta) (double theta))
        [ax ay az] (traverse-fast G soft theta2 px py pz (:id body) self-eps 0.0 0.0 0.0 tree)]
    [ax ay az]))
