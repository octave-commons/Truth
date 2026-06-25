# Π Handoff — octave-commons/Truth

**Date:** 2026-06-25  
**Branch:** main  
**Tests:** `clj -M:test` → 55 tests, 120 assertions, 0 failures, 0 errors  
**License:** GNU GPL v3 or later (standalone application, per ημΠ.dev.v1 §7)

## What this snapshot contains

Pivot from terminal renderer to **LWJGL/OpenGL 3D renderer** while preserving the pure `shape`/`law`/`domain` stack.

### Changes committed

| File | Change |
|------|--------|
| `AGENTS.md` | Removed Lanterna/terminal-raycast references; project is now a full simulated universe with LWJGL rendering. |
| `deps.edn` | Added LWJGL 3.3.3 (core, GLFW, OpenGL, STB + Linux natives); added `:dev` alias (`infra.dev.server`) and `:run` alias (`infra.main`). |
| `.gitignore` | Ignore `.lsp/`, `hs_err_pid*.log`, and `receipts.log`; keep `.clj-kondo/.cache/` ignored but allow `.clj-kondo/imports/`. |
| `.clj-kondo/imports/metosin/malli/config.edn` | Imported clj-kondo config for Malli. |
| `dev/ecosystem.config.js` | PM2 ecosystem for the dev server (`clj -M:dev`). |
| `src/infra/main.clj` | Demo entry point: Sun/Earth/Moon world, renders a frame via `infra.render`. |
| `src/infra/render.clj` | LWJGL renderer: GLSL shaders, icosphere mesh, camera, interactive window + offscreen-to-PNG path. |
| `src/infra/dev/server.clj` | Dev server bootstrap (REPL/window helper). |
| `src/infra/dev/window.clj` | Window/input helpers for dev mode. |

### Residual / ignored

- `.clj-kondo/.cache/` — generated lint cache.
- `.lsp/.cache/db.transit.json` — LSP index.
- `hs_err_pid*.log` — JVM crash dumps.
- `receipts.log` — append-only session ledger (Receipt River).

## Blockers

None.
