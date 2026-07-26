---
uuid: "narrowing-worldscale-overlap-gate"
title: "Narrowing: world-scale overlap gate (targeting must mean something)"
status: "review"
priority: "P1"
labels: ["domain", "narrowing", "integration-debt"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/narrowing-worldscale-overlap-gate.md"
category: "specs"
estimate: 3
---

# Narrowing: world-scale overlap gate

> Integration-debt card (2026-07-23 re-triage). The binding overlap test is
> sized to the whole inner system, so "focusing on a world" requires no real
> targeting — binding accrues on every candidate at once, passively.

## Root cause (investigation)
`domain.narrowing/focus-overlap?` (`narrowing.clj:87-91`) counts focus as
overlapping a world when `:focus-position` is within the observer's
`(:attention-shell :immediate-r)` = **4.0e15 m (~26,700 AU)**
(`domain/player/state.clj:8`) of it. Planets form at a few AU inside a
`3.0e16 m` nebula. That radius was sized for the unrelated `:focus-zone`
regional-cell test and reused verbatim. With default `:focus-intensity` already
`0.5` (== `focus-intensity-floor`), the gate is satisfied passively for every
candidate simultaneously; full capture in ~43 ticks (<1 s @60 Hz).

## Done when (player-visible)
- Binding accrues only on the world the player is actually pointing focus at:
  overlap radius keyed to the candidate's own scale (a few world/orbit radii, or
  a new `law.narrowing` constant sized to planetary distances), not the
  system-scale attention shell.
- Integration-style test: two simultaneously-visible candidates do **not** both
  accrue when only one is targeted.

## Scope
`domain.narrowing` overlap predicate + a `law.narrowing` constant. Failing test
first (two-candidate discrimination), then minimal change.

## Dependencies
None technically. This is the **domain half of "the spark stays on a planet"**;
pairs with `spark-planet-binding` (the position half) and should land before
`narrowing-tether-default-camera-modes` so the tether has a meaningful single
target.
