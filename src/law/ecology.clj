(ns law.ecology
  "Contracts and schemas for the toy biosphere model.

   The toy ecology gives Phase 0 planets a life arc before the full biological
   simulation is written. It is a five-variable phase-driven system that runs
   as a pure tick over an ecology component.

   Derived from: docs/designs/player-abilities-and-ecology.md §5–§7."
  (:require
   [law.ecology.schema :as schema]))

;; --- Re-exports from schema -------------------------------------------------

(def phase-abiotic schema/phase-abiotic)
(def phase-prebiotic schema/phase-prebiotic)
(def phase-prokaryotic schema/phase-prokaryotic)
(def phase-eukaryotic schema/phase-eukaryotic)
(def phase-multicellular schema/phase-multicellular)
(def phase-complex schema/phase-complex)

(def phases schema/phases)
(def phase? schema/phase?)
(def ecology-phase? schema/ecology-phase?)
(def unit-scalar? schema/unit-scalar?)
(def positive-unit-scalar? schema/positive-unit-scalar?)
(def transition-record? schema/transition-record?)
(def ecology-schema schema/ecology-schema)
(def ecology-extended-schema schema/ecology-extended-schema)
(def ecology-contract schema/ecology-contract)
(def ecology-extended-contract schema/ecology-extended-contract)

;; --- Phase ordering ---------------------------------------------------------

(defn phase-index
  "Integer index of `phase` in the ordered phase chain."
  [phase]
  (case phase
    :abiotic       0
    :prebiotic     1
    :prokaryotic   2
    :eukaryotic    3
    :multicellular 4
    :complex       5
    -1))

;; --- Thresholds -------------------------------------------------------------

(def ^:const moisture-prebiotic-threshold    0.15)
(def ^:const biomass-prokaryotic-threshold   0.15)
(def ^:const temp-prokaryotic-max            0.80)
(def ^:const biomass-eukaryotic-threshold    0.35)
(def ^:const complexity-eukaryotic-threshold 0.20)
(def ^:const complexity-multicellular-threshold 0.45)
(def ^:const stability-multicellular-threshold  0.40)
(def ^:const complexity-complex-threshold    0.70)
(def ^:const biomass-complex-threshold       0.60)
(def ^:const habitability-low                0.25)
(def ^:const habitability-high               0.75)
(def ^:const collapse-stability-threshold    0.10)
(def ^:const collapse-biomass-threshold      0.05)
(def ^:const extinction-biomass-floor        0.05)

;; --- Passive tick rates -----------------------------------------------------

(def ^:const moisture-gain-rate        0.005)
(def ^:const moisture-loss-rate        0.008)
(def ^:const biomass-gain-rate         0.003)
(def ^:const complexity-gain-rate      0.001)
(def ^:const stability-gain-rate       0.002)
(def ^:const stability-loss-rate       0.008)
(def ^:const temperature-reversion-rate 0.01)
(def ^:const collapse-biomass-loss-rate 0.02)
