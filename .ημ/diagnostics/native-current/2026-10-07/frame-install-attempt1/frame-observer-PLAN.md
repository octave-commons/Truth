# One-frame native identity and full-scene observation

Prepared only; do not install before root review and authorization. This
observer targets the new PM2 truth-native-7896 game, not either lost world.
Two read-only health audits established PID 131581, boot ID
b04f7fb6-ea65-4b47-8866-73e24a6887f6, process start ticks 125503, world atom
identity 919497450, and native window 137768861339584 in truth-validation.
Ticks advanced from 1791 to 1913, both workers were alive, errors were nil,
and the production tick/projection callables were installed. Runtime.maxMemory
was 2,147,483,648 bytes in both reads. The published world was the nonfixture
natural nebula route. This is a fresh world, not recovery.

The installer rechecks boot, PID, process start, cwd, DISPLAY :0, port 7896,
world/window identities, live workers, nil errors and nonfixture nebula route.
The new user/truth-native-current-frame-observer name must be absent and the
new frame-capture directory must not already exist. Any failed guard aborts;
do not restart or reinterpret a timeout as permission to install twice.

Wrap only the actual infra.render/render-scene facade invoked by the existing
native loop. Call the original exactly once, with unchanged arguments and
return value. After it returns, capture the complete scene/HUD before the
ordinary swap. The callback asserts the owning render thread, unchanged world
atom and current native context. It records actual GL vendor/renderer/version,
actual framebuffer dimensions, scene camera and shape count, and a separately
labeled later published-world readback. The later readback is not asserted to
be the scene's exact projection input; this observer makes no per-body causal
or trail-attribution claim.

Only one capture attempt, with a 30-second wall deadline. A named daemon
watchdog requests restoration even if no normal frame arrives. Both capture
and restoration share a guard, so a stalled GL readback or hung JVM cannot be
forcibly preempted. The original renderer runs outside the guard; a stalled
original call does not itself prevent a watchdog restoration request. Record
actual elapsed time and any missing capture. Root performs explicit fallback
restore/audit after completion or deadline.

Read the native default GL_BACK buffer after full scene/HUD. Refuse foreign
read FBO, pixel-pack buffer or nondefault pack layout. Save the prior read
buffer and restore it in finally, then verify it. GL error reads are bounded
and retained; they consume error flags. Pixel readback, PNG writing and record
serialization have host cost, so this is not native FPS/performance evidence.
No additional context or existing offscreen screenshot helper is used.

Restore the exact original scene Var immediately after the single capture or
any diagnostic error. Diagnostic errors are recorded and do not enter the
normal UI error handler; original renderer exceptions retain their throwable.
The explicit restore script verifies both current root identity and recorded
restoration. No bodies-fn, config, camera, selection, input, world, tick,
service lifecycle or production source is changed. No desktop input/unlock
or other-window capture occurs. A resulting image proves only the actually
recorded rendering; it does not prove manual flight, binding, sculpt or Gate.

Root owns launch records, growing runtime logs, receipts and closure. These
observer scripts and health-audit files are separate agent-owned additions.
Before installation, verify frozen hashes and the current process identity.
