# Commit-ready natural approach — preparation only

This is a new diagnostic derivative on parent
`7f1fbcae786a7aa4c6b1cb2eed5d79dd71bb6e58`. Root owns execution, publication,
canonical board state and the existing InProgress3 native-verification owner
`f369c598-279c-498d-a64f-45d2ce16ad34`. The exact authorization is
[root-scope.json](root-scope.json), referring to the inherited
[canonical successor scope](../natural-flight-profile/root-successor-scope.json).
Nothing here authorizes itself to run. No JVM, game, display or native input was
used to prepare this bundle. Bounded Python protocol/deadline checks use synthetic
strings and mocked waits with subprocess, process signals and sockets forbidden;
they are not game fixtures or natural gameplay evidence.

Production `src`, `dev`, `resources` and `deps.edn` stay byte-identical to
`b395c4049718f7ce015ddf25fc0a192d97821373`. The predecessor's
[closed result](../natural-flight-profile/RESULT.md) remains unchanged: a fresh
ordinary world produced 12 observed planets, but the stronger diagnostic fresh
handoff admission never passed in its samples. Natural body1010 retained a
historical candidate and current `ready-to-commit?` true. No aim or thrust ran;
operator stop at T995.365 was late against the intended no-target cutoff. Sparse
observations did not establish exact birth, loss-of-parent or ejection timing.
This derivative corrects diagnostic admission and enforces the previously manual
cutoff; it does not change production readiness or promise an approachable planet.

## Same-world admission and terminal evidence

[snapshot.clj](snapshot.clj) reads one immutable published world `w`. Its new
`TRUTH_COMMIT_TARGET` row is explicitly:

```text
eid matter-state stored-candidate? ready-to-commit? fresh-handoff? x y z vx vy vz
```

Admission requires an actually present ordinary natural `:planet` or `:gas-giant`,
a stored `c/planet-candidate`, direct current `narrowing/ready-to-commit?`, finite
position/velocity, and finite positive relative distance from the real Spark.
The root chooses one observed ID; there is no automatic selection or retarget.
The readiness predicate is called in the snapshot, never reconstructed in Python.
Its current contract is allowed arc plus stored candidate, as documented in
[narrowing.clj](../../../src/domain/narrowing.clj) and
[commitment tests](../../../test/domain/commitment_test.clj).

Fresh production handoff remains separately evaluated once and recorded, along
with current parent, per-body eligibility and the stored record. False fresh
handoff is permissible here: [candidate.clj](../../../src/domain/stellar/classifier/candidate.clj)
explicitly preserves historical candidates. It is not evidence of current
habitability, a bound low-eccentricity orbit, or current M5 eligibility.

`TRUTH_COMMITMENT tick count eid...` covers **all** `:committed` entries in the
same world's commitment component, independent of the 128-body detail limit.
Structured `:global-commitment` records the same tick/count/IDs. The parser checks
count, uniqueness and pairing with the physical tick. Before first admission the
count must be zero. The current global evidence is retained before target
readiness/geometry checks; any commitment becomes a terminal observation, even
if the selected body simultaneously loses readiness or finite geometry.

Snapshot evidence also keeps actual observer focus position/intensity, host
focus-offset, all observer bindings/scars, body commitment, Spark/target position
and velocity, tick, dt and simulated time. The host camera/config are sampled
separately; one published focus observation is not the frozen binding-consumer
input. Heading, physical range, published overlap, accumulated binding and actual
commitment remain distinct outcomes. A readiness flag alone is not commitment.

On a terminal frame, [runner.py](runner.py) records the global observation and
closes the run without further gameplay input. If a W key is held, its existing
`finally` releases that key first. Post-release commitment is preserved before
another target predicate can fail. Cleanup releases only owned outstanding input
and retires owned processes; no further aim or movement occurs. The action result
and `closed.json` label `commitment-observed`, including an unexpected prior/other
world commitment. This is evidence, not automatically selected-target success.
Detection is sampled: this does not prove that no gesture occurred after the
physical commitment instant but before its first observed frame.
Release or cleanup failures retain their errors/uncertainty and do not become a
clean run merely because commitment was observed.

## One shared lease and the no-target cutoff

The original caller monotonic origin begins before preflight and is shared with
the supervisor. Bounds remain **180s startup, 1470s work, 1500s total**. Until a
successful admission, the operative phase deadline is also clipped to original
**T+960**. The idle supervisor checks it independently of operator requests.
Idle sleeps, source subprocess waits, per-file guard checks, command waits and pending snapshot
waits use the remaining phase budget; a blocking stage expiration retains its
error and is classified as no-target cutoff if the deadline was reached.

First `aim-start` validates the fresh same-world frame with no prior commitment,
then rechecks the cutoff before setting either the fixed target or the explicit
`successful_admission` record. A tentative or failed target cannot lift T960.
Admission must complete **strictly before T960**; a request submitted earlier does
not reserve eligibility past the cutoff. The 120s aim plus40s reserve is checked
against the original **T1470** work budget, not the T960 phase limit. Therefore
valid admission between T800 and T960 remains possible. Subsequent aims keep the
same target and original lease; a successful first admission lifts only the
no-target phase bound, never the total budget.

At T960 without successful admission, the supervisor starts cleanup with outcome
`no-target-deadline`. It does not claim that every process is dead at precisely
T960. The existing shared cleanup bound is
`min(original T1500, cleanup-start +30s)`. Callers retain bounded closure waits,
STOP on ambiguous submitted requests and no gesture retries. The external command
client retains its inherited at-most60s result/closure observation wait, clipped
to original T1470. It does not apply a stale pre-admission T960 cutoff to a
successfully admitted request whose result publishes just after T960; this wait
does not submit more work or extend the supervisor lease. OS/JVM/filesystem
calls cannot be forcibly preempted by a Python deadline; actual elapsed time,
overruns, uncertain input release and unreaped children must remain visible.

## Unchanged process, input and evidence boundaries

The runner owns one fresh private Xvfb display, a fresh allowed free port, ordinary
`clojure -J-Xms256m -J-Xmx2g -M:demo serve nebula`, and one persistent fixed local
snapshot worker. It never attaches to retained games, the primary display or
primary7896. JVM override variables are cleared; snapshot/tools.deps JVMs retain
64MiB/256MiB limits. PID/start/boot/cwd/executable/display/port/world/window,
non-fixture provenance, production `arc/tick-genesis` and
`render/phase0-bodies+fields`, live workers and nil-error guards stay intact.

[snapshot_client.clj](snapshot_client.clj) changes only the pinned snapshot hash.
Its sequential fixed read-only connection retains 2048-request,4MiB-per-response
and128MiB-total encoded journal caps, exact UTF-8 byte offsets and explicit
partial-write/error records. Caps apply after decode, not to transport allocation.
The worker's relative backup lease is not the supervisor's absolute lease;
supervisor bounds and owned cleanup remain authoritative. Closing its connection
is not remote cancellation. No server Var, hook, world setter or alternate tick
or projection is installed.

The supervisor still enforces **20 W holds, each requested at most2s;360 look
gestures, each at most100px per axis;3 finite aims, each120s;3 full-display PNGs**.
No video, T/Y sculpt, follow, teleport, candidate injection, physical writer,
source hot reload or desktop unlock. Aiming and movement cannot interleave.
[aim-client.py](aim-client.py) only orients through actual acknowledged mouse
input and explicit supervisor admission. It parses the same named readiness
framing and stops on terminal commitment without issuing another gesture.

Before each W pulse, fresh geometry must show the same target, current readiness,
locked manual controls, nil thrust and heading error at most0.5 degrees. One
keyup is owned by `finally`; sampled advancing expected thrust and then advancing
nil thrust must be confirmed. Requested2s is not a strict physical latency bound:
actual release time is recorded and over10s fails. There is no in-hold distance
abort, target matching or velocity reset. Ordinary menu Thrust changes keep
their existing clamped-value acknowledgements and do not erase momentum.

## Root-operated stages after a separate execution release

Use a new run directory and independently checked free port. These command shapes
are documentation only; replace uppercase placeholders before use.

```sh
python3 .ημ/diagnostics/commit-ready-approach/runner.py start \
  .ημ/diagnostics/commit-ready-approach/runs/NEW-ATTEMPT --port FREE-PORT
python3 .ημ/diagnostics/commit-ready-approach/runner.py inspect \
  .ημ/diagnostics/commit-ready-approach/runs/NEW-ATTEMPT
python3 .ημ/diagnostics/commit-ready-approach/aim-client.py \
  .ημ/diagnostics/commit-ready-approach/runs/NEW-ATTEMPT EXPLICIT-TARGET-ID
python3 .ημ/diagnostics/commit-ready-approach/runner.py hold \
  .ημ/diagnostics/commit-ready-approach/runs/NEW-ATTEMPT w 2
python3 .ημ/diagnostics/commit-ready-approach/runner.py stop \
  .ημ/diagnostics/commit-ready-approach/runs/NEW-ATTEMPT
```

Root verifies ordinary ready state and two reads, then uses individually
acknowledged R/Spark open-close/Fine/Tab inputs to establish manual controls and
cursor history. Observe natural formation about every30–60s; retain actual gaps.
Choose one recorded commit-ready body before T960, with zero global commitments.
After a finite aim and each confirmed pulse release, take two fresh coast reads.
Compare measured range/relative endpoint motion and stop on loss, invalid data,
overshoot, or two successive pulse/coast intervals without decreasing range.
Stop if no available ordinary Thrust setting fits the existing conservative
[distance screen](../natural-flight-profile/PLAN.md#optional-diagnostic-distance-screen-and-honest-limits).
That screen is experimental, not a physical guarantee or accepted gameplay law.
No escalating pursuit or budget extension is automatic. Capture at most three
unaltered private-display frames. Inspect `closed.json`, errors, release flags,
elapsed budgets and reaped children rather than relying on a client exit code.

## Preparation checks and scope of proof

[test_protocol.py](test_protocol.py) checks framing, historical-ready admission
with fresh handoff false, refusal of fresh-only/missing/duplicate/nonfinite
targets, global prior/terminal commitment, held/post-release terminal handling,
fixed target, T960 idle/pending-wait closure and late admission without the T800
error. Pure fake-time/child tests cannot qualify actual native scheduling,
physics, biological eligibility, overlap or cleanup timing. Their subprocess,
process-signal and socket entry points are explicitly blocked.

AST, independent Clojure static lint, source equality, resolved local links and
exact predecessor diffs are captured in the closed preparation inventory.
Independent source review plus root execution release are still required. This
preparation makes no approach, binding, commitment, sculpt, embodiment, Gate,
visual-quality or performance claim.
