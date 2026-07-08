(ns domain.planet-formation.spec
  "Thin facade over the split planet-formation sub-modules."
  (:require
   [domain.planet-formation.physics :as pfph]
   [domain.planet-formation.composition :as pfc]
   [domain.planet-formation.orbit :as pfo]))

;; --- Re-exports from domain.planet-formation.physics ------------------------

(def ^{:doc "Re-export; see `domain.planet-formation.physics/snow-line-temperature`."}
  snow-line-temperature pfph/snow-line-temperature)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/proto-solar-metal-frac`."}
  proto-solar-metal-frac pfph/proto-solar-metal-frac)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/ice-enhancement-factor`."}
  ice-enhancement-factor pfph/ice-enhancement-factor)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/min-seed-mass-solar`."}
  min-seed-mass-solar pfph/min-seed-mass-solar)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/max-seed-mass-solar`."}
  max-seed-mass-solar pfph/max-seed-mass-solar)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/critical-core-mass-kg`."}
  critical-core-mass-kg pfph/critical-core-mass-kg)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/runaway-gas-fraction`."}
  runaway-gas-fraction pfph/runaway-gas-fraction)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/condensation-seed-mass-kg`."}
  condensation-seed-mass-kg pfph/condensation-seed-mass-kg)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/condensation-seed-mass`."}
  condensation-seed-mass pfph/condensation-seed-mass)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/sound-speed`."}
  sound-speed pfph/sound-speed)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/equilibrium-temperature`."}
  equilibrium-temperature pfph/equilibrium-temperature)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/snow-line-radius`."}
  snow-line-radius pfph/snow-line-radius)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/solid-surface-density`."}
  solid-surface-density pfph/solid-surface-density)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/mmsn-sigma0`."}
  mmsn-sigma0 pfph/mmsn-sigma0)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/mmsn-sigma`."}
  mmsn-sigma pfph/mmsn-sigma)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/core-accretion-timescale`."}
  core-accretion-timescale pfph/core-accretion-timescale)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/planet-mass`."}
  planet-mass pfph/planet-mass)

;; --- Re-exports from domain.planet-formation.composition --------------------

(def ^{:doc "Re-export; see `domain.planet-formation.composition/planet-type`."}
  planet-type pfc/planet-type)

(def ^{:doc "Re-export; see `domain.planet-formation.composition/planet-composition`."}
  planet-composition pfc/planet-composition)

(def ^{:doc "Re-export; see `domain.planet-formation.composition/planet-material-density-by-type`."}
  planet-material-density-by-type pfc/planet-material-density-by-type)

;; --- Re-exports from domain.planet-formation.orbit --------------------------

(def ^{:doc "Re-export; see `domain.planet-formation.orbit/planet-bond-albedo`."}
  planet-bond-albedo pfo/planet-bond-albedo)

(def ^{:doc "Re-export; see `domain.planet-formation.orbit/planet-greenhouse-warming`."}
  planet-greenhouse-warming pfo/planet-greenhouse-warming)

(def ^{:doc "Re-export; see `domain.planet-formation.orbit/surface-temperature`."}
  surface-temperature pfo/surface-temperature)

(def ^{:doc "Re-export; see `domain.planet-formation.orbit/build-planet-spec`."}
  build-planet-spec pfo/build-planet-spec)
