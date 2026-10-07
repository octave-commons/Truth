# Physical approach diagnostics — preparation only

This is diagnostic preparation under existing native verification owner
`f369c598-279c-498d-a64f-45d2ce16ad34` (InProgress3), with the root-authored scope
in [root-scope.json](root-scope.json). No prepared script has been executed
against a JVM or native service. Static review is not runtime qualification.
The author owns only new files here; canonical state, receipts, publication and
actual execution remain root-owned.

Preparation begins at `a29447c994e65e5b78c4e01efa4c11aec8d32206` and preserves
production `b395c4049718f7ce015ddf25fc0a192d97821373` across `src`, `dev`,
`resources` and `deps.edn`. Evidence-only ancestry may advance without changing
that pin. All inherited closed bundles remain unchanged.

## Basis and bounded change

The preceding [guarded connection smoke](../guarded-approach/attempt-01-audit.json)
observed 29 completed fixed snapshots on one local worker/connection and all 12
ordinary input operations, with clean closure. Its observed snapshot timings
were descriptive young-world measurements, not isolated cost or native FPS.
This derivative retains its source/process/world/window guards, ordinary
nebula launch, balanced mouse input, acknowledged small look and cleanup.

The production [menu](../../../src/infra/menu/widgets.clj) defines Fine/Cruise and
Thrust m/t scaling. The production
[camera basis](../../../src/infra/camera/navigation/input.clj) defines z-up
forward, right and vertical input; the
[render input](../../../src/infra/render/input.clj) initializes cursor history on
the first callback. The [window loop](../../../src/infra/dev/window/loop.clj)
turns ordinary held keys into queued thrust, and
[player flight](../../../src/domain/player/flight.clj) supplies acceleration
through the existing integration channel. The diagnostic observes those paths;
it adds no domain writer, alternative simulation, controller law or settings API.

The exact copy deltas and source hashes are in [COPY-PROOF.json](COPY-PROOF.json).
[STATIC-CHECKS.json](STATIC-CHECKS.json) records syntax/lint and link/source checks.

## One connection, two append-only journals, separate IPC

[snapshot_client.clj](snapshot_client.clj) is one local 256MiB JVM using the
existing nREPL1.0.0 dependency. It opens one loopback connection, constructs only
the fixed hashed [snapshot.clj](snapshot.clj) evaluation and creates a fresh
response cursor per serialized request. It installs no remote Var/hook or
retained session and accepts no remote code, path, setter or operation from an
individual request. Startup identity comes from the supervisor's owned Java.
The first guarded read pins world/window; every later read must match.

* `snapshot-messages.edn` appends each decoded nREPL response as `pr-str` plus a
  newline, encoded once as UTF-8. This preserves decoded message fields/values;
  it is **not a bencode wire packet capture**. File-channel offsets count the
  actual bytes written. Raw output is flushed before any success summary/ack.
* `snapshot-requests.edn` appends connection-open/close and request-start,
  request-finish or request-failure entries. IDs, expected identity, inclusive
  start/exclusive end byte offsets, byte/message counts, durations and errors
  locate each exact raw span. A local publication failure can append a failure
  after a completed evaluation; a finish entry alone is not an acknowledged
  successful request. Failure overrides it in the operational result. If raw
  writing/flushing fails, the failure summary inspects the actual channel end
  (or file size), distinguishes the last confirmed offset and pending message
  bytes/hash/flush state, and marks an unavailable end explicitly. Secondary
  journal/result errors are suppressed onto the original exception; they cannot
  manufacture a successful acknowledgement.
* `snapshot-current-request.edn`, `snapshot-current.stdout`, and
  `snapshot-current.result` are explicitly **mutable operational slots**, not
  evidence ledgers. The worker accepts only the next sequential ID and bounded
  timeout. It publishes stdout before the matching `NNNN ok` result; the
  supervisor consumes stdout before it may publish the next request. Thus the
  next result cannot overwrite an unconsumed response. The prior ID cannot
  satisfy a later read. Unexpected IDs or fields fail. No directory of hundreds
  of raw per-snapshot files is created.
* The supervisor's append-only `operations.jsonl` records request/ack/consume,
  input expectations, observations and owned subprocess events. Ordinary
  CLI action request/results and bounded subprocess stdout/stderr remain saved
  separately for the existing supervision contract; they are not raw snapshot
  transcripts. Future aiming is finite, so these artifacts remain bounded.

At most **2048 reads**, **4MiB encoded raw data per request**, and **128MiB
aggregate raw data** are admitted. The snapshot worker's per-file RLIMIT_FSIZE
is explicitly128MiB; other children retain32MiB. A cap violation preserves the
raw prefix and rejected message byte count/hash, then fails. The oversized
message is explicitly not fully archived. These checks run after nREPL decoding
and are not a claim that its decoder cannot allocate an oversized message.
Partial failure journals and process stderr remain; no successful closure is
inferred from an incomplete span.

Every complete read requires matching message IDs, exactly one returned value,
one world/window frame, `done`, and no remote error/unexpected status. The
supervisor bounds the entire request even though nREPL's timeout is per message.
No uncertain read or gesture is retried. Closing the local connection is **not
remote cancellation**. Owned native teardown follows a failed sequence.

## Source, lifecycle and time bounds

[runner.py](runner.py) solely owns a fresh private Xvfb, free allowed port,
ordinary `-M:demo serve nebula` game, local snapshot worker and helper processes.
It never attaches to a retained game or a primary display. PID/start/boot/cwd,
executable, display/port, world/window, production tick/projection, ordinary
non-fixture provenance, worker liveness, heap and error guards remain active.
Game heap is256MiB/2GiB, snapshot/tools.deps heaps64MiB/256MiB; inherited JVM
option overrides are removed with only their names recorded. Startup waits for
actual shader/window readiness as well as the demo log.

The **only executable profile here is270s work/300s total**, including the
supervisor's reserved cleanup. A1470s/1500s derivative is not prepared or started
by this bundle; it requires short qualification and separate review. The owned
supervisor's monotonic origin is recorded for a finite same-host aiming lease.
Wall timestamps are evidence, not scheduling authority.

One action or snapshot is outstanding at a time; root is the sole operator.
Controller processes own only their direct runner clients. The supervisor owns
all native helpers and releases keys/mouse, closes the snapshot worker, reaps
helpers, then Java, then Xvfb. Both controller and supervisor outcomes, release
flags, budget and child reap evidence must pass. OS scheduling/uninterruptible
work can delay deadlines; overruns remain explicit failures, not silent passes.

## Ordinary input and movement evidence

Clicks use one tracked press and one release. A completed click or Tab never
adds an unpaired mouseup. The only additional menu targets are exact visible
production Thrust down/up actions. Their expected result uses the snapshot's
actual factor/lower/upper bounds and the same clamping operation; no arbitrary
world setting is exposed. Fine/Cruise and Spark toggles retain their existing
acknowledgements. Initial menu motion seeds cursor history.

Each look changes at most100 pixels per axis and retains the existing0.02-degree
actual yaw/pitch acknowledgement. The controller reserves40 work seconds before
new input. The next action waits for confirmed result/identity; repeated
read-only observations are not repeated gestures.

`hold` admits one of W/A/S/D/Space/LeftCtrl and a positive requested duration at
most10s. It requires manual mode, the actually locked cursor, no open drawer or
pending pick, and nil published thrust. A source-derived normalized forward,
right or world-Z vector is recorded from the pre-hold yaw/pitch. An advancing
simulation tick must show finite thrust matching that vector within1e-9 per
component and sampled yaw/pitch unchanged within1e-9 degrees. The release is
attempted exactly once in `finally`, including an ambiguous keydown.

Full git/source/preparation hashing finishes **before keydown**. During the
held interval only cheap owned-process checks and the fixed remote identity
read run, and each read is bounded by the absolute planned release time.
No git subprocess/full-file hash scan extends an intentional hold. Requested
seconds are not a hard real-time physical key-duration guarantee: helper/OS
latency is recorded separately as actual release elapsed time. Observed elapsed
over10s fails and admits no further input. A two-second hold is used by the
smoke; an unobserved thrust interval fails rather than silently extending it.

After keyup, a later advancing tick must publish nil thrust. Before/during/after
positions, velocities, dt, simulated time and tick delta are retained. Position
deltas use published recentered coordinates; they are not by themselves a pure
thrust-only displacement. Nil thrust does not cancel a previous Jacobi influence,
remove velocity, stop world evolution or prove a braking distance.

## First qualification command

Only after root review and release, from this worktree and with a new attempt
name/free port:

```sh
python3 .ημ/diagnostics/physical-approach/short-smoke-client.py attempt-01 --port 7898
```

[short-smoke-client.py](short-smoke-client.py) serializes startup → two reads → R
→ Spark open → Fine → Thrust up → Thrust down → Spark close → Tab lock → +100X
→ −100X → +100Y → −100Y → W held2s and release → Tab free → Tab lock → stop.
No planet, maturity delay or full approach is needed. All16 operations and clean
closure must succeed, with exact journal/ID coverage and stable original
identities. There is no native/frame-rate performance claim from duration.

## Separately reviewed finite aiming helper

[aim-client.py](aim-client.py) is preparation only and is **not invoked by the
short smoke**. After a separately admitted ordinary longer run has a naturally
existing eligible planet, the root operator may explicitly choose one ID and
invoke a single finite orientation diagnostic. It calls only frozen runner
`inspect`, `look` and failure-cleanup `stop`; it cannot start, move or follow an
entity. It never automatically chooses another target or adjusts thrust range.

```sh
python3 .ημ/diagnostics/physical-approach/aim-client.py \
  .ημ/diagnostics/physical-approach/runs/NEW-QUALIFIED-ATTEMPT EXPLICIT-TARGET-ID
```

At most180 small look gestures,120s work, and at most65s failure cleanup bounded
by the original supervisor lease are permitted. Each iteration refreshes the
same target's existence and **membership in the current production handoff
write-set**, Spark and target
positions from one published world, and separately sampled camera state. Missing,
ineligible, omitted, nonfinite or unreachable-pitch targets fail. There is no
stored-candidate substitute. The snapshot evaluates the existing handoff system
once, reuses that result for the target boolean and returned evidence, and thus
includes its real-star and settled-system gates. The separate per-body predicate
in detailed body records is not treated as that full gate. The128-body reporting limit explicitly records
omissions. The helper inverts existing camera-forward geometry and requests at
most100 pixels per axis with the measured sensitivity; each gesture must receive
its normal acknowledgement. A0.5-degree angular observation ends this diagnostic.
It is a measurement criterion, not an accepted gameplay gain or capture radius.

The helper neither predicts a moving orbit nor translates the Spark. Success
means aligned with **one published direction**, not sustained consumer overlap,
interception, binding, commitment or Gate play. Source snapshot guards do not
make camera/config and the published world atom one atomic transaction. Further
coarse/intermediate/Fine travel would be explicitly bounded actual menu and key
operations with refreshed measured distances, never an unbounded pursuit loop.
The previously recorded star translation at large dt is materially larger than
Fine travel; no stationary-target or successful rendezvous assumption is made.

## Closed preparation inventory

`PREPARATION-FILES.txt` and `SHA256SUMS` include only preparation files. They
exclude root-scope.json and all future mutable run/controller output directories.
`preparation-hashes.json` pins executable/read files and this plan during a run.
No prior record, production source, board state, receipt or learning ledger is
changed by this author. The initial static check found zero Clojure errors or
warnings; no imported/executed Python module, project/JVM namespace, native
process or input was used in preparation.
