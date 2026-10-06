# Magnetic visualization obscures natural-body motion

Observed and derived grounding, 2026-10-06. Source audited at
`3064336a482bd7e93875808f6ad1200164347c68`; native evidence is separately pinned
at `607c4361032816ecb0fafd57f8c77946eea7d898`. This note does not claim a fix.

## Observed pixels and controls

The closed ordinary-nebula run's [star close-up](https://github.com/octave-commons/Truth/blob/607c4361032816ecb0fafd57f8c77946eea7d898/.%CE%B7%CE%BC/diagnostics/focus-input/native-400ba3a/star-close-trails.png)
and [planet follow view](https://github.com/octave-commons/Truth/blob/607c4361032816ecb0fafd57f8c77946eea7d898/.%CE%B7%CE%BC/diagnostics/focus-input/native-400ba3a/planet-follow-trails.png)
show cyan loops spanning most of the 1280×720 viewport. Permanent telemetry and
the selected-body inspector also compete for space. The loops are magnetic
glyphs, not evidence of orbital paths. The run's [README](https://github.com/octave-commons/Truth/blob/607c4361032816ecb0fafd57f8c77946eea7d898/.%CE%B7%CE%BC/diagnostics/focus-input/native-400ba3a/README.md),
[manifest](https://github.com/octave-commons/Truth/blob/607c4361032816ecb0fafd57f8c77946eea7d898/.%CE%B7%CE%BC/diagnostics/focus-input/native-400ba3a/manifest.json)
and [checksums](https://github.com/octave-commons/Truth/blob/607c4361032816ecb0fafd57f8c77946eea7d898/.%CE%B7%CE%BC/diagnostics/focus-input/native-400ba3a/SHA256SUMS)
retain source transitions, actual natural histories, raw GL faults and subsequent
repair observations, camera inspection versus manual movement, and lifecycle
discontinuity. Media is referenced, not copied into this planning branch.

Current source has no ordinary magnetic-visibility or general HUD toggle:

- [View settings](../../src/infra/menu/widgets.clj) `view-rows` exposes look and
  zoom sensitivity; [the View panel](../../src/infra/menu/panels.clj)
  `view-panel-ctx` adds camera mode.
- `apply-action` closes an already-active drawer tab. Empty-viewport picking
  clears selection and the inspector but also releases follow into manual mode.
  There is no inspector-only dismissal preserving selection follow.
- [The window loop](../../src/infra/dev/window/loop.clj) always composes the
  selected inspector, stats, observer and controls HUD. [Input](../../src/infra/render/input.clj)
  makes Tab a cursor-lock toggle and Escape a window-close request, not drawer
  dismissal. Existing public camera/zoom controls cannot switch magnetic glyphs
  off. No native input was sent during this audit.

## Source explanation and limits

[particles.clj](../../src/infra/render/scene/particles.clj), `field-line-shapes`
(lines 113–144 at the pinned source), selects up to twelve positive magnetic
moment sources. Each gets three shells × six azimuths × 22 segments. Its base
radius is `max(0.6, physical-radius / render-scale)`; the largest shell reaches
approximately 2.04 render units at this floor. Color is fixed cyan. This is a
schematic size floor, not a measured physical magnetosphere extent.

[bodies.clj](../../src/infra/render/scene/bodies.clj), `phase0-bodies+fields`
(lines 273–282), unconditionally adds these loops to ordinary Phase 0 bodies.
The ordinary [demo service](../../dev/demo.clj) selects that projection. The base
body projection also emits short `particles/field-line` vectors for stars and
protostars (lines 194–234); hiding only the appended loops would leave part of
the magnetic visualization behind.

[units.clj](../../src/infra/render/units.clj), `phys->body-render-radius`, maps
body radii at true scale. The existing sprite LOD keeps subpixel bodies visible
(four-pixel threshold, minimum ordinary sprite diameter 2.5 pixels). At the
captured star's approximately 1.56-render-unit camera distance, the magnetic
floor can occupy hundreds of pixels; closer zoom increases that obstruction.
This is a geometric inference explaining the pixels, not a physical field law.

[trails.clj](../../src/infra/render/scene/trails.clj) projects actual retained
positions with fading opacity. Stars are gold; planets are pale blue, close to
the magnetic cyan. Removing magnetic glyphs may improve distinguishability;
only a new ordinary-control capture can establish readable star/planet fading.
No general GPU/performance benefit or gameplay completion follows from this
note. Inspector/HUD collisions remain separate observations and owners.

## Authority boundary

[UX architecture](../designs/ux-architecture.md) places overlays in View and
makes viewport readability the governing principle. It does not yet specify a
magnetic toggle or its default. [Flight design §6.1](../designs/spark-flight-and-camera.md)
and canonical card `body-trails-ringbuffer` require readable fading paths.
The [proposed visibility amendment](../designs/magnetic-field-visibility.md)
turns this local pixel/source finding into one reviewable rendering slice.
It adds no astrophysical claim requiring external research.
