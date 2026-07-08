# ECS Physics Substrate

**Topic:** Unifying gravity, hydro, MHD-lite, thermal, and mass-transfer physics under one ECS substrate.  
**Source:** `claude-physics-merge` conversation chunks (12 files).  
**Status:** Implemented for gravity + hydro + thermal; EM/MHD-lite and full regime classifier remain future slices.

## Invariant

There is exactly one simulation world: the ECS world (`domain.ecs.core`). Every new physical mechanism must attach as a **component** and a **system**, never as a parallel engine or a second renderer. This is enforced by `test/architecture_test.clj`.

## Architectural Decisions

- **Single world model:** `domain.phase0` is a content layer over the ECS, not a separate simulation engine.
- **One renderer:** `infra.render` consumes the ECS world as pure data.
- **Influence registry:** Forces, mass flux, recoil, and torque all flow through `domain.integrator/influence-registry`. A system emits a self-owned influence component on each affected entity; the integrator folds contributors with generic `:sum` accumulation.
- **Single writer per component:** Every component a system writes appears in its registry `:writes`; every read appears in `:reads`.
- **Parallel double-buffer:** Systems read frozen snapshot N and write snapshot N+1; order within the fan-out is irrelevant. Lifecycle reaping uses per-owner `consumed.*` markers.

## Physics Coupled

| Coupling | Components | Systems | Notes |
|---|---|---|---|
| Gravity | `position`, `mass` | `gravity-system` | Barnes-Hut via `domain.gravity.barnes-hut` |
| Hydro/thermal | `density`, `pressure`, `temperature` | `pressure-acceleration`, `eos`, `temperature` | Ideal-gas closure |
| MHD-lite | `magnetic-field` | `em-field`, `lorentz` | Threshold-gated; full curl only where β or Alfvén Mach demands it |
| Regime | `regime` | `regime-system` | β, Mach, Alfvén-Mach, Jeans ratio; Toomre Q, Rayleigh, cooling ratio attach later |
| Mass transfer | `mass-flux-transfer`, `dv-transfer` | `mass-transfer-system` | Donor→sink debit on both entities, single-writer safe |
| Collision | `body-kind`, `radius`, `position` | `collision-detection`, `collision-response` | Merge or elastic bounce |

## Regime Classifier (Current)

Implemented tags:
- `:gravitationally-unstable` — nebula/Jeans unstable
- `:mhd-dominated` — magnetic pressure dominates thermal
- `:gravity-hydro` — gravity + thermal pressure in balance

Future tags (from notes):
- `:gravitationally-unstable` (disc, via Toomre Q)
- `:stable-disc`
- `:convective`
- `:tectonically-dead`

## Key Code-Design Findings

- The coupled-physics spec originally required regime to run before hydro, but the parallel pipeline placed it at the end, making regime tags stale. Reordering is required for correctness.
- The `:stellar-remnant` state was specified but not implemented; stripped stars currently route to `:debris`.
- `wind-rate-scale` defaults to `1.5` (cinematic) instead of `1.0` (physical), a drift from the spec.
- Wind reservoirs are not cleared on star→brown-dwarf demotion, a latent bug.

## Connections to Other Topics

- `phase0-nebula` is the first content layer on this substrate.
- `stellar-mergers-accretion` adds donor/sink and Roche-lobe overflow influences to the same registry.
- `formation-rendering` depends on this substrate because the renderer must consume only ECS data.
- `hops315-fsm` maps the substrate's continuous state into discrete FSM labels.

## Open Questions

- Where exactly does the MHD-lite threshold gate live so it stays single-writer?
- How do we add Toomre-Q and convective tags without breaking the single-writer invariant?
- What is the correct ordering of regime, fusion, and hydro in the parallel pipeline?
