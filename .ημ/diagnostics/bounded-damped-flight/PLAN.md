# Bounded damped flight — preparation only

This diagnostic uses the ordinary game controls on unchanged production
`b395c4049718f7ce015ddf25fc0a192d97821373`. It has not been run against a native
world. Root must review, publish and separately release execution. The owner is
native verification `f369c598-279c-498d-a64f-45d2ce16ad34`, In Progress, three points.
The initial [scope](../bounded-flight-cadence/root-attempt03-closure-and-next-scope.json)
and subsequent [three-stage amendment](../bounded-flight-cadence/root-damped-refinement-scope.json)
are root-owned and preserved. The amendment supersedes only the single-aim
restriction. This is an ordinary-input measurement, not a new gameplay controller,
interceptor, clock policy, or proof that capture is feasible.

## Question and retained evidence

Attempt03 stopped before its first W hold: the second aim's published geometry
was tick5056/error0.09185856°, its aim-end CLI reaped11:55:10.039920, and the cadence
helper did not issue its first inspection until11:55:22.608426. That fresh tick5105
had error0.603170828°, failing the unchanged0.5° guard. Removing this operator gap
and using ordinary stronger damping is the next measurement, not an established
causal remedy. Neither an unchanged camera nor a successful aim freezes the target.

Only files in this new directory are authored. Predecessor
[runner](../bounded-look-200/runner.py), [aim](../bounded-look-200/aim-client.py),
[snapshot worker](../bounded-look-200/snapshot_client.clj), and
[cadence](../bounded-flight-cadence/cadence_client.py) remain unchanged. COPY-PROOF
records exact source hashes and diffs. The new worker differs only in the hash of
its fixed snapshot. No remote function/session, arbitrary eval, world setter,
fixture, follow-camera, teleport, automatic target selection or retarget exists.

## Ordinary setup and fixed controls

The actual [Spark widgets](../../../src/infra/menu/widgets.clj) define
`Damp keep/t` as an additive -0.01/+0.01 stepper, clamped to[0.80,0.999], default0.97.
The fixed snapshot obtains its exact live action, bounds and value from the
production menu/world. Only `damping-down` is added to the input allowlist. Each
click uses the existing balanced mouse-down/release, one gesture, then bounded
observations. It must acknowledge the expected retention with unchanged D,
yaw/pitch/sensitivity, manual/free cursor, Spark drawer and no pending pick.
No rounded label substitutes for the numeric world value.

`root-setup.py` performs: inspect; R; Spark open; Fine then Cruise; one ordinary
Thrust-down (D=1.5e14m/tick); seventeen individually acknowledged damping-down
clicks ending at0.80; Spark close; Tab lock; baseline; +200/-200X and +200/-200Y;
final baseline return; one young PNG. Every snapshot requires nil thrust and no
global commitment. The inherited four-look calibration is retained. It never
waits for maturity or applies thrust. Setup failure requests owned STOP.

`root-formation-poller.py` retains at most32 read-only inspections at nominal30s
cadence and the original T+960 no-target deadline. Global commitment outranks
closure, which outranks stored/current-ready candidate reporting. It never selects
a target. Root reads the returned observations and explicitly supplies one ID.
The original game admission is stored candidate plus current `ready-to-commit?`;
fresh classifier membership is recorded but is not substituted for that contract.

## Combined measurement and deadlines

`cadence_client.py` requires exclusive root command ownership. It gathers two
advancing released observations at least1s apart, with no heading requirement.
It verifies D=1.5e14, retention≈0.80, finite positive dt/range, fixed world/window
and observer, and the inherited-speed screen. A baseline range/rate screen rejects
unsupported D before spending time on aim; it is not permission for W.

Then it invokes the same finite, source-pinned `aim-client.py` once for the explicit
ID. That client retains120s, at most180 steps,±200px/axis, and the supervisor's
run-wide360-look/three-aim limits. Initial admission must occur before T+960.
The outer initial wait allows185s only to encompass the child's existing120s aim
and up to65s failure cleanup; the child remains the120s work authority.

Only after a successfully completed, reaped initial aim does one60s pulse phase
start. Up to three stages each obtain fresh geometry, screen and at most one
requested2s W hold. Stages2/3 first invoke the same aim on the same ID, each outer
wait capped20s and clipped to the remaining original60s phase and total lease.
There is no phase restart or target/D/retention adjustment. Only the camera
reference resets after a successful logged aim stage; D/r/dt/observer, previous
observations, maximum measured tick rate and consumed-tick count persist.

Every pre-hold read must have heading≤0.5°. The runner independently checks fresh
heading immediately before keydown. The helper requires at least25phase seconds
remaining and rechecks that reserve immediately before dispatch, after hash/state
I/O. A source guard, snapshot, queued event or OS operation can still take time;
this is an admission limit, not a hard real-time cancellation guarantee.

After a hold, require an advancing nil-thrust publication, then two additional
advancing released coast observations at least1s apart. Complete both before
recording the post-pulse heading and deciding another orientation stage. Range
change uses the runner's actual `hold-complete.target_before`, not the earlier
inspection. Relative endpoint chords use paired target-minus-Spark positions and
cancel shared recentering. Stop after two consecutive noncontracting intervals,
three pulses, any terminal commitment, loss/invalid observation, changed D/r/dt,
unplanned camera change, failed screen or uncertain action. No gesture retry.

All exits after client admission request STOP, including normal exhaustion. A
failed/late refinement is not retried. The helper terminates/reaps only its own
CLI child; the unchanged child requests supervisor STOP. An already published
native action is not synchronously cancelled by killing a waiting client. Cleanup
uses the separate original total lease, not extra input time. Before Client
admission, a bad argument, pin or controller-lock conflict performs no input and
leaves root/the original supervisor responsible for its existing lease.

The unchanged limits remain: shared180s startup from caller preflight;1470s work,
1500s total; T+960 before target admission;30s cleanup clipped to total;2048 fixed
reads;4MiB/request and128MiB raw journal;360look gestures;three aims;twenty requested
≤2s holds;three PNGs. The worker's1470000ms lease is a relative backup that starts
later than the supervisor. Requested2s differs from observed release latency; the
inherited runner records and rejects latency>10s. OS/JVM stalls cannot be forcibly
preempted. No video or primary display/service interaction is included.

## Conditional accounting, not a physics guarantee

The [flight law](../../../src/domain/player/flight.clj) emits a delayed acceleration.
For constant dt h and fixed direction, ignoring gravity and other effects,
`w[n+1]=w[n]-(1-r)w[n-1]+(1-r)D*u[n-1]`, where w=h*v. A rest-to-rest N-driven-tick
pulse has total displacement ND. Do not add a full braking tail to that complete
ND again. A steady-speed release has conditional tail `rD/(1-r)`, now4D at0.8
instead of32.333D at0.97.

The diagnostic conservatively reserves ONE initial tail plus consumed ticks:
`C=Σ(actual released tick − actual pre-keydown tick)` (never reset),
`N*=ceil(2 * 2s * max observed ticks/s)+2`,
`reservation=rD/(1-r)+(C+N*)D`. Require initial-tail≤10% of current range and
reservation≤25% of current range. This includes conservative extra observed
release ticks; no additional4D is allocated independently per pulse.
Also require `norm(current Spark velocity)*dt≤D` on every sampled observation.

This is not a rigorous stopping bound. Double-buffered pending acceleration is
not directly bounded by the velocity sample; nil thrust does not erase velocity
or the prior force. Variable dt, gravity, old force terms, changing direction,
moving planetary substeps and unsampled motion invalidate a guarantee. Changed dt
is rejected, but that does not prove the other assumptions. There is no in-hold
distance abort. No D escalation, reverse-thrust intervention or adaptive physics
is introduced. Failure is useful evidence and need not complete three pulses.

## Root-only invocation after separate release

From the Truth root, with no competing command owner:

```sh
python3 .ημ/diagnostics/bounded-damped-flight/root-client.py 001-start start
python3 .ημ/diagnostics/bounded-damped-flight/root-setup.py
python3 .ημ/diagnostics/bounded-damped-flight/root-formation-poller.py
```

These use a fresh own `runs/attempt-01`, private Xvfb, free port7898, bounded2GiB
JVM, and fresh `attempt-01-clients` logs. The wrapper creates the log directory
before Popen and records full stdout/stderr, arguments/PID/times and reap result.
After the poller has closed and root has selected an actual ID, substitute that
ID (the placeholder below is deliberately not executable as an integer):

```sh
python3 .ημ/diagnostics/bounded-damped-flight/root-client.py 034-flight flight EXPLICIT_TARGET
```

Do not issue a separate aim or interleave other inputs with this command. A manual
closure-only command remains available using a fresh stem:

```sh
python3 .ημ/diagnostics/bounded-damped-flight/root-client.py 035-stop stop
```

The root wrapper copies a snapshot only for snapshot-producing ordinary operations;
start/frame/aim/flight/stop do not imply a fresh observation. Flight controller
and supervisor must both close cleanly; subprocess exit0 alone is insufficient.

## Qualification and limitations

Pure tests block real subprocess/native/network entry points and use explicit
fake children for adapter timeout/reap and result-binding cases. Current result:
33 protocol tests and29 cadence/operator tests pass. They cover the inherited
30 protocol cases, ordinary damping acknowledgment with camera preservation,
delayed acknowledgments, phase cutoffs, one gesture, baseline-before-aim ordering,
actual retention/tail arithmetic, exact hold baseline, same-target refinement,
persistent C, changed parameters, finite geometry, terminal priority, no retry,
late replies and STOP outcomes. AST-only operator tests verify17steps/four looks
and the unchanged poller classification order.

`red/` preserves expected missing damping/old-D failures; `refinement-red/`
preserves five failures against the initial single-aim version. `camera-screen-red/`
preserves three missing-camera-preservation failures and one missing pre-aim-screen
failure. Intermediate green attempts retain a temporary missing PINS assignment,
a synthetic fixture transform mistake and index expectation correction; none is
claimed as runtime evidence. `green-qualified/` records the final passing commands
and exact hashes. Source AST, clj-kondo, manifest-reader and186production-pin checks
are in STATIC-CHECKS.json. No native/input/game/JVM qualification has occurred.
