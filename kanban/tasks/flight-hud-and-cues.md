---
uuid: "flight-hud-and-cues"
title: "Flight HUD: throttle/speed, coherence taper/lockout, FA indicator, velocity + nose cues"
status: "todo"
priority: "P3"
labels: ["infra", "render", "player", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/flight-hud-and-cues.md"
category: "specs"
estimate: 3
---

# Flight HUD & piloting cues

> spark-flight epic, Wave 4 card 10 of 10. Design: `docs/designs/spark-flight-and-camera.md` §4-§6.
> The readability layer for actually piloting: speed/throttle, the coherence meter
> with its taper and lockout zones, a flight-assist indicator, and Elite-style
> velocity + nose vector cues so drift is legible. Keep it quiet — the viewport is
> for awe (`ux-architecture.md` governing principle).

## Grounded integration (from design investigation, cite file:line)
- The player overlay/HUD lives in `src/infra/render/scene/hud.clj` (spark overlay
  `:29-46`, focus reticle ring builder `:14-27`) and the broader HUD surface;
  status bar per `docs/designs/ux-architecture.md` (Status bar layer). Add:
  - **Speed / throttle** readout (magnitude of `c/velocity`, throttle setting).
  - **Coherence meter** showing the soft-taper band and the hard-lockout floor
    (from `coherence-gated-thrust`, card 5) — a subtle badge, not a warning
    (`ux-architecture.md` "cost is real but quiet").
  - **Flight-assist ON/OFF** indicator (from card 3).
  - **Velocity vector + nose-direction cues** in the viewport (Elite-style):
    where you're drifting vs where you're pointing — the single most useful cue
    for a Newtonian-ish flight model. Nose from `c/orientation`, drift from
    `c/velocity`.
- Cues render via the `:line`/`:particle` raw-position path, consistent with
  overlays (`CLAUDE.md` Coordinates). Respect the four-pillar doctrine — this is
  instrumentation for Camera-navigate, not new verbs.

## Done when (player-visible via live pm2 window)
- Speed/throttle, coherence (with taper + lockout zones), and FA state are
  readable at a glance without dominating the viewport.
- Velocity and nose vectors are visible and make drift vs facing obvious,
  especially in FA-off.
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green.

## Risks
HUD clutter vs the "viewport is for awe" principle — keep it minimal and quiet.
Overlays that cost coherence should badge (existing rule); flight cues themselves
should be free. Don't validate vector placement with debug markers alone
(coordinate-path caveat).

## Dependencies
Cards 3 (FA state), 5 (coherence zones); reads orientation (card 1) + velocity
(card 2). Last card — the polish pass.
