---
uuid: "orbit-integration-regression-tests"
title: "Regression tests for orbit integration (bounded e/a, energy, reversibility)"
status: "review"
priority: "P1"
labels: ["test", "physics", "integrator", "multi-timescale"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/orbit-integration-regression-tests.md"
category: "specs"
estimate: 5
---

# Regression tests for orbit integration

> multi-timescale epic, card 3. Design: `docs/designs/multi-timescale-integration.md` §7.
> The test suite that would have caught the decoherence blocker, and gates the
> merge of card 1. Windowed-equivalence style per
> `.agents/skills/physics-dt-unit-mismatch/` (this is exactly that bug class: a
> per-tick process whose correctness depends on `dt`).

## Grounded integration (cite file:line)
- Drive integration through the real tick: `domain.ecs.tick/run-parallel`
  (`tick.clj:153-180`) with a minimal world (a star + one planet) at realistic
  LATE-sim `dt`/`softening` (mirror live: `dt`≈2.5e9 s, softening≈5e14 m from
  `pacing.clj:42-57`), NOT default cold-start values — the bug only appears at the
  dilated late `dt`.
- Compute orbital elements with the softened two-body form (see
  `stability-softened-elements`, card 5) matching the integrator's actual force.

## Done when
- **(e,a) bounded:** two-body pair over 10^4–10^6 ticks at several K — eccentricity
  and semi-major axis stay bounded, not drifting toward 1. (Fails on today's code;
  passes with `integrator-kepler-substep`.)
- **Energy bounded:** same pair — energy oscillates within a bound, no monotonic
  growth.
- **Reversibility:** integrate N ticks, negate velocities, integrate back —
  return-to-start error under a small tolerance; holds across any rung transition.
- **Stale-slow-field budget:** perturbation-freezing error stays bounded as
  `τ_slow/dt` shrinks.
- **Candidate emergence:** a scripted nebula→star→planets run yields ≥1
  `c/planet-candidate` (the player-visible north star).
- `clojure -M:test` green.

## Risks
Test must use late-sim `dt`/softening or it won't reproduce the bug (the classic
trap this skill warns about). Keep runtimes sane (10^6 ticks on a 2-body world is
cheap; don't scale N).

## Dependencies
Pairs with card 1 (write the failing tests first, per the repo's schema→failing
test→impl workflow). Uses card 5's softened elements.
