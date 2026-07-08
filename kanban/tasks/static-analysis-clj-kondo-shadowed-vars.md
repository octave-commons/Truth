---
uuid: "static-analysis-clj-kondo-shadowed-vars"
title: "clj-kondo: shadowed vars & idiom cleanup"
status: "accepted"
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
