---
uuid: "ci-coverage-workflow-broken"
title: "The `coverage` CI check has been failing on a missing cloverage dependency"
status: "ready"
priority: "P2"
labels: ["specs", "ci", "tooling", "testing"]
created_at: "2026-07-25T00:00:00Z"
source: "kanban/tasks/ci-coverage-workflow-broken.md"
category: "specs"
estimate: 2
---

# `coverage` CI fails: `ClassNotFoundException: TablesExtension`

Found 2026-07-25 while landing
`kanban/tasks/static-analysis-regression-2026-07-24.md`. Not caused by that work —
it fails identically on `main` and on `spark-gravity-bound-body`.

## The failure

`.github/workflows/coverage.yml` → "Coverage report" step:

```
Execution error (ClassNotFoundException) at java.net.URLClassLoader/findClass
  (URLClassLoader.java:445).
org.commonmark.ext.gfm.tables.TablesExtension
:cause "org.commonmark.ext.gfm.tables.TablesExtension"
```

A missing transitive dependency, not a coverage threshold. Cloverage's Markdown
reporter wants `com.atlassian.commonmark/commonmark-ext-gfm-tables` (or the newer
`org.commonmark/commonmark-ext-gfm-tables`) on the classpath and it is not in the
`:coverage` alias.

## Why it matters

`coverage` is **not** a required status check, so it does not block merges — which
is precisely why it has been able to sit red. Same shape as the failure the
static-analysis epic just closed: a check that runs, fails, and is ignored.

Either fix it or delete the workflow. A permanently-red non-required check trains
everyone to ignore the checks list, which is what let `static-analysis` stay red
from 2026-07-10 to 2026-07-21.

## Likely fix

Add the missing artifact to the `:coverage` alias in `deps.edn`, or pin cloverage
to a version whose reporter deps resolve. Reproduce locally first:

```bash
bin/coverage --text
```

Check whether it fails the same way locally — if it does, this is purely a deps
problem and nothing to do with the CI environment.

## Done when

- [ ] `bin/coverage --text` succeeds locally.
- [ ] The `coverage` workflow is green on `main`, or the workflow is deleted with a
      note saying why.
- [ ] If it is kept and made green, decide deliberately whether it should become a
      required check (see `docs/STATIC-ANALYSIS.md` › The gating contract for the
      argument that a non-gating check decays).
