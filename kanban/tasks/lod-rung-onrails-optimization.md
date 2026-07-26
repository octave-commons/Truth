---
uuid: "lod-rung-onrails-optimization"
title: "Performance: power-of-two rungs + on-rails Kepler for far/quiescent bodies"
status: "icebox"
priority: "P3"
labels: ["domain", "physics", "integrator", "lod", "multi-timescale", "performance"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/lod-rung-onrails-optimization.md"
category: "specs"
estimate: 5
---

# Performance: rungs + on-rails Kepler

> multi-timescale epic, card 6. Design: `docs/designs/multi-timescale-integration.md` §5.
> OPTIONAL, do only if sub-stepping many close bodies (card 1) becomes a wall-clock
> problem. Correctness must never depend on this layer.

## Grounded integration (cite file:line)
- The existing LOD machinery (`domain.lod/lod-scheduler`, `lod.clj:47-82`;
  `c/lod-level`/`c/lod-tick-phase`, `components.clj:273-274`; gate
  `:lod/throttle-ticks?` read at `integrator/base.clj:70-88`) is currently INERT
  (flag never set true in production) and points the WRONG way (throttles distant
  bodies down). Revive + invert it: rung selection fed by both the dynamical-time
  `K_i` (card 1) and distance/quiescence from the observer focus.
- Far/quiescent, already-circular bodies → coarse rung or a KSP-style **on-rails
  analytic Kepler** propagation (advance the mean anomaly, no perturbation
  integration) until they re-enter focus or a close encounter.
- Rung transitions MUST be block-synchronized (only at a completed sub-step cycle)
  and, if adaptive, gated by a time-symmetric acceptance test (design §4, Dehnen &
  Read 2023) — otherwise reversibility/symplecticity breaks.

## Done when (performance-visible)
- Wall-clock tick cost with many close bodies drops materially with no change in
  the (e,a)/energy/reversibility regression results from card 3.
- On-rails bodies show no position discontinuity when packed/unpacked.

## Risks
Rung-transition artifacts; reversibility violations if transitions aren't
block-synchronized. This is why it's isolated from the correctness cards.

## Dependencies
Cards 1, 3. Iceboxed until sub-step cost is shown to be a real bottleneck.
