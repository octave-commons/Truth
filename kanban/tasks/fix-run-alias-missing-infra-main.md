---
uuid: "fix-run-alias-missing-infra-main"
title: "Fix :run alias — src/infra/main.clj is missing"
status: "todo"
priority: "P2"
labels: ["infra", "tooling", "bug"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/fix-run-alias-missing-infra-main.md"
category: "specs"
estimate: 1
---

# Fix :run alias — src/infra/main.clj is missing

> Found during 2026-07-23 re-triage. CLAUDE.md documents `clojure -M:run`
> (Phase 0 console sim) and `clojure -M:run demo` (render one frame to
> /tmp/truth-view.png) as working commands. Both fail.

## Root cause
`deps.edn` `:run` alias is `{:main-opts ["-m" "infra.main"]}`, but
`src/infra/main.clj` does not exist. The only `-main` namespaces are
`infra.dev.actor-dashboard` and `infra.dev.server`. The live window runs via
`clj -M:dev` (pm2), not `:run`. So the documented console/demo entry point is a
`FileNotFoundException`.

## Done when
- `clojure -M:run` runs the Phase 0 console simulation.
- `clojure -M:run demo` renders one frame to /tmp/truth-view.png headlessly
  (the render path works today via `infra.render/render-to-file`; this just
  needs the missing `-main` entry point that dispatches `demo` vs console).
- CLAUDE.md commands section verified accurate, or corrected if the intended
  entry point moved.

## Notes
Low-risk, high-doc-value: it's the difference between the docs being trustworthy
or not for any agent that follows them. Check git history for a deleted
`infra/main.clj` (may be an uncommitted-deletion casualty).
