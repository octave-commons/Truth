---
uuid: "static-analysis-rationalize-genesis"
title: "Rationalize domain.genesis Fan-Out"
status: "done"
priority: "P1"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "architecture"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-rationalize-genesis.md"
category: "specs"
estimate: 2
---

# Rationalize domain.genesis Fan-Out

> Parent spec: `kanban/tasks/static-analysis-structural-cleanup.md`
> Parent kanban: `kanban/tasks/static-analysis-structural-cleanup.md`

Reduce `domain.genesis` to a bootstrapper-only facade. Move generic tick orchestration and Phase 0 content wiring into appropriate ECS or Phase 0 namespaces, lowering fan-out and clarifying the single-substrate architecture.

**Scope:**
- Move generic tick/pipeline code (`physics-systems-parallel`, `materialize-lifecycle`, `tick-world`, `step-physics`, `field-report`) into `domain.ecs.pipeline`, `domain.ecs.tick`, or `domain.phase0` as appropriate.
- Keep `domain.genesis` as the sole Phase 0 world bootstrapper: `seed-nebula`, `create-world`, `system-summary`/`stats-of`, and a thin `tick-world` delegate.
- Document each remaining dependency and why it is required.
- Ensure the single-substrate invariant holds: exactly one ECS world, one renderer, one bootstrapper.

**Done when:**
- `domain.genesis` fan-out is ≤ 18, or an explicit architectural exception is documented.
- `domain.genesis` remains the sole Phase 0 world bootstrapper (`single-ecs-substrate` architecture test passes).
- `clojure -M:test` is green, including `test/architecture_test.clj`.
- Removed or moved public APIs have `^:deprecated` aliases during transition.

---
Triage 2026-07-10 (accepted→done): DONE per bin/analyze ground truth — smell report HIGH FAN-OUT: none (genesis fan-out <18); single-ecs-substrate arch test green.
---
