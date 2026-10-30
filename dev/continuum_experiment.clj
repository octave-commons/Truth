(ns continuum-experiment
  "Record three material-resolution runs through the existing ECS tick."
  (:require [clojure.java.io :as io] [domain.ecs.core :as ecs]
            [domain.ecs.components :as c] [domain.genesis :as genesis]
            [domain.continuum :as fe] [adaptive-material :as encoding]))

(defn initial-world
  "Seed a closed material region in the ECS, with no self-gravity or external work."
  [mode dt]
  (let [w (genesis/create-world {:gas-count 1})
        w (reduce ecs/despawn w (ecs/all-entities w))
        [w id] (ecs/spawn (assoc w :sim/dt dt :sim/G 0.0 :genesis/adaptive-pacing? false
                                :genesis/active true :continuum/region-id 0))]
    (assoc (ecs/put-component w id c/continuum-region (fe/create-region mode)) :continuum/region-id id)))

(defn save-frame!
  "Persist exact region state plus numerical observations from an ECS snapshot."
  [out index world]
  (let [state (ecs/get-component world (:continuum/region-id world) c/continuum-region)
        e (fe/elastic state)
        data {:observation (fe/observe state) :field (select-keys state [:ref :x :v :tri :damage :heat :projectile :mode :level])
              :pressure (:pressure e) :shear (:shear e) :jacobian (:jacobian e)}]
    (spit (format "%s/frame-%04d.json" out index) (encoding/json data))
    (spit (format "%s/frame-%04d.edn" out index)
          (pr-str (select-keys world [:components :tick :genesis/sim-time :continuum/region-id])))))

(defn run-case!
  "Run one matched impact at a declared mesh policy, duration and timestep."
  [out mode dt duration]
  (.mkdirs (io/file out))
  (loop [w (initial-world mode dt) i 0 frame 0 previous-transfers 0]
    (let [state (ecs/get-component w (:continuum/region-id w) c/continuum-region)
          transfers (count (:transfers state))
          sample? (or (zero? (mod i (max 1 (long (/ 10.0 dt))))) (> transfers previous-transfers) (>= (:time state) duration))]
      (when sample? (save-frame! out frame w))
      (when (zero? (mod i (max 1 (long (/ 100.0 dt)))))
        (println :progress mode dt (:time state) :elements (count (:tri state)) :min-J (:min-J (fe/observe state))) (flush))
      (if (>= (:time state) duration)
        (do (spit (str out "/final-checkpoint.edn") (pr-str state))
            (spit (str out "/transfers.json") (encoding/json (:transfers state)))
            (println :complete mode dt (select-keys (fe/observe state) [:energy-J :heat-J :maximum-damage :coarsen-rejections :transfers])) (flush))
        (recur (genesis/tick-world w) (inc i) (if sample? (inc frame) frame) transfers)))))

(defn -main
  "Record coarse/fine/adaptive at dt and dt/2. Optional pilot duration in seconds."
  [& [out duration]]
  (try
    (doseq [dt [0.5 0.25] mode [:coarse :fine :adaptive]]
      (run-case! (str out "/" (name mode) "-dt-" dt) mode dt (Double/parseDouble (or duration "900"))))
    (finally (shutdown-agents))))
