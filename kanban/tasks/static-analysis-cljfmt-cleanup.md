---
uuid: "static-analysis-cljfmt-cleanup"
title: "cljfmt formatting cleanup"
status: "accepted"
priority: "P2"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "formatting"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-cljfmt-cleanup.md"
category: "specs"
estimate: 2
---

# cljfmt Formatting Cleanup

> Original spec: `kanban/tasks/spec-cljfmt-formatting-cleanup.md`

Run `bin/analyze --fix` once the clj-kondo and Splint mechanical passes are stable, then keep `clojure -M:cljfmt check src test` green.

**Blocked by:** `static-analysis-clj-kondo-cleanup`, `static-analysis-splint-idiom-cleanup` (to avoid formatting churn on code that is about to change).
