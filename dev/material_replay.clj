(ns material-replay
  "Replay recorded ECS snapshots through Truth's existing native renderer."
  (:require [clojure.edn :as edn] [clojure.java.io :as io]
            [infra.dev.window :as window] [infra.render :as render]
            [infra.camera :as camera]))

(defn present!
  "Wait for the simulation owner to publish the exact requested replay snapshot."
  [snapshot token]
  (reset! (:world-intents @window/service-state) (assoc snapshot :material/replay-token token))
  (loop [attempt 0]
    (when-not (= token (:material/replay-token @(:world @window/service-state)))
      (when (> attempt 400) (throw (ex-info "Replay publication timed out" {:token token})))
      (Thread/sleep 10) (recur (inc attempt))))
  (Thread/sleep 100))

(defn -main
  "Render actual snapshots and read back the native window without physics changes."
  [& [source out]]
  (let [files (->> (.listFiles (io/file source))
                   (filter #(re-matches #"frame-[0-9]+\.edn" (.getName %)))
                   (sort-by #(.getName %)) vec)
        files (vec (distinct (concat (take-nth 3 files) [(last files)])))
        initial (edn/read-string (slurp (first files)))
        w (atom initial)]
    (.mkdirs (io/file out))
    (try
      (window/start! w {:tick-fn identity :bodies-fn render/phase0-bodies+fields
                         :camera (camera/make-camera 1.0) :width 1280 :height 720 :volumetric? false})
      (swap! (:config @window/service-state) assoc :mode :fit-all :smoothing 0.0
             :manual-yaw -70.0 :manual-pitch -25.0 :ui/cursor-free? true)
      (loop [i 0]
        (when-not (:body-program @(:config @window/service-state))
          (when (> i 400) (throw (ex-info "Renderer timeout" {})))
          (Thread/sleep 100) (recur (inc i))))
      (doseq [[index file] (map-indexed vector files)]
        (let [snapshot (edn/read-string (slurp file))]
          (present! (assoc snapshot :material/view :composition) [index :composition])
          (window/take-screenshot! (format "%s/composition-%04d.png" out index)
                                   {:expected-world @(:world @window/service-state)})
          (when (zero? (mod index 3))
            (present! (assoc snapshot :material/view :damage) [index :damage])
            (window/take-screenshot! (format "%s/damage-%04d.png" out index)
                                     {:expected-world @(:world @window/service-state)})))
        (when (zero? (mod index 20)) (println :rendered index) (flush)))
      (finally (window/stop!) (shutdown-agents)))))
