# Bounded 200-pixel look — separately frozen preparation

This diagnostic derivative implements only the [root-recorded scope](root-scope.json)
under existing InProgress3 native-verification owner
`f369c598-279c-498d-a64f-45d2ce16ad34`. Its predecessor preparation is
`51a35312290fb722740a21fd225c3227d9c8b12c`. The inherited
[observation-window plan](../input-observation-window/PLAN.md) supplies the
unchanged process, input, target, journal and cleanup contracts. This preparation
does not authorize execution: root and independent source review plus a fresh
release are required.

## Observed boundary and minimal change

Root's [closed-run observation](../input-observation-window/root-closure-observation.json)
records a failed aim client and clean owned-process closure. The scoped account
records natural target 1010 admitted at T788.978, 42 acknowledged ordinary looks
of at most 100 pixels per axis, then exhaustion of the original 120-second aim
allowance. The last completed controller geometry was about 6.52 degrees from
the target. This is the controller's last geometry, not a claim that the final
camera stayed there. No W pulse or PNG occurred during that aim. The supervisor
closed by operator-stop at T910.585; root recorded all four owned PIDs absent.
Client failure and clean supervisor cleanup are distinct outcomes. The prior
native acknowledgement groups used at most three reads, so they do not qualify
the inherited extension beyond three observations under real latency.

The [runner](runner.py) adds one shared `LOOK_PIXEL_CAP = 200` and uses it in the
existing per-axis validation. The [aim client](aim-client.py) uses that same
constant to clamp each rounded pixel request and updates its runner hash pin.
There is no other executable change. At the previously observed sensitivity of
0.01 degrees per pixel, 200 pixels corresponds to 2 degrees **per axis** before
the existing pitch clamp. A two-axis gesture is not a promise of a 2-degree
total angular displacement. Production sensitivity and callbacks are unchanged;
the actual current sensitivity and acknowledged orientation must still be read.
Larger requests do not guarantee mature-world callback latency or successful
orientation, translation, capture or commitment.

## Preserved contracts

- The 20-second maximum observation allowance, late-result rejection, one
  gesture followed by observations, and no replay on uncertain input are intact.
  Inspection errors and terminal commitment still propagate. The allowance is
  clipped by current phase/work/aim time; caller timeouts may expire earlier.
- Shared 180-second startup, T960 no-successful-target cutoff, 1470-second work,
  1500-second total and deadline-clipped cleanup are unchanged. No time or
  operation budget is expanded: three aims, 360 run-wide looks, twenty requested
  at-most-two-second W holds and three PNGs. Each aim retains its original
  120-second limit and 180-step ceiling, further constrained by run-wide budgets.
- Admission, fixed target, active-aim exclusion, terminal commitment ordering,
  input reserves and submitted-request STOP/closure behavior are unchanged.
  Root remains the sole operator. There is no automatic target choice, pursuit,
  follow camera, seed-based pointing, fixture, teleport or world setter.
- Private display/free port, source/PID/start/boot/cwd/executable/world/window
  guards, process limits and original production tick/projection remain intact.
  The production pin is `b395c4049718f7ce015ddf25fc0a192d97821373`.
- [snapshot.clj](snapshot.clj) and [snapshot_client.clj](snapshot_client.clj) are
  byte-identical copies. Fixed read-only connection, serialized IDs, 2048 reads,
  4 MiB per-request/128 MiB aggregate encoded-response limits and exact UTF-8
  journals remain intact. These post-decode limits do not bound transport
  allocation; disconnect is not remote cancellation. OS/JVM stalls cannot be
  forcibly preempted by the diagnostic's requested duration.
- `HERE` selects this new directory and `HERE.parents[2]` resolves this Truth
  worktree. Frozen predecessors and their raw evidence are untouched.

## Pure qualification

[test_protocol.py](test_protocol.py) retains all 24 predecessor test methods
byte-for-byte and adds six tests. They exercise the actual diagnostic action
with one recorded gesture and acknowledgement for all signed 200-pixel axis
boundaries, reject 201 pixels before reads or native commands, verify geometry
clamping and reduced heading error, preserve small-error rounding and the yaw
seam, and preserve the existing pitch clamp and unsupported target rejection.
Two new helpers load the diagnostic module under the existing operation blockers
and construct synthetic framing strings; they do not create game worlds.

Before the cap change, the [RED log](pure-tests-red.stderr) recorded 30 tests,
10 expected assertion failures including parameterized subcases, zero errors,
and actual process exit 1. All 24 inherited tests passed. After the minimal
change, the [GREEN log](pure-tests-green.stderr) recorded all 30 tests passing
with actual exit 0. Test time and native readbacks are synthetic. Subprocess,
socket, process-signal and native identity entry points are blocked; native
commands in action checks are explicit recorders. This proves the diagnostic
protocol boundary, not real callback delivery, native cleanup or gameplay.

[STATIC-CHECKS.json](STATIC-CHECKS.json), [COPY-PROOF.json](COPY-PROOF.json) and
[SOURCE-EQUALITY.json](SOURCE-EQUALITY.json) record actual exit evidence,
unchanged methods/guards, exact diffs, AST/link checks and the 186 production
file pins. Byte-identical Clojure files retain their prior static results; no
new JVM/project code was loaded. The manifest excludes mutable future runs.

## Required fresh-run calibration before formation

After separate release, root must start a fresh ordinary young nebula in a new
run directory. Initialize cursor history through the already qualified ordinary
Spark-menu opening/closing, then lock manual look with the ordinary Tab gesture.
Read and retain baseline yaw, pitch, sensitivity and released thrust. Calibrate
away from pitch saturation; at the observed 0.01 gain the baseline needs more
than 2 degrees of margin from either pitch limit.

Submit `look 200 0`, `look -200 0`, `look 0 200`, `look 0 -200` separately,
awaiting each original action result and intended orientation before the next.
Each pair must return to its recorded baseline under the existing 0.02-degree
acknowledgement tolerance, including a final fresh inspection. Require the
same owned world/window, manual locked controls, no pending pick and nil thrust.
These four looks consume the ordinary run-wide look budget. A failure or
uncertain result stops the run; never resend a gesture or continue into
formation to compensate. No calibration is executed by this preparation.

Only successful calibration allows root to continue the already scoped ordinary
setup and bounded formation observation within the **original** run clock. No
deadline restarts or extensions are granted here. Later aiming still refreshes
the fixed actual target and requires ordinary admission; it carries no guarantee
that moving-planet approach, binding, embodiment or Gate will be reached.
