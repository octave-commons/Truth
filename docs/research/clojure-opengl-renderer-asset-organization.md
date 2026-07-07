# Research Report: Organizing Visual Assets in a Clojure OpenGL Renderer

**Project:** Gates of Truth  
**Date:** 2026-07-02  
**Scope:** Namespace/folder patterns, shader-as-data, asset lifecycle, LOD/particle systems for an LWJGL/GLFW/GL 3.3+ Clojure renderer.  
**Sources:** Current codebase (`src/infra/render.clj`, `src/infra/camera.clj`, `src/infra/dev/window.clj`, `src/infra/inspect.clj`, tests), GitHub surveys of `oakes/play-cljc`, `oakes/iglu`, `overtone/shadertone`, `Kimbsy/clunk`, `IGJoshua/s-expresso`.

---

## 1. Current State of `infra.render`

`src/infra/render.clj` is a ~2,200-line monolith that currently owns:

* 5 shader pairs (body, particle, sprite, line, HUD) plus a volumetric ray-march pair.
* Sphere mesh generation (`make-sphere-mesh`, `upload-mesh`).
* Particle/sprite/line buffer packing (`make-particle-mesh`, `upload-particle-mesh`, `upload-sprite-mesh`).
* Phase 0 render projection (`phase0-bodies-from-world`, `phase0-bodies+fields`, color ramps, material logic).
* Volumetric froxel texture baking (`build-volume-texture`, `frame-volume`, `render-volume`).
* HUD rectangles and text (`render-hud`, `render-text`, STBEasyFont triangulation).
* Input handling (`setup-input`, `action-palette`).
* Offscreen rendering and screenshots (`render-to-file`).

This works for the current dev window, but it violates the “no junk drawers” spirit of the quadrant architecture: a single file mixes GL resource management, domain projection, UI, and input. The architecture tests already enforce two hard constraints relevant here:

1. **Single renderer.** `test/architecture_test.clj` asserts that `infra.render` is the sole Phase 0 renderer and forbids a parallel `src/infra/render/phase0_renderer.clj`.
2. **Domain purity.** `domain/` namespaces must never import `infra/`.

These constraints are non-negotiable; any refactor must keep `infra.render` as the single entry point and keep all rendering code in `infra/`.

---

## 2. Patterns Found in the Clojure Ecosystem

### 2.1 `oakes/play-cljc` — separation by concern

`play-cljc` is the most complete public example of a Clojure/ClojureScript OpenGL/WebGL game library. Its source layout is:

```
src/play_cljc/
  gl/
    core.cljc        ; shader compile/link, uniform/attribute upload, render loop
    entities_2d.cljc ; pre-built 2D entity templates
    entities_3d.cljc ; pre-built 3D entity templates
    utils.cljc       ; buffer/VAO/program helpers
  instances.cljc     ; instanced rendering helper
  math.cljc          ; matrix/vector math
  primitives_2d.cljc ; 2D mesh generators
  primitives_3d.cljc ; 3D mesh generators
  transforms.cljc    ; model/view/projection helpers
```

Key pattern: **one file does not do everything.** `gl.core` knows how to take a *data description* of a shader + mesh + uniforms and turn it into GL state, but the actual geometry and game-specific rendering live elsewhere. The library’s `compile` function takes an entity map `{:vertex <iglu-map> :fragment <iglu-map> :attributes {...} :uniforms {...}}` and returns a compiled entity ready to render.

Citation: `oakes/play-cljc/src/play_cljc/gl/core.cljc`, lines 235–271 (the `compile` and `render` functions).

### 2.2 `oakes/iglu` — shaders as data

`iglu` is a companion library that turns Clojure data into GLSL strings. A shader is represented as:

```clojure
'{:version "300 es"
  :uniforms {u_matrix mat4}
  :inputs   {a_position vec4
             a_color    vec4}
  :outputs  {v_color vec4}
  :signatures {main ([] void)}
  :functions  {main ([]
                     (= gl_Position (* a_position u_matrix))
                     (= v_color a_color))}}
```

`iglu.core/iglu->glsl` compiles this to a GLSL source string. The library also provides an escape hatch: `:functions` can be a raw GLSL string for cases where data representation is awkward.

Citation: `oakes/iglu/src/iglu/examples.cljs`, lines 12–109.

Implication for Gates of Truth: shaders can be **declarative data** with named slots, which makes them easier to validate, hot-reload, and reason about than inline string literals scattered through `render.clj`.

### 2.3 `overtone/shadertone` — shader strings + live reload

`shadertone` takes the opposite approach for its use case (live-coded visuals paired with Overtone audio):

* Shaders are stored in `.glsl` files or atoms holding strings.
* `start` watches the active shader file and reloads it on save.
* Uniform values are communicated via atoms in `:user-data`.

This is optimized for **artist iteration**, not game architecture. It shows that file-based shader loading and atom-driven uniform updates are practical in Clojure/LWJGL, but it does not offer a structural model for meshes, materials, or passes.

Citation: `overtone/shadertone/README.md`, “Usage” and “Shader Inputs” sections.

### 2.4 `Kimbsy/clunk` — small, focused namespaces

`clunk` is an LWJGL-based 2D game engine with a clean separation:

```
src/clunk/
  core.clj     ; window + game loop
  shader.clj   ; compile/link/use programs, loads .vert/.frag from resources
  sprite.clj   ; sprite creation and drawing
  image.clj    ; texture loading
  scene.clj    ; scene switching
  input.clj    ; input handling
```

Notable patterns:

* `clunk.shader/default-shader-programs` returns a map of named programs (`::line`, `::solid-poly`, `::texture`).
* Shader source is loaded from `resources/shader/*.vert` and `*.frag` via `clojure.java.io/resource`.
* Each `use-*-shader` function uploads shader-specific uniforms and returns the program id.

Citation: `Kimbsy/clunk/src/clunk/shader.clj`, lines 83–128.

### 2.5 `IGJoshua/s-expresso`

A minimal LWJGL 3 Clojure engine skeleton. Less mature, but confirms the pattern of separating `core`, rendering, and asset namespaces under a single engine root.

---

## 3. Clojure-Specific Patterns for Shaders

Three representations are in use:

| Approach | Example project | Pros | Cons |
|---|---|---|---|
| **Raw strings** in vars | Current `infra.render` | Zero deps, trivial to start | No structure, hard to compose/hot-reload |
| **Data → GLSL** (iglu) | `iglu`, `play-cljc` | Composable, inspectable, can validate slots | Adds a dependency; ClojureScript-oriented; GLSL 3.3 desktop needs minor adaptation |
| **Resource files** (.glsl) | `clunk`, `shadertone` | Clean separation, artist-friendly, file-watch hot reload | File I/O in tests, needs resource path discipline |

For Gates of Truth, the best fit is a **hybrid**: adopt the *data shape* of `iglu` (a Clojure map describing the shader) but keep the GLSL body as strings inside the map, and compile with a small local function. This avoids a new dependency and file I/O, while gaining structure, named uniform/attribute documentation, and easier hot-reload.

Example target shape:

```clojure
(def body-shader
  {:name :body
   :version "330 core"
   :vertex {:inputs {aPos vec3}
            :uniforms {model mat4 view mat4 projection mat4}
            :outputs {vNormal vec3 vWorldPos vec3}
            :source "..."}
   :fragment {:inputs {vNormal vec3 vWorldPos vec3}
              :uniforms {color vec3 cameraPos vec3 glow float}
              :outputs {FragColor vec4}
              :source "..."}})
```

This is **not** full `iglu` syntax; it is a domain-specific program record that the renderer can compile, cache, and reload.

---

## 4. Asset Lifecycle Patterns

Across the surveyed projects, the lifecycle is:

1. **Define** — shader/map or file, mesh generator, texture path.
2. **Compile/Create** — `glCreateShader`, `glLinkProgram`, `glGenBuffers`, `glGenTextures`.
3. **Cache** — programs, meshes, textures are cached by a key so they are not recreated every frame.
4. **Bind/Use** — set uniforms, bind VAO/textures, draw.
5. **Hot-reload** — watch file or atom; invalidate cache entry; recreate on next use.
6. **Cleanup** — `glDeleteProgram`, `glDeleteBuffers`, `glDeleteVertexArrays`, `glDeleteTextures`.

Current Gates of Truth already does some caching:

* `particle-cache` and `disk-cache` store deterministic particle clouds per entity.
* `phase0-bodies-cache` stores the per-frame render projection.
* `volume-cache` stores the persistent 3D texture and host float array.

What is missing is a **unified resource cache** for programs and meshes. Right now `infra.dev.window/ensure-resources` manually checks each program slot and recreates it when nil, and `render-scene` creates/disposes a fullscreen quad VAO every frame. A small asset manager would centralize this.

---

## 5. LOD / Sprite Fallback / Particle Systems

Current implementation already has the right idea:

* `classify-body-lod` splits `:body` shapes into solids vs. screen-space sprites based on projected pixel diameter.
* Distant bodies become `GL_POINTS` sprites with clamped pixel size (`sprite-min-pixels` / `sprite-max-pixels`).
* Nebula/protostar gas is rendered via volumetric particles and a froxel texture.

ECS-friendly pattern observed in `play-cljc` and `clunk`: the renderer consumes **render shapes** (plain maps with `:render-mode`) produced from ECS components, rather than querying ECS directly inside draw calls. Gates of Truth already does this via `phase0-bodies-from-world`, which is the correct architectural seam.

For future scale, the pattern is:

* A **projection system** (pure, domain-aware) turns ECS entities into a sequence of render shapes.
* The **renderer** sorts/binns shapes by pass and mode, uploads batched buffers, and issues draw calls.
* LOD is applied to the shape list, not to ECS entities.

---

## 6. Recommended Namespace Layout

Keep `infra.render` as the single orchestrator, but split its responsibilities into focused sub-namespaces. This respects the “single renderer” invariant while eliminating the monolith.

```
src/infra/
  render.clj              ; render-scene, public API, pass orchestration
  render/
    shader.clj            ; shader program records, compile/link, program cache
    mesh.clj              ; sphere, fullscreen quad, particle/sprite/line buffer builders
    material.clj          ; pass descriptions: body, line, sprite, hud, volume
    projection.clj        ; phase0-bodies-from-world, color ramps, LOD classification
    hud.clj               ; HUD rectangles, text, stats panel
    volume.clj            ; froxel baking, ray-march pass
    asset.clj             ; resource cache, creation, hot-reload, cleanup
    passes.clj            ; pass state (blend/depth/uniforms) helpers
```

Notes:

* `infra.render` remains the only namespace the dev window and server require directly.
* `infra.camera` and `infra.inspect` stay as they are; they are already well-scoped.
* `infra.dev.window` should eventually own only window/service lifecycle; input could move to `infra.input`, but that is out of scope for this report.
* `domain/` continues to produce pure render shapes; it never imports `infra.render.*`.

---

## 7. Shader-as-Data Recommendation

**Adopt a local, data-shaped shader program representation, but do not add `iglu` as a dependency.**

Rationale:

* `iglu` is primarily targeted at WebGL/CLJS and adds a dependency to `deps.edn` for a small gain.
* The valuable part of `iglu` is the **structural insight**: a shader is a map with `:version`, `:inputs`, `:outputs`, `:uniforms`, and `:source`.
* Gates of Truth can implement a 50-line `infra.render.shader` namespace that compiles these maps to GLSL strings, validates attribute locations, and caches programs.
* Raw GLSL strings remain inside the data structure for complex fragment logic (e.g., the volumetric ray-marcher); this is the same escape hatch `iglu` provides.

This gives the maintainability benefits of shader-as-data without the dependency cost or ClojureScript-centric assumptions.

---

## 8. Concrete Next-Step Recommendation

The safest, highest-impact first refactor is to **extract shader management** from `infra.render`:

1. Create `src/infra/render/shader.clj`.
2. Define a `defprogram` or `->program` data shape for the existing 5 shader pairs (body, particle, sprite, line, HUD) plus the volume pair.
3. Move `compile-shader`, `link-program`, and program IDs into `infra.render.shader`.
4. Add a simple program cache keyed by shader source hash, so `reload-shaders!` in `infra.dev.window` invalidates the cache and recompiles.
5. Update `infra.render` to require `infra.render.shader` and call `(shader/compile-program! program-def)`.
6. Run the existing test suite (`clj -M:test`) and the render tests (`test/infra/render_test.clj`) to ensure no behavioral change.

This is a pure code-motion change with no simulation or visual change, so it is low-risk and immediately reduces `infra.render` by several hundred lines. After it lands, follow-up steps can extract mesh building (`infra.render.mesh`) and then the Phase 0 projection (`infra.render.projection`).

---

## 9. Summary Table

| Concern | Current location | Recommended location | Rationale |
|---|---|---|---|
| Shader source strings | `infra.render` top-level vars | `infra.render.shader` program records | Centralize compile/link/cache |
| Shader compile/link | `infra.render` | `infra.render.shader` | Single responsibility |
| Sphere / quad / buffer builders | `infra.render` | `infra.render.mesh` | Reuse across passes |
| Phase 0 render shapes | `infra.render` | `infra.render.projection` | Pure domain→render mapping |
| LOD classification | `infra.render` | `infra.render.projection` | Part of shape generation |
| HUD rects/text | `infra.render` | `infra.render.hud` | UI layer separation |
| Volume froxel + ray-march | `infra.render` | `infra.render.volume` | Complex pass isolated |
| Resource cache/lifecycle | scattered atoms | `infra.render.asset` | Unified create/cache/cleanup |
| Pass orchestration | `infra.render` | `infra.render` (kept) | Single renderer invariant |

---

## 10. Limitations

* This survey is based on public GitHub repositories and the current codebase as of 2026-07-02. Some smaller Clojure LWJGL projects may exist that were not surfaced by search.
* `s-expresso` is a skeleton with only one commit; it was included for completeness but does not offer mature patterns.
* No runtime benchmarks were performed; recommendations assume the current GL 3.3 forward-compatible core profile and single-threaded GL context remain in place.
