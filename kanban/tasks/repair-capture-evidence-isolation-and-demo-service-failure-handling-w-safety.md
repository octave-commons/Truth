---
category: "tasks"
labels: "hygiene, regression, infra, demo"
type: "task"
write-id: "1791317917742-0.bi9u88t6g7g9s244jl9"
points: "3"
title: "Repair capture evidence isolation and demo-service failure handling"
priority: "P1"
status: "incoming"
estimate: "3"
uuid: "demo-capture-review-safety"
created_at: "2026-10-06T20:18:00.086Z"
---

# Repair PR 7 capture identity and demo-service failure handling

Grounding and bounded plan: `docs/notes/2026-10-06-demo-review-safety.md`.
This is a hygiene/regression repair of four native PR 7 findings on 0112e22.

## Outcome

Concurrent capture runs preserve separate diagnostic evidence; invalid demo
client invocation has no network effects; failed formation preparation restores
the caller's prior pause-hook settings without erasing unrelated config changes.

## Scope

- Unique diagnostic allocation in both existing capture commands and path documentation.
- Local missing-form rejection before nREPL connection.
- Formation service guard and exact presence/value restoration for tick-fn/on-step on failure.
- Meaningful RED tests using real shell commands with controlled command boundaries,
  recorded nREPL effects, and isolated service atoms. Test-only classpath/dependency wiring.

## Acceptance

- Two same-second runs with separate ports/output directories retain unmixed diagnostics.
- Missing client form reports usage without opening a connection; valid dispatch still closes it.
- Nil service is rejected before setup; failed wait preserves original absent/nil/function hook values.
- Unrelated concurrent config changes survive restoration; successful setup remains intentionally paused.
- Record focused regression results, full suite/static gates, exact correction revision and native thread IDs.

## Non-goals

No physics/rendering changes, historical diagnostic rewrites, fixture claims,
configuration migration, PR-head changes, review requests or settlement replies.
Root owns all commits, pushes and external review operations.

## Verification

Tests precede production changes and require a root RED checkpoint. Native
threads: PRRT_kwDOTDahac6plKPq, PRRT_kwDOTDahac6plKPw,
PRRT_kwDOTDahac6plKP8, PRRT_kwDOTDahac6plKQD.
Full commands and current evidence belong in canonical comments after planning.