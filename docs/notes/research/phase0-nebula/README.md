# Phase 0 Stellar Nebula

**Topic:** Collapse of a stellar nebula to a stable star and candidate planets.  
**Source:** `phase-0` conversation chunks (7 files).  
**Status:** Phase 0 implemented on ECS substrate; a two-path divergence was discovered and resolved; remaining work includes rotational support, disc formation, and full Phase 0→1 handoff.

## Milestone

Phase 0 begins with a nebular mass distribution and ends when one of the following is true:
- A stable star forms and the surrounding system resolves into candidate planets.
- The nebula fails to produce a life-bearing trajectory and transitions into a witnessed conclusion.
- The player's coherence can no longer sustain focus, and the spark releases to another nebular site.

## Two-Path Divergence (Resolved)

A second simulation path was accidentally created: `domain.phase0` (the intended ECS content layer) and `src/domain/particles/` (a parallel particle-field engine). The architecture invariant requires a single substrate, so the parallel path was removed and all physics unified under `domain.ecs.core`.

## Physical Mechanisms in Phase 0

| Mechanism | Status | Notes |
|---|---|---|
| Mutual N-body gravity | Implemented | Barnes-Hut via `domain.gravity.barnes-hut` |
| Jeans instability / collapse | Implemented | `gravitational-collapse-rate`, `collapse-system` |
| Ideal-gas pressure + virial heating | Implemented | `domain.stellar` |
| Radiative cooling | Implemented | Simplified opacity |
| Fusion ignition | Implemented | Hydrogen-burning threshold |
| Inelastic merging | Implemented | `stellar-merge-handler` |
| Stellar wind | Implemented | Reservoir + one-parcel emission, velocity cap |
| Stellar flares | Implemented | Bipolar along spin axis |
| Magnetic field / MHD-lite | Implemented | `domain.em`, `c/b-field` |
| Regime classifier | Partial | β, Mach, Alfvén-Mach, Jeans only |
| Angular momentum | Partial | Component exists, conservation incomplete |
| Rotational support / disc | Missing | Collapse is radial; no oblateness or disc yet |
| Accretion sink | Missing | Stars form by overlap merge |
| Proper induction equation | Missing | B amplified only by flux-freeze + resistive decay |

## Critical Drift from Spec

1. **Regime order:** `regime-system` runs at the end of the parallel pipeline, so regime tags are stale for the systems they should gate. It must run earlier.
2. **Wind default:** `wind-rate-scale` defaults to `1.5` (cinematic) instead of `1.0` (physical).
3. **Stellar remnant state:** `:stellar-remnant` is specified but not implemented; stripped stars route to `:debris`.
4. **Wind reservoir:** Not cleared on star→brown-dwarf demotion.

## Connections to Other Topics

- `ecs-physics-substrate` is the substrate Phase 0 runs on.
- `stellar-mergers-accretion` provides binary outcomes and mass transfer for Phase 0 star systems.
- `hops315-fsm` maps the continuous Phase 0 state into the `:Matter` and `:Role` FSMs.
- `formation-rendering` visualizes the nebula and body formation.
- `deep-research-brief` Sections 1–2 define the physics Phase 0 needs to implement.

## Open Questions

- What is the minimum angular-momentum model that produces rotationally supported discs?
- When does a clump promote from `:nebula` to `:protostar` to `:star`?
- How do we represent brown-dwarf desert and deuterium-burning limits?
- What is the handoff record format that Phase 1 consumes?
