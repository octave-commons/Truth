# Hidden native line regression on the host Intel GPU

Observed on 2026-10-06 at Truth c79be1e957695505b48ea7f1905821927e7fb270.
The four inspected source hashes matched before and after execution; no production,
test, dependency or board file changed. Root authorized context creation after the
exclusive baseline window ended. The existing Xvfb game was not contacted.

## Result

- `glxinfo -B` on display `:0` exited 0 in 0.394 seconds. It reported Intel,
  Mesa Intel(R) Arc(tm) Graphics (MTL), PCI device 0x7d55, accelerated yes,
  and OpenGL core 4.6 / Mesa25.2.8.
- One separate JVM ran the unchanged production-context native line regression
  under a 180-second timeout and explicit 128MiB initial / 1GiB maximum heap.
  It exited 0 after 24.840 seconds: **1 test, 9 assertions, 0 failures/errors**.
  This duration includes JVM startup and loading; it is not GPU performance.
- The actual tested context independently reported vendor `Intel`, renderer
  `Mesa Intel(R) Arc(tm) Graphics (MTL)`, version
  `4.6 (Core Profile) Mesa 25.2.8-0ubuntu0.24.04.4`, and `visible? false`.
  The recorder observed one context and confirmed restoration of its original
  process-local test function. Both commands' stderr files are empty.
- The test exercised the production forward-compatible core context, line shader,
  line upload/draw path, default and fading opacity, GL_NO_ERROR and nonblack
  framebuffer pixels. The existing test's nested finally cleanup remained in
  force. The owned process was reaped; its timeout PID no longer exists.

This is hardware-context correctness evidence for that one rendering contract.
It is not a full game scene, visible game capture, GPU throughput measurement,
native FPS result, manual approach/commitment result, or Gate completion. The
hardware inventory also exposes NVIDIA RTX4070 device access, but no NVIDIA
context was selected or tested here.

## Provenance and scope

`plan.md` is the unchanged preparation-only proposal as it existed before root
execution authorization; its opening historical statement does not describe the
completed result. `native-start.json` records the exact executed command, which
uses this directory's same-hash recorder instead of its earlier /tmp location.
`source-and-start.json` and `post-run-verification.json` pin source and verify it
remained unchanged. `hardware-inventory.json` records passive sysfs/device access.
Raw command stdout, stderr, status, timestamps and durations are preserved.

The recorder wraps only the existing test's private hidden-context callback in
its disposable JVM. It invokes the original callback once inside the existing
cleanup boundary and reads GL identity on its current context thread. Independent
source-only review by intent_research found no context, cleanup or alias blocker.
No production API was added, no test skipped, and no result was substituted.

No window was shown or focused by this procedure; no input, desktop unlock,
other-window capture, X access-policy change, driver installation, global setting
change, game lifecycle change, serialization or world migration occurred. The
retained Java3083624 / Xvfb:2 / nREPL7895 world was not addressed. Pre-existing
untracked manual-approach evidence is outside this inventory and untouched.

`CLOSED-FILES.txt` lists exactly this bundle. `SHA256SUMS` hashes every listed
file except itself and is verified from this directory with `sha256sum -c`.
