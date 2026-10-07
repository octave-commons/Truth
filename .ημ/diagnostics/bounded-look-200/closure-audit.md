# Bounded-look-200 attempt 01: closed evidence audit

(己, p=1.0) The attempt **failed on an invalid operator menu request and cleaned up**. Four native 200-pixel calibration looks were acknowledged. There was **no target admission, target aim, W input, approach, or commitment**. This audit read closed files only; it did not import the driver, run tests/JVMs, probe processes, or send input.

The ordinary nebula used production source b395c4049718f7ce015ddf25fc0a192d97821373, private display :1, port 7898, world atom 1558902476, GLFW window 132442363675584 and X11 window 2097159. All 186 current source files match launch hashes and the recorded b395 manifest. All 16 preparation checksums and six runtime pins match the released manifest fae23f36582a3b67caf1e0fd0ba33625b9519e7b6f507a1c42a040662b4ed59e. Both saved operator programs match their pre-execution hashes.

## Observed sequence

R entered manual mode. Actual menu actions selected Fine, Cruise, and two halvings, leaving D=7.5e13 m/tick; Spark closed and Tab locked the cursor. Four relative mouse gestures were [200,0], [-200,0], [0,200], [0,-200]. Acknowledged yaw/pitch values were [-88,-20], [-90,-20], [-90,-22], [-90,-20], with sensitivity .01. Final calibration read tick 396 returned to baseline. One 1280×720 young-world PNG followed near tick 398; its SHA256 is 19f1bf1d40b46bbd0b51ec40add2c4c7584bbcc0df08dc29e543bb25247979d0. No mature visual or FPS claim follows from it.

Twenty formation polls ran, numbered 00–19. Poll 18/tick 4008 showed no planets; poll 19/tick 4164 first observed 12. IDs 1010, 1011 and 1012 were stored candidates and currently ready under the actual commitment predicate; only 1010 had fresh handoff membership. This bounds first observation, not exact birth time. Root examined 1010 and obtained two further coast snapshots without thrust:

| Observation | Tick | Spark-to-1010 range |
| --- | ---: | ---: |
| Poll 19 | 4164 | 153,220.751 AU |
| First coast | 4243 | 154,250.648 AU |
| Second coast | 4335 | 155,453.763 AU |

Observed rates 5.199, 4.737 and 5.123 ticks/second yielded N*=23. At D=7.5e13, the coast fraction was 0.104276174721, exceeding .1; the combined fraction was 0.178452010348, below .25. The independently recomputed screen therefore **rejected** the setting. It is a heuristic, not a trajectory guarantee; two coast samples do not establish fully settled velocity.

## Exact rejection and cleanup

The final IPC request 1791369254174771460-b17e51ce was click spark at T749.422839. Fresh snapshot 0065, tick 4490, showed manual mode, cursor-free=false, no open domain and no pending pick. The assertion in runner.py lines 696–700 requires a free cursor before any menu positioning. It failed. **No menu-position, mouse press, new look or retry followed this request**; only cleanup keyup r Tab ran. Root omitted unlocking before reopening Spark. This is not evidence of failed native click delivery, a lost Tab acknowledgement or impossible piloting.

Closed state records failure at 2026-10-07T10:34:15.732769+00:00, T750.858305, before the original 960-second no-target cutoff. There were no cleanup errors, uncertain/pending releases or unreaped children. All 33 supervisor children have matching reap records before closure: the game exited 143 during cleanup and the other 32 returned 0. All 40 outer clients were reaped: 39 returned 0 and the rejected-menu client returned 1. The latter intentionally waited for closure and reaped at T751.045270, about .187 seconds **after** supervisor closure.

Root's separate root-attempt01-closure.json observation at 2026-10-07T10:36:53.166905+00:00 records supervisor 3451533, Xvfb 3451543, game 3451673 and snapshot worker 3452818 absent. This is root's independent process observation, not a live probe by this auditor. Explicit heaps were 2 GiB for the game and 256 MiB for the worker. All recorded output files are below their child file ceilings.

## Complete journal checks

The audit checked 65 ordered snapshots, IDs 0001–0065, 132 request-journal events, 913 raw nREPL message maps and **543,254 UTF-8 bytes**. Contiguous request intervals exactly partition the raw bytes, each with one returned value and one done response. Full consumed stdout byte counts match reconstruction. Every snapshot identifies the same ordinary nonfixture world/window, global commitment 0 and nil thrust. No published body-overlap true value appears; this is not binding-consumer history.

The 402-event operations log has 39 IPC requests: 25 inspect, 7 click, 4 look, 2 tap and 1 frame. There were 38 successes and the one rejected request. Eighteen acknowledgement groups used 26 reads: 11 groups of one, six of two, one of three. **No group exceeded three reads**, so the longer observation allowance was not exercised. The four look acknowledgements took 1, 1, 1 and 2 reads; they establish those young-world responses, not mature aiming performance.

The only nonempty native-run stderr files are Xvfb's 2,480-byte display/keymap diagnostics and the rejected CLI's 543-byte assertion traceback. Game stdout is 74,035 bytes with shader startup and 1,089 literal K clamp binds markers. Interleaved text is not a per-tick solver trace or causal ejection evidence. There was no GL-error observer or FPS measurement.

## Preserved limitations

- The poller checks stored-ready before nonzero commitment. Simultaneous truth could mislabel its stop reason, although either condition stops and the supervisor treats commitment as terminal. All actual commitment samples were zero, so this reporting bug did not trigger or cause this failure. The original source is preserved and is not described as fixed.
- The original preparation manifest was a metadata envelope incompatible with the flat filename-to-hash reader. manifest-shape-repair.json preserves its bytes/hash and failed reader probe. The released flat six-pin map matches every runtime file; executable/test/plan pins were unchanged. This prelaunch failure is not a native failure.
- There was no successful admission, approach, sustained binding, commitment, sculpting, embodiment or Gate. Root's screening choice of 1010 is distinct from driver target admission. This one early failure does not qualify every cancellation/deadline path.
- An initial audit-only assertion omitted the returned value when measuring reconstructed stdout. Correcting that expectation to the actual worker contract yielded 3600 passing offline assertions without altering raw evidence.

The JSON audit retains critical hashes and an inventory digest over 335 selected historical files (1,384,602 bytes). All selected inputs were rechecked unchanged. Attempt 02 preparation and both audit outputs are excluded; only these two audit files were written.

The next action is to preserve this attempt while root reviews a separately scoped successor operator sequence. This audit authorizes no new run, input, deadline or physics change.
