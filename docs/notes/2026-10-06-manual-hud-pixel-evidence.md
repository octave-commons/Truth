# Native manual HUD observations

Observed 2026-10-06. These are project UI observations, not an external usability
study. The proposed response is [the quiet HUD amendment](../designs/manual-flight-hud.md).

The existing native capture [manifest](../../.ημ/diagnostics/manual-hud-plan/run.edn)
records source `81207e7c65212685456ebe0faff3cac1cec50613`, a real `:nebula`
world, no world injection, and a 1280×720 window. Copies below preserve the
original image bytes; this planning task did not rerun or alter the capture.
The relevant HUD/menu/frame-loop/inspector sources are identical between that
recorded commit and planning base `d803859` (`git diff --exit-code` passed).

| Native image | Direct observation | SHA256 |
|---|---|---|
| [start.png](../../.ημ/diagnostics/manual-hud-plan/start.png) | FIT ALL view; bright event text crosses the action palette; diagnostic block occupies the upper left. | `63c19d78677141a2261727735380e99f73e7840c848ced28836adffad1b5080e` |
| [after-controls.png](../../.ημ/diagnostics/manual-hud-plan/after-controls.png) | 3RD PERSON view after actual controls; the same eleven-line diagnostic block persists. | `a8b0a3baf49672e83c8ae7323bd17f788d003be42032dd515c68c459028eb6e5` |

Code explains these pixels:

- [`hud.clj`](../../src/infra/render/hud.clj) `hud-text-from-world` starts at
  `(16,38)`, uses 22-pixel line spacing and scale 2.2. Eleven lines extend through
  y≈274; the long IMF row also reaches far across the viewport.
- `notif-entry` starts text at `(width/2−140,height−200)` without measuring it.
  At 1280×720 that is `(500,520)`, scale 2.8. `controls-hud` reserves a 252×200
  action panel at `(1012,486)`–`(1264,686)`. The start image confirms the overlap.
- `controls-hud` also anchors the passive legend at x=352. Ambient text,
  observer text and bars each use their own pixel or NDC anchors. These formulas
  establish a resize risk; no resized capture is claimed here.
- [`window/loop.clj`](../../src/infra/dev/window/loop.clj) currently concatenates
  stats, observer, actions, inspector, view bar and menu lists independently.
  [`menu-hud`](../../src/infra/menu.clj) already exposes framebuffer-pixel
  `:regions`; [`inspector-card`](../../src/infra/inspect/card.clj) already has
  calculated card bounds. Reuse these surfaces rather than invent another UI.
- [`World` details](../../src/infra/menu/panels.clj) currently show brief identity
  placeholders, not the detailed simulation statistics. Moving diagnostics there
  therefore requires real read-only content; merely removing the overlay loses
  information.
- `domain-lines :narrator` and `read-only-panel-ctx` also put the current event
  into one unwrapped line in a 320-pixel-wide panel. Independent review caught
  that this existing surface cannot be presumed to solve event-text overflow.

Interpretation: reduce permanent diagnostic density in manual mode and give
existing overlays coordinated bounds. This does not show that world rendering,
wire markers, heading cues, camera restoration, or flight feel are solved.
