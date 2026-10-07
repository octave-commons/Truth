# Diagnostic native follow-camera trail visibility

Status: prepared for independent review and root authorization; not executed.
Owner: existing body-trails-ringbuffer InProgress 5-point native-render acceptance.

The closed passive observation established exact tagged production trail geometry,
real-time alpha aging, and expiry, but its fit-all paths were too small to read
visually. This bounded pass frames the same natural star 258 and planet 1001.
It does not establish manual flight, overlap, binding, commitment, or Gate use.

## Identity and boundary

The installer refuses any process except PID 4070721, DISPLAY :0, port 7896,
world atom identity 275227937, GLFW window 129843170276688, both live workers,
and a service without visible errors. The operator also checks executable, cwd,
and /proc start ticks 3869117 before loading. This is the existing naturally
evolving seed-42 world, launched at be832d7 with 1000 gas entities; its
production source is byte-equivalent to c79be1e.

It reuses the proven facade boundary: call the original bodies-fn once and
return its unchanged result; retain that exact immutable projection input for
one scene only. Clear any old token before the next original projection and
consume the token once after the original full scene/HUD draw. A scene without
a fresh token is skipped. Capture only GL_BACK on the owning render thread,
after the production scene/HUD and before the ordinary buffer swap. Compare
only :trail/entity-tagged scene vertices with the unchanged production
project-trails result for that same input and scale. Later published world
reads are separate evidence, never substituted for the projection input.

## Framing and cadence

At the first paired render callback, read real current target position and
stored motion-trail samples. Compute R as the maximum physical distance from
the current body head to those stored positions, then divide by the production
context scale. Use existing camera/distance-for-radius(R, 60 degrees, 1.6),
floored by existing camera/min-approach-distance on the true rendered body
radius. No body scaling, history editing, or physics change occurs.

The earlier passive frame 03, tick 25195, gives provisional planning values:

| Target | History radius (m) | History radius (render units) | Fit distance (render units) |
| --- | ---: | ---: | ---: |
| Star 258 | 1.3424084709335117e14 | 0.13424084709335118 | 0.37201914818683063 |
| Planet 1001 | 3.118860414171547e14 | 0.3118860414171547 | 0.864323951849669 |

The prior fit-all distance was 66.03956291794024 render units. The live pass
recomputes every value when each target stage begins; these are not frozen
placement values. Existing menu/apply-action [:ui/select-entity eid] selects
and follows the target. Only camera distance is then set through existing
camera/update-camera-position, retaining yaw/pitch and the ordinary target
lerp. The target is not hard-snapped to camera center. Normal selection may
show the normal inspector; neither HUD nor field geometry is hidden.

Require at least three subsequent paired scenes and the target head within
the central 40 percent of the framebuffer before capture. Abort that pass if
ordinary follow does not settle within ten seconds, target disappears or
changes star/planet state, or diagnostic selection/mode changes. Capture at
most three images per target: first settled scene, half of the existing
6.3e11-second trail horizon later, and one full horizon later. Finish star
then planet. Each frame retains exact input ticks/times/dt, camera, tagged
vertices and screen projection, production budget accounting, natural history,
GL identity/error flags, and distinct later published tick/time.

## Restoration and bounds

At most six capture attempts; a 90-second total wall deadline. The scene check
and a named daemon watchdog both request restoration at the deadline. The
watchdog makes no GL call and does not tick or replace the world. It acquires
the same observer guard as capture, so a running GL readback or hung JVM
cannot be forcibly preempted. The original renderer executes outside that
guard: a stalled original call does not itself prevent the watchdog from
requesting host restoration, although that draw cannot be preempted. Report actual elapsed
time and any incomplete horizon rather than claiming a hard real-time bound.
The operator performs explicit restore/audit after the deadline if needed.

Save the whole original camera value and exact presence/value of :mode,
:selection, :follow-eid and :zoom-min just before the first intervention on the
render owner thread. Restore only those fields, plus original bodies-fn
presence/value and render-scene root, preserving unrelated config keys. A
failure before any view intervention records successful no-op restoration.
Original renderer exceptions retain their throwable; diagnostic exceptions
stop the observer without entering the normal renderer's error handler.
Original bodies-fn exceptions propagate through the unchanged host guard;
the watchdog still requests cleanup if no later scene runs.

A successful normal completion restores at the render boundary. A watchdog
or operator restoration can overlap a frame that already read follow config;
record exact values at the restoration boundary separately from later ordinary
camera/config evolution. Do not assert the camera remains frozen after the
normal game continues. Ordinary follow changes attention, and resulting world
evolution cannot be undone. This intervention is diagnostic, not manual
input or proof of an unchanged world.

No desktop input/unlock, other-window capture, extra GL context, forced
formation, time/history/body injection, or production source reload. GL error
queries consume flags; native readback/PNG writing costs host time. No FPS or
isolated performance claim follows from these captures. Preserve incomplete
fading coverage and any unreadable image as an open acceptance result.
