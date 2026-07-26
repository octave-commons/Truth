(ns domain.planet-formation
  "Thin facade over the split planet-formation sub-modules."
  (:require
   [domain.planet-formation.spec :as pfs]
   [domain.planet-formation.seed :as pfd]))

;; --- Re-exports from domain.planet-formation.spec ---------------------------

(def ^{:doc "Re-export; see `domain.planet-formation.spec/ice-enhancement-factor`."}
  ice-enhancement-factor pfs/ice-enhancement-factor)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/condensation-seed-mass`."}
  condensation-seed-mass pfs/condensation-seed-mass)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/snow-line-radius`."}
  snow-line-radius pfs/snow-line-radius)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/solid-surface-density`."}
  solid-surface-density pfs/solid-surface-density)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/mmsn-sigma0`."}
  mmsn-sigma0 pfs/mmsn-sigma0)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/mmsn-sigma`."}
  mmsn-sigma pfs/mmsn-sigma)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/planet-type`."}
  planet-type pfs/planet-type)

(def ^{:doc "Re-export; see `domain.planet-formation.spec/planet-composition`."}
  planet-composition pfs/planet-composition)

;; --- Re-exports from domain.planet-formation.seed --------------------------

(def ^{:doc "Re-export; see `domain.planet-formation.seed/disk-maturity-seconds`."}
  disk-maturity-seconds pfd/disk-maturity-seconds)

(def ^{:doc "Re-export; see `domain.planet-formation.seed/min-planet-orbit-radius-au`."}
  min-planet-orbit-radius-au pfd/min-planet-orbit-radius-au)

(def ^{:doc "Re-export; see `domain.planet-formation.seed/planet-seeds`."}
  planet-seeds pfd/planet-seeds)
