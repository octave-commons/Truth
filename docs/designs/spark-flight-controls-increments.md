# Spark flight controls: bounded implementation increments

Date: 2026-10-06. Status: proposed amendment, ready for design review; no
implementation readiness or visual acceptance is claimed. GPL-3.0-or-later.
Base inspected: `a3609f6`.

This refines [Spark Flight & Camera](spark-flight-and-camera.md) §§3, 5–7
without replacing its approved direction. The immediate outcome is visible,
controllable spark heading, then body-frame translation and a chase view.
The north star remains flying to a formed planet, resolving voxels, and acting
on it; these cards do not implement the later embodied character or Gate.

## Evidence and boundaries

- [Rotation research](../research/physics/spark-rotation-integration.md) grounds
  scalar-first Hamilton, body-to-world orientation, world-axis angular velocity,
  exponential free-spin integration, and the semi-implicit torque step. Its
  cited Solà/AHRS sources do not establish a human input-response law.
- [Existing linear control](../../src/domain/player/flight.clj) and
  [its dilation regression](../../test/domain/spark_body_test.clj) derive
  acceleration from displacement per simulation tick. The workspace
  `dt-dilated-player-constants` skill informed this amendment; its source was
  `/home/err/spaces/foresight/.agents/skills/dt-dilated-player-constants/SKILL.md`.
  The derivation below is included here so the design does not depend on that
  external workspace path remaining available.
- [Rotation integrator](../../src/domain/integrator/rotation.clj) consumes the
  previous snapshot's torque. [Tick orchestration](../../src/domain/genesis/tick.clj)
  updates adaptive pacing after physics. Neither clock nor the barrier is
  changed by this plan.
- [Current input](../../src/infra/render/input.clj) rotates the camera;
  [the frame loop](../../src/infra/dev/window/loop.clj) derives thrust from it.
  [The observer overlay](../../src/infra/render/scene/hud.clj) has a particle and
  a line reticle but no heading cue. These are inspected code findings, not
  proof of a completed control experience.

## Proposed body frame and torque law

Use body +X = right, +Y = forward, +Z = up, consistent with the default camera
looking approximately along +Y in the z-up world. The existing quaternion maps
these vectors into world axes. Positive pilot pitch-up rotates about +X,
roll-right about +Y, and yaw-right about −Z. Thus a pilot-axis command
`[pitch-up roll-right yaw-right]` maps to torque axes `[pitch roll (- yaw)]`
before quaternion rotation. Pin identity and nonidentity sign cases in tests.
The conventions are proposed project choices, not claims about the sources.

Let `h > 0` be the emitter snapshot's `:sim/dt` in simulation seconds, `I` the
existing inertia, `u` a bounded world-axis command (norm ≤1), `Θ` the target
heading angle per simulation tick, and `r` the response retention. Derive from
`ω' = ω + τ h/I` and heading increment `ω' h`, below the physical speed clamp:

```
τ_input = I Θ (1-r) u / h²
τ_FA    = -I (1-r) ω / h
```

These are separate registered torque channels, with values in N·m. The torque
child owns input only; existing `flight-assist-damping-and-toggle` owns damping
and the FA boolean. No emitter writes orientation/angular velocity directly.
`Θ` and `r` are named law/tuning values to establish with tests and live evidence,
not new claims of physical inertia or an empirically established flight feel.
At `h=0` emit zero; reject invalid dt/commands and non-finite derived values.
Never substitute wall dt, clamp fractional simulation dt to 1, or invent dt.

At fixed `h` and a fixed command axis, assisted terminal heading per tick tends
to `Θu`. With the actual one-tick lag, the unclamped recurrence is instead
`ω[n+1] = ω[n] + (1-r)(Θu[n-1]/h - ω[n-1])`; it is not exact one-step retention.
The homogeneous roots solve `λ²-λ+(1-r)=0`. Choosing `0.75≤r<1` gives real,
nonnegative roots below 1; this does not promise every transient is monotone.
This algebra is derived from repository behavior, not additional primary research.

FA off removes only damping. Continued input accelerates spin; released input
leaves physical `ω` conserved once the pending torque has drained, subject to
the existing safety clamp. Changing dt during coast changes angle per tick;
it must never silently rescale stored `ω`. There is no assisted terminal-angle
promise with FA off, changing axes, or an active physical clamp.

Adaptive dt needs explicit transient evidence: a torque sized at `h_emit` and
consumed at `h_consume` produces an input-angle contribution proportional to
`(h_consume/h_emit)²`; damping's fractional kick scales with that ratio once.
A fixed-dt sweep cannot prove adaptive stability. Before implementation admission,
review the response bounds and representative pacing transitions; before review
admission, test them through the actual snapshot fold. If they are unstable,
return the control law to Breakdown. Do not fix it by rescaling coast momentum,
special-casing the integrator, changing the global clock, or adding a new barrier.

The current [pacing implementation](../../src/domain/pacing.clj) bounds ordinary
adaptive dt to `1e7..5e10` seconds, permits a time-slip multiplier up to 5, and
caps slipping dt at `1e12`. Include slip entry/exit and the first transition from
an explicitly supplied bootstrap dt in the transient envelope. Fractional dt
remains valid at the integrator boundary; normal pacing bounds do not justify
rounding it up. Sweep both ordinary/slipping scales and fractional test steps,
and record the actual adjacent-step ratios used rather than only range endpoints.

Linear thrust uses the same body frame and the already established displacement
derivation. Preserve the 1.5–2 forward/lateral tuning range as a project choice;
the missing original research does not verify it as an Elite measurement.
Existing FA work must remove embedded linear damping exactly when its separate
channel replaces it; no duplicate braking and no FA-off claim while it remains.

## Camera research prerequisite

The original scratchpad is unavailable, as recorded in the rotation research.
The parent camera card refers to a closed-form update formula in §5.1, but that
section supplies no formula or source. Before the camera children are Ready:

1. Add a tracked note with a verified primary derivation/implementation source
   for a critically damped spring's position/velocity update, assumptions about
   moving targets, and fixed-target elapsed-time partition tests. A spring can
   overshoot with arbitrary initial velocity; do not promise universal absence
   of overshoot merely from ζ=1.
2. Specify finite behavior at zero velocity, opposite heading/velocity and view
   poles, plus how the rest frame and partial bank are computed without Euler
   discontinuities. Record the default constants as tuning choices.
3. Define one view basis for [scene matrices](../../src/infra/render/scene/setup.clj),
   [projection/picking](../../src/infra/render/units.clj), and
   [volume rays](../../src/infra/render/volume.clj). All three currently calculate
   fixed-z-up frames separately. World coordinates stay z-up; partial bank may
   rotate camera up. Reconcile the old literal fixed-up sentence accordingly.

Camera smoothing uses explicit render wall dt and render units; it never feeds
back into physics. The position/aim child removes the additional manual tether
step. Partial bank and MMB idle-return are separate children after that rig.
The camera work is not required to display a quaternion-derived facing cue.

## Scope allocation and readiness

All seven child cards are initial Incoming planning artifacts. Parent statuses
are unchanged. The first two split the oversized force parent; the next three
split the oversized camera parent. Input and facing are independent early slices
of already-sized parents, justified by immediate control/visibility and distinct
dependencies. Their remaining acceptance stays with their original parents.
Fresh child estimates need not sum to the old estimates.

| Child UUID | Points | Parent UUID | Readiness condition |
| --- | ---: | --- | --- |
| `spark-body-frame-torque-commands` | 5 | `spark-flight-force-channels` | Review proposed control law and adaptive-dt test envelope |
| `spark-body-frame-translation` | 3 | `spark-flight-force-channels` | Torque child establishes common axes; coordinate FA extraction |
| `spark-chase-position-aim-springs` | 5 | `chase-camera-spring-rebuild` | Camera research/target-frame contract missing |
| `spark-camera-shared-basis-bank` | 3 | `chase-camera-spring-rebuild` | Shared-basis/bank research and positional rig |
| `spark-camera-mmb-orbit-return` | 2 | `chase-camera-spring-rebuild` | Positional rig, rotational gesture routing, spring grounding |
| `spark-rotational-input-intents` | 3 | `spark-6dof-input-mapping` | Torque, facing cue and existing FA domain scope |
| `spark-facing-cue` | 2 | `flight-hud-and-cues` | Common axes/transform from torque child; review placement; no spring research needed |

Mouse aims the mote; Q/E roll; MMB controls camera orbit; F remains the later FA
binding, Shift boost, arrows/comma/period focus, C cycle, R reset. The old input
card's acceptance and FA card's R wording contradict corrected §7. Those parent
findings require a canonical Rheos comment/refinement before they are claimed
complete; this amendment does not hand-edit their state or settled bodies.
The force parent's deletion of `drift` is also stale: that teleport is gone.

The rotational input child owns arbitration and routes MMB to the existing camera
orbit; its camera child later adds anchor/idle return. Full settings/rebinding UI,
translation/boost/F wiring stay with the input parent. Facing is permanent pilot
instrumentation via the existing line path; the mote shader, drift cue, coherence
zones and full HUD remain their existing scopes. `debug-view-state-restore`
retains mode-state preservation, including the eventual spring state.

Each feature still needs Rheos admission, red tests before code, full tests and
strict analysis, measured hot-path effects where applicable, and real native
window evidence. A scripted or fixture pose proves placement only; real input
and ECS state must establish controllability. None of these cards completes Gate
gameplay or proves natural planetary survival.
