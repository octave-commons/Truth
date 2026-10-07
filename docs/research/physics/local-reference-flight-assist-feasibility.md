# Local-reference flight assist: delayed motion and capture residence

**Domain:** Physics/control | **Phase:** 0, before commitment

**Date:** 2026-10-07 UTC | **Author:** interactive Codex runtime scout

**Status:** draft; derived feasibility analysis, no accepted controller

**Owner:** Incoming three-point `flight-local-reference-assist-spec`

**License:** GPL-3.0-or-later

## 1. Research question and evidence boundary

(己, p=0.99) Under what pre-capture timestep and timestep-ratio envelope could
finite manual thrust plus the proposed delayed velocity-matching assist keep the
spark near an accelerating natural reference for the required binding reads,
when the reference's actual composed displacement differs from final velocity
times dt? This is the unresolved question; choosing a new gain or clock here
would conceal it.

(世, p=1.0) The [existing proposal](../../notes/2026-10-06-local-reference-flight-assist.md)
defines explicit reference identity/loss and two possible consumer boundaries.
This supplement characterizes its candidate law, without promoting it. Source
inspection is pinned to `ad0e685eb227fd41cd6e2f1778fd3fce90225653`.
The flight, kinematics, pacing, genesis tick and narrowing files also have no
diff against composition `6b160b2dc63e923be5badad7d2de846d74c99093`.
Later host-focus changes are not part of this planning base. No new experiment,
runner, tests, native session, clock actuation or implementation accompanies this
note. Numerical values below are evaluations of displayed closed expressions.

## 2. Primary-source grounding

(世, p=0.99) Sanfridson, Törngren and Wikander (2005), §§2–4, model sampling and
actuation instants separately and retain delayed controls in the discrete state.
Their aperiodic zero-order-hold formulation supports inspecting the actual
emission/consumption order instead of importing a constant-period retention
claim. Their stochastic performance analysis is not a stability proof for
Truth's saturated, nonlinear world. [IFAC primary paper](https://skoge.folk.ntnu.no/prost/proceedings/ifac2005/Fullpapers/03310.pdf),
DOI `10.3182/20050703-6-CZ-1902.01075`.

(世, p=0.99) Izák, Görges and Liu (2008), §2.1 equations (1)–(4) and §2.2,
augment the plant state with the previous command and require bounded timing
intervals for their switched uncertainty analysis. This supports asking for an
explicit timing envelope. Their LMI controller and continuous-plant
discretization are not adopted here; Truth's Euler/Kepler composition must be
derived directly. [IFAC primary paper](https://skoge.folk.ntnu.no/prost/proceedings/ifac2008/data/papers/1621.pdf),
DOI `10.3182/20080706-5-KR-1001.1621`.

(世, p=0.99) Jezewski (1980), NASA-TM-81111, §§1 and 3.1, studies rendezvous through linear
Clohessy–Wiltshire equations and impulse optimization. It establishes relevant
relative-motion literature, not authority to add automatic rendezvous to this
game. The circular example below is a direct inertial-coordinate kinematic
identity, not an application of its optimal controller.
[NASA primary report](https://ntrs.nasa.gov/api/citations/19800019595/downloads/19800019595.pdf?attachment=true)
and [record](https://ntrs.nasa.gov/citations/19800019595). The two IFAC full texts
and these NASA sections were verified. Initial NASA PDF requests failed; a later
indexed PDF text retrieval succeeded, while its page-image request timed out.
No scanned equation is transcribed or used as a controller here.

## 3. Governing equations from the actual fold

(世, p=1.0) [Flight emission](../../../src/domain/player/flight.clj),
`thrust-acceleration-system`, uses `q = max(1 second, :sim/dt)` and writes one
acceleration channel. [Genesis `step-physics`](../../../src/domain/genesis/tick.clj)
fans out from one frozen snapshot; the integrator consumes the previously
published channel. `advance-simulation-clock` counts the duration just consumed
and then publishes the next dt. These are simulation seconds, not render time.

(己, p=0.99) Let snapshot n precede a fold of duration h_n > 0. Positions have
units m, velocities m/s, and accelerations m/s². In common inertial axes define
e_n = v_s,n − v_r,n and rho_n = x_s,n − x_r,n. Let b_n denote only proposed
relative assist emitted at n. With alpha = 1 − retention, the proposal is

$$
q_n=\max(1\,\mathrm{s},h_n),\qquad
b_n=\operatorname{sat}_{A_{\mathrm{remaining},n}}
       \left(-\frac{\alpha_n e_n}{q_n}\right),
\qquad
A_{\mathrm{remaining},n}=\max(0,A_{\mathrm{control},n}-\|m_n\|).
$$

(己, p=0.99) Here m_n is manual acceleration; saturation limits vector magnitude.
The residual allocation is a candidate priority rule, not implemented behavior.
Gravity is outside that budget. Define g_n as all spark acceleration consumed
in fold n other than b_(n−1), including manual thrust; j_n is its outer velocity
impulse. On the ordinary spark path, before any absorb/merge operation,

$$
d_n=h_ng_n+j_n-(v_{r,n+1}-v_{r,n}),\qquad
e_{n+1}=e_n+h_nb_{n-1}+d_n.
\tag{1}
$$

(己, p=0.99) In an unsaturated segment with constant alpha this becomes
e_(n+1) = e_n − alpha (h_n/q_(n−1)) e_(n−1) + d_n. At constant h = q and d = 0,
the characteristic polynomial is lambda² − lambda + alpha. Its roots lie
strictly inside the unit circle for 0 < alpha < 1; at the existing default
alpha = .03 they are approximately .969041576 and .030958424. This proves only
that constant-duration, unsaturated homogeneous limit. It proves neither a
variable-duration product bound nor finite-time capture under saturation.

(世, p=1.0) [Ordinary `euler-advance`](../../../src/domain/integrator/kinematics.clj)
sets x_s,n+1 = x_s,n + h_n v_s,n+1 − f_n. The spark lacks the compact body's
matter-state selector. Planets use WH/Kepler composition when its gate passes,
otherwise embedded KDK; their final velocity generally does not determine their
whole-step displacement. Compact outer dv is also applied after its position
composition. Treat the reference's complete displacement as measured data.

(己, p=0.99) For two live, advancing entities receiving the same recenter offset
f_n, define Delta x_r,n = x_r,n+1 + f_n − x_r,n and
c_n = h_n v_r,n+1 − Delta x_r,n. Subtracting the two position updates gives

$$
\rho_{n+1}=\rho_n+h_ne_{n+1}+c_n.
\tag{2}
$$

(己, p=0.99) Thus matching final velocities does not imply matching positions:
c_n can remain nonzero. Shared recentering cancels; optional skipped-entity LOD,
absorption or lifecycle discontinuities fall outside this simple two-body
derivation and need explicit treatment in later composed traces. An ordinary
Euler reference with the same position convention has c_n = 0. None of these
equations writes a position or supplies an alternate movement path.

## 4. Production mapping and limits

(世, p=1.0) [Pacing](../../../src/domain/pacing.clj) ordinarily derives dt from
complexity and bulk collapse, with a 1e7-second minimum and further time-slip
scaling. These bounds do not establish a small local approach step. Its fixed
wall-rate comments are stale: the actual [host `sim-loop`](../../../src/infra/dev/window/loop.clj)
only sleeps when work finishes before its target period, so 60 ticks/s is not
guaranteed.
The existing one-second emission floor is therefore dormant in ordinary current
genesis pacing, but remains a real boundary for a proposed smaller clock. For
0 < h < 1 second, equation (1) contains h_n / 1 second when consuming such an
emission, not a unit ratio. A zero-duration fold can retain a command for a later
positive-duration fold. No zero/fractional-dt policy is selected here.

(己, p=0.99) Reference switching/loss remains as proposed: switch only identity,
preserve physical state, and stop new assist after loss/FA OFF without erasing
the prior channel's ordinary final kick. Equation (1) makes the pending command
explicit. The producer would need coordinated removal of the existing absolute
brake and complete registry declarations; adding both brakes is not this model.
No pseudocode or consumer API is introduced in this docs-only analysis.

## 5. Worked analytic cases, not runtime experiments

### 5.1 Constant reference and finite authority

(己, p=0.99) For a constant-velocity reference, zero other forces, zero pending
assist, e_0 = 0 and c_n = 0, equations (1)–(2) preserve relative velocity and
separation exactly in ideal arithmetic. Adding the same constant velocity to
both bodies leaves those equations unchanged. This is the necessary translating
frame sanity check; it says nothing about acquisition from a large error.

(己, p=0.99) For any finite budget, assist can change relative velocity by at
most sum h_n A_remaining,n−1 over the consumed kicks (before disturbances).
Compare the two unselected budget families:

| Candidate budget, with no manual allocation | Constant-h maximum assist kick | Consequence as h grows |
|---|---|---|
| Existing displacement scale: A_D = alpha D / h² | alpha D / h | Less velocity authority per tick |
| Finite physical acceleration: A_phys | A_phys h | Larger delayed kick and stopping displacement |

(己, p=0.99) Using the existing Fine D = 1e7 m/tick and alpha = .03 with
h = 4.1e9 seconds (the duration already used in the
[precision probe analysis](../../notes/2026-10-06-manual-flight-precision-boundary.md)),
the displacement-budget hypothesis yields A_D = 1.784652e−14 m/s² and a maximum
kick of 7.317073e−5 m/s. Forty-three such kicks change speed by at most
.003146341 m/s. Removing a hypothetical 1000 m/s error needs at least 13,666,667
effective consumed kicks, plus initial channel latency when empty. That speed
moves 27.406807 AU in one uncorrected fold using the repository's
[AU constant](../../../src/law/stellar/orbital/constants.clj). These are bounds
for this budget hypothesis, not a measured target or proof that every manual
approach fails. Full manual allocation can leave no residual assist at all.

### 5.2 Changing dt preserves the delay

(己, p=0.99) Take e_0 = E, an empty prior channel, d_n = 0 and no saturation.
Let h_0 = H, h_1 = 4H, h_2 = H, with H at least one second. At alpha = .03:

| Completed fold | Emission consumed | Result |
|---|---|---|
| 0, duration H | empty | e_1 = E |
| 1, duration 4H | b_0 = −alpha E/H | e_2 = .88 E |
| 2, duration H | b_1 = −alpha E/(4H) | e_3 = .8725 E |

(己, p=0.99) Repeating H instead gives e_2 = .97 E and e_3 = .94 E. This
closed substitution illustrates the ratio dependency; it is not an observed
pacing sequence or proof of instability. The unsaturated assumption must be
checked against each candidate's finite budget before using this example.

### 5.3 Uniformly accelerating reference

(己, p=0.99) For constant duration, a reference with constant acceleration A_r,
and constant spark acceleration A_s from other channels, the forcing in (1) is
−h (A_r − A_s). An unsaturated steady velocity error would be
e_star = −h (A_r − A_s)/alpha, requiring assist A_r − A_s. It cannot exist if
the remaining magnitude budget is smaller than that acceleration difference.
Even when it exists, nonzero e_star produces a separation drift through (2).

(己, p=0.99) A further distinction survives even if both velocities match at
every step: an exact or uniform-acceleration KDK reference moves
Delta x_r = h v_r,n + A_r h²/2, while v_r,n+1 = v_r,n + A_r h. Therefore
c_n = A_r h²/2. With equal other acceleration A_s = A_r, zero initial error
and zero relative assist, the ordinary spark accumulates this displacement
difference despite e_n = 0. Starting colocated, N equal folds give
rho_N = N A_r h²/2. The bound for that limiting case to remain within R_gate is
|A_r| <= 2 R_gate/(N h²). At N = 43 and the duration above it is
4.139227e−10 m/s². This is an analytic integration comparison, not a celestial
acceleration estimate or a selected clock limit.

### 5.4 Circular reference: displacement is not final tangent velocity

(己, p=0.99) In inertial axes, let a radius-R orbit advance through theta = omega h
from (R,0). Exact reference displacement is R(cos theta − 1, sin theta) and
its final velocity times h is R theta (−sin theta, cos theta). Thus

$$
\frac{c}{R}=(1-\cos\theta-\theta\sin\theta,\;
             \theta\cos\theta-\sin\theta),\qquad
\frac{\|c\|}{R}=\sqrt{\theta^2+2(1-\cos\theta)-2\theta\sin\theta}.
\tag{3}
$$

| theta, radians | Single-fold magnitude of c/R |
|---|---|
| .1 | .004998611 |
| 1 | .486264762 |
| 2 pi | 6.283185307 |

(己, p=0.99) At a full turn the reference returns to its start, while a spark
using the same final velocity in one Euler drift moves a circumference. For
small theta, the leading magnitude is R theta²/2. This explains why end-velocity
agreement alone cannot certify local residence even with an accurate Kepler
reference. It does not prove impossibility: a deliberately nonzero relative
velocity could offset a displacement term. Deriving such commands would be a
different controller decision, not an implicit addition to velocity damping.
Across an orbit c rotates, so multiplying its magnitude by 43 is not an actual
accumulated-error calculation; use the vector sum in (2).

## 6. Validation boundary: 43 reads are not a controller proof

(世, p=1.0) [Binding law](../../../src/law/narrowing.clj) and
[binding/commitment systems](../../../src/domain/narrowing.clj) use neutral gain
.02, capture threshold .85, intensity floor .5 and a one-AU focus overlap.
From zero, 43 qualifying evaluations produce .86; commitment sees that binding
in a later Jacobi snapshot and still needs its readiness gate. The first through
43rd qualifying reads span 42 inter-read intervals; advancing 43 equal folds
from the starting snapshot consumes 43h = 1.763e11 simulation seconds in the
worked example. This accounting is not a promise of 43/60 wall seconds, nor a
claim that 43 folds exhaust the whole capture route.

(己, p=0.99) Equation (2) concerns body separation. Capture reads focus position:
it also needs the admitted manual focus/offset contract at the same snapshot.
Camera-following cannot substitute for manual approach. Finite reference data,
stored candidacy, fresh production eligibility, attention and commitment must
remain separate observations. This analysis certifies no natural capture.

(己, p=0.99) Checked here: dimensions, delayed indices, constant-velocity
invariance, constant-duration roots, closed saturation bounds and analytic
acceleration/circle identities. Still unvalidated: switched/saturated stability,
actual composed reference trajectories and achievable manual residence. No
numerical simulation, benchmark, runtime test or native acceptance was run.

(彼, p=0.99) An independent source-only agent review found no blocker in
equations (1)–(3), the displayed arithmetic, the Spark/compact-path mapping and
the 43-read accounting. That reviewer did not independently fetch the papers or
execute tests; this is mathematical/source review, not experimental validation.

## 7. Promotion dependency and smallest future evidence plan

(己, p=0.99) The blocking dependency is a reviewed **pre-capture** timing and
integration contract: the permitted h_n and successive-duration ratios, the
same-time interpretation of spark/reference snapshots, the reference's actual
displacement path, and the finite manual/assist budget. The proposed
post-commitment clock in PR12 begins after capture, so cannot by itself justify
reaching capture. This note selects no alternate clock or integration policy.

(己, p=0.97) Once that contract is admitted, the smallest proposed experiment
would retain the real ECS emission/fold and reference integrator, recording
(h_n, emitted/consumed assist, e_n, Delta x_r,n, c_n, rho_n) at each snapshot.
It would first compare constant, uniform-acceleration and circular limiting
cases, then one natural target's complete approach/residence segment, including
switch/loss, saturation, frame recentering and attention. Report ratio and budget
violations explicitly. No runner or new card is created here, and this future
experiment is not currently authorized by the Incoming specification alone.

## 8. Open decision

(己, p=0.99) Review the feasible pre-capture timing/integration envelope before
selecting a budget or advancing either consumer proposal. The existing note's
two bounded consumer boundaries and reference lifecycle remain proposals; this
supplement neither completes the whole specification nor advances its status.

## 9. References and provenance

(世, p=1.0) Primary publication links and the specific portions verified appear
in §2. Production links resolve within the pinned checkout in §§3–6. The
[multi-timescale Jacobi research](multi-timescale-integration-jacobi-ecs.md) and
[flight design](../../designs/spark-flight-and-camera.md) provide project context;
actual source controls the fold claims. This interactive supplement was prepared
under the workspace `deep-research` skill with the explicitly authorized
docs-only scope; no pseudocode, executable toy or controller implementation was
substituted for missing admission.

## 10. Recovered pre-capture intent

(己, p=0.99) This 2026-10-07 supplement recovers prior intent; it does not
replace §§1–9 or choose a clock, rate, reference controller or integration
policy. The missing contract is a reconciliation of existing requirements,
not an absence of earlier concern for approach and timing.

**Living UX intent.** (世, p=1.0) The [UX architecture's View menu](../../designs/ux-architecture.md#view)
specifies an automatic rate following observable complexity, a manual rate
override throughout Phases 0–5, and permanent removal of that override at Gate
discovery. It is a living product design, introduced in
[`241efa61`](https://github.com/octave-commons/Truth/commit/241efa61035d87c378c277ab927b32094fb45581),
not an executable timing contract. The archived [original user request](../../notes/designs/architecture-exploration-001-the-simulation-is-moving-too-fast-for-wh.md)
asks for smaller steps and formation slow enough to explore; the [recorded
strong-dilation selection](../../notes/designs/architecture-exploration-002-text-rendering-is-the-key-gap-the-hud-on.md)
at lines 244–252 includes a planet-stage preview near five years per real
second. That is historical preference, not a selected rate here. The current
[View settings](../../../src/infra/menu/widgets.clj) at lines 99–107 expose
camera sensitivities; this recovery found no implemented ordinary rate slider.
The rate range, local/global scope and adaptive-pacing/slip precedence remain
unresolved.

**Approved physical law; unverified player acceptance.** (世, p=1.0) The
[Spark body card](../../../kanban/tasks/spark-as-gravity-bound-body.md) requires
gravity-driven motion and eventual satellite capture after releasing input
near a planet. Canonical Rheos readback reports Done, estimate 8. Its
[implementation commit](https://github.com/octave-commons/Truth/commit/b0b3b4aff11203c322c34ac86c342bb3e1fffdf6)
nevertheless states that the player-visible orbit/capture acceptance needs
live tuning. The [approved flight design](../../designs/spark-flight-and-camera.md#3-physics-model-all-as-ecs-accelerationtorque-channels)
requires force/influence composition through one physical integrator and no
second position/velocity writer. Later [approved multiscale integration](../../designs/multi-timescale-integration.md#30-coordinates-relative-jacobian-formulation--required-not-optional)
requires parent-relative compact motion and rejects indiscriminately shrinking
the global step to the shortest orbit. Its implementation,
[`cbe80ddd`](https://github.com/octave-commons/Truth/commit/cbe80ddd9a0678cf41c255e917f9b0dbfb97a6cb),
postdates the Spark-body change. Those obligations remain constraints;
historical Done status does not prove current natural approach or capture.

**Proposed narrowing and unresolved local frame.** (世, p=1.0) The
[first-narrowing proposal](../../designs/the-first-narrowing-star-to-planet.md#23-the-system-contracts-around-you)
concentrates full local simulation as binding deepens, while its capture
boundary starts planetary time-lock later. The [dual-representation umbrella](../../../kanban/tasks/phase-0-player-focus-dual-representation-spec.md)
calls its body canonical, but Rheos reports Breakdown and its triage records
partial implementation with promotion/demotion still open. The [FA parent](../../../kanban/tasks/flight-assist-damping-and-toggle.md)
is Todo, estimate 5: it promises stopping relative to a local frame, while its
formula damps inertial velocity and supplies no celestial-reference lifecycle.
Neither document defines the missing pre-capture same-time motion contract.
The [post-commitment local lock](../../designs/commitment-and-resonance.md#51-the-hard-time-lock)
starts after capture and cannot establish reachability beforehand.

**Superseded code is evidence, not a restoration instruction.** (世, p=1.0)
Historical [`phase0/pacing-for`](https://github.com/octave-commons/Truth/blob/4b9cf814a495ae3cae0061f0e5756cdcc16fe32f/src/domain/phase0.clj#L144-L158)
separated wall-clock rate from integration step; the
[`c89e3e0e` replacement](https://github.com/octave-commons/Truth/commit/c89e3e0ed622305c69c84c68d2b189440c079710)
removed its accumulator and adopted nominal fixed cadence with bulk-driven dt.
The later [wall-time Spark spring](https://github.com/octave-commons/Truth/commit/0b20c557e99abfb7fdceb06c103e9b40728e615a)
was explicitly superseded by the [owner's physical-body pivot](https://github.com/octave-commons/Truth/commit/452503538a0558c17b6d8092176a99d50fdbe627)
and deleted by `b0b3b4a`. Neither earlier path is admitted by this recovery.

(己, p=0.99) The remaining question is how the already-specified pre-Gate timing
control provides a physically resolved, controllable interval before binding,
consistent with delayed forces, the reference's actual displacement, finite
player budget and continued surrounding evolution. This sharpens §7 without
selecting an answer. Canonical reads used the verified merged Rheos CLI
SHA256 `83c6b397278d418d69ce6509b8c3d9fe87e88cca9141b28143d9efb5d78a75a7`;
the existing specification remains Incoming, points 3. No source, tests,
runner, runtime, new card or status transition accompanies this supplement.
