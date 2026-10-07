---
uuid: "manual-hud-quiet-telemetry"
title: "Keep manual flight telemetry compact and move diagnostics into World details"
status: incoming
priority: P1
labels: "infra, render, ux, spark-flight"
created_at: "2026-10-06T19:07:22Z"
source: "kanban/tasks/manual-hud-quiet-telemetry.md"
category: specs
estimate: 3
parent: "flight-hud-and-cues"
dependency: ["manual-hud-overlay-spacing"]
design: "docs/designs/manual-flight-hud.md"
---

# Quiet telemetry in manual flight

> Design: docs/designs/manual-flight-hud.md — telemetry slice and visual contract.
> Grounding: [design](../../docs/designs/manual-flight-hud.md) → [native pixel evidence](../../docs/notes/2026-10-06-manual-hud-pixel-evidence.md).

## Outcome and scope

Remove the eleven-line permanent simulation diagnostic block from normal manual
flight. Retain phase, elapsed time and rate in at most two compact lines; expose
every existing diagnostic value through the existing World details panel in
readable short rows. Reuse the preceding child's framebuffer region contract.
Keep current speed/view, resources, actions and binding/commitment readouts.

## Non-goals

No deletion of diagnostic information, new shell domain or key binding, shader,
new UI dependency, ECS/camera/time-rate writes, new flight quantities, heading
cue, debug/cinematic labels, camera restoration or Gate gameplay.

## Acceptance

- Manual compact telemetry is at most two lines, scale ≤1.5 and ≥1.3, within
  36px height below the top bar. All former diagnostic values are available in
  World details, without clipped long rows or placeholder substitutes.
- At 1280×720, 960×540 and 1600×900, manual mode with drawers closed and no
  selection has no permanent text/opaque panels in the design's central 40%
  rectangle; current player/action data remains available and truthful.
- Other camera modes retain diagnostic information. World open/close and the
  specified resize round trip preserve live flight and produce no collisions.
- World identity, simulation, camera and input intent are unchanged by either
  projection; binding/commitment keeps its existing semantics and visibility.

## Readiness and verification

Incoming behind `manual-hud-overlay-spacing` and design review. Red projection
tests for manual/detail information and mode selection, measured region tests,
focused render/menu tests, full tests and strict analysis. Compare native
manual and World-open screenshots at the three acceptance sizes. The parent
HUD's FA, coherence-zone and drift-cue work remains open.
