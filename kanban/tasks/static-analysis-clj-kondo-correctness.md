---
uuid: "static-analysis-clj-kondo-correctness"
title: "clj-kondo: correctness & require hygiene"
status: "done"
priority: "P2"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-clj-kondo-correctness.md"
category: "specs"
estimate: 3
---

# clj-kondo: Correctness & Require Hygiene

> Parent spec: `kanban/tasks/static-analysis-clj-kondo-cleanup.md`  
> Parent kanban: `kanban/tasks/static-analysis-clj-kondo-cleanup.md`

Fix the clj-kondo findings that can hide real bugs or break compilation: `unresolved-namespace`, `redefined-var`, `unused-import`, `unused-namespace`, `unused-referred-var`, `misplaced-docstring`, and the `redundant-nested-call` info finding.

**Scope:**
- Resolve 3 `unresolved-namespace` findings (missing requires in `infra.render.shader`, `test/domain.condensation-seeder-test`, `test/domain.mass-transfer-test`).
- Fix 1 `redefined-var` in `infra.render` (likely caused by misplaced docstring).
- Remove 2 `unused-import` entries in `infra.dev.actor-dashboard`.
- Remove 15 `unused-namespace` requires across `src/` and `test/`, checking for side-effect-only loads.
- Remove 5 `unused-referred-var` entries (unused `testing` referrals in tests).
- Move 7 `misplaced-docstring` strings to the correct position in `defn` forms.
- Simplify 1 `redundant-nested-call` in `law.mass-transfer`.

**Done when:**
- `clj-kondo --lint src test` no longer reports these categories.
- `clojure -M:test` is green.
- The architecture test (`test/architecture_test.clj`) still passes.
- Any kept side-effect require is documented with a suppression and comment.

---
Triage 2026-07-10 (accepted→done): DONE per bin/analyze ground truth — clj-kondo 0 errors / 0 warnings.
---
