---
category: "specs"
labels: ["specs", "phase0", "phase1", "player", "narrowing", "epic-the-first-narrowing"]
write-id: "1784749024823-0.qyw492wf12o042e3ry"
source: "kanban/tasks/narrowing-binding-mechanic.md"
title: "Narrowing A: gravitational binding coupling + cost curve"
priority: "P1"
status: "blocked"
estimate: "5"
uuid: "narrowing-binding-mechanic"
created_at: "2026-07-22T00:00:00Z"
---

# Narrowing A: gravitational binding coupling + cost curve

> Parent epic: `kanban/tasks/the-first-narrowing-star-to-planet.md`
> Design: `docs/designs/the-first-narrowing-star-to-planet.md` §2.
> Blocked on: `phase-0-player-focus-b-focus-zone-system` (promotion/demotion).

**Goal:** Make "binding" a real continuous coupling between the observer and a
candidate world — the mechanical substance of becoming gravitationally bound.

## Scope

- Add `:component/binding` on the observer: a map `{world-eid -> binding∈[0,1]}`.
- Pure `domain.narrowing/binding-step`: accrues while the observer's
  attention-shell immediate radius overlaps a candidate world and Focus (`Q`) is
  sustained; rate scales with habitability/resolution + Resonance-in-world;
  decays slowly when attention is elsewhere (sticky).
- Cost curve keyed to the world's potential-well depth (M5 surface gravity):
  deeper binding → lower Nudge/Perturb cost on that world; higher Release/Widen
  (`R`) Agency cost, scaling like an escape-energy proxy. `R` is free at
  binding≈0, expensive near capture.
- Emit as a fan-out emitter (sole writer of `:component/binding`); wire into the
  tick. Binding deepening drives which world stays `:immediate` vs demotes.
- Schema in `law/`.

## Done when

- `binding-step` pure + tested (accrual on sustained overlapping Focus; decay;
  monotone cost curve vs. binding).
- Single-writer preserved; `reg/write-conflicts` `{}`; `architecture-test` green.
- `clojure -M:test` green.

---
Created 2026-07-22 (Claude): child A of The First Narrowing.

Design decisions 2026-07-22 (Aaron): (1) binding cost curve = LITERAL shape from the world's escape-energy proxy (M5 surface gravity/mass), tuned scale only — heavier/denser worlds genuinely harder to leave; (2) pre-capture un-binding leaves a SMALL SUNK COST / world-line scar (spent Agency), so binding is a real decision; (3) TWO WORLDS: binding to one actively DECAYS binding to others — attention is zero-sum, you can only fall one way. Bake these into binding-step + cost curve.
---