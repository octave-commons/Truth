# Pre-capture clock with finite upward-step admission

Status: **proposed for planning review, not accepted or implemented**. Owner:
`flight-local-reference-assist-spec` (Incoming, 3 points). This finite clock
breakdown is authorized by the owner's 2026-10-07 scope comment. It does not
complete that owner's separate reference-selection/assist specification.

## 1. Authority and outcome

The [living UX](ux-architecture.md#view) calls for ordinary manual time control
before Gate discovery. The [closed-flight clock research](../research/physics/2026-10-07-pre-capture-clock-envelope.md)
derives the pending-force hazard and distinguishes sparse endpoint motion from
capture residence. The [upward-step proposal](../research/physics/2026-10-07-clock-up-transition-admission.md)
supplies the finite allowance and source audit adopted below. That last file is
a byte-identical archival copy of the independently reviewed root proposal;
its statements about the earlier publication boundary remain historical.

Let the player select a **maximum global simulation step** from 1 second to
1 day or return to Auto. Apply the cap after the existing automatic pacing and
time-slip policy; preserve cloud-derived softening. On an increase after manual
control has been used, admit only a finite, identified ordinary Spark step whose
pending control kick and own drift fit the selected displacement `D`.

This is a proposed gameplay eligibility rule, not a physical safety certificate.
It does not select a reference, brake relative to a planet, guarantee capture,
change celestial integration, implement local time, or establish a frame rate.
Lowering the global step deliberately slows surrounding evolution. Fixed `D`
continues to mean metres per tick, so smaller steps change physical speed and
acceleration; selecting a clock never silently changes `D`, damping or momentum.

## 2. One ordinary advancement law and one decision

The current shared `euler-advance` computes `v+ = (v + h*A) + J`, then
`delta = h*v+`, then `x+ = x + delta`. Preserve this binary64 grouping when
extracting it into portable pure arithmetic used by the existing integrator and
the new admission law. Return the directly computed `delta`; subtracting large
absolute positions to reconstruct it loses small displacement. Existing map,
SoA, and compact-parent callers retain their prior velocity/position result and
branch selection. No second kinematic writer or predictor is introduced.

`A` is the sum in the actual integrator's registered acceleration order; `J` is
the raw velocity-impulse sum in its registered order. Obtain these through the
existing registry/reductions, never a new copied component list. The separate
SoA drift-prediction cache omits channels and is not this decision's oracle.
Validate every present consumed channel as well as the sums: cancellation must
not conceal malformed inputs. Absent channels mean the existing zero value.

For a proposed increase to `h` seconds, choose **B = D metres**, with finite
`D > 0` and `h >= 1`. Compute `n = hypot(hypot(a_c.x,a_c.y),a_c.z)`,
`kick = h*n`, `kick-limit = D/h`, and the shared Euler result above. Here `a_c`
is the **already published** thrust/brake channel, including a released key's
pending brake. Use the same stable norm ordering for `delta`. The exact proposed
binary64 comparisons are inclusive:

```
kick <= kick-limit        ; m/s, algebraically h²*||a_c|| <= D
norm(delta) <= D          ; m, before common frame recentering
```

There is no tolerance widening `D`, alternate rearrangement on overflow, or
search for another passing `h`. Reject nonfinite inputs or intermediates,
including each Euler multiply/add, the absolute position result and its actual
recentered output. Validate the recenter vector but do not charge the common
frame subtraction against `D`. Admission covers an identified live observer
Spark, due this fold, taking the ordinary Euler branch and receiving no
absorption packets. Reject missing/stale identities, unsupported compact or
non-due branches, malformed data and absorption with distinct reasons. Do not
infer supported routing from a displayed body label.

The result names the world/fold and observer, old/requested `h`, `D`, `a_c`,
`A`, `J`, `v+`, `delta`, finite thresholds and accept/reject reason. It is consumed
only by that fold; a changed input requires recomputation, not cached approval.
Current keys or retention are diagnostic context, never replacements for `a_c`.

Rejecting an increase retains the previous consumed step and ordinary physics.
It neither certifies that retained step nor cancels future input, external
forces or same-step motion. A smaller step can also increase net drift when
force/velocity cancellation changes. For example, `v=10D/H`, `A=-10D/H²`,
`J=0` gives zero drift at `H` but `2.5D` at `H/2`.

## 3. Effective fold and persistent guarded Auto

Use one named, validated world clock state, owned by the serial tick path.
Its data separates requested mode/cap, the automatic proposal after slip,
last actually consumed step/fold, a sticky manual-use flag, and latest decision.
Queued menu intents change only requested clock data. They never write position,
velocity, influence channels, binding, camera or focus. Multiple queued requests
remain FIFO; the last valid request before the fold determines the desired mode.
An invalid request leaves the prior request intact and exposes a reason.

Track consumed-step provenance from actual completed folds. Initial or imported
state without that provenance uses the finite positive initialized/current legacy
`:sim/dt` only as an **unverified comparison baseline**, never as a previously
consumed step. A valid Manual decrease or equal request applies on that first
fold; an increase remains unsupported and retains the legacy baseline until a
completed fold supplies actual consumption provenance. This explicitly resolves
the earlier ambiguous all-transitions baseline-retention wording. The integration
story must characterize initialization and exact host→domain validation. Adding
clock provenance does not change never-manual Auto's physical values or ordering.

After serial intents, tick advance and dt-independent spatial preparation, resolve
the requested step **before constructing systems that capture dt and before
building dt-dependent SoA caches**. For Manual, request
`min(h_auto_after_slip, cap_seconds)`; for Auto request the automatic proposal.
The automatic proposal is the previous publication's proposed next step, not
the previous consumed step. Adaptive-pacing-disabled worlds retain their existing
configured proposal rather than recomputing cloud pacing. Initial proposals come
from the existing initialized `:sim/dt`.

| State | Proposed behavior |
| --- | --- |
| Never-manual Auto | Preserve existing Auto/slip step, softening, physical outputs and event ordering; no `D` eligibility test. |
| Manual | Cap is finite, within `[1,86400]` seconds. Decrease or equal step is applied directly; an increase over the last consumed step requires §2. |
| Return to Auto after any Manual request | Remove the cap but retain the sticky flag. The first and **every later** automatic/slip increase requires §2. A rejection remains visibly guarded Auto at the previous consumed step. |
| Gate disables manual controls | Withdraw the cap and retain guarded Auto after manual use. Do not erase transition history. A future separately admitted clock owner needs an explicit handoff; current data-only time-lock supplies none. |

Guard rejection applies the old step, with no hidden clamp, retry or channel
rewrite. Equal/downward requests are not motion-certified by this law. Unsupported
ordinary Spark context blocks an increase in guarded mode, not the world tick.
Ordinary adaptive pacing currently floors its step at `1e7` seconds and time slip
does not reduce it. Imported or adaptive-pacing-disabled worlds can instead carry
a finite positive fractional step. Never-manual Auto preserves that legacy
behavior. After manual use, an upward request `0 < h < 1` is unsupported under
§2 and retains the actual prior step; equal/downward fractional requests remain
the stated uncertified legacy path. The flight emitter still uses `q=max(1,h)`;
neither the manual cap band nor this admission rule changes that physics.
When consumed provenance is absent, compare the requested step with that
explicit legacy baseline: apply a valid equal/decrease immediately, including a
Manual cap below initialized `:sim/dt`; retain the baseline only for an unsupported
increase. Neither path certifies motion or turns the baseline into consumed
history. Preserve the pending request and sticky manual-use flag. Only successful
whole-fold completion publishes the actual consumed step/fold and applied
outcome. A failed whole fold publishes neither consumed provenance nor an
applied decision; it must not discard the already valid queued request. A missing
or invalid legacy baseline cannot supply this comparison or fabricate history;
reject it through the proposed named clock/host→domain validation and existing
failed-fold handling rather than invent a step or claim an applied outcome.

Use **one effective `h`** for every fan-out consumer, SoA prediction, integration
and `sim-time += h`. Post-fold pacing writes a distinct next automatic proposal
and its original softening; it must not overwrite the consumed-step record or
silently disable guarded mode. Legacy `:sim/dt` remains the published next-step
proposal surface; new readouts label proposal versus consumed step explicitly.
This is one global clock, with no separate player tick or second physics pass.

## 4. Ordinary View controls and visible outcome

Extend the existing View menu and intent route. Choose seven labelled cap values:
**1 second, 10 seconds, 1 minute, 10 minutes, 1 hour, 6 hours, 1 day**
(`1,10,60,600,3600,21600,86400` seconds). Previous/next controls stop at the ends;
Auto is a separate action. The validated domain request accepts any finite cap
within the range; the initial UI offers these deliberately bounded choices.
Selecting Manual from Auto defaults to the displayed 1-day cap and submits it
once, rather than secretly choosing Fine or changing camera controls.

Show requested mode/cap, latest consumed seconds/tick, and one concise accepted
or blocked outcome with its fold. Guarded Auto is labelled **Auto · guarded**;
a blocked increase additionally shows requested versus retained step and reason.
Never display a rejected cap as consumed. Detailed numerical evidence is
available to the existing inspection/diagnostic path, not a new telemetry engine.
No promise of wall-clock rate, near-planet accuracy or successful capture belongs
in the labels. Existing camera rows and hit regions remain usable at 1280×720
and 960×540; controls must not overlap the Actions palette or escape panel bounds.
Use existing menu layout and ordinary pointer/key input, without a UI library.

## 5. Worked decision and ordering traces

The following are constructed exact arithmetic cases, not native measurements;
future binary64 tests also establish the chosen finite evaluation order.

| Input/operation | Required result |
| --- | --- |
| Ordinary due Spark; `D=100`, old `h=1`, request `h=2`, `v=0`, `a_c=A=1`, `J=0`, zero frame shift | Kick `2 <= 50`; drift `4 <= 100`; accept 2. The pending acceleration remains 1 until ordinary fan-out publishes its successor. |
| Same request, `a_c=30`, another channel `-30`, hence `A=0` | Drift is zero, but kick `60 > 50`; reject increase and consume 1. Full-force cancellation cannot hide the control kick. |
| `D=100`, request `h=2`, `a_c=A=0`, `v=49`, `J=2` | Drift `102 > 100`; reject. Raw impulses participate even when pending control is zero. |
| `D=100`, request `h=2`, `a_c=A=0`, `v=50`, `J=0` | Drift exactly 100 passes; a large common frame shift is validated but not charged as travel. Nonfinite absolute/recentered position still rejects. |
| First fold, no consumed provenance, valid initialized/current legacy `:sim/dt=300`, Manual cap 60 | Consume 60 immediately as an uncertified decrease against the unverified 300 baseline. Retain Manual/sticky state; only successful completion records consumed 60 and the actual fold. A cap/request equal to 300 leaves that step unchanged. |
| Same first-fold decrease, but the whole fold fails | Publish neither consumed provenance nor an applied outcome; retain the valid Manual request and sticky flag. A retry again has no consumed history. No 300-second baseline fold or fictitious 60-second completion is recorded. |
| Manual cap 60, automatic proposal after slip 300, prior consumed 30; admission fails | Consume 30, show requested 60 and reason. Preserve `D`, all pending channels and physical input; time advances by 30, not 60/300. |
| Return to Auto: proposed 60 passes from prior 30; later slip proposes 300 and fails | First consume 60; later retain 60. Sticky guarded Auto prevents a later increase bypassing admission. |

Never-manual Auto, initialization without provenance, rapid mode changes,
unsupported context, finite overflow, exact boundaries, pending released brake,
failed whole fold and Gate removal each need explicit tests. Real map and SoA
tests for imported/adaptive-disabled fractional steps must preserve never-manual
Auto, reject a guarded `0.25→0.5` increase while retaining `0.25`, and retain the
existing equal/downward fractional path with `q=max(1,h)`. Real map and SoA
ordinary integration must agree with the shared arithmetic on actual prepared
inputs; test all registered contributors and preserve existing compact-parent
behavior. Do not manufacture a failing historical correctness test for a pure
extraction: baseline characterization passes first; new admission behavior gets
meaningful RED tests before production.

## 6. Implementation ownership, dependencies and qualification

| Incoming UUID | Points | Outcome and dependency |
| --- | --- | --- |
| `flight-clock-upward-admission-law` | 3 | Shared canonical Euler arithmetic and finite §2 decision; design convergence is required, no implementation predecessor. |
| `flight-clock-effective-fold` | 5 | Serial requested/effective clock and sticky guarded Auto; depends on the law. |
| `flight-clock-view-controls` | 3 | Ordinary View controls and truthful outcomes; depends on effective-fold integration. |

All three are children of the existing specification, not claims that the entire
reference-assist parent is complete. Their dependency chain is clock-only;
reference acquisition/relative forces, post-capture local time, embodiment and
Gate production remain separate owners. They become executable work only after
configured planning convergence, canonical Rheos admission, and committed RED.

Each implementation preserves one-writer/component declarations, runs its
meaningful focused tests, full suite and all strict gates. The first two touch
hot paths and require matched existing-adapter before/after measurements under
coordinated resource ownership; no expected speedup is asserted. Native proof
uses a disposable ordinary world and actual View actions after qualification:
observe one accepted and one rejected upward request, consumed/published h,
unchanged command-time physical channels and visible outcome. An inability to
naturally produce both outcomes is a recorded verification gap, never permission
to inject state or claim capture. Existing native measurement ownership applies.

## 7. Source anchors and remaining limits

Source is pinned to `b395c4049718f7ce015ddf25fc0a192d97821373`; the linked research
contains exact GitHub line anchors. Relevant seams are
`src/domain/integrator/kinematics.clj:191–198,401–431,515–552,590–603`,
`src/domain/integrator/base.clj:11–44`,
`src/domain/genesis/tick.clj:78–100,178–216`,
`src/domain/player/flight.clj:79–107`, and
`src/infra/menu/panels.clj:173–190`. This design chooses the proposed rule and
finite breakdown, replacing the earlier open upward-rule question for review.
It supplies no empirical calibration for `B=D`, no guarantee of useful Auto
return, no all-force or continuous displacement limiter, and no target-relative
residence bound. All source/test/runtime implementation and natural acceptance
remain future work.
