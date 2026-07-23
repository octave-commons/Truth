---
uuid: "remove-passive-halo-invert-influence"
title: "Invert influence: remove the passive spark halo, strengthen paid wells"
status: "todo"
priority: "P1"
labels: ["domain", "physics", "player", "spark-redesign"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/remove-passive-halo-invert-influence.md"
category: "specs"
estimate: 2
---

# Invert influence: remove the passive spark halo, strengthen paid wells

> Spark-redesign card 2 of 4 (owner decision 2026-07-23). By default the SYSTEM
> moves the spark more than the spark moves the system — without spending quanta.
> The spark's passive world-pull goes away; the player's paid gravity-well
> abilities get stronger to compensate.

## Grounded integration (from design investigation, cite file:line)
**Remove the passive halo (clean single-writer/single-consumer channel):**
- Delete `observer-acceleration-system`, `halo-mass`, `default-halo-mass-factor`
  from `src/domain/player/influence.clj`.
- Delete the `:observer-accel` registry entry (`src/domain/ecs/registry.clj:182-185`)
  and its emitter call in `src/domain/genesis/systems.clj:43`.
- Remove `c/accel-observer` from the integrator accumulate vector
  (`src/domain/integrator/base.clj:16-18`) and the `:integrator` `:reads`
  (`registry.clj:169`). Grep confirms nothing else reads `c/accel-observer`.

**Also remove the now-superseded spring binding** (gravity replaces it — but only
once card 4 makes the spark a real body; if card 4 hasn't landed, KEEP the spring
until it does so the spark isn't stranded — coordinate ordering, see deps):
- `domain.narrowing/spark-binding-step` + `observer-motion-step`
  (`src/domain/narrowing.clj:301-342`) and their call site
  (`src/infra/dev/window/loop.clj:309-327`).

**Strengthen paid wells (pure constant tuning):**
- `domain.intervention/warp-acceleration-system` is the template
  (`src/domain/intervention.clj:112-148`) — sole writer of `c/accel-warp`, same
  `influence-reference`/`halo-reach-factor` the old halo used.
- Raise `default-well-mass-factor` (`intervention.clj:51-56`, now 0.5) and tune
  `action-cost` (`intervention.clj:32-35`) / radius / ttl so a placed well is the
  player's real lever. Optionally scale with formation-progress or resonance.

## Done when (player-visible)
- The spark no longer pulls distant bodies toward itself just by being looked at.
- Placing a `:warp/well` still gathers matter, now at higher potency.
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green;
  `write-conflicts {}`.

## Dependencies
Card 1 (dark-matter halo) must be LIVE — removing the passive halo without a
replacement binding force strands the early-collapse nebula. Spring removal is
gated on card 4 (don't strand the spark). Well-strengthening is independent.
