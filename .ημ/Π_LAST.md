# Π Handoff — octave-commons/Truth

**Date:** 2026-06-28T20:00:00Z  
**Branch:** main  
**Tag:** Π-2026.06.28.2  
**Tests:** `clojure -M:test` → 198 tests, 3551 assertions, **0 failures**, 0 errors  
**License:** GNU GPL v3 or later (standalone application, per ημΠ.dev.v1 §7)

## What this snapshot contains

**Deep research infrastructure, static analysis tooling, and new research notebooks.**

### Core changes

- **Deep research skill** (`.agents/skills/deep-research/SKILL.md`): Full academic research protocol for the simulation — arxiv investigation, LaTeX, Clojure pseudocode, charts, validation against benchmarks, and promotion paths to `domain/` code.

- **AGENTS.md**: Added deep-research skill entry and the full Deep Research Actors table (7 actors: cosmology, geology, biology, atmosphere, physics, culture, coordinator) with dispatch commands and research output documentation.

- **Static analysis tooling**:
  - `bin/analyze` — Runs Splint (idiomatic-style linter), cljfmt (formatting), and kibit in sequence.
  - `deps.edn` — New `:splint` and `:cljfmt` aliases; bumped clojupyter from 0.3.8 → 0.4.332.
  - `.clj-kondo/config.edn` + `.clj-kondo/hooks/ecs_dsl.clj` — Custom clj-kondo hook for the ECS DSL patterns.
  - `.github/workflows/static-analysis.yml` — CI workflow running the analysis pipeline on push/PR.
  - `.jscpd.json` — Copy-paste detector configuration.
  - `docs/STATIC-ANALYSIS.md` — Documentation for the analysis toolchain.

- **`.gitignore` cleanup**: Removed stale `.agents/`, `.opencode/`, `.ημ/actors/` ignores (those are now tracked or documented as concurrent dirt); removed stale EOF/PY lines; added `target/`.

### Research output

| Notebook / Report | Domain | Content |
|-------------------|--------|---------|
| `docs/research/cosmology/primordial-nucleosynthesis-yields.md` | Cosmology | Big Bang nucleosynthesis yields, light element abundances |
| `docs/research/cosmology/bbn_yields.ipynb` | Cosmology | Computable BBN yields notebook |
| `docs/research/INDEX.md` | Cross-domain | Master index of all research notebooks |

### New notes

| File | Content |
|------|---------|
| `docs/notes/# Deep Research Brief_ Gates of Truth — Physics Si.md` | Physics simulation research brief (1.8 MB) |
| `docs/notes/modeling stellar merges and feeding.md` | Stellar merger and accretion modeling (135 KB) |

### Other

| File | Change |
|------|--------|
| `.agents/skills/clojupyter.md` | Deleted (replaced by deep-research skill and updated deps) |
| `.opencode/skill/agent-notes-splitter/SKILL.md` | Now tracked (removed from .gitignore) |
| `dev/smell_report.clj` | Code smell analysis report script |

### Concurrent / unowned dirt (left unstaged)

- `.ημ/actors/` — actor mailboxes/sessions/outboxes; not owned by this fork-tax session.

### Residual / ignored

- `.agents/` — local agent session state.
- `.cpcache/`, `.clj-kondo/.cache/`, `.lsp/` — Clojure tooling caches.
- `.nrepl-port`, `hs_err_pid*.log`, `receipts.log`, `receipts.edn` — runtime/transient artifacts.
- `debugging-*.jsonl` — debug traces.

## Verification notes

- `clojure -M:test` completed with **0 failures, 0 errors** out of 198 tests / 3551 assertions.
- All architecture invariants enforced by `test/architecture_test.clj` remain satisfied.

## Blockers

None.
