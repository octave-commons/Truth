(ns domain.player.focus
  "Focus, observation effect, and attention-follow laws."
  (:require
   [shape.spatial :as sp]
   [domain.player.state :as state]))

(defn observation-effect "How strongly the observer's attention resolves reality." [{:keys [coherence focus-intensity]}] (* coherence focus-intensity))

(defn probability-collapse-radius "Radius within which the observer's attention collapses probability into\n   resolved matter." [{:keys [coherence focus-radius]}] (* focus-radius coherence))

(defn set-focus "Set the observer's focus position, radius, and intensity (clamped to [0.1, 1.0])." [observer position radius intensity] (assoc observer :focus-position position :focus-radius radius :focus-intensity (max 0.1 (min 1.0 intensity))))

(defn narrow-focus "Tighten focus radius and raise intensity." [{:keys [focus-radius focus-intensity], :as o} factor] (set-focus o (:focus-position o) (/ focus-radius factor) (min 1.0 (* focus-intensity factor))))

(defn widen-focus "Broaden focus radius and lower intensity." [{:keys [focus-radius focus-intensity], :as o} factor] (set-focus o (:focus-position o) (* focus-radius factor) (max 0.1 (/ focus-intensity factor))))

(defn focus-follow
  "Manual-mode focus-follow (card focus-follows-pilot, design
   docs/designs/spark-flight-and-camera.md §7.5): pin the observer's
   `:focus-position` to the spark's `c/position` plus the player's
   persistent `offset` (world metres — the arrow-nudge delta, owned by
   infra config). The law is POSITION-ONLY, no aim/velocity lead: the
   resolve pipeline keys off focus overlap, and the simplest law that
   makes 'fly up to a planet' accrue binding is focus rides the mote;
   lead/tuning is live-tweak territory and the chase camera (Wave 3
   card 6).

   Resolution order vs manual arrow nudges: there is NO competing writer
   — a nudge edits the offset, not the position, so auto-follow and the
   nudge commute and the nudge always lands (`:focus-position` keeps a
   single writer per mode: this intent in :manual, the camera-target
   sync in tracking modes).

   Pure world → world', applied serially pre-tick through the intent
   queue. Never touches the spark's physical columns: reads `c/position`,
   writes only the `c/observer` attention map."
  [world offset]
  (if-let [obs (state/get-observer world)]
    (if-let [pos (state/observer-position world)]
      (state/put-observer
       world
       (set-focus obs (sp/v+ pos offset) (:focus-radius obs) (:focus-intensity obs)))
      world)
    world))

(defn decoherence-state "Return the coherence band label from :highly-coherent to :dissolved." [{:keys [coherence]}] (cond (> coherence 0.8) :highly-coherent (> coherence 0.5) :coherent (> coherence 0.2) :wavering (> coherence 0.05) :fading :else :dissolved))

(defn can-interact? "True if the observer is still coherent enough to act." [{:keys [coherence]}] (> coherence 0.05))

(defn time-slip-threshold? "Time slips (jumps forward) when coherence is low and there is little\n   observable complexity left to hold attention." [{:keys [coherence]} system-complexity] (and (< coherence 0.3) (< system-complexity 5)))
