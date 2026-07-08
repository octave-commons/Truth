(ns law.ecology.schema
  "Ecology schemas and contracts."
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

(defn ecology-phase?
  "True if x is a recognised ecology phase keyword."
  [x]
  (boolean (phase? x)))

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
   {:id       :law.ecology/ecology
    :shape-id :law.ecology/biosphere
    :kind     :type
    :schema   ecology-schema
    :name     "Toy Ecology"
    :description "Five-variable phase-driven toy biosphere state."}))

(def ecology-extended-contract
  (contract/->contract
   {:id       :law.ecology/ecology-extended
    :shape-id :law.ecology/biosphere
    :kind     :quality
    :schema   ecology-extended-schema
    :name     "Toy Ecology (extended)"
    :description "Toy ecology state with transition history and origin tracking."}))
