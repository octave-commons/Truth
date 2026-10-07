---
category: "tasks"
labels: "research, design, ecology, actors, playable-gate"
parent: "embodied-character-voxel-mode"
type: "task"
write-id: "1791345209862-0.gm8solrmhln9ubbk0yx"
points: "3"
title: "Ground the first causal life-to-represented-actor boundary"
priority: "P1"
status: "incoming"
uuid: "life-to-represented-actor-spec"
created_at: "2026-10-06T20:19:09.245Z"
---

## Context
The embodied Gate epic explicitly requires intervening phase specifications. Current ecology is a scalar model ending at complex life; no social actor, civilization, avatar or Gate-producing system exists. Phase 5 allows temporary control of an actual historically produced individual, which still requires a causal actor/history model.

Grounding: `docs/notes/2026-10-06-playable-gate-route-audit.md`, `docs/designs/gates-of-truth-world-gen-phases.md` Phases 2-5, `docs/designs/simulation-methods-research.md`, the original Gate vision, current ecology/arc laws and the single-ECS architecture.

Coordinate modeled time units and update cadence with `committed-clock-executable-policy`; this is a semantic reference, not a fabricated blocking dependency on independent research.

## Outcome
A grounded specification of the first life-to-represented-actor boundary, small enough to implement without inventing a civilization from a scalar flag.

## Scope
Research the minimum ecological and individual/behavioral state needed for a persistent actor with one meaningful environmental action. Separate observed simulation state, derived model quantities and fictional product choices. Define identity, birth/admission conditions, resource/material interaction, failure/death, causal events, persistence and one visible projection in the existing world. Propose at most two initial implementation stories, each at most five points.

## Non-goals
No society-scale simulation, full evolution model, arbitrary sentience score, injected avatar, instant civilization, Gate object, network synchronization or new ECS/runtime. Do not break the embodiment epic into implementation work before its prerequisite phase laws exist.

## Acceptance criteria
- Primary sources support any selected scientific/modeling claim, with explicit applicability and limits; existing bibliography alone is not evidence that a method is implemented.
- At least two plausible minimal models are compared against causal continuity, determinism, computational cost and the intended first player-observable behavior.
- The chosen specification contains reusable shapes/laws and names every producer, owner and consumer; a scalar complex-life tag alone cannot create a historically rich actor.
- Worked traces cover a valid birth/action, lack of resources, invalid habitat, repeated admission and actor loss; retained events preserve provenance.
- Proposed implementation stories have explicit RED production-boundary tests and a native acceptance path tied to a naturally produced world. Future embodiment/Gate dependencies remain visible and unresolved where appropriate.

## Verification
Independent source/model review plus a small labeled derivation or disposable model where useful. Record reproducibility assumptions and performance bounds. No fixture or research toy counts as native game acceptance.

## Risks
Anthropomorphic labels can conceal absent mechanism, and overly detailed biology can consume the whole project. Keep one causal, visible actor boundary as the deliverable; broader civilization and Gate rules remain later specifications.

---

Planning refinement from independent review, included while this card is Incoming: coordinate modeled time units and update cadence with committed-clock-executable-policy; this is a semantic reference, not a fabricated blocking dependency on independent research. This append-only comment supplies supplemental provenance for the supported Markdown authoring step; creation history remains unchanged.

Design-only continuation at PR12 base 98b847ce75491bdccacf2ec3a7476d451270bf49: docs/notes/2026-10-07-first-represented-life-boundary.md and resolution-regimes-and-scale-coupling.md section 6.1 compare an individual with a provisionally preferred microbial cohort. The proposed carbon account and nutrient-limited reference rate are grounded in Monod1949 and Jayathilake2017, with no calibration adopted. Cohort representation partitions already accounted living stock; normalized ecology biomass is not that budget and no organism/avatar/phase jump is claimed. Actual local-budget/habitat producers, elapsed-time integration, loss law, durable identity/LOD history and single-writer-compatible generic birth/removal settlement remain implementation blockers. Current request materialization still creates stellar clumps. Worked valid/retry/invalid/resource-loss/same-fold-removal traces and later real-pipeline/native acceptance requirements are recorded. This continuation does not complete the implementation-level specification acceptance or admit code; status and points remain Incoming3. Independent board_scout and runtime_scout source/accounting reviews found no blocker to publishing the proposal; board_scout also checked both primary full texts. Final note SHA256 b5466b3696e881f7414b2bd4f9f17ffbfc6765cbc340fdd815b57d6e276b3b3b; design SHA256 4c16181afd3b128f854df162ef9c878b10095e37ed9e0d27ee070c9a44aae463. Documentation-only: local links and whitespace checked; no source/tests/native/commit/push/review request. Existing source/body/history remain preserved.

---