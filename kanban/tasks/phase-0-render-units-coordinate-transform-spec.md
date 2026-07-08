---
uuid: "phase-0-render-units-coordinate-transform-spec"
title: "Phase 0 Render Units / Coordinate Transform Spec"
status: "todo"
priority: "P2"
labels: ["specs", "phase0", "render"]
created_at: "2026-07-08T02:24:29.765675587Z"
source: "kanban/tasks/phase-0-render-units-coordinate-transform-spec.md"
category: "specs"
---

# Phase 0 Render Units / Coordinate Transform Spec

**Status:** draft  
**Date:** 2026-07-02  
**Parent spec:** `kanban/tasks/phase-0-renderer-asset-organization-spec.md` (Phase 3)  
**Companion research:** `docs/reports/research/unit-coordinate-transforms-game-render.md`

***

## 1. Goal

Introduce a small, pure, testable layer — `infra.render.units` — that owns every physical-unit ↔ render-unit ↔ screen-pixel transform in the Phase 0 renderer. Stop scattering `(/ x phase0-view-scale)` and reconstructed camera-basis math across `infra.render` and `infra.inspect`.

This spec is a **prerequisite slice** of Phase 3 (`infra.render.projection`) from the render-asset-organization spec. Projection converts ECS entities into render shapes; `infra.render.units` provides the coordinate math that projection (and inspect) uses.

***

## 2. Constraints

1. **Single renderer.** `test/architecture_test.clj` must still pass. No second renderer.
2. **Domain purity.** `domain/` continues to import nothing from `infra/`.
3. **No junk drawers.** `infra.render.units` has one responsibility: coordinate transforms. It does **not** own shaders, meshes, ECS queries, or GL state.
4. **Pure functions.** All public functions in `infra.render.units` are pure (no `!` suffix, no GL calls, no atoms).
5. **Behavior-preserving.** After the refactor, the dev window looks identical and `test/infra/render_test.clj` still passes.
6. **No new dependencies.** Reuse `shape.spatial` for vector math and `malli` schemas already on the classpath.

***

## 3. Definitions

| Term | Meaning | Example units |
|---|---|---|
| **World / physical** | ECS simulation coordinates. | metres [m] |
| **Render** | The intermediate coordinate space sent to OpenGL. | render units (1 ru = `phase0-view-scale` m) |
| **NDC** | Normalized device coordinates. | `[-1, 1]³` |
| **Screen** | Window pixel coordinates. | `[0, width)` × `[0, height)` |

Current canonical value: `infra.camera/phase0-view-scale` = `1.0e15` m/ru.

***

## 4. Target namespace layout

```
src/infra/
  camera.clj              ; per-phase view-scale constants + camera matrices
  render/
    units.clj             ; this spec
    projection.clj        ; ECS → render shapes (Phase 3 parent spec)
    ...
  inspect.clj             ; picking/selection overlay
src/shape/
  spatial.clj             ; pure vec3/AABB/plane math, unit-agnostic
src/law/
  render.clj              ; Malli schemas for RenderContext, RenderShape, Viewport
```

***

## 5. Data shapes (schemas in `law.render`)

Add to `src/law/render.clj`:

```clojure
(def viewport
  "Screen pixel rectangle."
  [:map
   [:width :int]
   [:height :int]])

(def render-context
  "Coordinate-transform context for one view."
  [:map
   [:scale :double]            ; physical metres per render unit
   [:camera :any]              ; infra.camera camera record/map
   [:viewport viewport]])

(def render-shape
  "A unit-agnostic shape ready for the renderer. All positions/radii are in
   render units; color is unitless RGB."
  [:map
   [:render-mode [:enum :body :sprite :particle :line :volume :hud]]
   [:position [:tuple :double :double :double]]
   [:radius {:optional true} :double]
   [:color {:optional true} [:tuple :double :double :double]]
   [:glow {:optional true} :double]
   [:label {:optional true} :string]])
```

***

## 6. API for `infra.render.units`

### 6.1 Records

```clojure
(defrecord RenderContext [scale camera viewport])
```

- `scale` — `double`, physical metres per render unit.
- `camera` — opaque camera value from `infra.camera` (carries eye, target, up, fov).
- `viewport` — `{:width int :height int}`.

### 6.2 Position transforms

```clojure
(defn world->render
  "[m] → [ru]. Divides each component by ctx.scale."
  [ctx pos]
  ...)

(defn render->world
  "[ru] → [m]. Multiplies each component by ctx.scale."
  [ctx pos]
  ...)
```

### 6.3 Radius transforms

```clojure
(def ^:private render-radius-ref
  "Physical radius [m] that maps to render-unit radius 1.0."
  3.0e13)

(defn phys->render-radius
  "Physical radius [m] → render-unit radius, log-compressed.
   Keeps a 5-order span legible while preserving monotonicity."
  [ctx r-phys]
  ...)

(defn render->phys-radius
  "Approximate inverse for debug/tooling only. Not for physics."
  [ctx r-render]
  ...)
```

The log-compression formula is the existing one from `infra.render/phys->render-radius`. Move it here verbatim; do not change visuals in this refactor.

### 6.4 Screen / ray transforms

```clojure
(defn render->screen
  "Render-unit point → {:x px :y py :depth clip-depth}." ; or return [px py depth]
  [ctx pos]
  ...)

(defn screen->render-ray
  "Pixel coordinates → {:ro render-pos :rd normalized-dir} for picking.
   Replaces the camera-basis reconstruction in infra.inspect."
  [ctx px py]
  ...)
```

`screen->render-ray` is the canonical inverse of the projection path used by the renderer. Keeping it in `infra.render.units` guarantees that picking and rendering share the same transform chain.

### 6.5 Context factory

```clojure
(defn make-context
  "Build a RenderContext from a camera and viewport.
   scale defaults to infra.camera/phase0-view-scale."
  ([camera viewport]
   (make-context infra.camera/phase0-view-scale camera viewport))
  ([scale camera viewport]
   (->RenderContext scale camera viewport)))
```

***

## 7. Refactor steps

### Step 1 — Add schemas

**Files modified:** `src/law/render.clj`

- Add `::viewport`, `::render-context`, and `::render-shape` schemas.
- Export a `valid-render-context?` helper.

**Tests:** `test/law/render_test.clj` (create if absent)
- Valid and invalid contexts validate as expected.

### Step 2 — Create `infra.render.units`

**Files created:** `src/infra/render/units.clj`

- Implement records and public functions above.
- Use `shape.spatial` for vector math (normalize, cross, dot).
- Do **not** import any `infra.render.*` or OpenGL namespaces.

**Tests:** `test/infra/render/units_test.clj`
- `world->render` then `render->world` round-trips within floating-point tolerance.
- `phys->render-radius` is monotonic.
- `phys->render-radius` of `3.0e13` m ≈ `1.0` ru.
- `screen->render-ray` produces a normalized direction vector.

### Step 3 — Migrate `infra.render` ad-hoc transforms

**Files modified:** `src/infra/render.clj`

- Replace inline `(/ pos scale)` calls with `(units/world->render ctx pos)` inside `phase0-bodies-from-world*` and friends.
- Replace `phys->render-radius` with `units/phys->render-radius`.
- Keep `body-draw-radius` special-case logic in `infra.render` (or move it to `infra.render.projection` later), but have it call the unit transform function.
- `volume-lights`, `gas-points`, `intervention-overlay-shapes` receive a `RenderContext` instead of a raw scale number.

**Tests:** `test/infra/render_test.clj` must still pass; update tests that asserted on raw radius values to use the unit function.

### Step 4 — Migrate `infra.inspect`

**Files modified:** `src/infra/inspect.clj`

- Replace the independent camera-basis reconstruction with `(units/screen->render-ray ctx px py)`.
- Replace `cursor->world` division with `units/render->world` or `units/world->render` as appropriate.
- `project-point` delegates to `units/render->screen`.

**Tests:** `test/infra/inspect_test.clj` (create if absent)
- A point projected to screen and back via the ray lands near the original point.

### Step 5 — Update callers

**Files modified:** `src/infra/dev/window.clj`, `src/infra/dev/server.clj` if they build projection contexts.

- Build `(units/make-context camera viewport)` once per frame and pass it down.

***

## 8. Verification strategy

1. `clj -M:test` — all tests pass.
2. `clj -M:splint` — no new warnings in changed files.
3. `test/architecture_test.clj` — single renderer and domain purity hold.
4. Dev window smoke test:
   - Start Phase 0 nebula.
   - Render a few ticks; confirm body positions, particles, and halos look identical.
   - Click to select a body; confirm the halo snaps to the same object as before.
5. Line-count sanity: `infra.render` loses the `phys->render-radius` helper and several inline scale divisions.

***

## 9. Definition of done

- `infra.render.units` exists and is pure (no GL, no atoms, no `!` public fns).
- All physical→render conversion in `infra.render` routes through `infra.render.units`.
- `infra.inspect` uses `infra.render.units/screen->render-ray` and `render->screen` instead of reconstructing the camera basis.
- `law.render` contains schemas for `RenderContext`, `Viewport`, and `RenderShape`.
- Tests cover round-trip, monotonicity, normalized ray direction, and screen↔render identity.
- `clj -M:test` and `test/architecture_test.clj` pass.
- Visual output is unchanged.

***

## 10. Open questions

1. Should `infra.render.units` own perspective/projection matrix construction currently in `infra.camera`?  
   **Recommendation:** No. Keep matrix math in `infra.camera`; `units` only converts between the spaces those matrices operate on.
2. Should the log-compressed radius function live in `units` or `projection`?  
   **Recommendation:** `units`, because it is a unit transform, not an ECS semantic decision. Star mass-based sizing stays in `projection`.
3. Should `infra.inspect` move into `infra.render.inspect` now or later?  
   **Recommendation:** Later, as part of the broader render-asset-organization Phase 3/4 split. This spec only updates its internals.

(End of file)
