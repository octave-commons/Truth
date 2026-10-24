---
uuid: "9294b12c-e551-4195-b96d-183ed5cce58f"
parent: "278bfe8a-468e-47e2-a669-537a2c16a6d3"
title: "Ground finite water and atmospheric inventory producers"
type: "story"
status: "incoming"
priority: "P1"
points: 3
labels: ["research", "design", "planetary-habitat"]
created_at: "2026-10-24"
---

## Context

As a planetary simulation researcher, I need a conserved material origin and pressure/enthalpy phase history so that rain and liquid-water habitats are physically supplied.

## Outcome

Ground finite water and atmospheric inventory producers.

## Scope and acceptance criteria

- [ ] Continue owner a4744132-2328-4cb2-a550-e1328d346673 and PR44/47 rather than duplicating their allocator. Specify finite donor origin, liquid extension and competitive oxygen reservations. Red controls: zero H with silicate O, unknown vs empty stock, finite depletion, phase overdraw and latent-heat closure.
- [ ] Required named contracts and meaningful red tests precede domain changes.
- [ ] Record executed, validated and unavailable checks separately.

## Non-goals

No new authoritative world, global Earth-history proof, fork creation or uncalibrated chemical-life conversion.

## Verification

[Proposed design](../../docs/designs/physical-habitat-coupling.md), [research and seven executed probes](../../docs/research/planetary-habitats/README.md). Initial Markdown planning input; no Rheos creation/admission/status transition is claimed.

## Risks

Chemistry/thermodynamics validity, timesteps spanning incompatible scales, finite donor closure and existing PR dependencies. Implementation depends on review and canonical admission; the research/design labels do not admit production code.
