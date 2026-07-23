---
uuid: "dark-matter-static-halo"
title: "Dark-matter static halo — deepen the well so bodies stay bound"
status: "review"
priority: "P1"
labels: ["domain", "physics", "spark-redesign"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/dark-matter-static-halo.md"
category: "specs"
estimate: 3
---

# Dark-matter static halo

> Spark-redesign card 1 of 4 (owner decision 2026-07-23). A separate, huge,
> very massive diffuse mass — NOT the spark — that holds the system together so
> bodies stop retaining their infall momentum and flinging out of the nebula.

## Design
A STATIC background gravitational potential: very massive (a multiple of
`:genesis/nebula-mass`, > nebula mass per owner), large scale radius (~½ initial
nebula radius), centered at the world origin `[0 0 0]`. It does not collapse or
render. Purely deepens the well.

## Grounded integration (from design investigation, cite file:line)
- New fan-out emitter `domain.gravity.dark-matter/dark-matter-acceleration-system`,
  registered next to `:gravity` in `src/domain/ecs/registry.clj:145-151`. Reads
  `c/position` (+`c/mass` only if needed), writes a NEW `c/accel-dark-matter`.
- Add `c/accel-dark-matter` to the integrator accumulate vector
  (`src/domain/integrator/base.clj:16-18`) and to the `:integrator` registry
  entry's `:reads` (`registry.clj:169`). Same shape as every other force —
  pure fan-out, no serial step, one writer.
- Reuse `law.stellar/plummer-acceleration` (the primitive the old halo + warp
  wells use). Mass/scale mirror `player/influence-reference`'s pattern.
- **No new barycenter tracker:** the kinematics recenter step subtracts
  `:genesis/frame-offset` every tick (`src/domain/integrator/kinematics.clj:49`,
  `src/domain/spatial/index.clj:80-97`), pinning the barycenter at the origin —
  so a halo centered at `[0 0 0]` IS centered on the barycenter. Do not rebuild
  COM tracking.

## Done when (player-visible)
- A body launched at initial nebula-collapse infall velocity no longer escapes
  the system edge; a multi-thousand-tick run shows bound orbits, not ejections.
- A debug HUD readout of halo mass/scale-radius exists for live tuning.
- `clojure -M:test` + architecture-test green; `write-conflicts {}`.

## Risks / tuning
"More massive than the nebula" risks a well deep enough to STALL SPH collapse
(accretion, disk formation) if mass/scale ratio is wrong. This needs live-window
tuning, not a-priori derivation — ship with the debug readout and tune.

## Dependencies
None. **Build and tune this FIRST at the live window** — it's the actual fix for
the flinging bug and is independently valuable.
