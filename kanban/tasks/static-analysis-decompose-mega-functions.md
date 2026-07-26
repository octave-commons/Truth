---
uuid: "static-analysis-decompose-mega-functions"
title: "Decompose HARD Mega-Functions"
status: "done"
priority: "P1"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "architecture"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-decompose-mega-functions.md"
category: "specs"
estimate: 3
---

# Decompose HARD Mega-Functions

> Parent spec: `kanban/tasks/static-analysis-structural-cleanup.md`
> Parent kanban: `kanban/tasks/static-analysis-structural-cleanup.md`

Extract helpers and decompose the 11 HARD mega-functions (≥80 LOC) that are not already being split as part of the `domain.stellar` or `infra.render` namespace moves. Introduce context maps to reduce arity bloat for renderer draw calls and physics helpers.

**Scope:**
- Extract pure helpers from `menu-hud`, `setup-input`, `render-scene`, `planet-seeds`, `build-physics-soa`, `sink-accretion-flux-system`, `create-world`, and any remaining warn-level mega-functions.
- Replace nested conditionals in state-machine functions with data tables where possible.
- Introduce `render-ctx` / `shape-ctx` context maps for renderer draw calls and physics helpers; keep `^:deprecated` wrappers for old signatures.
- Ensure every extracted helper is ≤40 LOC and every parent function is ≤80 LOC after extraction.

**Done when:**
- No function in the touched files is ≥80 LOC.
- `bin/analyze` HARD function count ≤ 2 (the remaining ones handled by namespace splits).
- `clojure -M:test` is green, including `test/architecture_test.clj`.
- No public API is removed without a `^:deprecated` alias.

---
Triage 2026-07-10 (accepted→done): DONE per bin/analyze ground truth — 0 functions >=80 LOC (HARD); only warn-tier (>=40) remain — done-when HARD<=2 satisfied.
---

---
Regression notice 2026-07-24 — this card remains `done` (history is not rewritten), but **its finding has returned**. Two HARD mega-functions are back: `derive-edits` (`src/domain/voxel/carve.clj:406`) and `voxel-focus-system` (`src/domain/voxel/focus.clj:238`), both in files created after this card closed. Note the metric itself is also wrong — it counts docstrings as code (`dev/smell_report.clj:110`). New work: `kanban/tasks/static-analysis-regression-2026-07-24.md`. Do not read this `done` as evidence the tree is clean; verify with `bin/analyze --strict`.
---
