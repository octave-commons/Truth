---
uuid: "formation-progress-metric"
title: "Formation-progress metric (fraction of nebula mass bound in star + planets)"
status: "done"
priority: "P2"
labels: ["domain", "physics", "spark-redesign"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/formation-progress-metric.md"
category: "specs"
estimate: 2
---

# Formation-progress metric

> Spark-redesign card 3 of 4 (owner decision 2026-07-23). A single [0,1] scalar
> = fraction of nebula mass now bound into the star + planets. Drives the spark's
> resolution curve (card 4). Inert until card 4 consumes it — buildable in
> parallel with cards 1-2.

## Grounded integration (from design investigation, cite file:line)
- The pieces exist: `domain.genesis.summary/system-summary`
  (`src/domain/genesis/summary.clj:98-125`) already partitions resolved bodies
  into `:stars`/`:planets`/`:regions`, each with `:mass`; `stats-of`
  (`summary.clj:58-96`) computes an analogous `:resolved-fraction`
  (`summary.clj:79`) cached once/tick via `cached-system-summary`
  (`summary.clj:127-135`). `:genesis/nebula-mass` is a live world key.
- Compute `formation-progress = (Σstar-mass + Σplanet-mass) / :genesis/nebula-mass`
  clamped [0,1], off `cached-system-summary`. Prefer a plain derived world scalar
  (`:genesis/formation-progress`) computed once/tick — it only READS mass, writes
  nothing contended. If it must be Jacobi-consistent per-tick, fold it into an
  existing summary-producing system's write-set as a diagnostic component (do NOT
  add a rogue second writer).
- `domain.arc/detect-arc` (`src/domain/arc.clj:38-60`) is the consumer-side
  precedent for reading tick-summary state into player-facing meaning.

## Done when
- `:genesis/formation-progress` (or a diagnostic component) is readable off the
  world every tick, in [0,1].
- A test asserts it rises ~monotonically across a scripted nebula→star→planets
  run.
- `clojure -M:test` + architecture-test green; `write-conflicts {}`.

## Dependencies
None. Parallel with cards 1-2. Unblocks card 4.
