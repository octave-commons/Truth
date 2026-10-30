(ns material-experiment
  "Run a finite-material debris patch through Truth's normal genesis tick."
  (:require [clojure.java.io :as io] [clojure.edn :as edn]
            [domain.ecs.core :as ecs] [domain.ecs.components :as c]
            [domain.genesis :as genesis] [domain.material :as material]
            [law.material :as lm] [shape.spatial :as sp]))

(defn add-cell
  "Seed finite carrier stocks and motion; no asteroid/comet/planet label."
  [world position velocity fractions edge temperature]
  (let [density (/ 1.0 (+ (/ (:silicate fractions 0.0) 3000.0)
                          (/ (:iron fractions 0.0) 7800.0) (/ (:water fractions 0.0) 917.0)))
        mass (* density edge edge edge)
        cell (lm/validate-cell! {:silicate-kg (* mass (:silicate fractions 0.0))
                                :iron-kg (* mass (:iron fractions 0.0))
                                :water-kg (* mass (:water fractions 0.0))
                                :edge-m edge :pressure-pa 1.0})
        [w id] (ecs/spawn world)
        energy (* (lm/heat-capacity cell) temperature)]
    (ecs/put-components w id
                        {c/material-cell cell c/mass mass c/radius (* 0.5 edge)
                         c/density density c/position position c/velocity velocity
                         c/body-kind :material-cell c/temperature temperature
                         c/internal-energy energy c/material-thermal (lm/thermal-state cell energy)
                         c/composition (into {} (map (fn [[el kg]] [el (/ kg mass)])) (lm/element-masses cell))})))

(defn initial-world
  "Cold, irregular material cloud in a local comoving patch; no stellar tides."
  []
  (let [base (genesis/create-world {:gas-count 1})
        base (reduce ecs/despawn base (ecs/all-entities base))
        base (assoc base :sim/dt 1.0 :sim/softening 8.0 :sim/theta 0.0
                    :genesis/adaptive-pacing? false :genesis/active true
                    :material/view-scale 1000.0 :material/contact-frequency 0.08
                    :material/damping-ratio 0.7 :material/scenario :collapse)
        rng (java.util.Random. 129)]
    (reduce (fn [w [i j k]]
              (let [p (mapv #(+ (* 105.0 %) (* 9.0 (- (.nextDouble rng) 0.5))) [i j k])
                    id (:next-id w)
                    f (cond (zero? (mod id 7)) {:iron 1.0}
                            (zero? (mod id 3)) {:water 0.65 :silicate 0.35}
                            :else {:silicate 1.0})]
                (add-cell w p (sp/v* p -0.0002) f 64.0 205.0)))
            base (for [i (range -2 3) j (range -2 3) k (range -2 3)
                       :when (<= (+ (* i i) (* j j) (* k k)) 6)] [i j k]))))

(defn observe
  "Actual stocks, momentum and internal/mechanical energies from a snapshot."
  [w]
  (let [items (material/cells w) mass (reduce + (map :m items))
        center (sp/v* (reduce sp/v+ [0.0 0.0 0.0] (map #(sp/v* (:p %) (:m %)) items)) (/ 1.0 mass))
        labels (vals (get-in w [:components c/material-aggregate]))
        groups (vals (into {} (map (juxt :aggregate-id identity)) labels))
        kinetic (reduce + (map #(* 0.5 (:m %) (sp/dot (:v %) (:v %))) items))
        potential (reduce + (for [a items b items :when (< (:id a) (:id b))]
                              (/ (* -6.6743e-11 (:m a) (:m b))
                                 (Math/sqrt (+ 64.0 (Math/pow (sp/dist (:p a) (:p b)) 2))))))
        spring (reduce + 0.0 (map (fn [[a b d]]
                                   (let [mu (/ (* (:m a) (:m b)) (+ (:m a) (:m b)))
                                         delta (- (+ (:r a) (:r b)) d)]
                                     (* 0.5 mu (Math/pow (:material/contact-frequency w) 2) delta delta)))
                                 (material/near-pairs items 1.0)))]
    {:tick (:tick w) :time-s (:genesis/sim-time w) :cells (count items)
     :mass-kg mass :center-m center
     :momentum (reduce sp/v+ [0.0 0.0 0.0] (map #(sp/v* (:v %) (:m %)) items))
     :elements-kg (apply merge-with + (map #(lm/element-masses (:cell %)) items))
     :water-kg (reduce + (map #(get-in % [:cell :water-kg]) items))
     :water-vapor-kg (reduce + 0.0 (map #(get % :water-vapor-kg 0.0) (vals (get-in w [:components c/material-thermal]))))
     :internal-J (reduce + (vals (get-in w [:components c/internal-energy])))
     :kinetic-J kinetic :gravity-J potential :spring-J spring
     :max-temperature-K (reduce max (vals (get-in w [:components c/temperature])))
     :radius-m (reduce max (map #(sp/dist center (:p %)) items))
     :largest-cluster (reduce max 1 (map :cells groups))
     :clusters (frequencies (map :kind groups))
     :damaged-cells (count (filter #(> (val %) 0.01) (get-in w [:components c/material-damage])))}))

(defn save-frame!
  "Persist renderable ECS columns, not interpolated or invented visual states."
  [out index w]
  (spit (format "%s/frame-%04d.edn" out index)
        (pr-str (select-keys w [:components :tick :genesis/sim-time :material/view-scale
                               :material/scenario :material/contact-frequency]))))

(defn run-segment!
  "Advance the normal genesis pipeline and sample observations and frames."
  [world out steps stride]
  (.mkdirs (io/file out))
  (loop [w world i 0]
    (when (zero? (mod i stride))
      (save-frame! out (quot i stride) w)
      (spit (str out "/observations.edn") (str (pr-str (observe w)) "\n") :append true))
    (when (zero? (mod i 250))
      (println :progress out i (select-keys (observe w) [:radius-m :largest-cluster :max-temperature-K])) (flush))
    (if (>= i steps)
      (do (spit (str out "/final.edn") (pr-str w)) w)
      (recur (genesis/tick-world w) (inc i)))))

(defn impact-world
  "Continue the assembled material, introducing a declared metallic projectile."
  [world]
  (let [center (:center-m (observe world))
        w (assoc world :sim/dt 0.25 :material/contact-frequency 0.08
                 :material/scenario :impact)]
    (reduce (fn [w offset]
              (add-cell w (sp/v+ center (sp/v+ [(- (+ 90.0 (:radius-m (observe world)))) 20.0 35.0] offset))
                        [0.8 0.0 0.0] {:iron 1.0} 40.0 205.0))
            w [[0.0 0.0 0.0] [-40.0 0.0 0.0] [0.0 40.0 0.0]])))

(defn -main
  "Run the material cloud then its impact continuation."
  [& [out steps]]
  (try
    (let [n (Long/parseLong (or steps "3000"))
          cloud (initial-world)
          formed (run-segment! cloud (str out "/collapse") n 30)]
      (run-segment! (impact-world formed) (str out "/impact") 1200 12)
      (println :complete out))
    (finally (shutdown-agents))))
