---
uuid: "spark-body-frame-translation"
title: "Apply existing translational thrust along spark body axes"
status: incoming
priority: P1
labels: "domain, physics, player, spark-flight"
created_at: "2026-10-06T00:00:00Z"
source: "kanban/tasks/spark-body-frame-translation.md"
category: specs
estimate: 3
parent: "spark-flight-force-channels"
dependency: ["spark-body-frame-torque-commands"]
design: "docs/designs/spark-flight-controls-increments.md"
---

# Body-frame translation

> Design: docs/designs/spark-flight-controls-increments.md — Proposed body frame and torque law.
> Grounding: [amendment](../../docs/designs/spark-flight-controls-increments.md) → [rotation research](../../docs/research/physics/spark-rotation-integration.md), existing displacement derivation and regression.

## Outcome and scope

Split the linear half of the 8-point force parent. Interpret forward/right/up
commands in the agreed spark body frame and rotate them into world axes before
emitting the existing acceleration influence. Preserve displacement-derived
simulation-dt scaling; put forward/lateral asymmetry in named law constants.
Reuse the torque child's frame transform and boundary conventions.

## Non-goals

No direct position/velocity writes, deleted-drift recreation, new integrator,
boost economy, keyboard/settings UI, or FA toggle. Embedded linear damping moves
only with its replacement in `flight-assist-damping-and-toggle`, never twice.

## Acceptance

- Camera orbit alone does not change thrust direction; spark attitude does.
- Forward/right/up and diagonal commands have bounded, documented magnitudes;
  the chosen forward/lateral ratio stays within the design's 1.5–2 tuning range.
- Existing displacement tests remain meaningful across dt values, including a
  fractional dt case; invalid dt does not receive an invented default.
- Contributions clear on zero command/missing observer; gravity still composes
  through the existing sum and physical state keeps its sole integrator writer.
- Record current damping behavior explicitly; do not claim FA-off conservation
  until the separate FA card removes the embedded damping term.

## Readiness and verification

Incoming until common frame/control-law review and FA ownership are settled.
Red body-frame and actual-fold tests, focused spark-body/architecture tests,
full `clojure -M:test`, `bin/analyze --strict`, and before/after hot-path benchmark.
Use an actual ECS attitude/thrust demonstration; input mapping remains its own
parent scope and is required before claiming keyboard-driven translation.
