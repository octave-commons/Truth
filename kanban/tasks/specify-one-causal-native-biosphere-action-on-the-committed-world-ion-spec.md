---
uuid: "committed-biosphere-native-action-spec"
title: "Specify one causal native biosphere action on the committed world"
status: "incoming"
type: "task"
priority: "P1"
points: "3"
labels: "design, ecology, input, playable-gate"
parent: "embodied-character-voxel-mode"
category: "tasks"
write-id: "1791317948903-0.dapl57xojb85u8cxskx"
created_at: "2026-10-06T20:19:08.903Z"
---

## Context
Natural production worlds now form planets and prebiotic-to-prokaryotic life. Existing ecology action helpers have no native callers; the live action palette currently dispatches physical interventions and sculpt requests. The missing interaction must be specified before claiming that older browser hotbar descriptions already work.

Grounding: `docs/notes/2026-10-06-playable-gate-route-audit.md`, `docs/designs/commitment-and-resonance.md`, `docs/designs/ux-architecture.md`, `src/domain/ecology/abilities.clj`, the ecology writer and native input/action paths.

Coordinate time-unit and cadence decisions with `committed-clock-executable-policy`. Independent interface research may proceed while that policy is reviewed; any consumer that depends on unresolved clock semantics must identify the dependency explicitly.

## Outcome
An implementation-level design for one meaningful committed-world biosphere action, using the existing ecology and intent ownership.

## Scope
Choose the first action from the documented planetary palette and existing helper semantics. Define its actual player affordance, committed target, eligibility, resource cost, queued input shape, single owner, acceptance/rejection event and visible effect. Inventory the other helper names without silently making them additional implementation scope. Produce one bounded consumer story of at most five points, with explicit prerequisites.

## Non-goals
No native binding yet, fabricated organism, direct ecology overwrite, alternate browser/runtime, instantaneous civilization, new economy or Gate producer. A helper call or event notification alone is not a completed interaction.

## Acceptance criteria
- The selected action and target contract reconcile the current native palette with accepted design, including unsupported or conflicting older wording.
- Valid and invalid target, insufficient resource, repeated input and queued-state-change cases have explicit outcomes; rejected actions do not spend resources.
- The existing ecology writer remains authoritative and the implementation story identifies the exact intent and render boundaries.
- RED scenarios use the production input/owner boundary; live acceptance requires a naturally produced eligible world, actual key/menu input, a causal state change, cost evidence and visible feedback.
- The result links dependencies by UUID and labels any unresolved choice instead of inventing implemented behavior.

## Verification
Trace existing helper and writer code, inspect relevant laws/tests, and independently review the proposed action trace. Do not mutate a verification world to simulate acceptance.

## Risks
The inherited helper may assume a different phase/target or charge resources inconsistently. Native controls and planetary unlock semantics must agree before a key is bound.
