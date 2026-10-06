---
uuid: "spark-camera-mmb-orbit-return"
title: "Orbit the chase anchor with middle mouse and ease back after idle"
status: incoming
priority: P2
labels: "infra, camera, input, spark-flight, research-needed"
created_at: "2026-10-06T00:00:00Z"
source: "kanban/tasks/spark-camera-mmb-orbit-return.md"
category: specs
estimate: 2
parent: "chase-camera-spring-rebuild"
dependency: ["spark-chase-position-aim-springs", "spark-rotational-input-intents"]
design: "docs/designs/spark-flight-controls-increments.md"
---

# Middle-mouse chase orbit and return

> Design: docs/designs/spark-flight-controls-increments.md — Camera research prerequisite and Scope allocation.
> Grounding: [amendment](../../docs/designs/spark-flight-controls-increments.md) → [rotation research](../../docs/research/physics/spark-rotation-integration.md); use the positional child's tracked spring evidence before coding.

## Outcome and scope

Complete the chase parent's orbit behavior using rotational input's existing MMB
gesture route. Orbit about the chase anchor while held; after the documented idle
interval, ease to the velocity/body rest frame using the grounded spring state.

## Non-goals

No duplicate mouse callback, ship-aim/torque changes, new bindings/settings UI,
partial bank or debug-mode save/restore (owned by `debug-view-state-restore`).

## Acceptance

- MMB changes only the camera; default mouse aim resumes without a queued delta
  jump after release. UI capture/mode exit clears the orbit gesture.
- Equal elapsed render time produces comparable return; no sim-dt coupling.
- Left-click selection still works; orbit never moves or rotates the ECS spark.
- Idle return remains finite at rest and when velocity opposes heading, using
  the positional rig's reviewed frame rule.

## Readiness and verification

Incoming behind the two UUID dependencies and missing camera spring grounding.
Red gesture/idle tests, focused input/camera tests, full `clojure -M:test`,
`bin/analyze --strict`, and native MMB hold/release/pick evidence. Coordinate the
new state shape with the existing debug-view card without expanding this scope.
