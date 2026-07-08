(ns domain.player.focus
  "Focus, observation effect, and movement."
  (:require
   [shape.spatial :as sp]))

(defn observation-effect "How strongly the observer's attention resolves reality." [{:keys [coherence focus-intensity]}] (* coherence focus-intensity))

(defn probability-collapse-radius "Radius within which the observer's attention collapses probability into\n   resolved matter." [{:keys [coherence focus-radius]}] (* focus-radius coherence))

(defn set-focus "Set the observer's focus position, radius, and intensity (clamped to [0.1, 1.0])." [observer position radius intensity] (assoc observer :focus-position position :focus-radius radius :focus-intensity (max 0.1 (min 1.0 intensity))))

(defn narrow-focus "Tighten focus radius and raise intensity." [{:keys [focus-radius focus-intensity], :as o} factor] (set-focus o (:focus-position o) (/ focus-radius factor) (min 1.0 (* focus-intensity factor))))

(defn widen-focus "Broaden focus radius and lower intensity." [{:keys [focus-radius focus-intensity], :as o} factor] (set-focus o (:focus-position o) (* focus-radius factor) (max 0.1 (/ focus-intensity factor))))

(defn drift "Move the observer by velocity * dt." [observer velocity dt] (-> observer (assoc :drift-velocity velocity) (update :position (fn* [p1__246#] (sp/v+ p1__246# (sp/v* velocity dt))))))

(defn- normalize-vec [v] (let [l (sp/len v)] (if (> l 0) (sp/v* v (/ 1.0 l)) v)))

(defn approach-focus "Drift toward the focus at `speed` for `dt`." [{:keys [position focus-position], :as o} speed dt] (drift o (sp/v* (normalize-vec (sp/v- focus-position position)) speed) dt))

(defn release-focus "Let the spark drift along a gradient toward interesting regions, faster when\n   coherence is low." [observer gradient-field] (let [g (gradient-field (:position observer)) speed (* 1.0E12 (- 1.0 (:coherence observer)))] (assoc observer :drift-velocity (sp/v* g speed))))

(defn decoherence-state "Return the coherence band label from :highly-coherent to :dissolved." [{:keys [coherence]}] (cond (> coherence 0.8) :highly-coherent (> coherence 0.5) :coherent (> coherence 0.2) :wavering (> coherence 0.05) :fading :else :dissolved))

(defn can-interact? "True if the observer is still coherent enough to act." [{:keys [coherence]}] (> coherence 0.05))

(defn time-slip-threshold? "Time slips (jumps forward) when coherence is low and there is little\n   observable complexity left to hold attention." [{:keys [coherence]} system-complexity] (and (< coherence 0.3) (< system-complexity 5)))
