---
uuid: "static-analysis-splint-final-gate"
title: "Splint cleanup: final suppression and gating readiness"
status: "rejected"
priority: "P2"
estimate: 1
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-splint-final-gate.md"
category: "specs"
---

# Splint cleanup: final suppression and gating readiness

> Parent: `kanban/tasks/static-analysis-splint-idiom-cleanup.md`

Triage any remaining Splint warnings, add documented suppressions where necessary, and update the static-analysis documentation so Splint becomes a gating tool.

## Scope

- Run `clojure -M:splint` and triage any remaining findings.
- Add inline `#_:splint/disable` suppressions only with a preceding `;; Intentional:` or `;; Suppressed:` comment explaining the reason.
- Consider adding a `.splint.edn` project-wide configuration only for rules that are intentionally disabled globally, and only after explicit discussion.
- Update `docs/STATIC-ANALYSIS.md` to reflect the Splint gating policy and zero-warning target.
- Ensure `bin/analyze` (and `bin/analyze --strict`) reports zero Splint warnings.

## Done when

- `clojure -M:splint` reports zero warnings (or only documented suppressions).
- `bin/analyze` Splint count is zero.
- `clojure -M:test` passes.
- `test/architecture_test.clj` still passes.
- `docs/STATIC-ANALYSIS.md` is updated with the Splint gating policy.

---
Triage 2026-07-10: BLOCKED — splint count is 18, not 0. Depends on the splint remainder + a decision on the 8 catch-throwable (suppress vs refactor). Capstone; keep parked until the remainder closes.

Triage 2026-07-10 → rejected: consolidated into static-analysis-splint-idiom-cleanup (18-warning remainder too small to justify separate cards).
---
