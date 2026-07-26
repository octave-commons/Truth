---
uuid: "star-substep-heating"
title: "Star sub-stepping: star–star encounters stop heating the cluster"
status: "todo"
priority: "P2"
labels: ["domain", "physics", "integrator", "multi-timescale"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/star-substep-heating.md"
category: "specs"
estimate: 5
---

# Star sub-stepping

> Formation-survival era, card 3. Research:
> `docs/research/physics/cluster-dispersal-integration-heating.md` §3.1.
> Stars/protostars never sub-step (`kinematics/stellar-parent-states` only
> parents planets; `substep-matter-states` excludes stars by design — "cluster
> orbits well-resolved at global dt"), but close star–star encounters at
> dt ≈ 100–600 yr/tick measurably heat the cluster: the coarse probe run sits
> ~48% hotter than baseline at matched sim-time. Heating accelerates the
> dispersal → R₉₀ → softening feedback loop.

## Grounded integration (cite file:line)
- `src/domain/integrator/kinematics.clj`: `substep-matter-states` currently
  `#{:planet :gas-giant :stellar-remnant}`; extend to `#{:star :protostar ...}`
  with parent = dominant OTHER star (nearest bound, the same
  `nearest-stellar-parent` machinery generalized to accept star parents of
  stars). The kick decomposition already subtracts the pair term both ways.
- Careful with hierarchy: in an equal-mass 5-star cluster there may be no
  clean dominant parent — the WH split degrades to "nearest neighbour as
  Kepler primary + all others as kick", which is exactly MERCURY's democratic
  heuristic and is fine for encounter resolution.
- The universal sub-stepping card (`universal-compact-substepping`) covers
  the gate-fail path; this card widens the gate-passing population.

## Done when
- Heating regression (notebook §6): isolated 2-star + 1-planet cluster at
  live dt — total energy bounded, no monotonic growth (pre-fix: growth).
- Probe re-run: stellar-subset energy drift per 1800 ticks measurably below
  the baseline ~25%/1800-tick figure.
- `bin/test` green.

## Risks
Binary/hierarchical stability: a tight binary's mutual orbit must sub-step
about EACH OTHER, not a distant third star — the parent lookup already picks
nearest; verify a bound binary survives 10⁴ ticks. Tick-time: sub-stepping
stars multiplies their per-tick cost; K is small for cluster orbits
(periods ≥ 10³ yr vs dt ≤ 600 yr → K ≈ 20–50 typically).

## Dependencies
`universal-compact-substepping` (same machinery). Slows the dispersal
feedback that `compact-pair-softening` (landed) and the eventual
halo/dispersal investigation care about.
