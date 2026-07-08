---
uuid: "static-analysis-structural-cleanup"
title: "Static Analysis Structural Cleanup"
status: "accepted"
priority: "P1"
labels: ["static-analysis", "architecture", "epic-static-analysis-cleanup"]
created_at: "2026-07-07T00:00:00Z"
source: "kanban/tasks/static-analysis-structural-cleanup.md"
category: "specs"
estimate: 21
---

# Spec: Structural Cleanup (Static Analysis)

**Parent epic:** `kanban/tasks/epic-static-analysis-cleanup.md` (Phase C — M2 Structural cleanup)  
**Status:** draft  
**Scope:** drive the structural-smell report from 14 HARD breaches and 81 undocumented public functions down to zero, without violating the quadrant law, the single-substrate invariant, or `test/architecture_test.clj`.

This is not a cosmetic pass. The current smell report flags namespaces that have accumulated too many responsibilities, functions that have grown into multi-page conditional blocks, and a public API surface that is largely undocumented. Each of those signals is a place where the next real bug can hide, and where new contributors must read implementation to understand intent.

***

## 1. Current findings (from `bin/analyze`)

The structural smell detector is implemented in `dev/smell_report.clj`. Thresholds are:

| Metric | warn | hard |
|--------|------|------|
| namespace LOC | 500 | 1200 |
| namespace vars | 30 | 60 |
| function LOC | 40 | 80 |
| arity (params) | 5 | 8 |
| fan-out (deps) | 18 | 30 |

Current report: **14 HARD breaches** and **81 undocumented public functions**.

### 1.1 God namespaces

Namespaces that breach the `warn` or `hard` thresholds for LOC or public-var count.

| Severity | Namespace | LOC | Public vars | File |
|----------|-----------|-----|-------------|------|
| HARD | `domain.stellar` | 2637 | 122 | `src/domain/stellar.clj` |
| HARD | `infra.render` | 2119 | 115 | `src/infra/render.clj` |
| HARD | `domain.ecs.components` | 204 | 92 | `src/domain/ecs/components.clj` |
| warn | `domain.stellar-test` | 792 | 36 | `test/domain/stellar_test.clj` |
| warn | `domain.em` | 748 | 37 | `src/domain/em.clj` |
| warn | `domain.genesis` | 731 | 23 | `src/domain/genesis.clj` |
| warn | `domain.gravity.barnes-hut` | 721 | 35 | `src/domain/gravity/barnes_hut.clj` |
| warn | `domain.hydro-test` | 644 | 34 | `test/domain/hydro_test.clj` |
| warn | `domain.integrator` | 619 | 25 | `src/domain/integrator.clj` |
| warn | `infra.dev.window` | 581 | 26 | `src/infra/dev/window.clj` |
| warn | `infra.menu` | 509 | 29 | `src/infra/menu.clj` |
| warn | `domain.ecology` | 491 | 46 | `src/domain/ecology.clj` |
| warn | `domain.player` | 426 | 38 | `src/domain/player.clj` |
| warn | `law.stellar` | 386 | 52 | `src/law/stellar.clj` |
| warn | `infra.camera` | 378 | 31 | `src/infra/camera.clj` |
| warn | `domain.planet-formation` | 330 | 31 | `src/domain/planet_formation.clj` |
| warn | `law.mass-transfer` | 320 | 39 | `src/law/mass_transfer.clj` |
| warn | `domain.formation-test` | 297 | 33 | `test/domain/formation_test.clj` |
| warn | `law.field` | 259 | 36 | `src/law/field.clj` |
| warn | `law.ecology` | 138 | 39 | `src/law/ecology.clj` |

This spec focuses on the three HARD namespaces, but the remediation strategy should also bring the warn-level namespaces under control where doing so is cheap and mechanical. Test namespaces are tracked separately and may be allowed larger thresholds if the project chooses to tighten `test/` limits later.

### 1.2 Mega-functions (≥80 LOC, HARD)

| Function | LOC | File | Primary responsibility |
|----------|-----|------|------------------------|
| `menu-hud` | 135 | `src/infra/menu.clj:357` | HUD layout and rendering for the in-game menu |
| `setup-input` | 126 | `src/infra/render.clj:601` | GLFW input mapping and camera/world callbacks |
| `render-scene` | 122 | `src/infra/render.clj:1832` | Main scene draw orchestration (bodies, fields, volume, HUD) |
| `planet-seeds` | 119 | `src/domain/planet_formation.clj:212` | Sub-grid planet seeding from disk conditions |
| `wind-ablation-system` | 106 | `src/domain/stellar.clj:2096` | Stellar wind ablation of nearby nebula parcels |
| `classifier-system` | 105 | `src/domain/stellar.clj:1093` | Matter-state classification (nebula → protostar → star) |
| `sink-formation-system` | 105 | `src/domain/stellar.clj:1325` | Protostar/sink formation and exclusion-zone logic |
| `build-physics-soa` | 99 | `src/domain/physics/cache.clj:320` | Structure-of-arrays cache construction for physics |
| `sink-accretion-flux-system` | 97 | `src/domain/mass_transfer.clj:154` | Gradual sink accretion flux computation |
| `classify-next-state` | 96 | `src/domain/stellar.clj:948` | State-transition decision for matter evolution |
| `create-world` | 93 | `src/domain/genesis.clj:249` | World bootstrap and system wiring |

Additional warn-level functions (40–79 LOC) are listed in the full report; they will be decomposed opportunistically as part of Phase 2.

### 1.3 Parameter-bloat functions (arity ≥5)

| Function | Arity | File | Notes |
|----------|-------|------|-------|
| `render-scene` | 7 | `src/infra/render.clj:1832` | Programs, mesh, camera, viewport, bodies, time |
| `acceleration-for-soa` | 6 | `src/domain/gravity/barnes_hut.clj:666` | BH tree + target + softening + theta + G + SOA |
| `acceleration` | 6 | `src/domain/gravity/barnes_hut.clj:701` | Same as above, legacy entry point |
| `oblate-collapse-shape` | 6 | `src/domain/stellar.clj:195` | Mass, angular momentum, radius, oblateness, collapse fraction, material |
| `defprojection` | 6 | `src/domain/ecs/dsl.clj:150` | DSL macro shape |
| `render-bodies` | 6 | `src/infra/render.clj:1955` | Programs, mesh, camera, viewport, bodies, time |
| `splat!` | 6 | `src/infra/render/field.clj:103` | Field particle splat parameters |
| `curl-estimate` | 5 | `src/domain/em.clj:36` | Vector-field stencil inputs |
| `thermal-step` | 5 | `src/domain/intervention.clj:190` | Thermal intervention parameters |
| `disk-regime-map` | 5 | `src/domain/stellar.clj:1639` | Disk regime map generation |
| `defsystem` | 5 | `src/domain/ecs/dsl.clj:127` | DSL macro shape |
| `defreaction` | 5 | `src/domain/ecs/dsl.clj:141` | DSL macro shape |
| `defaggregate` | 5 | `src/domain/ecs/dsl.clj:162` | DSL macro shape |
| `defrewind` | 5 | `src/domain/ecs/dsl.clj:175` | DSL macro shape |
| `classify-body-lod` | 5 | `src/infra/render.clj:496` | Body LOD classification |
| `setup-input` | 5 | `src/infra/render.clj:601` | Window, world-atom, camera, config, programs |
| `body-appearance` | 5 | `src/infra/render.clj:905` | Body appearance parameters |
| `render-volume` | 5 | `src/infra/render.clj:1739` | Volume rendering parameters |
| `frame-volume` | 5 | `src/infra/render.clj:1802` | Volume frame parameters |
| `halo-shapes` | 5 | `src/infra/inspect.clj:103` | Inspector halo shapes |
| `overlay-radius` | 5 | `src/infra/inspect.clj:154` | Inspector overlay radius |

### 1.4 High fan-out namespaces

| Namespace | Fan-out | Notes |
|-------------|---------|-------|
| `domain.genesis` | 27 | Orchestrates almost every Phase 0 system; structural because it is the bootstrapper |
| `infra.render` | 20 | Touches physics, domain, camera, input, and OpenGL in one file |

### 1.5 Undocumented public functions (81 total)

Grouped by namespace. The full list is reproduced here so the cleanup can be tracked to completion.

**`src/domain/ecology.clj`**
- `abiotic?`, `prebiotic?`, `prokaryotic?`, `eukaryotic?`, `multicellular?`, `complex?`

**`src/domain/ecs/dsl.clj`**
- `defcomponent`, `defevent`, `defsystem`, `defreaction`, `defprojection`, `defaggregate`, `defrewind`

**`src/domain/ecs/event.clj`**
- `new-event-id`

**`src/domain/ecs/ledger.clj`**
- `empty-ledger`, `append`

**`src/domain/ecs/tick.clj`**
- `removed?`

**`src/domain/gravity/barnes_hut.clj`**
- `internal-node?`, `leaf-node?`

**`src/domain/intervention.clj`**
- `cost-of`

**`src/domain/player.clj`**
- `can-afford?`, `set-focus`, `narrow-focus`, `widen-focus`, `drift`, `approach-focus`, `decoherence-state`, `can-interact?`, `put-observer`

**`src/infra/inspect.clj`**
- `fmt-mass`, `fmt-radius`, `state-label`

**`src/infra/render.clj`**
- `create-program`, `create-particle-program`, `create-sprite-program`, `create-line-program`, `create-hud-program`, `create-volume-program`, `make-sphere-mesh`, `upload-mesh`, `init-glfw`, `create-window`, `bodies-from-world`, `delete-volume`, `run-window`

**`src/law/ecology.clj`**
- `ecology-phase?`

**`src/law/field.clj`**
- `finite-number?`, `regime-tag?`

**`src/law/mass_transfer.clj`**
- `validate-accretion-radius`, `validate-accretion-rate`, `validate-binary-pair`, `validate-roche-lobe`, `validate-mass-transfer-rate`

**`src/law/render.clj`**
- `valid-program-def?`, `valid-render-context?`, `valid-render-shape?`, `valid-volume-config?`, `valid-volume-descriptor?`

**`src/shape/spatial.clj`**
- `vec3`

**Test namespaces** (32 total)
- `test/domain/ecs/dsl_test.clj`: `position?`, `velocity?`, `->collision`, `emit-collision`, `mark-collided`, `zero-velocity`
- `test/domain/ecs/ledger_test.clj`: `hp?`, `alive??`, `->took-damage`, `emit-took-damage`, `->died`, `emit-died`, `total-damage-dealt`, `damage-log`
- `test/domain/ecs/rewind_test.clj`: `->healed`, `emit-healed`, `->permanent-death`, `emit-permanent-death`, `apply-heal`, `undo-heal`
- `test/law/contract_test.clj`: `demo-position-shape`
- `test/law/registry_test.clj`: `demo-resource-contract`

Test helper functions are public by design (they are consumed by DSL macros or by other tests). The fix for these is to add docstrings identifying them as test fixtures or DSL-generated API, not to make them private.

***

## 2. Root-cause analysis

### 2.1 God namespaces

**`domain.stellar`** — This file has become the default home for every Phase 0 stellar/nebular concern. It currently contains:

- Pure thermodynamic helpers (`body-density`, `moment-of-inertia`, `oblateness-from-spin`, etc.)
- Disc identification (`disc-classify`, `disc-identification-system`)
- Collapse physics (`jeans-collapse-system`, `collapse-system`, `oblate-collapse-shape`)
- Matter-state classification (`classify-next-state`, `classifier-system`)
- Sink formation and accretion exclusion (`sink-formation-system`, `effective-accretion-radius`)
- Condensation seeding (`condensation-seeder-system`)
- Disk evolution (`disk-regime-map`, `disk-evolution-system`)
- Stellar winds and ablation (`stellar-wind-system`, `wind-ablation-system`, `stellar-flare-system`)
- Structural/evolution systems (`structure-system`, `temperature-system`, `eos-system`, `stellar-merge-handler`)
- Spawning helpers (`seed-clump`, `spawn-clump`, `sphere-radius`)

The root cause is not a single mistake; it is the absence of a boundary. Every new stellar feature was added to the file that already had the most stellar code, so the namespace became a vertical slice of the entire stellar lifecycle.

**`infra.render`** — The renderer mixes responsibilities that are naturally separate:

- Low-level OpenGL helpers (matrix math, shader program loading via `infra.render.shader`)
- Mesh generation (`make-sphere-mesh`, particle/sprite meshes)
- Input handling (`setup-input`, `action-for-key`)
- Color and material logic (`composition->material-color`, `body-appearance`)
- HUD and text rendering (`render-hud`, `render-text`, `observer-hud-text`, `controls-hud`)
- Field/volume rendering (`field-line-shapes`, `render-volume`, `frame-volume`)
- Scene orchestration (`render-scene`, `render-bodies`, `bodies-from-world`)
- Window lifecycle (`init-glfw`, `create-window`, `run-window`)
- Off-screen capture (`render-to-file`)

Again, the root cause is incremental accretion: new rendering paths were added to the file that already knew how to draw. There are already sub-modules (`infra.render.shader`, `infra.render.field`, `infra.render.units`), but the parent file still retains most of the implementation.

**`domain.ecs.components`** — This is a vocabulary namespace. It contains 92 component keyword definitions and almost no logic. It is flagged because the smell detector counts public vars, not LOC. A single vocabulary file with 92 keywords is a natural consequence of a rich ECS component model, but it is also a cross-cutting dependency that every system must import. The root cause is that all component keywords were defined in one place without subsystem grouping.

### 2.2 Mega-functions

The largest functions fall into three shapes:

1. **System functions with long case analysis.** `classifier-system`, `classify-next-state`, `sink-formation-system`, and `wind-ablation-system` are dominated by nested conditionals and per-state branching. They grew because each new matter state or physics regime added another branch rather than a new helper.
2. **Renderer orchestrators.** `render-scene`, `setup-input`, and `menu-hud` combine OpenGL state management, coordinate transforms, and policy decisions in one block. They are hard to unit-test and hard to reuse.
3. **Bootstrappers.** `create-world` and `build-physics-soa` wire many subsystems together. The wiring is legitimate, but the function is large because it does not delegate to named sub-steps.
4. **Sub-grid seeders.** `planet-seeds` implements a complex disk-to-planet heuristic in one place.

### 2.3 Parameter bloat

Most arity-5+ functions fall into two categories:

1. **Renderer draw calls** that pass the same context (programs, mesh, camera, viewport, bodies) repeatedly. `render-scene`, `render-bodies`, and `render-volume` are prime examples.
2. **DSL macros** (`defsystem`, `defreaction`, `defaggregate`, `defrewind`, `defprojection`) whose signatures are macro shapes, not ordinary function calls. These are not user-callable with positional arguments; they look like bloat in the report but are not bloat in practice.
3. **Physics helpers** that take independent scalar inputs (e.g., `oblate-collapse-shape` with mass, angular momentum, radius, oblateness, collapse fraction). These can be grouped into context maps.

### 2.4 High fan-out

`domain.genesis` must fan out: it is the Phase 0 bootstrapper and tick coordinator. Its 27 dependencies are a direct consequence of the project's decision to keep exactly one world model and one bootstrapper. However, the namespace currently both *wires* systems and *implements* some of them (e.g., `physics-systems-parallel`, `materialize-lifecycle`, `tick-world`). The fan-out is inflated because the file is doing coordination plus implementation.

`infra.render` fans out to 20 namespaces because it reads from physics, player, chemistry, and ecology to decide how to draw. Some of these dependencies are for color computation and could be moved into a small `infra.render.color` module, reducing the surface area of the main renderer.

### 2.5 Undocumented public functions

The 81 undocumented functions cluster in three areas:

1. **DSL macros and generated helpers** in `domain.ecs.dsl` and the test files that exercise them. These are public API but have no docstrings.
2. **Internal helpers promoted to public** by omission (e.g., `create-program`, `make-sphere-mesh`, `vec3`). They are useful but undocumented.
3. **Test fixture functions** generated by DSL macros. These are flagged as public because the macros emit `def`/`defn`, but their publicness is a testing artifact.

***

## 3. Remediation strategy

### 3.1 God namespaces — split by lifecycle and rendering layer

Use the quadrant law (`src/domain/`, `src/infra/`, `src/shape/`, `src/law/`) and the existing sub-module pattern. No `utils/` or `helpers/` namespaces. Every new namespace must have a single, well-named responsibility.

**`domain.stellar` → split into `domain.stellar.*` sub-modules**

Move ECS systems and their pure helpers into topical namespaces. Keep `domain.stellar` as a thin public facade that re-exports the most common names, or remove it once call sites are migrated. The split should follow the physical lifecycle of a stellar body:

- `domain.stellar.thermodynamics` — pure helpers: `body-density`, `moment-of-inertia`, `oblateness-from-spin`, `equivalent-radius`, `oblate-density`, `oblate-moment-of-inertia`, `rotation-axis`, `spin-from-angular-momentum`, `spin-from-angular-momentum-oblate`, `virial-temperature`, `effective-temperature`, `self-gravity-pressure`, `radiative-cooling-delta`, `sound-speed`, `compression-heating`.
- `domain.stellar.disc` — disc identification and kinematics: `disc-classify`, `disc-identification-system`, `in-disc?`, `disk-radius`, `toomre-q`, `cooling-time-ratio`, `disc-regime`, `disk-regime-map`, `disk-viscous-timescale`, `resolvable-orbit-radius`.
- `domain.stellar.collapse` — gravitational collapse and Jeans physics: `jeans-unstable?`, `jeans-length`, `jeans-collapse-system`, `collapse-system`, `gravitational-collapse-rate`, `oblate-collapse-shape`.
- `domain.stellar.classifier` — matter-state classification: `classify-next-state`, `classifier-system`, `complexity-score`, `entity->region`, `contraction-stalled?`.
- `domain.stellar.sink` — sink/protostar formation and accretion zones: `sink-formation-system`, `sink-exclusion-zones`, `within-existing-sink?`, `bondi-radius`, `pending-absorbed-mass`, `effective-accretion-radius`, `resolution-feeding-zone-factor`, `absorb-packets`.
- `domain.stellar.seeder` — condensation and planet seeding: `condensation-seeder-system`, `condense-tick?`, `seed-clump`, `spawn-clump`.
- `domain.stellar.disc-evolution` — disk evolution and mass transfer: `disk-evolution-system`, `disk-evolution-pass`, `put-tracked`.
- `domain.stellar.wind` — winds, ablation, and flares: `stellar-wind-system`, `wind-ablation-system`, `stellar-flare-system`, `stellar-feedback-temperature`, `wind-direction`, `ablation-for-parcel`, `nearby-nebula-parcels`, `merge-ablation`, `compute-wind-profile`, `wind-launch-speed`, `wind-corona-temperature`.
- `domain.stellar.structure` — post-collapse structure and merger: `structure-system`, `temperature-system`, `eos-system`, `resolved-shape`, `sphere-radius`, `debris-material-density`, `planet-material-density`, `stellar-merge-handler`, `shatter-bodies`.
- `domain.stellar.fusion` — fusion and SED: `fusion-rate`, `fusion-promotion-system`, `luminosity-from-fusion`, `star-luminosity`, `irradiance-at`, `radiation-equilibrium-temperature`, `radiation-heating-delta`, `sed-heating-delta`, `stellar-sed-system`, `deuterium-depletion-system`, `atmosphere-from-teff`, `atmosphere-shells-system`.

**`infra.render` → split into `infra.render.*` sub-modules**

- `infra.render.math` — renderer-specific matrix helpers (`translation-matrix`, `scale-matrix`, `oblate-scale-matrix`, `rotation-align-z`, `mat4*`, `model-matrix`). Note: these are render-specific column-major GL helpers; generic vector math stays in `shape.spatial`.
- `infra.render.mesh` — sphere, particle, and sprite mesh generation (`make-sphere-mesh`, `upload-mesh`, particle/sprite mesh helpers).
- `infra.render.hud` — HUD primitives, text, and overlays (`render-hud`, `render-text`, `hud-text-from-world`, `observer-hud-text`, `controls-hud`, `view-bar-hud`, HUD helpers from `infra.menu`).
- `infra.render.color` — color/material mapping (`body-color`, `tint-color`, `temp-color`, `disk-temp-color`, `body-brightness`, `composition->material-color`, `body-render-color`, `body-appearance`, `stellar-spectral-color`, `coherence-color`, `afford-colors`).
- `infra.render.field` — field-line and nebula-fog rendering (`field-line`, `field-line-shapes`, `nebula-fog`, `player-overlay-shapes`, `hud-rects-from-world`). This already exists but can absorb more of the scene-specific field code.
- `infra.render.volume` — volumetric ray-marching (`render-volume`, `frame-volume`, `build-volume-texture`, `volume-lights`, `create-volume-program`, `delete-volume`, volume cache helpers).
- `infra.render.scene` — `render-scene`, `render-bodies`, `phase0-bodies-from-world`, `phase0-bodies+fields`, `bodies-from-world`.
- `infra.render.window` — GLFW/bootstrap (`init-glfw`, `create-window`, `run-window`, `render-to-file`).
- `infra.render.input` — move `setup-input`, `action-for-key`, `move-focus-by`, `player-key` into `infra.input` if it does not already belong there, or keep in `infra.render.input` if it is tightly coupled to render state.

**`domain.ecs.components` → split into `domain.ecs.components.*` sub-modules**

The component vocabulary is a deliberate cross-cutting concern. Splitting it into many sub-modules would fragment the single vocabulary that every ECS system shares and would raise the risk of duplicate or divergent keyword definitions. Instead, `domain.ecs.components` should remain a single vocabulary namespace, but it should be treated as a structural exception and documented as such. The remediation for this namespace is therefore:

- **Document it as an exception** in `dev/smell_report.clj` and `docs/STATIC-ANALYSIS.md`: vocabulary namespaces that contain only keyword/component definitions are exempt from the public-var HARD threshold, but not exempt from the LOC threshold.
- **Add docstrings** to every component keyword definition so the namespace is not also flagged for undocumented public vars. If `defcomponent` does not emit docstrings, extend it so it does.
- **Do not split** the component vocabulary into sub-modules unless a future architecture review explicitly decides to do so and updates `test/architecture_test.clj` to reflect the new boundary.

If a future split is desired, the alternative would be `domain.ecs.components.*` sub-modules. That is out of scope for this cleanup and is left as an open question in §8.

### 3.2 Mega-functions — decompose and extract

For each HARD mega-function, apply one of the following patterns:

1. **Extract pure helpers.** Pull out self-contained computations that do not depend on the surrounding `let` bindings (e.g., the per-branch state calculations in `classify-next-state`).
2. **Extract state-machine tables.** Replace nested `cond`/`case` in `classify-next-state` and `classifier-system` with a data table of transition predicates and target states. This is the preferred pattern for the single-substrate architecture: physics becomes data, not code.
3. **Extract rendering passes.** `render-scene` should delegate to `render-bodies-pass`, `render-field-pass`, `render-volume-pass`, `render-hud-pass`, each of which is a named function with a context map.
4. **Extract input binding tables.** `setup-input` should build a key→action map and then install it, rather than inline every callback.
5. **Extract sub-grid steps.** `planet-seeds` should be broken into disk-condition query, filter predicates, and seed-generation helpers.

Soft rule: every extracted helper must be ≤40 LOC, and the parent function should be ≤80 LOC after extraction. If the parent is still >80 LOC, split it into two functions with distinct responsibilities.

### 3.3 Parameter bloat — introduce context maps

For renderer draw calls, introduce a `render-ctx` map:

```clojure
(def RenderContext
  [:map
   [:programs [:map-of keyword? int?]]
   [:mesh any?]
   [:camera any?]
   [:width pos-int?]
   [:height pos-int?]
   [:bodies [:sequential any?]]
   [:time number?]])
```

`render-scene` becomes `(render-scene render-ctx)`; `render-bodies` becomes `(render-bodies render-ctx)`. This is a breaking API change, so keep the old signatures as `^:deprecated` thin wrappers during the transition.

For DSL macros, the arity is a macro shape, not positional bloat. The remediation is to document each macro's shape and, if possible, simplify the macro internals. The macros do not need context maps because they are compile-time forms.

For physics helpers like `oblate-collapse-shape`, introduce a `shape-ctx` map or use the existing entity/region maps. Do not introduce a generic map just to reduce arity; the map must have a named schema.

### 3.4 High fan-out — reduce and rationalize

`domain.genesis` fan-out is partially legitimate, but we can reduce it by:

1. Moving the remaining implementation functions (`physics-systems-parallel`, `materialize-lifecycle`, `tick-world`) into `domain.ecs.tick` or `domain.ecs.pipeline` if they are generic, or into the appropriate domain owner if they are physics-specific.
2. Introducing a `domain.phase0` namespace that is the *content layer* over the ECS substrate, as described in AGENTS.md. `domain.genesis` should become the bootstrapper only; the Phase 0 content layer should live in `domain.phase0` or in topical namespaces.
3. Keeping `domain.genesis` as the fan-out owner if the architecture intentionally centralizes wiring there, but documenting each dependency and why it is required.

`infra.render` fan-out is reduced by the namespace split: the main scene renderer should only depend on `infra.render.*` sub-modules, `infra.camera`, `infra.input`, and a small set of domain read-only functions (`domain.genesis/stats-of`, `domain.player/...`). Color and material logic moves to `infra.render.color`, which can depend on `domain.chemistry` and `domain.ecology` without dragging the whole renderer along.

### 3.5 Undocumented public functions — add docstrings or mark private

For every undocumented function:

1. **Public API** (used from other namespaces or tests): add a docstring with a one-line summary and, if needed, a detail paragraph.
2. **Pure internal helper** (no external usage): add `^:private` or move to a `-impl` namespace. If the function is currently public but only used inside its own namespace, make it private.
3. **DSL-generated test fixtures** (e.g., `position?`, `hp?`, `->collision`): add a docstring explaining that the var is generated by `defcomponent`/`defevent` and is a test fixture.
4. **Macro-generated constructor functions** (e.g., `emit-collision`, `mark-collided`): add docstrings in the macro or in the generated code. The `defcomponent`/`defevent` macros should emit docstrings automatically if they do not already.

***

## 4. Proposed new namespace/module boundaries

The split must respect the quadrant law and the single-substrate invariant. The following boundaries are proposed as the target state after the cleanup.

### 4.1 `domain.stellar` → topical sub-modules

```
src/domain/stellar.clj                  # thin facade or removed after migration
src/domain/stellar/thermodynamics.clj
src/domain/stellar/collapse.clj
src/domain/stellar/classifier.clj
src/domain/stellar/sink.clj
src/domain/stellar/seeder.clj
src/domain/stellar/disc.clj
src/domain/stellar/disc_evolution.clj
src/domain/stellar/wind.clj
src/domain/stellar/structure.clj
src/domain/stellar/fusion.clj
```

Each sub-module owns a single-writer component set where applicable. For example, `domain.stellar.classifier` remains the sole writer of `c/matter-state`; `domain.stellar.structure` remains the sole writer of `c/radius`, `c/density`, `c/oblateness`, `c/rotation-axis`.

### 4.2 `infra.render` → layered sub-modules

```
src/infra/render.clj                    # scene orchestration + re-exports
src/infra/render/math.clj               # GL-specific matrix helpers
src/infra/render/mesh.clj               # sphere / particle / sprite meshes
src/infra/render/color.clj              # body color, material, temperature mapping
src/infra/render/hud.clj                # HUD primitives, text, overlays
src/infra/render/field.clj              # field lines + nebula fog (already exists)
src/infra/render/volume.clj             # volumetric ray-marching
src/infra/render/scene.clj              # render-scene / render-bodies
src/infra/render/window.clj             # GLFW bootstrap and window loop
src/infra/render/input.clj              # render-side input setup (or merge into infra.input)
```

The existing `infra.render.shader` and `infra.render.units` sub-modules remain unchanged.

### 4.3 `domain.ecs.components` — documented exception, not split

`src/domain/ecs/components.clj` remains the single canonical vocabulary namespace for ECS component keywords. It is exempt from the public-var HARD threshold by explicit policy in `dev/smell_report.clj` and `docs/STATIC-ANALYSIS.md`. Every public var in this file must carry a docstring.

No new `src/domain/ecs/components/*.clj` files are introduced as part of this cleanup. A future architecture review may decide to split the vocabulary, but that is a separate, higher-risk change with its own spec.

### 4.4 `domain.genesis` → bootstrapper-only facade

Move generic tick orchestration into `domain.ecs.pipeline` (or reuse `domain.ecs.tick` if appropriate). Move Phase 0 content wiring into `domain.phase0`. `domain.genesis` should retain only:

- `seed-nebula` and its helpers
- `create-world` (the bootstrapper)
- `system-summary` / `stats-of` (observables)
- `tick-world` (thin delegate)

The `physics-systems-parallel`, `materialize-lifecycle`, `step-physics`, and `field-report` functions should move to the tick/pipeline layer or to their topical owners.

### 4.5 No `utils/` or `helpers/`

All cross-cutting helpers must land in a quadrant-appropriate namespace:

- Pure physics/math → `law.*` or `shape.*`
- ECS substrate → `domain.ecs.*`
- Rendering → `infra.render.*`
- Validation/schemas → `law.*`

If a helper does not fit, either the general mechanism grows or a new topical namespace is created with a single responsibility.

***

## 5. Phased execution plan

This work belongs to the parent epic's **M2 — Structural cleanup**. It should be executed as a series of small PRs, one namespace or one function at a time, with full test runs after each.

| Phase | Name | Work | Exit criteria |
|-------|------|------|---------------|
| P1 | **Document and privatize** | Add docstrings to all 81 undocumented public functions; mark truly internal helpers as `^:private`; document DSL-generated test fixtures. | `bin/analyze` reports zero undocumented public functions; tests green. |
| P2 | **Decompose mega-functions** | Extract helpers from the 11 HARD mega-functions; introduce `render-ctx` and similar context maps; reduce arity where the function is user-facing. | No function in the touched files is ≥80 LOC; `bin/analyze` HARD function count ≤ 2; tests green. |
| P3 | **Component vocabulary exception** | Add docstrings to every component keyword in `domain.ecs.components`; update `dev/smell_report.clj` to exempt vocabulary namespaces from the public-var HARD threshold; document the exception in `docs/STATIC-ANALYSIS.md`. | `domain.ecs.components` has zero undocumented public vars and is exempt from the public-var HARD threshold by policy; `test/architecture_test.clj` still passes. |
| P4 | **Split `domain.stellar`** | Move topical systems and helpers into `domain.stellar.*` sub-modules; keep `domain.stellar` as a thin facade; update `domain.genesis` and tests. | `domain.stellar` and all sub-modules below `hard` thresholds; no `domain.*` namespace imports `infra.*`; tests green. |
| P5 | **Split `infra.render`** | Move mesh, color, HUD, field, volume, window, and scene code into `infra.render.*` sub-modules; reduce `infra.render` fan-out. | `infra.render` and all sub-modules below `hard` thresholds; `one-renderer` architecture test still passes; tests green. |
| P6 | **Rationalize fan-out** | Reduce `domain.genesis` to bootstrapper; move generic tick/pipeline code into `domain.ecs.pipeline` or `domain.phase0`; document remaining dependencies. | `domain.genesis` fan-out ≤ 18, or explicit architectural exception documented; `domain.genesis` still the sole Phase 0 bootstrapper; tests green. |
| P7 | **Final validation and gating** | Run `bin/analyze --strict`, `bin/bench`, and the full test suite; tighten thresholds in `dev/smell_report.clj` if desired; update `docs/STATIC-ANALYSIS.md`. | `bin/analyze --strict` passes; `clojure -M:test` green; `docs/STATIC-ANALYSIS.md` updated. |

**Dependency order:** P1 and P2 are independent and can be done in parallel. P3 (component vocabulary exception) is independent of P4 and P5. P4 and P5 are independent after P3. P6 depends on P4. P7 is final.

***

## 6. Acceptance criteria

- [ ] `bin/analyze --strict` reports **zero HARD structural breaches**.
- [ ] `bin/analyze` reports **zero undocumented public functions**.
- [ ] `clojure -M:test` is green, including `test/architecture_test.clj`.
- [ ] No `domain/` namespace imports any `infra/` namespace (enforced by `architecture_test.clj` `domain-never-imports-infra`).
- [ ] There is still exactly one Phase 0 renderer (`infra.render` or its direct sub-modules); no orphan renderer namespace is created (enforced by `architecture_test.clj` `one-renderer`).
- [ ] The ECS single-writer invariant still holds: `reg/write-conflicts` is empty (enforced by `architecture_test.clj` `single-writer-ownership-holds`).
- [ ] `domain.genesis` remains the sole Phase 0 world bootstrapper (enforced by `architecture_test.clj` `single-ecs-substrate`).
- [ ] Every public function that existed before the cleanup either (a) has a docstring, (b) is marked `^:private`, or (c) has a `^:deprecated` alias in the old namespace pointing to the new location.
- [ ] If a hot-path namespace was touched, `bin/bench` shows no regression vs. the pre-cleanup baseline.
- [ ] `docs/STATIC-ANALYSIS.md` is updated with the new namespace map, the threshold policy, and any intentional exceptions (e.g., `domain.genesis` fan-out).

***

## 7. Risks and mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Splitting namespaces changes public API** | High | Keep old namespaces as thin `^:deprecated` re-export facades during the transition. Migrate internal call sites first; external consumers second. Do not remove facades until the next release cycle. |
| **Cross-quadrant import violations** | High | Run `test/architecture_test.clj` after every PR. If a new namespace logically needs a dependency from another quadrant, introduce a `law.*` contract/schema namespace rather than importing across the boundary. |
| **Single-writer invariant breakage** | High | After any component-related move, run `domain.ecs.registry/write-conflicts` and `assert-single-writer!`. Verify that each component still has exactly one fan-out writer. |
| **Performance regression from namespace hops** | Medium | Namespace splits do not add runtime cost, but context-map creation can. Measure hot paths with `bin/bench` before and after. Do not decompose hot-path functions in ways that increase allocation or loop nesting. |
| **Tests break due to macro-generated fixtures** | Medium | The DSL macros generate test vars. If the macro shape changes, update the clj-kondo hooks in `.clj-kondo/hooks/ecs_dsl.clj` and the tests that reference generated vars. Add tests for the new namespace boundaries. |
| **Large merge conflicts** | Medium | Keep PRs small (one namespace per PR). Coordinate with other active work on the same files (e.g., Phase 0 physics features). Use the parent epic to sequence dependent changes. |
| **Component vocabulary exception** | Low | `domain.ecs.components` is treated as a structural exception. The public-var threshold is relaxed for vocabulary namespaces, but every public var must still be documented. |
| **Test namespaces remain oversized** | Low | Test namespaces are tracked but may be allowed larger thresholds. If test files grow, consider splitting them by topic, but do not block the main cleanup on test files. |

***

## 8. Open questions

1. Should `domain.genesis` remain the canonical high-fan-out bootstrapper, or should we introduce a `domain.phase0` content-layer namespace and lower `domain.genesis` fan-out to the warn threshold?
2. Is the proposed policy — keep `domain.ecs.components` as a single documented vocabulary namespace exempt from the public-var threshold — acceptable, or should we instead split it into a small number of high-level component namespaces?
3. Should `dev/smell_report.clj` thresholds be tightened after this cleanup (e.g., warn at 400 LOC / 40 vars), or are the current thresholds the right long-term policy?
4. Should `infra.render` continue to own input setup (`setup-input`), or should all input handling move to `infra.input`?

## 9. Estimate

**Total point estimate: 21 story points**

This estimate is grounded in the current structural report (`bin/analyze --strict`): **14 HARD breaches** and **81 undocumented public functions**. The estimate assumes the phased execution plan in §5 is followed and that each phase is delivered as a small, independently reviewable PR with full test runs.

### Estimate rationale

- **HARD breaches (14):** Three god namespaces drive the bulk of the risk. `domain.stellar` (2,637 LOC, 122 public vars) and `infra.render` (2,119 LOC, 115 public vars) are large, mature, and cross-cutting; splitting them requires careful preservation of the single-writer and one-renderer invariants. `domain.ecs.components` is structurally a vocabulary namespace and is treated as a documented exception, so its remediation cost is lower than its HARD count suggests.
- **Mega-functions (11 HARD):** The HARD mega-functions are concentrated in `domain.stellar` (7), `infra.render` (3), and one each in `domain.planet-formation`, `domain.physics/cache`, `domain.mass-transfer`, and `domain.genesis`. The stellar and render mega-functions are decomposed as part of the namespace splits in P4 and P5, so P2 is scoped to the remaining cross-cutting functions and the introduction of `render-ctx` and `shape-ctx` context maps.
- **Undocumented public functions (81):** The work is mechanical but broad — 32 are test fixtures generated by DSL macros, 17 are DSL macro definitions, and the rest are internal helpers promoted to public by omission. Most can be fixed with docstrings; a minority will be made `^:private`.
- **Namespace-split risk:** `domain.stellar` is the highest-risk split because it owns the matter-state lifecycle and several single-writer components. The split must keep `reg/write-conflicts` empty and preserve the single-substrate rule. `infra.render` is lower risk but still must maintain the one-renderer invariant.
- **Architecture invariants:** Every change must keep `domain/` free of `infra/` imports, keep `domain.genesis` as the sole Phase 0 bootstrapper, and keep exactly one renderer. This adds verification overhead to each phase but prevents the cleanup from creating a second reality.

### Phase breakdown

| Phase | Estimate | Rationale |
|-------|----------|-------------|
| P1 — Document and privatize | 3 | 81 docstrings; mostly mechanical, with some DSL-macro documentation updates. |
| P2 — Decompose mega-functions | 3 | Remaining cross-cutting mega-functions plus context-map introduction; stellar/render functions are handled in P4/P5. |
| P3 — Component vocabulary exception | 1 | Docstrings for ~92 component keywords plus a small policy change in `dev/smell_report.clj` and `docs/STATIC-ANALYSIS.md`. |
| P4 — Split `domain.stellar` | 8 | Largest structural change: 2,637 LOC across ~10 lifecycle sub-modules, preserving single-writer invariants and updating all call sites. |
| P5 — Split `infra.render` | 3 | 2,119 LOC across ~9 rendering sub-modules; mechanical layer split with one-renderer invariant enforcement. |
| P6 — Rationalize fan-out | 2 | Reduce `domain.genesis` to bootstrapper and move generic tick/pipeline code to `domain.ecs.pipeline` or `domain.phase0`. |
| P7 — Final validation and gating | 1 | Run `bin/analyze --strict`, `bin/bench`, and full tests; update `docs/STATIC-ANALYSIS.md`. |
| **Total** | **21** | Fits the 21-story-point Fibonacci bucket for a bounded, multi-PR structural cleanup. |

This estimate assumes no new feature work is added to the touched namespaces during the cleanup and that the project accepts the `domain.ecs.components` vocabulary exception.

## 10. Breakdown into ≤5-point tasks

This spec is too large for a single PR or work session. It has been decomposed into the following child tasks, each estimated at ≤5 story points. The child estimates sum to the original **21** points.

| Sub-task UUID | Title | Estimate | Notes |
|---------------|-------|----------|-------|
| `static-analysis-document-privatize` | Document and Privatize Undocumented Public Functions | 3 | Covers all 81 undocumented public functions; P1. |
| `static-analysis-decompose-mega-functions` | Decompose HARD Mega-Functions | 3 | Covers remaining mega-functions after namespace splits; P1. |
| `static-analysis-component-vocabulary-exception` | Component Vocabulary Exception and Docstrings | 1 | `domain.ecs.components` exemption + docstrings; P2. |
| `static-analysis-split-stellar-core` | Split domain.stellar Core Lifecycle Modules | 5 | Thermodynamics, collapse, classifier, sink, structure; P1. |
| `static-analysis-split-stellar-disc-wind` | Split domain.stellar Disc, Wind, and Seeder Modules | 3 | Disc, disc-evolution, seeder, wind, fusion; P1. |
| `static-analysis-split-render` | Split infra.render into Layered Sub-Modules | 3 | Math, mesh, color, HUD, field, volume, scene, window, input; P1. |
| `static-analysis-rationalize-genesis` | Rationalize domain.genesis Fan-Out | 2 | Bootstrapper-only facade; P1. |
| `static-analysis-final-validation` | Final Validation and Gating for Structural Cleanup | 1 | Strict analysis, tests, bench, docs; P2. |
| **Total** | | **21** | Matches the original estimate above. |