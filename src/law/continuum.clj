(ns law.continuum
  "Contracts for a localized continuum component, independent of renderer and I/O."
  (:require [malli.core :as m]))
(def region-schema
  "Finite plane-strain geometry, velocities, material and inventory."
  [:map [:ref [:vector [:tuple number? number?]]] [:x [:vector [:tuple number? number?]]]
   [:v [:vector [:tuple number? number?]]] [:tri [:vector [:tuple int? int? int?]]]
   [:damage [:vector number?]] [:heat [:vector number?]] [:material map?]
   [:projectile map?] [:stocks-kg map?] [:time number?] [:level int?]])
(def region? "Validate the localized physical field schema." (m/validator region-schema))
(defn validate-region! "Reject malformed finite regional state before seeding ECS." [s]
  (when-not (and (region? s) (= (count (:ref s)) (count (:x s)) (count (:v s)))
                 (= (count (:tri s)) (count (:damage s)) (count (:heat s))))
    (throw (ex-info "Invalid continuum region" {:state s}))) s)
