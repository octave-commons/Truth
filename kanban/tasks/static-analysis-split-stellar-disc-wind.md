---
uuid: "static-analysis-split-stellar-disc-wind"
title: "Split domain.stellar Disc, Wind, and Seeder Modules"
status: "in_progress"
priority: "P1"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "architecture"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-split-stellar-disc-wind.md"
category: "specs"
estimate: 3
---

# Split domain.stellar Disc, Wind, and Seeder Modules

> **Status update (2026-07-10, Claude Code — code-state review):** The split is
> **complete in code**; this card's `in_progress` status no longer reflects the
> tree. All target sub-modules exist under `src/domain/stellar/`:
> `disc.clj`, `disc_evolution.clj`, `seeder.clj`, `wind.clj`, `fusion.clj`
> (plus the earlier `classifier`, `collapse`, `geometry`, `merge`, `sink`,
> `structure`, `temperature`, `thermodynamics`). `src/domain/stellar.clj` is a
> thin re-export facade (docstring: "Thin facade over the split stellar
> sub-modules"). `clojure -M:test -n architecture-test` is **green** (6 tests /
> 23 assertions, 0 failures), which validates the "Done when" criteria that it
> covers: single-writer / empty `reg/write-conflicts`, no `domain/`→`infra/`
> import, and the LOC/public-var HARD thresholds. Not independently re-verified
> here: `^:deprecated` markers on the facade re-exports (they are plain `def`
> re-exports, not tagged `^:deprecated`) and the full `clojure -M:test` suite.
> **Recommend moving this card to `done`** after confirming those two.
>
> Parent spec: `kanban/tasks/static-analysis-structural-cleanup.md`
> Parent kanban: `kanban/tasks/static-analysis-structural-cleanup.md`

Move the remaining topical stellar systems out of `domain.stellar` into their own sub-modules. This task covers disc physics, disk evolution, condensation seeding, winds/ablation, and fusion/SED.

**Scope:**
- Create `domain.stellar.disc` for disc identification and kinematics.
- Create `domain.stellar.disc-evolution` for disk evolution and mass transfer.
- Create `domain.stellar.seeder` for condensation and planet seeding helpers.
- Create `domain.stellar.wind` for stellar winds, ablation, and flares.
- Create `domain.stellar.fusion` for fusion, SED, and irradiance heating.
- Keep `domain.stellar` as a thin `^:deprecated` re-export facade if it still exists.
- Preserve the ECS single-writer invariant.
- Update `domain.genesis` wiring and any affected tests.

**Done when:**
- Each new sub-module is below the HARD thresholds for LOC and public vars.
- `reg/write-conflicts` is empty.
- No `domain/` namespace imports `infra/`.
- `clojure -M:test` is green, including `test/architecture_test.clj`.
- Removed or moved public APIs have `^:deprecated` aliases during transition.
