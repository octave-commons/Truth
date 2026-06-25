(ns infra.main
  "Entry point for the live 3D renderer demo."
  (:require
    [domain.ecs.components :as c]
    [domain.ecs.core       :as ecs]
    [infra.render          :as render]))

(defn- body
  "Helper to insert a celestial body into the world."
  [world eid kind mass radius pos vel]
  (-> world
      (ecs/put-component eid c/body-kind kind)
      (ecs/put-component eid c/mass     mass)
      (ecs/put-component eid c/radius   radius)
      (ecs/put-component eid c/position pos)
      (ecs/put-component eid c/velocity vel)))

(defn make-demo-world
  "Return an atom holding a small Sun/Earth/Moon world."
  []
  (atom
    (-> (ecs/empty-world)
        (body :sun   :body/star   1000.0 5.0  [0.0 0.0 0.0]  [0.0 0.0 0.0])
        (body :earth :body/planet 10.0   2.0  [50.0 0.0 0.0] [0.0 0.0 4.47])
        (body :moon  :body/moon   0.1    0.5  [58.0 0.0 0.0] [0.0 0.0 5.57]))))

(defn -main
  "Launch the renderer on a demo solar system.
   If a display is unavailable, renders an offscreen frame to /tmp/truth-view.png."
  [& _args]
  (println "Starting Gates of Truth demo...")
  (let [world (make-demo-world)
        path  "/tmp/truth-view.png"]
    (render/render-to-file world path)
    (println "Saved frame to" path)))
