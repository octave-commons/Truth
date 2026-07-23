(ns law.stellar
  "Contracts and schemas for stellar nebula, star formation, and planetary bodies.
   Defines the physical laws governing Phase 0 of Gates of Truth."
  (:require
   [law.stellar.schema :as schema]
   [law.stellar.orbital :as orbital]
   [law.atmosphere :as atmosphere]))

;; Schemas
(def matter-state-schema schema/matter-state-schema)
(def nebula-cloud-schema schema/nebula-cloud-schema)
(def angular-momentum-schema schema/angular-momentum-schema)
(def spin-schema schema/spin-schema)
(def oblateness-schema schema/oblateness-schema)
(def rotation-axis-schema schema/rotation-axis-schema)
(def accretion-radius-schema schema/accretion-radius-schema)
(def material-class-schema schema/material-class-schema)
(def material-class? schema/material-class?)
(def thermal-band-schema schema/thermal-band-schema)
(def thermal-band? schema/thermal-band?)
(def atmosphere-class-schema atmosphere/atmosphere-class-schema)
(def atmosphere-class? atmosphere/atmosphere-class?)
(def retained-species-schema atmosphere/retained-species-schema)
(def retained-species? atmosphere/retained-species?)
(def stellar-system-schema schema/stellar-system-schema)

;; Contracts
(def matter-state-contract schema/matter-state-contract)
(def nebula-cloud-contract schema/nebula-cloud-contract)
(def stellar-system-contract schema/stellar-system-contract)

;; Physical constants
(def ^:const G orbital/G) ;; Gravitational constant m³/kg·s²
(def ^:const k-B orbital/k-B) ;; Boltzmann constant J/K
(def ^:const m-H orbital/m-H) ;; Hydrogen mass kg
(def ^:const stefan-boltzmann orbital/stefan-boltzmann) ;; Stefan-Boltzmann constant W/m²·K⁴
(def ^:const fusion-temp-threshold orbital/fusion-temp-threshold) ;; Fusion ignition temperature K (hydrogen burning)
(def ^:const fusion-pressure-threshold orbital/fusion-pressure-threshold) ;; Fusion ignition pressure Pa (stellar-core scale)
(def ^:const rounding-mass-threshold orbital/rounding-mass-threshold) ;; kg — above this self-gravity pulls a body into hydrostatic roundness
(def ^:const solar-mass orbital/solar-mass) ;; kg
(def ^:const solar-radius orbital/solar-radius) ;; m
(def ^:const solar-luminosity orbital/solar-luminosity) ;; W
(def ^:const earth-mass orbital/earth-mass) ;; kg
(def ^:const jupiter-mass orbital/jupiter-mass) ;; kg
(def ^:const au orbital/au) ;; m — astronomical unit

(def ^:const opacity-limit-mass orbital/opacity-limit-mass)
(def ^:const deuterium-burning-mass orbital/deuterium-burning-mass)
(def ^:const brown-dwarf-desert-mass orbital/brown-dwarf-desert-mass)
(def ^:const hydrogen-burning-mass orbital/hydrogen-burning-mass)

(def ^:const feeding-zone-hill-factor orbital/feeding-zone-hill-factor) ;; ≈ 2√3

(def ^:const debris-mass-threshold orbital/debris-mass-threshold) ;; kg — gas → planetesimal/debris
(def ^:const planet-mass-threshold orbital/planet-mass-threshold)   ;; kg — debris → planet-scale
(def ^:const star-mass-threshold   orbital/star-mass-threshold) ;; kg — planet → star-forming core (dominant)

(def ^:const ablation-floor orbital/ablation-floor) ;; kg — bound body despawn threshold

(def ^:const melt-temperature orbital/melt-temperature)
(def ^:const shatter-malleability-max orbital/shatter-malleability-max)
(def ^:const shatter-dv-threshold orbital/shatter-dv-threshold)
(def ^:const shatter-min-mass orbital/shatter-min-mass)

(def ^:const fusion-sustain-temp-threshold orbital/fusion-sustain-temp-threshold)

(def ^:const default-dark-matter-mass-factor orbital/default-dark-matter-mass-factor)
(def ^:const default-dark-matter-scale-factor orbital/default-dark-matter-scale-factor)

;; Functions
(def substellar-mass-class orbital/substellar-mass-class)
(def ideal-gas-pressure orbital/ideal-gas-pressure)
(def softened-circular-speed orbital/softened-circular-speed)
(def hill-radius orbital/hill-radius)
(def isolation-mass orbital/isolation-mass)
(def virial-speed orbital/virial-speed)
(def plummer-acceleration orbital/plummer-acceleration)
(def main-sequence-radius orbital/main-sequence-radius)
(def white-dwarf-radius orbital/white-dwarf-radius)
(def mass-class orbital/mass-class)
(def malleability orbital/malleability)
(def hydrostatic-equilibrium? orbital/hydrostatic-equilibrium?)
(def fusion-possible? orbital/fusion-possible?)
(def fusion-sustaining? orbital/fusion-sustaining?)
(def orbital-cleared? orbital/orbital-cleared?)
(def planet? orbital/planet?)
