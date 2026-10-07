---
uuid: "flight-clock-upward-admission-law"
title: "Share ordinary Euler advancement and define finite upward-clock admission"
status: "incoming"
priority: "P1"
points: "3"
labels: "feature, specs, player, clock, spark-flight"
parent: "flight-local-reference-assist-spec"
design: "docs/designs/pre-capture-clock.md"
---

# Share ordinary Euler advancement and define finite upward-clock admission

> Design: [docs/designs/pre-capture-clock.md](../../docs/designs/pre-capture-clock.md)
> Research: [clock envelope](../../docs/research/physics/2026-10-07-pre-capture-clock-envelope.md)
> and [finite upward rule](../../docs/research/physics/2026-10-07-clock-up-transition-admission.md).

## Context and outcome

The proposed pre-capture clock requires the actual consumed Euler calculation,
not a second predictor or the incomplete gravity drift cache. Establish the pure
reusable decision before introducing a clock mode or UI.

Proposed implementation scope, 3 points, **Incoming**. Parent ownership does
not imply its separate reference-assist acceptance is complete. Configured
planning convergence, canonical Rheos admission and committed RED precede code.

## Scope

- Extract the canonical ordinary advancement into portable pure arithmetic,
  retaining binary64 operation grouping and existing map/SoA/compact-parent outputs.
- Use existing registered acceleration and raw-impulse reductions; expose the
  directly computed drift and validate every present input channel.
- Add named Malli input/result laws and the finite B=D upward-admission function:
  the specified stable norm/evaluation order, pending control kick, full drift,
  inclusive limits, finite intermediates and explicit unsupported-state reasons.
- Bind evidence to one due ordinary non-absorbing Spark/fold and actual pending
  channels; validate recentering without charging its common shift as motion.

## Non-goals

No clock storage/mode, effective-fold wiring, UI, new force or world writer,
compact integration redesign, prediction-cache repair, epsilon tuning, physical
safety/capture guarantee, local reference or continuous motion limiter.

## Acceptance criteria

- [ ] Existing ordinary map/SoA and compact-parent trajectories retain their
  velocity/position arithmetic and branch behavior under passing characterization.
- [ ] New law accepts exact finite boundaries and rejects separate kick/drift
  violations, including cancellation and raw impulses in design §5.
- [ ] Every registry contributor is tested through actual prepared ordinary
  inputs. Absent channels use existing zero; malformed/nonfinite present values,
  overflow, stale identity, compact/non-due/absorption contexts reject explicitly.
- [ ] Held/released pending control is consumed as supplied; neither input keys
  nor a new h regenerates it. Large frame translations do not spend D; nonfinite
  absolute/recentered outputs reject.
- [ ] No production position/velocity writer is added and no alternate force
  list or SoA predicted-position oracle is introduced.

## Dependencies

No implementation predecessor. The linked design must complete planning review;
this card is not admitted merely by its creation.

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
