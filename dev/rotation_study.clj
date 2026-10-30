(ns rotation-study
  "Paired rotating-cache and 32 m resolution-sensitivity experiments."
  (:require [clojure.edn :as edn] [clojure.java.io :as io]
            [material-experiment :as exp] [adaptive-material :as adaptive]
            [domain.genesis :as genesis] [domain.ecs.core :as ecs]
            [domain.ecs.components :as c] [domain.material.resolution :as resolution]
            [law.material :as lm] [shape.spatial :as sp]))

(defn- center [items]
  (let [mass (reduce + (map :m items))]
    (sp/v* (reduce sp/v+ [0.0 0.0 0.0] (map #(sp/v* (:p %) (:m %)) items)) (/ 1.0 mass))))

(defn add-spin
  "Declare a small solid-rotation velocity perturbation around the material COM."
  [w omega]
  (let [items (resolution/observed-cells w) com (center items)]
    (reduce (fn [w {:keys [id p v]}]
              (ecs/put-component w id c/velocity (sp/v+ v (sp/cross omega (sp/v- p com))))) w items)))

(defn subdivide
  "Split each carrier into eight equal-volume subcells, with explicit parent provenance.

   Extensive material, heat and momentum remain the same. Newly resolved
   intra-cell gravity/contact geometry changes discrete potential energy."
  [w]
  (let [items (resolution/observed-cells w)
        templates (into {} (map (fn [{:keys [id]}] [id (ecs/get-components w id)])) items)
        empty-w (reduce ecs/despawn w (ecs/all-entities w))]
    (reduce
     (fn [w {:keys [id source-id p v cell m r energy damage]}]
       (let [edge (:edge-m cell)
             child (-> cell
                       (update :edge-m / 2.0)
                       (update :silicate-kg / 8.0) (update :iron-kg / 8.0) (update :water-kg / 8.0)
                       (assoc :parent-source-id source-id))]
         (lm/validate-cell! child)
         (reduce (fn [w offset]
                   (let [[w eid] (ecs/spawn w)]
                     (ecs/put-components w eid
                                         (assoc (select-keys (templates id) [c/density c/temperature c/composition])
                                                c/material-cell child c/mass (/ m 8.0) c/radius (/ r 2.0)
                                                c/body-kind :material-cell c/position (sp/v+ p offset) c/velocity v
                                                c/internal-energy (/ energy 8.0) c/material-damage damage
                                                c/material-thermal (lm/thermal-state child (/ energy 8.0))))))
                 w (for [x [-0.25 0.25] y [-0.25 0.25] z [-0.25 0.25]] (sp/v* [x y z] edge)))))
     empty-w items)))

(defn- run-branch! [world out phase]
  (.mkdirs (io/file out))
  (let [start (System/nanoTime) steps 480]
    (loop [w world i 0]
      (when (zero? (mod i 40)) (adaptive/snapshot! out (quot i 40) w phase))
      (if (= steps i)
        (let [seconds (/ (- (System/nanoTime) start) 1e9)]
          (spit (str out "/final-checkpoint.edn") (pr-str (adaptive/checkpoint w)))
          (spit (str out "/timing.json") (adaptive/json {:wall-s seconds :steps steps :dt-s (:sim/dt world)}))
          (println :complete phase seconds) (flush) w)
        (recur (genesis/tick-world w) (inc i))))))

(defn -main
  "Compare the same spinning material state across two cache models and two resolutions."
  [& [checkpoint out]]
  (.mkdirs (io/file out))
  (try
    (let [w (merge (exp/initial-world) (edn/read-string (slurp checkpoint)))
          id (first (ecs/entities-with w c/material-packet))
          base (-> (resolution/unpack w id)
                   (assoc :material/coarse-enabled? false :sim/dt 0.25 :sim/theta 0.5
                          :material/max-coarse-seconds 1e9 :material/drift-cell-fraction 1e9)
                   (add-spin [0.0 0.0 0.0002]))
          ids (vec (ecs/all-entities base))
          rotating (resolution/pack (assoc base :material/coarse-rotation? true) ids)
          translating (resolution/pack (assoc base :material/coarse-rotation? false) ids)
          finer (subdivide base)
          metadata {:origin :saved-adaptive-coarse-entry :spin-added-rad-s [0.0 0.0 0.0002]
                    :interval-s 120.0 :fine-dt-s 0.25 :barnes-hut-theta 0.5
                    :note "Forced shadow approximations beyond normal validity guards; not automatic admission."
                    :initial-64m (resolution/account base) :initial-32m (resolution/account finer)
                    :subdivision-energy-jump-J (- (:energy-J (resolution/account finer)) (:energy-J (resolution/account base)))}]
      (spit (str out "/study.json") (adaptive/json metadata))
      (run-branch! base (str out "/detailed-64m") "64 m detailed reference")
      (run-branch! rotating (str out "/rotating-64m") "64 m rotating coarse shadow")
      (run-branch! translating (str out "/translating-64m") "64 m translating coarse shadow")
      (run-branch! finer (str out "/detailed-32m") "32 m detailed sensitivity")
      (println :study-complete))
    (finally (shutdown-agents))))
