(ns domain.stellar
  "Thin facade over the split stellar sub-modules. Most callers (tests, genesis)
   can keep requiring `domain.stellar`, but the canonical implementations live in
   their topical namespaces under `domain.stellar.*`."
  (:require
   [domain.stellar.thermodynamics   :as thermo]
   [domain.stellar.geometry         :as geometry]
   [domain.stellar.merge            :as stellar-merge]
   [domain.stellar.classifier       :as classifier]
   [domain.stellar.sink             :as sink]
   [domain.stellar.seeder           :as seeder]
   [domain.stellar.disc             :as disc]
   [domain.stellar.disc-evolution   :as disc-evolution]
   [domain.stellar.wind             :as wind]
   [domain.stellar.fusion           :as fusion]
   [domain.stellar.temperature      :as temperature]))

;; --- Re-exports from existing sub-modules -----------------------------------
(def ^{:doc "Re-export; see `domain.stellar.thermodynamics/entity->region`."}
  entity->region thermo/entity->region)
(def ^{:doc "Re-export; see `domain.stellar.thermodynamics/orbital-angular-momentum`."}
  orbital-angular-momentum thermo/orbital-angular-momentum)
(def ^{:doc "Re-export; see `domain.stellar.geometry/structure-system`."}
  structure-system geometry/structure-system)
(def ^{:doc "Re-export; see `domain.stellar.geometry/eos-system`."}
  eos-system geometry/eos-system)
(def ^{:doc "Re-export; see `domain.stellar.classifier/classifier-system`."}
  classifier-system classifier/classifier-system)
(def ^{:doc "Re-export; see `domain.stellar.temperature/temperature-system`."}
  temperature-system temperature/temperature-system)
(def ^{:doc "Re-export; see `domain.stellar.classifier/complexity-score`."}
  complexity-score classifier/complexity-score)
(def ^{:doc "Re-export; see `domain.stellar.sink/sink-formation-system`."}
  sink-formation-system sink/sink-formation-system)
(def ^{:doc "Re-export; see `domain.stellar.sink/resolution-feeding-zone-factor`."}
  resolution-feeding-zone-factor sink/resolution-feeding-zone-factor)
(def ^{:doc "Re-export; see `domain.stellar.sink/capture-velocity-dispersion`."}
  capture-velocity-dispersion sink/capture-velocity-dispersion)
(def ^{:doc "Re-export; see `domain.stellar.sink/effective-accretion-radius`."}
  effective-accretion-radius sink/effective-accretion-radius)
(def ^{:doc "Re-export; see `domain.stellar.sink/imf-accretion-bias`."}
  imf-accretion-bias sink/imf-accretion-bias)
(def ^{:doc "Re-export; see `domain.stellar.sink/stellar-feedback-temperature`."}
  stellar-feedback-temperature sink/stellar-feedback-temperature)
(def ^{:doc "Re-export; see `domain.stellar.sink/feedback-radius`."}
  feedback-radius sink/feedback-radius)
(def ^{:doc "Re-export; see `domain.stellar.merge/stellar-merge-handler`."}
  stellar-merge-handler stellar-merge/stellar-merge-handler)

;; --- Re-exports from the new split modules ----------------------------------
(def ^{:doc "Re-export; see `domain.stellar.seeder/seed-clump`."}
  seed-clump seeder/seed-clump)
(def ^{:doc "Re-export; see `domain.stellar.seeder/spawn-clump`."}
  spawn-clump seeder/spawn-clump)
(def ^{:doc "Re-export; see `domain.stellar.seeder/condensation-seeder-system`."}
  condensation-seeder-system seeder/condensation-seeder-system)
(def ^{:doc "Re-export; see `domain.stellar.seeder/default-composition`."}
  default-composition seeder/default-composition)
(def ^{:doc "Re-export; see `domain.stellar.disc/disc-identification-system`."}
  disc-identification-system disc/disc-identification-system)
(def ^{:doc "Re-export; see `domain.stellar.disc/in-disc?`."}
  in-disc? disc/in-disc?)
(def ^{:doc "Re-export; see `domain.stellar.disc/disc-classify`."}
  disc-classify disc/disc-classify)
(def ^{:doc "Re-export; see `domain.stellar.disc-evolution/disk-evolution-system`."}
  disk-evolution-system disc-evolution/disk-evolution-system)
(def ^{:doc "Re-export; see `domain.stellar.disc-evolution/max-gi-fragments-per-disk`."}
  max-gi-fragments-per-disk disc-evolution/max-gi-fragments-per-disk)
(def ^{:doc "Re-export; see `domain.stellar.wind/stellar-wind-system`."}
  stellar-wind-system wind/stellar-wind-system)
(def ^{:doc "Re-export; see `domain.stellar.wind/wind-ablation-system`."}
  wind-ablation-system wind/wind-ablation-system)
(def ^{:doc "Re-export; see `domain.stellar.wind/stellar-flare-system`."}
  stellar-flare-system wind/stellar-flare-system)
(def ^{:doc "Re-export; see `domain.stellar.fusion/fusion-system`."}
  fusion-system fusion/fusion-system)
(def ^{:doc "Re-export; see `domain.stellar.fusion/fusion-promotion-system`."}
  fusion-promotion-system fusion/fusion-promotion-system)
(def ^{:doc "Re-export; see `domain.stellar.fusion/stellar-sed-system`."}
  stellar-sed-system fusion/stellar-sed-system)
(def ^{:doc "Re-export; see `domain.stellar.fusion/atmosphere-shells-system`."}
  atmosphere-shells-system fusion/atmosphere-shells-system)
(def ^{:doc "Re-export; see `domain.stellar.fusion/deuterium-depletion-system`."}
  deuterium-depletion-system fusion/deuterium-depletion-system)
