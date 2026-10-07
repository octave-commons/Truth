---
uuid: "flight-clock-view-controls"
title: "Expose ordinary View clock controls and truthful admission outcomes"
status: "incoming"
priority: "P1"
points: "3"
labels: "feature, specs, player, clock, spark-flight"
parent: "flight-local-reference-assist-spec"
design: "docs/designs/pre-capture-clock.md"
dependency: "flight-clock-effective-fold"
---

# Expose ordinary View clock controls and truthful admission outcomes

> Design: [docs/designs/pre-capture-clock.md](../../docs/designs/pre-capture-clock.md)
> Research: [clock envelope](../../docs/research/physics/2026-10-07-pre-capture-clock-envelope.md)
> and [finite upward rule](../../docs/research/physics/2026-10-07-clock-up-transition-admission.md).

## Context and outcome

The reviewed clock needs a discoverable ordinary player control. Implement the
bounded View interaction and visible outcome using the existing menu/intent path,
with no domain policy hidden in rendering.

Proposed implementation scope, 3 points, **Incoming**. Parent ownership does
not imply its separate reference-assist acceptance is complete. Configured
planning convergence, canonical Rheos admission and committed RED precede code.

## Scope

- Add seven labelled manual caps (1 s, 10 s, 1 min, 10 min, 1 h, 6 h, 1 day)
  through bounded previous/next controls plus a separate Auto action.
- Enter Manual with the displayed 1-day cap; validate seconds in the domain and
  submit each physical action once through existing queued intents.
- Display requested mode/cap, last consumed seconds/tick and accepted/blocked
  result/fold; Auto after manual stays visibly guarded and shows retained versus
  requested h when blocked.
- Preserve camera controls, existing panel/input behavior and usable bounded
  layout at 1280×720 and 960×540. Follow existing Gate visibility policy while
  retaining domain transition history.

## Non-goals

No clock mathematics or new mode policy, UI library, automatic Fine/D selection,
teleportation, reference selection, camera-follow workaround, native fixture,
frame-rate promise, capture-success badge or new monitoring engine.

## Acceptance criteria

- [ ] Real rendered hitboxes dispatch exactly one validated clock intent per
  activation; cap endpoints and labels match the seven stated seconds values.
- [ ] Actual queue/drain tests preserve all command-time physical channels,
  D, retention, camera/focus and unrelated intents.
- [ ] UI readback distinguishes proposed and consumed steps. A rejection never
  appears as applied; later Auto increases cannot silently become unguarded.
- [ ] Existing View camera controls remain reachable, and clock text/hitboxes
  stay within the panel without covering Actions at both named sizes.
- [ ] Qualified disposable ordinary-native verification records actual View
  input and at least one accepted and one rejected upward result with source,
  before/consumed/published h and visible outcome. Unobserved outcome remains an
  explicit gap; no fixture or state injection supplies this acceptance.

## Dependencies

Implement only after `flight-clock-effective-fold` supplies its qualified contract.

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
