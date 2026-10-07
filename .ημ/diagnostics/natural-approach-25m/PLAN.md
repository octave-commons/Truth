# Ordinary natural-approach follow-up — 25-minute preparation only

Existing owner: `f369c598-279c-498d-a64f-45d2ce16ad34` (native playable
verification). Source base: `b395c4049718f7ce015ddf25fc0a192d97821373`.
Nothing here has been run. Root operates exactly one separately bounded follow-up attempt after
independent source review. This is measurement, not feature admission or a
claim of overlap, binding, commitment, sculpt, Gate, or playable completion.

## Scope and actual production path

Start a **new** ordinary seed-42, 1,000-gas nebula using unchanged
`clojure -J-Xms256m -J-Xmx2g -M:demo serve nebula`. The launch performs the
existing ordinary nebula selection. It is not a retained-world recovery or
snapshot replay. Actual `domain.arc/tick-genesis` and
`infra.render/phase0-bodies+fields` remain identical throughout. No world,
tick, projection, focus, config or render wrappers/setters are installed.
Ordinary startup selects fit-all with a free cursor. The recorded readback
after the real R press marks the beginning of the manual segment.

The source guard compares tracked `src/`, `dev/`, `resources/` and `deps.edn`
to the base, then pins their working-file hashes at launch. Preparation hashes
are also pinned and rechecked. No live source reload is supported.

The supervisor owns one dynamically allocated Xvfb display, one disposable
Java game, short read-only demo clients, X input tools and optional FFmpeg.
It uses no PM2, existing display, existing nREPL, existing world, fixture,
teleport, body/time injection, forced classification, camera-follow API or
new serialization path. It never targets the retained PID or port 7896.

## Operator commands

Run from this worktree. Choose a new direct child name under `runs/` and an
unused loopback port; `7897` below is a proposal, **not a verified free port**.
The driver refuses occupied ports and old common ports 7888/7890/7896.
It captures a fresh PID/start/boot/cwd, display, world-atom identity, GLFW
window and X window. All these are new values, never inherited guards.

```sh
task_dir="$PWD/.ημ/diagnostics/natural-approach-25m"
task_run="$task_dir/runs/attempt-01"
python3 "$task_dir/runner.py" start "$task_run" --port 7897
python3 "$task_dir/runner.py" inspect "$task_run"
python3 "$task_dir/runner.py" frame "$task_run"
```

Inspect `state.json`, the operation result and the immutable numbered
`*-inspect.stdout`/`.stderr` files. First readiness requires both workers,
an unchanged tick/projection route, no reported errors, nonfixture nebula,
and `Runtime.maxMemory <= 2 GiB`. The initial read can wait up to 10 seconds
for asynchronous shader/window readiness. No readiness failure is retried.

Use actual input in separate stages, checking snapshots/pixels between them:

```sh
python3 "$task_dir/runner.py" tap "$task_run" r
python3 "$task_dir/runner.py" inspect "$task_run"
# If the current readback says cursor-free? false, toggle it once:
python3 "$task_dir/runner.py" tap "$task_run" Tab
python3 "$task_dir/runner.py" click "$task_run" spark
python3 "$task_dir/runner.py" click "$task_run" fine
python3 "$task_dir/runner.py" inspect "$task_run"
python3 "$task_dir/runner.py" frame "$task_run"
# Close Spark by its actual menu hit and relock cursor only as readback requires:
python3 "$task_dir/runner.py" click "$task_run" spark
python3 "$task_dir/runner.py" tap "$task_run" Tab
```

These are staged examples, not a blind macro: R resets ordinary camera settings;
Tab is a toggle. `click` accepts only Spark/Fine/Cruise and computes coordinates
from a fresh production menu layout. It requires manual mode and a free cursor.
No hardcoded target or historical menu coordinate exists. There is no general
mouse-click command permitting world selection or hidden UI policy changes.

```sh
# Bounded optional capture runs concurrently with further operator stages.
python3 "$task_dir/runner.py" record "$task_run" 30
# Examples only; choose direction/duration from actual fresh target geometry.
python3 "$task_dir/runner.py" look "$task_run" 20 -10
python3 "$task_dir/runner.py" hold "$task_run" w 1
python3 "$task_dir/runner.py" inspect "$task_run"
python3 "$task_dir/runner.py" frame "$task_run"
python3 "$task_dir/runner.py" stop "$task_run"
```

Look deltas are bounded to ±500 px per stage. Movement holds are >0 and ≤10
seconds and allow only W/A/S/D/Space/left-Ctrl. Discrete taps allow R, Tab,
comma and period. Arrow offset nudges, follow controls, Escape, interventions
and sculpt are excluded. Any failed stage closes the attempt; do not repeat
an uncertain input. Fine changes only thrust displacement, not velocity or
target matching. Numerical heading assistance is diagnostic scripted piloting,
not evidence of unaided human navigation.

## Resource, ownership and failure contract

- At most 1470 seconds of work from supervisor initialization, reserving 30
  seconds for cleanup within the 1500-second follow-up budget. Normal
  subprocess waits are bounded by remaining work time; all termination/reap
  waits share one absolute 1500-second cleanup deadline. Any overrun or unreaped
  child is explicit in the final record. This is an operational
  bound, not a hard guarantee against an uninterruptible kernel/hung host.
- Fresh Xvfb is 1280×720×24 and has TCP disabled. Only this application's
  private display is focused/captured. The locked physical desktop is untouched.
  Xvfb/software rendering is not a hardware renderer or performance claim.
- Game heap: 256 MiB initial, 2 GiB maximum. Client heap: 64/256 MiB.
  Inherited JAVA_TOOL_OPTIONS, _JAVA_OPTIONS, JDK_JAVA_OPTIONS, JAVA_OPTS and
  CLJ_JVM_OPTS are removed for owned children; only their names/presence are
  recorded, never contents. Tools.deps preparation uses explicit 64/256 MiB
  CLJ_JVM_OPTS. Clojure's user-config flags may precede the final explicit
  game/client -J flags; the actual game maximum is checked from the running JVM.
- Each child has a fresh process session. Every command/argv, display, port,
  process identity, bounded output path, timeout, exit and cleanup is recorded.
  Fast helpers can exit before /proc/exe is observable; this is recorded as an
  unavailable sample, followed by their Popen exit/reap result. Long-lived
  Xvfb/JVM/supervisor identity checks remain strict.
  Each child output/media file has a 32 MiB OS file-size cap; cap failure is
  retained, never called a successful capture. No unlimited video or log stream.
- Video is optional, one at a time, ≤60 seconds, full 1280×720 at requested
  12 capture frames/s with one encoder thread. This is not measured game FPS.
  Screenshots are the full private X framebuffer, not a paired per-body GL
  observation. The subsequent published readback has its own tick/time.
- A detached supervisor retains Popen ownership and reaps its children. Root
  stages submit only allowlisted request files; no arbitrary shell or remote
  form is accepted. The mutable `state.json` is operational state; immutable
  per-request outputs and append-only `operations.jsonl` preserve history.
- Every key/click action has a `finally` release of all allowed keys/button1.
  Overall shutdown attempts releases unconditionally while its verified X
  server lives, then stops/reaps helpers/video, Java, and Xvfb in that order.
  Failed release and cleanup are explicit in `closed.json`. No Escape lifecycle
  correctness is claimed. If the supervisor/host is killed, inspect exact
  ownership before recovery; no process-name kill or old-PID fallback exists.
- Start readiness is bounded; action submission is serialized with a lock.
  Explicit stop is a file request, not an external signal to a possibly reused
  PID. Stop may wait for an already bounded readback/hold before cleanup.

## Observation and acceptance limits

Each `snapshot.clj` call reads one immutable **published** world. Body positions,
velocities, relative geometry, binding/scar/commitment and current production
handoff output are from that same world. At most 128 sorted natural planetary
bodies are included; total/recorded/omitted counts are explicit. Menu/config and
camera are separate host reads. Stored candidate and per-body eligibility are
distinct from the full current `candidate/handoff-system` write-set.

The binding radius comes directly from `law.narrowing/world-focus-radius`
(currently 1 AU). `:published-focus-overlap?` compares published focus and
target, not the exact binding consumer input. Manual attention is prepared
before the fold and physical positions may advance/recenter afterward.
Snapshots cannot prove sustained consumer overlap by themselves. If physical
approach succeeds, a separately reviewed observation may substantiate the
binding-input boundary; none is installed for this follow-up attempt.

Root selects an actual fresh target adaptively. Keep focus offset zero when
assessing physical approach and record mode, offset, D, retention, relative
velocity, physical distance and target loss. If no natural suitable target is
present before the deadline, close as an inconclusive target-availability
result; do not force birth, advance time, reset, transplant or continue past
the bound. If approach fails, preserve the measured trajectory and inputs.
Do not promote overlap to commitment, resolution, sculpt, life, embodiment or
Gate, or a read-only local interpretation to native reviewer approval.

## Reused evidence/source handles

Process allocation follows `bin/demo-capture`, but none of its fixture tour or
closeup calls are used. Snapshot semantics come from
`.ημ/diagnostics/playable-foundation/manual-flight/approach-snapshot.clj` and
`eligibility-snapshot.clj`; actual Fine input precedent is
`.ημ/diagnostics/flight-precision/native-24cbffd/README.md`.
Bounded Java `/proc` reading follows the verified native-current repair.
Existing old handles/displays/PIDs and old snapshot hooks are not reused.

Root owns board comments, receipts, execution, evidence closure and publication.
Preparation alone does not claim any command below has succeeded at runtime.


## Why this separate budget exists

The first ordinary attempt remains immutable in
[`../natural-approach/runs/attempt-01/`](../natural-approach/runs/attempt-01/).
Its `closed.json` records operator stop at 2026-10-07T05:31:14.312677Z after
561.625129247 seconds, no cleanup errors, no unreaped children and no budget
overrun. Root reports its last published sample at tick 3748 and full frame
at tick 3825, both without planets; no thrust was applied. This new preparation
neither extends that run nor reuses its processes, display, port ownership,
world atom or window. A new unused run directory and fresh identities are
required even if an available port number happens to be reused.

Prior closed natural evidence establishes an operational range, not a promised
formation time:

| Prior run | Loaded source | Formation | Observed wall interval |
|---|---|---|---|
| Native default seed 42 / 1000 gas | `a3609f6931951f8e011950ec5eab0a24265e93cd` | Actual formation events tick 4169; handoff 4171. No planets at 4164; 24 at 4172. | Tick-zero monitor 18:25:15.652Z; no-planets sample 18:32:26.163Z; first planet sample 18:32:27.164Z on 2026-10-06. Birth bracket430.511–431.512 seconds after that monitor start. |
| Pure production seed 42 / 1000 gas | `81207e7c65212685456ebe0faff3cac1cec50613` | First 12 births at 4038; handoff 4040; second 12 births at 4907. | First birth 808.664 seconds (13m28.664s); handoff 809.468s; second batch 1010.349s (16m50.349s). |
| Pure production seed 43 / 1000 gas | Same `81207e7…` | 24 births at 4102; handoff 4104. | Birth 750.865 seconds (12m30.865s); handoff 752.578s. |

Immutable source evidence is present in commit
`7a607e7d9c8f7183baa2c14ddd82f3de69da48ff`:

- [`../formation-20261006T182457Z/progress.edn`](../formation-20261006T182457Z/progress.edn)
  and [`../playable-foundation/natural-formation-observation/birth-snapshot.edn`](../playable-foundation/natural-formation-observation/birth-snapshot.edn).
- [`../playable-foundation/live-long-run/README.md`](../playable-foundation/live-long-run/README.md)
  records the native route: paused startup for frame readiness, camera reframe
  at 240 seconds, then later actual selection input. It is natural nonfixture
  physics, but not the same ordinary/manual host trajectory as this attempt.
- [`../playable-foundation/natural-seed42/observations.edn`](../playable-foundation/natural-seed42/observations.edn)
  and [`../playable-foundation/natural-seed43/observations.edn`](../playable-foundation/natural-seed43/observations.edn)
  contain exact elapsed timestamps and actual event counts. These pure runs
  had concurrent work and are not isolated performance comparisons.

Current production disk maturity is `3.156e13` simulation seconds (about 1 Myr),
not a wall timeout. Both pure first-birth observations crossed disk ages near
`3.157e13` seconds. Adaptive pacing, workload, source and attention differ among
runs; the 25-minute bound merely provides more formation/piloting margin.
No target by the bound remains an inconclusive availability result. Do not
change the physical clock, force formation, add a fixture or extend the run.

This copy changes only three runner lines containing the work/cleanup budgets;
`snapshot.clj` is byte-identical to the reviewed ten-minute preparation. Its
published-versus-consumer limitations and every input/resource/cleanup guard
remain unchanged. `COPY-PROOF.json` records both hashes and the exact code diff.
If an optional video is recorded, root's subsequent evidence closure also
preserves a full-duration GIF companion; no video processing is run by this
preparation step.
