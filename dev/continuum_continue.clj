(ns continuum-continue
  "Resume serialized physical region state through the same ECS tick."
  (:require [clojure.edn :as edn] [clojure.java.io :as io] [continuum-experiment :as experiment]
            [domain.ecs.core :as ecs] [domain.ecs.components :as c] [domain.continuum :as fe]
            [domain.genesis :as genesis] [adaptive-material :as encoding]))
(defn -main "Extend every case from the retained checkpoint; do not overwrite earlier frames." [& [root end]]
  (try
    (doseq [dt [0.5 0.25] mode [:coarse :fine :adaptive]]
      (let [dir (str root "/" (name mode) "-dt-" dt)
            s (edn/read-string (slurp (str dir "/final-checkpoint.edn")))
            initial (experiment/initial-world mode dt) id (:continuum/region-id initial)
            initial (-> initial (ecs/put-component id c/continuum-region s)
                        (assoc :genesis/sim-time (:time s) :tick (long (/ (:time s) dt))))
            index (count (filter #(re-matches #"frame-[0-9]+\.json" (.getName %)) (.listFiles (io/file dir))))]
        (assert (= s (ecs/get-component initial id c/continuum-region)))
        (loop [w (genesis/tick-world initial) frame index last-count (count (:transfers s))]
          (let [s (ecs/get-component w id c/continuum-region)
                count-now (count (:transfers s))
                sample? (or (zero? (mod (:tick w) (long (/ 20 dt))))
                            (> count-now last-count) (>= (:time s) (Double/parseDouble end)))]
            (when sample? (experiment/save-frame! dir frame w))
            (if (>= (:time s) (Double/parseDouble end))
              (do (spit (str dir "/extended-final.edn") (pr-str s))
                  (spit (str dir "/extended-transfers.json") (encoding/json (:transfers s)))
                  (println :extended mode dt (select-keys (fe/observe s) [:nonrigid-rms-m-s :coarsen-rejections :transfers])) (flush))
              (recur (genesis/tick-world w) (if sample? (inc frame) frame) count-now))))))
    (finally (shutdown-agents))))
