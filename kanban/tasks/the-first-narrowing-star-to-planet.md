---
category: "specs"
labels: ["specs", "phase0", "phase1", "player", "narrowing", "epic"]
write-id: "1784754416111-0.48txxo1b4tejodv6pj0"
source: "kanban/tasks/the-first-narrowing-star-to-planet.md"
title: "Epic: The First Narrowing (star-system → planet)"
priority: "P1"
status: "done"
estimate: "13"
uuid: "the-first-narrowing-star-to-planet"
created_at: "2026-07-22T00:00:00Z"
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

Epic COMPLETE 2026-07-22 (resumed session): all 3 children done + committed on m5-ecology-handoff (0d08012 binding, d230bca commitment, this commit frame-handoff). The star->planet narrowing is now mechanically real end-to-end: binding accrues under sustained focus (zero-sum, sunk-cost scar, literal GM/R cost curves), capture fires :event/world-commitment at binding>=0.85 + ready (hard-irreversible, palette re-arms in place, unchosen worlds inert, time-lock hook), and the frame tightens continuously into capture with no cut + one ambient narrator line. Open follow-ups for the next ladder rung (planetary-voxel-substrate): time-lock cadence actuation, Agency-spend verb wiring, tether tracking-mode support, cells into froxel volume. Closing epic.

INTEGRATION-DEBT CORRECTION 2026-07-23 (Claude, re-triage after Aaron's field report: "the game doesn't really seem to have any of the narrowing perspective stuff in the UX"). "Mechanically real end-to-end" = DOMAIN/single-writer correctness only (unit tests, no live-loop/HUD integration test). It is NOT felt in play: (1) the binding overlap gate uses the observer's SYSTEM-scale attention shell (~26,700 AU) so binding accrues on every candidate passively with no real targeting; (2) there is NO HUD readout of binding or commitment — grep finds zero; (3) the camera tether only actuates in :manual mode, not the default :fit-all; (4) the "sky simplifies" cue is pre-existing generic regional dimming, not driven by c/commitment-state; (5) no Focus/Nudge/Release verbs are bound to input. New integration-debt cards capture the real work: narrowing-hud-binding-commitment-readout, narrowing-worldscale-overlap-gate, narrowing-tether-default-camera-modes, spark-planet-binding. The three child cards are domain-done but player-invisible — read with that caveat.
---