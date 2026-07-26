---
category: "specs"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "cleanup"]
write-id: "1784985311388-0.6jb5589zrfgbhfvocx"
source: "kanban/tasks/static-analysis-dead-code-ecs-components.md"
title: "Dead code cleanup: ECS component future-facing vocabulary"
priority: "P2"
status: "done"
estimate: "2"
uuid: "static-analysis-dead-code-ecs-components"
created_at: "2026-07-07T00:00:00Z"
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

---
Triage 2026-07-10: OPEN — domain.ecs.components still has 10 unsuppressed unused-public-var (elements, orbit-ref, force-accum, event-source, sink-identity, biome-cell, civilization, territory, renderable, cell-id) — future-facing vocabulary. Fix = mark ^:api / documented suppression, not delete.

Triage 2026-07-10: scoped 2pt, clear future-facing vocabulary decision. Ready for implementation.
---

---
## Closed by config (2026-07-25)

`domain.ecs.components` is excluded wholesale in `.lsp/config.edn`, keyed to the same
concept `dev/smell_report.clj`'s `vocabulary-namespaces` already uses and to the
`done` card `static-analysis-component-vocabulary-exception.md`. Verified per-var
there: both the var AND its `:component/*` keyword have zero hits outside the defining
file, so nothing reaches them by indirection either — a component keyword with no
consumer yet is a declared noun, not dead code.

Superseded by `kanban/tasks/static-analysis-lsp-config-dead-vars.md`, which took
unused-public-vars to 0.
---