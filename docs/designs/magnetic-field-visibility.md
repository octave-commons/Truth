# Magnetic-field visibility in View

**Status:** proposed for planning review; no implementation or Ready claim.
**Child:** [3a501e16-f8fe-41a3-bdd1-760e503dd84d](../../kanban/tasks/view-magnetic-visualization-toggle-for-readable-body-trails-503dd84d.md), 3 points, parent
`body-trails-ringbuffer`.

## Why this bounded amendment

The [native pixel and source evidence](../notes/2026-10-06-magnetic-visibility-evidence.md)
shows schematic magnetic loops obscuring naturally formed stars, planets and
their motion trails. [UX architecture](ux-architecture.md) gives View ownership
of overlays and says the viewport is for awe. [Flight design §6.1](spark-flight-and-camera.md)
requires readable fading paths. This amendment proposes a visibility choice;
it does not revise field physics, body scale, trail geometry or flight.

## Proposed interaction and defaults

- Add one **Magnetic fields: Off / On** control to the existing View drawer.
  **Off is the fresh-window default**, including absent legacy configuration.
  The default is an explicit proposal: these schematic glyphs are useful
  inspection detail but currently overwhelm ordinary motion viewing.
- The ordinary menu hit/action path changes only a boolean in window UI
  configuration. The rendered label always reflects that configuration. No
  keyboard shortcut, sim intent, coherence/resource cost or world write is added.
- The choice survives drawer close/reopen, camera-mode changes, selection
  changes and R camera reset within the same window. A fresh window starts Off;
  no disk preference/save-game persistence is introduced.
- The control stays reachable with a drawer open at 1280×720, 960×540 and
  1600×900. Reuse the current row/hit geometry and font conventions; preserve
  existing View actions and labels. Closing the drawer works as it does today.

## Exact visual coverage and invariants

Off excludes **both** large dipole loops from `field-line-shapes` and short
star/protostar B-field vectors from `field-line`. On reproduces those existing
magnetic shapes and their current size/color policy. Do not hide all line
shapes: trails, focus/selection/hover rings and intervention overlays remain.
Do not infer magnetic membership from color, size, order or line mode; name it
explicitly where magnetic geometry is produced.

Use the existing Phase 0 projection and renderer. Any projection/cache choice
must include visibility so Off → On → Off on an unchanged captured world cannot
reuse the wrong geometry. Reuse nonmagnetic projection; avoid rebuilding field
loops while Off. Existing field-source bounds remain unchanged when On.

Changing visibility leaves the supplied ECS world, physical magnetic components,
forces, field effects, histories, timestamps, trail alpha/budget, body positions
and radii, camera state, selection/follow target and simulation clock unchanged.
Normal simulation continues during native observation, so equality assertions
belong to deterministic tests on the same world snapshot, not successive live
ticks. No shader, GL-state, new renderer, physics or trail-writer change is in
scope.

## Ownership and ordering

This is a child that helps close the existing parent's visual acceptance, not a
replacement trail implementation. Source base
`3064336a482bd7e93875808f6ad1200164347c68` already contains the trail projection
and native line-width repair. Do **not** add a dependency on the parent's Done
state: the parent's remaining acceptance uses this child, which would form a
completion cycle. Planning approval and normal Rheos readiness precede code.

The existing `flight-hud-and-cues` ownership remains intact. Its separate
proposed children `manual-hud-overlay-spacing` and
`manual-hud-quiet-telemetry` retain layout/wrapping and quiet telemetry; see the
[pinned HUD proposal](https://github.com/octave-commons/Truth/blob/9f2d7722d2166c2a0f1af3bd16b3558446f5c01f/docs/designs/manual-flight-hud.md).
Those are cross-branch proposals, not prerequisites for this control. Inspector
dismissal/placement, debug camera restoration, heading cues, scene art direction
and global HUD hiding are excluded. Existing top-bar/status visibility remains.

## Acceptance and verification

1. RED before production: exercise the actual View row/hit and pure menu action.
   Missing/false configuration shows Off; a real action enables On; the next
   action returns Off. Assert only the owned UI setting changes, including
   preservation across drawer, camera reset/mode and selection operations.
2. RED through the production projection on one unchanged world containing a
   star, protostar, planet, magnetic sources and actual trail samples: Off omits
   both magnetic shape families; On matches their existing geometry; all
   nonmagnetic shapes and ECS input remain equal. Repeated Off/On transitions
   on that same world exercise caching. Absent/zero magnetic data stays valid.
3. Focused menu/render/trail tests, architecture checks, full repository tests
   and strict analysis pass. Coordinate the required before/after benchmark
   because projection is a hot path; make no cost claim from native capture.
4. In an ordinary naturally forming world, use only real View clicks and
   ordinary camera controls to capture default Off → On → Off, with the same
   target/view between toggles. Record source, actual setting, world tick,
   history/alpha/budget and raw GL error evidence. No fixture, forced birth,
   REPL camera/config injection or substituted projection counts as player proof.
5. Close the drawer and record star and planet trails visibly fading over their
   actual simulation-time history, with no magnetic glyphs competing. Resize
   1280×720 → 960×540 → 1600×900 → 1280×720 and verify the control/label remain
   usable. Camera-follow inspection is labeled separately from manual flight.
   If HUD/inspector occlusion still prevents readable fading, preserve the
   failure and existing owner links; do not claim the parent's acceptance done.

The three-point estimate covers one boolean menu control and two explicitly
owned magnetic shape families in the existing projection, plus their tests.
It excludes UI re-layout, physically scaled magnetospheres and camera changes.
