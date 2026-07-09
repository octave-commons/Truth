---
status: promoted
reviewed: 2026-07-09T01:35:37Z
reviewer-session: 19b3e756-ddf4-4fbc-a500-e68b33bcdc64
created: 2026-07-08T20:16:36.103789056Z
source-session: ses_0c0f9f161ffegZK7S36nqvx4Zs
source-task: Recovered lost two-channel formation from receipts.edn
p-efficiency: 0.5
p-friction: 0.5
p-skill-candidate: 0.75
p-recurrence: 0.85
p-generalizable: 0.85
p-worth-promoting: 0.85
promoted-to: /home/err/spaces/Truth/.agents/skills/receipt-driven-regression-recovery/SKILL.md
rejected-reason: ""
---

## Problem
Regression looked like a new bug, but it was previously-fixed work that had been lost; re-implemented from scratch before discovering receipt 96.

## Pattern
When a regression appears in a receipt-keeping project, the fix may already be documented in receipts.edn.

## Candidate skill outline
- Name suggestion
- Trigger phrases
- Key steps or rules
- Anti-patterns to avoid

## Better path
Before writing replacement code, tail receipts.edn for the same symptom or design area; recover the prior decisions rather than re-inventing them.

## Receipt refs
- none
