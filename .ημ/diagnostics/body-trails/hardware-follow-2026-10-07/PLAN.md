# Fresh natural-world trail visibility: offline preparation

Status: one authorized read-only target snapshot completed; final installer
prepared for independent/root review and not installed. The preserved
`install.draft.clj` still rejects its pending target value and must not be used. The previous 2026-10-06 follow bundle is immutable and was
never installed; its old world was lost at host reboot.

Owner: canonical `body-trails-ringbuffer`, In Progress, estimate 5, accepted
`docs/designs/spark-flight-and-camera.md` §6.1. The remaining native acceptance
requires visible, readable fading paths on real star/planet bodies. Diagnostic
follow is not manual flight, overlap, binding, commitment or Gate evidence.

## Provenance and release boundary

This is the fresh PM2 game launched from production revision
`9c5c889d8923c76bf9a9530a8bd18cd72f37a93f`, with new natural seed-42 genesis and
1000 initial gas bodies. Evidence HEAD at assignment is `90d6c2e`.
Expected identity is PID 131581, start ticks 125503, host boot
`b04f7fb6-ea65-4b47-8866-73e24a6887f6`, DISPLAY :0, port 7896,
world atom 919497450, native window 137768861339584, and cwd `truth-validation`.
These are guards to reverify after release, not a claim the paused service is
currently advancing. Root owns all process signals and the benchmark window.

The bounded RandomAccessFile reader is copied from the successfully executed
fresh one-frame observer: one 4096-byte read, explicit UTF-8, require a nonempty
short read and final newline, close on every path. Boot/start assertions remain.
The installer also requires both live workers, nil service/UI errors, and the
previous one-frame observer inactive with its exact renderer callable restored.

## Observed read-only target selection

Root resumed the guarded process at 01:09:56.920178 UTC. The single authorized
`target-selection.clj` call completed at 01:10:59.032347 UTC, exit 0, empty stderr,
client PID326774 reaped. Its unchanged raw response is `target-selection.stdout`
(SHA256 `67e7434756f596433e8860fbe0effd0bcf3d2e9d7f7ccd8c9dede44ac3b8beda`).
It read one immutable
published world plus the current camera/config, without changing any of them.
The response contains 26 actual production star/planet candidates, all returned
with none omitted. At tick17265, sim time9.555358640260339e13s, dt3.065696697424829e9s,
the selected lowest-ID framing-ready body of each kind is star258 and planet1001.
Both have63 production-valid observed samples. Their equality to older world
IDs is a fresh observation, not evidence of world recovery or carried selection.
`selected-targets.edn` records the explicit rule and derived framing. No body,
history, clock, camera or world mutation occurred during selection.

| Selected target | History radius (m) | Radius (render units) | Fit distance (render units) |
| --- | ---: | ---: | ---: |
| Star258 | 2.0888203483173847e14 | 0.20888203483173848 | 0.5788708753871089 |
| Planet1001 | 3.167475387400184e14 | 0.3167475387400184 | 0.8777965284321649 |

The observed prior mode is fit-all with no selection/follow-eid/zoom-min keys;
its separately read camera distance is69.07408855938783. Preserve actual view
values at the later intervention boundary, not this potentially stale snapshot.
The planning dimensions are the prior actual 2536×1490 framebuffer, labelled as
historical. The installer recomputes framing from its actual paired scene. For
each chosen target, R is the maximum physical distance of stored sample position
from the real current head, divided by the production context scale. Use existing
`camera/distance-for-radius(R, 60, 1.6)`, floored by existing
`camera/min-approach-distance` on the true rendered body radius. No geometry,
history, shader, body size, sim clock or physics changes occur.

## Capture and restoration contract

Retain the existing reviewed follow procedure: original bodies callable once,
unchanged return, fresh projection-input world token cleared before projection
and consumed once per scene. Original full scene/HUD renderer once, unchanged
arguments/results. Read GL_BACK only afterward on the actual owning render
thread/current context and before the normal buffer swap. Compare actual tagged
trail vertices to production `project-trails` for that same immutable input and
scale; report requested/rendered/dropped budget counts. A later published read is
separate and must never substitute for that input.

Existing menu selection enters ordinary follow, followed by the existing camera
distance update. Do not snap the target. Wait at least three paired scenes and
for the target head to enter the central 40% of the framebuffer; fail a stage if
it has not settled within ten seconds. Capture at most three frames for the star,
then three for the planet, at first settled observation and one-half/full of the
existing 6.3e11-s history horizon afterward. Preserve incomplete coverage if the
sim clock or camera does not reach those points within the total budget.

At most six attempts and a 90-second wall deadline. A named daemon watchdog and
scene check both request cleanup. Neither can preempt a hung JVM or GL readback
holding the observer guard. The original renderer executes outside that guard:
the watchdog can request host restoration while that renderer is stalled, but
cannot interrupt it. Report actual elapsed time and remaining in-flight work.

Save the complete original camera plus exact presence/value of `:mode`,
`:selection`, `:follow-eid`, `:zoom-min` at the first render-owner intervention.
Restore those fields only, preserving unrelated config, plus exact original
`:bodies-fn` presence/callable and scene Var root. Failure before view changes
records no-op restoration. Normal completion restores at the render boundary;
off-thread watchdog/operator cleanup may overlap a frame already using old
follow config. Record restoration-boundary values separately from later normal
camera evolution. Explicit restore and same-world/window/worker/error audit
follow any execution.

Original renderer errors propagate unchanged. Diagnostic readback/errors stop
the observer without entering the production UI-error path. Original bodies
errors still use the existing host guard; watchdog cleanup remains required if
no later scene arrives. GL error reads consume flags and image readback costs
host time. No native-FPS/performance claim follows.

Ordinary follow changes attention; resulting world evolution cannot be undone.
No desktop input/unlock, other-window capture, extra GL context, source reload,
render filtering/recoloring, world/history injection or lifecycle changes.
Visible readability must be assessed from the actual new pixels and may remain
unproven despite correct tagged geometry or alpha aging.
