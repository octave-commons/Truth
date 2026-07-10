---
uuid: "static-analysis-final-validation"
title: "Final Validation and Gating for Structural Cleanup"
status: "blocked"
priority: "P2"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "architecture"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-final-validation.md"
category: "specs"
estimate: 1
---

# Final Validation and Gating for Structural Cleanup

> Parent spec: `kanban/tasks/static-analysis-structural-cleanup.md`
> Parent kanban: `kanban/tasks/static-analysis-structural-cleanup.md`

Run the final validation gate for the structural cleanup. Ensure all child tasks have driven the structural-smell findings to zero and that architecture invariants still hold.

**Scope:**
- Run `bin/analyze --strict` and confirm zero HARD structural breaches.
- Run `clojure -M:test` (full suite) and confirm green, including `test/architecture_test.clj`.
- Run `bin/bench` on any hot paths touched by the child tasks and compare against the pre-cleanup baseline.
- Optionally tighten `dev/smell_report.clj` thresholds if desired.
- Update `docs/STATIC-ANALYSIS.md` with the new namespace map, threshold policy, and intentional exceptions (e.g., `domain.ecs.components`, `domain.genesis` fan-out).
- Verify every removed or moved public API has a `^:deprecated` alias during transition.

**Done when:**
- `bin/analyze --strict` passes.
- `clojure -M:test` is green.
- `bin/bench` shows no regression on hot paths.
- `docs/STATIC-ANALYSIS.md` reflects the final state.

---
Triage 2026-07-10: capstone, BLOCKED. bin/analyze --strict already passes (no blocking findings), but the epic's intent (zero warnings) is unmet: splint 18, dead-code 312, cljfmt 7 remain. Close only after those + splint-final-gate.

Triage 2026-07-10: capstone blocked; cannot close until splint warnings, dead-code findings, and cljfmt files are cleared. Status set to blocked.
---
