# Genesis Arc Separation: Physics Substrate vs. Player Arc

**Status:** draft  
**Date:** 2026-07-02  
**Supersedes:** `docs/specs/decouple-formation-loop-from-phase0.md`  
**Companion docs:**
- `docs/notes/designs/2026.06.25.16.41.16-001-the-core-vision-truth-as-a-physics-first.md`
- `docs/designs/ux-architecture.md`
- `docs/designs/gates-of-truth-world-gen-phases.md`
- `docs/specs/phase0-narrator-presence.md`
- `docs/specs/phase0-player-focus-and-dual-representation.md`
- `test/architecture_test.clj`

---

## Goal

Separate the physical genesis/formation loop from the narrative arc/quest layer.

Truth is a physics-first substrate. The same gravity, hydrodynamics, MHD, and
chemistry run at every scale. The "phases" are not simulation boundaries; they
are the player's **story state**: a quest, a set of layered abilities, and a
progressive narrowing of perspective from cosmic witness to embodied person.

Currently `src/domain/phase0.clj` mixes three things:

1. The physical formation loop (correct).
2. Ongoing planetary/EM/atmosphere physics that belong elsewhere.
3. Arc/quest text and phase detection that belong in a narrative layer.

This spec untangles them so the genesis loop is portable across the whole game
and arcs become a real gameplay layer.

---

## Design choice

### The simulation is substrate; arcs are story state

- A nebula can collapse at any point in the game. The physics does not care
  whether this is "Phase 0" or "Phase 5."
- The player's current arc is determined by what they are witnessing and what
  abilities are narratively available, not by which simulation is running.
- Abilities are added in layers and never removed; their descriptions rewrite as
  the arc advances (`Spark → Self`).
- Threshold events are physical (`:event/stellar-ignition`,
  `:event/planet-formation`). The arc layer interprets them into quest updates,
  coherence gains, and observation notes.
- No arc is announced with a banner. It is felt first, then readable in the
  arc/quest UI.

### Event flow after separation

1. `domain.genesis/tick-world` advances the physical world and emits threshold
   events into the shared ledger.
2. `domain.arc/advance-arc` reads the new world summary and recent threshold
   events, then updates `:arc/current`, `:arc/quest`, `:arc/notification`, and
   coherence gains.
3. `infra.render` reads `:arc/*` and `:genesis/*` state to draw the viewport,
   HUD, and quest/phase UI.

---

## 1. What changes

### 1a. Namespace rename

```
src/domain/phase0.clj  →  src/domain/genesis.clj
```

`domain.genesis` owns only the physical genesis loop:

- `seed-nebula`, `gas-particle-spec`
- `create-world`
- `tick-world`, `step-physics`, `physics-systems-parallel`
- `materialize-lifecycle`
- `system-summary`, `stats-of`
- `emit-threshold` (physics-driven threshold events)
- `center-of-mass`, `frame-offset` helpers

It does **not** own arc detection, quest text, observer input dispatch,
habitability scoring, or ongoing planetary physics.

### 1b. New narrative-arc layer

```
src/domain/arc.clj
```

`domain.arc` owns the story/quest state that was scattered through
`domain.phase0` and `domain.player`:

- `detect-arc` (was `detect-phase`)
- `quest-for` (was `phase-quest`)
- `observation-note`
- `event-notification`
- `advance-arc`
- `ready-to-narrow?` (was `ready-for-phase-1?`)
- `genesis-ending` (was `world-ending`)

`domain.player` keeps the observer entity, focus, coherence resource, and
control mechanics. Arc-facing text moves to `domain.arc`.

### 1c. World-state keys

Physics state (owned by `domain.genesis`):

| Current | New |
|---|---|
| `:phase0/sim-time` | `:genesis/sim-time` |
| `:phase0/time-scale` | `:genesis/time-scale` |
| `:phase0/rate-yr` | `:genesis/rate-yr` |
| `:phase0/stats` | `:genesis/stats` |
| `:phase0/complexity` | `:genesis/complexity` |
| `:phase0/active` | `:genesis/active` |
| `:phase0/adaptive-pacing?` | `:genesis/adaptive-pacing?` |
| `:phase0/wind-rate-scale` | `:genesis/wind-rate-scale` |
| `:phase0/collapse-fraction` | `:genesis/collapse-fraction` |
| `:phase0/contraction-time` | `:genesis/contraction-time` |
| `:phase0/gas-particle-mass` | `:genesis/gas-particle-mass` |
| `:phase0/gas-smoothing-radius` | `:genesis/gas-smoothing-radius` |
| `:phase0/feeding-zone-factor` | `:genesis/feeding-zone-factor` |
| `:phase0/frame-offset` | `:genesis/frame-offset` |
| `:phase0/_prev-summary` | `:genesis/_prev-summary` |
| `:phase0/_summary-cache` | `:genesis/_summary-cache` |
| `:phase0/validate-seed?` | `:genesis/validate-seed?` |
| `:phase0/profile-subsystems?` | `:genesis/profile-subsystems?` |

Arc state (owned by `domain.arc`):

| Current | New |
|---|---|
| `:phase0/phase` | `:arc/current` |
| (new) | `:arc/previous` |
| (new) | `:arc/quest` |
| (new) | `:arc/notification` |
| (new) | `:arc/recent-events` |

### 1d. Arc keywords (formerly `:phase-0/*`)

| Current | New |
|---|---|
| `:phase-0/nebula-collapse` | `:arc/genesis-nebula-collapse` |
| `:phase-0/accretion` | `:arc/genesis-accretion` |
| `:phase-0/protostar` | `:arc/genesis-protostar` |
| `:phase-0/ignition` | `:arc/genesis-ignition` |
| `:phase-0/planets-formed` | `:arc/genesis-planets-formed` |
| `:phase-0/dispersed` | `:arc/genesis-dispersed` |

### 1e. Move arc text out of `domain.player`

Current functions in `src/domain/player.clj` move to `src/domain/arc.clj`:

| Function | New owner | Notes |
|---|---|---|
| `phase-quest` | `domain.arc/quest-for` | Argument becomes arc keyword |
| `observation-note` | `domain.arc/observation-note` | Reads observer state + current arc |
| `event-notification` | `domain.arc/event-notification` | Maps event kind to player-facing text |

`domain.player` keeps:

- observer entity spawning/updating
- focus radius/intensity
- coherence drain/regen
- control mechanics (Drift, Focus, Influence, Release)
- threshold-event agency gains (`:stellar-ignition 25.0`, etc.)

`domain.arc` may call `domain.player/coherence-gain-from-event` or expose a
function `arc-coherence-gain` that `domain.player` consumes.

### 1f. Move ongoing physics out of `domain.genesis`

| Function / system | Current owner | New owner |
|---|---|---|
| `xuv-atmospheric-escape-system` | `domain.phase0` | `domain.atmosphere` |
| `magnetosphere-coupling-system` | `domain.phase0` | `domain.em` |
| `lod-scheduler` | `domain.phase0` | `domain.lod` |
| `habitability-of` | `domain.phase0` | `domain.habitability` |
| `habitable-worlds` | `domain.phase0` | `domain.habitability` |
| `handle-input` | `domain.phase0` | `infra.input` |

### 1g. Handoff / ending rename

`domain.phase0/world-ending` moves to `domain.arc/genesis-ending` and returns
arc outcomes:

| Current | New |
|---|---|
| `:type :success` | `:type :ready-to-narrow` |
| `:type :sterile` | `:type :sterile` |
| `:type :dispersal` | `:type :dispersal` |
| `:type :fadeout` | `:type :fadeout` |

The message for `:ready-to-narrow` keeps the physical description:

> "A world capable of harboring life has formed."

`ready-for-phase-1?` becomes `domain.arc/ready-to-narrow?`, using
`domain.habitability/candidate-worlds`.

---

## 2. ECS / API changes

### New namespaces

- `src/domain/arc.clj` — arc detection, quest text, observation notes,
  notifications, ending detection.
- `src/domain/atmosphere.clj` — receives `xuv-atmospheric-escape-system`.
- `src/domain/lod.clj` — receives `lod-scheduler`.
- `src/domain/habitability.clj` — receives `habitability-of`, `habitable-worlds`.

`infra.input` absorbs `handle-input`.

### Modified system registry

`domain.genesis/physics-systems-parallel` references moved systems by their new
locations:

```clojure
(domain.atmosphere/xuv-atmospheric-escape-system)
(domain.lod/lod-scheduler)
(domain.em/magnetosphere-coupling-system)
```

`domain.arc/advance-arc` is added to the tick pipeline after
`domain.genesis/tick-world`.

### Components unchanged

No component names change. This is a namespace and key migration, not a schema
migration.

---

## 3. Implementation plan

### Phase 1 — Rename `domain.phase0` to `domain.genesis`

**Tests:**
- `create-world-uses-genesis-keys`: `create-world` returns `:genesis/*` keys,
  not `:phase0/*`.
- `tick-world-advances-genesis-sim-time`: `:genesis/sim-time` increases by
  `:sim/dt`.
- `phase0-namespace-is-removed`: no file at `src/domain/phase0.clj`.

**Implementation:**
1. Copy `src/domain/phase0.clj` to `src/domain/genesis.clj`.
2. Replace namespace declaration and `:phase0/` keys.
3. Update every `:require [domain.phase0 ...]` across the codebase.
4. Delete `src/domain/phase0.clj`.

### Phase 2 — Create `domain.arc` and move arc text

**Tests:**
- `arc-quest-returns-text-for-genesis-arcs`: `(quest-for :arc/genesis-ignition)`
  returns a non-empty string.
- `observation-note-uses-arc-and-coherence`: low coherence returns the fade
  warning.
- `event-notification-maps-stellar-ignition`: returns the expected notification
  text.
- `detect-arc-returns-arc-keyword`: from a given summary, returns the correct
  `:arc/genesis-*` keyword.
- `ready-to-narrow-true-when-planets-formed-and-habitable`: returns true iff
  star + habitable candidates exist.
- `world-ending-moved-to-arc`: `domain.arc/genesis-ending` exists and returns
  expected outcomes.

**Implementation:**
1. Create `src/domain/arc.clj`.
2. Move `phase-quest`, `observation-note`, `event-notification` from
   `src/domain/player.clj`.
3. Rename argument from phase keyword to arc keyword.
4. Move `detect-phase`, `ready-for-phase-1?`, `world-ending` from
   `src/domain/genesis.clj`.
5. Add `advance-arc` that consumes summary + recent events and returns arc state
   map.

### Phase 3 — Wire `domain.arc` into the tick pipeline

**Tests:**
- `tick-world-updates-arc-state`: after one tick, `:arc/current` is present.
- `arc-transition-emits-threshold-event`: when arc changes, an
  `:event/phase-transition` (kept for now) is emitted.
- `genesis-active-ignores-arc`: physics ticks even when arc state is unchanged.

**Implementation:**
1. In `domain.genesis/tick-world`, stop calling `detect-phase` and updating
   `:phase0/phase`.
2. Keep emitting physical threshold events (`:event/stellar-ignition`, etc.).
3. After `tick-world`, call `domain.arc/advance-arc` with the new world.
4. The caller (e.g., `infra.dev.server` or a new top-level driver) merges the
   arc state into the world.

### Phase 4 — Move ongoing physics to proper owners

**Tests:**
- `xuv-system-lives-in-domain-atmosphere`
- `magnetosphere-system-lives-in-domain-em`
- `lod-scheduler-lives-in-domain-lod`
- `handle-input-lives-in-infra-input`
- `habitability-functions-live-in-domain-habitability`
- `genesis-systems-table-references-new-owners`

**Implementation:**
1. Create `src/domain/atmosphere.clj`, `src/domain/lod.clj`,
   `src/domain/habitability.clj`.
2. Move functions/systems, adapting imports.
3. Move `handle-input` to `infra.input`.
4. Update `domain.genesis/physics-systems-parallel`.

### Phase 5 — Update consumers

**Tests:**
- `architecture-test-passes`
- `render-reads-genesis-and-arc-keys`
- `player-reads-threshold-events`

**Implementation:**
1. Update `infra.render`, `infra.dev.server`, `infra.repl`, HUD helpers, and test
   fixtures.
2. Run the full test suite.

---

## 4. Out of scope

- Renaming threshold-event kinds (`:event/phase-transition` stays for now).
- Adding new abilities beyond the current Drift/Focus/Influence/Release set.
- Implementing the chat-shell narrator or full Myth Engine integration.
- Modifying physics equations, constants, or simulation behavior.
- Splitting `tick-world` into multiple files beyond the arc-layer separation.

---

## 5. First deliverable

**Phase 1** (rename `domain.phase0` → `domain.genesis`) is the smallest step.
It changes names and keys without moving systems or altering behavior.

**Phase 2** (create `domain.arc` and move text) is the conceptual payoff: arcs
become a real layer, and the physics loop no longer knows it is "Phase 0."

Next action: approve this spec, then write failing tests and begin Phase 1.
