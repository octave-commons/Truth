# Π Fork Tax — 2026-06-28

## Signal

Full handoff snapshot of the Phase 0 stellar simulation after a burst of domain work on nucleosynthesis, seed validation, magnetized outflows, collision response, and observer influence.

## Changes (389 insertions, 139 deletions across 13 files)

### Domain Systems
- **`chemistry.clj`** — `nucleosynthesis-system`: live H→He burn wired into the ECS tick pipeline. Uses `burn-step` with dt-correct rate `f = min(cap, dt/τ_MS)` where `τ_MS ∝ M^(-2.5)`. Caps at 1% per tick to prevent Myr-scale lurch. Sole writer of `:component/composition`.
- **`phase0.clj`** — `assert-seed-contracts!`: boot-time guard that folds every seeded matter-state body through `law.registry` governed by `law.stellar/matter-state-contract`. Malformed seeds fail at boot, not mid-flight.
- **`stellar.clj`** — Wind and flare parcels now carry `:b-field` (the star's field at launch via `em/net-field-at`), seeding the Phase 1 magnetised-outflow substrate. New `shatter-bodies` collision response: cold brittle bodies split into two debris fragments (mass + momentum conserved), hot molten bodies merge.
- **`player.clj`** — `observer-acceleration`: bounded per-tick velocity nudge toward focus (`accel = pull × ref-speed / dt`, so `Δv = pull × ref-speed` regardless of dt). `apply-observer-influence`: sole writer of `:component/accel.observer`.

### New ECS Components
- `accel-observer` in `ecs/components.clj` — observer influence acceleration vector

### New Law Schemas
- `law/composition.clj` — composition contracts
- `law/plasma.clj` — plasma state schemas
- `law/sed.clj` — spectral energy distribution schemas
- `law/system_specs.clj` — system-level spec contracts

### New Tests
- `test/domain/chemistry_system_test.clj`
- `test/domain/collision_malleability_test.clj`
- `test/domain/em_field_substrate_test.clj`
- `test/domain/observer_influence_test.clj`
- `test/law/seed_contract_test.clj`

### Research Notebooks
- Expanded `docs/research/` with atmosphere, biology, geology, physics domains
- Cosmology: BBN yields notebook, Lane-Emden solver, stellar SED template grid

## Verification
- Architecture test: ✅ 5 tests, 7 assertions, 0 failures
- No secrets detected in tracked files
- `.gitignore` updated for research checkpoints

## Concurrent Dirt (intentionally untouched)
- `src/law/.#system_specs.clj` — Emacs lockfile, not ours
- `docs/research/.ipynb_checkpoints/` — Jupyter runtime artifacts

## Next
Continue Phase 0 integration — the nucleosynthesis system and observer influence are live but need a full simulation run to validate behavior at Myr timescales.
