---
uuid: "flight-local-reference-assist-spec"
title: "Specify explicit local reference and bounded linear flight assist"
status: "incoming"
type: "task"
priority: "P1"
points: "3"
labels: "design, research, player, spark-flight"
parent: "flight-assist-damping-and-toggle"
category: "tasks"
write-id: "1791320205348-0.3umbf3fsp0fk8bm0km8"
created_at: "2026-10-06T20:56:45.348Z"
---

# Specify an explicit local reference and bounded linear flight assist

## Context and outcome

Parent: `flight-assist-damping-and-toggle` (existing TODO, estimate 5).
The parent promises a stop relative to the local frame, but neither it nor the
approved flight design defines reference identity, acquisition/loss/switching,
or an applied assist budget. The current brake uses absolute spark velocity.
Fine changes the thrust range; it does not provide velocity matching.

Produce one reviewable specification for an explicit translating reference and
bounded linear assist under the existing ECS/influence path. This is a
three-point design/research task, initially Incoming. No implementation admission
or completed planning review is claimed.

Grounding:
- [Proposed reference/assist note](../../docs/notes/2026-10-06-local-reference-flight-assist.md).
- [Existing flight design, §§3.1, 3.3–3.5](../../docs/designs/spark-flight-and-camera.md).
- [Measured precision boundary and delayed response](../../docs/notes/2026-10-06-manual-flight-precision-boundary.md).
- [Production-system probe output](../../.ημ/diagnostics/playable-foundation/manual-flight/response-probe.edn).

## Scope

- Specify deliberate selection of an existing entity or explicit inertial
  reference; keep inspected entity, assist reference, and committed world
  distinct. No automatic nearest-body, camera, gravity, or candidate selection.
- Define valid references, explicit switching/reacquisition, loss indication,
  finite-data guards and FA OFF behavior, including the pending channel's normal
  Jacobi latency. Commands preserve physical state and manual camera/attention.
- Analyze the proposed `v_spark - v_reference` damping law, finite manual/assist
  allocation, dt normalization, saturation and loss behavior. Compare the current
  displacement-derived budget with a physical acceleration budget before choosing
  coefficients. Resolve the emitter's 1-second floor versus fractional integration
  explicitly. Document the existing angular cap and the missing linear-cap
  contract without silently adding a cap to celestial bodies.
- Describe one coordinated extraction of the embedded brake, additive influence
  ownership and complete registry reads/writes; identify interfaces to later
  body-frame thrust, angular assist and coherence without implementing them.
- Define target range, signed radial closing speed and total relative speed as
  distinct readouts, with a manual-preserving reference action under the existing
  HUD owner. Produce at most two proposed ≤5-point implementation slices, with
  explicit boundaries and dependencies, in the specification; this task does not
  create or admit those consumer cards.

## Non-goals

No production code, tests, diagnostic runner, native session, force tuning,
teleportation, position spring, automatic interception/capture, target fabrication,
binding-rule change, new world/physics path, full 6DOF rebinding, angular-assist
implementation, coherence economy, camera overhaul, clock actuation, or Gate work.
No claim of Ready status for the parent, consumer slices, or inspector card.

## Acceptance criteria

- [ ] A design amendment citing the grounding evidence states the selected
  reference lifecycle and candidate-law disposition, finite budget/priority,
  dimensional units, zero/fractional/changing-dt treatment, control latency and
  safety boundary. Unresolved viability is recorded as a blocker, not disguised
  as an accepted controller.
- [ ] Worked traces through the production emission/fold order characterize
  constant moving and accelerating/orbiting references, switching/loss, recentering,
  changing dt and saturation. Derived examples are clearly separated from observed
  runtime evidence. No scalar retention claim ignores the delayed recurrence.
- [ ] The future RED matrix names real intent and composed-motion boundaries:
  moving targets after initialization; no command-time physical jump; ordinary
  gravity with FA OFF; exactly one kinematic writer; no duplicate braking; bounded
  output and explicit missing/lost readouts. Angular behavior is preserved under
  its existing owner.
- [ ] The future native acceptance requires normal manual reference selection
  and thrust toward a naturally formed planet, with trajectory/relative-speed,
  overlap, fresh eligibility, binding and commitment evidence. The later sculpt
  route is identified without claiming this planning task performs it.
- [ ] At most two bounded consumer proposals distinguish the linear primitive
  from manual reference/readout integration. Each states dependencies on admitted
  body-frame/FA/HUD work and any measured pre-capture clock boundary; none is
  declared Ready merely because the specification exists.
- [ ] Existing FA `R` versus corrected `F` wording, stale embedded-damping
  assumptions and missing linear-cap claim have explicit proposed dispositions;
  no historical card body or ledger event is silently rewritten.

## Dependencies and review

The existing parent is the scope owner, not a prerequisite requiring its own
implementation to complete before this specification. Specification analysis can
proceed independently; its consumers must coordinate with the parent's force
dependencies and the existing `flight-hud-and-cues` owner.

[PR9's pinned amendment](https://github.com/octave-commons/Truth/blob/9f2d7722d2166c2a0f1af3bd16b3558446f5c01f/docs/designs/spark-flight-controls-increments.md)
contains proposed body-frame children and key/damping reconciliation. These are
cross-PR proposed dependencies, absent from this base checkout; their planning
convergence and implementation are not assumed.
[PR12](https://github.com/octave-commons/Truth/pull/12), observed at
`96b19f086707388e43f272e5ec87a3a7378d4c4c`, proposes the post-capture clock and later
actor specifications. Its clock may reveal a future integration boundary; it is
not a prerequisite for this independent reference-law analysis.

Configured planning review must settle scope and evidence before any advancement.

## Verification

Review the design and card diff against the cited source and evidence. Check
dimensions, actual Jacobi ordering, reference identity and loss cases, budgets,
purity/single-writer declarations, and bounded consumer interfaces. Use canonical
Rheos readback for UUID, parent, estimate/points and status. This initial proposal
requires no source test run and claims no native acceptance or gated transition.

## Risks

Cosmological dt and a planet's acceleration may make finite assist insufficient
for the current focus-residence requirement. Fine's small displacement budget may
match orbital speed too slowly. Hidden camera/attention coupling can masquerade
as manual success. These are questions for the specification and later real
trajectory evidence, not permission to alter clocks or inject capture here.
