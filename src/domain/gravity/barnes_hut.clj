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
     :bodies bodies
     :mass   total
     :com    (if (pos? total)
               (sp/v* (reduce (fn [acc b]
                                 (sp/v+ acc (sp/v* (:position b) (:mass b))))
                               (sp/vec3 0.0 0.0 0.0)
                               bodies)
                      (/ 1.0 total))
               (sp/center bb))}))

(defn- internal-node [bb children mass com]
  {:type     :internal
   :aabb     bb
   :children children
   :mass     mass
   :com      com})

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
   :children (vec (repeat 8 nil))
   :mass     0.0
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
                                        (sp/aabb (sp/v- (:min child-bb) pad)
                                                 (sp/v+ (:max child-bb) pad))
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
                   (update :min #(sp/v+ % [-1e-6 -1e-6 -1e-6]))
                   (update :max #(sp/v+ % [1e-6 1e-6 1e-6])))
          tree (reduce insert-body-into-node (empty-internal bb) bodies)]
      (propagate-mass tree))))

;; --- Acceleration evaluation ------------------------------------------------

(def ^:const default-theta 0.5)
(def ^:const softening     1e-4)

(defn- accel-from-mass
  "Gravitational acceleration on a test body at position `pos`
   due to aggregate mass `mass` at center-of-mass `com`."
  [^double G pos mass com]
  (let [r   (sp/v- com pos)
        r2  (+ (sp/len2 r) (* softening softening))
        r3  (* r2 (Math/sqrt r2))
        scale (/ (* G (double mass)) r3)]
    (sp/v* r scale)))

(defn- traverse
  "Recursive Barnes–Hut traversal."
  [G theta pos acc node]
  (cond
    (nil? node) acc

    (leaf-node? node)
    (let [body-ids (set (map :id (:bodies node)))
          self-id  (:id (meta pos))]
      (reduce (fn [acc' body]
                (if (= (:id body) self-id)
                  acc'
                  (sp/v+ acc' (accel-from-mass G pos (:mass body) (:position body)))))
              acc
              (:bodies node)))

    (internal-node? node)
    (let [s (sp/max-side (:aabb node))
          d (sp/dist pos (:com node))]
      (if (or (zero? d) (< (/ s d) theta))
        (sp/v+ acc (accel-from-mass G pos (:mass node) (:com node)))
        (reduce (fn [a child] (traverse G theta pos a child))
                acc
                (:children node))))))

(defn acceleration
  "Compute gravitational acceleration on `body` from all bodies in `tree`.
   G     — gravitational constant
   theta — Barnes–Hut opening angle (default 0.5)"
  ([G tree body]
   (acceleration G default-theta tree body))
  ([G theta tree body]
   (let [pos (with-meta (:position body) {:id (:id body)})]
     (traverse G theta pos (sp/vec3 0.0 0.0 0.0) tree))))
