(ns domain.stellar.structure
  "Thin facade over the split stellar structure sub-modules.

   The canonical implementations now live in topical namespaces:
     - domain.stellar.geometry    — shape, compactness, structure-system, eos-system
     - domain.stellar.temperature — radiative heating, temperature-system
     - domain.stellar.merge       — collision/merge handler
     - domain.stellar.disc        — disk geometry and stability diagnostics
     - domain.stellar.sink        — feeding-zone-factor

   This namespace re-exports the old public surface for backward compatibility
   during the transition; new call sites should require the topical namespace
   directly."
  (:require
   [domain.stellar.geometry     :as geometry]
   [domain.stellar.temperature  :as temperature]
   [domain.stellar.merge        :as stellar-merge]
   [domain.stellar.disc         :as disc]
   [domain.stellar.sink         :as sink]))

;; --- Geometry / shape / compactness -----------------------------------------
(def ^{:doc "Re-export; see `domain.stellar.geometry/debris-material-density`."}
  debris-material-density geometry/debris-material-density)

(def ^{:doc "Re-export; see `domain.stellar.geometry/planet-material-density`."}
  planet-material-density geometry/planet-material-density)

(def ^{:doc "Re-export; see `domain.stellar.geometry/sphere-radius`."}
  sphere-radius geometry/sphere-radius)

(def ^{:doc "Re-export; see `domain.stellar.geometry/resolved-shape`."}
  resolved-shape geometry/resolved-shape)

(def ^{:doc "Re-export; see `domain.stellar.geometry/structure-system`."}
  structure-system geometry/structure-system)

(def ^{:doc "Re-export; see `domain.stellar.geometry/eos-system`."}
  eos-system geometry/eos-system)

;; --- Temperature -------------------------------------------------------------
(def ^{:doc "Re-export; see `domain.stellar.temperature/irradiance-at`."}
  irradiance-at temperature/irradiance-at)

(def ^{:doc "Re-export; see `domain.stellar.temperature/radiation-equilibrium-temperature`."}
  radiation-equilibrium-temperature temperature/radiation-equilibrium-temperature)

(def ^{:doc "Re-export; see `domain.stellar.temperature/radiation-heating-delta`."}
  radiation-heating-delta temperature/radiation-heating-delta)

(def ^{:doc "Re-export; see `domain.stellar.temperature/sed-heating-delta`."}
  sed-heating-delta temperature/sed-heating-delta)

(def ^{:doc "Re-export; see `domain.stellar.temperature/temperature-system`."}
  temperature-system temperature/temperature-system)

;; --- Merge / collisions ------------------------------------------------------
(def ^{:doc "Re-export; see `domain.stellar.merge/stellar-merge-handler`."}
  stellar-merge-handler stellar-merge/stellar-merge-handler)

;; --- Disc / disk stability ---------------------------------------------------
(def ^{:doc "Re-export; see `domain.stellar.disc/disk-radius`."}
  disk-radius disc/disk-radius)

(def ^{:doc "Re-export; see `domain.stellar.disc/disk-viscous-alpha`."}
  disk-viscous-alpha disc/disk-viscous-alpha)

(def ^{:doc "Re-export; see `domain.stellar.disc/disk-sound-speed`."}
  disk-sound-speed disc/disk-sound-speed)

(def ^{:doc "Re-export; see `domain.stellar.disc/disk-outer-temperature`."}
  disk-outer-temperature disc/disk-outer-temperature)

(def ^{:doc "Re-export; see `domain.stellar.disc/disk-viscous-timescale`."}
  disk-viscous-timescale disc/disk-viscous-timescale)

(def ^{:doc "Re-export; see `domain.stellar.disc/min-fragment-orbit-periods`."}
  min-fragment-orbit-periods disc/min-fragment-orbit-periods)

(def ^{:doc "Re-export; see `domain.stellar.disc/resolvable-orbit-radius`."}
  resolvable-orbit-radius disc/resolvable-orbit-radius)

(def ^{:doc "Re-export; see `domain.stellar.disc/toomre-q`."}
  toomre-q disc/toomre-q)

(def ^{:doc "Re-export; see `domain.stellar.disc/cooling-time-ratio`."}
  cooling-time-ratio disc/cooling-time-ratio)

(def ^{:doc "Re-export; see `domain.stellar.disc/disc-regime`."}
  disc-regime disc/disc-regime)

(def ^{:doc "Re-export; see `domain.stellar.disc/disk-regime-map`."}
  disk-regime-map disc/disk-regime-map)

;; --- Sink / accretion zones --------------------------------------------------
(def ^{:doc "Re-export; see `domain.stellar.sink/feeding-zone-factor`."}
  feeding-zone-factor sink/feeding-zone-factor)
