---
uuid: "body-trails-ringbuffer"
title: "Motion trails on star, planets, and spark (ring-buffer component + line render)"
status: "todo"
priority: "P2"
labels: ["domain", "infra", "render", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/body-trails-ringbuffer.md"
category: "specs"
estimate: 5
---

# Motion trails on significant bodies + spark

> spark-flight epic, Wave 4 card 8 of 10. Design: `docs/designs/spark-flight-and-camera.md` §6.1.
> Fading trails behind the star, planets/protoplanets, and the mote — the
> perspective anchor that lets the owner judge whether flight and camera feel
> right. NO dependency on the flight cards: can be pulled forward at any time for
> grounding.

## Grounded integration (from design investigation, cite file:line)
- No trail/path component exists today (confirmed — none in `domain.ecs`, none in
  the renderer). Add a ring-buffer trail component (fixed capacity N positions)
  written by ONE system for the eligible bodies. Eligibility: star + planets +
  spark — gate on `c/body-kind` / matter-state so dust/fragments are excluded and
  a dense nebula stays legible.
- **Sample at a sim-time cadence, not per render frame** (`CLAUDE.md` Time model;
  `.agents/skills/physics-dt-unit-mismatch/`) — a per-tick or per-render sampler
  changes trail length with the clock. Store world positions; the trail writer is
  the sole writer of the trail component (`registry.clj:536`).
- Render via the renderer's `:line` path (raw positions, NOT the `:body`
  model-matrix path — see `CLAUDE.md` Coordinates; body vs particle/line split).
  Fade alpha along the strip (oldest → transparent). Precedent for particle/line
  overlay rendering in `src/infra/render/scene/` (spark particle at
  `scene/hud.clj:29-46`; bodies at `scene/bodies.clj`).
- Convert world→render units with `phase0-view-scale = 1e15`
  (`projection.clj:17-19`). Cap total segments across all trailed bodies for perf.

## Done when (player-visible via live pm2 window)
- The star, each planet, and the mote leave a visible fading trail showing their
  recent path; orbits and your own flight path are readable.
- Trail length is stable across time-rate changes (sim-time sampled).
- Dust/fragments are NOT trailed (scene stays legible).
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green;
  `write-conflicts {}`.

## Risks
Segment-count blowup with many bodies (cap it); trails through the wrong render
path (must be `:line`/raw, not `:body`); sim-time sampling (recurring dt bug).
Choose N and cadence so trails are long enough to read but cheap.

## Dependencies
None. Can land any time. Pairs well with card 9 for visual grounding.
