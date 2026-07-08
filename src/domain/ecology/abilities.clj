(ns domain.ecology.abilities
  "Player ability effects on the toy ecology."
  (:require
   [law.ecology :as le]
   [domain.ecology.math :as em]
   [domain.ecology.state :as state]))

(defn apply-seed "Prebiotic seeding. Requires surface mode (enforced by caller) and moisture\n   ≥ 20%. Sets seeded=true and adds +4% biomass seed stock. Returns [ecology'\n   ok? reason]." ([ecology] (apply-seed ecology {})) ([ecology {:keys [ignore-surface?]}] (cond (and (not ignore-surface?) (< (:moisture ecology) 0.2)) [ecology false :seed-failed-moisture] (not= (:phase ecology) le/phase-abiotic) [ecology false :seed-failed-not-abiotic] :else [(-> ecology (assoc :seeded true) (update :biomass em/+c 0.04)) true :seed-ok])))

(defn apply-heat "Raise local temperature by 8% (clamped)." [ecology] [(update ecology :temp em/+c 0.08) true :heat-ok])

(defn apply-cool "Lower local temperature by 8% (clamped)." [ecology] [(update ecology :temp em/-c 0.08) true :cool-ok])

(defn apply-spark "Excite chemistry: +6% biomass, +3% complexity. Requires seeded=true and temp\n   in habitable band (30–75%). Returns [ecology' ok? reason]." [ecology] (cond (not (:seeded ecology)) [ecology false :spark-failed-not-seeded] (not (state/habitable? ecology)) [ecology false :spark-failed-not-habitable] :else [(-> ecology (update :biomass em/+c 0.06) (update :complexity em/+c 0.03)) true :spark-ok]))

(defn apply-grow "Stimulate replication: +12% biomass, +4% complexity. Requires prokaryotic+." [ecology] (if (< (le/phase-index (:phase ecology)) (le/phase-index le/phase-prokaryotic)) [ecology false :grow-locked] [(-> ecology (update :biomass em/+c 0.12) (update :complexity em/+c 0.04)) true :grow-ok]))

(defn apply-evolve "Selection pressure: +15% complexity, −5% stability. Requires eukaryotic+." [ecology] (if (< (le/phase-index (:phase ecology)) (le/phase-index le/phase-eukaryotic)) [ecology false :evolve-locked] [(-> ecology (update :complexity em/+c 0.15) (update :stability em/-c 0.05)) true :evolve-ok]))
