---
uuid: "spark-rotational-input-intents"
title: "Route mouse aim and Q/E roll through spark torque intents"
status: incoming
priority: P1
labels: "infra, input, player, spark-flight"
created_at: "2026-10-06T00:00:00Z"
source: "kanban/tasks/spark-rotational-input-intents.md"
category: specs
estimate: 3
parent: "spark-6dof-input-mapping"
dependency: ["spark-body-frame-torque-commands", "spark-facing-cue", "flight-assist-damping-and-toggle"]
design: "docs/designs/spark-flight-controls-increments.md"
---

# Rotational input intents

> Design: docs/designs/spark-flight-controls-increments.md — Proposed body frame and Scope allocation.
> Grounding: [amendment](../../docs/designs/spark-flight-controls-increments.md) → [rotation research](../../docs/research/physics/spark-rotation-integration.md), current input/intent-queue observations.

## Outcome and scope

Extract independently verifiable rotation from the 5-point input parent to make
the new physics visible before full piloting/economy settings land. Mouse X/Y
supplies bounded yaw/pitch; held Q/E supplies roll through the existing intent
queue. Consume mouse deltas once; define held-key versus pulse lifetime explicitly.
MMB routes movement to existing camera orbit, with no ship-aim command. Keep
bindings in live configuration data and use corrected design §7.

## Non-goals

No physical-state writes, body translation mapping, boost/F wiring, full rebinding
UI, new camera rig, idle return or changed focus/ability bindings. These remain
parent/sibling scopes; no claim that the entire input parent is complete.

## Acceptance

- Real mouse and Q/E input turns the ECS orientation and visible facing cue with
  documented signs, rather than merely changing camera yaw/pitch.
- Release, UI capture, lost focus and leaving manual mode clear pending commands;
  recapture/first movement cannot replay a stale cursor delta or held key.
- MMB moves the camera only; left-click picks; left-drag does not own a competing
  orbit route. Rotation keys do not collide with C/R camera actions.
- Assist-on braking and scripted assist-off free spin follow the domain contract;
  input never resets omega. Changing live binding data works without restart.

## Readiness and verification

Incoming behind torque, cue and FA domain dependencies. Parent acceptance still
contains obsolete Z/C, E, R and Q assignments; record their reconciliation through
Rheos before parent completion. Red pure input-policy/queue tests, full
`clojure -M:test`, `bin/analyze --strict`, and native key/mouse capture with ECS
orientation/omega evidence. MMB idle return remains its separate camera child.
