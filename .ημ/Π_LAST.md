# Π Handoff — octave-commons/Truth

**Date:** 2026-06-25  
**Branch:** main  
**Tag:** Π-2026.06.25.1  
**Tests:** `clj -M:test` → 83 tests, 187 assertions, 0 failures, 0 errors  
**License:** GNU GPL v3 or later (standalone application, per ημΠ.dev.v1 §7)

## What this snapshot contains

Continues the LWJGL pivot with the **Phase 0 stellar nebula** simulation stack, particle systems, and supporting design notes.

### Changes committed

| File | Change |
|------|--------|
| `.gitignore` | Ignore `.agents/` session state. |
| `.ημ/Π_STATE.sexp`, `.ημ/Π_LAST.md` | Handoff artifacts for this snapshot. |
| `dev/ecosystem.config.js` | Dev process tuning for `clj -M:dev`. |
| `docs/designs/gates-of-truth-world-gen-phases.md` | World generation phase design. |
| `docs/designs/truth-phase-0-stellar-nebula-design.md` | Phase 0 stellar nebula design. |
| `docs/notes/2026.06.25.16.41.16.md` | Session notes. |
| `src/domain/chemistry.clj` | Chemistry simulation primitives. |
| `src/domain/ecs/components.clj` | ECS component updates. |
| `src/domain/particles/fft.clj` | FFT particle helpers. |
| `src/domain/particles/field.clj` | Particle field simulation. |
| `src/domain/particles/phase0.clj` | Phase 0 particle dynamics. |
| `src/domain/particles/pm.clj` | Particle mesh / PM system. |
| `src/domain/phase0.clj` | Phase 0 top-level simulation. |
| `src/domain/player.clj` | Player entity records. |
| `src/domain/stellar.clj` | Stellar body generation. |
| `src/infra/dev/server.clj` | Dev server updates. |
| `src/infra/dev/window.clj` | Window/input updates. |
| `src/infra/main.clj` | Entry point updates. |
| `src/infra/render.clj` | Renderer updates. |
| `src/infra/render/phase0_renderer.clj` | Phase 0 nebula renderer. |
| `src/law/stellar.clj` | Malli schemas for stellar entities. |
| `test/domain/particles/*_test.clj` | Particle system tests. |
| `test/domain/phase0_test.clj` | Phase 0 tests. |

### Residual / ignored

- `.agents/` — local agent session state (now ignored).

## Blockers

None.
