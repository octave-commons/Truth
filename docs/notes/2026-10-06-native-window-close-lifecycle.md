# Native window closure and the retained development service

Observed on `24cbffd40fcc22e77cb967fa9da2b65e67be509c` during the ordinary-nebula
[precision verification](../../.ημ/diagnostics/flight-precision/native-24cbffd/README.md).
This finding belongs to the existing native verification card
`f369c598-279c-498d-a64f-45d2ce16ad34`; no lifecycle implementation or card transition
is included in this note.

## Observed result

Actual Escape input at 2026-10-06T19:55:20Z stopped the render thread. The
[first readback](../../.ημ/diagnostics/flight-precision/native-24cbffd/after-escape.edn)
and [later readback](../../.ημ/diagnostics/flight-precision/native-24cbffd/after-escape-later.edn)
show an alive simulation thread, `:stop false`, and progression from tick 2272
to 3799 without a service/UI error. The X11 window remained
[`IsViewable`](../../.ημ/diagnostics/flight-precision/native-24cbffd/after-escape-xwindow.txt)
with a stale rendered frame. Escape occurred after the video interval; these
separate artifacts are the closure evidence.

The owned service and display were subsequently terminated and reaped. The
service printed `Dev window stopped.` and its port 7895 became free. The retained
world on port 7894 was not contacted or signalled.

## Intended policy versus the defect

- [AGENTS](../../AGENTS.md#running-the-dev-service) explicitly describes the
  continuously running development service as intentional.
- [The demo instructions](../demo/README.md#L322) say Escape closes the window
  and Ctrl-C stops the terminal process. The shorter flight-design wording
  [“ESC quit”](../designs/spark-flight-and-camera.md#L262) is ambiguous beside
  this precise service contract.
- Both [demo serve](../../dev/demo.clj#L237) and
  [the dev entry point](../../src/infra/dev/server.clj#L59) await process shutdown
  after starting the window/simulation and nREPL. Continued simulation/nREPL
  after window closure is therefore consistent with the service policy.
- [The input callback](../../src/infra/render/input.clj#L191) only sets the GLFW
  should-close flag. [The render loop](../../src/infra/dev/window/loop.clj#L435)
  returns when that flag is set, but its owning
  [window loop](../../src/infra/dev/window/loop.clj#L448) has no disposal/finally
  path. [Service stop](../../src/infra/dev/window/lifecycle.clj#L95) flags, joins
  and resets state; it does not dispose of the native window either.
- [Existing window tests](../../test/infra/dev/window_test.clj) cover input
  intent publication and error reporting, but do not establish native close or
  resource-lifetime behavior.

The confirmed defect is the still-mapped window after rendering ends, not the
continued service. Do not repair this by making Escape terminate the JVM,
discard the live world or stop the nREPL service.

## Smallest follow-up

Propose a separate three-point lifecycle repair under the native verification
card: release the native window on normal and exceptional render exit, clear its
stale published handle, and preserve simulation/world/nREPL semantics. Verify
real Escape causes the X11 window to disappear while simulation ticks and
read-only nREPL remain available; verify subsequent service shutdown is clean.
Add focused lifetime/error-path coverage before implementation, and clarify
“quit” to “close window” where needed. Canonical title searches for window,
Window, close, Escape, quit and shutdown found no existing implementation card
for this repair. Board creation and implementation remain for a later dispatch.

## Proposed disposal contract — 2026-10-07

This planning amendment supersedes the tentative three-point estimate above,
not its observations. Source inspection at
`b395c4049718f7ce015ddf25fc0a192d97821373` supports one **five-point hygiene
child**, `native-window-close-disposal`, under the unchanged measurement owner
`f369c598-279c-498d-a64f-45d2ce16ad34`. It is proposed work for Incoming/planning
review, not an implementation admission or new native reproduction. The
[merged PR7](https://github.com/octave-commons/Truth/pull/7) launch documentation
and [continuous-service policy](../../AGENTS.md#running-the-dev-service) supply
the existing behavior to restore: close the window, retain the running service.

### Source and API boundaries

| Boundary at the pinned source | Finding and consequence |
| --- | --- |
| [Window owner](../../src/infra/dev/window/loop.clj#L455) | `window-loop` publishes a handle, installs callbacks and renders without a finalizer. Its setup and loop exits need one owner-controlled cleanup path. |
| [Partial construction](../../src/infra/render/window.clj#L59) | `create-window` can allocate a GLFW handle and then throw before returning it. An outer `window-loop` finalizer alone cannot recover that unpublished handle. |
| [Error display](../../src/infra/dev/window/loop.clj#L288) | The error-overlay branch renders and returns true without event polling or a should-close check. Close processing must also work while this branch is active. |
| [Service stop](../../src/infra/dev/window/lifecycle.clj#L95) | `stop!` writes the close flag from another thread, joins each worker for 5 seconds and then clears service state even if a worker remains alive. Adding real destruction without fixing this ownership race could call GLFW through a freed handle or admit a new owner too soon. |
| [Resources](../../src/infra/dev/window/loop.clj#L71) | Direct sphere/cube meshes and config program handles are separate from [asset caches](../../src/infra/render/asset.clj#L146). [Volume reset](../../src/infra/render/volume.clj#L70) drops cached texture/host references; it does not delete the texture. One asset helper does not cover all retained handles. |
| [Offscreen call](../../src/infra/render/window.clj#L175) | `render-to-file` creates another unshared context and touches the global shader cache. The window's screenshot path calls it. Cache membership alone is therefore not proof that an ID belongs to the closing visible context. |

The [GLFW destruction contract](https://www.glfw.org/docs/latest/group__window.html)
requires destruction outside callbacks and with the context absent from other
threads. Its [thread-safety rules](https://www.glfw.org/docs/latest/intro_guide.html#thread_safety)
restrict window creation/destruction and event processing to the main thread;
the close flag itself is not synchronized. Truth currently uses a named render
worker on Linux. This proposal preserves that existing owner and tests that
host; it does **not** establish cross-platform main-thread compliance.

### Proposed behavior

1. Escape/OS close ends the existing render loop and retires its window after
   the callback returns, including while the error overlay is visible. It does
   not set the shared simulation stop flag, replace the world atom, terminate
   nREPL or terminate GLFW/the JVM. Rendering failure still reports the original
   error; cleanup does not disguise it as successful closure.
2. Every allocated visible window has a cleanup owner, including partial
   context/capability initialization and callback/setup failure. Finalization
   runs on that owner, attempts all remaining release stages after a cleanup
   error, preserves the primary failure and records secondary cleanup failures.
   Per-window callbacks are freed; the process-global error callback and GLFW
   termination stay process-owned.
3. While its live context is current, the owner may explicitly delete only
   resources whose context ownership is known. It then detaches that context,
   clears the thread's GL capabilities and destroys its unshared window/context,
   which retires remaining context-owned GPU objects. Clear the corresponding
   host config handles and stale shader/asset/volume cache references before a
   later service can reuse them. Never delete an unproven cached numeric ID in
   another context, or invoke GL deletion after detachment/destruction. An
   offscreen screenshot redesign or a new cache framework is outside this card;
   a bounded teardown strategy must satisfy this constraint before RED admission.
4. Publishing/clearing a window handle and reporting owner failure must compare
   the exact service/stop identity, not only a reusable native pointer. An old
   owner must not clear a later service's handle or overwrite its error state.
   Cleanup failure must not leave a published freed handle.
5. Full `stop!` signals the captured service's shared stop atom and joins those
   workers; it does not call GLFW through a concurrently destroyed pointer.
   Preserve the existing bounded waits. Clear only that same service after both
   workers actually end. If a worker survives or waiting is interrupted, retain
   its service ownership and report failure, so `start!` cannot overlap it.
   There is no new window-only reopen API: restart means explicit completed
   full stop, then ordinary `start!` with the retained world atom and recorded
   ordinary-nebula launch options, with fresh context resources. Reusing the
   atom alone must not silently select a different tick/body-projection route.

Preserving the world means preserving its atom and normal evolution, not
freezing all component values. A held manual thrust may outlive the render
loop under current input ownership; changing that policy is separate work.
The current simulation loop pauses tick execution while `:ui/error-state`
exists. Error-overlay closure must retain that error and the responsive service;
it must not clear the error or promise advancing ticks without separate recovery.
Likewise this card does not repair caller-thread reload helpers, pending
screenshot semantics, offscreen simulation behavior, gameplay progression,
camera controls or the rendering architecture.

### RED and native acceptance for later implementation

| Check | Required observation; no result claimed yet |
| --- | --- |
| Normal/error close | Drive the actual owner loop and ordinary GLFW close callback. Error-overlay mode must also poll/process close and exit, retaining its error state and live service without claiming tick advancement. Record owner-thread finalization and exactly one retirement of each successfully allocated window. |
| Partial setup and cleanup failure | Inject failures after native allocation, during setup and during a cleanup stage. Later cleanup stages still run; the primary error stays distinguishable, and the matching published handle is removed. Unallocated handles are never destroyed. |
| Stop deadline and stale owner | Use bounded worker/latch tests to retain ownership on timeout/interruption, deny overlapping start, and permit clearing only after both captured workers end. An old finalizer cannot clear a different service identity, even if a numeric window value repeats. |
| Real secondary process | Launch an ordinary default nebula in a separate disposable JVM/display/free port. Record exact source, PID/start identity, window ID, worker/world identity and ticks. Send real Escape, then verify X11 disappearance externally, no published window handle, render worker ended, the same simulation/world still advancing and nREPL responding. Never query GLFW through a destroyed pointer. |
| Fresh resource use | In that disposable process, fully stop both workers and restart with the retained world atom and recorded ordinary-nebula launch options, including the same tick/body-projection functions and fresh GL resources. Verify a new usable native window/context and real draw without stale-resource GL errors; then close and reap only this instance. This is a full service restart, not automatic reopen. |

Native fault fixtures may isolate error/setup lifetime boundaries; label them
separately from the ordinary-nebula Escape evidence. Fake handle/order tests
cannot establish X11 disappearance or usable real-context restart. The retained
primary game is never the reproduction target. Future implementation still
requires reviewed scope/size, a legal InProgress claim, committed failing RED,
the relevant ordinary/native tests and the full repository gates. If resolving
context ownership expands into offscreen/reload architecture, refine the scope
before code instead of stretching this five-point estimate.
