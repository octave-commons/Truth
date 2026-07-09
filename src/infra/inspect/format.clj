(ns infra.inspect.format
  "Human-readable formatting helpers for inspected ECS bodies.

   Mass, radius, temperature, speed, luminosity, composition, and ecology stats
   are rendered into strings and colours for the inspector card."
  (:require
   [clojure.string :as str]
   [domain.ecology :as ecology]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [shape.spatial :as sp]))

;; Physical reference scales for human-readable readouts.
(def ^:const solar-mass   1.989e30)  ;; kg
(def ^:const solar-radius 6.957e8)   ;; m
(def ^:const solar-lum    3.828e26)  ;; W
(def ^:const earth-mass   5.972e24)  ;; kg
(def ^:const earth-radius 6.371e6)   ;; m
(def ^:const au           1.496e11)  ;; m

(defn fmt-mass
  "Format a mass in kg as a human-readable string."
  [kg stellar?]
  (let [kg (double (or kg 0.0))]
    (cond
      (or stellar? (>= kg (* 0.05 solar-mass))) (format "%.3f Msun" (/ kg solar-mass))
      (>= kg (* 0.05 earth-mass)) (format "%.2f Mearth" (/ kg earth-mass))
      :else                       (format "%.2e kg" kg))))

(defn fmt-radius
  "Format a radius in metres as a human-readable string."
  [m star?]
  (let [m (double (or m 0.0))]
    (cond
      star?            (format "%.2f Rsun" (/ m solar-radius))
      (>= m earth-radius) (format "%.2f Rearth" (/ m earth-radius))
      (>= m 1.0e3)     (format "%.0f km" (/ m 1.0e3))
      :else            (format "%.2e m" m))))

(defn- fmt-comp
  "Top two composition fractions, e.g. \"H 0.74  He 0.24\"."
  [cm]
  (when (seq cm)
    (->> cm
         (sort-by (fn [[_ v]] (- (double v))))
         (take 2)
         (map (fn [[k v]] (format "%s %.2f" (name k) (double v))))
         (str/join "  "))))

(defn state-label
  "Return a human-readable label for a matter state keyword."
  [state]
  (case state
    :nebula "Nebula gas"
    :condensed-core "Condensed core"
    :planetesimal "Planetesimal"
    :gas-giant "Gas giant"
    :brown-dwarf "Brown dwarf"
    :protostar "Protostar"
    :star "Star"
    :planet "Planet"
    (some-> state name)))

(defn state-color
  "RGBA colour for a matter-state keyword, used by the inspector card title."
  [state]
  (case state
    :star          [1.0 0.92 0.55 1.0]
    :protostar     [1.0 0.72 0.45 1.0]
    :condensed-core [0.85 0.45 0.30 1.0]
    :planet        [0.55 0.78 1.0 1.0]
    :brown-dwarf   [0.85 0.55 0.35 1.0]
    :gas-giant     [0.55 0.65 0.85 1.0]
    :planetesimal  [0.75 0.75 0.8 1.0]
    :nebula        [0.7 0.6 0.9 1.0]
    [0.85 0.9 1.0 1.0]))

(defn body-facts
  "Ordered [label value] readout lines for entity `eid` from its live ECS state.
   Includes ecology stats when the body is alive and SED/magnetosphere/disk
   readouts where available."
  [world eid]
  (let [g     (fn [k] (ecs/get-component world eid k))
        state (g c/matter-state)
        stellar? (boolean (#{:star :protostar} state))
        vel   (g c/velocity)
        speed (when vel (/ (sp/len vel) 1000.0))
        temp  (g c/temperature)
        lum   (g c/luminosity)
        regime (g c/regime)
        c  (fmt-comp (g c/composition))
        eco (g c/ecology)
        sed (g c/sed-bands)
        mag (g c/magnetosphere)
        disk-m (g c/disk-mass)
        esc (g c/atmosphere-escape)]
    (cond-> [["mass"  (fmt-mass (g c/mass) stellar?)]]
      true        (conj ["radius" (fmt-radius (g c/radius) stellar?)])
      temp        (conj ["temp"  (format "%.0f K" (double temp))])
      speed       (conj ["speed" (format "%.2f km/s" (double speed))])
      (and lum (pos? (double lum)))
      (conj ["lum"   (format "%.3g Lsun" (/ (double lum) solar-lum))])
      c        (conj ["comp"  c])
      regime      (conj ["regime" (name regime)])
      eco         (conj ["life"  (name (:phase eco))])
      (and eco (ecology/living? eco))
      (-> (conj ["biomass" (format "%.0f%%" (* 100.0 (:biomass eco)))])
          (conj ["complexity" (format "%.0f%%" (* 100.0 (:complexity eco)))])
          (conj ["stability" (format "%.0f%%" (* 100.0 (:stability eco)))])
          (conj ["moisture" (format "%.0f%%" (* 100.0 (:moisture eco)))]))
      (and disk-m (pos? (double disk-m)))
      (conj ["disk" (format "%s" (fmt-mass disk-m false))])
      mag         (conj ["mag" (format "%.2f R" (:standoff-distance mag))])
      esc         (conj ["escape" (name (:regime esc))])
      sed         (conj ["sed" (format "%d bands" (count sed))])
      true        (conj ["eid"   (str eid)]))))
