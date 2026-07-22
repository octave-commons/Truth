---
category: "specs"
labels: ["specs", "phase0", "phase1", "player", "narrowing", "epic-the-first-narrowing"]
write-id: "1784752417035-0.rcmlu6oabhmjwhpgmqq"
source: "kanban/tasks/narrowing-binding-mechanic.md"
title: "Narrowing A: gravitational binding coupling + cost curve"
priority: "P1"
status: "done"
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

Triage 2026-07-22 (resumed session): Player Focus epic COMPLETE (A+B+C committed f5dc0f5/0dedd69/8cdb728, conservation proven) — blocker cleared. Dispatching impl agent for binding coupling + cost curve. blocked -> in_progress.

Complete + independently verified 2026-07-22 (resumed session). narrowing-test 11 tests green; full suite 687/13589 (was 676/13561) 0 failures; architecture green; write-conflicts {}. Landed: c/binding + c/binding-scar on observer (sole-writer :binding fan-out system wired after :focus-zone); pure domain.narrowing/binding-step (accrue on sustained overlapping focus [focus-position within immediate-r + focus-intensity>=0.5], sticky decay elsewhere, zero-sum decay of unfocused bound worlds, permanent sunk-cost scar); literal GM/R escape-energy cost curves (nudge-cost falls, release-cost rises with binding; heavier worlds costlier to leave — Aaron's 3 decisions baked in); law.narrowing schemas + tuned rates. GAPS for later cards: Focus(Q)/Nudge/Release(R) verbs + Agency-spend wiring uncalled; habitability/resonance parameterized neutral; binding not yet fed to promotion/demotion. in_progress -> done. Unblocks narrowing-commitment-horizon.
---