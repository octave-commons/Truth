---
uuid: "universal-compact-substepping"
title: "Universal compact sub-stepping: total-force K-sub-steps when the dominance gate fails (kills the fling machine)"
status: "review"
priority: "P1"
labels: ["domain", "physics", "integrator", "multi-timescale", "blocker"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/universal-compact-substepping.md"
category: "specs"
estimate: 5
---

# Universal compact sub-stepping

> Formation-survival era, card 1. Design: `docs/designs/multi-timescale-integration.md` §9.
> Research: `docs/research/physics/cluster-dispersal-integration-heating.md` §3.3.
> THE fling-machine fix: a compact body whose dominance gate fails currently takes
> ONE raw symplectic-Euler step at dt ≈ 100–600 yr — `a·dt ~ 10⁹ m/s` of Δv per
> tick — an instant ejection. Live: 3 consecutive runs ejected 100% of planets.

## Grounded integration (cite file:line)
- `domain.integrator.kinematics/compact-advance` returns nil when the gate fails
  and the caller falls through to `euler-advance` — one step at the full global
  dt (`src/domain/integrator/kinematics.clj`, the `(or (compact-advance ...)
  (euler-advance ...))` call in `kinematics-cell`). That single step IS the
  fling: at dt=131 yr, a modest 1e-3 m/s² tide injects 4e6 m/s in one tick.
- Fix: the fallthrough becomes **K sub-steps of the TOTAL force** — same
  frozen `Σ accel.*` (already computed in `forces`/`a`), `h = dt/K`, K from the
  same frozen-at-tick-entry `substep-count` criterion (period+acceleration,
  clamp 4096 with log-on-bind). GADGET block-step shape (research §2,
  Springel 2005). No Kepler split, no parent needed — this path also covers
  compact bodies with NO resolvable parent (today's second fallthrough).
- The K loop stays entity-local inside `kinematics-ws`/`kinematics-ws-soa`'s
  one `:run` — parallel-safe, single writer, invisible to the Jacobi barrier,
  no registry change.
- Scope: `substep-matter-states` population only (formed compact bodies). Gas
  parcels keep the single-step Euler path (their dt IS the CFL-safe one).

## Done when (player-visible via live pm2 window)
- A seeded world through planet formation RETAINS bound planets (probe:
  ≥1 planet with negative two-body energy vs its parent after the formation
  era; stretch: e < 0.4) — the live 0-bound streak ends.
- New regression test (notebook §6): a gate-failing body in a fixed tidal
  field gains bounded Δv per tick (no a·dt fling); energy stays in a physical
  envelope over 10³ ticks.
- The full multi-timescale regression suite stays green; `bin/test` green.

## Risks
K sub-steps of the frozen total force is raw leapfrog — secular drift over
LONG horizons (research notebook §2.2) — acceptable: tide-dominated bodies
have no Kepler orbit to protect, and gate-passing bodies never take this
path. Watch tick time: gate-failing bodies multiply their per-tick cost by K;
the genesis log's profile line should be checked live (K clamp logs already
exist). Do NOT weaken the dominance gate to force more bodies onto the Kepler
path — the gate is honest (research §3.3).

## Dependencies
Builds on card 1 of the multi-timescale epic (landed: K machinery, clamp,
x̂-consistent kick). Unblocks candidate emergence together with
`formation-placement-v2`.
