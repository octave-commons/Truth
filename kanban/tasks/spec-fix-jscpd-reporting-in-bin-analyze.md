---
uuid: "spec-fix-jscpd-reporting-in-bin-analyze"
title: "Spec: Fix jscpd Reporting in `bin/analyze`"
status: "todo"
priority: "P1"
labels: ["specs"]
created_at: "2026-07-08T02:24:29.826122060Z"
source: "kanban/tasks/spec-fix-jscpd-reporting-in-bin-analyze.md"
category: "specs"
---

# Spec: Fix jscpd Reporting in `bin/analyze`

**Parent epic:** `kanban/tasks/epic-static-analysis-cleanup.md` (Phase A — M0 Tooling honesty)  
**Status:** draft  
**Scope:** make the `jscpd` section of `bin/analyze` print actionable duplication details instead of blank `Clone found (clojure):` lines.

***

## 1. The problem

`bin/analyze` ran:

```bash
npx --yes jscpd@4 2>/dev/null | grep -E 'Clone found|clones|Duplicated' \
  || echo "  jscpd unavailable (needs node/npx)"
```

The `grep` matched only the header line `Clone found (clojure):`; the file-path, line-range, and token-count details that `jscpd` prints on the following lines were discarded. As a result, the report only showed 51 blank headers and gave no way to locate or fix the duplication.

A secondary bug: the `||` branch fired whenever `grep` found no matches, so zero clones or an empty stdout was reported as "jscpd unavailable" even when `npx` and `node` were present.

## 2. The fix

Update the `jscpd` section in `bin/analyze` to:

- Capture `jscpd` stdout and stderr separately.
- If stdout is empty, print the unavailable message and surface the captured stderr (indented).
- If stdout is non-empty, print:
  - clone headers and their indented detail lines (file path, line ranges, duplicated lines/tokens);
  - the `Found N clones.` summary line;
  - a cleaned `Total:` summary showing duplicated lines and duplicated tokens.

Keep the section advisory (non-gating) unless the project later decides to make duplication a blocking failure.

### Sample expected output

```text
━━━ jscpd  (copy-paste duplication)
Clone found (clojure):
 - test/domain/physics/soa_cache_test.clj [230:5 - 238:39] (8 lines, 112 tokens)
   test/domain/physics/soa_cache_test.clj [222:5 - 229:61]
...
Clone found (clojure):
 - src/domain/em.clj [452:18 - 461:17] (9 lines, 171 tokens)
   src/domain/em.clj [83:19 - 92:10]
Found 51 clones.
duplicated lines: 616 (2.48%), duplicated tokens: 7481 (2.4%)
```

## 3. Acceptance criteria

- [ ] Running `bin/analyze` prints the `jscpd` section with file paths, line ranges, and token counts for every clone.
- [ ] Running `bin/analyze` when `jscpd` produces no clones prints the `Found 0 clones.` summary (or equivalent) and no spurious "unavailable" message.
- [ ] Running `bin/analyze` when `node`/`npx` is missing prints a clear "jscpd unavailable" message and any captured stderr.
- [ ] Other sections of `bin/analyze` are unchanged.

## 4. Implementation note

This fix is a small shell-script change. It should be committed as its own change, before the broader duplication-removal work begins, so that subsequent clone-fix PRs can be verified against useful output.

***

## 5. Open questions

1. Should `jscpd` eventually become a blocking check in `bin/analyze --strict`, or should it remain advisory because some duplication is acceptable in test helpers and DSL-generated code?
2. Should the project add a `.jscpd.json` configuration to exclude generated files or test fixtures that are intentionally repetitive?
