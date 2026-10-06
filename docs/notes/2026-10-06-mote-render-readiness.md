# Mote render readiness: current pipeline and remaining decisions

Date: 2026-10-06. Status: observed source evidence and proposed readiness repair;
no implementation admission. Source: `b402939931575e377ae4409d9cd4061dff11df06`.
Owner: existing `mote-of-light-shader`, canonical TODO, estimate 5. GPL-3.0-or-later.

The accepted direction remains [Spark Flight & Camera §6.2](../designs/spark-flight-and-camera.md#62-mote-of-light):
bright core, soft halo, an orientation-derived heading flare and coherence-driven
brightness, distinct from real stars. This note repairs stale integration facts;
it does not recreate the missing flight scratchpad or establish new flight laws.

## Observed renderer and orientation support

- [The observer overlay](../../src/infra/render/scene/hud.clj), lines 29–46,
  already anchors the Spark at its ECS `c/position`, converted by
  `units/world->render`. Its footprint is a camera-facing `:particle` point:
  fixed pale-cyan RGB `[0.85 0.96 1.0]`, nominal size `28 + 44 * focus-intensity`.
  The [particle shader](../../src/infra/render/shader.clj), lines 233–288,
  projects that position and adjusts/clamps pixel size by camera distance.
  It has no orientation input. “No world presence” is therefore inaccurate.
- [Scene setup](../../src/infra/render/scene/setup.clj) enables depth testing,
  subtracts the render origin from shape positions, then draws particles before
  solid bodies. The particle pass disables depth writes and restores them.
  This is not “no depth”; neither is it evidence that a new world-space halo
  will composite correctly. Verify the actual replacement pass and occlusion.
- Coherence currently colors the focus ring and HUD. The Spark point above has
  fixed RGB; [particle packing](../../src/infra/render/mesh.clj), lines 116–127,
  defaults its absent density to 1. The old `scene/bodies.clj:152` opacity cite
  now addresses [player-focus-level](../../src/infra/render/scene/bodies.clj),
  whose caller binds unused `_focus`. There is no existing Spark-opacity
  coupling to reuse. The new brightness input must explicitly read coherence.
- The [trail colors](../../src/infra/render/scene/trails.clj), lines 22–26, are
  cyan for Spark and warm gold for star. These are line colors, not a gold/cyan
  mote material switch. Current body and sprite shaders have separate roles.
- The [shader registry](../../src/infra/render/shader.clj) and
  [shared render-scene](../../src/infra/render/scene/setup.clj) support named
  programs in one renderer. The live window and
  [offscreen PNG path](../../src/infra/render/window.clj), lines 140–157, both
  supply that renderer's program map. A new program must reach both paths and
  their resource lifecycle. Reuse the existing coordinate boundaries described
  by [coordinate research](../research/unit-coordinate-transforms-game-render.md);
  that historical note is not authority to restore superseded radius scaling.
  The inherited card's nREPL `w/reload-shaders!` recipe is not owner-thread safe:
  [window lifecycle](../../src/infra/dev/window/lifecycle.clj), lines 108–117,
  calls `shader/invalidate-all!` synchronously, which deletes GL programs on the
  caller thread. Future validation must refresh resources on the owning GL
  thread with its context current, or use a fresh isolated renderer. This
  limitation does not add a lifecycle rewrite to the mote scope.
- [Rotation research](../research/physics/spark-rotation-integration.md) and
  [the integrator](../../src/domain/integrator/rotation.clj) define scalar-first
  Hamilton, body-to-world orientation, with one writer of `c/orientation` and
  `c/angular-velocity`. [The physics table](../../src/domain/genesis/systems.clj)
  runs it with simulation dt. Canonical Card 1 is
  `spark-orientation-angular-momentum`, REVIEW, estimate 5; its substrate is
  integrated here, while final review remains open.
- [Spawn](../../src/domain/player/state.clj) supplies identity and zero angular
  velocity. [The influence registry](../../src/domain/integrator/base.clj) has
  no attitude torque contributors. [Mouse input](../../src/infra/render/input.clj)
  changes camera attitude; [the window loop](../../src/infra/dev/window/loop.clj)
  queues camera-relative translation. Ordinary controls do not yet rotate the
  Spark. Rendering must never become another writer to manufacture that motion.

## Readiness decisions still open

1. Review this grounding repair and record the scoped plan on the existing
   card before a legal Rheos In Progress claim; TODO5 alone is not admission.
2. Resolve the shared nose convention with the
   [pinned PR9 proposal](https://github.com/octave-commons/Truth/blob/9f2d7722d2166c2a0f1af3bd16b3558446f5c01f/docs/designs/spark-flight-controls-increments.md#proposed-body-frame-and-torque-law):
   **+Y forward, +X right, +Z up is proposed, pending review**, not an accepted
   independent renderer convention. That plan assigns the common transform to
   its torque child; do not duplicate or silently transfer that ownership.
3. Specify and test graphical extent, coherence-to-brightness response and depth
   composition within the existing visual scope. Preserve position/orientation
   ownership, focus ring, trails, camera and selection; no torque or input work.
4. Distinguish static-pose/native-offscreen shader verification from ordinary
   input turning. Pose tests can prove identity/nonidentity heading and roll,
   placement, coherence response, no-observer behavior, compilation, framebuffer
   pixels and GL errors. They do not prove controllable flight or satisfy its
   missing native input acceptance. Do not inject live-world attitude for a
   player-proof claim. Normal-input rotation stays with the existing flight work.

The same pinned proposal's `spark-facing-cue` child is separate permanent HUD
instrumentation and explicitly excludes the bespoke mote shader. No new feature
card, camera dependency, physics law or implementation is introduced here.
