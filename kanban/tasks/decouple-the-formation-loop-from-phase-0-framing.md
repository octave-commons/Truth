---
uuid: "decouple-the-formation-loop-from-phase-0-framing"
title: "Decouple the Formation Loop from Phase-0 Framing"
status: "rejected"
priority: "P1"
labels: ["specs"]
created_at: "2026-07-08T02:24:29.624013616Z"
source: "kanban/tasks/decouple-the-formation-loop-from-phase-0-framing.md"
category: "specs"
---

# Decouple the Formation Loop from Phase-0 Framing

**Status:** draft  
**Date:** 2026-07-02  
**Companion docs:** `docs/designs/gates-of-truth-world-gen-phases.md`,
`docs/designs/truth-phase-0-stellar-nebula-design.md`,
`docs/designs/phase0-coupled-physics-and-regime-classifier.md`,
`test/architecture_test.clj`.

***

## Goal

Make the stellar-formation gameplay loop **portable across the whole game**.
Nebular collapse, star birth, disk accretion, and planetary coalescence are
ongoing universal processes, not a one-time prologue. The current namespace
`domain.phase0` and its `:phase0/*` world keys imply a locked, disposable stage.
This spec renames the loop to match what it actually does, evicts concerns that
belong elsewhere, and preserves the single ECS substrate.

***

## Design choice

Phases are **soft developmental landmarks** and **threshold events**, not
namespace boundaries or parallel simulations. The same gravitational,
hydrodynamic, electromagnetic, and chemical laws operate across all timescales.
What changes is the player's focal scale and the dominant regime, not the
underlying physics.

Therefore:

- The formation-loop driver gets a physical name, not a phase number.
- World-state keys describe physical system state, not stage progress.
- Internal phase-detection keywords become `:threshold/*` or `:landmark/*`
  events, never hard gates.
- Systems that are physically ongoing (atmosphere escape, magnetosphere
  coupling, LOD scheduling, habitability scoring) move to their proper
  `domain/` owners.

***

## 1. What changes

### 1a. Namespace rename

```
src/domain/phase0.clj  →  src/domain/formation.clj
```

The new namespace owns:

- Nebula seeding (`seed-nebula`, `gas-particle-spec`).
- World bootstrap for a new formation play-through (`create-world`).
- The tick pipeline (`tick-world`, `step-physics`, `physics-systems-parallel`).
- Lifecycle materialization (`materialize-lifecycle`).
- System-summary and observable stats (`system-summary`, `stats-of`).
- Threshold emission and landmark detection (`emit-threshold`,
  `detect-landmark`).

It does **not** own planetary atmosphere escape, magnetosphere coupling,
observer input dispatch, or cross-epoch habitability scoring.

### 1b. World-state keys

Current key                          New key
`:phase0/sim-time`                   `:formation/sim-time`
`:phase0/time-scale`                 `:formation/time-scale`
`:phase0/rate-yr`                    `:formation/rate-yr`
`:phase0/stats`                      `:formation/stats`
`:phase0/complexity`                 `:formation/complexity`
`:phase0/phase`                      `:formation/landmark`   (value also changes, see 1c)
`:phase0/active`                     `:formation/active`
`:phase0/adaptive-pacing?`           `:formation/adaptive-pacing?`
`:phase0/wind-rate-scale`            `:formation/wind-rate-scale`
`:phase0/collapse-fraction`          `:formation/collapse-fraction`
`:phase0/contraction-time`           `:formation/contraction-time`
`:phase0/gas-particle-mass`          `:formation/gas-particle-mass`
`:phase0/gas-smoothing-radius`       `:formation/gas-smoothing-radius`
`:phase0/feeding-zone-factor`        `:formation/feeding-zone-factor`
`:phase0/frame-offset`               `:formation/frame-offset`
`:phase0/_prev-summary`              `:formation/_prev-summary`
`:phase0/_summary-cache`             `:formation/_summary-cache`

`:phase0/validate-seed?`             `:formation/validate-seed?`
`:phase0/profile-subsystems?`        `:formation/profile-subsystems?`

### 1c. Landmark keywords (formerly `:phase-0/*`)

Current keyword                      New keyword
`:phase-0/nebula-collapse`           `:landmark/nebula-collapse`
`:phase-0/accretion`                 `:landmark/accretion`
`:phase-0/protostar`                 `:landmark/protostar`
`:phase-0/ignition`                  `:landmark/stellar-ignition`
`:phase-0/planets-formed`            `:landmark/planets-formed`
`:phase-0/dispersed`                 `:landmark/dispersed`

`detect-phase` renames to `detect-landmark` and returns a `:landmark/*`
keyword. Threshold events keep their existing kinds
(`:event/stellar-ignition`, `:event/planet-formation`, `:event/phase-transition`)
for now; the third may later become `:event/landmark-transition` in a separate
spec.

### 1d. System moves

| Function / system | Current owner | New owner | Reason |
|---|---|---|---|
| `xuv-atmospheric-escape-system` | `domain.phase0` | `domain.atmosphere` | Ongoing planetary physics |
| `magnetosphere-coupling-system` | `domain.phase0` | `domain.em` | Stellar-wind / B-field coupling |
| `lod-scheduler` | `domain.phase0` | `domain.lod` | Observer-centric level of detail |
| `handle-input` | `domain.phase0` | `infra.input` | Input dispatch lives in `infra/` |
| `habitability-of` | `domain.phase0` | `domain.habitability` | Cross-epoch habitability scoring |
| `habitable-worlds` | `domain.phase0` | `domain.habitability` | Cross-epoch candidate search |
| `ready-for-phase-1?` | `domain.phase0` | `domain.habitability` | Soft handoff predicate, rename to `ready-for-settlement?` |

### 1e. Stats read model

`stats-of` stays in `domain.formation` because it tallies the *current*
formation play-through. New keys added by moved systems (`:xuv-escape-count`,
`:sed-band-count`, LOD counts) remain in the stats map; they are sourced from
components that any system may write.

### 1f. World-ending predicates

`world-ending` stays in `domain.formation` but renames outcomes:

| Current | New |
|---|---|
| `:type :success` | `:type :ready-for-settlement` |
| `:type :sterile` | `:type :sterile` |
| `:type :dispersal` | `:type :dispersal` |
| `:type :fadeout` | `:type :fadeout` |

The success message changes to reflect that this is one possible handoff, not
"beating Phase 0":

> "A world capable of harboring life has formed."

kept verbatim because it already describes the physical outcome.

***

## 2. ECS / API changes

### New namespaces

- `src/domain/atmosphere.clj` — receives `xuv-atmospheric-escape-system`.
- `src/domain/lod.clj` — receives `lod-scheduler`.
- `src/domain/habitability.clj` — receives `habitability-of`,
  `habitable-worlds`, `ready-for-settlement?`.

`infra.input` is extended with `handle-input` (or the caller already in
`infra/` absorbs it).

### Modified system registry

`domain.formation/physics-systems-parallel` references the moved systems by
their new locations:

```clojure
(domain.atmosphere/xuv-atmospheric-escape-system)
(domain.lod/lod-scheduler)
(domain.em/magnetosphere-coupling-system)
```

### Components unchanged

No component names change. This is a namespace and key migration, not a schema
migration.

***

## 3. Implementation plan

### Phase 1 — Rename in place

**Tests:**

- `create-world-uses-formation-keys`: `create-world` returns a world whose
  keys are prefixed `:formation/`, not `:phase0/`.
- `tick-world-advances-formation-sim-time`: after one tick,
  `:formation/sim-time` increases by `:sim/dt`.
- `detect-landmark-returns-landmark-keyword`: `detect-landmark` returns a
  keyword in the `:landmark/*` namespace.
- `phase0-namespace-is-removed`: no file exists at `src/domain/phase0.clj`.

**Implementation:**

1. Copy `src/domain/phase0.clj` to `src/domain/formation.clj`.
2. Replace namespace declaration and all `:phase0/` keys and `:phase-0/`
   keywords.
3. Rename `detect-phase` → `detect-landmark`.
4. Rename `ready-for-phase-1?` → `ready-for-settlement?`.
5. Update every `:require [domain.phase0 ...]` across the codebase.
6. Delete `src/domain/phase0.clj`.

### Phase 2 — Move systems to proper owners

**Tests:**

- `xuv-system-lives-in-domain-atmosphere`: requiring `domain.atmosphere`
  provides `xuv-atmospheric-escape-system`.
- `magnetosphere-system-lives-in-domain-em`: requiring `domain.em` provides
  `magnetosphere-coupling-system`.
- `lod-scheduler-lives-in-domain-lod`: requiring `domain.lod` provides
  `lod-scheduler`.
- `handle-input-lives-in-infra-input`: `infra.input` exposes `handle-input`.
- `habitability-functions-live-in-domain-habitability`: requiring
  `domain.habitability` provides `habitability-of`, `habitable-worlds`, and
  `ready-for-settlement?`.
- `formation-systems-table-references-new-owners`: `physics-systems-parallel`
  includes the moved systems from their new namespaces.

**Implementation:**

1. Create `src/domain/atmosphere.clj`, `src/domain/lod.clj`,
   `src/domain/habitability.clj`.
2. Move the corresponding functions/systems, preserving docstrings and
   adapting imports.
3. Move `handle-input` to `infra.input`.
4. Update `domain.formation/physics-systems-parallel` to require and call the
   new locations.

### Phase 3 — Update consumers

**Tests:**

- `architecture-test-passes`: `test/architecture_test.clj` still passes.
- `render-reads-formation-keys`: renderer or dev HUD reads
  `:formation/stats` and `:formation/landmark`.
- `player-reads-threshold-events`: `domain.player` still receives
  `:event/stellar-ignition`, `:event/planet-formation`, and
  `:event/phase-transition`.

**Implementation:**

1. Update `infra.render`, `infra.dev.server`, `infra.repl`, and any dev tools
   that read `:phase0/*` keys.
2. Update any test fixtures or seed helpers.
3. Run the full test suite.

***

## 4. Out of scope

- Renaming threshold-event kinds (`:event/phase-transition` stays for now).
- Changing gameplay-loop boundaries or adding new phases.
- Modifying physics equations, constants, or simulation behavior.
- Splitting `tick-world` into multiple files; this is a naming/portability pass.

***

## 5. First deliverable

**Phase 1** (namespace rename in place) is the smallest step. It changes names
and keywords without moving systems or altering behavior. It makes the file's
actual job explicit and clears the way for the physical system moves.

Next action: approve this spec, then write failing tests and begin Phase 1.

---
Triage 2026-07-10 (todo→rejected): STALE-SUPERSEDED — explicitly superseded by genesis-arc-separation (which shipped as domain.genesis+domain.arc with :genesis/* keys); the proposed domain.formation / :formation/* naming was never adopted.
---
