---
uuid: "ecology-water-gate-snowline"
title: "Ecology water/habitability gate is trivially satisfied"
status: "in_progress"
priority: "P2"
labels: ["fix", "phase0", "chemistry", "handoff"]
created_at: "2026-07-06T16:21:51.000000000Z"
source: "docs/specs/phase0-habitability-handoff.md"
category: "fix"
---

# Ecology water/habitability gate is trivially satisfied

> Milestone M4. Spec: `docs/specs/phase0-habitability-handoff.md`; nebular-chemistry-realspec (bulk categories).

`ecology/moisture-from-composition` (`ecology.clj:383`) sums raw `:H + :O + :H2O + :volatiles + :ices` mass fractions. The legacy molecule keys are never present, and counting raw `:H` (~0.75 in any parcel) means "moisture" is dominated by hydrogen gas, not condensed water. Likewise `chemistry/habitability-score` (`chemistry.clj:257`) `has-water` fires whenever `O+H > 0.01`, i.e. for essentially every body.

**Fix:** gate liquid-water plausibility on the condensed `:ice` bulk category (`bulk-categories`) **and** a temperature band (snow-line / liquid-water range), not raw element sums. Depends on M2 (bulk categories) and M1 (metals/O actually present).

**Done when:** a hot metal-poor body scores no water; a temperate body with condensed H₂O scores water; the gate no longer fires for a pure H/He gas parcel.

**Progress (2026-07-06):** the ecology *adoption* gate (`ecology/planet-habitable?`) was realigned to the ecology's own 225–375 K band + `moisture>0.1` (was `chemistry/habitability-score>0.2`, needing 273–373 K + pressure). This unblocked end-to-end biogenesis (confirmed tick 7248). **Remaining:** `moisture-from-composition` still sums raw `:H`+`:O` rather than the condensed `:ice` bulk category — gate it on condensed water + a liquid-water temperature band.
