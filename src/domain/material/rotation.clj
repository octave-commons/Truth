(ns domain.material.rotation
  "Orientation update fragment owned by the existing physical-state integrator."
  (:require [domain.ecs.core :as ecs] [domain.ecs.components :as c]
            [shape.rigid :as rigid]))

(defn orientation-ws
  "Integrate the orientation of rotating coarse packets, retaining world-frame spin."
  [world dt]
  {c/material-orientation
   (into {} (keep (fn [id]
                    (let [packet (ecs/get-component world id c/material-packet)]
                      (when (:rotation? packet)
                        [id (rigid/step (ecs/get-component world id c/material-orientation)
                                        (:spin-angular-momentum packet) (:inverse-inertia packet) dt)]))))
         (ecs/entities-with world c/material-packet))})
