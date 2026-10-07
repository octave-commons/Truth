---
category: "tasks"
labels: "research, chemistry, material-inventory, playable-life"
parent: "roadmap-phase-0-physics-honesty-chemistry-disks-plasma-inspection"
type: "task"
write-id: "1791355305551-0.tu3rqy3hahm5ejh8oa"
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

---