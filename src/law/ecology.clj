(ns law.ecology
  "Contracts and schemas for the toy biosphere model.

   The toy ecology gives Phase 0 planets a life arc before the full biological
   simulation is written. It is a five-variable phase-driven system that runs
   as a pure tick over an ecology component.

   Derived from: docs/designs/player-abilities-and-ecology.md §5–§7."
  (:require
   [law.contract :as contract]))

;; --- Phases -----------------------------------------------------------------

(def ^:const phase-abiotic       :abiotic)
(def ^:const phase-prebiotic     :prebiotic)
(def ^:const phase-prokaryotic   :prokaryotic)
(def ^:const phase-eukaryotic    :eukaryotic)
(def ^:const phase-multicellular :multicellular)
(def ^:const phase-complex       :complex)

(def phases
  "Ordered ecology phase keywords."
  [phase-abiotic phase-prebiotic phase-prokaryotic
   phase-eukaryotic phase-multicellular phase-complex])

(def phase?
  "True if x is a recognised ecology phase keyword."
  #{phase-abiotic phase-prebiotic phase-prokaryotic
    phase-eukaryotic phase-multicellular phase-complex})

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

;; --- Validation predicates --------------------------------------------------

(defn unit-scalar?
  "True if x is a finite double in [0,1]."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (<= 0.0 (double x) 1.0)))

(defn positive-unit-scalar?
  "True if x is a finite double in (0,1]."
  [x]
  (and (number? x)
       (Double/isFinite (double x))
       (< 0.0 (double x) 1.001)))

(defn ecology-phase?
  [x]
  (boolean (phase? x)))

(defn transition-record?
  "A recorded phase transition: tick, phase, biomass, complexity."
  [x]
  (and (map? x)
       (integer? (:tick x))
       (phase? (:phase x))
       (unit-scalar? (:biomass x))
       (unit-scalar? (:complexity x))))

;; --- Schemas ----------------------------------------------------------------

(def ecology-schema
  "Five-variable toy ecology state plus phase tracking.
   Required: moisture, temp, biomass, complexity, stability, phase, seeded.
   Optional: record of phase transitions, origin-body for panspermia."
  {:moisture   unit-scalar?
   :temp       unit-scalar?
   :biomass    unit-scalar?
   :complexity unit-scalar?
   :stability  unit-scalar?
   :phase      ecology-phase?
   :seeded     boolean?})

(def ecology-extended-schema
  "Ecology schema with optional transition record and origin tracking."
  (assoc ecology-schema
         :record      (some-fn nil? #(every? transition-record? %))
         :origin-body (some-fn nil? uuid? integer?)))

;; --- Contracts --------------------------------------------------------------

(def ecology-contract
  (contract/->contract
   {:id       ::ecology
    :shape-id ::biosphere
    :kind     :type
    :schema   ecology-schema
    :name     "Toy Ecology"
    :description "Five-variable phase-driven toy biosphere state."}))

(def ecology-extended-contract
  (contract/->contract
   {:id       ::ecology-extended
    :shape-id ::biosphere
    :kind     :quality
    :schema   ecology-extended-schema
    :name     "Toy Ecology (extended)"
    :description "Toy ecology state with transition history and origin tracking."}))
