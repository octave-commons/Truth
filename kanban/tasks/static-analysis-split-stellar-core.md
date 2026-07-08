---
uuid: "static-analysis-split-stellar-core"
title: "Split domain.stellar Core Lifecycle Modules"
status: "done"
priority: "P1"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "architecture"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-split-stellar-core.md"
category: "specs"
estimate: 5
---

# Split domain.stellar Core Lifecycle Modules

> Parent spec: `kanban/tasks/static-analysis-structural-cleanup.md`
> Parent kanban: `kanban/tasks/static-analysis-structural-cleanup.md`

Move the core stellar lifecycle systems and helpers out of `domain.stellar` into topical sub-modules. This task covers the lifecycle heart of the file: thermodynamics, collapse, classification, sink formation, and post-collapse structure.

**Scope:**
- Create `domain.stellar.thermodynamics` from pure stellar helpers.
- Create `domain.stellar.collapse` for Jeans collapse and oblate-collapse physics.
- Create `domain.stellar.classifier` for matter-state classification (`classify-next-state`, `classifier-system`).
- Create `domain.stellar.sink` for sink/protostar formation and accretion zones.
- Create `domain.stellar.structure` for post-collapse structure, EOS, temperature, and merger handling.
- Keep `domain.stellar` as a thin `^:deprecated` re-export facade or remove it after migrating internal call sites.
- Preserve the ECS single-writer invariant: each component still has exactly one writer.
- Update `domain.genesis` wiring and any tests that refer to moved functions.

**Done when:**
- Each new sub-module is below the HARD thresholds for LOC and public vars.
- `reg/write-conflicts` is empty.
- No `domain/` namespace imports `infra/`.
- `clojure -M:test` is green, including `test/architecture_test.clj`.
- Removed or moved public APIs have `^:deprecated` aliases during transition.
