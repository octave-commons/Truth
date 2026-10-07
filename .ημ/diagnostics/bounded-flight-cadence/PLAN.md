# Finite pulse/coast cadence — preparation only

The [canonical preparation scope](../bounded-look-200/root-attempt02-canonical-closure.json)
belongs to existing native measurement owner `f369c598-279c-498d-a64f-45d2ce16ad34`.
Root separately approved the non-replenishing screen and every-exit closure.
This directory changes no production or frozen diagnostic source. Source review,
pure tests and hashes are preparation; a separate root release is required before
any fresh execution. Never use the closed attempt02 again.

## Why this measurement

The [closed attempt02 audit](../bounded-look-200/attempt-02-closure-audit.md)
and [lossless auxiliary map](../bounded-look-200/attempt-02-AUXILIARY-MAP.json)
preserve the client records `023-pulse-one.json` and `026-pulse-two.json`.
They show holds returned in 3.952/3.755 seconds, but actual pulse starts were about
131.393 seconds apart. First release to coast-A/B observation-copy was about
27.535/49.405 seconds; second was 17.858/34.897 seconds. Inspections themselves
took approximately 0.60–0.87 seconds. Those operator delays confound a sustained
flight conclusion. This trial measures shorter, fully serialized intervals; it
does not promise contraction, capture, binding, embodiment or Gate progression.

Actual hold pre-keydown→released endpoint range changes were −0.57565 AU and
+11.23005 AU. Both longer pulse/coast intervals expanded. There is no measured
basis here for reducing or increasing D; retain the root-selected `3.75e13 m/tick`
for the cadence comparison. No automatic thrust setting, aiming, target choice,
retargeting or pursuit is introduced.

## Exact finite operation

Root must finish ordinary setup/calibration, explicitly admit and aim one natural
target using the unchanged profile, then give this helper sole command ownership.
The helper rejects a different target, active aim, non-ready run or missing
original pre-T960 admission. It requires at least 100 seconds of original work
time remaining: a 60-second command phase plus the existing 40-second input
reserve. It does not start a service, grant admission or extend the original clock.

```sh
python3 .ημ/diagnostics/bounded-flight-cadence/cadence_client.py \
  .ημ/diagnostics/bounded-look-200/runs/NEW-ROOT-RELEASED-ATTEMPT EXPLICIT-TARGET-ID
```

1. Take two released baseline inspections with a one-second pause between them.
2. Before each of at most three pulses, inspect freshly and apply the screens.
3. Submit exactly one unchanged `hold w 2`. The existing driver performs another
   actual pre-keydown read, verifies heading/thrust direction and observes release.
4. Take two further advancing, nil-thrust coast observations, with a one-second
   pause after receipt of A before requesting B. Their actual sampled interval
   includes request latency and may exceed one second; it is recorded, not assumed.
5. Compare coast-B range with `hold-complete.target_before.distance_m`, the actual
   pre-keydown baseline. Preserve the preceding inspection separately. Stop after
   two consecutive noncontracting full intervals or after three completed pulses.

The helper checks global commitment before target geometry through the pinned
existing parser. Historical candidate plus current ready-to-commit are required;
fresh handoff is recorded, not required. The same world/window/observer, strictly
increasing observation ticks/simulated time, finite positive dt/range, unchanged
camera/input state, locked manual mode, no pick and nil thrust are mandatory.
Fresh heading must remain ≤0.5°. No look or menu operations are available here.
Sampled crossing of the original target direction also stops. These endpoint
checks cannot detect an unobserved crossing between samples.

## Conditional residual reservation, not a physical guarantee

The [existing flight law](../../../src/domain/player/flight.clj) consumes a lagged
acceleration channel. At constant dt and default retention r=.97, its isolated
settled-release tail is `rD/(1−r) ≈ 32.333D`. This is one initial allowance T,
never another full tail added after each pulse. The current snapshot must also
satisfy `norm(Spark velocity)×dt ≤ D`. All observed dt values must match the
initial positive dt; changes stop this conditional comparison.

Let C be the sum of the actual hold endpoint tick spans, each
`hold-complete.after.tick − hold-complete.before.tick`. This bounds the number
of driven ticks in those already completed intervals, rather than claiming all
ticks actually carried input. Let
`N = ceil(2×2 seconds×maximum observed ticks/second) + 2`.
Before each pulse require `T ≤ .1×fresh range` and
`T + D×(C+N) ≤ .25×fresh range`. C never decreases; observed movement does not
replenish it. This includes earlier pulse reservations without double-counting
a separate full-D tail after every input. The next pulse is refused if the
screen fails; three pulses are a ceiling, not a requirement.

These fractions and the throughput margin are diagnostic controls, not accepted
gameplay law. Gravity, moving targets, unobserved dt changes, prior double-buffered
force/pending input and initial phase of the delayed recurrence prevent this from
being a rigorous live stopping-distance bound. The snapshot does not expose the
prior acceleration channel or damping knob; default .97 and unchanged setup are
explicit assumptions. The speed envelope alone cannot prove the pending force
buffer is benign. Relative endpoints cancel common recenter translation; `v×dt`
is not substituted for the compact target's actual composed orbital displacement.
Two close coasts measure residual motion, not settled damping or zero momentum.

## Deadlines, children and closure

The 60-second deadline begins before the first measurement command. Every command
wait is clipped to it and the original supervisor total; each ordinary command
also has a 25-second caller maximum. At least 25 phase seconds must remain before
another hold. A late matching response is rejected. No command is retried.

Every outcome after client admission—including three-pulse exhaustion, terminal
commitment, invalid observation or uncertain command—invokes unchanged `runner.py
stop` once. STOP/reaping is a separate cleanup interval of at most 65 seconds,
clipped to the original supervisor total, not a 60-second input extension.
On a child timeout the helper signals only its own CLI child, waits at most two
seconds, then kills/reaps that child if necessary; the frozen child signal handler
requests owned STOP. The final explicit STOP remains necessary even if that child
was interrupted. Neither client termination nor nREPL disconnection proves remote
cancellation. Requested two seconds is not a guaranteed native keyhold duration;
the original driver's release evidence and uncertainty guards remain authoritative.

No native PID is signalled by this helper. OS/JVM/filesystem stalls can exceed
requested wall limits and must remain visible as failures. Pre-admission argument,
source, ownership or duplicate-lock failures issue no operation and do not claim
the run was closed. A cadence lock prevents duplicate helpers only; root must
exclude ordinary clients and other operators throughout the phase and cleanup.

The helper records complete child stdout/stderr, PID/reap events, command deadlines,
copied snapshots and the exact byte spans of supervisor operation events used to
identify each hold. It verifies each response against the saved request-ID result.
No current snapshot is treated as fresh merely because a command exited zero.
Original run-wide holds/looks/aims/frames, snapshot/byte caps, source/PID/world
guards, 1470/1500 lease and T960 admission law are unchanged.

## Pure proof and remaining limits

[test_cadence.py](test_cadence.py) uses synthetic framing and fake time/children;
subprocess, native signals and sockets are blocked except explicit fake-Popen
substitution. Pinned pure definitions are AST-extracted from the existing runner
and aim source; no driver module, project namespace or native service is loaded.
The initial [RED source](red-cadence_client.py) lacked the new cadence method:
12 tests produced 16 parameterized `NotImplementedError` outcomes and exit1.
This is new-contract RED, not reproduction of a historical production failure.
The first [GREEN run](green-01-execution.json) passed all12 tests. The
[final GREEN run](green-02-execution.json) passes16 tests, including actual
`Client.invoke` fake-child timeout/reaping/STOP and saved-result mismatch checks,
plus nonadvancing tick and changed dt/observer rejection.
All failures and exact source/test hashes remain retained.

Pure success does not qualify actual event delivery, timing, child cleanup, natural
formation, multi-pulse flight, target capture or any failure path on a real OS.
The frozen [runner](../bounded-look-200/runner.py),
[aim](../bounded-look-200/aim-client.py),
[snapshot](../bounded-look-200/snapshot.clj) and
[worker](../bounded-look-200/snapshot_client.clj) remain byte-identical.

## Fresh attempt03 root operator copies

[OPERATOR-COPY-PROOF.json](OPERATOR-COPY-PROOF.json) records exact small diffs
from the already qualified attempt02 setup, formation poller and one-operation
root client. These are operator preparation, not automatic orchestration of
formation→selection→flight. All use the original frozen runner/aim and **new**
`bounded-look-200/runs/attempt-03`; its original path guard requires that run
parent. New outer-client logs and reports live in this directory under
`attempt-03-clients`, `attempt-03-calibration.json` and
`attempt-03-formation-poller.json`. The output directory is created before Popen;
fresh per-command stdout/stderr files refuse overwrite. Recorded direct-client
PIDs/exit codes are retained. Existing outer-wrapper interruption/I/O limitations
remain unchanged; the underlying runner still owns lease and cleanup.

After separate source review and release, root runs these stages separately:

```sh
python3 .ημ/diagnostics/bounded-flight-cadence/root-client.py 001-start start
python3 .ημ/diagnostics/bounded-flight-cadence/root-setup.py
python3 .ημ/diagnostics/bounded-flight-cadence/root-formation-poller.py
```

Start uses free-port check7898 and the unchanged shared180-second startup law.
Setup preserves Fine→Cruise→three acknowledged halvings (`D=3.75e13`), cursor
initialization, ±200 on both axes and baseline return, with one young PNG.
The finite poller preserves global commitment before closed-state before ready
observation, originalT960 cutoff and no target selection. Root reads its closed
result and separately chooses the target and whether to invoke the unchanged aim:

```sh
python3 .ημ/diagnostics/bounded-flight-cadence/root-client.py 019-aim aim EXPLICIT-TARGET-ID
python3 .ημ/diagnostics/bounded-flight-cadence/cadence_client.py \
  .ημ/diagnostics/bounded-look-200/runs/attempt-03 EXPLICIT-TARGET-ID
```

The cadence helper must not overlap the poller, aim or any other root input.
The root client is one operation only and never chooses or pursues a target.
No preparation command in this note has been executed against a runtime.
