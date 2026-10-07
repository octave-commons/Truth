# Native thrust qualification — closed attempt 01

The fresh ordinary-nebula short smoke passed all 16 requested operations on
2026-10-07. It ran from committed preparation
`6dc48ccb1f37c5f7193bcbd70291862a71f32768`, with production unchanged from
`b395c4049718f7ce015ddf25fc0a192d97821373`. The root release and three source
reviews preceded execution. The [plan](PLAN.md) and [release](root-release.json)
remain preparation-time records; this document records the subsequent run.

## Observed result

The supervisor ran from 07:32:25.425634 to 07:33:08.529829 UTC, **43.104 seconds**.
The controller completed in **43.598 seconds**, within its 270-second work and
300-second total limits. Its [result](completion-client-runs/attempt-01/result.json)
reports `passed`, all 16 operations, no errors, no unreaped clients and no budget
overrun. The [native closure](runs/attempt-01/closed.json) reports `operator-stop`,
no cleanup errors or unreaped children, and no pending or uncertain key/mouse
release. Root additionally observed all four owned PIDs absent after closure.

The ordinary sequence selected manual mode, opened Spark, selected Fine,
stepped the visible Thrust control up and down, closed Spark, locked the cursor,
acknowledged plus/minus100px look on each axis, held W, and verified the Tab
free/lock roundtrip. Fifty fixed snapshots were requested, acknowledged and
consumed by the supervisor. Early observations waited for published state;
no input gesture was replayed to manufacture acknowledgement.

The visible production control changed displacement from **10,000,000** to
**20,000,000** and back to **10,000,000 metres per tick**, matching its actual
factor and clamp bounds. During W, the sampled camera remained yaw−90°, pitch−20°.
The expected normalized world-space forward vector was
`[-5.753957801139251e-17, 0.9396926207859084, 0.3420201433256687]`.
Published thrust was nil at tick173, matched that vector at tick177, and returned
to nil at tick193 after release. The requested hold was2s; actual recorded
release elapsed time was **2.029316153999389s**. One early post-release read at
tick190 still showed thrust; the later read established clearing without another
keyup. Full states and timestamps remain in the
[operations journal](runs/attempt-01/operations.jsonl).

## Identity and evidence

The run owned display`:1`, port7898, world`305915124`, GLFWwindow
`127983248096304` and Xwindow`2097159`. Recorded PIDs were supervisor2473196,
Xvfb2473216, game2473219 and snapshotworker2474443. These are closed-run identity
records, not instructions to reuse or attach to a process. No retained-primary
process or desktop received input.

The fixed snapshot worker's two append-only journals separate decoded raw
responses from request boundaries and byte offsets. Mutable current-response
slots are operational IPC. The raw journal is a UTF-8 encoding of decoded nREPL
messages, not a bencode wire capture. Source and preparation hashes, command
outputs, input observations and child lifecycle records are retained alongside
controller evidence. The [independent audit](attempt-01-audit.json) confirms619 decoded messages,354583
UTF-8 bytes with contiguous spans, all50 request/ack/consume triples,35 unique
child reaps (34exit0; intentional game143),18 controller client reaps,186 source
hashes,6 run-preparation hashes and10 manifest hashes. The closed inventory completes the
byte-level result record; source-only review is not substituted for that audit.

## Limits and next scope

This young ordinary world qualifies a short input and release path. It does not
establish thrust-only displacement: the recorded positions are recentered and
the world continues evolving. Nil thrust does not erase velocity or cancel a
previous integration influence. It does not establish mature-world latency,
interception, arrival, binding, commitment, terrain, sculpting, earned Gates,
visual quality or native frame rate. No screenshot or video was captured by
this smoke, and no forced timeout, cap violation or partial-write fault was
injected.

The inherited140s start-client wait can expire before the allowed startup phases
complete; 90+35+35 seconds already exceed it, before other guards/helpers. This
successful fast startup does not prove that slower allowed path unreachable.
The supervisor still owns cleanup and its absolute attempt lease. A separately
frozen longer derivative must use one shared absolute startup deadline and a
caller cleanup reserve. This closed bundle will not be rewritten or extended.

The next separately reviewed1470s-work/1500s-total run may observe ordinary
planet formation, orient through finite acknowledged native look operations at
one explicitly selected currently eligible natural body, and measure bounded
physical travel. Arrival is not presumed. The existing native verification card
remains InProgress3; its full fly/resolve/sculpt acceptance remains unfinished.

The sole staged whitespace finding is the preserved extra EOF blank in
`completion-client-runs/attempt-01/18-stop.stdout:61`. All other staged paths
pass the whitespace check; [the exception record](WHITESPACE-CHECK.json) keeps
the raw-output distinction explicit.
