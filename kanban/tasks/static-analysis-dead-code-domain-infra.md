---
uuid: "static-analysis-dead-code-domain-infra"
title: "Dead code cleanup: domain.* and infra.* + final verification"
status: "ready"
priority: "P2"
estimate: 5
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-dead-code-domain-infra.md"
category: "specs"
---

# Dead code cleanup: domain.* and infra.* + final verification

Parent: `kanban/tasks/static-analysis-dead-code-cleanup.md` (dead-code epic)

Scope: Phases 5–6 of the dead-code cleanup spec.

- Triage the remaining 44 `domain.*` and 13 `infra.*` findings.
- Decide per-function: delete, privatize, or mark `^:api`.
- Check runtime/dev bindings for `infra.dev.window` and `infra.menu` functions.
- Keep future-phase chemistry/stellar/em helpers as `^:api` if appropriate.
- Run final verification so only documented `^:api` surface remains.
- Update `docs/STATIC-ANALYSIS.md` with the suppression conventions.
- Optional: add a CI step or `bin/analyze` gate.

Done when:
- `clojure-lsp diagnostics | grep unused-public-var | grep -E 'src/domain|src/infra'` returns nothing or only documented `^:api` surface.
- `clojure -M:test` is green.
- `test/architecture_test.clj` still passes.
- `bin/bench` shows no regression if hot-path namespaces were touched.
- `docs/STATIC-ANALYSIS.md` is updated.

---
Triage 2026-07-10: scoped 5pt, clear exit criteria. Ready for implementation.
---
