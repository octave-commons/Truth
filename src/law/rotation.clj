(ns law.rotation
  "Contracts for spark rotation; design spark-flight-and-camera.md §3.2."
  (:require
   [clojure.math :as math]
   [malli.core :as m]
   [law.field.schema :as field]))

(def identity-orientation
  "Scalar-first Hamilton quaternion mapping body axes to world axes."
  [1.0 0.0 0.0 0.0])

(def ^:const moment-of-inertia
  "Constant v1 gameplay inertia in kg m²; independent of spark formation mass."
  1.0)

(def ^:const max-angular-speed
  "Always-on angular safety ceiling in rad/s; input tuning is a later slice."
  math/PI)

(def orientation-schema
  "Finite unit quaternion [w x y z], accepting roundoff within 1e-9 in norm²."
  [:and
   [:vector {:min 4 :max 4} [:fn field/finite-number?]]
   [:fn (fn [q] (< (abs (- 1.0 (reduce + (map #(* % %) q)))) 1.0e-9))]])

(def angular-velocity-schema
  "Finite world-axis angular velocity vector in radians per simulation second."
  [:vector {:min 3 :max 3} [:fn field/finite-number?]])

(def rotation-input-schema
  "A complete rotation state and its finite, nonnegative simulation step."
  [:map
   [:orientation orientation-schema]
   [:angular-velocity angular-velocity-schema]
   [:dt [:and [:fn field/finite-number?] [:fn (complement neg?)]]]])

(def rotation-input?
  "Compiled Malli validator for the quaternion integration boundary."
  (m/validator rotation-input-schema))
