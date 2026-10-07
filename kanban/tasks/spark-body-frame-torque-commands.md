---
uuid: "spark-body-frame-torque-commands"
title: "Emit body-frame torque commands with simulation-dt response"
status: incoming
priority: P1
labels: "domain, physics, player, spark-flight"
created_at: "2026-10-06T00:00:00Z"
source: "kanban/tasks/spark-body-frame-torque-commands.md"
category: specs
estimate: 5
parent: "spark-flight-force-channels"
dependency: ["spark-orientation-angular-momentum"]
design: "docs/designs/spark-flight-controls-increments.md"
---

# Body-frame torque commands

> Design: docs/designs/spark-flight-controls-increments.md — Proposed body frame and torque law.
> Grounding: [amendment](../../docs/designs/spark-flight-controls-increments.md) → [rotation research](../../docs/research/physics/spark-rotation-integration.md).

## Outcome and scope

Split the torque half of the 8-point force parent. Define a Malli-validated,
bounded pilot command, body-axis/sign constants, quaternion vector transformation,
and one self-owned world-axis torque channel in the existing influence registry.
Use the amendment's proposed dt-derived input law; command intents enter through
the existing serial queue and are consumed through the ordinary Jacobi fold.

## Non-goals

No input callbacks, linear thrust, camera, shader, FA damping, inertia refinement,
physical-state writes, clock changes, new barrier or integrator special case.
Existing `flight-assist-damping-and-toggle` owns the separate damping channel.

## Acceptance

- Identity and nonidentity orientations produce the specified pitch/yaw/roll signs.
- One declared writer emits/clears the channel; only the rotation integrator
  writes orientation/angular velocity. No observer means no stale contribution.
- Fixed-dt tests cover fractional and cosmological steps below the physical cap;
  zero dt emits zero and invalid input/dt fails at its named boundary.
- A pulse drains with documented Jacobi lag, then no-input/no-damping coast
  preserves physical omega even when dt changes. No hidden omega rescaling.
- Test representative adaptive-dt transitions and finite response bounds through
  the actual fold; record failures as control-law findings, not a passing sweep.

## Readiness and verification

Incoming proposal: review law values and the adaptive-dt test envelope before
implementation admission. Write red pure-law, channel and actual-pipeline tests;
run focused rotation/architecture tests, full `clojure -M:test`, and
`bin/analyze --strict`. Benchmark the changed tick path with dev service stopped.
Record scripted pulse/world-state evidence; real keyboard acceptance belongs to
`spark-rotational-input-intents`, not this emitter-only child.
