---
uuid: "static-analysis-clj-kondo-shadowed-vars"
title: "clj-kondo: shadowed vars & idiom cleanup"
status: "done"
priority: "P2"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-clj-kondo-shadowed-vars.md"
category: "specs"
estimate: 3
---

# clj-kondo: Shadowed Vars & Idiom Cleanup

> Parent spec: `kanban/tasks/static-analysis-clj-kondo-cleanup.md`  
> Parent kanban: `kanban/tasks/static-analysis-clj-kondo-cleanup.md`

Perform the most invasive local-rename pass after the tree is otherwise stable, plus small idiom cleanups.

**Scope:**
- Rename 43 `shadowed-var` locals across `src/` and `test/` without changing public API names. Focus on hot-path namespaces (`infra.render`, `domain.stellar`, `domain.integrator`).
- Replace 2 `not-empty?` findings in `domain.integrator` with `seq`.
- Supply explicit init values for 2 `reduce-without-init` findings in `test/domain.stellar-test`.
- Replace 2 `redundant-fn-wrapper` findings in `infra.dev.actor-dashboard` with direct function references.

**Done when:**
- `clj-kondo --lint src test` reports zero `shadowed-var`, `not-empty?`, `reduce-without-init`, and `redundant-fn-wrapper` findings.
- Public API names are unchanged; only local bindings are renamed.
- `clojure -M:test` is green.
- A short live dev-window run shows no regressions if a hot-path namespace was touched.

---
Triage 2026-07-10 (accepted→done): DONE per bin/analyze ground truth — clj-kondo 0 errors / 0 warnings.
---

---
Regression notice 2026-07-24 — this card remains `done` (history is not rewritten), but **its finding has returned**. 35 shadowed-var warnings are back — including `src/infra/render/passes.clj:95`, where the shadow has already forced a `clojure.core/name` workaround into live code, and `src/law/atmosphere.clj:91,108`, where the local `species-mass` is a double (kg) while the same-named var at `:21` is a map. New work: `kanban/tasks/static-analysis-regression-2026-07-24.md`. Do not read this `done` as evidence the tree is clean; verify with `bin/analyze --strict`.
---
