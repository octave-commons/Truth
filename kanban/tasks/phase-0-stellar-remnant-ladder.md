---
category: "specs"
labels: ["specs", "phase0", "em"]
write-id: "1784745365933-0.k4m2djg58odj1gaefd"
source: "kanban/tasks/phase-0-stellar-remnant-ladder.md"
title: "Phase 0 Stellar Remnant Ladder"
priority: "P1"
status: "done"
estimate: "5"
uuid: "phase-0-stellar-remnant-ladder"
created_at: "2026-07-10T12:00:00Z"
---

# Phase 0 Stellar Remnant Ladder

> Parent: `kanban/tasks/phase-0-stellar-winds-mass-transfer-remnants-technical-spec.md`
> Scope: the unbuilt §2/§4/§5 remnant-ladder work from the parent spec.

**Goal:** Make gravitational collapse irreversible. A bound resolved body
(`:debris`, `:planet`, `:protostar`, `:brown-dwarf`, `:star`) that loses mass
must walk down a **remnant ladder** rather than return to `:nebula`.

**Principle:** Mass loss creates `:nebula` parcels, not a dissolved core. The
core degrades to `:stellar-remnant` and, only when fully ablated, despawns.

## Scope

1. Add `:stellar-remnant` matter-state.
   - Terminal, degenerate, no contraction, no fusion.
   - Cooling white-dwarf-like temperature/luminosity track.
   - Reuses `:debris` rendering as a v1 fallback if full rendering is too much.
2. Extend `classifier-system` / `classify-next-state` with downward transitions.
   - `:star` → if `< hydrogen-burning-mass` → `:stellar-remnant`.
   - `:protostar` → if `< deuterium-burning-mass` → `:stellar-remnant`.
   - `:brown-dwarf` → if `< deuterium-burning-mass` → `:stellar-remnant`.
   - `:stellar-remnant` stays terminal.
   - No bound state ever transitions to `:nebula`.
3. Add despawn-on-ablation in `stellar-wind-system` / `sink-formation`.
   - When `mass ≤ ablation-floor`, despawn the entity.
   - Mass already conserved in wind parcels / companion.
4. Touch points:
   - `domain.stellar/structure` (resolved shape, radius, density).
   - `domain.stellar/thermal` (cooling track, not virial heating).
   - `domain.stellar/regime` (faint, no corona, no wind).
   - `domain.stellar/classify-next-state` (down-ladder transitions).
   - `law.stellar` schemas for `:stellar-remnant` and thresholds.

## Tests

- `test-remnant-ladder-star-demotes-to-remnant`: drive a `:star` below H-burning mass; it becomes `:stellar-remnant`, never `:nebula`.
- `test-remnant-ladder-protostar-demotes`: drive a `:protostar` below D-burning mass; it becomes `:stellar-remnant`.
- `test-remnant-ladder-brown-dwarf-demotes`: drive a `:brown-dwarf` below D-burning mass; it becomes `:stellar-remnant`.
- `test-total-ablation-despawns-never-nebula`: strip a body to zero mass; it despawns and never becomes `:nebula`.
- `test-remnant-cools-not-contracts`: a `:stellar-remnant` loses temperature over time, does not re-expand or re-heat by contraction.
- `test-remnant-does-not-wind`: a `:stellar-remnant` does not emit stellar wind.

## Done when

- `:stellar-remnant` is a valid matter-state throughout the tick pipeline.
- Down-ladder transitions are covered by the classifier tests above.
- `clojure -M:test` is green.
- `test/architecture_test.clj` passes.
- `bin/bench` shows no regression if hot paths were touched.
- Parent card is updated with a link to this residual card.

---
Started 2026-07-10: moving to in_progress. Will read current matter-state/structure/classifier code, write tests, implement remnant state.

Completed 2026-07-10: implemented stellar-remnant ladder.\n- Added :stellar-remnant matter-state to components + schema.\n- Added white-dwarf-radius and ablation-floor constants in law.stellar.\n- Modified classifier: stars, protostars, brown dwarfs demote to :stellar-remnant below thresholds; never return to :nebula.\n- Modified structure: remnant uses degenerate white-dwarf radius + density.\n- Modified temperature: remnant cools radiatively.\n- Modified integrator: bound bodies at/below ablation-floor emit c/consumed-ablation and despawn.\n- Added 6 tests covering demotion, terminal state, ablation, cooling, no wind.\n- Verification: clojure -M:test 637 tests/13449 assertions green; architecture-test green; clj-kondo 0 warnings; bin/analyze --strict no blocking findings.

Review 2026-07-22 (Claude, verified by review agent): VERDICT PASS-WITH-NITS. All 6 code criteria met — :stellar-remnant terminal matter-state (components.clj:35, schema.clj:16); down-ladder star/protostar/brown-dwarf -> remnant, never -> nebula (classifier.clj:106-216); degenerate white-dwarf-radius (orbital/constants.clj:45-50) used in geometry.clj:40-43; radiative cooling not virial (temperature.clj:85-89); despawn-on-ablation via c/consumed-ablation at ablation-floor (integrator/core.clj:81-133); 6+1 tests green. domain.stellar-test 42/130 green; full suite 642/13463 green. NITS (non-blocking, flagged): 4 test names differ from the card's Tests wording; parent card describes the split but has no literal filename/uuid cross-link. review -> done.
---