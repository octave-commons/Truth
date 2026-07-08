---
uuid: "static-analysis-dead-code-tooling-dsl"
title: "Dead code cleanup: tooling baseline and test DSL suppressions"
status: "accepted"
priority: "P2"
estimate: 3
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-dead-code-tooling-dsl.md"
category: "specs"
---

# Dead code cleanup: tooling baseline and test DSL suppressions

Parent: `kanban/tasks/static-analysis-dead-code-cleanup.md` (dead-code epic)

Scope: Phases 1–2 of the dead-code cleanup spec.

- Capture the current `clojure-lsp diagnostics` output so the baseline is reproducible.
- Add `clj-kondo` / `clojure-lsp` configuration so `^:api` and `^:private` metadata are respected and DSL-generated vars can be suppressed.
- Add a helper script or alias that reports `unused-public-var` counts per namespace.
- Suppress macro-generated false positives in `test/domain/ecs/dsl_test.clj`, `rewind_test.clj`, and `ledger_test.clj` with namespace comments and `clj-kondo` config.
- Run the full test suite and record the baseline green state.

Done when:
- `clojure-lsp diagnostics | grep unused-public-var | grep -E 'dsl_test|rewind_test|ledger_test'` returns nothing.
- The diagnostic count is reproducible and matches the spec table.
- `clojure -M:test` is green.
