(ns domain.player.focus
  "Focus, observation effect, and movement."
  (:require
   [shape.spatial :as sp]
   [domain.ecs.core :as ecs]
   [domain.ecs.components :as c]
   [domain.player.state :as state]))

(defn observation-effect "How strongly the observer's attention resolves reality." [{:keys [coherence focus-intensity]}] (* coherence focus-intensity))

(defn probability-collapse-radius "Radius within which the observer's attention collapses probability into\n   resolved matter." [{:keys [coherence focus-radius]}] (* focus-radius coherence))

(defn set-focus "Set the observer's focus position, radius, and intensity (clamped to [0.1, 1.0])." [observer position radius intensity] (assoc observer :focus-position position :focus-radius radius :focus-intensity (max 0.1 (min 1.0 intensity))))

(defn narrow-focus "Tighten focus radius and raise intensity." [{:keys [focus-radius focus-intensity], :as o} factor] (set-focus o (:focus-position o) (/ focus-radius factor) (min 1.0 (* focus-intensity factor))))

(defn widen-focus "Broaden focus radius and lower intensity." [{:keys [focus-radius focus-intensity], :as o} factor] (set-focus o (:focus-position o) (* focus-radius factor) (max 0.1 (/ focus-intensity factor))))

(defn drift
  "Manual flight (WASD): translate the spark's `c/position` column by
   `velocity * dt` and return the updated world. A DIRECT per-frame position
   write paced on wall-clock dt — spark-redesign card 4's documented choice
   over a velocity impulse: input written to `c/velocity` would be integrated
   over the fan-out's dilated `:sim/dt`, either teleporting the spark
   (physical dt x UI-scale velocity) or double-counting the motion the frame
   write already made.

   SINGLE-WRITER: pure world->world, invoked on the SIM thread only — the
   render/input thread enqueues it through the IntentAtom
   (infra.dev.window.loop/drain-intents), which sequences it before the
   tick, so the sim thread is the sole writer of `c/position` (this intent +
   the `:motion` system) and no drift can be lost to the tick's publish.

   Gravity COMPOSES with the override instead of fighting it: the integrator
   still sums every accel channel into the spark's `c/velocity` and advances
   the position by it every tick, so thrust displacements and gravitational
   drift add. While the player flies, the wall-clock displacement dominates
   (input wins); on release, the gravity-accumulated velocity carries the
   spark on and the wells bend it into a fall or an orbit."
  [world velocity dt]
  (if-let [eid (state/observer-entity world)]
    (if-let [pos (ecs/get-component world eid c/position)]
      (ecs/put-component world eid c/position (sp/v+ pos (sp/v* velocity dt)))
      world)
    world))

(defn decoherence-state "Return the coherence band label from :highly-coherent to :dissolved." [{:keys [coherence]}] (cond (> coherence 0.8) :highly-coherent (> coherence 0.5) :coherent (> coherence 0.2) :wavering (> coherence 0.05) :fading :else :dissolved))

(defn can-interact? "True if the observer is still coherent enough to act." [{:keys [coherence]}] (> coherence 0.05))

(defn time-slip-threshold? "Time slips (jumps forward) when coherence is low and there is little\n   observable complexity left to hold attention." [{:keys [coherence]} system-complexity] (and (< coherence 0.3) (< system-complexity 5)))
