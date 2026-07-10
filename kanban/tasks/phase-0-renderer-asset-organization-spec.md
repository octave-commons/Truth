---
uuid: "phase-0-renderer-asset-organization-spec"
title: "Phase 0 Renderer Asset Organization Spec"
status: "breakdown"
priority: "P2"
labels: ["specs", "phase0", "render", "render"]
created_at: "2026-07-02T19:35:28.971042782Z"
source: "kanban/tasks/phase-0-renderer-asset-organization-spec.md"
category: "specs"
---

# Phase 0 Renderer Asset Organization Spec

**Status:** draft  
**Date:** 2026-07-02  
**Companion docs:** `docs/reports/research/clojure-opengl-renderer-asset-organization.md`  

***

## 1. Goal

Stop `src/infra/render.clj` from becoming an everything-in-one-file monolith. Introduce a focused `infra.render.*` namespace layout for visual assets (shaders, meshes, materials/passes, projection, HUD, volume, resource lifecycle) while preserving the **single renderer** architecture invariant and keeping all rendering code in `infra/`.

***

## 2. Constraints

1. **Single renderer.** `test/architecture_test.clj` requires `infra.render` to remain the sole Phase 0 renderer. No `src/infra/render/phase0_renderer.clj`, no parallel renderer.
2. **Domain purity.** `domain/` must never import `infra/`.
3. **No junk drawers.** No `infra.render.utils`. Every namespace has a single responsibility.
4. **No new dependencies.** The renderer is already GL 3.3 core via LWJGL. Do not add `iglu`, `play-cljc`, or other shader libraries; borrow their *patterns* only.
5. **Behavior-preserving.** Each phase is a pure refactor with identical visuals and test outcomes.

***

## 3. Target Namespace Layout

```
src/infra/
  render.clj              ; public renderer API + pass orchestration
  render/
    shader.clj            ; shader program records, compile/link, program cache
    mesh.clj              ; sphere, fullscreen quad, particle/sprite/line builders
    material.clj          ; pass descriptions: body, line, sprite, hud, volume
    projection.clj        ; phase0-bodies-from-world, color ramps, LOD classification
    hud.clj               ; HUD rectangles, text, stats panel
    volume.clj            ; froxel baking, ray-march pass
    asset.clj             ; resource cache, creation, hot-reload, cleanup
    passes.clj            ; pass state helpers (blend/depth/uniform binding)
```

- `infra.camera` and `infra.inspect` stay unchanged.
- `infra.dev.window` and `infra.dev.server` continue to require `infra.render` directly.
- `domain/` continues to produce pure render shapes; it never imports `infra.render.*`.

***

## 4. Shader-as-Data Representation

Adopt a local data-shaped program record. Do **not** compile a Clojure DSL to GLSL; keep the GLSL source as strings inside the data structure.

Target shape:

```clojure
(def body-program
  {:name :body
   :version "330 core"
   :vertex {:inputs     {aPos vec3}
            :uniforms   {model mat4 view mat4 projection mat4}
            :outputs    {vNormal vec3 vWorldPos vec3}
            :source     "<raw GLSL vertex source>"}
   :fragment {:inputs   {vNormal vec3 vWorldPos vec3}
              :uniforms {color vec3 cameraPos vec3 glow float}
              :outputs  {FragColor vec4}
              :source   "<raw GLSL fragment source>"}})
```

Responsibilities:
- `infra.render.shader` defines this shape and a `compile-program!` function.
- `infra.render.shader` maintains a program cache keyed by source hash.
- `infra.dev.window/reload-shaders!` invalidates the cache so programs recompile.
- `infra.render` calls `(shader/use-program! state :body)` and sets uniforms by name.

Validation:
- A Malli schema for `::program` lives in `law/render.clj` (pure schema, no GL).
- `compile-program!` throws `ex-info` on compile/link failure with the GL log.

***

## 5. Asset Lifecycle

`infra.render.asset` owns:

1. **Program cache** — map of `program-name -> {id source-hash}`.
2. **Mesh cache** — map of `mesh-key -> {vao vbo ebo count}`.
3. **Texture cache** — map of `texture-key -> {id width height}`.
4. **Invalidation** — `invalidate-programs!`, `invalidate-meshes!`, `invalidate-all!`.
5. **Cleanup** — `dispose-asset!` and `dispose-all!` for graceful GL teardown.

Current scattered atoms (`particle-cache`, `disk-cache`, `phase0-bodies-cache`, `volume-cache`) stay where they are for this spec; they are domain-projection caches, not GL resource caches. They may move to `infra.render.projection` in a later phase.

***

## 6. Phased Implementation Plan

### Phase 1 — Extract `infra.render.shader`

**Goal:** Move shader definitions, compile/link, and program cache out of `infra.render`.

**Files created:**
- `src/infra/render/shader.clj`
- `src/law/render.clj` (Malli schemas for shader records)

**Files modified:**
- `src/infra/render.clj` — remove shader vars, `compile-shader`, `link-program`, `create-*-program`; use `infra.render.shader`
- `src/infra/dev/window.clj` — `ensure-resources` and `reload-shaders!` use the shader cache
- `src/infra/dev/server.clj` — if it creates programs directly, delegate to window/render
- `bench/gates_of_truth/bench/render.clj` — update benchmark setup

**Tests:**
- `test/infra/render_test.clj` — add tests that a shader record compiles to a valid program id and that cache invalidation works.
- `test/architecture_test.clj` — must still pass.
- `clj -M:test` — zero failures.
- `clj -M:splint` — no new warnings.

**Definition of done:**
- `infra.render` no longer contains inline shader strings or `create-*-program` functions.
- `infra.render.shader/compile-program!` can compile all existing shader pairs (body, particle, sprite, line, HUD, volume).
- Dev window still renders the same scene; hot-reload still works.

### Phase 2 — Extract `infra.render.mesh`

**Goal:** Move sphere, fullscreen quad, particle/sprite/line buffer builders into one namespace.

**Files created:**
- `src/infra/render/mesh.clj`

**Files modified:**
- `src/infra/render.clj` — use mesh builders from `infra.render.mesh`

**Tests:**
- `test/infra/render_test.clj` — add mesh creation tests (VAO ids positive, counts correct).

**Definition of done:**
- `infra.render` no longer contains mesh/VAO builder functions except orchestration glue.

### Phase 3 — Extract `infra.render.projection`

**Goal:** Move Phase 0 shape generation, color ramps, material logic, and LOD classification out of `infra.render`.

**Files created:**
- `src/infra/render/projection.clj`

**Files modified:**
- `src/infra/render.clj` — call `projection/phase0-bodies-from-world` and `projection/classify-body-lod`

**Tests:**
- Move existing projection tests from `test/infra/render_test.clj` to `test/infra/render/projection_test.clj`.

**Definition of done:**
- `infra.render` is purely pass orchestration; domain→shape mapping lives in `infra.render.projection`.

### Phase 4 — Extract `infra.render.hud` and `infra.render.volume`

**Goal:** Separate HUD/text and volumetric froxel/ray-march passes.

**Files created:**
- `src/infra/render/hud.clj`
- `src/infra/render/volume.clj`

**Files modified:**
- `src/infra/render.clj` — delegate HUD and volume passes

**Tests:**
- `test/infra/render/hud_test.clj` — pure text/triangulation tests.
- `test/infra/render/volume_test.clj` — pure froxel sample tests (no GL).

**Definition of done:**
- HUD and volume code no longer lives in `infra.render`.

### Phase 5 — Introduce `infra.render.asset` and `infra.render.passes`

**Goal:** Centralize resource cache/lifecycle and pass-state helpers.

**Files created:**
- `src/infra/render/asset.clj`
- `src/infra/render/passes.clj`

**Files modified:**
- All `infra.render.*` namespaces to use `asset` for caching and `passes` for GL state helpers.

**Tests:**
- `test/infra/render/asset_test.clj` — cache hit/miss and disposal tests.

**Definition of done:**
- A unified asset cache backs programs, meshes, and textures.
- Pass state (blend/depth/cull) is handled by `infra.render.passes` helpers.

### Phase 6 — Material definitions in `infra.render.material`

**Goal:** Replace ad-hoc uniform-setting for each pass with small material records.

**Files created:**
- `src/infra/render/material.clj`

**Files modified:**
- `infra.render` and sub-namespaces to use `material` records.

**Definition of done:**
- Each render pass is described by a material record (`{:program :uniforms :mesh :blend :depth}`).

***

## 7. Verification Strategy

After every phase:

1. `clj -M:test` — all tests pass.
2. `clj -M:splint` — no new lint warnings in changed files.
3. `test/architecture_test.clj` — single renderer and domain purity invariants hold.
4. Manual dev-window smoke test (start, render a few ticks, hot-reload shaders).

Final verification:
- `infra.render` is under ~600 lines and contains only orchestration.
- No `domain/` namespace imports `infra.`.
- `src/infra/render/phase0_renderer.clj` does not exist.

***

## 8. Open Questions

1. Should we keep shader source as strings in Clojure vars, or move them to `resources/shaders/*.glsl` files for artist iteration?  
   **Recommendation:** Keep strings in vars for Phase 1–2; defer file-based loading until artist iteration becomes the bottleneck.
2. Should `infra.render.asset` own the deterministic particle/disk caches currently in `infra.render`, or should those stay in `infra.render.projection`?  
   **Recommendation:** Those are shape caches, not GL resources; keep them in `infra.render.projection`.
3. Do we want runtime shader uniform reflection, or keep uniform locations explicit?  
   **Recommendation:** Keep explicit for now; add reflection only if uniform-set bugs accumulate.

---
Triage 2026-07-10 (todo→accepted): PARTIAL/ROADMAP — Phases 1-4 done (single-renderer split, shader/mesh/hud/volume, schemas); only asset-cache/passes/material Phases 5-6 remain — split residual card. Needs breakdown into residual ≤5pt cards before re-entering the queue.

Triage 2026-07-10: phases 1-4 done; asset-cache/passes/material remain. Moved to breakdown to split a residual ≤5pt card for phases 5-6.

Triage 2026-07-10: residual Phases 5-6 work split into child card phase-0-renderer-asset-phases-5-6 (ready, P2, 5pt). Parent stays in breakdown as umbrella.
---
