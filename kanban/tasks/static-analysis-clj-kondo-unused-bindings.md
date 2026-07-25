---
uuid: "static-analysis-clj-kondo-unused-bindings"
title: "clj-kondo: unused bindings & values cleanup"
status: "done"
priority: "P2"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-clj-kondo-unused-bindings.md"
category: "specs"
estimate: 5
---

# clj-kondo: Unused Bindings & Values Cleanup

> Parent spec: `kanban/tasks/static-analysis-clj-kondo-cleanup.md`  
> Parent kanban: `kanban/tasks/static-analysis-clj-kondo-cleanup.md`

Clean up the largest visible noise category: unused local bindings, discarded values, and one incorrectly underscored binding.

**Scope:**
- Remove or rename 46 `unused-binding` findings across `src/` and `test/`.
- Fix 10 `unused-value` findings (mostly stray string literals in `infra.render` that should be docstrings or deleted, plus test comments).
- Fix 1 `used-underscored-binding` in `domain.gravity.barnes-hut` by removing the leading underscore.

**Done when:**
- `clj-kondo --lint src test` reports zero `unused-binding`, `unused-value`, and `used-underscored-binding` findings.
- Bindings intentionally kept for shape are renamed with `_` prefix and documented.
- `clojure -M:test` is green.

---
Triage 2026-07-10 (accepted→done): DONE per bin/analyze ground truth — clj-kondo 0 errors / 0 warnings.
---

---
Regression notice 2026-07-24 — this card remains `done` (history is not rewritten), but **its finding has returned**. 5 unused-binding warnings are back: `src/domain/interior.clj:463` (`kind`), `src/domain/voxel/band.clj:89`, `src/domain/voxel/carve.clj:322`, `src/domain/voxel/sculpt.clj:362`, `test/domain/pilot_resolve_seam_test.clj:200`. Note `interior.clj:463` must NOT be underscore-prefixed — the caller computes it and `law.voxel/resource-cell-schema` has no `:kind` key, which is the "coded but never ticked" case. New work: `kanban/tasks/static-analysis-regression-2026-07-24.md`. Do not read this `done` as evidence the tree is clean; verify with `bin/analyze --strict`.
---
