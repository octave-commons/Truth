---
uuid: "static-analysis-clj-kondo-verification"
title: "clj-kondo: final verification & suppression lock-in"
status: "done"
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

---
Triage 2026-07-10 (accepted→done): DONE per bin/analyze ground truth — clj-kondo section empty (0/0); suppressions locked.
---

---
Regression notice 2026-07-24 — this card remains `done` (history is not rewritten), but **its finding has returned**. Verification did not hold. clj-kondo went from 0/0 to 50 warnings, and CI has been red continuously since 2026-07-11 without anyone acting on it. New work: `kanban/tasks/static-analysis-regression-2026-07-24.md`. Do not read this `done` as evidence the tree is clean; verify with `bin/analyze --strict`.
---
