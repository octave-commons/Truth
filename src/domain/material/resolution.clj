(ns domain.material.resolution
  "Finite-time translating/rotating aggregate approximation with retained material history.

   Physics continues on ordinary ECS entities. Only lifecycle construction
   packs/unpacks entities. Cached microstate is retained, not reconstructed."
  (:require [domain.ecs.core :as ecs] [domain.ecs.components :as c]
            [domain.ecs.registry :as reg] [domain.material :as material]
            [law.material :as lm] [shape.spatial :as sp] [shape.rigid :as rigid]))

(defn- weighted [items key mass]
  (sp/v* (reduce sp/v+ [0.0 0.0 0.0] (map #(sp/v* (key %) (:m %)) items)) (/ 1.0 mass)))

(defn- active-cell [world item]
  (let [id (:id item)]
    (assoc item :source-id (or (ecs/get-component world id c/material-source-id) id)
           :energy (double (or (ecs/get-component world id c/internal-energy) 0.0))
           :damage (double (or (ecs/get-component world id c/material-damage) 0.0)))))

(defn observed-cells
  "Read active and cached cells once, for display/accounting only."
  [world]
  (->> (concat (map #(active-cell world %) (material/cells world))
               (mapcat (fn [id]
                         (let [p (ecs/get-component world id c/position)
                               v (ecs/get-component world id c/velocity)
                               packet (ecs/get-component world id c/material-packet)
                               rotating? (:rotation? packet)
                               q (if rotating?
                                   (or (ecs/get-component world id c/material-orientation)
                                       (throw (ex-info "Rotating material packet is missing orientation" {:id id})))
                                   [1.0 0.0 0.0 0.0])
                               omega (when rotating? (rigid/angular-velocity q (:spin-angular-momentum packet) (:inverse-inertia packet)))]
                           (map (fn [{:keys [source-id offset relative-velocity rotation-residual-velocity components]}]
                                  (let [r (if rotating? (rigid/rotate q offset) offset)
                                        dv (if rotating? (sp/v+ (sp/cross omega r) (rigid/rotate q rotation-residual-velocity)) relative-velocity)]
                                  {:id [id source-id] :source-id source-id :cached? true
                                   :p (sp/v+ p r) :v (sp/v+ v dv)
                                   :m (components c/mass) :r (components c/radius)
                                   :cell (components c/material-cell)
                                   :energy (components c/internal-energy)
                                   :damage (double (or (components c/material-damage) 0.0))}))
                                (:members packet))))
                       (ecs/entities-with world c/material-packet c/position c/velocity)))
       (sort-by :source-id) vec))

(defn- mechanical [world items]
  (let [soft (double (:sim/softening world 8.0))
        omega (double (:material/contact-frequency world 0.08))]
    (+ (reduce + 0.0 (map #(* 0.5 (:m %) (sp/dot (:v %) (:v %))) items))
       (reduce + 0.0 (for [a items b items :when (< (:source-id a) (:source-id b))]
                       (/ (* -6.6743e-11 (:m a) (:m b))
                          (Math/sqrt (+ (* soft soft) (Math/pow (sp/dist (:p a) (:p b)) 2))))))
       (reduce + 0.0 (for [a items b items :when (< (:source-id a) (:source-id b))
                          :let [overlap (- (+ (:r a) (:r b)) (sp/dist (:p a) (:p b)))]
                          :when (pos? overlap)]
                       (* 0.5 (/ (* (:m a) (:m b)) (+ (:m a) (:m b))) omega omega overlap overlap))))))

(defn account
  "Extensive diagnostics expanded from the single authoritative representation."
  [world]
  (let [items (observed-cells world)]
    {:mass-kg (reduce + 0.0 (map :m items))
     :water-kg (reduce + 0.0 (map #(get-in % [:cell :water-kg]) items))
     :elements-kg (apply merge-with + (map #(lm/element-masses (:cell %)) items))
     :momentum (reduce sp/v+ [0.0 0.0 0.0] (map #(sp/v* (:v %) (:m %)) items))
     :angular-momentum (reduce sp/v+ [0.0 0.0 0.0]
                               (map #(sp/cross (:p %) (sp/v* (:v %) (:m %))) items))
     :energy-J (+ (reduce + 0.0 (map :energy items)) (mechanical world items))
     :material-cells (count items) :active-cells (count (material/cells world))
     :coarse-bodies (count (ecs/entities-with world c/material-packet))
     :physics-bodies (count (ecs/entities-with world c/mass c/position c/velocity))}))

(defn pack
  "Replace selected active cells with one coarse ECS body; retain exact local state."
  [world ids]
  (let [items (filterv #(contains? (set ids) (:id %)) (observed-cells world))
        _ (when-not (= (count ids) (count items))
            (throw (ex-info "Cannot pack missing or duplicate material cells" {:ids ids})))
        mass (reduce + (map :m items)) center (weighted items :p mass)
        velocity (weighted items :v mass)
        radius (reduce max (map #(+ (:r %) (sp/dist center (:p %))) items))
        keep-keys [c/material-cell c/mass c/radius c/density c/internal-energy
                   c/temperature c/composition c/material-thermal c/material-damage]
        members (mapv (fn [{:keys [id p v source-id]}]
                        {:source-id source-id :offset (sp/v- p center)
                         :relative-velocity (sp/v- v velocity)
                         :components (select-keys (ecs/get-components world id) keep-keys)}) items)
        rotating? (boolean (:material/coarse-rotation? world))
        angular (reduce sp/v+ [0.0 0.0 0.0]
                        (map #(sp/cross (:offset %) (sp/v* (:relative-velocity %) (get-in % [:components c/mass]))) members))
        inv (when rotating? (rigid/inverse (rigid/inertia items center)))
        omega (when rotating? (rigid/matvec inv angular))
        members (if rotating?
                  (mapv #(assoc % :rotation-residual-velocity
                                  (sp/v- (:relative-velocity %) (sp/cross omega (:offset %)))) members)
                  members)
        packet (cond-> {:members members :packed-time (:genesis/sim-time world 0.0)
                :max-speed (reduce max (map #(sp/len ((if rotating? :rotation-residual-velocity :relative-velocity) %)) members))
                :max-accel (reduce max 0.0
                                   (map #(sp/len (sp/v+ (or (ecs/get-component world % c/accel-gravity) [0.0 0.0 0.0])
                                                       (or (ecs/get-component world % c/accel-material) [0.0 0.0 0.0]))) ids))
                :retained-mechanical-J (- (mechanical world items) (* 0.5 mass (sp/dot velocity velocity)))
                :resolution-m (reduce min (map #(get-in % [:cell :edge-m]) items))}
                 rotating? (assoc :rotation? true :inverse-inertia inv :spin-angular-momentum angular))
        _ (lm/validate-packet! packet)
        world (reduce ecs/despawn world ids)
        [world id] (ecs/spawn world)]
    (ecs/put-components world id
                        {c/material-packet packet c/body-kind :material-coarse
                         c/mass mass c/position center c/velocity velocity c/radius radius
                         c/internal-energy (reduce + (map :energy items))
                         c/temperature (/ (reduce + (map #(* (get-in % [:components c/mass]) (get-in % [:components c/temperature])) members)) mass)
                         c/material-orientation [1.0 0.0 0.0 0.0]})))

(defn unpack
  "Reactivate cached cells in the current COM frame, without duplicating stock."
  [world id]
  (let [packet (lm/validate-packet! (ecs/get-component world id c/material-packet))
        current (into {} (map (juxt :source-id identity))
                      (filter #(= id (first (:id %))) (filter :cached? (observed-cells world))))]
    (when-not packet (throw (ex-info "No coarse packet to reactivate" {:id id})))
    (reduce (fn [w {:keys [source-id offset relative-velocity components]}]
              (let [[w eid] (ecs/spawn w)]
                (ecs/put-components w eid
                                    (assoc components c/material-source-id source-id c/body-kind :material-cell
                                           c/position (:p (current source-id)) c/velocity (:v (current source-id))))))
            (ecs/despawn world id) (:members packet))))

(defn reactivation
  "Reason to leave the approximation; neighbor guard looks ahead before contact."
  [world id]
  (let [packet (ecs/get-component world id c/material-packet)
        elapsed (- (:genesis/sim-time world 0.0) (:packed-time packet))
        drift (+ (* elapsed (:max-speed packet)) (* 0.5 elapsed elapsed (:max-accel packet)))
        p (ecs/get-component world id c/position) v (ecs/get-component world id c/velocity)
        radius (ecs/get-component world id c/radius)
        threat (some (fn [other]
                       (when (not= id other)
                         (let [dp (sp/v- (ecs/get-component world other c/position) p)
                               dv (sp/v- (ecs/get-component world other c/velocity) v)
                               rr (+ radius (double (or (ecs/get-component world other c/radius) 0.0)))
                               speed2 (sp/dot dv dv)
                               look (double (:material/lookahead-s world 120.0))
                               tau (if (pos? speed2) (min look (max 0.0 (/ (- (sp/dot dp dv)) speed2))) 0.0)
                               closest (sp/len (sp/v+ dp (sp/v* dv tau)))]
                           (when (or (< (sp/len dp) (* 2.5 rr)) (< closest (* 1.5 rr))) other))))
                     (ecs/entities-with world c/position c/velocity c/mass))]
    (cond threat {:reason :approach :neighbor threat :drift-estimate-m drift}
          (>= elapsed (:material/max-coarse-seconds world 80.0)) {:reason :time-limit :drift-estimate-m drift}
          (> drift (* (:material/drift-cell-fraction world 0.25) (:resolution-m packet)))
          {:reason :displacement-estimate :drift-estimate-m drift}
          :else nil)))

(defn resolution-system
  "Classify validity on frozen state; request representation changes at lifecycle."
  []
  {:id :material-resolution :reads (reg/registry-reads :material-resolution)
   :writes (reg/registry-writes :material-resolution)
   :run
   (fn [world]
     (let [packets (ecs/entities-with world c/material-packet)
           groups (vals (group-by #(get-in world [:components c/material-aggregate % :aggregate-id])
                                  (map :id (material/cells world))))
           requests (into {} (keep (fn [id] (when-let [reason (reactivation world id)]
                                             [id (assoc reason :op :unpack)]))) packets)]
       (reduce
        (fn [ws ids]
          (let [leader (apply min ids)
                label (ecs/get-component world leader c/material-aggregate)
                rms (if label (Math/sqrt (/ (* 2.0 (:kinetic-J label)) (:mass-kg label))) 1e99)
                quiet? (and (:material/coarse-enabled? world)
                            (>= (count ids) (:material/min-coarse-cells world 16))
                            (:bound? label) (< rms (:material/quiet-rms-m-s world 0.04))
                            (< (/ (:kinetic-J label) (:binding-proxy-J label))
                               (:material/quiet-binding-ratio world 0.04)))
                seconds (if quiet? (+ (:sim/dt world)
                                      (double (or (ecs/get-component world leader c/material-quiet-time) 0.0))) 0.0)
                entering? (and quiet? (>= seconds (:material/quiet-seconds world 40.0)))
                entering? (and entering?
                               (let [candidate (pack world ids)
                                     proxy (dec (:next-id candidate))]
                                 (nil? (reactivation candidate proxy))))]
            (cond-> (assoc-in ws [c/material-quiet-time leader] seconds)
              entering? (assoc-in [c/material-resolution-request leader] {:op :pack :ids (vec ids) :reason :quiet}))))
        {c/material-resolution-request requests c/material-quiet-time {}}
        (filter #(some? (get-in world [:components c/material-aggregate (first %) :aggregate-id])) groups))))})

(defn materialize-requests
  "Apply disjoint representation requests at the existing lifecycle barrier."
  [world]
  (reduce (fn [w [id request]]
            (let [w (ecs/remove-component w id c/material-resolution-request)
                  before (account w)
                  after (case (:op request) :pack (pack w (:ids request)) :unpack (unpack w id))
                  receipt {:tick (:tick w) :time-s (:genesis/sim-time w 0.0)
                           :op (:op request) :reason (:reason request)
                           :before-bodies (:physics-bodies before)
                           :after-bodies (:physics-bodies (account after))
                           :mass-residual-kg (- (:mass-kg (account after)) (:mass-kg before))
                           :energy-residual-J (- (:energy-J (account after)) (:energy-J before))}]
              (update after :material/handoffs (fnil conj []) receipt)))
          world (sort-by key (get-in world [:components c/material-resolution-request]))))
