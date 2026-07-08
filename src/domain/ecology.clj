(ns domain.ecology
  "Public facade for the toy ecology model. Implementation is split into
   domain.ecology.state, domain.ecology.abilities, domain.ecology.system, and
   domain.ecology.math."
  (:require
   [domain.ecology.state :as state]
   [domain.ecology.abilities :as abilities]
   [domain.ecology.system :as system]))

;; --- Construction -------------------------------------------------------------

(def make-ecology "Create a fresh ecology state." state/make-ecology)

;; --- Predicates -------------------------------------------------------------

(def habitable? "True if the ecology's temperature is inside the habitable band." state/habitable?)
(def living? "True if the ecology is in a living phase (past prebiotic)." state/living?)
(def abiotic? "True if the ecology is in the :abiotic phase." state/abiotic?)
(def prebiotic? "True if the ecology is in the :prebiotic phase." state/prebiotic?)
(def prokaryotic? "True if the ecology is in the :prokaryotic phase." state/prokaryotic?)
(def eukaryotic? "True if the ecology is in the :eukaryotic phase." state/eukaryotic?)
(def multicellular? "True if the ecology is in the :multicellular phase." state/multicellular?)
(def complex? "True if the ecology is in the :complex phase." state/complex?)

;; --- Passive dynamics --------------------------------------------------------

(def tick-moisture "Advance moisture one step (gains below 0.6 temp, loses above)." state/tick-moisture)
(def tick-biomass "Advance biomass one step under living, moist, habitable conditions." state/tick-biomass)
(def tick-complexity "Advance complexity one step under the same conditions as biomass." state/tick-complexity)
(def tick-stability "Advance stability one step (recovers when habitable, drains otherwise)." state/tick-stability)
(def tick-temperature "Mean-revert temperature toward 0.5 (climate regulation)." state/tick-temperature)
(def collapse? "True if low stability and meaningful biomass signal collapse." state/collapse?)
(def tick-collapse "Apply collapse-driven biomass loss for one step." state/tick-collapse)
(def passive-tick "Apply all passive dynamics for one ecology tick." state/passive-tick)

;; --- Phase transitions ------------------------------------------------------

(def check-phase-transition "Return the next phase keyword if a transition is warranted, else nil." state/check-phase-transition)
(def record-transition "Append a phase-transition record to the ecology's history." state/record-transition)
(def advance-phase "Move the ecology to `new-phase` and record the transition." state/advance-phase)
(def maybe-advance-phase "Apply the next phase transition if one is warranted; returns [ecology' event]." state/maybe-advance-phase)
(def extinction? "True if biomass fell below the extinction floor while living." state/extinction?)
(def extinguish "Collapse the ecology back to abiotic, preserving its record." state/extinguish)
(def maybe-extinguish "Extinguish if warranted; returns [ecology' event]." state/maybe-extinguish)

;; --- Player abilities --------------------------------------------------------

(def apply-seed "Player ability: seed prebiotic chemistry on a moist abiotic world." abilities/apply-seed)
(def apply-heat "Player ability: raise local temperature by 8% (clamped)." abilities/apply-heat)
(def apply-cool "Player ability: lower local temperature by 8% (clamped)." abilities/apply-cool)
(def apply-spark "Player ability: excite chemistry, boosting biomass and complexity." abilities/apply-spark)
(def apply-grow "Player ability: stimulate replication, requiring prokaryotic+." abilities/apply-grow)
(def apply-evolve "Player ability: apply selection pressure, requiring eukaryotic+." abilities/apply-evolve)

;; --- Autonomous chemistry / self-seeding -------------------------------------

(def prebiotic-drift-rate "Biomass accrual per tick from spontaneous prebiotic chemistry." state/prebiotic-drift-rate)
(def prebiotic-drift "Apply spontaneous prebiotic biomass accrual." state/prebiotic-drift)
(def self-seed-moisture "Moisture threshold for abiotic worlds to self-seed." state/self-seed-moisture)
(def maybe-self-seed "Spontaneously seed an abiotic, habitable, wet world." state/maybe-self-seed)
(def tick-ecology "Run one full ecology update (passive + chemistry + transitions + extinction)." state/tick-ecology)

;; --- ECS integration --------------------------------------------------------

(def adopt-ecology "Create a fresh ecology for a planet from its physical state." system/adopt-ecology)
(def ecology-interval-ticks "Physics ticks between ecology updates." system/ecology-interval-ticks)
(def ecology-system "ECS system that writes c/ecology for all planets." system/ecology-system)
(def emit-phase-events "Emit ledger events for ecology phase changes since the previous world." system/emit-phase-events)