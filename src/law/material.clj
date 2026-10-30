(ns law.material
  "Finite material carriers and an explicitly approximate ice/vapor enthalpy law."
  (:require [malli.core :as m]))

(def cell-schema
  "SI inventories for one moving finite material cell."
  [:map [:silicate-kg [:and number? [:>= 0]]]
   [:iron-kg [:and number? [:>= 0]]] [:water-kg [:and number? [:>= 0]]]
   [:edge-m [:and number? [:> 0]]]
   [:pressure-pa [:and number? [:> 0] [:< 611.657]]]])
(def cell? "Validate the carrier schema." (m/validator cell-schema))

(defn validate-cell!
  "Reject invalid, empty or unsupported material inventories."
  [cell]
  (when-not (and (cell? cell) (pos? (+ (:silicate-kg cell) (:iron-kg cell) (:water-kg cell))))
    (throw (ex-info "Invalid finite material cell" {:cell cell})))
  cell)

(defn mass
  "Total carrier mass in kg; phases never add a second stock."
  [cell]
  (+ (:silicate-kg cell) (:iron-kg cell) (:water-kg cell)))

(defn heat-capacity
  "Constant mixture heat capacity J/K, exploratory reference values."
  [cell]
  (+ (* 800.0 (:silicate-kg cell)) (* 450.0 (:iron-kg cell)) (* 2100.0 (:water-kg cell))))

(defn element-masses
  "Elemental kg carried by SiO2, Fe and H2O using declared molar masses."
  [cell]
  (let [h (/ 2.016 18.015) si (/ 28.085 60.083)]
    {:Si (* si (:silicate-kg cell)) :Fe (:iron-kg cell)
     :H (* h (:water-kg cell))
     :O (+ (* (- 1.0 si) (:silicate-kg cell)) (* (- 1.0 h) (:water-kg cell)))}))

(defn sublimation-temperature
  "Constant-latent-heat Clausius–Clapeyron boundary, not an IAPWS EOS."
  [pressure]
  (/ 1.0 (- (/ 1.0 273.16) (* (/ 461.5 2834000.0) (Math/log (/ pressure 611.657))))))

(defn thermal-state
  "Allocate the same H2O stock between ice/vapor from a finite energy budget."
  [cell energy]
  (when-not (and (number? energy) (Double/isFinite (double energy)) (>= energy 0))
    (throw (ex-info "Invalid material internal energy" {:energy energy})))
  (let [cap (heat-capacity cell) water (:water-kg cell)
        ts (sublimation-temperature (:pressure-pa cell))
        base (* cap ts) latent (* water 2834000.0)
        vapor (if (pos? latent) (* water (min 1.0 (max 0.0 (/ (- energy base) latent)))) 0.0)
        temp (cond (<= energy base) (/ energy cap)
                   (< energy (+ base latent)) ts
                   :else (/ (- energy latent) cap))]
    {:temperature temp :water-ice-kg (- water vapor) :water-vapor-kg vapor
     :pressure-pa (:pressure-pa cell) :model :constant-cp-clausius-clapeyron}))

(def coarse-packet-schema
  "Retained cell state for a finite-time translating or rotating approximation."
  [:map
   [:members [:vector {:min 1}
              [:map [:source-id int?]
               [:offset [:tuple number? number? number?]]
               [:relative-velocity [:tuple number? number? number?]]
               [:rotation-residual-velocity {:optional true} [:tuple number? number? number?]]
               [:components map?]]]]
   [:packed-time number?] [:max-speed [:and number? [:>= 0]]]
   [:max-accel [:and number? [:>= 0]]]
   [:retained-mechanical-J number?] [:resolution-m [:and number? [:> 0]]]
   [:rotation? {:optional true} boolean?]
   [:inverse-inertia {:optional true} [:tuple [:tuple number? number? number?] [:tuple number? number? number?] [:tuple number? number? number?]]]
   [:spin-angular-momentum {:optional true} [:tuple number? number? number?]]])

(def coarse-packet?
  "Validate the cached-state handoff contract."
  (m/validator coarse-packet-schema))

(defn validate-packet!
  "Reject malformed cache packets before crossing the lifecycle boundary."
  [packet]
  (when-not (and (coarse-packet? packet)
                 (apply distinct? (map :source-id (:members packet)))
                 (or (not (:rotation? packet))
                     (and (:inverse-inertia packet) (:spin-angular-momentum packet)
                          (every? :rotation-residual-velocity (:members packet)))))
    (throw (ex-info "Invalid coarse material packet" {:packet packet})))
  packet)
