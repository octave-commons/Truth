---
uuid: "static-analysis-splint-math"
title: "Splint cleanup: math interop bulk sweep"
status: "accepted"
priority: "P2"
estimate: 5
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-splint-math.md"
category: "specs"
---

# Splint cleanup: math interop bulk sweep

> Parent: `kanban/tasks/static-analysis-splint-idiom-cleanup.md`

Replace all `Math/*` interop with the equivalent `clojure.math` function across `src/` and `test/`. This is the bulk of the Splint idiom cleanup (323 of 404 warnings).

## Scope

- Address all `style/prefer-clj-math` findings.
- Add `(:require [clojure.math :as math])` to namespaces that need it, or use fully-qualified `clojure.math/...`.
- Tackle the top namespaces first: `domain/stellar.clj`, `infra/render.clj`, `domain/planet_formation.clj`, `domain/orbital/kepler.clj`, `law/stellar.clj`, `law/mass_transfer.clj`, `domain/genesis.clj`.
- Preserve numeric precision and return types; pay special attention to `Math/toRadians` → `clojure.math/to-radians`.
- Also fix the single `style/redundant-nested-call` finding that combines with this sweep.

## Done when

- `clojure -M:splint` reports ≤ 81 warnings.
- `clojure -M:test` passes.
- Hot-path namespaces (`domain.gravity.barnes-hut`, `domain.stellar`, `infra.render`) are benchmarked with `bin/bench` if their math-heavy paths changed.
