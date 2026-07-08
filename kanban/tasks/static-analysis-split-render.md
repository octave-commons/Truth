---
uuid: "static-analysis-split-render"
title: "Split infra.render into Layered Sub-Modules"
status: "in_progress"
priority: "P1"
labels: ["specs", "static-analysis", "epic-static-analysis-cleanup", "architecture"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-split-render.md"
category: "specs"
estimate: 3
---

# Split infra.render into Layered Sub-Modules

> Parent spec: `kanban/tasks/static-analysis-structural-cleanup.md`
> Parent kanban: `kanban/tasks/static-analysis-structural-cleanup.md`

Move rendering concerns out of `infra.render` into layered sub-modules: math, mesh, color, HUD, field, volume, window, scene, and input. Keep the one-renderer invariant intact.

**Scope:**
- Create `infra.render.math` for GL-specific matrix helpers.
- Create `infra.render.mesh` for sphere/particle/sprite mesh generation.
- Create `infra.render.color` for color and material mapping.
- Create `infra.render.hud` for HUD primitives and text overlays.
- Absorb remaining scene field code into `infra.render.field`.
- Create `infra.render.volume` for volumetric ray-marching.
- Create `infra.render.scene` for `render-scene`, `render-bodies`, and body extraction.
- Create `infra.render.window` for GLFW bootstrap and window loop.
- Move render-side input setup into `infra.render.input` or `infra.input`.
- Keep `infra.render` as a thin orchestration + re-export facade.
- Reduce `infra.render` fan-out to below the hard threshold where possible.

**Done when:**
- Each new sub-module is below the HARD thresholds for LOC and public vars.
- `infra.render` no longer breaches HARD thresholds.
- The `one-renderer` architecture test still passes.
- `clojure -M:test` is green, including `test/architecture_test.clj`.
- Removed or moved public APIs have `^:deprecated` aliases during transition.
