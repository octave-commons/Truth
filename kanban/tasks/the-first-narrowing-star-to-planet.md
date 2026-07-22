---
uuid: "the-first-narrowing-star-to-planet"
title: "Epic: The First Narrowing (star-system → planet)"
status: "breakdown"
priority: "P1"
labels: ["specs", "phase0", "phase1", "player", "narrowing", "epic"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/the-first-narrowing-star-to-planet.md"
category: "specs"
estimate: 13
---

# Epic: The First Narrowing (star-system → planet)

> Canonical design: `docs/designs/the-first-narrowing-star-to-planet.md`
> Sits on: `phase-0-player-focus-promotion-demotion` epic (promotion/demotion
> substrate) — PREREQUISITE.
> Doorway: `ecology-water-gate-snowline` (M5 `:planet-candidate` record).

**Goal:** Make the Phase 0 → Phase 1 transition a *felt, gradual, physical*
narrowing — the player falls into one world's gravity well over sustained
attention and chooses to be captured, without it feeling like a new game. This
is the first rung of the master arc and the template for every later narrowing.

**The new mechanic:** gravitational **binding** as a continuous observer↔world
coupling (design §2). Binding accrues from sustained Focus, makes acting on the
world cheaper and withdrawing costlier (Release scales like escape-energy), and
drives the promotion/demotion machinery so the rest of the system visibly
demotes to statistical fields. **Commitment** becomes the horizon binding
crosses (capture), not a button — emitting the canonical `:event/world-commitment`
as a felt threshold event, re-arming the six hotbar slots to the planetary
palette, and engaging planetary time-lock.

## Child slices (dependency order)

| Child | Est | Covers | Status |
|---|---|---|---|
| narrowing-binding-mechanic | 5 | `:component/binding`; accrue/decay from Focus; cost curve (act-cheaper / withdraw-costlier ∝ well depth) | blocked (needs Player Focus B) |
| narrowing-commitment-horizon | 5 | capture threshold → `:event/world-commitment`; palette re-arm; planetary time-lock | blocked (needs binding) |
| narrowing-frame-handoff | 3 | camera tether/auto-frame; sky-simplification cue; ambient narrator line at capture | blocked (needs commitment-horizon) |

## Done when

- The transition plays as the design's §6 choreography, felt not announced.
- Binding is a real conserved-feeling coupling on top of promotion/demotion;
  no new `reg/write-conflicts`; `architecture-test` green.
- `:event/world-commitment` fires from capture, not a modal; palette re-arms in
  place; planetary time-lock engages.
- Open design questions in the design doc §8 resolved with the owner.

---
Created 2026-07-22 (Claude): manifested from Aaron's vision ("smooth gradual
transition that feels like a decision; gravitationally bound; gets sculpting
abilities"). Sequenced after the Player Focus rewrite (shared substrate).
---
