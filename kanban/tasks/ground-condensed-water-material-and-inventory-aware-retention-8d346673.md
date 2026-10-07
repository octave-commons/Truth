---
category: "tasks"
labels: "research, chemistry, material-inventory, playable-life"
parent: "roadmap-phase-0-physics-honesty-chemistry-disks-plasma-inspection"
type: "task"
write-id: "1791357570047-0.zlop4yp1q576y8xbwq"
points: "3"
title: "Ground condensed water material and inventory-aware retention"
priority: "P1"
status: "incoming"
uuid: "a4744132-2328-4cb2-a550-e1328d346673"
created_at: "2026-10-07T06:23:11.629Z"
---

## Outcome

Ground a minimal conserved distinction between free nebular hydrogen and hydrogen chemically bound in condensed water or hydrated solids, and distinguish atmospheric escape capability from available material inventory. This is a research/design-refinement task, not permission to modify the accepted planet-seeding model.

## Grounding and scope

The existing nebular-chemistry-and-composition-spec intentionally excludes free gas-former elements from core grains. Current source also excludes hydrogen carried by water-bearing solids. The completed ecology-m5-phase3-atmosphere-retention model classifies retention capability; its research section8.1 leaves inventory intersection open. Inspect existing docs/research/physics/nebular-chemistry-metal-enrichment.md and docs/research/atmosphere/planetary-atmosphere-retention-classifier.md first, then verify a small primary-source literature set.

Research the smallest stoichiometrically bounded condensed-water partition consistent with elemental mass conservation and the existing one-ECS model. Describe oxygen competition, gas/solid accounting and normalization, temperature/pressure applicability and which choices remain toy prescriptions. Define how inventory may constrain a retained-species projection without claiming an atmosphere or accessible biological substrate. Record source pin b395c404 and source-derived limits independently from literature and proposals. Related proposed life contract is Truth PR38; its H/O/C/N guard is not weakened here.

## Non-goals

No production/test/config changes, full molecular reaction network, first-principles abiogenesis, new biological rate, forced world, calibrated planet distribution, acceptance of PR38, or reopening of completed cards. No native or JVM experiments during research authoring. Toy calculations may characterize stoichiometry only and must be explicitly labeled and reproducible.

## Acceptance criteria

- One dated research notebook in docs/research/physics with primary-source citations, equations, explicit applicability limits and pure Clojure pseudocode for later review.
- A small reproducible arithmetic table/experiment separates conservation checks from empirical calibration; no fabricated benchmark or native result.
- Cross-links to the accepted grain-filter and retention research, source audit and existing chemistry roadmap explain the new distinction.
- Candidate design/implementation boundaries are independently reviewed and each later feature can be sized at most5; this task admits none of them.
- Research INDEX and ACTORS record the interactive research output without inventing a scheduled actor or changing prior entries.

## Verification

Validate links and source pins, independently review elemental/mass balance and regime claims, preserve raw numerical inputs/results if run, append Receipt River and reflection. Planning PR review and canonical admission remain separate from research completion.

---

Research handoff for this Incoming 3-point owner: docs/research/physics/2026-10-07-condensed-water-material-budget.md (SHA256 5ac7505b055f8bbad0845bb90003ad9cee047a6dad4225ccc0f3894981e8ecd6) recovers the existing grain-filter and retention evidence, verifies three primary full texts, and proposes a common-basis elemental water allocation. Explicit H/O reservations, conversion/phase inputs, and separate solid/gas draw bounds conserve amounts before normalization. Stored exact-rational toy evidence contains seven admitted balanced cases, one overspend rejection and a 21-point oxygen-reservation sensitivity sweep; this is not empirical or native calibration. Independent board-scout review confirmed source applicability, stored arithmetic and figure, and its phase-overdraw finding is resolved. INDEX and ACTORS additions preserve their prior byte prefixes. Source pin b395c4049718f7ce015ddf25fc0a192d97821373 is unchanged; no production/test/config, JVM, native, or service work. Research remains a draft with unaccepted oxygen chemistry, reaction extent, phase conditions, numerical policy and donor depletion choices. It creates neither an atmosphere nor represented life and does not weaken PR38 guards. Card status/points/body remain Incoming/3/original; implementation admission and hosted review remain separate. Closure/provenance: .ημ/diagnostics/condensed-water-research/closure.json.

2026-10-07 bounded design continuation on PR44 research head8f9ee36, same Incoming3 owner. Specify a versioned pure condensed-water allocation and finite phase-extraction contract grounded in the published material-budget research. Choose common elemental kg/exact rational reference arithmetic, explicit model-versioned reservations, bounded reaction extent and phase fraction, rejection rules and donor/child conservation. Require explicit finite donor stock; missing stock remains unsupported, never reconstructed silently from host-star composition. Work four traces: oxygen-limited, zero H, phase overdraw despite identical normalized compositions, sequential donor exhaustion. Map all disk inflow/outflow ownership seams, distinguish capacity from actual reacted material and retention capability from inventory. Record the absent disk elemental-supply integration and binary64 residual policy as separate prerequisites; no formation code, material stock, natural-water/life claim or implementation admission follows from this document. New design docs/designs/condensed-water-inventory-boundary.md may reference the earlier parcel-disk proposal without adopting it. Existing card state, estimate and body remain unchanged; canonical append-only comment records scope.

Design continuation closed for local publication review: docs/designs/condensed-water-inventory-boundary.md (SHA256 5ca573b02dc209ad893511329b0b2e129b5dc0bed45a4a45e753ecff6ff85c4a), with exact-rational trace/source audit .ημ/diagnostics/condensed-water-research/design-audit.json (SHA256 dc5871315ea98365d7b54ecf913fd80bdf9d8e28de5ec23d8cfb26310fa324a9). Proposed choices are explicit 17-element common-basis stock, supplied versioned reservations and reaction/phase fractions, water stoichiometry, separate finite phase draw bounds, and returned-compartment sequencing. Missing stock is unsupported; known zero is empty. Four constructed rational traces pass independent recombination, including phase-overdraw and exhaustion; 36 stored water maps satisfy the selected H/O relation. Root and board-scout local design reviews found no planning blocker. All 17 cited source blobs match b395c404, 16 local links resolve, and the four published research files remain byte-identical to PR44 head 8f9ee36. This is a proposed design, not physical calibration, stock production, settlement atomicity, Clojure test/native evidence, feature readiness or review convergence. Donor inflow/outflow integration, binary64 residual policy, actual chemistry/phase producers and normal-formation proof remain separate prerequisites. Body, Incoming status and 3-point estimate remain unchanged. Publication is a successor planning PR over PR44; closure/prefix evidence is .ημ/diagnostics/condensed-water-research/design-closure.json.

---