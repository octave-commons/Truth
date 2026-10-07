# One bounded ordinary natural-flight profile — preparation only

This new diagnostic directory derives the qualified physical-input driver without
editing its frozen source or evidence. Root owns the single operator role,
execution, canonical board state and publication. The existing native verification
owner remains InProgress3 (`f369c598-279c-498d-a64f-45d2ce16ad34`); the exact
root-authored scope is [root-scope.json](root-scope.json).

Preparation starts at `64f8141b40cfb8db94ea2f005004f068a3f47a72`. Production
`src`, `dev`, `resources` and `deps.edn` must remain byte-identical to
`b395c4049718f7ce015ddf25fc0a192d97821373`. No script here has run against a JVM
or native service. Static review is preparation, not qualification of this profile.

The predecessor's [independent physical audit](../physical-approach/attempt-01-audit.json)
verified 50 fixed snapshots, 16 operations, real Fine/Thrust controls, four small
look steps, and a requested2s W hold with measured2.029316s release latency. It
verified an advancing nil→expected-vector→nil thrust sequence and owned cleanup.
There were no planets or aiming calls in that run; those remain unqualified.

## Profile, source and process ownership

[runner.py](runner.py) owns one fresh private Xvfb, one free allowed port, one
ordinary `-M:demo serve nebula` game, one fixed snapshot worker and all helper
processes. It never attaches to a retained game, primary display or primary port.
The game is bounded to256MiB/2GiB, the snapshot/tools.deps JVMs64MiB/256MiB.
Inherited JVM overrides are removed with only names recorded. Existing PID,
start, boot, cwd, executable, display/port, original world/window, non-fixture
provenance, production tick/projection, worker liveness and error guards remain.

[snapshot.clj](snapshot.clj) is byte-identical to the qualified physical snapshot.
Its `TRUTH_TARGET` membership comes from the actual current production handoff
write-set, including real-star and settled-system gates, not stored candidate
records or merely the per-body orbital predicate. One published world supplies
both positions; camera/config are separately sampled and are not an atomic
binding-consumer input.

[snapshot_client.clj](snapshot_client.clj) differs only in the allowed remaining
lease ceiling:1470000ms. It retains the qualified fixed form, one loopback
connection, 2048-read cap,4MiB per-request and128MiB aggregate encoded response
caps, exact UTF-8 offsets, append-only raw/request journals, sequential IDs and
consumed-before-next-request operational slots. Partial write/flush failure
metadata and original errors remain preserved. The caps apply after decoding;
this is not a wire-packet capture or a decoder-allocation bound.

Only the supervisor and caller have the shared absolute origin. The worker's
relative backup lease is calculated before its JVM startup and begins later in
that worker, so it is **not exactly the same absolute deadline**. Supervisor
request, phase and teardown bounds remain authoritative. Closing a connection
is not remote cancellation. No remote Var, hook, setter or alternative world is
installed.

## Shared startup and cleanup deadlines

The caller records a common monotonic origin **before** tool/source preflight.
It passes that origin to its owned supervisor, which publishes it in state. The
run has **1470s work / 1500s total**, including startup, with a separate shared
**180s absolute startup deadline**.

Source subprocess waits and per-file source checks use remaining startup time.
Xvfb readiness (at most10s), game log readiness (90s), snapshot-worker readiness
(35s), first guarded snapshot (35s), and window search/focus helpers are each
clipped to the same remaining startup budget. STOP is checked between startup
phases and in readiness/snapshot polling. The caller has no independent140s or
145s cutoff. Reaching180s cannot be turned into a fresh relative startup budget.

The supervisor enters `finally` on startup failure, STOP, action failure or any
normal exit. Cleanup is bounded by `min(original total deadline, cleanup start
+30s)`. The caller can request STOP and await closure before ready exists, using
the recorded supervisor identity. Its closure wait is at most45s and clipped to
the original total deadline: this allows up to10s of source-subprocess stop
recognition,30s cleanup and5s observation margin. It never extends world work.

A submitted or ambiguously published ordinary action whose caller times out,
is interrupted or receives a failure result triggers owned STOP and bounded
closure; the original error remains visible and the gesture is never retried.
A lock/pre-publication failure explicitly records that no request was submitted
and leaves the existing owner/lease authoritative. The stop command is available
before ready and during an active aim; already-closed state is read without
another native command. All direct client processes remain owned/reaped.

Blocked OS/JVM/filesystem work is not forcibly preemptible by a Python deadline.
Overruns, uncertain release or unconfirmed closure are failures requiring review;
no successful cleanup is inferred merely from a signal or caller exit.

## Run-wide input limits and aim ownership

The supervisor, rather than a caller-local counter, enforces:

| Resource | Run-wide maximum |
| --- | --- |
| W hold requests |20, each positive and at most2s requested |
| Look gestures |360, at most100 pixels per axis each |
| Finite aim admissions |3, each at most120s |
| Owned-display PNG captures |3 |
| Snapshot requests |2048 |

Counters are reserved before the native gesture; a failed attempt does not
refund capacity. There is no video, capture replay, scene filtering, world
repositioning or follow-camera substitute. Ordinary Spark/Fine/Cruise/Thrust
menu controls retain visible-hit and clamped-value acknowledgements. Mouse
press/release remains balanced; no extra mouseup follows a completed click/Tab.
At least40s of **run work** must remain before new input. Active aim deadlines
independently bound each operation; the40s reserve is not subtracted from the
120s aiming limit.

[aim-client.py](aim-client.py) uses explicit local `aim-start`/`aim-end`
admission. The first admission pins the root-chosen target for the entire run,
must occur by T+960s, and requires current full handoff membership. Later
admissions must use the same target. At least160s of run work must remain before
an aim starts, reserving120s plus40s. Each helper is additionally bounded to180
steps and120s; all its looks count toward the shared360 limit.

While active, only `inspect`, `look` and `aim-end` with the matching invocation
are admitted. Ordinary menu/hold/tap calls cannot interleave. Root sends no
other commands during a helper. STOP always remains available. An abandoned
active aim expires and stops the run; it does not silently unlock movement.
A helper failure stops the owned run, with no implicit retarget or retry.
Constructor/lock failures also attempt bounded cleanup only after verifying the
frozen runner and its owned run; inability to verify cleanup remains explicit.

The helper changes orientation only. Its0.5-degree endpoint is alignment with
one published direction, not capture or sustained overlap. Missing, omitted,
ineligible, nonfinite, degenerate or unreachable-pitch targets fail. Reporting
is bounded to128 natural planets, with omissions recorded; an omitted target
is not silently treated as eligible.

## Root-operated stages and commands

Run commands from this worktree. Use a new attempt name and free port; no command
in this plan authorizes itself to execute.

```sh
python3 .ημ/diagnostics/natural-flight-profile/runner.py start \
  .ημ/diagnostics/natural-flight-profile/runs/attempt-01 --port 7898
```

1. Confirm ordinary provenance/identities and two acknowledged reads. Use real
   R, Spark open/close and Tab to establish manual mode, initialize cursor history
   and lock look, as in the qualified smoke. Do not run a blind command batch.
2. Let the ordinary world evolve. Root performs bounded read-only inspections
   about every30–60s, recording tick/sim-time, planet count and current handoff.
   **If no eligible target is admitted by T+960s, stop and document that result.**
   No forced planet, alternative seed/world or budget extension follows.
3. Root explicitly chooses one currently existing full-handoff target and starts
   a finite aim. Use its ID, not the literal placeholder:

```sh
python3 .ημ/diagnostics/natural-flight-profile/aim-client.py \
  .ημ/diagnostics/natural-flight-profile/runs/attempt-01 EXPLICIT-TARGET-ID
```

4. Outside an active aim, root may use the actual Thrust stepper to choose a
   diagnostic range, then reread geometry and nil thrust. No automatic D selection,
   escalation, target selection, retargeting or pursuit is implemented.
5. Submit one positive W pulse of at most2s. The supervisor checks current full
   handoff membership and target-relative geometry from a fresh published world,
   actual locked manual mode, nil thrust and a fresh heading error≤0.5 degrees
   **before keydown**. A stale aim therefore does not authorize a later pulse.

```sh
python3 .ημ/diagnostics/natural-flight-profile/runner.py hold \
  .ημ/diagnostics/natural-flight-profile/runs/attempt-01 w 2
```

The source-derived thrust vector must appear on an advancing tick, with sampled
camera angles unchanged. One keyup is attempted in `finally`; a later advancing
tick must publish nil thrust. Target-relative pre/post positions, velocities,
dt, tick and simulated time are retained. Requested≤2s is not a hard physical
wall-time guarantee: actual release latency is separately recorded, and the
inherited observed-over10s rejection remains a failure guard, not proof of2s.

6. After each complete pulse and confirmed release, root takes **two fresh coast
   observations** before another pulse. Compare actual range and relative
   displacement, current handoff and heading. Stop on any loss/nonfinite/error,
   observed overshoot, or two successive pulse/coast intervals without decreasing
   range. Do not repeat an uncertain gesture or extend a hold to reach a goal.
   At most20 pulses,3 aiming admissions and360 looks are available, even if none
   reaches the desired radius.
7. Capture at most three unmodified full-display PNGs at useful milestones.
   Close explicitly and inspect final release flags, child reaps and budgets:

```sh
python3 .ημ/diagnostics/natural-flight-profile/runner.py stop \
  .ημ/diagnostics/natural-flight-profile/runs/attempt-01
```

The same runner permits individually acknowledged `inspect`, `tap r`, `tap Tab`,
`click spark`, `click fine`, `click cruise`, `click thrust-down`, `click thrust-up`,
small `look DX DY`, and `frame`. Root supplies one operation at a time, reads its
saved result and current identity, and stops on failure. Stop always remains
available; nobody edits live IPC, counters, source or preparation hashes.

## Optional diagnostic distance screen and honest limits

The [existing flight law](../../../src/domain/player/flight.clj) gives an isolated
constant-dt model a settled coast of `r D/(1-r)`, or approximately32.333D at
r=.97. As a conservative **diagnostic screen**, root may let
`N*=ceil(2*pulseSeconds*max recent observed ticks/sec)+2` and choose the largest
ordinary half-Cruise step satisfying both `32.333D≤0.1*range` and
`(N*+32.333)D≤0.25*range`, while respecting the production Fine floor. These
fractions and timing margin are experiment controls, not accepted gameplay laws
or rigorous bounds on this live world. If no available range passes, stop rather
than inventing a smaller setting or tuning the world.

Changing D does not erase imported momentum. Variable dt, gravity, moving
parents, prior Jacobi influences and compact orbital displacement invalidate a
strict stopping guarantee from those inequalities. The driver intentionally
stops observing a held interval after its first advancing thrust sample and
waits for release: **there is no in-hold distance abort or interception control**.
Root uses complete short pulses and observed coast endpoints.

At each endpoint use `target_position − Spark_position` from the same published
world; differencing those relative endpoints cancels their common recentering.
Use actual simulated time/tick differences and observed relative chords.
Velocity×dt is only a tangent prediction and is not assumed equal to a compact
planet's integrated displacement. Camera heading and published physical
attention/commitment are different observations. A moving local frame may exceed
Fine flight capacity; no guaranteed sustained1AU binding, commitment, embodiment,
intervention or Gate outcome is presumed.

## Frozen preparation and execution evidence

[COPY-PROOF.json](COPY-PROOF.json) contains the exact qualified-parent hashes,
full derivative diffs and production source hashes. [STATIC-CHECKS.json](STATIC-CHECKS.json)
records AST-only Python parsing, clj-kondo, link checks and hash consistency.
No Python module import, project namespace, JVM, native process or diagnostic
execution was used in preparation.

`PREPARATION-FILES.txt`/`SHA256SUMS` close only the new preparation artifacts.
The root-owned scope file and future mutable `runs/` outputs are excluded.
Raw journals, input request/results, helper/client stdout/stderr and every
failure remain inspectable. A future publication may losslessly package closed
raw files with original path/byte hashes; local originals must remain unchanged.
