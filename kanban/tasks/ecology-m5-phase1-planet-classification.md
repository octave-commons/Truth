---
uuid: "ecology-m5-phase1-planet-classification"
title: "M5 Handoff Phase 1: material + thermal classification"
status: "accepted"
priority: "P2"
labels: ["fix", "phase0", "chemistry", "handoff", "epic-ecology-water-gate-snowline"]
created_at: "2026-07-10T00:00:00Z"
source: "kanban/tasks/ecology-m5-phase1-planet-classification.md"
category: "specs"
estimate: 5
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
