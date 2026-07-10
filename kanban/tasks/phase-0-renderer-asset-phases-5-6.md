---
uuid: "phase-0-renderer-asset-phases-5-6"
title: "Phase 0 Renderer Asset Phases 5–6: Asset Cache, Passes, Materials"
status: "ready"
priority: "P2"
labels: ["specs", "phase0", "render"]
created_at: "2026-07-10T12:00:00Z"
source: "kanban/tasks/phase-0-renderer-asset-phases-5-6.md"
category: "specs"
estimate: 5
---

# Phase 0 Renderer Asset Phases 5–6: Asset Cache, Passes, Materials

> Parent: `kanban/tasks/phase-0-renderer-asset-organization-spec.md`
> Scope: the unbuilt Phases 5 and 6 from the parent spec. Phases 1–4
> (shader/mesh/hud/volume/projection) are already done.

**Goal:** Centralize GL resource lifecycle and describe each render pass with a
small material record, completing the renderer namespace split.

## Scope

1. Phase 5 — Introduce `infra.render.asset` and `infra.render.passes`.
   - `infra.render.asset`:
     - Program cache: `program-name → {id source-hash}`.
     - Mesh cache: `mesh-key → {vao vbo ebo count}`.
     - Texture cache: `texture-key → {id width height}`.
     - `invalidate-programs!`, `invalidate-meshes!`, `invalidate-all!`.
     - `dispose-asset!` and `dispose-all!` for GL teardown.
   - `infra.render.passes`:
     - Pass-state helpers: blend, depth, cull, uniform binding.
2. Phase 6 — Material definitions in `infra.render.material`.
   - Material record shape: `{:program :uniforms :mesh :blend :depth}`.
   - Replace ad-hoc uniform-setting per pass with material records.
3. Update `infra.render` to use the new namespaces.
   - All caches go through `infra.render.asset`.
   - Pass state goes through `infra.render.passes`.
   - Pass descriptions go through `infra.render.material`.

## Tests

- `asset-cache-hit-miss`: a program is cached and reused; invalidation causes
  recompilation.
- `asset-disposal-releases-gl-resources`: `dispose-all!` clears all caches.
- `passes-blend-state-helpers`: setting blend/depth/cull via helpers produces
  the expected GL state.
- `material-record-describes-body-pass`: a body pass can be represented as a
  material record and rendered.
- `architecture-test-passes`: single renderer invariant and domain purity hold.

## Out of scope

- Moving domain-projection caches (particle-cache, disk-cache, etc.) into
  `infra.render.asset`; those remain shape caches in `infra.render.projection`.
- File-based shader loading; keep strings in vars.
- Runtime uniform reflection.

## Done when

- `infra.render` is purely pass orchestration and under ~600 lines.
- `infra.render.asset`, `infra.render.passes`, and `infra.render.material` exist
  with clear responsibilities.
- `clojure -M:test` green.
- `test/architecture_test.clj` passes.
- Manual dev-window smoke test passes (start, render a few ticks, hot-reload).
- Parent card updated with link to this residual card.
