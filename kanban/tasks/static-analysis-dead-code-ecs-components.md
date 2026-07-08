---
uuid: "static-analysis-dead-code-ecs-components"
title: "Dead code cleanup: ECS component future-facing vocabulary"
status: "accepted"
priority: "P2"
estimate: 2
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-dead-code-ecs-components.md"
category: "specs"
---

# Dead code cleanup: ECS component future-facing vocabulary

Parent: `kanban/tasks/static-analysis-dead-code-cleanup.md` (dead-code epic)

Scope: Phase 3 of the dead-code cleanup spec.

- Triage the 13 unused component keywords in `domain.ecs.components`.
- Keep reserved vocabulary for Phase 1+ and mark with `^:api` + docstring.
- Delete any keyword that is genuinely redundant or abandoned.
- Update the spec to record the final kept/deleted list.

Done when:
- `clojure-lsp diagnostics | grep unused-public-var | grep 'domain.ecs.components'` returns nothing or only documented `^:api` surface.
- `clojure -M:test` is green.
- `test/architecture_test.clj` still passes.
