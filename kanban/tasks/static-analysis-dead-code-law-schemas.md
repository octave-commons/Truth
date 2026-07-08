---
uuid: "static-analysis-dead-code-law-schemas"
title: "Dead code cleanup: law.* contracts and schemas"
status: "accepted"
priority: "P2"
estimate: 5
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-dead-code-law-schemas.md"
category: "specs"
---

# Dead code cleanup: law.* contracts and schemas

Parent: `kanban/tasks/static-analysis-dead-code-cleanup.md` (dead-code epic)

Scope: Phase 4 of the dead-code cleanup spec.

- Triage the 67 unused public vars across `law.*` namespaces (`law.sed`, `law.ledger`, `law.stellar`, `law.mass-transfer`, `law.composition`, `law.ecology`, `law.field`, `law.system-specs`, `law.plasma`).
- Delete genuinely orphaned helpers, constants, and duplicated contracts.
- Mark intended public schemas and contracts as `^:api` with docstrings.
- Make internal helpers `^:private` where appropriate.
- Update namespace docstrings if public API surface changes.

Done when:
- `clojure-lsp diagnostics | grep unused-public-var | grep 'src/law/'` returns nothing or only documented `^:api` surface.
- `clojure -M:test` is green.
