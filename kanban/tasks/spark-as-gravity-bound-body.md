---
uuid: "spark-as-gravity-bound-body"
title: "Spark becomes a real, gravity-bound ECS body that resolves with formation"
status: "done"
priority: "P1"
labels: ["domain", "infra", "physics", "player", "spark-redesign"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/spark-as-gravity-bound-body.md"
category: "specs"
estimate: 8
---

# Spark becomes a real, gravity-bound ECS body

> Spark-redesign card 4 of 4 (owner decision 2026-07-23). THE BIG ONE — do last,
> on its own branch/PR, atomically. The spark stops being a hand-moved singleton
> and becomes a light physics body: gravity moves it for free (so tracking is
> solved by physics), and it resolves from diffuse+large to small+dense+body-like
> as planets form — light enough that a planet could capture it as a satellite.

## Grounded integration (from design investigation, cite file:line)
- The observer is ALREADY an ECS entity (`domain.player.state/spawn-observer`,
  `src/domain/player/state.clj:18`) but its physical position lives INSIDE the
  `c/observer` map. Give the observer eid first-class columns:
  `c/position c/velocity c/mass c/radius c/body-kind` — and deliberately NO
  `c/matter-state`/`c/accretion-radius`/`c/composition`, which auto-excludes it
  from collision (`src/domain/physics/collision.clj:45`), hydro, classifier,
  sink-formation, and disc evolution (they all gate on matter-state). It will be
  picked up automatically by gravity (`spatial/index.clj:86`) and the integrator
  kinematics (`kinematics.clj:76-79`) — no change needed there.
- **Single-writer subtlety (must get right or CI fails):** component ownership is
  per-TYPE, not per-entity. The spark's `c/mass`/`c/radius` writes CANNOT be a new
  system — fold the resolve-from-formation-progress logic into the EXISTING
  mass/radius writer(s) (`domain.integrator` / `domain.stellar.geometry`) as a
  `body-kind = :spark` branch, or `write-conflicts` fails (`registry.clj:536`).
- Spark mass/radius interpolate on `:genesis/formation-progress` (card 3): large
  diffuse → small dense as it rises. State the pre-formation (progress≈0) mass
  explicitly (0 is numerically fine in gravity sums but contributes nothing).
- **DELETE** `domain.narrowing/spark-binding-step` + `observer-motion-step`
  (`narrowing.clj:301-342`) and their infra call site (`loop.clj:309-327`) —
  gravity replaces the spring.
- **Migrate the 8 infra readers** of `(:position (get-observer world))` to read
  `c/position` on the observer eid: `camera/navigation/tracking.clj`,
  `dev/window/loop.clj`, `menu.clj`, `menu/panels.clj`, `render/hud.clj`,
  `render/input.clj`, `render/scene/bodies.clj`, `render/scene/hud.clj`. Pick ONE
  source of truth (recommend `c/position` is truth; `c/observer`'s `:position`
  becomes a read-only mirror or is deleted) — a half-migration with two live
  position sources is exactly the split-brain `CLAUDE.md`'s single-substrate rule
  forbids.

## Done when (player-visible)
- Drop all input: the spark falls/orbits under gravity and settles into a
  Kepler-ish orbit around the star/halo — no spring, no puppet.
- As planets form, the spark visibly shrinks/densifies.
- Once a planet exists, releasing input near it lets the planet's gravity capture
  the spark as a satellite, camera riding along for free (tracking solved by
  physics).
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green;
  `write-conflicts {}`.

## Risks
Split-brain (map position vs ECS column); spark stranded before any body exists
(needs card 1 halo live); single-writer for spark mass/radius; camera/render/menu
blast radius. Do atomically, own branch, live-tune. WASD flight must still work
(player input overrides gravity drift for control).

## Dependencies
Cards 1 (halo binds it early), 2 (halo/spring removed), 3 (progress drives
resolve). Build last.
