(ns law.narrowing
  "Schemas and tuned constants for gravitational binding — the continuous
   observer<->world coupling of The First Narrowing (child A,
   kanban/tasks/narrowing-binding-mechanic.md; design
   docs/designs/the-first-narrowing-star-to-planet.md §2)."
  (:require
   [malli.core :as m]
   [law.stellar :as law-stellar]))

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

;; --- Commitment horizon (child B) ---------------------------------------------

(def commitment-state-schema
  "The `c/commitment-state` component on a candidate world: `:committed` on the
   one captured world, `:inert` on every unchosen candidate (visible, no longer
   interactive — commitment-and-resonance.md §4.3). Write-once: capture is
   hard-irreversible for the world-line, so no transition out of these states
   exists at the system level."
  [:enum :committed :inert])

(def commitment-state?
  "Predicate: does `value` satisfy `law.narrowing/commitment-state-schema`?"
  (m/validator commitment-state-schema))

(def ability-schema
  "An allocatable hotbar ability. Genesis palette (commitment-and-resonance.md
   §3) and Phase 1 planetary palette (§4.4) share the same six slots."
  [:enum :seed :heat :cool :spark :grow :evolve
   :atmosphere :hydrography :tectonics :orbit :biosphere :culture])

(def palette-schema
  "The `c/palette` component on the observer entity: which palette is active
   and which ability each of the six allocatable slots is armed with."
  [:map
   [:active [:enum :genesis :planetary]]
   [:slots [:map-of [:int {:min 1 :max 6}] ability-schema]]])

(def palette?
  "Predicate: does `value` satisfy `law.narrowing/palette-schema`?"
  (m/validator palette-schema))

(def time-lock-schema
  "The `c/time-lock` component stamped on the committed world at capture — the
   planetary time-lock of commitment-and-resonance.md §5.1: the committed world
   and its immediate neighborhood run at base rate (one simulation second per
   wall second); everything outside is sub-cycled. This record is the DATA
   HOOK only: pacing (`:sim/dt`) actuation and lod-scheduler sub-cycle
   rewiring against it are a later card."
  [:map
   [:locked? [:= true]]
   [:captured-tick :int]
   [:base-rate [:= 1.0]]
   [:neighborhood [:= :immediate]]
   [:outside [:= :sub-cycled]]])

(def time-lock?
  "Predicate: does `value` satisfy `law.narrowing/time-lock-schema`?"
  (m/validator time-lock-schema))

(def genesis-palette
  "The Genesis allocatable palette (commitment-and-resonance.md §3): the six
   slots before capture. Declared for completeness — nothing writes `c/palette`
   pre-capture yet (the live Genesis hotbar is the infra-side keymap
   `infra.render.input/action-palette`; modelling it domain-side is a later
   card)."
  {:active :genesis
   :slots  {1 :seed 2 :heat 3 :cool 4 :spark 5 :grow 6 :evolve}})

(def planetary-palette
  "The Phase 1 planetary palette (commitment-and-resonance.md §4.4), re-armed
   IN PLACE over the same six slots at capture."
  {:active :planetary
   :slots  {1 :atmosphere 2 :hydrography 3 :tectonics
            4 :orbit 5 :biosphere 6 :culture}})

(def phase-1-unlock-costs
  "Resonance unlock cost per Phase 1 palette ability (§4.4). Data for the
   allocation/respec card; nothing consumes it yet."
  {:atmosphere 0 :hydrography 0 :tectonics 1 :orbit 1 :biosphere 2 :culture 2})

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

(def ^:const world-focus-radius
  "Overlap radius (m) for 'the observer's focus is ON this specific world',
   consumed by `domain.narrowing/focus-overlap?` (NOT the observer's
   `:attention-shell :immediate-r`, which is the unrelated whole-system
   `:focus-zone` regional-cell radius — ~4.0e15 m / ~26,700 AU — reusing it
   here made binding accrue on every candidate world at once, passively,
   regardless of where the player actually pointed; see
   kanban/tasks/narrowing-worldscale-overlap-gate.md).

   Sized to planetary distances, one AU (`law.stellar/au`): the characteristic
   scale of a single world's own local neighborhood (an Earth-Sun distance),
   four orders of magnitude tighter than the old system-wide shell and small
   compared to the ~0.1-30 AU span candidate worlds occupy across the disk
   (`domain.planet-formation.seed/min-planet-orbit-radius-au` /
   `planet-seeding-outer-au`), so a focus aimed at one world's position does
   not also reach a neighboring candidate. No richer per-candidate world/orbit
   radius is available where this predicate reads: `binding-system` only reads
   each candidate's `c/position`, not its full `c/planet-candidate` record (no
   physical radius field exists there either) — a fixed, honestly-labelled
   distance is the simplest scale that is not a lie about what data backs it."
  law-stellar/au)

(def ^:const capture-threshold 0.85)
;; Binding at which the world reaches capture (design §3): the point past which
;; the exit cost exceeds any reserve the player can hold. Consumed by the
;; `:commitment` fan-out system (domain.narrowing/commitment-system).

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
