(ns law.stellar.orbital
  "Orbital mechanics, Plummer gravity, and stellar constants for Phase 0.
   Thin facade over law.stellar.orbital.constants and law.stellar.orbital.dynamics."
  (:require
   [law.stellar.orbital.constants :as constants]
   [law.stellar.orbital.dynamics :as dynamics]))

;; Physical constants
(def ^:const G constants/G)
(def ^:const k-B constants/k-B)
(def ^:const m-H constants/m-H)
(def ^:const stefan-boltzmann constants/stefan-boltzmann)
(def ^:const fusion-temp-threshold constants/fusion-temp-threshold)
(def ^:const fusion-pressure-threshold constants/fusion-pressure-threshold)
(def ^:const rounding-mass-threshold constants/rounding-mass-threshold)
(def ^:const solar-mass constants/solar-mass)
(def ^:const solar-radius constants/solar-radius)
(def ^:const solar-luminosity constants/solar-luminosity)
(def ^:const earth-mass constants/earth-mass)
(def ^:const jupiter-mass constants/jupiter-mass)
(def ^:const au constants/au)

;; Stellar/sub-stellar mass boundaries
(def ^:const opacity-limit-mass constants/opacity-limit-mass)
(def ^:const deuterium-burning-mass constants/deuterium-burning-mass)
(def ^:const brown-dwarf-desert-mass constants/brown-dwarf-desert-mass)
(def ^:const hydrogen-burning-mass constants/hydrogen-burning-mass)
(def ^:const ablation-floor constants/ablation-floor)

;; Feeding-zone and accretion thresholds
(def ^:const feeding-zone-hill-factor constants/feeding-zone-hill-factor)
(def ^:const debris-mass-threshold constants/debris-mass-threshold)
(def ^:const planet-mass-threshold constants/planet-mass-threshold)
(def ^:const star-mass-threshold constants/star-mass-threshold)
(def ^:const melt-temperature constants/melt-temperature)
(def ^:const shatter-malleability-max constants/shatter-malleability-max)
(def ^:const shatter-dv-threshold constants/shatter-dv-threshold)
(def ^:const shatter-min-mass constants/shatter-min-mass)
(def ^:const fusion-sustain-temp-threshold constants/fusion-sustain-temp-threshold)

;; Classification / threshold helpers
(def substellar-mass-class constants/substellar-mass-class)
(def ideal-gas-pressure constants/ideal-gas-pressure)
(def main-sequence-radius constants/main-sequence-radius)
(def white-dwarf-radius constants/white-dwarf-radius)
(def mass-class constants/mass-class)
(def malleability constants/malleability)
(def hydrostatic-equilibrium? constants/hydrostatic-equilibrium?)
(def fusion-possible? constants/fusion-possible?)
(def fusion-sustaining? constants/fusion-sustaining?)

;; Orbital-dynamics computations
(def softened-circular-speed dynamics/softened-circular-speed)
(def hill-radius dynamics/hill-radius)
(def isolation-mass dynamics/isolation-mass)
(def virial-speed dynamics/virial-speed)
(def plummer-acceleration dynamics/plummer-acceleration)
(def ^:const default-dark-matter-mass-factor dynamics/default-dark-matter-mass-factor)
(def ^:const default-dark-matter-scale-factor dynamics/default-dark-matter-scale-factor)
(def orbital-cleared? dynamics/orbital-cleared?)
(def planet? dynamics/planet?)
