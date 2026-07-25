---
category: "specs"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "cleanup"]
write-id: "1784985314058-0.7i2qltmf6lwtal75fy"
source: "kanban/tasks/static-analysis-dead-code-tooling-dsl.md"
title: "Dead code cleanup: tooling baseline and test DSL suppressions"
priority: "P2"
status: "done"
estimate: "3"
uuid: "static-analysis-dead-code-tooling-dsl"
created_at: "2026-07-07T00:00:00Z"
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

---
Triage 2026-07-10: scoped 3pt, clear baseline + DSL suppression work. Ready for implementation.
---

---
## Closed by config (2026-07-25)

Handled in `.lsp/config.edn`. Two groups, both genuine false positives:

- **14 bench vars** reached only via `(ns-resolve (the-ns ns) 'run)` /
  `'profile-iterations` (`bench.clj:106,178,211`). No static call site exists; the
  names ARE the harness contract, so renaming either breaks `bin/bench` silently.
- **`domain.ecs.rewindable/snapshot`** — a `defprotocol` method, implemented via
  `extend-type`/`reify`.

**Correction:** the DSL-generated vars could NOT be excluded the intended way.
`:exclude-when-defined-by #{domain.ecs.dsl/defcomponent domain.ecs.dsl/defevent}` has
no effect, because `.clj-kondo/hooks/ecs_dsl.clj` rewrites those macros into plain
`def`/`defn` and the analysis records `:defined-by clojure.core/def`. The three DSL
test namespaces are excluded wholesale instead, with that cost stated in the file.

Superseded by `kanban/tasks/static-analysis-lsp-config-dead-vars.md`.
---