---
category: "tasks"
labels: "infra, render, ux, spark-flight"
parent: "body-trails-ringbuffer"
type: "task"
write-id: "1791321950939-0.lej50ctx9poqc8bc8l"
points: "3"
title: "View: magnetic visualization toggle for readable body trails"
priority: "P1"
status: "incoming"
uuid: "3a501e16-f8fe-41a3-bdd1-760e503dd84d"
created_at: "2026-10-06T21:25:10.465Z"
---

# Make magnetic visualization an ordinary View choice

> Design: docs/designs/magnetic-field-visibility.md
> Grounding: [design](../../docs/designs/magnetic-field-visibility.md) → [native pixels and current source](../../docs/notes/2026-10-06-magnetic-visibility-evidence.md).
> Parent UUID: body-trails-ringbuffer. Estimate: 3 points.

## Context and outcome

Natural star/planet captures show large magnetic glyphs obscuring actual fading
motion trails. Let the player switch this inspection detail on and off through
View, with **Off as the fresh-window default**. This is proposed planning scope,
not an implemented or Ready feature.

## Scope

- One existing-style View Off/On row controlling only window UI configuration.
- Off covers both dipole loops and short star/protostar magnetic vectors; On
  retains their existing geometry and appearance. Name magnetic ownership at
  generation, rather than filtering all lines or matching color.
- Preserve the choice across drawer reopen, selection changes, camera modes
  and R camera reset. A fresh window defaults Off; no saved-preference system.
- Preserve nonmagnetic projection/cache correctness on the same world when
  toggling repeatedly; avoid generating large field loops while Off.

## Non-goals

No ECS writes, magnetic physics/force/resource changes, trail history/alpha or
body scale changes, camera/selection mutations, shader/GL changes, new renderer,
shortcuts, inspector dismissal, HUD re-layout, heading cues or Gate progression.
The existing manual-hud-overlay-spacing and manual-hud-quiet-telemetry proposals
retain HUD ownership. This child does not wait on parent completion; its visual
evidence helps close the parent's remaining acceptance.

## Acceptance criteria

- Actual View hits show the current state and produce Off → On → Off using the
  normal action path; absent configuration means Off. Only the owned UI setting
  changes. Existing controls and same-window persistence behave as designed.
- For one unchanged production world snapshot, Off removes both magnetic shape
  families and On restores them; bodies, trails, selection/hover/focus overlays,
  camera state and ECS data remain unchanged, including across cache reuse.
- Ordinary native clicks and public camera controls expose natural star and
  planet trails without magnetic glyphs. Record visible fading over actual
  history time; no injected world/config/camera or fixture substitutes.
- The row and labels remain reachable/readable at 1280×720, 960×540 and
  1600×900, including round-trip resize. Keep camera-follow inspection separate
  from manual-flight claims. Remaining HUD occlusion leaves parent acceptance
  open and is linked to its existing owners.

## Verification

Commit meaningful failing menu/hit, configuration-preservation and production
projection/cache tests before source. Include both magnetic families, real trail
samples, absent/zero-field data and exact nonmagnetic equality. Run focused
menu/render/trail and architecture checks, full tests and strict analysis;
coordinate before/after hot-path benchmark. Capture ordinary Off/On/Off and
fading/resize evidence with source, settings, ticks, actual histories, alpha,
budget and raw GL errors. No performance or gameplay completion claim follows
from merely passing projection tests.

## Risks and readiness

Main risks are missed short vectors, accidentally hiding unrelated lines,
stale cached geometry, and mistaking a clearer frame for proved fading. The
design's default is explicit and still subject to planning review. Incoming
only; normal review and canonical Rheos readiness/claim precede implementation.

---
Planning only, 2026-10-06. Exact source base3064336a482bd7e93875808f6ad1200164347c68; native pixel evidence remains separately pinned at607c4361032816ecb0fafd57f8c77946eea7d898 and is linked without copying media. Proposed design docs/designs/magnetic-field-visibility.md cites docs/notes/2026-10-06-magnetic-visibility-evidence.md. One3-point View control defaults Off and explicitly covers dipole loops plus short star/protostar vectors. Existing HUD children retain ownership; no world/physics/trail/camera/selection mutation or new resource policy. Canonical frontmatter --set design=docs/designs/magnetic-field-visibility.md refused with exit1: frontmatter keys not allowed: design. Explicit body links are present; no manual metadata bypass, Ready claim or implementation. Parent completion is not a prerequisite because this child helps close parent visual acceptance. Root owns review and publication.
---