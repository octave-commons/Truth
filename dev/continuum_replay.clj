(ns continuum-replay
  "Read exact ECS region snapshots through the existing native rendering path."
  (:require [clojure.edn :as edn] [clojure.java.io :as io] [clojure.string :as str]
            [infra.dev.window :as window] [infra.camera :as camera] [material-replay :as replay]))
(defn renderables "Surface mesh from the exact region state; projection and shading remain in infra.render." [w]
  (if-let [path (:continuum/surface-file w)]
    [{:entity :continuum-surface :position [0.0 0.0 0.0] :radius 0.5
      :render-mode :material-surface :surface-file path}] []))
(defn -main "Render and capture three exact-state physical views." [& [source out]]
  (.mkdirs (io/file out))
  (let [files (->> (.listFiles (io/file source)) (filter #(re-matches #"frame-[0-9]+\.edn" (.getName %)))
                   (sort-by #(.getName %)) vec)
        state (atom (edn/read-string (slurp (first files))))]
    (try
      (window/start! state {:tick-fn identity :bodies-fn renderables :camera (camera/make-camera 1.0)
                            :width 1280 :height 720 :volumetric? false})
      (swap! (:config @window/service-state) assoc :mode :fit-all :smoothing 0.0 :manual-yaw -90.0 :manual-pitch -12.0 :ui/cursor-free? true)
      (loop [i 0] (when-not (:body-program @(:config @window/service-state))
                    (when (> i 400) (throw (ex-info "Native renderer did not initialize" {})))
                    (Thread/sleep 100) (recur (inc i))))
      (doseq [[i file] (map-indexed vector files)]
        (let [w (edn/read-string (slurp file)) prefix (str/replace (.getAbsolutePath file) #"\.edn$" "")]
          (doseq [view [:pressure :damage :mesh]]
            (replay/present! (assoc w :continuum/surface-file (str prefix "." (name view) ".mesh")
                                   :continuum/view view) [i view])
            (window/take-screenshot! (format "%s/%s-%04d.png" out (name view) i)
                                     {:expected-world @(:world @window/service-state)})))
        (when (zero? (mod i 20)) (println :rendered i) (flush)))
      (finally (window/stop!) (shutdown-agents)))))
