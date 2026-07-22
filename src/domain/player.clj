(ns domain.player
  "The player as a quantum oscillation — a coherent spark whose attention is the
   resource. This namespace is a thin facade over the split player sub-modules."
  (:require
   [domain.player.economy :as economy]
   [domain.player.focus :as focus]
   [domain.player.influence :as influence]
   [domain.player.state :as state]
   [domain.player.system :as system]))

;; --- Construction -------------------------------------------------------------

(def ^{:doc "A fresh observer map at the given position."}
  create-observer state/create-observer)

;; --- Coherence mechanics ----------------------------------------------------

(def ^{:doc "Per-frame coherence drain based on focus-intensity."}
  coherence-drain-from-focus economy/coherence-drain-from-focus)

(def ^{:doc "Per-frame passive coherence regeneration."}
  coherence-regen-rate economy/coherence-regen-rate)

(def ^{:doc "Coherence restored by witnessing a threshold event."}
  coherence-gain-from-event economy/coherence-gain-from-event)

(def ^{:doc "Influence quanta granted for witnessing a threshold event."}
  agency-gain-from-event economy/agency-gain-from-event)

(def ^{:doc "Resonance awarded the first time a threshold is crossed."}
  resonance-gain-from-event economy/resonance-gain-from-event)

(def ^{:doc "Add earned agency quanta from witnessed events."}
  accrue-agency economy/accrue-agency)

(def ^{:doc "Add resonance for newly witnessed threshold categories."}
  accrue-resonance economy/accrue-resonance)

(def ^{:doc "True if the observer has at least `cost` agency."}
  can-afford? economy/can-afford?)

(def ^{:doc "Deduct `cost` agency, clamped at zero."}
  spend-agency economy/spend-agency)

(def ^{:doc "True if the observer has at least `cost` resonance."}
  can-afford-resonance? economy/can-afford-resonance?)

(def ^{:doc "Deduct `cost` resonance, clamped at zero."}
  spend-resonance economy/spend-resonance)

(def ^{:doc "Update observer coherence from drain, regen, and witnessed events."}
  apply-coherence economy/apply-coherence)

;; --- Observation / focus ----------------------------------------------------

(def ^{:doc "How strongly the observer's attention resolves reality."}
  observation-effect focus/observation-effect)

(def ^{:doc "Radius within which attention collapses probability into matter."}
  probability-collapse-radius focus/probability-collapse-radius)

(def ^{:doc "Set the observer's focus position, radius, and intensity."}
  set-focus focus/set-focus)

(def ^{:doc "Tighten focus radius and raise intensity."}
  narrow-focus focus/narrow-focus)

(def ^{:doc "Broaden focus radius and lower intensity."}
  widen-focus focus/widen-focus)

;; --- Movement ---------------------------------------------------------------

(def ^{:doc "Move the observer by velocity * dt."}
  drift focus/drift)

(def ^{:doc "Drift toward the focus at `speed`."}
  approach-focus focus/approach-focus)

(def ^{:doc "Drift along a gradient toward interesting regions."}
  release-focus focus/release-focus)

;; --- Influence: the dark halo -----------------------------------------------

(def ^{:doc "Halo mass factor at full coherence and focus intensity."}
  default-halo-mass-factor influence/default-halo-mass-factor)

(def ^{:doc "Per-tick Δv ceiling for influence fields, as a virial-speed multiple."}
  default-influence-dv-cap influence/default-influence-dv-cap)

(def ^{:doc "Influence cutoff in scale radii."}
  halo-reach-factor influence/halo-reach-factor)

(def ^{:doc "Reference mass and Δv cap for influence fields."}
  influence-reference influence/influence-reference)

(def ^{:doc "Observer halo gravitating mass scaled by coherence and focus."}
  halo-mass influence/halo-mass)

(def ^{:doc "Halo acceleration on a body at `body-pos`."}
  observer-acceleration influence/observer-acceleration)

(def ^{:doc "ECS system emitting the halo pull toward the focus."}
  observer-acceleration-system influence/observer-acceleration-system)

;; --- Decoherence / endings --------------------------------------------------

(def ^{:doc "Coherence band label from :highly-coherent to :dissolved."}
  decoherence-state focus/decoherence-state)

(def ^{:doc "True if the observer is still coherent enough to act."}
  can-interact? focus/can-interact?)

(def ^{:doc "True when low coherence and low complexity invite a time slip."}
  time-slip-threshold? focus/time-slip-threshold?)

;; --- ECS integration --------------------------------------------------------

(def ^{:doc "The singleton observer entity id, or nil."}
  observer-entity state/observer-entity)

(def ^{:doc "The observer map from the world."}
  get-observer state/get-observer)

(def ^{:doc "Replace the observer component in the world."}
  put-observer state/put-observer)

(def ^{:doc "Apply f to the observer map in the world."}
  update-observer state/update-observer)

(def ^{:doc "Spawn the singleton observer entity. Returns [world eid]."}
  spawn-observer state/spawn-observer)

;; --- Observer ECS system ----------------------------------------------------

(def ^{:doc "ECS system driving coherence, agency, and observation state."}
  observer-system system/observer-system)
