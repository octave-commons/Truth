# Three actual native frames; preparation only

Root will authorize the fresh visible game after the current cost window. These
forms are not installed and no new game/context has been launched for this bundle.
Use the unchanged natural launch on display:0/loopback7896 with seed42/1000gas,
bounded2GiB heap. Its ordinary visible focus/cursor behavior is accepted by root;
no synthetic desktop input, unlock, old-world migration or service7895 call occurs.

The observer is restricted to the new process: DISPLAY:0, TRUTH_DEMO_PORT7896,
PID other than3083624, natural nebula fixturefalse, live workers and clean service.
It refuses reinstallation or an existing captures directory. Root must separately
record actual launch HEAD, command, source hashes/PID before installation.

Install frame-observer-install.clj through the NEW nREPL7896 only after startup.
The hook is the actual infra.render/render-scene facade used by window-loop.
It calls production exactly once with identical arguments and returns that result;
original errors propagate unchanged after diagnostic restoration. A successful
full scene includes the current HUD. Capture occurs on the same owner thread
immediately afterward, before window-loop swaps its buffers.

At most3attempts are made: first available scene, then5seconds after each prior
capture. A120second deadline is checked only when another frame arrives; root
must use frame-observer-restore.clj after that bound if frames stop. There is no
background timer or worker. Three completed frames or any error restores the
original Var automatically. Manual restore serializes against a capture already
in flight and is idempotent after automatic completion. A competing Var owner is
reported and never overwritten.

The hook asserts the expected current native context/default read framebuffer,
no PBO and ordinary pixel-pack layout. It changes only GL_READ_BUFFER to GL_BACK
and restores it in finally, reusing the existing read-pixels and vertical-flip
helpers. No world, tick, camera, config, queue, input, lifecycle, shader, context,
or framebuffer binding is changed. Error samples via glGetError DO consume flags;
the raw before/readback/restoration lists are bounded and retained, and cannot be
restored. Any nonzero/truncated list stops the probe without poisoning the game's
:ui/error-state. Probe failure does not substitute an image or retry unboundedly.

Each PNG filename includes runtime PID, GLFW window handle and capture index.
Its paired EDN records actual context vendor/renderer/version, capture boundary,
scene dimensions/time/camera, and world atom identity. The independently published
world read after the scene is explicitly labelled; its tick is not claimed as the
exact input to the rendered scene. observer-state.edn preserves attempts, errors,
raw GL samples and restoration outcome. A failed writer can leave a partial file;
root must retain/label that failure rather than counting it as a completed frame.

After collection, read the restore form's result and verify3captures/noerrors,
original Var restored, actual hardware identity, PNG dimensions and visually
inspect each image. Close/checksum actual outputs only then. A locked desktop
may make external-window capture unavailable; these own-back-buffer images do not
prove the user saw an unobscured desktop window. They supply no FPS/throughput or
manual-flight/commitment/Gate claim. The diagnostic readback/PNG work adds host
cost, so record it rather than treating these frames as performance measurements.
