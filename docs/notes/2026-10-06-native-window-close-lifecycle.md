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
