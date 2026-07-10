---
uuid: "phase-0-player-focus-promotion-demotion"
title: "Phase 0 Player Focus Promotion & Demotion"
status: "in_progress"
priority: "P1"
labels: ["specs", "phase0", "player"]
created_at: "2026-07-10T12:00:00Z"
source: "kanban/tasks/phase-0-player-focus-promotion-demotion.md"
category: "specs"
estimate: 5
---

# Phase 0 Player Focus Promotion & Demotion

> Parent: `kanban/tasks/phase-0-player-focus-dual-representation-spec.md`
> Scope: the unbuilt promotion/demotion + conservation invariants from the
> parent spec. Assumes `domain.lod` tick-cadence LOD already exists.

**Goal:** When the player's focus enters a statistical region, promote it to
resolved ECS entities; when focus withdraws, demote resolved matter back to a
statistical cell. Conserve mass, momentum, angular momentum, magnetic flux, and
energy budget.

## Scope

1. Add `component/field-zone` (`:immediate`, `:regional`, `:global`) and
   `component/statistical-mass`.
2. Add `component/attention-shell` on the observer singleton
   (`{:immediate-r m :regional-r m}`).
3. Add `law.field/statistical-cell-schema` and
   `law.field/promotion-invariant?` validator.
4. Implement `domain.genesis/promotion-system` (Phase 1 of parent).
   - Scan regional cells overlapping the immediate focus radius.
   - Sample resolved particles from each overlapping cell conserving mass,
     COM velocity, and angular momentum.
   - Mark spawned particles `:immediate`; debit mass from the regional ledger.
5. Implement `domain.genesis/demotion-system` (Phase 2 of parent).
   - Runs after all physics and event systems.
   - Particles outside the immediate zone with no recent threshold events are
     aggregated into their containing regional cell.
   - Write back angular momentum, magnetic flux, and energy using the cell's
     moment of inertia and mean field.
6. Verify conservation invariants via tests and `law.field/promotion-invariant?`.

## Tests

- `promotion-conserves-mass`: promote a 1e27 kg regional cell; immediate-zone
  mass increases by exactly that amount.
- `promotion-conserves-momentum`: spawned clumps have the same net momentum as
  the source cell.
- `promotion-conserves-angular-momentum`: net L of spawned clumps equals source
  cell L.
- `demotion-conserves-mass`: resolved particles removed and cell mass increases
  by their total.
- `demotion-preserves-ledger`: events emitted by resolved particles remain in
  the ledger.
- `demotion-threshold-events-delay`: particles involved in a recent collision
  or ignition are not demoted until the event is processed.
- `promotion-invariant-validator`: `law.field/promotion-invariant?` returns
  true for a valid promotion and false for a violated one.

## Out of scope

- Regional/global sub-cycling beyond the existing `domain.lod` work (Phase 3).
- Statistical stellar mechanics (Phase 4).
- Coherence cost coupling (Phase 5).
- Rendering changes beyond using the existing particle renderer for promoted
  clumps.

## Done when

- Promotion and demotion systems are wired into the tick pipeline.
- All conservation tests pass.
- `clojure -M:test` green.
- `test/architecture_test.clj` passes.
- No new `reg/write-conflicts` from the new systems.
- Parent card updated with link to this residual card.

---
Started 2026-07-10: moving to in_progress. Will inspect current LOD/focus code, then implement promotion/demotion + conservation invariants.
---
