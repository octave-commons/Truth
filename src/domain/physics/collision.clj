(ns domain.physics.collision
  "Broad-phase bounding-sphere collision detection system.
   Emits :event/collision events — does NOT mutate state directly.
   Response is handled by registered event handlers.

   Detection: two entities collide when
     dist(posA, posB) <= radiusA + radiusB

   Broad phase is a uniform spatial hash: bodies are bucketed into a grid whose
   cell side is twice the largest body radius, so any overlapping pair lands in
   the same or an adjacent cell. This is ~O(n) for a well-distributed cloud
   (thousands of accreting gas particles), versus the naive O(n²) all-pairs scan."
  (:require
    [domain.ecs.core       :as ecs]
    [domain.ecs.components :as c]
    [domain.ecs.event      :as event]
    [shape.spatial         :as sp]))

(defn- collidable-bodies
  "Project world into vec of [eid position radius velocity] for all entities
   that have position, radius, mass, and velocity components."
  [world]
  (->> (ecs/all-of world c/position c/radius c/mass c/velocity)
       (mapv (fn [[eid comps]]
               [eid (comps c/position) (double (comps c/radius)) (comps c/velocity)]))))

(defn- cell-of
  [^double cell-size [x y z]]
  [(long (Math/floor (/ (double x) cell-size)))
   (long (Math/floor (/ (double y) cell-size)))
   (long (Math/floor (/ (double z) cell-size)))])

(def ^:private neighbor-offsets
  (vec (for [dx [-1 0 1] dy [-1 0 1] dz [-1 0 1]] [dx dy dz])))

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

(defn- swept-sphere-overlap?
  "Exact continuous collision test for two spheres moving at constant velocity
   over dt. Returns true if their surfaces touch or cross during [0, dt]."
  [pos-a vel-a rad-a pos-b vel-b rad-b dt]
  (let [p  (sp/v- pos-b pos-a)
        v  (sp/v- vel-b vel-a)
        R  (+ rad-a rad-b)
        pp (sp/len2 p)
        vv (sp/len2 v)
        pv (sp/dot p v)]
    (cond
      ;; Already overlapping at t=0 (caller may also check this)
      (<= pp (* R R)) true
      ;; No relative motion; already tested above
      (<= vv 1e-30) false
      :else
      (let [disc (- (* pv pv) (* vv (- pp (* R R))))]
        (when (>= disc 0.0)
          (let [sqrt-disc (Math/sqrt disc)
                t0        (/ (- (- pv) sqrt-disc) vv)
                t1        (/ (+ (- pv) sqrt-disc) vv)]
            ;; A root in [0, dt] means the spheres touch during the step.
            (or (and (>= t0 0.0) (<= t0 dt))
                (and (>= t1 0.0) (<= t1 dt))
                (and (< t0 0.0) (> t1 dt)))))))))

(defn- detect-pairs
  "Return a seq of collision maps for overlapping pairs, found via a uniform
   spatial hash. Each unordered pair is emitted once (guarded by eid-a < eid-b).

   Swept-sphere continuous collision detection is used so fast bodies do not
   tunnel through each other between ticks."
  [bodies dt]
  (if (empty? bodies)
    []
    (let [max-r     (reduce max 0.0 (map #(nth % 2) bodies))
          max-speed (reduce max 0.0 (map #(sp/len (nth % 3)) bodies))
          ;; Cell size must cover the largest body and the furthest any body can
          ;; move in one step, so a tunneling pair lands in adjacent cells.
          cell-size (max (* 2.0 max-r) (* max-speed dt) 1.0)
          grid      (group-by (fn [b] (cell-of cell-size (nth b 1))) bodies)]
      (for [[[cx cy cz] cell-bodies] grid
            a   cell-bodies
            [dx dy dz] neighbor-offsets
            b   (get grid [(+ cx dx) (+ cy dy) (+ cz dz)])
            :when (< (long (nth a 0)) (long (nth b 0)))
            :let  [[_ pos-a rad-a vel-a] a
                   [_ pos-b rad-b vel-b] b
                   d (sp/dist pos-a pos-b)]
            :when (or (<= d (+ rad-a rad-b))
                      (swept-sphere-overlap? pos-a vel-a rad-a pos-b vel-b rad-b dt))]
        (pair-map a b d)))))

(defn collision-detection-system
  "ECS system: detects bounding-sphere overlaps, emits :event/collision
   for each pair. No state mutation — all response is via handlers."
  [world]
  (let [bodies (collidable-bodies world)
        dt     (double (or (:sim/dt world) 0.0))
        tick   (:tick world)
        pairs  (detect-pairs bodies dt)]
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
