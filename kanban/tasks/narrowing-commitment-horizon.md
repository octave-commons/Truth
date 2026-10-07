---
category: "specs"
labels: ["specs", "phase1", "player", "narrowing", "epic-the-first-narrowing"]
write-id: "1791362577640-0.6q5yf3eyukrzg08998j"
source: "kanban/tasks/narrowing-commitment-horizon.md"
title: "Narrowing B: commitment horizon (capture → world-commitment)"
priority: "P1"
status: "done"
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

Triage 2026-07-22 (resumed session): Narrowing A done + committed (0d08012) — binding coupling + cost curves + scar live. Dispatching impl agent for the commitment horizon. blocked -> in_progress.

Complete + independently verified 2026-07-22 (resumed session). commitment-test 8 tests green; full suite 695/13622 (was 687/13589) 0 failures; architecture green; write-conflicts {}. Landed: capture at binding>=0.85 + ready-to-commit? fires :event/world-commitment exactly once (serial emit-threshold post-fold, handoff precedent; canonical §4.2 payload {:world :arc :reason} under :data); c/palette re-armed in place to the 6 Phase 1 planetary slots with Resonance carried over (lives in c/observer, untouched); unchosen worlds marked c/commitment-state :inert; c/time-lock data hook engaged (:base-rate 1.0, neighborhood :immediate, outside :sub-cycled); :committed short-circuits forever (hard-irreversible). GAPS noted in docstrings: ready-to-narrow? unreachable (ns cycle) -> minimal ready-to-commit? world-key + M5 planet-candidate gate; no domain hotbar (palette modeled as data); time-lock cadence actuation later card. in_progress -> done. Unblocks narrowing-frame-handoff.

2026-10-07 native evidence records the existing readiness approximation, without changing this Done5 contract: ordinary natural-flight read0028 tick4429 through0032 tick5346 shows historical candidate1010 ready-to-commit? true while fresh M5 handoff is empty and current dominant-attractor lookup nil. Candidate persistence is explicitly intentional in candidate.clj; narrowing readiness uses allowed arc plus stored record. No binding/commitment was observed, and sparse snapshots cannot establish the exact formation/admission/parent-loss tick or global continuous absence. Evidence: .ημ/diagnostics/natural-flight-profile/RESULT.md and attempt-01-audit.json. This is not a new regression or authorization to delete historical candidacy, change irreversible commitment, or impose fresh M5 as a production prerequisite. A later refinement of current capture habitability needs its own reviewed contract. The next diagnostic will test the actual current readiness predicate and separately report historical admission/current eligibility.

---