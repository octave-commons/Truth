# Π Handoff — octave-commons/Truth

**Date:** 2026-06-26  
**Branch:** main  
**Tag:** Π-2026.06.26.1  
**Tests:** `clj -M:test` → 94 tests, 249 assertions, 0 failures, 0 errors  
**License:** GNU GPL v3 or later (standalone application, per ημΠ.dev.v1 §7)

## What this snapshot contains

Converges the **Phase 0 stellar nebula** onto the single ECS substrate: removes the parallel particle-world fork, adds coupled electrodynamics and regime classification, and enforces the architecture invariants with a dedicated test.

### Changes committed

| File | Change |
|------|--------|
| `.gitignore` | Ignore `.agents/` local session state. |
| `.ημ/Π_STATE.sexp`, `.ημ/Π_LAST.md` | Handoff artifacts for this snapshot. |
| `AGENTS.md` | Updated agent guidance. |
| `README.md` | Project readme. |
| `docs/designs/phase0-coupled-physics-and-regime-classifier.md` | Design doc for the unified Phase 0 physics + regime classifier. |
| `docs/notes/2026.06.25.22.11.59.md` | Session notes. |
| `docs/notes/2026.06.25.22.13.14.md` | Session notes. |
| `src/domain/ecs/components.clj` | ECS component updates. |
| `src/domain/ecs/parallel.clj` | Parallel ECS tick helpers. |
| `src/domain/em.clj` | Electrodynamics / magnetic field system. |
| `src/domain/gravity/barnes_hut.clj` | Barnes-Hut gravity updates. |
| `src/domain/orbital/system.clj` | Orbital system updates. |
| `src/domain/phase0.clj` | Phase 0 top-level simulation on the single ECS substrate. |
| `src/domain/physics/collision.clj` | Collision system updates. |
| `src/domain/player.clj` | Player entity records. |
| `src/domain/regime.clj` | Plasma regime classifier. |
| `src/domain/stellar.clj` | Stellar body generation. |
| `src/infra/dev/server.clj` | Dev server updates. |
| `src/infra/dev/window.clj` | Window/input updates. |
| `src/infra/render.clj` | Renderer updates. |
| `src/law/field.clj` | Field Malli schemas. |
| `src/law/stellar.clj` | Malli schemas for stellar entities. |
| `test/architecture_test.clj` | Architecture invariants (single ECS substrate, no infra in domain, etc.). |
| `test/domain/em_test.clj` | Electrodynamics tests. |
| `test/domain/phase0_test.clj` | Phase 0 tests. |
| `test/domain/physics/collision_test.clj` | Collision tests. |
| `test/domain/regime_test.clj` | Regime classifier tests. |
| `test/infra/render_test.clj` | Renderer tests. |

### Deleted

- `src/domain/particles/*` — particle-world fork, folded into ECS.
- `src/infra/render/phase0_renderer.clj` — folded into `infra.render`.
- `test/domain/particles/*_test.clj` — superseded by ECS-level tests.

### Residual / ignored

- `.agents/` — local agent session state.

## Blockers

None.
