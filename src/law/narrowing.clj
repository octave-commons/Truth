(ns law.narrowing
  "Schemas and tuned constants for gravitational binding — the continuous
   observer<->world coupling of The First Narrowing (child A,
   kanban/tasks/narrowing-binding-mechanic.md; design
   docs/designs/the-first-narrowing-star-to-planet.md §2)."
  (:require
   [malli.core :as m]))

;; --- Schemas ----------------------------------------------------------------

(def binding-schema
  "The `c/binding` component on the observer entity: a map from candidate-world
   entity id (int) to binding strength in [0,1]. 0 = no coupling, 1 = capture."
  [:map-of :int [:and :double [:>= 0.0] [:<= 1.0]]])

(def binding?
  "Predicate: does `value` satisfy `law.narrowing/binding-schema`?"
  (m/validator binding-schema))

(def binding-scar-schema
  "The `c/binding-scar` component on the observer entity: a map from
   candidate-world entity id (int) to a non-negative sunk tally — binding that
   was accrued and then lost before capture. Scars never decrease and are never
   refunded: they are the world-line record that attention was spent here."
  [:map-of :int [:and :double [:>= 0.0]]])

(def binding-scar?
  "Predicate: does `value` satisfy `law.narrowing/binding-scar-schema`?"
  (m/validator binding-scar-schema))

;; --- Tuned constants ----------------------------------------------------------
;; All rates are PER TICK (one frame), not per sim-second — the same convention
;; as the coherence economy (domain.player.economy), so binding moves at a
;; consistent wall-clock rate regardless of simulation time compression.
;; Binding is a player-attention quantity, not a sim-time physical field.

(def ^:const accrual-rate 0.02)
;; Per-tick binding gain while the observer's sustained, overlapping focus is
;; on the world (at neutral habitability/resonance signals). ~50 focused ticks
;; from first contact to full capture.

(def ^:const decay-rate 0.002)
;; Per-tick sticky decay while attention is elsewhere entirely. An order of
;; magnitude slower than accrual: a glance away does not unbind you.

(def ^:const zero-sum-decay-rate 0.01)
;; Additional per-tick decay applied to every OTHER bound world while any one
;; world is accruing — attention is zero-sum, you can only fall one way
;; (design decision 2026-07-22, Aaron, on the card).

(def ^:const scar-fraction 0.1)
;; Fraction of LOST binding that becomes a permanent sunk scar. Pre-capture
;; un-binding is not free: the spent attention is never refunded (design
;; decision 2026-07-22, Aaron, on the card).

(def ^:const focus-intensity-floor 0.5)
;; Observer :focus-intensity at or above which focus counts as SUSTAINED
;; (Focus, Q, held). Below the floor the observer is glancing, not falling.

(def ^:const capture-threshold 0.85)
;; Binding at which the world reaches capture (design §3). Exposed as data for
;; the later commitment/threshold card; nothing consumes it yet.

;; --- Cost-curve tuning ---------------------------------------------------------

(def ^:const nudge-base-cost 1.0)
;; Agency cost of a Nudge/Perturb on a world at binding 0, before leverage.

(def ^:const nudge-leverage 0.9)
;; Fraction of the nudge base cost that full binding discounts. At capture the
;; observer acts at 10% cost — leverage from closeness (design §2.2).

(def ^:const release-tuning 1.0e-6)
;; Scale factor converting the world's specific escape energy GM/R (J/kg) into
;; Agency units for Release/Widen. The SHAPE is the literal escape-energy
;; proxy — this factor is the only tuned knob (design decision 2026-07-22,
;; Aaron, on the card). At 1.0e-6, releasing an Earth-like world
;; (GM/R ~ 6.3e7 J/kg) at full binding costs ~63 Agency.
