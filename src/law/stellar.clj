(ns law.stellar
  "Contracts and schemas for stellar nebula, star formation, and planetary bodies.
   Defines the physical laws governing Phase 0 of Gates of Truth."
  (:require
   [law.contract :as contract]))

;; Physical constants
(def ^:const G 6.674e-11) ;; Gravitational constant m³/kg·s²
(def ^:const k-B 1.380649e-23) ;; Boltzmann constant J/K
(def ^:const m-H 1.6735e-27) ;; Hydrogen mass kg
(def ^:const stefan-boltzmann 5.670374419e-8) ;; Stefan-Boltzmann constant W/m²·K⁴
(def ^:const fusion-temp-threshold 1e7) ;; Fusion ignition temperature K (hydrogen burning)
(def ^:const fusion-pressure-threshold 1e12) ;; Fusion ignition pressure Pa (stellar-core scale)
(def ^:const rounding-mass-threshold 3e20) ;; kg — above this self-gravity pulls a body into hydrostatic roundness
(def ^:const solar-mass 1.989e30) ;; kg

;; --- Matter States ---

(def matter-state-schema
  "Schema for matter in various states from nebula to planet"
  {:id          uuid?
   :position    vector? ;; [x y z]
   :velocity    vector? ;; [vx vy vz]
   :mass        pos?
   :radius      pos?
   :temperature pos?
   :density     pos?
   :composition map? ;; {:H 0.75 :He 0.24 :metals 0.01}
   :state       keyword? ;; :nebula :protostar :star :planet :debris
   :luminosity  number?
   :pressure    number?})

(def nebula-cloud-schema
  "Statistical representation of unfocused nebular region"
  {:id          uuid?
   :center      vector?
   :extent      pos? ;; radius of cloud
   :total-mass  pos?
   :temperature pos?
   :density     pos?
   :composition map?
   :angular-momentum vector?
   :turbulence  number? ;; 0.0 to 1.0
   :focus-level number? ;; 0.0 (statistical) to 1.0 (fully resolved)
   })

(def stellar-system-schema
  "Container for all bodies in a forming star system"
  {:id           uuid?
   :age          number? ;; seconds since formation began
   :central-star (some-fn nil? map?) ;; nil until star forms
   :bodies       sequential? ;; all other bodies
   :nebula       (some-fn nil? map?) ;; remaining nebula if any
   :time-scale   pos? ;; current time compression factor
   :complexity   number? ;; observable complexity metric
   })

;; --- Thresholds and Phase Transitions ---

(defn hydrostatic-equilibrium?
  "Test if a body is massive enough that its self-gravity overcomes material
   strength and pulls it into a round (hydrostatic) shape — the first half of
   the astronomical definition of a planet. We use a mass-threshold proxy rather
   than a full pressure-balance integration (see design open question #2)."
  [{:keys [mass]}]
  (boolean (and mass (> mass rounding-mass-threshold))))

(defn fusion-possible?
  "Test if conditions allow fusion ignition"
  [{:keys [temperature pressure composition]}]
  (and (> temperature fusion-temp-threshold)
       (> pressure fusion-pressure-threshold)
       (> (get composition :H 0) 0.1))) ;; at least 10% hydrogen

(defn orbital-cleared?
  "Test if a body has cleared its orbital neighborhood"
  [{:keys [mass orbital-radius]} other-bodies]
  ;; Simplified Stern-Levison parameter
  (let [hill-radius (* orbital-radius (Math/pow (/ mass (* 3 1.989e30)) 0.333))
        nearby (filter #(< (- (:orbital-radius %) orbital-radius) (* 2 hill-radius)) 
                       other-bodies)
        nearby-mass (reduce + 0 (map :mass nearby))]
    (> mass (* 100 nearby-mass)))) ;; dominates by factor of 100

(defn planet?
  "Full astronomical definition of a planet"
  [body other-bodies]
  (and (hydrostatic-equilibrium? body)
       (orbital-cleared? body other-bodies)
       (not (fusion-possible? body))))

;; --- Contracts ---

(def matter-state-contract
  (contract/->contract
   {:id       ::matter-state
    :shape-id ::stellar-body  
    :kind     :type
    :schema   matter-state-schema
    :name     "Matter State"
    :description "Physical state of matter from nebula to planet"}))

(def nebula-cloud-contract
  (contract/->contract
   {:id       ::nebula-cloud
    :shape-id ::nebular-region
    :kind     :type
    :schema   nebula-cloud-schema
    :name     "Nebula Cloud"
    :description "Statistical representation of nebular gas cloud"}))

(def stellar-system-contract
  (contract/->contract
   {:id       ::stellar-system
    :shape-id ::star-system
    :kind     :type
    :schema   stellar-system-schema
    :name     "Stellar System"
    :description "Complete star system in formation"}))