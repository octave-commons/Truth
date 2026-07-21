---
status: rejected
reviewed: 2026-07-11T18:54:40Z
reviewer-session: 103dc52a-0768-4541-a24a-4d41e3ac17f1
created: 2026-07-07T04:55:51.875939905Z
source-session: ses_0c5304a02ffelcFB41KkldUz4s
source-task: Pivot from a hack/workaround to the honest architecture when the user rejects it
p-efficiency: 0.5
p-friction: 0.5
p-skill-candidate: 0.7
p-recurrence: 0.6
p-generalizable: 0.8
p-worth-promoting: 0.7
promoted-to: ""
rejected-reason: "Too vague and generic: the pattern is good engineering discipline (do the right thing when pushed back) but is already covered by work-cycle, spec-driven-dev, and honest user-feedback loops. It does not add a distinct, reusable protocol."
---

## Problem
A proposed hack (single bootstrap core) got into the code/plan; user rejected it and wanted the right thing done

## Pattern
User rejects a hack → acknowledge immediately → rewrite plan to describe the honest fix → remove the hack from code → implement the proper architecture → add tests that enforce the honest behavior → run full suite + analyze → receipt + session-mycology

## Candidate skill outline
- Name suggestion
- Trigger phrases
- Key steps or rules
- Anti-patterns to avoid

## Better path
When user says a workaround is pathetic/dishonest, stop and do it right: update docs first, then remove hack and implement the honest mechanism, with tests proving it works

## Receipt refs
- none
