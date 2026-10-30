(ns adaptive-replay
  "Replay recorded ECS material state with smooth/raw views in Truth's native window."
  (:require [clojure.edn :as edn] [clojure.java.io :as io]
            [infra.dev.window :as window] [infra.render :as render]
            [infra.camera :as camera] [material-replay :as replay]
            [domain.material.resolution :as resolution]))

(defn renderables
  "Existing cube pass for raw cells, or the scalar surface pass for the same state."
  [world]
  (let [base (remove #(or (= :voxel-cube (:render-mode %)) (= :material-coarse (:kind %)))
                     (render/phase0-bodies+fields world))]
    (if-let [path (:material/surface-file world)]
      (conj (vec base) {:entity :material-surface :position [0.0 0.0 0.0] :radius 0.5
                        :surface-file path :render-mode :material-surface})
      (into (vec base)
            (map (fn [{:keys [source-id p cell m cached?]}]
                   {:entity source-id :position (mapv #(/ % 1000.0) p)
                    :radius (/ (* 0.48 (:edge-m cell)) 1000.0)
                    :color (cond cached? [0.25 0.85 0.65]
                                 (> (/ (:water-kg cell) m) 0.2) [0.30 0.70 0.95]
                                 (> (/ (:iron-kg cell) m) 0.5) [0.88 0.47 0.20]
                                 :else [0.56 0.52 0.44])
                    :material :material-cell :kind :voxel :render-mode :voxel-cube}))
            (resolution/observed-cells world)))))

(defn -main
  "Render only the recorded frames with extracted meshes; capture GL framebuffer."
  [& [source out]]
  (.mkdirs (io/file out))
  (let [files (->> (.listFiles (io/file source))
                   (filter #(re-matches #"frame-[0-9]+\.composition\.mesh" (.getName %)))
                   (sort-by #(.getName %)) vec)
        first-file (str (.getAbsolutePath (first files)))
        first-edn (clojure.string/replace first-file ".composition.mesh" ".edn")
        w (atom (edn/read-string (slurp first-edn)))]
    (try
      (window/start! w {:tick-fn identity :bodies-fn renderables
                         :camera (camera/make-camera 1.0) :width 1280 :height 720 :volumetric? false})
      (swap! (:config @window/service-state) assoc :mode :fit-all :smoothing 0.0
             :manual-yaw -70.0 :manual-pitch -25.0 :ui/cursor-free? true)
      (loop [i 0]
        (when-not (:body-program @(:config @window/service-state))
          (when (> i 400) (throw (ex-info "Renderer timeout" {})))
          (Thread/sleep 100) (recur (inc i))))
      (doseq [[index file] (map-indexed vector files)]
        (let [path (.getAbsolutePath file)
              state (edn/read-string (slurp (clojure.string/replace path ".composition.mesh" ".edn")))
              settings (io/file (clojure.string/replace path ".composition.mesh" ".render.edn"))
              state (cond-> state (.exists settings)
                      (assoc :material/display-grid-m (:display-grid-m (edn/read-string (slurp settings)))))]
          (doseq [view [:composition :resolution]]
            (let [mesh-path (clojure.string/replace path ".composition.mesh" (str "." (name view) ".mesh"))]
              (replay/present! (assoc state :material/surface-file mesh-path :material/view view) [index view])
              (window/take-screenshot! (format "%s/%s-%04d.png" out (name view) index)
                                       {:expected-world @(:world @window/service-state)})))
          (do
            (replay/present! state [index :raw])
            (window/take-screenshot! (format "%s/raw-%04d.png" out index)
                                     {:expected-world @(:world @window/service-state)})))
        (when (zero? (mod index 10)) (println :rendered index) (flush)))
      (finally (window/stop!) (shutdown-agents)))))
