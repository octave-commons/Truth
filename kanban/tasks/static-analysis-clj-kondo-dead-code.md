---
uuid: "static-analysis-clj-kondo-dead-code"
title: "clj-kondo: dead private code removal"
status: "done"
priority: "P2"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-clj-kondo-dead-code.md"
category: "specs"
estimate: 1
---

# clj-kondo: Dead Private Code Removal

> Parent spec: `kanban/tasks/static-analysis-clj-kondo-cleanup.md`  
> Parent kanban: `kanban/tasks/static-analysis-clj-kondo-cleanup.md`

Remove genuinely unused private vars and imports that survived the correctness pass, or document why they must stay.

**Scope:**
- Delete or suppress 7 `unused-private-var` findings (`infra.render` ×3, `domain.genesis`, `domain.gravity.barnes-hut`, `domain.integrator`).
- Clean up any remaining `unused-import` or `unused-referred-var` findings left over from M1.1.

**Done when:**
- `clj-kondo --lint src test` reports zero `unused-private-var` findings.
- Any kept private var has a docstring explaining why and a `{:clj-kondo/ignore [:unused-private-var]}` annotation.
- `clojure -M:test` is green.

---
Triage 2026-07-10 (accepted→done): DONE per bin/analyze ground truth — clj-kondo 0 errors / 0 warnings.
---
