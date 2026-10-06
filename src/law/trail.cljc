(ns law.trail
  "Portable contracts for bounded observations of body motion."
  (:require [malli.core :as m]))

(def default-options
  "Simulation-time history bounds; design spark-flight-and-camera §6.1."
  {:capacity 64 :cadence 1.0e10 :horizon 6.3e11})

(def head-opacity
  "Maximum trail opacity, matching the existing ordinary line default."
  0.85)

(def segment-budget
  "Maximum complete trail segments emitted by one scene projection."
  4096)

(defn finite-number?
  "True for a finite number on either host."
  [x]
  (and (number? x) (< ##-Inf x ##Inf)))

(def finite-vector
  "Three finite world-space coordinates."
  [:tuple [:fn finite-number?] [:fn finite-number?] [:fn finite-number?]])

(def sample-schema
  "One actual observation in simulation seconds and the current world frame."
  [:map [:time [:fn finite-number?]] [:position finite-vector]])

(def trail-schema
  "Bounded observation history plus absolute deadline and skipped-slot count."
  [:map
   [:samples [:vector sample-schema]]
   [:next-at [:fn finite-number?]]
   [:skipped-deadlines [:and :int [:>= 0]]]])

(def valid-trail?
  "Compiled validator for a stored body trail."
  (m/validator trail-schema))

(def valid-observation?
  "Compiled validator for a frozen snapshot and its pending frame shift."
  (m/validator
   [:map
    [:time [:fn finite-number?]]
    [:dt [:and [:fn finite-number?] [:>= 0]]]
    [:position finite-vector]
    [:frame-offset finite-vector]]))

(def valid-options?
  "Compiled validator for finite positive history limits."
  (m/validator
   [:map
    [:capacity [:and :int [:> 0]]]
    [:cadence [:and [:fn finite-number?] [:> 0]]]
    [:horizon [:and [:fn finite-number?] [:> 0]]]]))

(def valid-sample?
  "Compiled validator for a published render head."
  (m/validator sample-schema))
