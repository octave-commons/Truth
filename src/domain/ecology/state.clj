(ns domain.ecology.state
  "Ecology state, predicates, passive dynamics, phase transitions, and extinction."
  (:require
   [domain.ecology.math :as math]
   [law.ecology :as le]
   [domain.ecs.event :as event]))

(defn make-ecology "Create a fresh ecology state. `phase` defaults to :abiotic; `seeded` to false." ([] (make-ecology {})) ([overrides] (merge {:moisture 0.0, :temp 0.5, :biomass 0.0, :complexity 0.0, :stability 0.5, :phase le/phase-abiotic, :seeded false, :record []} overrides)))

(defn habitable? "Temperature inside the habitable band (0.25, 0.75)." [{:keys [temp]}] (and (> (double temp) le/habitability-low) (< (double temp) le/habitability-high)))

(defn living? "Phase is beyond prebiotic — biomass growth and complexity gain apply." [{:keys [phase]}] (not (#{le/phase-abiotic le/phase-prebiotic} phase)))

(defn abiotic? "True if the ecology is in the :abiotic phase." [{:keys [phase]}] (= phase le/phase-abiotic))

(defn prebiotic? "True if the ecology is in the :prebiotic phase." [{:keys [phase]}] (= phase le/phase-prebiotic))

(defn prokaryotic? "True if the ecology is in the :prokaryotic phase." [{:keys [phase]}] (= phase le/phase-prokaryotic))

(defn eukaryotic? "True if the ecology is in the :eukaryotic phase." [{:keys [phase]}] (= phase le/phase-eukaryotic))

(defn multicellular? "True if the ecology is in the :multicellular phase." [{:keys [phase]}] (= phase le/phase-multicellular))

(defn complex? "True if the ecology is in the :complex phase." [{:keys [phase]}] (= phase le/phase-complex))

(defn tick-moisture "Moisture gains when temp < 0.6, loses when temp ≥ 0.6." [{:keys [moisture temp]}] (math/clamp01 (if (< (double temp) 0.6) (+ moisture le/moisture-gain-rate) (- moisture le/moisture-loss-rate))))

(defn tick-biomass "Biomass grows only in a living phase under habitable, moist conditions." [{:keys [biomass], :as ecology}] (math/clamp01 (if (and (living? ecology) (habitable? ecology) (> (:moisture ecology) 0.1)) (+ biomass le/biomass-gain-rate) biomass)))

(defn tick-complexity "Complexity grows under the same conditions as biomass." [{:keys [complexity], :as ecology}] (math/clamp01 (if (and (living? ecology) (habitable? ecology) (> (:moisture ecology) 0.1)) (+ complexity le/complexity-gain-rate) complexity)))

(defn tick-stability "Stability drains when conditions are hostile, recovers when habitable." [{:keys [stability], :as ecology}] (math/clamp01 (if (habitable? ecology) (+ stability le/stability-gain-rate) (- stability le/stability-loss-rate))))

(defn tick-temperature "Temperature mean-reverts toward 0.5 (climate regulation)." [{:keys [temp]}] (math/clamp01 (+ temp (* (- 0.5 temp) le/temperature-reversion-rate))))

(defn collapse? "True if stability is critically low and there is meaningful biomass." [{:keys [stability biomass]}] (and (< (double stability) le/collapse-stability-threshold) (> (double biomass) le/collapse-biomass-threshold)))

(defn tick-collapse "During collapse, biomass drops by 2% per tick." [{:keys [biomass], :as ecology}] (if (collapse? ecology) (math/clamp01 (- biomass le/collapse-biomass-loss-rate)) biomass))

(defn passive-tick "Apply one passive ecology tick (design doc §5). Returns the updated ecology\n   map. Does NOT evaluate phase transitions — call `check-phase-transition`\n   separately so the caller can emit events and record the transition." [ecology] (let [m' (tick-moisture ecology) t' (tick-temperature ecology) s' (tick-stability ecology) e1 (assoc ecology :moisture m' :temp t' :stability s') b' (tick-biomass e1) e2 (assoc e1 :biomass b') b'' (tick-collapse e2) e3 (assoc e2 :biomass b'') c' (tick-complexity e3)] (assoc e3 :complexity c')))

(defn check-phase-transition "Return the next phase keyword if a transition should occur, else nil." [ecology] (letfn [(next-phase [{:keys [phase seeded moisture biomass complexity stability temp]}] (case phase :abiotic (when (and seeded (> (double moisture) le/moisture-prebiotic-threshold)) le/phase-prebiotic) :prebiotic (when (and (> (double biomass) le/biomass-prokaryotic-threshold) (< (double temp) le/temp-prokaryotic-max)) le/phase-prokaryotic) :prokaryotic (when (and (> (double biomass) le/biomass-eukaryotic-threshold) (> (double complexity) le/complexity-eukaryotic-threshold)) le/phase-eukaryotic) :eukaryotic (when (and (> (double complexity) le/complexity-multicellular-threshold) (> (double stability) le/stability-multicellular-threshold)) le/phase-multicellular) :multicellular (when (and (> (double complexity) le/complexity-complex-threshold) (> (double biomass) le/biomass-complex-threshold)) le/phase-complex) :complex nil))] (next-phase ecology)))

(defn record-transition "Append a transition record for `new-phase` at `tick` with current biomass\n   and complexity snapshot." [ecology tick new-phase] (update ecology :record conj {:tick tick, :phase new-phase, :biomass (:biomass ecology), :complexity (:complexity ecology)}))

(defn advance-phase "Move ecology to `new-phase`, recording the transition at `tick`." [ecology tick new-phase] (-> ecology (assoc :phase new-phase) (record-transition tick new-phase)))

(defn maybe-advance-phase "If a phase transition is warranted, apply it and return [ecology' event],\n   else [ecology nil]. `tick` is the current simulation tick; `body-eid` is the\n   planetary entity id for the event." [ecology tick body-eid] (if-let [new-phase (check-phase-transition ecology)] (let [ecology' (advance-phase ecology tick new-phase) event (event/->event {:tick tick, :kind :event/ecology-phase-transition, :entities #{body-eid}, :payload {:from (:phase ecology), :to new-phase, :biomass (:biomass ecology'), :complexity (:complexity ecology')}})] [ecology' event]) [ecology nil]))

(defn extinction? "True if biomass dropped below the extinction floor while in a living phase." [{:keys [phase biomass], :as ecology}] (and (living? ecology) (< (double biomass) le/extinction-biomass-floor) (not= phase le/phase-abiotic)))

(defn extinguish "Collapse the ecology back to abiotic: biomass/complexity zero, seeded false,\n   record preserved." [ecology] (assoc ecology :phase le/phase-abiotic :seeded false :biomass 0.0 :complexity 0.0))

(defn maybe-extinguish "If extinction conditions are met, return [ecology' event], else [ecology nil]." [ecology tick body-eid] (if (extinction? ecology) (let [ecology' (extinguish ecology) event (event/->event {:tick tick, :kind :event/ecology-extinction, :entities #{body-eid}, :payload {:previous-phase (:phase ecology), :stability (:stability ecology), :biomass (:biomass ecology)}})] [ecology' event]) [ecology nil]))

(def ^{:const true} prebiotic-drift-rate "Biomass accrual per ecology tick from spontaneous prebiotic chemistry —\n   1/6 of the living growth rate; crossing the prokaryotic threshold unaided\n   takes ~300 ecology ticks of sustained warm-wet conditions." (/ le/biomass-gain-rate 6.0))

(defn prebiotic-drift "Apply spontaneous prebiotic biomass accrual: only in :prebiotic phase, only\n   while habitable and moist." [ecology] (if (and (prebiotic? ecology) (habitable? ecology) (> (double (:moisture ecology)) 0.25)) (update ecology :biomass math/+c prebiotic-drift-rate) ecology))

(def ^{:const true} self-seed-moisture "Moisture above which an abiotic habitable world seeds its own prebiotic\n   chemistry (shallow warm seas). Drier worlds wait for the player's Seed." 0.3)

(defn maybe-self-seed "Spontaneous seeding: an abiotic, habitable, wet world becomes seeded." [ecology] (if (and (abiotic? ecology) (not (:seeded ecology)) (habitable? ecology) (>= (double (:moisture ecology)) self-seed-moisture)) (assoc ecology :seeded true) ecology))

(defn tick-ecology "Run one full ecology update on `ecology` for entity `body-eid` at `tick`:\n   passive tick, autonomous chemistry, phase transition, extinction.\n   Returns [ecology' events]." [ecology tick body-eid] (let [ecology' (-> ecology passive-tick maybe-self-seed prebiotic-drift) [ecology'' evt1] (maybe-advance-phase ecology' tick body-eid) [ecology''' evt2] (maybe-extinguish ecology'' tick body-eid)] [ecology''' (vec (keep identity [evt1 evt2]))]))
