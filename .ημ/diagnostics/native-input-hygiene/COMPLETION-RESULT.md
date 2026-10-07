# Initialized native menu and camera input completion

A fresh ordinary native run completed all ten specified input gestures and their
read-only postconditions on unchanged production `b395c404…`. It used the actual
native window, keyboard and mouse route. **No thrust, planetary approach,
binding, commitment, sculpt, embodiment or Gate was attempted.**

The earlier callback and partial menu run are preserved in
[MENU-RESULT.md](MENU-RESULT.md), published in PR42 at
`d703715bae567ef3681089bec3ab648783750031`. This successor preserves both a failed
first-event assumption and a separate successful initialized run. No failed
artifact was overwritten or recast as passing.

## Attempt02: first cursor event is a baseline

The serialized controller started a fresh ordinary nebula on private display:1,
port7898. R and Tab passed. Before the first +100px X gesture, observed cursor
was nil. The gesture produced cursor `[740,360]`, but yaw remained−90degrees in
three completed readbacks. All inspection commands finished; this was not a
timeout. The driver failed its expected−89degree postcondition and did not retry.

The source callback at `src/infra/render/input.clj:214–217` explicitly records
the first cursor event without applying a camera delta. That source behavior and
the saved observations support the initialization explanation. It is distinct
from the duplicate-release hypothesis in the earlier long run.

Attempt02 closed failed after59.847seconds, no cleanup errors, pending/uncertain
mouse release, unreaped children or budget overrun. Three accepted requests had
two successful outcomes. Nineteen unique native-supervisor children and all five
controller clients were reaped. The controller's additional bare AssertionError
rejects a failed closure outcome; it is not another native or cleanup failure.
[initialization-preflight.json](initialization-preflight.json) preserves the
exact source anchor, audit and three-hunk successor delta.

## Attempt03: ordinary initialization, unchanged angle assertions

The new controller adds two ordinary Spark clicks before locking the cursor,
which place the pointer through the real callback and confirm open/close states.
It also applies the existing40-second remaining-work reserve to clicks.
It changes no production source, frozen driver, snapshot or look assertion.
Root and board_scout reviewed exact controller SHA
`13f5feaad7bc117ca4a8541063f25212ecd71329ab8b1283859e8064c3adfd7b`
before launch at preparation commit`2863728ad6ec1c0a8d218aa5abf38ee413a61005`.

The fixed sequence was R, Spark open, Spark close, Tab, +100X,−100X,+100Y,−100Y,
Tab,Tab. Every client finished and its exit, JSON, saved successful request result
and unchanged ready identities were checked before the next gesture. No input
retry, camera setter, follow hook, world mutation or fixture was used.

| Gesture | Verified camera/state result |
| --- | --- |
| R and two Spark clicks | Manual; drawer opens then closes through balanced press/release |
| First Tab | Cursor locked; drawer remains closed |
| +100px X | Yaw−90→−89degrees; pitch−20 |
| −100px X | Yaw−89→−90degrees; pitch−20 |
| +100px Y | Pitch−20→−21degrees; yaw−90 |
| −100px Y | Pitch−21→−20degrees; yaw−90 |
| Final Tab pair | Cursor free then locked, no drawer or pending pick |

Ten request/result pairs succeeded. All twelve recorded input observations
matched, including menu positioning and toggle readbacks. The observed gain was
0.01degree/pixel, with the unchanged0.02degree assertion tolerance.
The source/host observations are separate samples, not a claim of coherent
render frames or complete callback timing.

Supervisor2127518/start2213484 owned Xvfb2127526/start2213502 and
Java2127530/start2213513. World atom932536646 and GLFW window140589916830816
remained pinned. The run closed **operator-stop** at06:33:02.643388UTC after
147.713seconds; controller completed at06:33:03.092953UTC after148.382seconds
with outcome **passed** and empty errors. All46 unique supervisor children and
12 controller clients were reaped. Cleanup errors, unreaped children, uncertain
mouse releases and budget flags are empty/false. All three owned long-lived PIDs
were verified absent. This teardown does not prove Escape lifecycle behavior.

## What this qualifies

The first run separately verified Fine/Cruise selection; this run verified the
initialized menu and four small look gestures plus cursor-mode roundtrip.
Together they support using these bounded, readback-checked diagnostic inputs in
a later ordinary approach. They do not establish all device/event schedules,
explain every missed large motion, or prove historical duplicate X11 delivery.
The only PNG remains the earlier attempt01 Fine frame; attempts02/03 did not
capture media. No unrecorded visual success is claimed.

Each run retained all186 production and four preparation hashes, independently
verified after closure. Production source, existing tests and retained primary
world were unchanged. No fresh full-suite, strict-analysis, benchmark or hardware
FPS qualification is claimed. The existing verification card staysInProgress3.

A later diagnostic may reuse one bounded local nREPL client to avoid repeated
snapshot-JVM startup, while preserving the fixed read-only form, all identity
checks, absolute deadlines and raw responses. That adapter and any renewed
natural approach require separate preparation and review; this result launches
neither automatically.

`COMPLETION-CLOSED-FILES.txt` and `COMPLETION-SHA256SUMS` close this successor.
The earlier MENU manifest remains valid at its own commit; receipt/reflection
files are append-only and may grow. All canonical board updates for the next
scope are made through Rheos in the guarded-approach child, preventing manual
reconciliation of divergent card histories here.
