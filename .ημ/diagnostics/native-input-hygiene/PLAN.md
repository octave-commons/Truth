# Short native input hygiene diagnostic — preparation, not execution

Owner: existing native-verification task
`f369c598-279c-498d-a64f-45d2ce16ad34`, scoped by root. This bundle changes no
production source, tests, board state, receipts or Git state. Production remains
`b395c4049718f7ce015ddf25fc0a192d97821373`; the evidence branch may advance without
changing those bytes. Root alone authorizes and performs execution/publication.

## What the closed attempt established

The preceding 25-minute attempt is separate and immutable. Its commands 043/044
requested a balanced `xdotool click 1`, then another unconditional `mouseup 1`.
Snapshots 042 and 045 both showed manual mode, free cursor and no open drawer.
The attempted Cruise action stopped before a click because the expected control
was absent. Zero effective activations and two consumed activations both fit
that observation. There is no callback trace proving which occurred.

Production `infra.render.input/mouse-button-callback` emits a pick on any
delivered left RELEASE when `dragged?` is false, without a preceding-press latch.
The render loop consumes one pending pick; Spark's production menu action toggles
nil to Spark and Spark to nil. This permits the proposed duplicate-release
mechanism, but does not prove X11/GLFW delivered both native events.

Large relative-motion requests also failed to match camera readback. The 0.01
degrees/pixel mapping predicted yaw −116.39° / pitch 33.48°; readback was
−112.79° / 1.60°. The ±89° application pitch clamp does not explain this.
X11 clipping, cursor recentering and event delivery remain candidate causes.
This probe uses small isolated steps and observes their actual result.

Source anchors: `src/infra/render/input.clj:126,207,233`,
`src/infra/dev/window/loop.clj:190,365`, `src/infra/menu/widgets.clj:197`.
See the prior bundle at
[`../natural-approach-25m/runs/attempt-01/`](../natural-approach-25m/runs/attempt-01/).

## First: actual callback characterization without a window

[`callback-probe.clj`](callback-probe.clj) constructs the actual private GLFW
mouse callback, invokes it directly, and frees it in `finally` for each case.
It uses the production top-bar layout, hit resolver and menu action. The
explicit consumption step stands in for the render consumer; it is not a render
frame. No GLFW initialization, window, GL context, ECS world, game service,
Xvfb or nREPL is created. LWJGL's existing callback allocation/free machinery
is still used; this is a JVM diagnostic, not a Babashka or native event test.

Predictions on the pinned current source, **not executed results**:

| Supplied callback sequence | Final drawer | Explicit consumes |
|---|---|---:|
| Release without preceding press; consume | Spark | 1 |
| Press, release; consume | Spark | 1 |
| Press, release, extra release; consume | Spark | 1 |
| Press, release; consume; extra release; consume | None | 2 |
| Press, mark dragged, release; consume | None | 0 |

The probe asserts these observations and prints all intermediate picks and
drawer states. Any unexpected result is a failed characterization, not a pass
to be forced by changing production. It does not establish that XTest sends a
duplicate release through GLFW or that it caused the historical native failure.

Root's proposed command, after saving exact command, start/end, exit and both
output streams under a **new** execution subdirectory:

```sh
git diff --exit-code b395c4049718f7ce015ddf25fc0a192d97821373 -- src dev resources deps.edn
# Run from this bundle directory for relative manifest paths:
sha256sum -c SHA256SUMS
# Then run from the Truth worktree root:
env -u DISPLAY -u JAVA_TOOL_OPTIONS -u _JAVA_OPTIONS -u JDK_JAVA_OPTIONS \
  -u JAVA_OPTS -u CLJ_JVM_OPTS \
  JAVA_CMD=/usr/lib/jvm/java-21-openjdk-amd64/bin/java \
  CLJ_JVM_OPTS='-Xms64m -Xmx256m' \
  timeout --signal=TERM --kill-after=5s 60s \
  /usr/local/bin/clojure -J-Xms64m -J-Xmx512m -M \
  .ημ/diagnostics/native-input-hygiene/callback-probe.clj
```

The 60-second callback-process bound is separate from the native supervisor's
300-second budget. No command above was run during preparation.

## Then: fresh ordinary input-only native smoke

The driver is an explicit derivative of the archived 25-minute driver, whose
hash and complete code diff are recorded in `COPY-PROOF.json`. It retains the
fresh Xvfb/free-port checks; PID/start/boot/cwd/executable and world/window guards;
pinned production/preparation hashes; no inherited Java options; 2 GiB game,
256 MiB client and tools.deps limits; 32 MiB child file caps; detached Popen
ownership; bounded startup; and shared absolute cleanup deadline.

Changes are confined to diagnostics:

- **270 seconds work / 300 seconds total**, retaining a 30-second cleanup
  reserve. An overrun, uncertain release or unreaped child remains explicit.
- Only R/Tab, Spark/Fine/Cruise, small look steps, inspection, one full PNG and
  stop are admitted. No movement, intervention, follow, offset nudge or video
  command is available. Formation and planetary targets are irrelevant.
- Keyboard cleanup sends key releases only. Mouse movement is observed at the
  requested menu hit before a single explicit `mousedown 1` begins. Its one
  `finally` `mouseup 1` is attempted only while that gesture is marked pending.
  The marker is cleared before the release command: a failed/ambiguous release
  is recorded and the attempt closes, without retrying it. Final process/Xvfb
  cleanup still occurs. Successful clicks and Tab never receive an additional
  unconditional mouse release.
- Every click has a bounded read-only postcondition: expected drawer, or the
  actual production Fine/Cruise displacement. Tab must change the free-cursor
  flag while preserving the drawer. Inputs never retry. Postcondition reads
  may repeat at most three times within 20 seconds, each client capped at ten
  seconds, still bounded by the overall work deadline. Every observation stays
  in its own numbered output and the operation log.
- Look is limited to nonzero steps of at most 100 pixels per axis, requires
  observed manual/locked mode and the applied GLFW disabled-cursor state, and
  records its expected angle from the observed sensitivity. It checks actual
  yaw/pitch within 0.02° after that single gesture. Failure records an input
  delivery mismatch; it does not retry, change gain, or set the camera.
- The snapshot remains read-only. Added protocol fields expose existing cursor,
  look sensitivity, applied cursor mode, pending pick and camera angles. Preset
  expectations come from the existing `widgets/spark-flight-ranges` values.
  Host config and camera are sampled separately from the immutable world and
  cannot certify a coherent render frame or exact callback chronology.

Root should execute the following as individual stages, inspecting each result;
they are not a blind shell batch. Each action internally records before/after
readbacks. Use a genuinely new output name and an unused loopback port. Port
7898 is an example, not a claim of current availability.

```sh
task_dir="$PWD/.ημ/diagnostics/native-input-hygiene"
task_run="$task_dir/runs/attempt-01"
python3 "$task_dir/runner.py" start "$task_run" --port 7898
python3 "$task_dir/runner.py" tap "$task_run" r
# Ordinary startup preserves a free cursor. If readback contradicts that,
# stop and inspect rather than blindly adding a toggle.
python3 "$task_dir/runner.py" click "$task_run" spark
python3 "$task_dir/runner.py" click "$task_run" fine
python3 "$task_dir/runner.py" frame "$task_run"
python3 "$task_dir/runner.py" click "$task_run" cruise
python3 "$task_dir/runner.py" click "$task_run" spark
# The last click leaves the cursor over Spark. Neither Tab nor its key cleanup
# should create another pick. Each command verifies the drawer remains closed.
python3 "$task_dir/runner.py" tap "$task_run" Tab
python3 "$task_dir/runner.py" tap "$task_run" Tab
python3 "$task_dir/runner.py" tap "$task_run" Tab
python3 "$task_dir/runner.py" look "$task_run" 100 0
python3 "$task_dir/runner.py" look "$task_run" -100 0
python3 "$task_dir/runner.py" look "$task_run" 0 100
python3 "$task_dir/runner.py" look "$task_run" 0 -100
python3 "$task_dir/runner.py" stop "$task_run"
```

`start` launches the detached supervisor; it alone creates Xvfb and the game.
Each external stage writes one serialized allowlisted request. Inspection uses
only the fixed `snapshot.clj` through `clojure -J-Xms64m -J-Xmx256m
-M:demo-client`; no arbitrary remote form is accepted. All requests, argv,
numbered client output, input observations and exits are preserved. Startup
may fail honestly under the declared time/resource limits.

Judge the result using `closed.json`: outcome, cleanup errors, unreaped children,
budget flag and `mouse_release_uncertain`, not the supervisor's exit code alone.
The normal stop is process-owned teardown, not proof of Escape lifecycle
correctness. The primary/retained native process, port 7896, physical display
and previous runs are untouched.

## Acceptance and limits

A successful short run establishes that these specific balanced gestures and
small motion steps reached the current ordinary UI with the recorded outcomes.
It is not proof of all event schedules, historical causality, raw mouse-device
behavior, hardware performance, manual planetary approach or gameplay progress.
No long formation run follows automatically. Root must review the actual
callback output and native result before deciding whether another approach run
is warranted or a separately scoped production input defect exists.

Preparation manifests list only the frozen source/document/static files. New
callback output and `runs/` files are excluded and must receive their own closure.
Root owns canonical comments, receipts, commits and publication.
