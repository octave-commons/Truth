(ns material-live
  "Live native finite-material experiment using the standard ECS tick and renderer."
  (:require [material-experiment :as exp] [domain.genesis :as genesis]
            [domain.ecs.core :as ecs] [domain.ecs.components :as c]
            [infra.dev.window :as window] [infra.render :as render] [infra.camera :as camera]))

(defn advance
  "Run the declared cloud experiment, then introduce its measured fast projectile."
  [world]
  (if (and (= :collapse (:material/scenario world)) (>= (:tick world) 2400))
    (let [impact (exp/impact-world world)]
      (reduce (fn [w id] (ecs/put-component w id c/velocity [2.5 0.0 0.0]))
              (assoc impact :material/scenario :faster-impact)
              (range (:next-id world) (:next-id impact))))
    (genesis/tick-world world)))

(defn -main
  "Open the live material cloud and its impact continuation. Stop with Ctrl-C."
  [& _]
  (try
    (window/start! (atom (exp/initial-world))
                   {:tick-fn advance :bodies-fn render/phase0-bodies+fields
                    :camera (camera/make-camera 1.0) :width 1280 :height 720 :volumetric? false})
    (swap! (:config @window/service-state) assoc :mode :fit-all :smoothing 0.0
           :manual-yaw -70.0 :manual-pitch -25.0 :ui/cursor-free? true)
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(window/stop!)))
    (while (.isAlive (:thread @window/service-state)) (Thread/sleep 250))
    (finally (when @window/service-state (window/stop!)) (shutdown-agents))))
