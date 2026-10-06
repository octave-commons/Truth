---
uuid: "spark-facing-cue"
title: "Make spark heading and roll visible with the existing line renderer"
status: incoming
priority: P1
labels: "infra, render, player, spark-flight"
created_at: "2026-10-06T00:00:00Z"
source: "kanban/tasks/spark-facing-cue.md"
category: specs
estimate: 2
parent: "flight-hud-and-cues"
dependency: ["spark-body-frame-torque-commands"]
design: "docs/designs/spark-flight-controls-increments.md"
---

# Spark facing cue

> Design: docs/designs/spark-flight-controls-increments.md — Proposed body frame and Scope allocation.
> Grounding: [amendment](../../docs/designs/spark-flight-controls-increments.md) → [rotation research](../../docs/research/physics/spark-rotation-integration.md), inspected observer overlay/line render path.

## Outcome and scope

Lift the independent nose cue out of the 3-point HUD parent so rotation can be
seen before FA/economy readouts. Add a subtle forward line and asymmetric up/wing
tick at the actual observer position using quaternion-derived body axes and the
existing raw-position line path. Reuse the torque child's common frame transform
and axis constants. This is permanent pilot instrumentation.

## Non-goals

No bespoke mote shader, replacement body model, chase camera, velocity/drift cue,
coherence meter, full HUD, simulation writes or claim of input controllability.

## Acceptance

- Identity, yaw/pitch and roll poses produce the agreed body-frame endpoints;
  the asymmetric tick makes roll legible, including a full-axis rotation.
- Cue placement uses the same world-to-render transform as the spark, stays
  readable at the tested camera distances and does not dominate the viewport.
- The existing particle/focus display remains coherent; missing observer yields
  no orphan cue. No camera state or physical state changes to manufacture motion.
- Native window and offscreen capture show the cue following ECS orientation;
  input-driven acceptance remains `spark-rotational-input-intents`.

## Readiness and verification

Incoming behind the common frame transform; review cue placement first. No camera
spring research prerequisite. Red pure render-shape tests, focused render tests,
full `clojure -M:test`, `bin/analyze --strict`, and neutral/rolled native captures.
Keep the HUD parent's remaining drift/speed/coherence/FA acceptance open.
