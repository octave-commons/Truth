# Input observation window — separately frozen preparation

This diagnostic derivative implements only the [root-recorded scope](root-scope.json)
under existing InProgress3 native-verification owner
`f369c598-279c-498d-a64f-45d2ce16ad34`. Its predecessor preparation is
`59ce1c05a963bb81fc52c7b4b6943a5725d23e64`. The inherited
[commit-ready approach plan](../commit-ready-approach/PLAN.md) supplies the
unchanged process, input, target, journal and cleanup contracts. This preparation
does not authorize execution; root and independent source review plus a separate
release are required for a fresh run.

## Observed boundary and narrow change

The predecessor native attempt closed at T744.1155 after its second Tab gesture:
three nonmatching readbacks spanned about 1.148 seconds after key release.
[Root's closed-run observation](../commit-ready-approach/root-closure-observation.json)
records all four owned PIDs absent and cleanup clear. Runtime stdout includes an
untimestamped `UI cursor : free` line. That proves neither its precise callback
time relative to the readbacks nor a renderer-freeze cause. No gesture is replayed
into the closed run. No aim, successful admission or W pulse occurred there.

The prior `await_input` captured a 20-second deadline but stopped after three
reads even when time remained. The new [runner](runner.py) changes **only that
method**: repeated nonmatching readbacks continue until its captured deadline,
with the inherited 0.2-second clipped pause. The deadline is
`min(work_limit(), observation_start + 20)`. `remaining()` is checked before and
after each inspection; even a matching read returned at/after the boundary is
rejected. The attempt index increases monotonically in existing observation
events. No input is repeated by polling.

The existing per-read maximum remains 10 seconds, further clipped by remaining
phase/work/aim time. An inspection error, timeout, commitment or STOP propagates;
this loop does not retry a failed remote read. Only successful nonmatching
observations repeat. The inherited 2048-read cap and encoded-journal limits are
unchanged. Observing more often can consume more of that finite cap; there is no
silent reset or rollover.

This is an upper observation allowance, not a promise of 20 usable seconds or a
hard OS scheduling bound. The unchanged aim caller's 25-second command timeout
and ordinary client's at-most-60-second result wait include work outside this
method and may expire earlier. Their submitted-request STOP/closure paths remain
intact. No deadline is extended to make an acknowledgement succeed.

## Preserved boundaries

- Shared caller origin and 180-second startup, T960 no-successful-target cutoff,
  1470-second work, 1500-second total and deadline-clipped cleanup remain intact.
  Admission, fixed-target ownership, active-aim exclusion, terminal commitment
  ordering and run reserves are unchanged.
- Root remains the sole native operator. At most three aims/360 bounded looks,
  twenty requested-at-most-two-second W holds and three PNGs; no new gesture,
  key, automatic selection, pursuit, fixture, follow-camera, teleport or world
  setter. Actual release latency and uncertain input stay observable.
- Private Xvfb/free-port and PID/start/boot/cwd/executable/world/window guards,
  source pin `b395c4049718f7ce015ddf25fc0a192d97821373`, process heap/file limits,
  original tick/projection and owned cleanup are unchanged.
- [snapshot.clj](snapshot.clj) and [snapshot_client.clj](snapshot_client.clj) are
  byte-identical copies. The worker still uses one fixed read-only connection,
  serialized IDs and exact UTF-8 raw-message/request journals; decoded response
  caps are not transport-allocation bounds, and disconnect is not cancellation.
- [aim-client.py](aim-client.py) changes only its runner SHA256 pin. `HERE` still
  selects this new directory, and `HERE.parents[2]` still resolves the Truth
  worktree. Old preparations, observations and logs remain immutable.

## Pure qualification and limits

[test_protocol.py](test_protocol.py) preserves all 17 predecessor test methods
byte-for-byte and adds seven tests. They cover actual tap action with one recorded
gesture/release and success after four nonmatches; full observation-budget timeout
without replay; startup/no-target/work/aim clipping; rejection of late matching
results; original inspection-error preservation; and delayed terminal commitment
without another gesture. Source inspection and the tests preserve the unchanged
snapshot cap; this preparation does not inject a real cap exhaustion or network
failure.

Before the runner change, 24 tests recorded 10 expected assertion failures
(including parameterized subcases), zero errors, while the 17 inherited tests
passed. After the method-only change, all 24 passed. The original
[RED log](pure-tests-red.stderr) and [GREEN log](pure-tests-green.stderr) are
preserved. Test time is synthetic. Subprocess, socket, process-signal and native
identity entry points are blocked, and native command methods are recorders in
the action tests. These are diagnostic protocol checks, not a real callback
latency experiment or native cleanup qualification.

AST/source/hash checks, exact predecessor diffs and qualification provenance are
in [STATIC-CHECKS.json](STATIC-CHECKS.json), [COPY-PROOF.json](COPY-PROOF.json) and
[SOURCE-EQUALITY.json](SOURCE-EQUALITY.json). The byte-identical Clojure copies
retain their predecessor zero-warning static results; no new JVM/project code
was loaded. The preparation manifest excludes future run outputs.

## Future root-operated command shapes

These are not executed by this preparation. Use a new direct child run directory
and independently verified free port after root release:

```sh
python3 .ημ/diagnostics/input-observation-window/runner.py start \
  .ημ/diagnostics/input-observation-window/runs/NEW-ATTEMPT --port FREE-PORT
python3 .ημ/diagnostics/input-observation-window/runner.py inspect \
  .ημ/diagnostics/input-observation-window/runs/NEW-ATTEMPT
python3 .ημ/diagnostics/input-observation-window/runner.py stop \
  .ημ/diagnostics/input-observation-window/runs/NEW-ATTEMPT
```

Root may separately release the existing bounded 30-second read-only formation
polling, stopping before gameplay input when a candidate appears. That is not
automatic admission. Keep ordinary setup gestures individually acknowledged;
never repeat an uncertain Tab/click/look merely to obtain the desired readback.
There is no claim here of approach, capture, commitment, embodiment, Gate,
performance improvement or successful native acknowledgement.
