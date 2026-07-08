---
uuid: "static-analysis-clj-kondo-verification"
title: "clj-kondo: final verification & suppression lock-in"
status: "accepted"
priority: "P2"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-clj-kondo-verification.md"
category: "specs"
estimate: 1
---

# clj-kondo: Final Verification & Suppression Lock-in

> Parent spec: `kanban/tasks/static-analysis-clj-kondo-cleanup.md`  
> Parent kanban: `kanban/tasks/static-analysis-clj-kondo-cleanup.md`

Drive clj-kondo to zero and lock in the result so the project cannot regress.

**Scope:**
- Run `clj-kondo --lint src test` and `bin/analyze`.
- For any remaining finding, add a `#_{:clj-kondo/ignore [...]}` annotation with a comment explaining the justification.
- Update the suppression inventory table in `kanban/tasks/static-analysis-clj-kondo-cleanup.md`.
- Add a CI note to promote clj-kondo warnings to blocking in `bin/analyze`.

**Done when:**
- `clj-kondo --lint src test` reports zero warnings and zero info (or only documented suppressions).
- `bin/analyze` clj-kondo section is empty.
- `clojure -M:test` is green.
- `bin/bench` is run if a hot-path namespace was touched in earlier phases.
