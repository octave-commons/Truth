---
uuid: "manual-hud-overlay-spacing"
title: "Keep existing flight overlays inside shared framebuffer regions"
status: incoming
priority: P1
labels: "infra, render, ux, spark-flight"
created_at: "2026-10-06T19:07:22Z"
source: "kanban/tasks/manual-hud-overlay-spacing.md"
category: specs
estimate: 3
parent: "flight-hud-and-cues"
design: "docs/designs/manual-flight-hud.md"
---

# Existing HUD overlays without collisions

> Design: docs/designs/manual-flight-hud.md — spacing slice and visual contract.
> Grounding: [design](../../docs/designs/manual-flight-hud.md) → [native pixel evidence](../../docs/notes/2026-10-06-manual-hud-pixel-evidence.md).

## Outcome and scope

Fix the captured event/action-palette collision using coordinated pixel bounds
for existing actions, passive controls, observer readouts/bars, notification and
ambient text. Reserve existing shell/menu, diagnostic, view-badge and inspector
bounds; use current framebuffer dimensions and measured text extents. Reuse the
current renderer and a small deterministic layout helper. Keep rectangles and
their labels aligned. This child owns placement, not new HUD content.

## Non-goals

No shader, new library, new renderer, generic UI engine, ECS/camera/input writes,
new menu behavior, heading geometry, camera restore/debug labels, or changes to
the existing binding/commitment and action-affordability semantics.

## Acceptance

- At 1280×720, 960×540 and 1600×900, the design's measured owned regions stay
  on-screen with ≥8px separation and no intersection with reserved shell/card
  regions; each text stays in its own region. Existing font scale stays ≥1.3.
- The full captured event text is wrapped within its region, not shortened or
  sent to the unwrapped Narrator panel. It and long ambient text do not cross
  action rows; keys/costs and resource/binding values stay readable and truthful.
- Closed drawer, World drawer, selected card, commitment and absent-observer
  cases follow the design's explicit reflow/yield policy without mutating world
  or camera state. Existing menu hit regions remain authoritative.
- Resizing 1280×720 → 960×540 → 1600×900 → 1280×720 restores a coherent layout.

## Readiness and verification

Incoming for design review. Red pure layout invariants for containment,
separation and retained information; focused render/menu tests, full tests and
strict analysis; native screenshots at all three sizes with the event/palette
case present. No implementation or visual acceptance is claimed by this card.
