---
uuid: "spark-chase-position-aim-springs"
title: "Replace manual camera lerp and tether with position and aim springs"
status: incoming
priority: P1
labels: "infra, camera, spark-flight, research-needed"
created_at: "2026-10-06T00:00:00Z"
source: "kanban/tasks/spark-chase-position-aim-springs.md"
category: specs
estimate: 5
parent: "chase-camera-spring-rebuild"
dependency: ["spark-body-frame-torque-commands"]
design: "docs/designs/spark-flight-controls-increments.md"
---

# Chase position and aim springs

> Design: docs/designs/spark-flight-controls-increments.md — Camera research prerequisite.
> Grounding: [amendment](../../docs/designs/spark-flight-controls-increments.md) → [rotation research](../../docs/research/physics/spark-rotation-integration.md); closed-form spring grounding is explicitly missing.

## Outcome and scope

Split the positional rig from the 8-point chase parent. Track the current
observer position with separate position/aim spring states, a fixed chase
distance, and velocity-biased look-ahead with a finite body-forward rest case.
Reuse the torque child's agreed body axes and quaternion vector transform.
Use explicit render wall dt and render units. Remove the competing manual
tether step when this rig becomes the manual camera authority.

## Non-goals

No partial bank, MMB gesture routing/return, debug-mode state restore, physics
forces, simulation dt changes, velocity-scaled chase distance or new renderer.

## Acceptance

- A tracked primary-source spring note specifies the exact update and assumptions
  before coding; the design links it and defines the zero-speed/opposing-frame case.
- Fixed-target elapsed-time partitions agree within stated tolerance. Zero dt
  preserves state; invalid/large dt behavior is explicit and tested.
- Observer/world-to-render placement is correct; gravity drift is followed
  continuously, with aim intentionally slower than positional catch-up.
- Manual tether no longer adds a second camera update. Simulation state is
  untouched and the mote remains legible in the lower-center chase composition.

## Readiness and verification

Not Ready: the camera parent cites a formula absent from its design and the
original research scratchpad is missing. Complete the amendment's camera research
prerequisite first. Then write red camera tests, run full `clojure -M:test` and
`bin/analyze --strict`, and capture a native window under stationary and moving
targets. Do not infer arbitrary-initial-velocity no-overshoot from damping ratio.
