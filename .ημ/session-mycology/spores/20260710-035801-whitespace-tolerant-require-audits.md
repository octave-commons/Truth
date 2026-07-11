---
status: promoted
reviewed: 2026-07-11T00:18:25Z
reviewer-session: 82b1ced6-f9e6-4e36-b207-efd8414d6285
created: 2026-07-10T08:58:01.070934604Z
source-session: /home/err/spaces/Truth
source-task: Audit callers of a namespace before deleting/deprecating it
p-efficiency: 0.5
p-friction: 0.5
p-skill-candidate: 0.85
p-recurrence: 0.75
p-generalizable: 0.85
p-worth-promoting: 0.85
promoted-to: /home/err/.agents/skills/whitespace-tolerant-require-audits/SKILL.md
rejected-reason: ""
---

## Problem
A grep pattern assuming single spaces (e.g. '\[ns :as') silently misses aligned-columnar requires ('\[ns        :as'), undercounting callers. This produced two wrong caller-count assertions and nearly closed a task on a false 'no production callers' premise.

## Pattern
For any require/usage audit that drives a decision (delete, deprecate, count callers), use whitespace-tolerant patterns ('\[ns +:as', ' +:as') and run a post-change safety sweep — a compile/broad grep AFTER the edit catches what the pre-change grep missed.

## Candidate skill outline
- Name suggestion
- Trigger phrases
- Key steps or rules
- Anti-patterns to avoid

## Better path
Before deleting a namespace: grep with '+' for whitespace, include :refer/:as/dynamic-require forms, and never state a caller count from a single narrow pattern. Let the compiler/full-suite be the ground truth, not the grep.

## Receipt refs
- none
