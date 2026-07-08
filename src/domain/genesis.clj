(ns domain.genesis
  "Phase 0: Stellar Nebula — composition layer over the ECS substrate.

   This is NOT a separate engine. It bootstraps a normal ECS world, wires the
   stellar/thermal/fusion/collision/observer systems into a tick pipeline, seeds
   a nebula of entities and the player's observer spark, and drives the world
   forward while emitting threshold events into the shared ledger.

   Everything here is pure data transformation; rendering and IO live in infra.

   The implementation is split across `domain.genesis.*` sub-namespaces; this
   namespace is a thin backward-compatible facade that re-exports the public
   entry points."
  (:require
   [domain.genesis.bootstrap :as bootstrap]
   [domain.genesis.tick :as tick]
   [domain.genesis.summary :as summary]
   [domain.genesis.systems :as systems]))

(def create-world
  "Bootstrap a Phase 0 world ready to tick."
  bootstrap/create-world)

(def seed-nebula
  "Seed a cold, rotating, turbulent, self-gravitating gas cloud on the single ECS
   world — `gas-count` equal-mass particles, no pre-placed core or planets."
  bootstrap/seed-nebula)

(def materialize-lifecycle
  "World-construction step (spec §5): spawn the entities requested by the fan-out
   lifecycle emitters (spawn-request.*), then reap every entity marked consumed.*."
  bootstrap/materialize-lifecycle)

(def assert-seed-contracts!
  "Boot-time structural guard. Every seeded matter-state body is folded through a
   law.registry governed by law.stellar/matter-state-contract."
  bootstrap/assert-seed-contracts!)

(def tick-world
  "Advance the world by one tick. Pure: world -> world'."
  tick/tick-world)

(def emit-threshold
  "Emit a threshold event into the ledger at the world's current tick."
  tick/emit-threshold)

(def emit-promotion-events
  "Emit per-body matter-state promotion events between `before` (pre-physics
   snapshot) and `after` (post-physics world)."
  tick/emit-promotion-events)

(def physics-systems-parallel
  "The transform systems as NATIVE write-set systems for the double-buffer fan-out."
  systems/physics-systems-parallel)

(def system-summary
  "Tally the world's resolved matter into the shape used for complexity, phase
   detection, and habitability."
  summary/system-summary)

(def stats-of
  "Observable readouts for the HUD, tallied once per tick from the post-physics
   world and a precomputed `summ`."
  summary/stats-of)

(def thermal-progress
  "Smooth 0→1 measure of how far the system has climbed from cold nebular gas
   (~10 K) toward fusion ignition (~1e7 K), on a log-temperature ramp."
  summary/thermal-progress)

(def center-of-mass
  "Mass-weighted centre of mass of every positioned body, or [0 0 0] when empty."
  summary/center-of-mass)

(def field-report
  "A one-line readout of the live fields for insight."
  summary/field-report)
