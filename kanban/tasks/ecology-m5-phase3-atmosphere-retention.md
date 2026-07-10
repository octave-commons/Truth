---
uuid: "ecology-m5-phase3-atmosphere-retention"
title: "M5 Handoff Phase 3: atmosphere retention"
status: "ready"
priority: "P2"
labels: ["phase0", "chemistry", "handoff", "epic-ecology-water-gate-snowline"]
created_at: "2026-07-10T00:00:00Z"
source: "kanban/tasks/ecology-m5-phase3-atmosphere-retention.md"
category: "specs"
estimate: 3
---

# M5 Handoff Phase 3: atmosphere retention

> Parent spec: `kanban/tasks/ecology-water-gate-snowline.md` (§4, §6 Phase 3)
> Parent kanban: `kanban/tasks/ecology-water-gate-snowline.md`

First-pass atmosphere class from escape velocity vs thermal velocity, plus the
set of retained species.

**Scope:**
- Add `domain.stellar/atmosphere-class` (pure): `v_esc = sqrt(2 G M / R)` vs
  `v_thermal = sqrt(2 k_B T / μ)`, bucketed `:none | :thin | :substantial |
  :thick` per parent §4.
- Estimate `:retained-species` (H/He gated at ratio > 6; H2O/CO2/N2 at > 3).
- Write `:component/atmosphere-class` and `:component/retained-species` as a
  fan-out emitter (single writer).
- Schemas in `law/`.

**Done when (plus global DoD):**
- Tests: `earth-like-retains-n2`, `moon-like-loses-atmosphere`,
  `gas-giant-retains-h2`.
- Single-writer preserved; `architecture-test` green.

---
Triage 2026-07-10: scoped 3pt, clear retention calculation. Ready for implementation.
---
