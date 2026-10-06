# Quiet manual flight HUD and overlay spacing

Date: 2026-10-06. Status: proposed planning amendment; no visual acceptance yet.
GPL-3.0-or-later. Refines [UX architecture](ux-architecture.md)'s governing
principle, “The viewport is for awe. The shell explains the world,” and the
readability scope of [Spark Flight & Camera](spark-flight-and-camera.md).

## Grounding and scope

[Native pixel evidence](../notes/2026-10-06-manual-hud-pixel-evidence.md) records
the actual 1280×720 collision and manual-view diagnostic density, capture
provenance, hashes and responsible layout code. These are sufficient grounds
for this local UI judgment; no external usability evidence is claimed.

Use the existing `infra.render` text/rectangle path, `infra.menu` shell and
frame loop. Derive layout from framebuffer dimensions and measured rendered
text bounds; a string's starting coordinate is not its occupied region.
Pixel bounds are the common layout boundary; convert to NDC once for rectangles.
This needs a small deterministic layout helper, not a framework or new renderer.

## Two independently reviewable slices

1. **`manual-hud-overlay-spacing` (3 points):** coordinate the existing action
   palette, passive legend, observer readouts/bars, event and ambient text.
   Reserve the top bar, open menu `:regions`, current view badge, diagnostic
   block and inspector card bounds before placing these HUD elements. Keep
   text and its background together. Wrap/reflow within measured bounds; do
   not reduce font scale below the existing small-text scale 1.3. Low-priority
   ambient/help text may yield when a drawer or selection occupies its region;
   close it to restore the default HUD. Keep the full event text and wrap it in
   its owned HUD region; the current Narrator panel is unwrapped and cannot
   supply reliable overflow access. Keep action keys, costs, affordability,
   coherence/focus and binding/commitment values truthful. Do not reposition the
   selected body, change menu hit regions, or solve menu/inspector geometry here.
2. **`manual-hud-quiet-telemetry` (3 points), after spacing:** in `:manual`,
   replace the eleven permanent diagnostic lines with at most two compact lines
   for phase, elapsed time and rate, within a 36-pixel-high region below the
   top bar, scale ≤1.5. Keep the current speed/view readout, action palette and
   player resources. Put all existing mass/temperature/count/SED/LOD/disk/IMF/
   halo/tick diagnostics in the existing World details panel as readable short
   rows; do not paste oversized old strings into its 320-pixel column. Opening
   and closing World is the detail affordance. Other camera modes retain their
   current diagnostic content, subject to the spacing rules.

## Concrete visual and resize contract

Review the same read-only world snapshot at **1280×720, 960×540 and 1600×900**;
resize 1280×720 → 960×540 → 1600×900 → 1280×720 without restarting. These are
acceptance sizes, not a claim that arbitrary smaller windows are supported.

- Text/background bounds stay inside the framebuffer; owned HUD regions have
  at least 8 pixels between them and do not intersect reserved shell/card regions.
  Intentional text-inside-own-panel and full-screen mood tint are not collisions.
- The captured “A dense core condenses. +3 quanta” message and a long ambient
  line remain legible with the full action palette. Test the empty-observer case,
  an active binding/commitment readout, World drawer and a selected-body card.
  If a drawer consumes the available area, explicitly yield low-priority HUD
  regions rather than drawing over it. Do not silently clip action keys/costs.
- After the quiet slice, with manual mode, drawers closed and no selection,
  the central rectangle `[0.30w,0.30h]..[0.70w,0.70h]` contains no permanent
  text or opaque HUD panels. At 1280×720 this is `(384,216)`–`(896,504)`.
  World-space spark/focus/facing cues and transient ambient narrative retain
  their separate roles; this is an instrumentation budget, not world culling.
- Layout changes do not write ECS, camera, time-rate or action intent. Opening
  details does not pause the simulation or alter flight. Resize uses current
  framebuffer dimensions and preserves shell hit targets and information.

Red layout tests should assert measured-region containment/separation and
information availability, not reproduce hard-coded coordinate formulas.
Capture native before/after at all three sizes and manually inspect legibility;
geometry alone does not prove readability. Future implementation admission also
requires focused render/menu tests, full `clojure -M:test` and strict analysis.

## Ownership and readiness

Both children remain Incoming under `flight-hud-and-cues`; its unimplemented
FA/coherence-zone/drift acceptance stays open. `spark-facing-cue` owns heading
and roll geometry. `debug-view-state-restore` owns camera state restoration,
tracking offsets and debug/cinematic labels. Existing narrowing HUD work owns
binding/commitment semantics. This amendment only preserves their visible data.
No shader, font replacement, new UI dependency, camera spring, wire-marker
redesign, new key binding, world mutation, or later Gate gameplay is included.
Review the proposed information budget before either child is marked Ready.
