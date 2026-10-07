---
uuid: "flight-clock-effective-fold"
title: "Apply the pre-capture cap on one effective fold with sticky guarded Auto"
status: "incoming"
priority: "P1"
points: "5"
labels: "feature, specs, player, clock, spark-flight"
parent: "flight-local-reference-assist-spec"
design: "docs/designs/pre-capture-clock.md"
dependency: "flight-clock-upward-admission-law"
---

# Apply the pre-capture cap on one effective fold with sticky guarded Auto

> Design: [docs/designs/pre-capture-clock.md](../../docs/designs/pre-capture-clock.md)
> Research: [clock envelope](../../docs/research/physics/2026-10-07-pre-capture-clock-envelope.md)
> and [finite upward rule](../../docs/research/physics/2026-10-07-clock-up-transition-admission.md).

## Context and outcome

The existing tick consumes one input step and publishes pacing's next proposal.
Wire the reviewed decision before dt-dependent systems/caches, preserving that
single path and all pending influences.

Proposed implementation scope, 5 points, **Incoming**. Parent ownership does
not imply its separate reference-assist acceptance is complete. Configured
planning convergence, canonical Rheos admission and committed RED precede code.

## Scope

- Own validated serial clock data: requested cap/mode, automatic proposal,
  consumed-step/fold provenance, sticky manual-use flag and latest decision.
- Resolve Manual after Auto/slip, then use one effective h for every fan-out
  consumer, cache, integration and sim-time increment; preserve cloud softening.
- Apply reviewed increase admission and exact reject/retain policy. Preserve
  never-manual Auto mechanics and guard every later Auto/slip increase after
  manual use, including Gate's removal of the cap until an admitted successor.
- Define initialization/import without prior consumed provenance, FIFO requests,
  disabled adaptive pacing, failed-fold publication and stale-decision rejection.
- Retain :sim/dt as the next proposal surface; label requested/consumed evidence
  distinctly. No approval computed before a changed input survives that change.

## Non-goals

No View widget, relative flight assistance, shortest-orbit global scheduler,
new physical writer, implicit D/retention/velocity/channel rescaling, hidden
lower-h search, all-force limiter, post-commitment neighborhood clock or Gate
producer. Returning Auto does not reset the sticky guard.

## Acceptance criteria

- [ ] Never-manual Auto has the same consumed h, softening, physical results and
  event ordering as its baseline; only explicit clock provenance is additional.
- [ ] A manual request preserves all physical/channel/camera/focus values at
  intent application. The identified first fold alone consumes its accepted h.
- [ ] Real pipeline tests prove one shared h before systems/cache construction
  and exactly that sim-time increment; post-fold pacing cannot erase the guard.
- [ ] Increasing Manual/Auto/slip requests use the actual old pending channel;
  rejection consumes the prior step with reason, without certifying that step.
- [ ] A successful Auto return followed by a failing later slip remains guarded;
  downward/equal transitions are explicit and not advertised as motion-certified.
- [ ] Initialization/import, missing observer, invalid requests, rapid FIFO mode
  changes, disabled adaptive pacing, whole-fold failure and Gate control removal
  follow design §3 without stale approval or fabricated consumed provenance.
- [ ] Imported/adaptive-disabled fractional steps preserve never-manual Auto;
  guarded `0.25→0.5` rejects as unsupported and retains `0.25`. Equal/downward
  fractional steps keep the uncertified legacy path and emitter `q=max(1,h)`.
- [ ] Registry ownership remains complete and the clock uses one serial path;
  no separate player tick or second physical pass exists.

## Dependencies

Implement only after `flight-clock-upward-admission-law` supplies its qualified contract.

The parent's reference-assist and post-capture clock proposals remain separate;
this chain does not declare any external dependency completed.

## Verification

Use meaningful failing law/real-pipeline tests for new behavior; passing baseline
characterization protects the existing arithmetic and route. Commit actual RED
before production, qualify focused tests, full suite and all strict stages, and
record exact source and outcomes through canonical Rheos. Coordinate matched
existing-adapter before/after measurements for any hot-path change. Native work
requires the existing measurement owner's separate resource release and actual
ordinary controls; no tests or native verification ran while drafting this card.

## Risks

B=D permits Cruise-scale travel and is not physical safety, all-force stability,
reference matching or capture. Pending influences and large coordinate rounding
must remain visible. If an additional clock owner or unsupported physical branch
is required, return to scoped planning rather than expanding this story silently.
