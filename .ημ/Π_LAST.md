# Π Fork Tax — 2026-06-30

## Signal

Absorb-accrete COM preservation in the integrator, stellar fusion fan-out refactor (promotion signals, one-tick latency), ECS DSL macro cleanup (shadowed core vars), renderer variable renaming, and comprehensive test updates. 230 tests, 3812 assertions, 0 failures.

## Changes (1278 insertions, 1007 deletions across 45 files)

### Domain Systems — Major Changes
- **`integrator.clj`** — +317/-50 lines: absorb-accrete/merge processing with COM-preserving mass-weighted blend, composition blending, temperature delta with impact heating, angular momentum sum
- **`stellar.clj`** — +542/-542 lines: fusion system refactored to fan-out pattern — `fusion-promotion-system` emits `c/promotion-signal`, `fusion-system` reads it on next tick. Eliminates direct matter-state writes conflicting with classifier
- **`ecs/dsl.clj`** — +61/-61 lines: macro params renamed from `name`→`nm`, `key`→`k` to avoid shadowing `clojure.core`

### Renderer Cleanup
- **`infra/render.clj`** — +236/-236 lines: docstrings converted to comments, shadowed vars renamed (`key`→`k`, `count`→`cnt`, `name`→`nm`, `first`→`fst`, `comp`→`compose`, `sound-speed`→`snd-spd`)

### Supporting Changes
- `ecs/components.clj`, `ecs/registry.clj`, `ecs/ledger.clj`, `ecs/timeline.clj` — registry and component updates
- `phase0.clj`, `em.clj`, `hydro.clj`, `chemistry.clj` — pipeline refinements
- `gravity/barnes_hut.clj`, `physics/collision.clj`, `spatial/index.clj` — solver refinements
- `orbital/kepler.clj`, `orbital/system.clj`, `world_bootstrap.clj` — orbital adjustments
- `law/composition.clj`, `law/contract.clj`, `law/ecs_dsl.clj`, `law/ledger.clj`, `law/system_specs.clj` — schema updates
- `shape/spatial.clj` — spatial math refinements

### Test Updates (16 files)
- All existing tests updated to pass with the refactored APIs
- No new test files — existing coverage validated

### Documentation
- **`README.md`** — +108 lines: research program docs, design docs index, codebase overview table, static analysis section

### New Files
- `docs/notes/specs/2026.06.30-retire-step-physics-implementation-plan.md`

## Verification
- 230 tests, 3812 assertions, 0 failures, 0 errors
- Architecture test: ✅ passing

## Next
Continue Phase 0 stabilization — retire step-physics implementation plan, absorb-accrete validation, benchmark-driven optimization, full Myr-scale simulation validation.
