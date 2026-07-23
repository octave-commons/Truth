---
uuid: "spark-planet-binding"
title: "Spark: bind the observer to a focused planet (co-moves, no flicker)"
status: "todo"
priority: "P1"
labels: ["domain", "narrowing", "player", "integration-debt"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/spark-planet-binding.md"
category: "specs"
estimate: 5
---

# Spark: bind the observer to a focused planet

> Integration-debt card (2026-07-23 re-triage). User report: "my spark can only
> keep up with a planet in follow-camera mode, and it appears/disappears a lot …
> I'd want the spark to be gravitationally bound to the planet."

## Root cause (investigation)
The spark is **not an ECS physics body**. Its state is the singleton
`c/observer` map (`domain/player/state.clj:8`); position is set only by (1)
kinematic WASD drift (`player.focus/drift`), (2) decoherence gradient drift
(`release-focus`), or (3) **camera puppeting** in non-manual modes
(`sync-observer-focus-to-camera`, `loop.clj:128-143`). No force ever pulls the
spark *toward* a body — `player.influence/observer-acceleration-system` writes
the halo pull *onto other bodies* (`influence.clj:24`), never onto the spark.
So outside follow-cam the spark and the (real, orbiting) planet separate.

**Flicker:** in follow mode the spark's position == the body's exact center, so
its `:particle` sprite is depth-occluded behind the body sphere; tracking lag
between camera-update (`loop.clj:276`) and observer-sync (`:290`) flaps it in and
out of the sphere. Not an LOD cull (`bodies.clj:58-86` leaves `:particle`
untouched) — a center-occlusion + lag artifact.

## Decision — approach (B), spring-force model (Aaron, 2026-07-23)
Give the observer a real, tick-integrated position pulled toward the focused
body's predicted position by a spring/tether force `k·(target−pos)` scaled by
`domain.narrowing/tether-strength`. Implemented as a **pure fan-out emitter**
(like `observer-acceleration-system`) in the single Jacobi tick — **no serial
barrier, no mass, no Keplerian orbit**. The spark is physically pulled toward the
world (honest to "gravitationally bound"), not painted on it.

Implementation notes:
- The observer is currently NOT an ECS body — its state is the singleton
  `c/observer` map. Either add a velocity field to that map integrated by a new
  observer-motion system, or introduce `c/observer-velocity`/`c/accel.observer-self`
  with a sole writer. Whichever, keep it Jacobi-consistent (one writer, one-tick
  lag) and pure — no serial post-fold step.
- Read the target body's predicted position via
  `domain.physics.cache/predicted-position-fn` (already used by
  `player.influence`). Gate strength by `domain.narrowing/tether-strength`.
- The camera-puppet paths (`sync-observer-focus-to-camera`, `loop.clj:128-143`)
  must now DEFER to the spark's real position rather than overwrite it —
  coordinate with `narrowing-tether-default-camera-modes`.
- Must scale by sim-time/dt, not raw ticks (CLAUDE.md Time model).

## Done when (player-visible)
- With a world bound above threshold, releasing flight keys leaves the spark
  visibly co-moving with that planet in **every** camera mode, not just
  follow-selection.
- The spark is never depth-occluded inside the body it is bound to (offset to a
  legible standoff / halo, or drawn depth-independent).

## Dependencies
Pairs with `narrowing-worldscale-overlap-gate` (defines *which* world).
Approach (A) folds into `narrowing-tether-default-camera-modes`; approach (B) is
this standalone card. Reuses `domain.narrowing/tether-strength` either way.
