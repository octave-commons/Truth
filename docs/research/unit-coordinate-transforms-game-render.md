# Physical Units → Game/Render Units: Patterns and a Recommendation for Gates of Truth

**Status:** research report  
**Date:** 2026-07-02  
**Scope:** coordinate-space conventions, unit-conversion layers, heterogeneous-scale rendering, and Clojure precedents; grounded in the Gates of Truth codebase.

---

## 1. What we looked at

- **Codebase:** `src/infra/render.clj`, `src/infra/camera.clj`, `src/infra/inspect.clj`, `test/infra/render_test.clj`, `kanban/tasks/phase-0-renderer-asset-organization-spec.md`, `src/shape/spatial.clj`, `test/architecture_test.clj`.
- **External sources:** LearnOpenGL coordinate-systems tutorial (local → world → view → clip → NDC → screen), Unity/Unreal/Godot docs on units and transforms, Godot vector-math docs, `play-cljc` examples (entity transform composition), `clunk` LWJGL engine (pixel-space sprites), and general gamedev literature on LOD/scale.

---

## 2. Key patterns found

### 2.1 Coordinate-space conventions are consistent across engines

Every 3D pipeline uses the same sequence (LearnOpenGL, Unity, Unreal, Godot):

```
Local/Model space  --(model matrix)-->  World space
World space        --(view matrix)-->   View/Camera/Eye space
View space         --(projection)-->    Clip space
Clip space         --(perspective divide)-->  NDC [-1,1]³
NDC                --(viewport transform)-->  Screen pixels
```

**Takeaway:** Gates of Truth already follows this pipeline. The missing layer is an *explicit* intermediate space between physical simulation units (metres) and renderer units.

### 2.2 Heterogeneous entity scales are handled with three levers

Engines that render both astronomical and human-scale objects together (space sims, planetary engines) use:

1. **View-scale compression** — one constant (or per-zone constant) maps physical metres to a renderer-friendly unit.
2. **Non-linear size mapping** — log or power-law functions keep body radii legible when they span many orders of magnitude.
3. **LOD proxies** — distant bodies become screen-space sprites, impostors, or points; nearby bodies use meshes.

Gates of Truth already does all three, but the code is spread across `infra.render` and `infra.camera` and repeated inline.

### 2.3 Three patterns for unit-conversion layers

| Pattern | Description | Pros | Cons |
|---|---|---|---|
| **Explicit transform functions** | `(world->render ctx pos)`, `(phys->render-radius ctx r)` | Easy to test, explicit, composes with `->`/`->>` | Call sites must pass `ctx` |
| **Unit-aware records/types** | `Length` tagged with unit, operations dispatch on tag | Type-safe, hard to mix units | Heavyweight in dynamic Clojure |
| **View-scale constants** | Single `phase0-view-scale` divided everywhere | Minimal, fast, what the codebase already does | Easy to scatter and repeat |

**Takeaway:** For a Clojure game with a single renderer and one physics substrate, *explicit transform functions backed by a `RenderContext` record* is the sweet spot. It is more testable than raw constants and lighter than a full unit-type system.

### 2.4 Clojure examples

- **`play-cljc`** (oakes): transforms are pure functions (`play-cljc.transforms/translate`, `rotate`, `scale`, `project`) composed onto entity maps. No implicit globals; the transform chain is data.
- **`clunk`** (Kimbsy): 2D sprite positions are in framebuffer pixels; no world/render split — appropriate for a pixel-scoped engine, not for an astronomical sim.
- **Gates of Truth current style:** manual division by `phase0-view-scale` inside `phase0-bodies-from-world*`, `gas-points`, `volume-lights`, `intervention-overlay-shapes`, etc.

---

## 3. Current state in Gates of Truth

The codebase already has the right *behavior* but not a clean *boundary*:

- `infra.camera/phase0-view-scale` = `1.0e15` metres per render unit. This is the Phase 0 world→render compression factor.
- `infra.render/phys->render-radius` maps a physical radius to a render radius using a reference radius (`3.0e13` m → render unit 1.0) and log compression.
- `infra.render/body-draw-radius` is a special case for stars: mass-based sizing plus luminosity boost.
- `infra.render/phase0-bodies-from-world*` divides positions by `scale` in four separate places, and radii in three separate ways.
- `infra.inspect/screen->ray` and `project-point` rebuild the camera basis independently; `cursor->world` uses the same scale constant.
- `infra.render/classify-body-lod` computes screen-space diameter from render units and switches bodies to pixel-sized sprites.

The architecture invariants are respected: `domain/` never imports `infra/`, and there is a single renderer (`infra.render`). The refactor proposed below stays inside those invariants.

---

## 4. Recommended approach

Introduce a small, pure **unit/transform layer** in `infra/` and move all physical→render conversion into it. Keep the ECS world in physical units; the renderer consumes *render shapes*; the projection layer is the only place that knows both.

### 4.1 Core ideas

1. **One `RenderContext` record per frame/view** carries `scale`, `camera`, and `viewport`.
2. **Transform functions are pure and explicit:**
   - `world->render` / `render->world` for positions
   - `phys->render-radius` / `render->phys-radius` for radii
   - `render->screen` / `screen->render-ray` for picking
3. **Per-phase view scales stay as constants** in `infra.camera` (e.g. `phase0-view-scale`).
4. **The projection namespace (`infra.render.projection`) produces render shapes** using the transform layer; no caller does ad-hoc division.
5. **`shape.spatial` stays unit-agnostic** — it operates on abstract 3-vectors.

### 4.2 Why this fits Gates of Truth

- It aligns with the planned `infra.render.projection` split in `kanban/tasks/phase-0-renderer-asset-organization-spec.md`.
- It preserves the single-renderer and domain-purity invariants.
- It makes the current implicit assumptions testable (e.g. "a solar-radius body at 1 AU is X pixels across").
- It avoids the heaviness of a unit-type system while still making unit mixing obvious at the function-name level.

---

## 5. Proposed namespace/file responsibilities

```
src/infra/
  camera.clj              ; view/projection matrices, camera modes,
                          ; per-phase view-scale constants (phase0-view-scale)
  render/
    units.clj             ; RenderContext + world↔render↔screen transforms
    projection.clj        ; ECS → render shapes, color ramps, LOD classification
    inspect.clj           ; (moved from src/infra/inspect.clj)
                          ; picking, selection overlay, screen projection
    ... existing shader/mesh/hud/volume/asset/passes files
src/shape/
  spatial.clj             ; pure vec3/AABB math, no unit semantics
src/law/
  render.clj              ; Malli schemas for ::RenderShape, ::RenderContext, ::Viewport
```

- `infra.camera` owns the *view scale constants* and camera matrices.
- `infra.render.units` owns the *conversion algebra*.
- `infra.render.projection` owns the *semantic mapping* from ECS bodies to render shapes.
- `infra.render.inspect` owns *screen-space interaction* (picking, overlays).
- `shape.spatial` owns *coordinate-free geometry*.
- `law.render` owns *schemas/contracts*.

---

## 6. Concrete API sketch

### 6.1 `infra.render.units`

```clojure
(ns infra.render.units
  "Physical ↔ render ↔ screen unit transforms.
   All functions are pure and operate on a RenderContext record.
   No OpenGL, no ECS imports from infra/.")

(defrecord RenderContext
  "The single source of truth for coordinate transforms in one view."
  [scale camera viewport])

(defrecord Viewport
  [width height])

(defn world->render
  "Convert a physical position [m] to render units using ctx.scale."
  [ctx pos]
  (let [s (double (:scale ctx))]
    (mapv #(/ (double %) s) pos)))

(defn render->world
  "Convert a render-unit position back to physical [m]."
  [ctx pos]
  (let [s (double (:scale ctx))]
    (mapv #(* (double %) s) pos)))

(def ^:private render-radius-ref
  "Physical radius [m] that maps to render-unit radius 1.0.
   Moved here from infra.render so the transform is reusable."
  3.0e13)

(defn phys->render-radius
  "Physical radius [m] → render-unit radius, log-compressed.
   Keeps a 5-order span legible while preserving monotonicity."
  [ctx r-phys]
  (let [r (double (or r-phys 0.0))]
    (if (pos? r)
      (let [linear (/ r render-radius-ref)
            log-r  (* 0.42 (Math/log10 (max 1e-6 linear)))]
        (max (* 0.5 linear) (+ 0.01 log-r) 0.001))
      0.001)))

(defn render->phys-radius
  "Approximate inverse of phys->render-radius. Useful for debug overlays
   that need to display physical size from a render shape."
  [ctx r-render]
  ;; Inverse of the log-compression above; exact only for the linear regime.
  (* (double r-render) render-radius-ref))

(defn render->screen
  "Project a render-space point to pixel coordinates [px py depth].
   Inverse of screen->render-ray. Reuses the same basis as infra.inspect."
  [ctx pos]
  ...)

(defn screen->render-ray
  "Pixel coordinates → {:ro :rd} ray in render space.
   ro = camera position in render units; rd = normalized direction."
  [ctx px py]
  ...)
```

### 6.2 `infra.render.projection`

```clojure
(ns infra.render.projection
  "Turn the ECS world into render shapes. The only namespace that knows
   both physical components and render conventions.")

(defn phase0-bodies-from-world
  "Project Phase 0 matter entities into view-scaled render shapes.
   `ctx` is an infra.render.units/RenderContext."
  [world ctx]
  ... ;; uses (units/world->render ctx pos) and (units/phys->render-radius ctx r)
      ;; instead of inline division.
  )

(defn classify-body-lod
  "Split render shapes into solid bodies and screen-space sprites.
   Operates purely on render shapes + viewport."
  [shapes ctx]
  ...)
```

### 6.3 `infra.camera`

```clojure
(ns infra.camera
  "Camera math and Phase 0 view scale.")

(def ^:const phase0-view-scale
  "World metres per render unit for the Phase 0 view."
  1.0e15)

(defn make-context
  "Build a RenderContext from a camera and viewport."
  [camera viewport]
  (units/->RenderContext phase0-view-scale camera viewport))
```

### 6.4 `law.render`

```clojure
(ns law.render
  "Malli schemas for render pipeline data.")

(def RenderShape
  [:map
   [:render-mode [:enum :body :sprite :particle :line :volume]]
   [:position [:tuple :double :double :double]]
   [:radius {:optional true} :double]
   [:color {:optional true} [:tuple :double :double :double]]
   ...])

(def RenderContext
  [:map
   [:scale :double]
   [:camera :any]
   [:viewport [:map [:width :int] [:height :int]]]])
```

---

## 7. Risks and trade-offs

| Risk | Mitigation |
|---|---|
| **Plumbing `ctx` through every projection function** is more verbose than the current inline division. | Keep `ctx` construction in `infra.camera`; most callers already have a camera + viewport. The `->>`/`->` macros keep it readable. |
| **`phys->render-radius` is non-linear**, so an inverse is approximate. | Document that `render->phys-radius` is debug-only; never use it for physics. Physics stays in `domain/` in physical units. |
| **Special cases like star sizing** (`body-draw-radius`) leak semantic knowledge into the transform layer. | Keep star sizing in `infra.render.projection` as a *semantic* decision; the transform layer only knows the generic radius mapping. |
| **Moving `inspect` into `infra.render.inspect`** changes require paths. | Do it as part of the Phase 3/4 refactor in `kanban/tasks/phase-0-renderer-asset-organization-spec.md`; update tests atomically. |
| **Performance** of passing a record vs. a raw scale number. | The JVM will elide the record allocation in hot loops; measure if it becomes an issue. The current per-frame cost is dominated by particle-cloud generation, not unit math. |
| **Multiple future phases** may need different view scales. | The `RenderContext` pattern scales naturally: each phase defines a constant in `infra.camera` and builds its own context. |

---

## 8. Immediate next steps (without modifying files)

1. Capture this recommendation in a technical spec that references `kanban/tasks/phase-0-renderer-asset-organization-spec.md`.
2. Add Malli schemas for `RenderContext` and `RenderShape` to `law.render` first (spec-before-impl).
3. Extract the transform functions into `infra.render.units` as a behavior-preserving refactor.
4. Migrate `phase0-bodies-from-world*` and `inspect` to use the new layer, adding tests for:
   - round-trip `world->render` → `render->world`
   - monotonicity of `phys->render-radius`
   - screen→ray→screen identity for the same point
5. Keep `infra.camera/phase0-view-scale` as the single canonical constant; remove duplicated literal `1e15` values from other namespaces.

---

## 9. Summary

Gates of Truth already compresses astronomical physical units into render units via `phase0-view-scale` and log-compressed radii. The recommended evolution is to make that boundary explicit: a `RenderContext` record, a pure `infra.render.units` transform layer, and a single projection namespace that feeds render shapes to the renderer. This fits the existing ECS + single-renderer architecture, aligns with the planned render-namespace split, and makes the current assumptions testable without adding a heavy unit-type system.
