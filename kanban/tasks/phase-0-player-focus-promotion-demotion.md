---
category: "specs"
labels: ["specs", "phase0", "player"]
write-id: "1784751643574-0.ruxyldkdgfg9hn7mh2x"
source: "kanban/tasks/phase-0-player-focus-promotion-demotion.md"
title: "Phase 0 Player Focus Promotion & Demotion"
priority: "P1"
status: "done"
estimate: "8"
uuid: "phase-0-player-focus-promotion-demotion"
created_at: "2026-07-10T12:00:00Z"
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

Discovery/plan 2026-07-22 (Claude, read-only plan agent). KEY FINDING: the existing src/domain/genesis/promotion.clj (from 74c04b8, never revisited) is a FALSE START, not near-done. It (a) directly put-components c/matter-state :planetesimal — a SECOND writer of a component the :classifier already owns, which would fail architecture_test's write-conflicts check; (b) uses NO statistical cell at all (the card's tests need e.g. 'promote a 1e27 kg regional cell'); (c) focus-zone-systems is called from nowhere. So item 4-6 need a real rewrite, not wiring.

Schemas/components ARE present (contra assumption): c/field-zone, c/statistical-mass, c/attention-shell (components.clj:170-172); statistical-cell/attention-shell schemas + promotion-invariant? (law/field/schema.clj:156-196); observer carries default attention-shell (player/state.clj:8). Missing entirely: the regional-cell entity concept + spawn-request-promotion/consumed-demote/promoted-from-cell markers.

TARGET DESIGN: one combined :focus-zone fan-out emitter (promotion+demotion MUST be one system/id — both write c/statistical-mass, so two ids conflict). Regional cells are ECS entities with c/statistical-mass + c/field-zone :regional but NO c/matter-state, so they're structurally invisible to gravity/hydro/classifier/integrator (all filter on c/matter-state) — free isolation. Promotion emits spawn-request-promotion (reuse spawn-entity/spawn-clump via materialize-lifecycle); demotion emits consumed-demote + folds credited mass/vel/L into the target cell's statistical-mass in the SAME run. Because debit+spawn and credit+despawn are emitted in one write-set and materialized in the same tick-physics call, totals are conserved at the tick boundary with NO settle window — conservation is unit-testable on one run. Keep the existing threshold-event-delay logic verbatim.

ORDERED TASKS: (S) add 3 components + register in bootstrap spawn/consumed lists; (M) rewrite promotion.clj as single :focus-zone constructor, drop all matter-state/body-kind writes; (S) register :focus-zone in ecs/registry; (S) wire into physics-lifecycle-systems (systems.clj); (M) 7 named tests (field_test.clj / stellar_test.clj patterns; promotion-invariant test is pure, no ECS); (S) full suite + architecture guard. TOP RISKS: double-writer on matter-state (design out, don't legacy-exclude — that silently no-ops); promotion/demotion racing (one id); new spawn/reap must route only through bootstrap lifecycle lists. Optional follow-up: hysteresis margin to stop promote/demote flapping at the immediate-radius boundary.

Re-scope 2026-07-22 (Claude, decision from Aaron): this card is now the EPIC. Discovery found the old promotion.clj is a false start needing a rewrite (see prior plan comment). Split into 3 child slices per the plan; execute the children, retain this as canonical spec + plan.

| Child | Est | Covers | Status |
|---|---|---|---|
| phase-0-player-focus-a-statistical-substrate | 2 | new components (spawn-request-promotion, consumed-demote, promoted-from-cell) + lifecycle-marker registration + regional-cell entity (no c/matter-state) | ready |
| phase-0-player-focus-b-focus-zone-system | 3 | rewrite promotion.clj as single :focus-zone fan-out emitter (promotion+demotion), register + wire, drop matter-state writes | blocked (needs A) |
| phase-0-player-focus-c-conservation-tests | 2 | 7 named tests; pure promotion-invariant test unblocked now, 6 world tests need B | blocked (needs B) |

Dependency order: A -> B -> C. Epic est bumped 5 -> 8 (rewrite, not wiring). in_progress -> breakdown (epic). Sequenced AFTER the M5 handoff chain to avoid file conflicts on components/registry/systems.

Epic COMPLETE 2026-07-22 (resumed session): all 3 children done + committed on m5-ecology-handoff (f5dc0f5, 0dedd69 + this commit). A: domain.field statistical-cell substrate + lifecycle markers. B: :focus-zone fan-out emitter (one combined promotion+demotion system, sole writer, threshold-delay preserved). C: 7 named conservation tests green + latent promotion-invariant? arity bug fixed. Working promotion/demotion with proven conservation — unblocks narrowing-binding-mechanic (first playable Narrowing slice). Closing epic.
---