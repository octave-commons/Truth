---
status: promoted
reviewed: 2026-07-11T00:18:25Z
reviewer-session: 82b1ced6-f9e6-4e36-b207-efd8414d6285
created: 2026-07-07T01:01:02.861903126Z
source-session: ses_0c62648feffeHiJD4Cv61Hieaz
source-task: Implemented seed-and-grow condensation with dedicated mass-flux influence channel
p-efficiency: 0.5
p-friction: 0.5
p-skill-candidate: 0.8
p-recurrence: 0.9
p-generalizable: 0.75
p-worth-promoting: 0.85
promoted-to: /home/err/.agents/skills/dedicated-influence-channel/SKILL.md
rejected-reason: ""
---

## Problem
Adding a new mass source/sink to the ECS requires choosing between reusing an existing influence channel (single-writer violation risk), post-fold mutation (barrier violation), or a new dedicated channel (integrator registry + single-writer system).

## Pattern
Every new conserved quantity flow should get its own c/mass-flux-* component, one single-writer emitter, and one entry in the integrator influence-registry :mass accumulate.

## Candidate skill outline
- Name suggestion
- Trigger phrases
- Key steps or rules
- Anti-patterns to avoid

## Better path
For future mass flows, always create a dedicated c/mass-flux-<name>, register it in domain.integrator/influence-registry, and own it with one system. Never reuse mass-flux-transfer or mutate mass post-fold.

## Receipt refs
- none
