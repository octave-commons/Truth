---
category: "tasks"
labels: "design, ecology, input, playable-gate"
parent: "embodied-character-voxel-mode"
type: "task"
write-id: "1791436599937-0.ya320xj2wwslg7rthn"
points: "3"
title: "Specify one causal native biosphere action on the committed world"
priority: "P1"
status: "incoming"
uuid: "committed-biosphere-native-action-spec"
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

---

Planning refinement from independent review, included while this card is Incoming: coordinate time-unit and cadence decisions with committed-clock-executable-policy. Independent interface research may proceed while that policy is reviewed; any consumer that depends on unresolved clock semantics must identify the dependency explicitly. This append-only comment supplies supplemental provenance for the supported Markdown authoring step; creation history remains unchanged.

Design-only continuation on existing PR12 lineage96b19f086707388e43f272e5ec87a3a7378d4c4c: docs/designs/commitment-and-resonance.md §4.4.1 now proposes one Grow request on the already committed world; the older prototype entry links to it. Recovered authority distinguishes Agency activation from Resonance unlock, Genesis Grow1 from committed Biosphere2, and existing prokaryotic helper effects from represented organisms. The proposed existing-queue → sole ecology writer → serial post-fold payment/result boundary grants no implementation admission. Activation amount, cooldown clock, unlock/request/result representation, budget/dedup ownership, passive ordering and same-fold target removal remain explicit blocking review decisions; current reaping order means event precedent alone is not atomic payment proof. Existing committed-clock-executable-policy remains separate. Independent source/authority review found no blocker to publishing this proposal; all17 added local links/heading fragments resolve and diff whitespace is clean. Source/tests/runtime unchanged; no native action, test run, state transition, key binding, card body rewrite or review request. Preserve Incoming3 until the design and later consumer prerequisites are actually reviewed.

PR12 MiMo review5437247456 found two inaccurate route-audit line citations on 0be81e75bc9cbca4929f793a8bbfdffa85d8543b. Verified actual section headings and corrected First Narrowing section3 from L83 to L94 and Commitment and Resonance section5 from L130 to L240. The latter moved because the Grow proposal inserted 110 lines. Earlier added-link existence verification did not verify these two existing semantic citation targets; this comment narrows that verification claim. No design, source, tests, runtime, card body or status change. Incoming3 and all unresolved Grow admission choices remain.

Readable verification-note correction (review 5451583235, comment 4214853373). The two historical statements above are restated with spaces; the original text and events remain preserved.

The earlier design-only continuation on PR12 lineage 96b19f086707388e43f272e5ec87a3a7378d4c4c reported that all 17 added local links and heading fragments resolved, with clean diff whitespace. That limited link check did not verify the two older semantic citation targets.

MiMo review 5437247456 on 0be81e75bc9cbca4929f793a8bbfdffa85d8543b identified those targets. The actual First Narrowing section 3 heading was corrected from line 83 to line 94; Commitment and Resonance section 5 was corrected from line 130 to line 240 after the 110-line Grow insertion. These remain historical documentation checks, with unchanged values and limits.

No Grow implementation, runtime, test, key binding or status transition is admitted. This broad progression card remains outside the accepted finite Gate execution batch.

---