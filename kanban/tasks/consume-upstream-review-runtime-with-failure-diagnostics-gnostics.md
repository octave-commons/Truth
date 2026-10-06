---
category: "tasks"
labels: "hygiene, ci, review"
type: "task"
write-id: "1791313214933-0.097a478zpzt3irndtbt6"
points: "3"
title: "Consume upstream review runtime with failure diagnostics"
priority: "P1"
status: "review"
uuid: "review-runtime-upstream-diagnostics"
created_at: "2026-10-06T18:44:30.671Z"
---

# Consume the upstream review runtime with diagnosable model failures

## Outcome

Repair Truth's reusable review caller after PR8 run37512668785 failed inside
OpenCode with empty stdout and discarded stderr. Evaluate the existing upstream
runtime at09a4454480baa67f6fdc40f6f73f5e48ef0457d1 before adopting its pinned
interface. This is operational hygiene, not a new reviewer implementation.

## Scope

- Inspect exact upstream requirements, reviewed/merged provenance, credentials,
  permissions, immutable head binding, and stacked-PR base handling.
- If sound, update only Truth's reusable-workflow pin and required inputs.
- Preserve existing named secrets and read permissions; document diagnostics,
  local verification, remaining hosted qualification, and unresolved Kimi identity.

## Non-goals

No repository-local review engine, fabricated approval, provider relabeling,
credential/secret changes, Kimi invitation workaround, merge or auto-merge.
No game simulation changes and no arbitrary default-branch base substitution.

## Acceptance

1. The adopted upstream revision and required inputs are immutable and recorded.
2. The actual PR head and actual stacked base are the review/evidence inputs.
3. Model failures preserve stderr as upstream artifacts; empty stdout is not
   treated as an approving or completed review.
4. Truth's caller retains least-privilege read permissions and named secret flow.
5. Local syntax/interface/upstream-test evidence is recorded separately from
   hosted workflow success, which cannot be claimed until an actual run completes.

## Verification

Inspect the exact reusable workflow plus its referenced upstream runner and tests;
run available upstream deterministic tests without credentials or publication.
Check the caller YAML/interface, run git diff --check, and record changes and
limits in a tracked note and append-only receipt. Root handles commits/publication
and canonical review admission. Mark upstream review/provenance blockers explicitly.

---
Caller-only adoption prepared: upstream pin 09a4454480baa67f6fdc40f6f73f5e48ef0457d1 plus required native pr_head_sha. Exact upstream tests 74/74 pass; actionlint and interface check pass. Provenance, exact stack/head binding, named-secret/read-permission preservation, free default, failed-attempt artifacts, fail-closed terminal gate and unverified hosted/Kimi qualification recorded in docs/notes/2026-10-06-review-runtime-adoption.md. Root owns commits, publication and gated review admission; no local reviewer implementation or credential changes.

2026-10-06T18:59:37Z: canonical in_progress→review succeeded at exact committed source 9b1818ba4cc84001c19f043a72f79c25bbaec43a. Rheos freshly ran clojure -M:test: 899 tests, 15635 assertions, zero failures/errors, then bin/analyze --strict: exit0, no blocking findings. Source/workflow files unchanged throughout; only canonical card/event mutations followed. Full gate log: .ημ/diagnostics/review-runtime-upstream/rheos-review-9b1818b.log. This is local gated review admission, not hosted reviewer approval or a Done transition.
---