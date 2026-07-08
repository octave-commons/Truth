---
uuid: "static-analysis-jscpd-reporting"
title: "Fix jscpd reporting in bin/analyze"
status: "done"
priority: "P1"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "tooling"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-jscpd-reporting.md"
category: "specs"
estimate: 1
---

# Fix jscpd reporting in `bin/analyze`

> Original spec: `kanban/tasks/spec-fix-jscpd-reporting-in-bin-analyze.md`

The `jscpd` section of `bin/analyze` used to print blank `Clone found (clojure):` headers. The script now captures jscpd output and prints file paths, line ranges, token counts, and summary statistics.

**Implementation:** applied to `bin/analyze` in this session. Verify with `bin/analyze`.
