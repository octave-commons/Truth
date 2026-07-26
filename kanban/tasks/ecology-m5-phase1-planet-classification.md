---
category: "specs"
labels: ["fix", "phase0", "chemistry", "handoff", "epic-ecology-water-gate-snowline"]
write-id: "1784745903016-0.gwjmpr1ctx77k39o32l"
source: "kanban/tasks/ecology-m5-phase1-planet-classification.md"
title: "M5 Handoff Phase 1: material + thermal classification"
priority: "P2"
status: "done"
estimate: "5"
uuid: "ecology-m5-phase1-planet-classification"
created_at: "2026-07-10T00:00:00Z"
---

# M5 Handoff Phase 1: material + thermal classification

> Parent spec: `kanban/tasks/ecology-water-gate-snowline.md` (§3, §6 Phase 1)
> Parent kanban: `kanban/tasks/ecology-water-gate-snowline.md`

Smallest step of the M5 handoff: make planet categories explicit and testable
from composition and two-body temperature. No orbit integration, no atmosphere
physics. This also **replaces the trivially-satisfied scalar water gate** that
gives this epic its name — a thermal band + composition class is a real gate,
where `habitability-score > 0.2` was not.

**Scope:**
- Add `domain.stellar/material-class` (pure): `:rocky | :icy | :gaseous | :mixed`
  from derived `domain.chemistry/bulk-categories` (`:metal`+`:rock` vs H/He vs
  ices/volatiles) and mass, per parent §3.1.
- Add `domain.stellar/thermal-band` (pure): `T_eff` from stellar `L` and orbital
  `a` with a coarse composition albedo, bucketed per parent §3.2.
- Extend `classify-system` to write `:component/material-class` and
  `:component/thermal-band` as a Jacobi fan-out emitter (single writer for those
  component types).
- Schemas in `law/` for both components.

**Done when (plus global DoD):**
- Tests: `rocky-planet-classified-by-composition`,
  `gas-giant-classified-by-hydrogen`, `thermal-band-computed-from-orbit`.
- Single-writer preserved; `reg/write-conflicts` empty; `architecture-test` green.

---
Triage 2026-07-10: scoped 5pt, clear pure-function + fan-out emitter scope. Ready for implementation.

Triage 2026-07-22 (Claude, focus decision from Aaron): this is the head of the M5 handoff chain and the next implementation focus. Dispatched a Sonnet implementation agent following schema->failing-test->impl: pure material-class + thermal-band, two new components written by classify-system as a single-writer fan-out emitter, law/ schemas. Moving ready -> in_progress. On green (full suite + architecture-test + reg/write-conflicts empty) it advances to review. Unblocks phase2 (orbit-stability) and phase3 (atmosphere) which then unblock phase4 (handoff-event).

Implementation complete + independently verified 2026-07-22 (Claude). Sonnet impl agent + orchestrator re-run: domain.stellar-classification-test 6 tests/12 assertions green; architecture-test 6/23 green; full suite 648/13475 (was 642/13463) 0 failures; reg/write-conflicts {} confirmed via REPL. Diff scoped to src/ + test/ only. Landed: pure material-class + equilibrium-temperature + thermal-band in domain.stellar.classifier (uses domain.chemistry/bulk-categories, no invented :metals key); new :classification fan-out emitter as SOLE writer of :component/material-class + :component/thermal-band, registered in ecs/registry and wired into physics-formation-systems; law/stellar schemas + facade re-export. Sound deviations: added a NEW classification-system rather than overloading the dead legacy classify-system (per CLAUDE.md 'new system not overload'); functions homed in domain.stellar.classifier (no domain.stellar facade exists); orbital 'a' = current star-body distance since this phase is 'no orbit integration'. in_progress -> done. Unblocks phase2 + phase3.
---