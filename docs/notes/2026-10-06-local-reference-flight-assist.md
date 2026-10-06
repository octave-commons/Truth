# Manual approach: bounded local-reference and assist proposal

2026-10-06. **Proposed research/design note; no implementation admission.**
Owner: existing UUID `flight-assist-damping-and-toggle` (TODO, estimate 5).
Proposed specification child: `flight-local-reference-assist-spec` (Incoming, 3).
License: GPL-3.0-or-later.

This planning proposal starts from precision revision
`6944df4ec266ec25059c89181fe3d07522d076b3`. Local source references below use
that revision. Additional board/source observations came from the separate
playable checkout at `e2876ad` and the pinned planning proposals
[PR9 at 9f2d772](https://github.com/octave-commons/Truth/tree/9f2d7722d2166c2a0f1af3bd16b3558446f5c01f)
and [PR12 at 96b19f0](https://github.com/octave-commons/Truth/tree/96b19f086707388e43f272e5ec87a3a7378d4c4c).
Their cards and amendments are **cross-PR proposed dependencies**, absent from
this base checkout; their presence on those branches does not confer readiness.
Only this Incoming child and its canonical provenance are introduced here.

Grounding: the existing [flight design](../designs/spark-flight-and-camera.md),
[precision investigation](2026-10-06-manual-flight-precision-boundary.md), and
its [production-system probe](../../.ημ/diagnostics/playable-foundation/manual-flight/response-probe.edn)
establish the current control law and its measured limits. The law below is a
proposal derived from that evidence; no new controller experiment is claimed.

## Decision

(己, p=0.99) The next authorized executable work is the existing native
verification card `f369c598-279c-498d-a64f-45d2ce16ad34` (IN_PROGRESS, 3): try the
corrected Fine/focus/trail controls on a naturally produced planet and retain the
actual approach trajectory. There is **no existing READY implementation scope
for target-relative approach control**. The FA owner needs a bounded **3-point
specification** before its local-frame acceptance is implementable. This is a
proposal to refine that existing owner's missing contract, not another flight
epic or permission to implement the entire five-point parent immediately.

(己, p=0.98) The spec's output should be one reference/assist law and its worked
production-boundary traces, plus at most two <=5-point consumer proposals: a
linear assist primitive and its explicit manual UI/relative readout. Keep angular
assist, full 6DOF rebinding, economy, chase camera, and post-commit clock actuation
in their existing scopes. Consumer estimates need review; no new UUID is claimed.

## Existing contracts and gaps

(世, p=1.0) Canonical Rheos reads show:

| UUID | State/points | Consequence |
|---|---|---|
| `flight-assist-damping-and-toggle` | TODO / 5 | Promises local-frame stopping but specifies `-k_lin * v`; no reference identity, selection, loss, switch, or budget law. Depends on cards 1 and 2. |
| `spark-flight-force-channels` | TODO / 8 | Oversized parent; its old drift-deletion instructions are stale. |
| `spark-body-frame-torque-commands` | PR9 Incoming / 5 | First proposed force child, depends on rotation. Owns input torque, not assist or a moving reference. |
| `spark-body-frame-translation` | PR9 Incoming / 3 | Depends on torque child's axes/transform; must coordinate removal of embedded damping. Body axes are not a translating planetary reference frame. |
| `spark-rotational-input-intents` | PR9 Incoming / 3 | Depends on torque, facing cue, and existing FA domain scope. |
| `flight-hud-and-cues` | TODO / 3 | Existing owner for quiet pilot instrumentation; full acceptance depends on FA/economy. |
| `rich-entity-inspection-ui-spec` | READY / **unestimated** | Accepted core is Header, Composition, selected-body Sparkline, Raw ECS; not target approach controls. |

(世, p=1.0) `docs/designs/spark-flight-and-camera.md:6-9` supersedes the old
spring/tether movement model. Its lines 47-70 require additive force channels,
one kinematic writer, gravity, and damping; lines 94-98 make FA a gate on damping.
The old FA card's `R` wording conflicts with corrected design `F`; R resets the
camera. Lines 94–98 and 144–148 of the
[pinned PR9 amendment](https://github.com/octave-commons/Truth/blob/9f2d7722d2166c2a0f1af3bd16b3558446f5c01f/docs/designs/spark-flight-controls-increments.md)
already record the required ownership/key reconciliation. Preserve historical bodies and
use canonical refinements when this proposal is admitted.

(世, p=1.0) Current `src/domain/player/flight.clj:79-107` in the precision
checkout emits `dir*D*(1-r)/h^2 - v*(1-r)/h`; it reads no reference body. Its
documented channel delay matters. The Fine amendment at
`docs/designs/spark-flight-and-camera.md:108-150` explicitly excludes local-frame
reference, velocity matching, automatic slowdown and FA toggle. COM recentering
in `src/domain/integrator/kinematics.clj:401-427` subtracts a position offset; it
does not make this absolute-velocity brake target-relative.

(世, p=0.99) The source has a real angular ceiling in
`src/domain/integrator/rotation.clj:12-37` / `src/law/rotation.clj`. No corresponding
always-on linear speed ceiling was found in the current flight/kinematic path.
The FA card's instruction to retain both ceilings must not be presented as an
already implemented linear safeguard. Decide a player-only safety contract
explicitly; do not clamp all celestial bodies or silently change the integrator.

## Proposed reference semantics to settle in the three-point spec

(己, p=0.97) Use a stable, explicit **existing entity ID** as a translating
reference, plus an explicit inertial-reference state. This is a proposal, not an
accepted new selection policy. Reference acquisition must be a deliberate manual
player action; do not infer it from nearest body, strongest gravity, camera target,
stored candidacy, or accumulated binding. The chosen body must exist, differ from
the observer, and have finite position/velocity. A navigation reference is not a
claim that it is a currently habitable or committable planet.

(己, p=0.97) Keep three identities distinct: inspected entity, assist reference,
and committed world. An explicit reference action may read an inspected entity's
ID, but must leave camera mode/pose, focus, binding, and physical state unchanged.
The current `:ui/select-entity` action cannot be reused unchanged for that promise:
`src/infra/menu/widgets.clj:201-209` also enters `:follow-selection`, and
`src/infra/dev/window/loop.clj:352-353` then copies camera attention. The spec must
choose a small inspect/reference affordance that preserves manual mode and routes
reference/assist commands through the existing serial intent queue.

(己, p=0.97) Reference switch applies at an identified tick boundary and preserves
position, velocity, orientation and spin exactly; only subsequent acceleration
may change. Never redirect to an entity's successor or another nearby body
implicitly. On disappearance, missing kinematic data, or invalid reference, mark
reference lost, cease new relative-assist output, retain momentum/gravity, and
show the loss. Do not silently substitute inertial braking. The one already
published acceleration channel may still be consumed on the next fold: specify
and test that ordinary Jacobi latency rather than adding a special barrier.
Reacquisition requires explicit action. FA OFF removes damping only after that
documented pending channel drains; it does not zero velocity or stop gravity.

## Candidate law and budget questions

(己, p=0.97) Start with the translating-frame error `u = v_spark - v_ref`, where
both vectors come from the same ECS snapshot. In inertial reference, `v_ref=0` by
the explicit selected mode. This is a velocity-matching assist, not a position
spring, auto-intercept, orbital controller, or capture command. It cannot by
itself promise station keeping against an accelerating planet.

For `h>0`, a candidate consistent with the current normalization is:

```text
alpha       = 1 - retention
a_raw       = -alpha * u / h                 # m/s^2
A_remaining = max(0, A_control(h, settings) - |a_manual|)
a_assist    = magnitude-limit(a_raw, A_remaining)
a_total     = a_gravity + a_manual + a_assist + other registered influences
```

(己, p=0.96) This is a **candidate to analyze**, not a selected numeric controller.
Define the finite control budget, allocation priority, and zero/invalid-dt behavior
before coding. The present emitter floors its local dt to 1 second, while the
integrator can consume fractional dt; that mismatch must be analyzed explicitly
instead of silently copied into the new law. Compare the established displacement-derived budget
`A_control proportional to D*alpha/h^2` with a physical acceleration budget.
Fine's very small D may make matching a planet impractically slow; increasing the
budget without a stated law would conceal the problem. The existing card's phrase
"meaningful fraction of forward acceleration" does not choose that budget.
Keep gravity outside the player thruster budget. Do not debit coherence through a
second writer; `coherence-gated-thrust` owns that economy and must eventually
account for applied assist as well as manual thrust. Do not claim this initial
primitive models fuel/reaction mass or conserves total momentum while thrusting.

(己, p=0.99) Separate the assist influence from the present embedded linear brake
in one coordinated change, so OFF removes every brake and ON never doubles it.
Register the emitter's complete reads/writes and additive channel; the existing
linear and rotational integrators retain sole ownership of physical state. A
massless unresolved spark already consumes acceleration; avoid an unguarded F/m
conversion. Angular damping remains separately owned by the existing FA scope.

(己, p=0.99) At fixed h, unsaturated constant reference and no other forces, the
candidate has delayed recurrence `u[n+1]=u[n]-alpha*u[n-1]`. With varying h, the
consumed correction is proportional to `h_consume/h_emit`; do not claim fixed
retention or adaptive invariance. Moving/accelerating references also add their
own velocity change. Current planets can use WH/subcycled motion while the spark
uses the ordinary path (`kinematics.clj:401-427`): test the real composed advance,
not only constant-velocity targets. If no finite budget and current clock can
keep the focus residence condition attainable, record that measured boundary and
link the PR12 clock policy; do not hide it with teleportation or auto-commit.

## Required evidence and manual acceptance

(己, p=0.99) The spec must define these RED scenarios before a consumer is Ready:

1. Reference set/switch/loss and FA toggle through the actual intent boundary;
   physical columns and camera/attention remain unchanged by the commands.
2. Constant moving target: after fixture initialization, only production systems
   advance both bodies. Relative error converges within the reviewed finite
   budget; translation/common-velocity shifts preserve the stated frame law.
3. Accelerating/orbiting target through actual kinematics, with recentering,
   variable dt/slip entry/exit, budget saturation and finite outputs. Record the
   achievable residence interval, not a universal capture claim.
4. Missing/despawned/invalid reference and switching between unequal target
   velocities: no stale target reassignment or instantaneous physical jump;
   pending-channel latency is explicit. Manual steering remains effective.
5. FA OFF after channel drain preserves free linear momentum absent external
   forces; preserve the separately owned angular contract without implementing
   angular assist in this slice. With gravity, normal acceleration remains. Test zero,
   fractional and cosmological dt at the intended boundary, and confirm exactly
   one motion writer / no duplicated damping.
6. Actual input/UI tests preserve manual mode, focus and selection semantics;
   readouts distinguish target range, signed radial closing speed, total relative
   speed, and reference/lost/OFF state. No missing value is printed as measured zero.

(己, p=0.99) The future consumer's native acceptance uses one naturally formed world, normal player
reference selection and thrust, no state injection, no follow-camera attention
substitution. Capture source/seed/tick/dt; spark and target position/velocity;
reference/assist state; focus distance/intensity; actual binding; stored candidate
and fresh production eligibility separately. Reach and maintain the existing
<=1-AU focus overlap at intensity >=0.5. Binding normally needs 43 accrual ticks
to exceed 0.85, plus snapshot lag. Then record one actual commitment event,
planetary palette, rendered voxel band, T/Shift+T/Y, Resonance debit, queued/applied
field/edit diff, and visible terrain consequence. Failed pursuit remains evidence.
These are downstream playable-route checks to specify, not completion criteria
requiring this planning-only child to implement or perform the route.

(世, p=1.0) `test/domain/pilot_resolve_seam_test.clj:61-65,78-111` directly places
and parks the spark; its later sculpt fixture injects commitment. Those valid
seam tests do not provide the required moving-target production trajectory.

## Inspector and parallel readiness disposition

(世, p=1.0) `rich-entity-inspection-ui-spec` is canonically READY but has neither
`estimate` nor `points`, no `design:` chain, and is labeled only `specs`. Its
explicit accepted core excludes Orbit/Hierarchy/Events and comparison mode.
Therefore it still fails the project's <=5 readiness prerequisite; READY alone
does not authorize implementation. `rich-entity-inspection-ui-panes-2` is TODO,
also unestimated, and incorrectly assumes the core already shipped. The duplicate
`rich-entity-inspection-ui` is REJECTED and must not be revived as a shortcut.

(世, p=1.0) Current `infra.inspect.format/body-facts` (lines 78-116) shows absolute
body speed, mass, size, temperature/composition/life, not player range or relative
velocity. `infra.inspect.card/inspector-card` (lines 75-90) is a read-only
projection, but the selection route changes camera mode as described above.
Consequently the existing core card cannot honestly be claimed to supply manual
target/range guidance without additional scoped design. Useful relative range /
closing-speed visibility can be a small pure projection plus a manual-preserving
selection intent, coordinated under `flight-hud-and-cues`; it is **not Ready now**
and must not be smuggled into PR9's spacing/quiet-telemetry children, which exclude
new flight quantities. Fixing/estimating the rich core is legitimate independent
planning, but it is not the shortest approach-control dependency.

(己, p=0.99) [PR9](https://github.com/octave-commons/Truth/pull/9)'s first potential code slice after planning convergence and
canonical admission remains `spark-body-frame-torque-commands` (5), followed by
the dependent translation/facing work. It improves controllable heading, not
target-relative approach. [PR12](https://github.com/octave-commons/Truth/pull/12)'s Incoming three-point
`committed-clock-executable-policy` owns the first executable post-capture clock;
`committed-biosphere-native-action-spec` and `life-to-represented-actor-spec` own
later causal interaction/actors. Neither proves local human-time control today.
The relative-assist spec may proceed alongside these plans, with explicit clock
assumptions and integration dependencies, without claiming their work is complete.

## Next action

(己, p=0.99) Review this Incoming three-point FA-contract specification proposal
through the configured planning process. The candidate reference law, budget and
consumer boundaries remain unaccepted until that review settles them; existing
native verification continues independently. No implementation or Ready
transition is claimed here.
