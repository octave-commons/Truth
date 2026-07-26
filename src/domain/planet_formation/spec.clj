(ns domain.planet-formation.spec
  "Thin facade over the split planet-formation sub-modules."
  (:require
   [domain.planet-formation.physics :as pfph]
   [domain.planet-formation.composition :as pfc]))

;; --- Re-exports from domain.planet-formation.physics ------------------------

(def ^{:doc "Re-export; see `domain.planet-formation.physics/ice-enhancement-factor`."}
  ice-enhancement-factor pfph/ice-enhancement-factor)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/condensation-seed-mass`."}
  condensation-seed-mass pfph/condensation-seed-mass)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/snow-line-radius`."}
  snow-line-radius pfph/snow-line-radius)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/solid-surface-density`."}
  solid-surface-density pfph/solid-surface-density)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/mmsn-sigma0`."}
  mmsn-sigma0 pfph/mmsn-sigma0)

(def ^{:doc "Re-export; see `domain.planet-formation.physics/mmsn-sigma`."}
  mmsn-sigma pfph/mmsn-sigma)

;; --- Re-exports from domain.planet-formation.composition --------------------

(def ^{:doc "Re-export; see `domain.planet-formation.composition/planet-type`."}
  planet-type pfc/planet-type)

(def ^{:doc "Re-export; see `domain.planet-formation.composition/planet-composition`."}
  planet-composition pfc/planet-composition)

;; --- Re-exports from domain.planet-formation.orbit --------------------------

