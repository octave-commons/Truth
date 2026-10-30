(ns domain.material
  "Finite material contact forces and downstream aggregate observations."
  (:require [domain.ecs.core :as ecs] [domain.ecs.components :as c]
            [domain.ecs.registry :as reg] [shape.spatial :as sp]))

(defn cells
  "Sorted material cells projected from the existing ECS."
  [world]
  (mapv (fn [id] {:id id :p (ecs/get-component world id c/position)
                  :v (ecs/get-component world id c/velocity)
                  :m (ecs/get-component world id c/mass)
                  :r (ecs/get-component world id c/radius)
                  :cell (ecs/get-component world id c/material-cell)})
        (sort (ecs/entities-with world c/material-cell c/position c/velocity c/mass c/radius))))

(defn near-pairs
  "Spatial-hash neighbor pairs, deterministic and unique by entity id."
  [items reach]
  (if (empty? items) []
    (let [edge (* reach 2.0 (reduce max (map :r items)))
          key-of (fn [p] (mapv #(long (Math/floor (/ (double %) edge))) p))
          bins (group-by #(key-of (:p %)) items)
          shifts (for [i [-1 0 1] j [-1 0 1] k [-1 0 1]] [i j k])]
      (into [] (mapcat (fn [a]
                        (let [key (key-of (:p a))]
                          (for [shift shifts b (get bins (mapv + key shift))
                                :when (< (:id a) (:id b))
                                :let [d (sp/dist (:p a) (:p b))]
                                :when (< d (* reach (+ (:r a) (:r b))))]
                            [a b d])))) items))))

(defn contact-system
  "Emit central spring/dashpot forces, damping heat and retained contact damage."
  []
  {:id :material-contact :reads (reg/registry-reads :material-contact)
   :writes (reg/registry-writes :material-contact)
   :run (fn [world]
          (let [items (cells world) dt (double (:sim/dt world))
                omega (double (:material/contact-frequency world 0.08))
                zeta (double (:material/damping-ratio world 0.7))
                empty-ws {c/accel-material (into {} (map (juxt :id (constantly [0.0 0.0 0.0]))) items)
                          c/heat-material (into {} (map (juxt :id (constantly 0.0))) items)}
                ws (reduce
                    (fn [ws [a b distance]]
                      (let [d (max 1e-9 distance)
                            n (if (< distance 1e-9) [1.0 0.0 0.0] (sp/v* (sp/v- (:p b) (:p a)) (/ 1.0 d)))
                            mu (/ (* (:m a) (:m b)) (+ (:m a) (:m b)))
                            overlap (- (+ (:r a) (:r b)) distance)
                            vn (sp/dot (sp/v- (:v b) (:v a)) n)
                            stiffness (* mu omega omega)
                            damping (* 2.0 zeta mu omega)
                            spring (* stiffness overlap)
                            force (max 0.0 (- spring (* damping vn)))
                            heat (max 0.0 (* (- spring force) vn dt))]
                        (-> ws
                            (update-in [c/accel-material (:id a)] sp/v+ (sp/v* n (/ (- force) (:m a))))
                            (update-in [c/accel-material (:id b)] sp/v+ (sp/v* n (/ force (:m b))))
                            (update-in [c/heat-material (:id a)] + (* 0.5 heat))
                            (update-in [c/heat-material (:id b)] + (* 0.5 heat)))))
                    empty-ws (near-pairs items 1.0))]
            (assoc ws c/material-damage
                   (into {} (map (fn [{:keys [id m]}]
                                   [id (min 1.0 (+ (double (or (ecs/get-component world id c/material-damage) 0.0))
                                                  (/ (get-in ws [c/heat-material id]) (* m (double (:material/damage-energy-J-per-kg world 0.1))))))])) items))))})

(defn- connected [ids edges]
  (let [adj (reduce (fn [m [a b _]] (-> m (update (:id a) (fnil conj #{}) (:id b))
                                       (update (:id b) (fnil conj #{}) (:id a)))) {} edges)]
    (loop [left (set ids) groups []]
      (if (empty? left) groups
        (let [start (apply min left)
              group (loop [pending [start] seen #{}]
                      (if (empty? pending) seen
                        (let [id (peek pending) rest (pop pending)]
                          (if (seen id) (recur rest seen)
                            (recur (into rest (get adj id)) (conj seen id))))))]
          (recur (reduce disj left group) (conj groups group)))))))

(defn aggregate-system
  "Observe nearby clusters and binding/composition; labels never change dynamics."
  []
  {:id :material-aggregate :reads (reg/registry-reads :material-aggregate)
   :writes #{c/material-aggregate}
   :run (fn [world]
          (let [items (cells world) lookup (into {} (map (juxt :id identity)) items)
                groups (connected (map :id items) (near-pairs items 1.18))]
            {c/material-aggregate
             (into {} (mapcat
                       (fn [group]
                         (let [members (map lookup group) mass (reduce + (map :m members))
                               center (sp/v* (reduce sp/v+ [0.0 0.0 0.0] (map #(sp/v* (:p %) (:m %)) members)) (/ 1.0 mass))
                               velocity (sp/v* (reduce sp/v+ [0.0 0.0 0.0] (map #(sp/v* (:v %) (:m %)) members)) (/ 1.0 mass))
                               radius (reduce max (map #(+ (:r %) (sp/dist center (:p %))) members))
                               kinetic (reduce + (map #(* 0.5 (:m %) (let [dv (sp/v- (:v %) velocity)] (sp/dot dv dv))) members))
                               binding (/ (* 0.6 6.6743e-11 mass mass) (max 1.0 radius))
                               water (/ (reduce + (map #(get-in % [:cell :water-kg]) members)) mass)
                               bound? (and (> (count group) 1) (< kinetic binding))
                               label {:aggregate-id (apply min group) :cells (count group)
                                      :mass-kg mass :radius-m radius :bound? bound?
                                      :water-fraction water :kinetic-J kinetic :binding-proxy-J binding
                                      :kind (cond (not bound?) :dispersed-material
                                                  (> water 0.2) :icy-aggregate :else :asteroid-like)}]
                           (map (fn [id] [id label]) group))) groups))}))})

(defn regional-grid
  "Conservative cloud-in-cell observation on an Eulerian grid; never a second world.

   Eight neighboring grid cells receive barycentric shares of each material
   parcel's extensive quantities. The sparse map has no clipping boundary."
  [world edge]
  (when-not (and (number? edge) (pos? edge))
    (throw (ex-info "Regional grid edge must be positive" {:edge edge})))
  (reduce (fn [grid {:keys [id p m v cell]}]
            (let [g (mapv #(- (/ % edge) 0.5) p)
                  lo (mapv #(long (Math/floor %)) g)
                  fraction (mapv - g lo)
                  energy (double (or (ecs/get-component world id c/internal-energy) 0.0))
                  stocks (select-keys cell [:silicate-kg :iron-kg :water-kg])]
              (reduce (fn [grid bits]
                        (let [offset (mapv + lo bits)
                              weight (reduce * (mapv (fn [f b] (if (zero? b) (- 1.0 f) f)) fraction bits))]
                          (if (zero? weight) grid
                            (-> grid
                                (update-in [offset :mass-kg] (fnil + 0.0) (* weight m))
                                (update-in [offset :internal-J] (fnil + 0.0) (* weight energy))
                                (update-in [offset :momentum] #(sp/v+ (or % [0.0 0.0 0.0]) (sp/v* v (* weight m))))
                                (update-in [offset :stocks-kg] #(merge-with + (or % {})
                                                                            (into {} (map (fn [[k kg]] [k (* weight kg)])) stocks)))))))
                      grid (for [i [0 1] j [0 1] k [0 1]] [i j k]))))
          {} (cells world)))
