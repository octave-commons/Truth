---
uuid: "seed-and-grow-condensation"
title: "Seed-and-Grow Condensation (decouple body mass from parcel mass)"
status: "todo"
priority: "P1"
labels: ["specs", "phase0", "physics", "resolution"]
created_at: "2026-07-06T18:00:00.000000000Z"
source: "docs/specs/seed-and-grow-condensation-realspec.md"
category: "specs"
---

# Seed-and-Grow Condensation

Fix the resolved-body mass floor: condensation currently promotes a WHOLE gas
parcel (4e27 kg = 669 M⊕ = 2.1 M_J), so "planetesimals" are super-Jupiters. The
floor is an assumption ("body = whole parcel"), not a physical law or a Lagrangian
necessity.

**Decision:** condensation spawns a SMALL physical seed (mass from condensation
physics, ~1e15–1e18 kg), debits it from the parent parcel, and grows it via the
already-built M3 gradual BHL (`domain.mass-transfer`, 0.58 ms / ~2% of tick).

Spec: `docs/specs/seed-and-grow-condensation-realspec.md`. The "grow" half is done
(commit 0d03d0f); the planet seeder already seeds sub-parcel cores (precedent).
Watch: entity-count growth (tick is super-linear in N) → gate seeding to genuine
condensation sites; float-precision boundary (~1e12 kg ULP against a 4e27 parcel)
→ asteroid-scale seeds OK, finer needs nested regimes.
