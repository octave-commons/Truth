---
status: incubating
created: 2026-07-25T13:08:43.359993818Z
source-session: /home/err/spaces/Truth
source-task: Renamed shadowed locals (comp/count/name/field) to clear clj-kondo :shadowed-var
p-efficiency: 0.5
p-friction: 0.5
p-skill-candidate: 0.85
promoted-to: ""
rejected-reason: ""
---

## Problem
A line-range-scoped rename missed one site outside the range. The leftover symbol did not error -- it resolved to clojure.core/comp, so (chem/volatile-budget comp 1.1e24) silently compared 0.0 < 0.0 and the assertion inverted

## Pattern
Renaming a local that shadows a clojure.core var is uniquely dangerous: a missed site is still resolvable, so you get a wrong answer instead of a compile error. The same applies to a local shadowing an ns-level def in the same file (dropping a param falls through to the fixture and the test passes vacuously)

## Candidate skill outline
- Name suggestion
- Trigger phrases
- Key steps or rules
- Anti-patterns to avoid

## Better path
After any such rename, grep the WHOLE file for the bare old symbol with word boundaries excluding qualified/keyword forms, and confirm every remaining hit is prose or a legitimately different binding. Never trust a line-range scope. Run the affected test namespace, not just the linter -- the linter goes quiet either way

## Receipt refs
- none
