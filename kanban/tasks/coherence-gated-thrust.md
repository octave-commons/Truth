---
uuid: "coherence-gated-thrust"
title: "Coherence-gated thrust: drain-while-thrusting, regen-while-coasting, taper + lockout"
status: "todo"
priority: "P2"
labels: ["domain", "player", "spark", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/coherence-gated-thrust.md"
category: "specs"
estimate: 5
---

# Coherence-gated thrust

> spark-flight epic, Wave 2 card 5 of 10. Design: `docs/designs/spark-flight-and-camera.md` §4.
> Thrust costs coherence; coasting restores it. Shaped as a heat/stamina meter
> (continuous drain/regen), NOT a charge-and-spend ultimate. Makes movement a
> rhythm: burn to maneuver, coast to recover — "let it rise."

## Grounded integration (from design investigation, cite file:line)
- Coherence already exists: `:coherence` 0.8 / `:max-coherence` 1.0 in the
  `c/observer` map (`src/domain/player/state.clj:17`), with drain/regen/event-gain
  in `domain.player.economy` (`src/domain/player/economy.clj:1-27`, writer
  `apply-coherence` `:26`). It already modulates the observer halo
  (`influence.clj:20`) and render opacity (`scene/bodies.clj:152`) — extend, don't
  fork.
- Add a thrust coupling inside `apply-coherence`'s existing single-writer path:
  - **Drain** ∝ `|a_thrust| · dt_sim` while thrusting; **boost (`E`) drains
    harder**.
  - **Regen** while coasting (existing regen, sim-time paced).
  - **Soft taper:** below a soft threshold, scale available thrust accel down (the
    thrust system in card 2 reads coherence and clamps its output). Telegraphs the
    limit.
  - **Hard floor + hysteresis:** at the floor, thrust locks out until coherence
    recovers past a higher re-arm threshold — no boundary flicker. At lockout only
    gravity + residual momentum move the spark.
- **Sim-time pacing is mandatory:** drain/regen scale by sim-time/dt, not raw tick
  count — `dt` dilates with dynamical time (`CLAUDE.md` Time model;
  `.agents/skills/physics-dt-unit-mismatch/`). This class of bug has recurred.
- Thresholds/rates as constants in `law/`.

## Done when (player-visible via live pm2 window)
- Sustained thrust visibly drains coherence (existing HUD bar / mote brightness);
  coasting refills it.
- As coherence nears empty, thrust weakens (taper) then cuts out (lockout); the
  spark drifts until coherence re-arms, then thrust returns.
- Regen rate is stable whether the sim clock is fast or slow (sim-time paced).
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green;
  `write-conflicts {}`.

## Risks
Tuning the burn/coast rhythm so it feels intentional, not punishing — live-tune.
Hysteresis band width (too narrow → flicker). Don't add a second coherence writer;
fold into `apply-coherence`. Sim-time pacing (see skill).

## Dependencies
Card 2 (thrust system reads coherence to clamp output). Benefits from card 3 (FA)
and card 4 (boost key). Feeds card 10 (HUD taper/lockout zones).
