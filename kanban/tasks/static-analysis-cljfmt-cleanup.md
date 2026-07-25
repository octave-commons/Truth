---
category: "specs"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "formatting"]
write-id: "1784985267380-0.smnrkcj2yubbpe7c6c"
source: "kanban/tasks/static-analysis-cljfmt-cleanup.md"
title: "cljfmt formatting cleanup"
priority: "P2"
status: "done"
estimate: "2"
uuid: "static-analysis-cljfmt-cleanup"
created_at: "2026-07-07T00:00:00Z"
---

# cljfmt Formatting Cleanup

> Original spec: `kanban/tasks/spec-cljfmt-formatting-cleanup.md`

Run `bin/analyze --fix` once the clj-kondo and Splint mechanical passes are stable, then keep `clojure -M:cljfmt check src test` green.

**Blocked by:** `static-analysis-clj-kondo-cleanup`, `static-analysis-splint-idiom-cleanup` (to avoid formatting churn on code that is about to change).

---
Triage 2026-07-10 (accepted→ready): OPEN, ~2pt. cljfmt reports 7 files needing formatting (e.g. src/infra/render/window.clj, actor_dashboard.clj) — pre-existing drift. Straight 'bin/analyze --fix' pass, scoped to those 7 files.
---

---
Triage 2026-07-24 — SUPERSEDED by `kanban/tasks/static-analysis-cljfmt-2026-07.md`. Two corrections: the count is **24 files, not 7** (regression since 2026-07-10), and this card is a **duplicate of `spec-cljfmt-formatting-cleanup.md`** (which states 2 files) — both describe the same single `bin/analyze --fix` pass. Folded into the one successor so a future reader does not run the pass twice against two stale counts. Critically, the successor also fixes the sequencing: at least 8 of the 24 files are rewritten by the Wave 1-3 structural/lint work, so the format pass must run LAST or it is pure churn.
---

---
## Superseded / folded (2026-07-25)

Folded into `kanban/tasks/static-analysis-cljfmt-2026-07.md`, which did the work.
This card and `spec-cljfmt-formatting-cleanup.md` described the SAME formatting pass
under two uuids — the duplication is recorded in
`kanban/tasks/static-analysis-regression-2026-07-24.md` §Wave 0.

`clojure -M:cljfmt check src test` now passes and cljfmt is a BLOCKING tool in
`bin/analyze`.
---