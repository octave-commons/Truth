---
uuid: "spark-camera-shared-basis-bank"
title: "Share the rendered camera basis and inherit partial spark bank"
status: incoming
priority: P2
labels: "infra, camera, render, spark-flight, research-needed"
created_at: "2026-10-06T00:00:00Z"
source: "kanban/tasks/spark-camera-shared-basis-bank.md"
category: specs
estimate: 3
parent: "chase-camera-spring-rebuild"
dependency: ["spark-chase-position-aim-springs"]
design: "docs/designs/spark-flight-controls-increments.md"
---

# Shared view basis and partial bank

> Design: docs/designs/spark-flight-controls-increments.md — Camera research prerequisite.
> Grounding: [amendment](../../docs/designs/spark-flight-controls-increments.md) → [rotation research](../../docs/research/physics/spark-rotation-integration.md); bank/rest-frame and spring grounding remain prerequisites.

## Outcome and scope

Split partial roll from the chase parent. Define one view-basis contract used
by scene view matrices, render/screen projection and picking, and volume rays.
Apply a tunable damped fraction of the spark bank, initially within the existing
0.3–0.6 design range. World geometry remains z-up while the camera can bank.

## Non-goals

No coordinate-system migration, separate picking camera, new shader pipeline,
orbit gestures, debug-view restore or writes to spark orientation.

## Acceptance

- Research/design defines the bank/rest-frame rule and finite view-pole/opposite
  direction behavior before implementation; no implicit Euler-angle discontinuity.
- The three current basis consumers agree for neutral and nonzero bank.
  Projection/ray round trips select the visibly rendered target under roll.
- Zero bank preserves existing z-up behavior; the tunable and damping are
  explicit, frame-time tested, and independent of simulation dt.
- Native fog, solid geometry and facing cue turn together without apparent
  picking offset; the spark's own roll remains visually distinguishable.

## Readiness and verification

Not Ready until the positional rig and amendment's camera research contract are
reviewed. Red basis/projection/picking tests, focused camera/render tests,
full `clojure -M:test`, `bin/analyze --strict`, and a rolled native-window capture
with picking evidence. A single debug marker does not validate every render path.
