---
category: "specs"
labels: ["specs"]
write-id: "1784985271864-0.97vmm0zr86nklwuytz1"
source: "kanban/tasks/spec-cljfmt-formatting-cleanup.md"
title: "Spec: cljfmt Formatting Cleanup"
priority: "P1"
status: "done"
uuid: "spec-cljfmt-formatting-cleanup"
created_at: "2026-07-08T02:24:29.818746740Z"
---

# Spec: cljfmt Formatting Cleanup

**Parent epic:** `kanban/tasks/epic-static-analysis-cleanup.md` (Phase B — M1)  
**Status:** draft  
**Scope:** bring the entire `src/` and `test/` tree into cljfmt-compliant formatting, then keep it clean.

***

## 1. The problem

`bin/analyze` currently reports:

```text
━━━ cljfmt  (formatting)
  files need formatting — run: bin/analyze --fix
```

cljfmt is intentionally advisory, but formatting drift makes diffs noisy and can mask real changes in reviews. The project should have a single, machine-enforced formatting style.

## 2. The fix

Apply `clojure -M:cljfmt fix src test` once the other mechanical tools are stable. This is a whitespace-only change; it must not alter semantics.

Because `cljfmt` is the last layer of formatting, it should run **after** the clj-kondo and Splint passes. If those passes introduce new formatting issues, they should be fixed in the same PR or cleaned up in a final formatting pass.

## 3. Acceptance criteria

- [ ] `clojure -M:cljfmt check src test` passes with no files needing formatting.
- [ ] `bin/analyze` prints `all files formatted.` in the cljfmt section.
- [ ] `clojure -M:test` is green after the formatting pass.
- [ ] No semantic changes are introduced; the diff is whitespace/indentation only.

## 4. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| cljfmt changes are noisy in git history | Run it once in a dedicated PR; do not mix with feature work. |
| Formatting breaks macro-heavy forms | Review the diff for DSL macros; revert any bad formatting and add a cljfmt ignore directive. |
| Conflicts with other open branches | Coordinate with active work; prefer running it after larger feature branches land. |

## 5. Open questions

1. Should `bin/analyze` be updated to make cljfmt blocking once the tree is clean, or remain advisory with a periodic `--fix` pass?
2. Should the project add a pre-commit hook that runs `cljfmt check`?

## Estimate

**Story points: 2**

Rationale: Only 2 files (`src/infra/dev/actor_dashboard.clj` and `src/infra/dev/window.clj`) require formatting. The change is a one-time, machine-generated whitespace pass (`clojure -M:cljfmt fix src test`), so implementation effort is minimal. Risk of merge conflicts is low but non-zero because both files sit in the active `infra/dev/` path where UI and actor-dashboard work may be in flight. Two points cover the fix pass, diff review for macro/DSL safety, and test verification.

---
Triage 2026-07-24 — SUPERSEDED by `kanban/tasks/static-analysis-cljfmt-2026-07.md`. This card's "only 2 files" rationale is three revisions stale: `clojure -M:cljfmt check src test` now fails on **24 files**. It also duplicates `static-analysis-cljfmt-cleanup.md` (which states 7). Both folded into the one successor, which additionally sequences the pass LAST — 8 of the 24 files are rewritten by the Wave 1-3 work, so formatting first only creates churn.
---

---
## Superseded / folded (2026-07-25)

Duplicate of `kanban/tasks/static-analysis-cljfmt-cleanup.md`; both folded into
`kanban/tasks/static-analysis-cljfmt-2026-07.md`, which did the work. See
`kanban/tasks/static-analysis-regression-2026-07-24.md` §Wave 0.
---