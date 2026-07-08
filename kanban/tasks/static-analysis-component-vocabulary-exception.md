---
uuid: "static-analysis-component-vocabulary-exception"
title: "Component Vocabulary Exception and Docstrings"
status: "done"
priority: "P2"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "architecture"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-component-vocabulary-exception.md"
category: "specs"
estimate: 1
---

# Component Vocabulary Exception and Docstrings

> Parent spec: `kanban/tasks/static-analysis-structural-cleanup.md`
> Parent kanban: `kanban/tasks/static-analysis-structural-cleanup.md`

Treat `domain.ecs.components` as a documented structural exception: it remains a single vocabulary namespace, but every public component keyword must carry a docstring, and the smell detector must exempt vocabulary namespaces from the public-var HARD threshold.

**Scope:**
- Add docstrings to every component keyword definition in `src/domain/ecs/components.clj`.
- Extend `defcomponent` to emit docstrings if it does not already.
- Update `dev/smell_report.clj` to exempt vocabulary namespaces from the public-var HARD threshold (but not LOC threshold).
- Document the exception and policy in `docs/STATIC-ANALYSIS.md`.
- Do not split `domain.ecs.components` into sub-modules as part of this task.

**Done when:**
- `domain.ecs.components` has zero undocumented public vars.
- `dev/smell_report.clj` recognizes vocabulary namespaces as a structural exception.
- `bin/analyze` no longer flags `domain.ecs.components` as a public-var HARD breach.
- `test/architecture_test.clj` still passes.
