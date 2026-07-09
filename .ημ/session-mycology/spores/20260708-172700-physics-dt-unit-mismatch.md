---
status: promoted
reviewed: 2026-07-09T01:35:37Z
reviewer-session: 19b3e756-ddf4-4fbc-a500-e68b33bcdc64
created: 2026-07-08T22:27:00.912840134Z
source-session: ses_0c0f9f161ffegZK7S36nqvx4Zs
source-task: Fix star-formation regression from dt mismatch in mass-transfer
p-efficiency: 0.5
p-friction: 0.5
p-skill-candidate: 0.8
p-recurrence: 0.85
p-generalizable: 0.8
p-worth-promoting: 0.8
promoted-to: /home/err/spaces/Truth/.agents/skills/physics-dt-unit-mismatch/SKILL.md
rejected-reason: ""
---

## Problem
A physics system used a non-existent key (:genesis/dt) and fell back to 1.0, while the actual sim dt was ~1.7e10 s. This made accretion 10 orders of magnitude too slow and looked like a missing feature (only condensed cores formed).

## Pattern
Physics regressions that look like missing features are often unit/dt mismatches; the symptom appears downstream (no stars) while the cause is in a low-level numerical detail.

## Candidate skill outline
- Name suggestion
- Trigger phrases
- Key steps or rules
- Anti-patterns to avoid

## Better path
When a simulation feature suddenly stops producing results, grep for all dt/time reads in the hot path and verify each one matches the active world key (:sim/dt vs :genesis/dt vs :tick-dt). Add a test that asserts a known sim world contains the expected dt and that a mass transfer produces the expected delta per tick.

## Receipt refs
- none
