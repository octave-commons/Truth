(ns domain.player.economy
  "Coherence, agency, and resonance economy.")

(defn coherence-drain-from-focus "Per-frame coherence drain based on focus-intensity. At default intensity\n   (0.5), drain roughly equals regen — the bar holds steady. At max (1.0),\n   the bar drains in ~30 seconds. At min (0.1), regen dominates." [focus-intensity] (* 0.00075 (double focus-intensity)))

(defn coherence-regen-rate "Per-frame passive coherence regeneration. At default intensity (0.5), regen\n   roughly equals drain — the bar holds steady. At max focus (1.0), regen is\n   zero. At min focus (0.1), regen refills the bar in ~24 seconds." [focus-intensity] (* 0.00075 (- 1.0 (double focus-intensity))))

(defn coherence-gain-from-event "Coherence restored by witnessing a threshold event, with diminishing returns\n   as coherence approaches its maximum." [event-type current-coherence]   (let [base (case event-type :nebula-collapse 0.1 :condensed-core-formation 0.1 :protostar-formation 0.15 :stellar-ignition 0.3 :planet-formation 0.2 :collision 0.1 :phase-transition 0.15 :life-emergence 0.5 :gate-discovery 1.0 0.05)] (* base (- 1.0 current-coherence))))

(defn agency-gain-from-event "Influence quanta granted for witnessing a threshold event. Rarer, more\n   dramatic transitions pay more — a star igniting is worth far more than a\n   routine phase tick. These are the player's earned capacity to act." [event-type]   (case event-type :nebula-collapse 3.0 :condensed-core-formation 3.0 :planetesimal-formation 2.0 :gas-giant-formation 4.0 :brown-dwarf-formation 8.0 :protostar-formation 12.0 :stellar-ignition 25.0 :planet-formation 10.0 :phase-transition 5.0 :collision 1.0 :life-emergence 50.0 :gate-discovery 100.0 0.0))

(defn resonance-gain-from-event "Resonance awarded the FIRST time a given threshold is crossed in a world-line.\n   Unlike agency (which pays every tick), resonance is legacy — it unlocks and\n   intensifies ability slots." [event-type]   (case event-type :nebula-collapse 1 :condensed-core-formation 1 :planetesimal-formation 1 :gas-giant-formation 1 :brown-dwarf-formation 1 :protostar-formation 1 :stellar-ignition 2 :planet-formation 1 :phase-transition 1 :life-emergence 4 :gate-discovery 8 0))

(defn accrue-agency "Add the quanta earned from a seq of witnessed event categories to `observer`." [observer witnessed-events] (update observer :agency (fnil + 0.0) (reduce + 0.0 (map agency-gain-from-event witnessed-events))))

(defn accrue-resonance "Add resonance for threshold event categories the observer has not yet resonated\n   with in this world-line. Returns updated observer with `:resonance` and\n   `:resonance-thresholds` updated." [observer witnessed-events] (let [seen (:resonance-thresholds observer) new-categories (remove seen (distinct witnessed-events)) gain (reduce + 0 (map resonance-gain-from-event new-categories))] (if (pos? gain) (-> observer (update :resonance (fnil + 0.0) gain) (update :resonance-thresholds into new-categories)) observer)))

(defn can-afford? "True if the observer has at least `cost` agency." [observer cost] (>= (double (or (:agency observer) 0.0)) (double cost)))

(defn spend-agency "Deduct `cost` quanta (clamped at zero). Caller should `can-afford?` first." [observer cost] (update observer :agency (fn* [p1__243#] (max 0.0 (- (double (or p1__243# 0.0)) (double cost))))))

(defn apply-coherence "Pure update of an observer's coherence. Drain and regen are per-frame\n   (not sim-time dependent), so the bar moves at a consistent wall-clock rate\n   regardless of simulation speed. Focus-intensity is the lever: high focus\n   drains fast, low focus lets coherence recover. Witnessing events gives bursts." [observer _dt _environmental-complexity witnessed-events] (let [fi (double (:focus-intensity observer 0.5)) drain (coherence-drain-from-focus fi) regen (coherence-regen-rate fi) gains (reduce + 0.0 (map (fn* [p1__244#] (coherence-gain-from-event p1__244# (:coherence observer))) witnessed-events)) coherence' (-> (:coherence observer) (- drain) (+ regen) (+ gains) (max 0.0) (min (:max-coherence observer)))] (-> observer (assoc :coherence coherence') (update :resonance-events (fn* [p1__245#] (into [] (take 100 (concat witnessed-events p1__245#))))))))
