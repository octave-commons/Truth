---
category: "specs"
labels: ["specs", "phase1", "player", "narrowing", "epic-the-first-narrowing"]
write-id: "1784749025351-0.aib02xecz7ai898mg5d"
source: "kanban/tasks/narrowing-commitment-horizon.md"
title: "Narrowing B: commitment horizon (capture → world-commitment)"
priority: "P1"
status: "blocked"
estimate: "5"
uuid: "narrowing-commitment-horizon"
created_at: "2026-07-22T00:00:00Z"
---

# Narrowing B: commitment horizon (capture → world-commitment)

> Parent epic: `kanban/tasks/the-first-narrowing-star-to-planet.md`
> Design: `docs/designs/the-first-narrowing-star-to-planet.md` §3, §4.
> Blocked on: `narrowing-binding-mechanic`.

**Goal:** Turn Commitment from a menu button into a crossed horizon. When
binding crosses capture AND `domain.arc/ready-to-narrow?` holds, commit — felt,
not prompted.

## Scope

- Capture predicate: `binding >= capture-threshold` (≈0.85) and
  `ready-to-narrow?`. Crossing emits the canonical `:event/world-commitment`
  (`commitment-and-resonance.md` §4.2) as a threshold event.
- On capture: unallocate Genesis Resonance and re-arm the six hotbar slots to
  the Phase 1 planetary palette (Atmosphere/Hydrography/Tectonics/Orbit/
  Biosphere/Culture) IN PLACE; carry Resonance over. Mark unchosen worlds
  non-interactive.
- Engage planetary time-lock (`commitment-and-resonance.md` §5.1): base tick →
  1 s/s for the committed world's immediate neighborhood; rest sub-cycled.
- Irreversible for the world-line. (Pre-capture withdrawal handled by the
  binding cost curve — resolve design §8.2 sunk-cost question with owner.)

## Done when

- Capture fires `:event/world-commitment` from binding, not a modal.
- Palette re-arms in place; time-lock engages; unchosen worlds inert.
- Tests: capture-emits-commitment-at-threshold; palette-rearms-on-commit;
  no-commit-below-threshold. `architecture-test` green; suite green.

---
Created 2026-07-22 (Claude): child B of The First Narrowing.

Design decision 2026-07-22 (Aaron): pre-capture reversibility carries a small sunk cost/scar (see binding-mechanic card); capture itself remains hard-irreversible for the world-line.
---