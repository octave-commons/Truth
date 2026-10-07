---
category: "tasks"
labels: "design, research, player, spark-flight"
parent: "flight-assist-damping-and-toggle"
type: "task"
write-id: "1791377988028-0.xjbthxo7vgz0a3j170"
points: "3"
title: "Specify explicit local reference and bounded linear flight assist"
priority: "P1"
status: "incoming"
uuid: "flight-local-reference-assist-spec"
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
  HUD owner. Retain at most two proposed reference-assist consumer boundaries;
  they remain separate from the three clock consumers authorized below.
- **2026-10-07 visible scope refinement:** the root's canonical comment supersedes
  the former no-consumer limit for the clock prerequisite only. Review
  [pre-capture clock design](../../docs/designs/pre-capture-clock.md) and three
  Incoming implementation children: `flight-clock-upward-admission-law` (3),
  `flight-clock-effective-fold` (5), and `flight-clock-view-controls` (3).
  This creates planning artifacts, not implementation admission or a claim that
  the parent reference-assist acceptance is complete.

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
- [ ] At most two reference-assist consumer proposals distinguish the linear
  primitive from manual reference/readout integration, with their existing
  body-frame/FA/HUD prerequisites. Separately review the 3→5→3 clock implementation
  chain against its grounded design, including finite admission, effective-fold
  ordering, guarded Auto and ordinary controls. No child is declared Ready merely
  because this specification or its Markdown card exists.
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

---

(己, p=0.99) Independent docs-only analysis is now recorded in docs/research/physics/local-reference-flight-assist-feasibility.md and linked from the existing proposal/ACTORS/index. Source pinned to ad0e685; relevant flight/kinematics/pacing/genesis/narrowing bytes also match 6b160b2. Two IFAC primary full texts plus NASA report sections ground timing/delay and relative-motion limits; initial NASA fetch failures and later text recovery are disclosed.

Derived results preserve the one-tick channel: e[n+1]=e[n]+h[n]b[n-1]+d[n]. Actual reference displacement enters rho[n+1]=rho[n]+h[n]e[n+1]+c[n], where c is final reference velocity times h minus actual displacement. Worked constant, changing-dt, accelerating and circular cases keep finite saturation, velocity matching and position residence distinct. Under the proposed Fine displacement budget at h=4.1e9 seconds, 43 maximum assist kicks change velocity by at most .003146341 m/s; this is a hypothesis-specific bound, not a measured target or universal impossibility claim. Forty-three qualifying binding reads span 42 inter-read intervals; later commitment readiness remains separate.

Independent source/math review found no blocker; source tests/native experiments were not run. Local links and whitespace checks pass. Remaining blocker: settle a reviewed pre-capture timestep/ratio and integration envelope, actual reference displacement interpretation and finite manual/assist budget. A post-commitment clock cannot alone establish pre-capture reachability. No source, runner, tests, clock/gain/controller choice, new consumer, Ready claim or status transition. Existing Incoming3 scope and historical evidence remain intact.

(己, p=0.99) Appended section 10, Recovered pre-capture intent, to docs/research/physics/local-reference-flight-assist-feasibility.md without rewriting its prior analysis. The living UX View menu already specifies manual time override in Phases 0–5, with removal at Gate discovery. Archived user preference asks for smaller steps and strong dilation; these are recovered intent, not a newly selected rate or clock.

Four evidence tiers remain explicit: living UX requirement; approved real-body/force/single-writer law with satellite-capture acceptance still unverified in the original Done-card implementation commit; proposed narrowing and the Todo parent's undefined local frame; and superseded separate-rate pacing plus deleted wall-time spring code. Canonical reads report spark-as-gravity-bound-body Done8, flight-assist-damping-and-toggle Todo5, and phase-0-player-focus-dual-representation-spec Breakdown despite its body saying canonical. None of those labels proves natural approach/capture.

The question is reconciliation of pre-capture timing, delayed forces, actual reference displacement, finite player budget and useful surrounding evolution. Post-commitment time-lock begins too late to establish approach. No clock/controller/rate choice, source/tests/runner/native action, new card or transition. Existing Incoming3 remains unchanged. Verified 18 appended reference targets against local files/headings and exact Git objects; note/actor historical prefixes and whitespace checks pass. Interactive ACTORS follow-up records the recovery. Root owns review, commit and publication.

(己, p=0.99) Root-authorized docs/research/planning refinement under this existing Incoming 3 specification, prior to any documentation edits. Integrate closed ordinary native attempt 02 and 03 evidence into a dated pre-capture clock-envelope research note and the existing local-reference proposal. Proposed range is a 1 second–1day maximum simulation step after automatic pacing/time slip, preserving auto-derived softening and the existing single-writer/frozen-channel path. The finite up-transition admission decision remains explicitly unaccepted and unresolved; fixed-D speed/acceleration, actual pending force, 43 binding reads versus later commitment, and compact actual displacement versus endpoint-velocity proxy receive worked bounds and a later acceptance matrix. No chord average becomes a maximum-speed or finite-capture guarantee. Source is pinned b395c404; evidence references ae5444b63e30207838f5c31f28138cb98d0027f0 and93dc5b2bc0c734e5d8c311ac004035581db885b4, with immutable map resolution for packed inputs. Retain current status/points/body/history and preserve pre-existing pr7-recovery dirt. No source/tests/JVM/native/control/clock implementation, new card, status transition, review request or Ready claim. Root independently reviews before commit/push.

Root scopes the next finite planning refinement: independently reviewed proposal00a35964a4637ebf7d36fddb7b85a2018504b740415293eb4d1473b3153c1c69 supplies proposed B=D one-fold upward admission and persistent guarded Auto after manual timing. Prepare a research-backed design and three explicitly dependent clock consumer cards sized3/5/3 (canonical shared Euler law; effective-fold cap/guardedAuto; ordinary UI/readback), all Incoming and no source/test/runtime actuation. Amend prior no-consumer planning scope visibly; reference-assist remains distinct and no existing implementation dependency is declared complete. Root and independent review precede publication; configured planning convergence precedes Ready/InProgress. This is scoped planning toward actual gameplay, not production admission or a physical safety/capture claim.

---