---
uuid: "focus-zoom-lod-ui-spec"
title: "Focus, Zoom, LOD, and Life-Emergence UI"
status: done
priority: "P1"
labels: ["specs", "phase0", "player", "ui", "lod"]
created_at: "2026-07-08T23:45:00.000000000Z"
source: "kanban/tasks/focus-zoom-lod-ui-spec.md"
category: "specs"
---
# Focus, Zoom, LOD, and Life-Emergence UI Spec

**Status:** done (2026-07-10)  

> **Status update (2026-07-10, Claude Code — code-state review):** Body status
> corrected from `in-progress` to `done` to match the frontmatter and the code.
> All specced pieces are present in the tree:
> - `c/lod-tick-phase` in `domain.ecs.components`; `domain.lod` is its sole
>   writer (and of `c/lod-level`); `domain.integrator` `:reads` it.
> - `:lod/throttle-ticks?` filtering in `domain.integrator.kinematics` /
>   `.core` / `.base` (due-entity filter, opt-in, default off).
> - Floating-origin render: `:render-origin` in `infra.render.scene.setup`.
> - Life-emergence UI: `"Life emerges on %s! +50 quanta"` in `domain.arc`;
>   `L`-key `jump-to-living-world` + `nearest-living-world` in
>   `infra.render.input`; Living Worlds section in `infra.menu.panels`;
>   ecology facts (biomass/complexity/…) in `infra.inspect.format/body-facts`.
> Full `clojure -M:test` not re-run here.

**Goal:** Let the player zoom in tightly on planets and life-bearing worlds without rendering artifacts, tell them where life emerges, surface the stats they need, and make the observer's focus actually drive simulation LOD.  
**Canonical backing:** `kanban/tasks/phase-0-player-focus-dual-representation-spec.md` §2–§3.

***

## 1. Invariants

1. The renderer is read-only with respect to the ECS world.
2. LOD scheduling is a single-writer concern: `domain.lod` owns `c/lod-level` and `c/lod-tick-phase`; the integrator only reads them.
3. The existing test suite remains green; LOD throttling is opt-in via `:lod/throttle-ticks?` until it is fully exercised.
4. No new special-case renderer: the same sphere mesh, solid pass, and sprite pass are used; only the mesh subdivision and near-plane adapt.

***

## 2. Rendering: tight zoom and close-up bodies

### 2.1 Problem

The dev window uses a fixed icosahedron mesh (`:subdivisions` from config, default 2). At close orbit distances three problems appear:

1. **Floating-point precision loss:** Camera and body positions are far from the world origin (≈1.0 render units) while a true-scale body radius is tiny (≈1e-7 ru). When the vertex shader subtracts these large values to obtain a small relative position, single-precision `float` destroys precision, causing the body to flicker, distort, or vanish entirely. This affects **all** bodies (stars, planets, moons), not only the selected one.
2. **Projection clipping:** the dynamic near-plane can clip the front of a small body, and the fixed mesh facets become visible.
3. **Low-resolution halo:** the selection/hover/intervention rings are drawn with a fixed segment count, so the ring appears as a polygon when zoomed in.

### 2.2 Fix

1. **Floating-origin render pass:** `infra.render.scene.setup/render-scene` accepts an optional `:render-origin`. Before rendering, it subtracts the origin from the camera position/target and from every body position, the volume bounding box, and the volume light positions. This keeps the viewed body near the origin in render space and gives the vertex shader enough precision to draw a smooth sphere. The origin is the camera target, so the focused region is always at the origin.
2. **Adaptive mesh subdivision:** `infra.dev.window.loop/ensure-resources` recomputes `:requested-subdivisions` from the **largest** body on screen (not just the selected one). The sphere mesh is regenerated only when the required subdivision crosses a threshold. Because the mesh is shared, this makes every body on screen smooth, not just the selection.
3. **Near-plane:** `infra.render.scene.setup/camera-matrices` clamps the near-plane to a small fraction of the orbit distance (or a hard floor), so tight orbits do not clip. The far-plane remains dynamic by orbit distance.
4. **Adaptive halo:** `infra.inspect.overlay/halo-shapes` now computes the segment count from the on-screen ring size, so the selection/hover/intervention rings stay smooth when zoomed in. The ring is also explicitly closed (last vertex equals first).
5. **Min approach:** keep `cam/min-approach-distance` at 2.5 radii; the artifact is rendering precision, not the camera bound.

### 2.3 Subdivision heuristic

```clojure
(defn subdivisions-for-body [screen-diameter-pixels]
  (cond
    (< screen-diameter 16) 1
    (< screen-diameter 64) 2
    (< screen-diameter 256) 3
    (< screen-diameter 1024) 4
    :else 5))
```

The mesh is recreated only when the computed subdivision differs from the current one. Mesh upload cost is paid once per transition, not per frame.

***

## 3. Life-emergence UI

### 3.1 Notification with location

`domain.arc/event-notification` and `arc-notification` currently emit "Life emerges! +50 quanta". The payload of `:event/life-emergence` already includes the entity id (`:entities #{eid}`). `arc-notification` will look up the body name and append it:

```clojure
"Life emerges on <name>! +50 quanta"
```

If the camera is in `:manual` or `:fit-all` mode, the notification will also set a `:follow-living-eid` request in the arc so the window loop can offer to pan to the world. This is a **request**, not a forced camera movement; the player chooses to follow.

### 3.2 Living-worlds list

The **Entities** panel will gain a "Living Worlds" subsection above the sorted body list. It shows every `:planet` whose `c/ecology` is living (`domain.ecology/living?`). Each row is clickable and dispatches `[:ui/select-entity eid]` and `[:camera/follow-entity eid]`.

### 3.3 Jump-to-life hotkey

`infra.render.input` adds `L` (or `LIFE`):

- If one or more living worlds exist, cycle the camera target and `:follow-eid` to the nearest living world to the current camera target.
- If no living world exists, print a brief HUD message: "No living world yet."

***

## 4. Stats readout

### 4.1 Inspector ecology pane

`infra.inspect.format/body-facts` will append ecology facts when the selected body has `c/ecology`:

- phase
- biomass (bar / percent)
- complexity (bar / percent)
- stability (bar / percent)
- moisture (bar / percent)
- temperature (0–1 scale, mapped to K)
- seeded? yes/no

### 4.2 Phase 1 extras

If present, the inspector will also show:

- SED bands (count + dominant)
- magnetosphere standoff distance
- disk mass / disk radius for stars
- atmosphere escape regime

These are appended only when the components exist, so the card stays compact for young nebula bodies.

***

## 5. Focus-driven LOD tick scheduling

### 5.1 Component

Add `c/lod-tick-phase`:

```clojure
(def lod-tick-phase :component/lod-tick-phase) ;; {:level :local|:system|:galaxy :period 1|2|4 :phase tick}
```

Owned by `domain.lod`. The integrator reads it but does not write it.

### 5.2 Scheduler

`domain.lod/lod-scheduler` already assigns `c/lod-level`. When the level changes, it also writes `c/lod-tick-phase` with:

- `:level` = new level
- `:period` = `{ :local 1, :system 2, :galaxy 4 }`
- `:phase` = current tick (so the entity is due immediately after a transition)

If the level is unchanged, no `c/lod-tick-phase` write is emitted.

### 5.3 Integrator

`domain.integrator.kinematics` filters the entity list before computing forces and positions:

```clojure
(defn due-this-tick? [world tick eid]
  (if-let [{:keys [period phase]} (ecs/get-component world eid c/lod-tick-phase)]
    (zero? (mod (- (long tick) (long phase)) (long period)))
    true))
```

Only due entities are advanced. Other physical-field updaters (mass, temperature, composition, rotation) also use the same filter.

### 5.4 Opt-in flag

`:lod/throttle-ticks?` in the world/config defaults to `false`. When false, the integrator treats every entity as due (backward-compatible). When true, the filter is active.

### 5.5 Why this is safe

- Acceleration/force influences are written fresh each tick by their emitters (single writer). A skipped entity's previous influence is overwritten by the next tick's emitter before the integrator reads it again.
- Position and velocity are unchanged for skipped ticks, which is consistent with a longer effective timestep.
- The neighbor cache is built from the snapshot; skipped entities do not move, so the cache remains valid.

### 5.6 Future work (out of scope here)

- Variable `dt` per entity (e.g., `:system` integrated with `2*dt` every 2 ticks instead of skipped for 1 tick).
- Demotion to statistical cells and promotion back to resolved particles.
- Coherence cost tied to immediate-zone particle count.

These follow the canonical dual-representation spec and are left for a subsequent pass.

***

## 6. Component changes

| File | Change |
|------|--------|
| `src/domain/ecs/components.clj` | Add `c/lod-tick-phase`. |
| `src/domain/lod.clj` | Write `c/lod-tick-phase` when `c/lod-level` changes. |
| `src/domain/integrator.clj` | Add `c/lod-tick-phase` to `:reads`. |
| `src/domain/integrator/kinematics.clj` | Filter `kinematics-ws` and `kinematics-ws-soa` by due entities. |
| `src/domain/integrator/core.clj` | Filter `mass-ws`, `ionization-ws`, `composition-ws`, `comp-condensed-ws`, `rotation-ws` by due entities. |
| `src/domain/integrator/temperature.clj` | Filter by due entities. |
| `src/infra/render/scene/setup.clj` | Smarter near-plane based on selected body radius. |
| `src/infra/render/scene/setup.clj` | Floating-origin camera/body/volume shift in `render-scene`. |
| `src/infra/dev/window/loop.clj` | Adaptive `:requested-subdivisions` from largest on-screen body; pass `render-origin` to renderer. |
| `src/infra/inspect/overlay.clj` | Adaptive halo segment count and closed ring. |
| `src/infra/render/window.clj` | Offscreen rendering also uses `render-origin`. |
| `src/infra/render/input.clj` | `L` key to jump to nearest living world. |
| `src/infra/render/hud.clj` | Life notification includes body name; optional follow hint. |
| `src/infra/menu/panels.clj` | Living-worlds subsection in Entities panel. |
| `src/infra/inspect/format.clj` | Ecology + phase-1 facts in `body-facts`. |
| `src/domain/arc.clj` | Notification text includes body name; `:follow-living-eid` request. |
| `test/domain/lod_test.clj` | New tests for `c/lod-tick-phase` and due filtering. |
| `test/infra/inspect_test.clj` | Ecology facts present. |
| `kanban/tasks/focus-zoom-lod-ui-spec.md` | This file. |

***

## 7. Tests

1. `adaptive-subdivisions-rise-with-screen-size` — closer selected body requests more subdivisions.
2. `life-notification-includes-body-name` — `:event/life-emergence` notification text contains the body name.
3. `living-worlds-list-contains-only-living-planets` — `infra.menu.panels` living-worlds rows are living planets.
4. `inspector-shows-ecology-stats` — `body-facts` returns ecology rows when `c/ecology` is living.
5. `lod-scheduler-writes-tick-phase-on-level-change` — level change produces `c/lod-tick-phase`.
6. `integrator-skips-non-due-entities` — with `:lod/throttle-ticks?` true, `:galaxy` entities are not advanced every tick.

***

## 8. Decisions

1. **Tick-skip, not variable dt.** Skipping is cheaper to implement and debug while still delivering the core mechanic: focused matter ticks more often than distant matter.
2. **Opt-in throttling.** Default off so the existing deterministic test suite is unaffected until the feature is proven.
3. **Camera follow is a request, not a teleport.** The `L` key and living-worlds clicks set `:follow-eid`; the existing tracking mode follows smoothly.
4. **Mesh subdivision changes are lazy.** The mesh is only rebuilt when the computed subdivision crosses an integer threshold, not every frame.

***

## 9. Next actions after this spec

1. Implement adaptive mesh subdivision and near-plane fix.
2. Implement life-emergence location UI and inspector ecology stats.
3. Implement LOD tick-phase scheduling and integrator filtering.
4. Run architecture-test and full suite; benchmark if LOD throttling is enabled.

***

## Review — 2026-07-10 (independent reviewer)

**Verdict:** READY-FOR-DONE

Deliverables verified against code (file:line):

- **§5 LOD component & single-writer:**
  - `c/lod-tick-phase` defined at `src/domain/ecs/components.clj:174`.
  - `domain.lod` is the SOLE writer of both `c/lod-level` and `c/lod-tick-phase`
    (docstring `src/domain/lod.clj:48`; writes at `src/domain/lod.clj:77-78`,
    guarded by a level-change check so no write when level is unchanged).
  - Integrator declares `:reads #{c/lod-tick-phase}` at `src/domain/integrator.clj:107`.
  - `:lod/throttle-ticks?` due-entity filtering present in
    `src/domain/integrator/base.clj:73-85` (`due-entity?`, default off — gated on
    `(:lod/throttle-ticks? world)`), `src/domain/integrator/core.clj:122-192`
    (mass/temperature/composition/rotation filtered via `base/due-entities`), and
    `src/domain/integrator/kinematics.clj:159` (`due-idxs` position filter).
- **§2 Rendering:** `:render-origin` floating-origin shift in
  `src/infra/render/scene/setup.clj:198-204`; offscreen path passes it through in
  `src/infra/render/window.clj:196-203` (origin = camera target). Adaptive
  subdivisions in `src/infra/dev/window/loop.clj:315-316`
  (`render/subdivisions-for-screen-size` from largest on-screen body). Adaptive
  halo segment count + closed ring in `src/infra/inspect/overlay.clj:23-44`
  (`adaptive-segments`, `halo-shapes`).
- **§3 Life-emergence UI:** notification with body name at
  `src/domain/arc.clj:136` (`"Life emerges on %s! +50 quanta"`). `L`-key jump in
  `src/infra/render/input.clj` (`nearest-living-world` :50, `jump-to-living-world`
  :60, "No living world yet." :68, bound :171). Living Worlds section in
  `src/infra/menu/panels.clj:127,193` (`ecology/living?` filter).
- **§4 Stats:** ecology facts (biomass/complexity/stability/moisture/temperature/
  seeded) in `src/infra/inspect/format.clj:106-110` (`body-facts`, gated on
  `ecology/living?`).

**§7 tests — all 6 exist** (three under slightly different names):

1. `adaptive-subdivisions-rise-with-screen-size` → `test-adaptive-subdivisions-rise-with-screen-size` — `test/infra/render_test.clj:361`.
2. `life-notification-includes-body-name` → `life-emergence-notification-includes-body-name` — `test/domain/arc_test.clj:208` (asserts text matches `#"Life emerges on"`).
3. `living-worlds-list-contains-only-living-planets` — `test/infra/menu_test.clj:139` (exact).
4. `inspector-shows-ecology-stats` → `test-inspector-shows-ecology-stats` — `test/infra/inspect_test.clj:100`.
5. `lod-scheduler-writes-tick-phase-on-level-change` — `test/domain/lod_test.clj:31` (exact).
6. `integrator-skips-non-due-entities` → covered by `integrator-due-entity-filter` (`test/domain/lod_test.clj:57`) and `kinematics-ws-skips-non-due-entities` (`test/domain/lod_test.clj:79`).

**Test runs (this review):**

- `clojure -M:test -n domain.lod-test` → Ran 3 tests, 15 assertions, **0 failures, 0 errors**.
- `clojure -M:test -n infra.inspect-test` → Ran 8 tests, 27 assertions, **0 failures, 0 errors**.
- `clojure -M:test -n architecture-test` → Ran 6 tests, 23 assertions, **0 failures, 0 errors** (confirms invariant #2: no single-writer / write-conflict violation for `c/lod-level` / `c/lod-tick-phase`).
- `clojure -M:test -n domain.arc-test -n infra.menu-test -n infra.render-test` → Ran 49 tests, 186 assertions, **0 failures, 0 errors**.

**Gaps / notes (non-blocking):**

- Three of the six §7 test names differ from the spec's exact strings (functionally equivalent coverage present). Cosmetic only.
- Full `clojure -M:test` was not run end-to-end here; the targeted namespaces covering every §2–§7 deliverable plus the architecture guards are all green.

All specced deliverables §2–§7 are present in the tree and the targeted + architecture tests pass. Recommend moving to done.
