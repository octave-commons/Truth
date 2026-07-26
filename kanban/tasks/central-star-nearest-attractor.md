---
uuid: "central-star-nearest-attractor"
title: "Dominant attractor = nearest bound star, not the most-massive (multi-star fix)"
status: "review"
priority: "P2"
labels: ["domain", "physics", "stellar", "multi-timescale"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/central-star-nearest-attractor.md"
category: "specs"
estimate: 2
---

# Dominant attractor = nearest bound star

> multi-timescale epic, card 4. Design: `docs/designs/multi-timescale-integration.md` §6.
> Small, independently-correct fix. The sim makes MULTI-star systems (5 stars,
> Maus apart), but the classifier assumes one central star — wrong dominant
> attractor for orbit tests and for the Kepler split (card 1).

## Grounded integration (cite file:line)
- `domain.stellar.classifier/central-star` (`classifier.clj:411-434`) currently
  picks the world's single most-massive star (`apply max-key mass`), regardless of
  distance. In a 5-star field a planet orbits its NEAREST bound star, not the
  biggest one across the cloud.
- Change to: per candidate/body, the nearest star to which it is gravitationally
  bound (negative two-body energy). This is also exactly the parent lookup
  `integrator-kepler-substep` (card 1) needs to choose each body's Kepler primary —
  consider exposing a shared `dominant-attractor` helper both call.
- Feeds `candidate-orbit-elements` (`classifier.clj:469-479`) and the handoff gate
  eligibility (`eligible-candidate?`, `classifier.clj:740-762`).

## Done when
- Orbit elements + candidate eligibility are computed against each body's nearest
  bound star; a scripted multi-star run classifies planets sensibly.
- `clojure -M:test` + architecture-test green; `write-conflicts {}`.

## Risks
Low. Ensure "bound" test uses the softened potential (card 5). A body bound to no
star is legitimately not a candidate.

## Dependencies
None. Feeds cards 1 and 5.

## Work notes (2026-07-23)

Gate/classifier scope landed. `dominant-attractor` (classifier.clj): nearest
BOUND star per body (unsoftened two-body energy < 0, per the velocity pairing
rule — classified planets are the sub-stepped population living under the
Newtonian drift); nil when bound to none. `central-star` retained for the
system-level handoff criterion only. classification-system Phases 1–3 and
handoff-system eligibility/record now evaluate per-body parents (handoff
requires the parent be :star, not protostar). The integrator side was already
folded into card 1 (`nearest-stellar-parent`). Tests: two-star fixture —
planet bound to the lighter secondary is governed by it (not the 2 M_sun
primary), hyperbolic body gets no parent, handoff admits with :star-id =
secondary. Hot-reloaded into the live sim: 1024's thermal-band immediately
re-evaluated :frozen → :cold against star 258. Live orbit-stable stays false
in the crowded cluster (Hill-separation proxy vs 23 sibling bodies —
conservative, physically defensible).
