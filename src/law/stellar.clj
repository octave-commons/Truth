(ns law.stellar
  "Contracts and schemas for stellar nebula, star formation, and planetary bodies.
   Defines the physical laws governing Phase 0 of Gates of Truth.

   This is a facade: it re-exports the surface that consumers actually use from
   `law.stellar.orbital.constants`, `law.stellar.orbital.dynamics` and
   `law.stellar.schema`. It re-exports ONLY live symbols — 27 unconsumed
   re-exports were removed 2026-07-24 (see
   `kanban/tasks/static-analysis-dissolve-law-stellar-facade.md`), along with the
   intermediate `law.stellar.orbital` pass-through that used to sit between this
   namespace and the two real ones. If you need something that is not here,
   require the owning namespace directly rather than widening this list — an
   unconsumed re-export is dead code that also hides the deadness of what it
   points at."
  (:require
   [law.stellar.orbital.constants :as constants]
   [law.stellar.orbital.dynamics :as dynamics]
   [law.stellar.schema :as schema]))

;; Contracts
(def matter-state-contract schema/matter-state-contract)

;; Physical constants
(def ^:const G constants/G) ;; Gravitational constant m³/kg·s²
(def ^:const k-B constants/k-B) ;; Boltzmann constant J/K
(def ^:const m-H constants/m-H) ;; Hydrogen mass kg
(def ^:const stefan-boltzmann constants/stefan-boltzmann) ;; Stefan-Boltzmann constant W/m²·K⁴
(def ^:const solar-mass constants/solar-mass) ;; kg
(def ^:const solar-radius constants/solar-radius) ;; m
(def ^:const solar-luminosity constants/solar-luminosity) ;; W
(def ^:const earth-mass constants/earth-mass) ;; kg
(def ^:const jupiter-mass constants/jupiter-mass) ;; kg
(def ^:const au constants/au) ;; m — astronomical unit

;; Fusion / roundness thresholds
(def ^:const fusion-temp-threshold constants/fusion-temp-threshold) ;; Fusion ignition temperature K (hydrogen burning)
(def ^:const fusion-pressure-threshold constants/fusion-pressure-threshold) ;; Fusion ignition pressure Pa (stellar-core scale)
(def ^:const rounding-mass-threshold constants/rounding-mass-threshold) ;; kg — above this self-gravity pulls a body into hydrostatic roundness

;; Stellar / sub-stellar mass boundaries
(def ^:const opacity-limit-mass constants/opacity-limit-mass)
(def ^:const deuterium-burning-mass constants/deuterium-burning-mass)
(def ^:const hydrogen-burning-mass constants/hydrogen-burning-mass)
(def ^:const ablation-floor constants/ablation-floor) ;; kg — bound body despawn threshold

;; Material response thresholds
(def ^:const melt-temperature constants/melt-temperature)
(def ^:const shatter-malleability-max constants/shatter-malleability-max)
(def ^:const shatter-dv-threshold constants/shatter-dv-threshold)
(def ^:const shatter-min-mass constants/shatter-min-mass)

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

;; Orbital dynamics and softening
(def ^:const softening-cutoff-fraction dynamics/softening-cutoff-fraction)
(def body-softening dynamics/body-softening)
(def pair-softening dynamics/pair-softening)
(def softened-circular-speed dynamics/softened-circular-speed)
(def newtonian-circular-speed dynamics/newtonian-circular-speed)
(def hill-radius dynamics/hill-radius)
(def isolation-mass dynamics/isolation-mass)
(def virial-speed dynamics/virial-speed)
(def plummer-acceleration dynamics/plummer-acceleration)
(def ^:const default-dark-matter-mass-factor dynamics/default-dark-matter-mass-factor)
(def ^:const default-dark-matter-scale-factor dynamics/default-dark-matter-scale-factor)
