# Π Handoff — octave-commons/Truth

**Date:** 2026-06-26  
**Branch:** main  
**Tag:** Π-2026.06.26.2  
**Tests:** `clj -M:test` → 150 tests, 393 assertions, 0 failures, 0 errors  
**License:** GNU GPL v3 or later (standalone application, per ημΠ.dev.v1 §7)

## What this snapshot contains

Expands the **Phase 0 stellar nebula** with hydrodynamics, stellar evolution, Lorentz electrodynamics, coupled rendering, and curated note/spec artifacts. The single ECS substrate invariant remains enforced by `test/architecture_test.clj`.

### Major additions

- **Hydrodynamics** (`src/domain/hydro.clj`, `test/domain/hydro_test.clj`) — SPH-style density/pressure gradient and gas dynamics on the ECS substrate.
- **Stellar evolution** (`src/domain/stellar.clj`, `test/domain/stellar_test.clj`) — Protostar ignition, main-sequence mass/luminosity scaling, and lifetime/burnout.
- **Lorentz electrodynamics** (`test/domain/em_lorentz_test.clj`) — Validates charged-particle motion under E/B fields.
- **Coupled rendering** (`src/infra/render.clj`, `test/infra/render_test.clj`) — Renderer consumes ECS world directly; added visual regime feedback.
- **Curated knowledge** — Split oversized session notes into topic-bounded chunks under `docs/notes/`, archived originals under `docs/notes/archive/`, and synthesized six new design specs under `docs/specs/`.

### Changed

| File | Change |
|------|--------|
| `.gitignore` | Ignore `.opencode/` local tooling state and stray `EOF`/`PY` artifacts. |
| `.ημ/Π_STATE.sexp`, `.ημ/Π_LAST.md` | Handoff artifacts for this snapshot. |
| `AGENTS.md` | Trimmed simulation-stack boilerplate; added invariants, dev-service note, and agent skill pointer. |
| `src/domain/ecs/components.clj` | New components for hydro/stellar/electrodynamics. |
| `src/domain/em.clj` | Electrodynamics extended and hardened. |
| `src/domain/orbital/system.clj` | Orbital integration adjustments. |
| `src/domain/phase0.clj` | Phase 0 tick pipeline integrates hydro, stellar, EM, and regime systems. |
| `src/domain/physics/collision.clj` | Collision response updates. |
| `src/infra/dev/window.clj` | Window/dev harness updates. |
| `src/law/field.clj`, `src/law/stellar.clj` | Malli schemas for new domains. |
| `src/shape/spatial.clj` | Spatial helpers for coupled physics. |
| `test/domain/phase0_test.clj` | Phase 0 pipeline tests updated. |
| `test/domain/physics/collision_test.clj` | Collision tests updated. |

### Deleted

None (original monolithic notes were renamed into `docs/notes/archive/`).

### Residual / ignored

- `.agents/` — local agent session state.
- `.opencode/` — local OpenCode tooling state (node_modules, symlinks).

## Verification notes

- `clj -M:test` passes: 150 tests, 393 assertions, 0 failures, 0 errors.
- clj-kondo/LSP reports unresolved symbols in `test/domain/ecs/{dsl,ledger,rewind}_test.clj`, but these namespaces load and pass at test runtime; treated as LSP analysis noise.

## Blockers

None.
