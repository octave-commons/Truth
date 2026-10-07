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

## Cache retirement and test-seam audit — 2026-10-07

This appendix proposes a concrete refinement of behavior 3; it does not admit
implementation. The audit read source at `9a73733d93338d8a0e1abb4034632f992e20e8ec`
and verified that it is unchanged at the provenance-only successor
`ef2728c546e8248f8b31ea2e99169e0de2ec26ab`. No new native reproduction or test run
was performed. The owner remains Incoming 5.

### Actual resource inventory

| Allocation or retained reference | Source boundary and proposed retirement |
| --- | --- |
| Visible GLFW window, context and default framebuffer | [window.clj:59](../../src/infra/render/window.clj#L59) uses `share = NULL`. Its constructor owns a successfully allocated handle even if make-current, swap-interval or capability initialization subsequently throws. Destroy this exact window/context once, outside callbacks, on its existing render owner. |
| Six built-in shader programs and intermediate shader objects | [shader.clj:29](../../src/infra/render/shader.clj#L29), [cache:73](../../src/infra/render/shader.clj#L73), [built-ins:458](../../src/infra/render/shader.clj#L458). Cache entries contain ID/hash, not context identity. Failed compilation or replacement can leave an object absent from the final cache. Drop host cache references; context destruction reclaims objects actually owned by the closing context. Do not call `invalidate-all!` here. |
| Sphere and cube VAO/VBOs held directly in service config | [loop.clj:79](../../src/infra/dev/window/loop.clj#L79) uploads these without the asset cache. Clear `:mesh`, `:cube-mesh` and all six `*-program` config handles for the captured service; retain unrelated settings. The latest config is not an inventory of every allocation. |
| Generic asset mesh/texture caches | [asset.clj:23](../../src/infra/render/asset.clj#L23) stores VAO/VBO/optional EBO entries; [line 47](../../src/infra/render/asset.clj#L47) stores texture IDs. No production caller of `mesh!` or `texture!` was found, but these public caches can retain IDs. A host-only discard must clear them without the existing GL-delete helpers. `dispose-all!` is therefore unsuitable. |
| Froxel 3D texture and staging buffers | [volume.clj:70](../../src/infra/render/volume.clj#L70) holds a raw texture ID plus CPU float array/direct buffer; its cache is resolution-keyed, not context-keyed. Existing `reset-volume-cache!` already drops these references without GL deletion. `delete-volume` is a deliberate no-op. The direct buffers are JVM-managed, not explicit `memFree` allocations. |
| Per-pass VAO/VBOs | [mesh.clj:104](../../src/infra/render/mesh.clj#L104), [scene/setup.clj:89](../../src/infra/render/scene/setup.clj#L89), [hud.clj:28](../../src/infra/render/hud.clj#L28) allocate geometry for solids, volume, particles, lines, sprites, rectangles and text. Several normal-path deletes are not protected by `finally`; exceptions can orphan IDs outside every cache. Destroying their context covers these objects without expanding this card into each pass. |
| Offscreen context, FBO, color texture, depth renderbuffer and meshes | [window.clj:71](../../src/infra/render/window.clj#L71), [FBO:80](../../src/infra/render/window.clj#L80), [render-to-file:173](../../src/infra/render/window.clj#L173). These belong to a different unshared context. Its entry replaces global shader/volume references; its success-only cleanup does not restore the visible context. The visible finalizer must neither delete these numeric IDs nor claim to retire a leaked offscreen window. |
| Four per-window LWJGL callbacks | [input.clj:253](../../src/infra/render/input.clj#L253) installs key, cursor, button and scroll callbacks. Free these callback allocations before destroying their own window; context destruction alone does not free the Java/native callback wrappers. |
| Process resources and pure projections | [window.clj:48](../../src/infra/render/window.clj#L48) installs the process-global GLFW error callback. Leave it, GLFW termination and the GL loader process-owned. Leave the CPU-only body/particle projection caches alone; they contain no GL IDs. No other retained GPU allocation family was found in the renderer source search. |

The `ensure-resources` comparison also permits repeated sphere uploads when
`:requested-subdivisions` is nil, and its upload effects occur inside `swap!`.
This is an audit observation explaining why enumerating the last config cannot
prove complete disposal. Repairing that allocation path is outside this child.

### Proposed teardown choice: no explicit GL-object deletion

Use **zero `glDelete*` calls in the close finalizer**, even for apparently known
config IDs. Both constructors explicitly request unshared contexts. The
[OpenGL specification, §5.1.1](https://registry.khronos.org/OpenGL/specs/gl/glspec46.core.pdf#page=76)
states that destruction of the last context in a sharing group reclaims its
objects and other resources; reclamation is not a promise of immediate physical
memory release. This is the general lifetime rule, not a proposal to upgrade
Truth's OpenGL 3.3 request or its LWJGL 3.3.3 dependencies.

[GLFW destruction](https://www.glfw.org/docs/latest/group__window.html) retires
the window and context, forbids destruction from a callback or while current on
another thread, and specifies main-thread ownership. The existing Linux worker
limitation stated above remains. Explicitly detach the closing context if it is
current on the owner; clear that thread's LWJGL capabilities and destroy the
owned window. An unexpected different current context is diagnostic evidence,
not permission to delete or destroy that context. The closing owner must not
make an unknown context current to clean a cache.

[LWJGL's GL contract](https://javadoc.lwjgl.org/org/lwjgl/opengl/GL.html)
requires capabilities to be cleared when their context is destroyed.
[glfwFreeCallbacks](https://javadoc.lwjgl.org/org/lwjgl/glfw/Callbacks.html)
resets/frees callbacks attached to one window, excluding the global error and
monitor callbacks. These are current API references; this audit did not execute
a new version-specific binding test.

Keep all host discard, callback, detach, capabilities and window-destruction
stages inside the captured owner's finalization, with independent attempts after
failure. Remove only its published handle; retain the original render/setup
failure and record cleanup failures separately. A failed destruction must not be
reported as successful disposal or permit an overlapping replacement owner.
Partial construction needs the same bounded retirement inside `create-window`,
because the outer loop has no handle when that function throws before returning.

GLFW failures are not necessarily Java exceptions: its void destruction call
reports through the [per-thread GLFW error channel](https://www.glfw.org/docs/latest/intro_guide.html#error_handling).
The proposed retirement adapter must preserve any pre-existing error separately
and collect/copy the owner thread's stage error, without replacing the global
error callback. A normal Java return alone is not proof of successful native
retirement. Reading `glfwGetError` consumes that error state; record this behavior
explicitly, and do not confuse it with OpenGL's separate `glGetError` flags.

### Cache exclusion is a real remaining admission decision

Service-generation matching protects service state; it does **not** prove that
process-global caches are unused by another renderer. The normal screenshot
request is [called synchronously on the same render thread](../../src/infra/dev/window/loop.clj#L101),
so it finishes or unwinds before that owner's finalizer. However, direct public
`render-to-file` calls can enter from another thread, and there is currently no
common exclusion guard. A `glfwGetCurrentContext` check only describes the
calling thread and cannot establish absence of another cache user.

The smallest proposed enforcement is one renderer-entry ownership guard in the
existing render/window boundary: acquire before GLFW initialization or cache
work; keep ownership through visible-loop finalization or the complete offscreen
call; allow same-thread nesting for the existing screenshot call; reject a
foreign-thread entry before native allocation or any cache mutation. This is
bounded entry exclusion, not context-keyed caches, a render scheduler or a new
window manager. It must be exception-safe and release only its own acquisition.
The visible finalizer may discard global caches only while it still owns that
guard. If ownership cannot be established, report the invariant failure and
preserve foreign cache contents rather than silently clearing them.

**The guard and its foreign-entry rejection are proposed API behavior requiring
review and size confirmation before RED admission.** It does not repair the
nested screenshot's context restoration, leaked offscreen partial construction,
or caller-thread reload helpers. Arbitrary concurrent calls into low-level
shader/asset/reload functions remain outside the supported lifetime guarantee;
do not present this entry guard as protection from those calls. If that broader
concurrency guarantee is required, this five-point story must be replanned.
Likewise, service start/stop must serialize claiming and releasing a generation:
the current separate validate/read and reset steps cannot alone deny simultaneous
starts. No cache discard is safe on behalf of an obsolete generation.

### Smallest meaningful RED boundary

The first RED should exercise an observable lifetime defect in the existing
production path, not merely a missing cleanup function. No result is claimed by
the following test plan.

| Layer | Concrete seam and observation |
| --- | --- |
| Actual owner-loop close | In a disposable native test JVM, create a real context and install the production callbacks through `window-loop`. Close through the real key callback/close flag, then require owner return and matching published-handle removal. Baseline leaves the handle/window. Bound the error-overlay case so its missing event polling cannot hang the suite. The final ordinary-player acceptance still uses real Escape and external X11 disappearance. Never pass a fake pointer to a raw GLFW call. |
| Service ownership | Real worker/latch fixtures can test interruption and the existing join deadline without a native handle. Require retention of the same service while either worker lives, no off-thread GLFW call, and no stale stop/finalizer clearing a newer stop/thread identity even when a numeric handle repeats. Simultaneous start claims must admit at most one generation. |
| Host cache discard and entry exclusion | Seed foreign-looking numeric IDs in the existing cache atoms; trap GL-delete seams and require host discard to call none. Preserve unrelated config and pure projection caches. Exercise a live owner plus a foreign-thread renderer entry: rejection must precede context creation/cache mutation. Same-thread nesting must return to the outer ownership count; cleanup failure must not release someone else's ownership. These proposed seams do not exist yet. |
| Constructor/finalizer failures | Add the smallest redefable native-effect seams for allocation/context initialization and retirement. Fail after allocation, during callback setup and at each cleanup stage; require remaining attempts, original-error preservation and one owned retirement. Existing raw static calls cannot be fault-injected with ordinary Var redefinition: obtain root agreement on a behavior-neutral seam checkpoint or pair these additional cases with the implementation after the real owner-loop RED is committed. Do not claim fake-handle spies verify the native allocation boundary. |
| Real reuse | Use the existing `:native-render-test` suite pattern in a separate process, then the ordinary-nebula Escape/full-stop/restart procedure above. Confirm fresh compilation and real drawing without stale-ID errors, retaining the world and recorded tick/projection options. Do not use `glIs*` or any GLFW query on a destroyed pointer as evidence. |

This refines the existing five-point story without adding gameplay, new resource
caches or allocation optimizations. Zero-delete teardown resolves the resource-ID
ambiguity; entry exclusion, generation claim atomicity and the exact fault seams
still require review before the story can be called implementation-ready.

Audited SHA-256 values (unchanged at both inspected revisions):

```text
817fef4fc2f39f54b5184c14541d3f43c9afaf7a250ece349e926c3cb3d75b14  src/infra/render/window.clj
3994a14ddb3fd052e7e9e4f20c4995422905d01f89a1d43877f634a8482c4067  src/infra/dev/window/loop.clj
ef6cfba6eb04a942fd5b62ac27fc460485304d336016caf770e6a3f8be579e0c  src/infra/dev/window/lifecycle.clj
6cb178a94a8a05566b963d64ab76b102023332d5446fdd2d3772b62019653172  src/infra/render/shader.clj
585b32dadb8913da1963fb054833d664fae2d9a39337b6f416414fe389eae1c7  src/infra/render/asset.clj
0811b4977f41e2b25db17a696f265c423d51edf7f441ccbaf71a3af33abc30ee  src/infra/render/volume.clj
6fce67c9bf2a0c4061cb74d8493bc6fef5ee56fb6921b8252181045235551384  src/infra/render/mesh.clj
a87cf23a26c38424e6d66f9f315ade5ef0112df563c2df320c8d4bf35bf716f7  src/infra/render/scene/setup.clj
8b63d1a1797ae0be97ccdacdb76fb25097303604a77f8fe50a1020e6fa176cb0  src/infra/render/hud.clj
d8af828b8628f98b0c90c1d1477e11820f23c6f73a9e2189db8ceff6a2e7869c  src/infra/render/input.clj
```
