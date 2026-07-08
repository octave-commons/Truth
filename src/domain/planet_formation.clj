(ns domain.planet-formation
  "Thin facade over the split planet-formation sub-modules."
  (:require
   [domain.planet-formation.spec :as pfs]
   [domain.planet-formation.seed :as pfd]))

;; --- Re-exports from domain.planet-formation.spec ---------------------------

(def ^{:doc "Re-export; see `domain.planet-formation.spec/snow-line-temperature`."}
  snow-line-temperature pfs/snow-line-temperature)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/planet-bond-albedo`."}
  planet-bond-albedo pfs/planet-bond-albedo)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/planet-greenhouse-warming`."}
  planet-greenhouse-warming pfs/planet-greenhouse-warming)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/proto-solar-metal-frac`."}
  proto-solar-metal-frac pfs/proto-solar-metal-frac)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/ice-enhancement-factor`."}
  ice-enhancement-factor pfs/ice-enhancement-factor)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/min-seed-mass-solar`."}
  min-seed-mass-solar pfs/min-seed-mass-solar)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/max-seed-mass-solar`."}
  max-seed-mass-solar pfs/max-seed-mass-solar)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/critical-core-mass-kg`."}
  critical-core-mass-kg pfs/critical-core-mass-kg)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/runaway-gas-fraction`."}
  runaway-gas-fraction pfs/runaway-gas-fraction)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/condensation-seed-mass-kg`."}
  condensation-seed-mass-kg pfs/condensation-seed-mass-kg)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/condensation-seed-mass`."}
  condensation-seed-mass pfs/condensation-seed-mass)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/sound-speed`."}
  sound-speed pfs/sound-speed)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/equilibrium-temperature`."}
  equilibrium-temperature pfs/equilibrium-temperature)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/surface-temperature`."}
  surface-temperature pfs/surface-temperature)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/snow-line-radius`."}
  snow-line-radius pfs/snow-line-radius)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/solid-surface-density`."}
  solid-surface-density pfs/solid-surface-density)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/mmsn-sigma0`."}
  mmsn-sigma0 pfs/mmsn-sigma0)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/mmsn-sigma`."}
  mmsn-sigma pfs/mmsn-sigma)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/core-accretion-timescale`."}
  core-accretion-timescale pfs/core-accretion-timescale)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/planet-type`."}
  planet-type pfs/planet-type)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/planet-composition`."}
  planet-composition pfs/planet-composition)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/planet-material-density-by-type`."}
  planet-material-density-by-type pfs/planet-material-density-by-type)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/planet-mass`."}
  planet-mass pfs/planet-mass)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/build-planet-spec`."}
  build-planet-spec pfs/build-planet-spec)

;; --- Re-exports from domain.planet-formation.seed --------------------------

(def ^{:doc "Re-export; see `domain.planet-formation.seed/disk-maturity-seconds`."}
  disk-maturity-seconds pfd/disk-maturity-seconds)

(def ^{:doc "Re-export; see `domain.planet-formation.seed/min-planet-orbit-radius-au`."}
  min-planet-orbit-radius-au pfd/min-planet-orbit-radius-au)

(def ^{:doc "Re-export; see `domain.planet-formation.seed/planet-seeding-outer-au`."}
  planet-seeding-outer-au pfd/planet-seeding-outer-au)

(def ^{:doc "Re-export; see `domain.planet-formation.seed/planet-seeding-annuli`."}
  planet-seeding-annuli pfd/planet-seeding-annuli)

(def ^{:doc "Re-export; see `domain.planet-formation.seed/planet-seeds`."}
  planet-seeds pfd/planet-seeds)
