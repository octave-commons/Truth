---
uuid: "focus-zoom-lod-ui-spec"
title: "Focus, Zoom, LOD, and Life-Emergence UI"
status: "done"
priority: "P1"
labels: ["specs", "phase0", "player", "ui", "lod"]
created_at: "2026-07-08T23:45:00.000000000Z"
source: "kanban/tasks/focus-zoom-lod-ui-spec.md"
category: "specs"
---

# Focus, Zoom, LOD, and Life-Emergence UI Spec

**Status:** in-progress  
**Goal:** Let the player zoom in tightly on planets and life-bearing worlds without rendering artifacts, tell them where life emerges, surface the stats they need, and make the observer's focus actually drive simulation LOD.  
**Canonical backing:** `kanban/tasks/phase-0-player-focus-dual-representation-spec.md` §2–§3.

***

## 1. Invariants

1. The renderer is read-only with respect to the ECS world.
2. LOD scheduling is a single-writer concern: `domain.lod` owns `c/lod-level` and `c/lod-tick-phase`; the integrator only reads them.
3. The existing test suite remains green; LOD throttling is opt-in via `:lod/throttle-ticks?` until it is fully exercised.
4. No new special-case renderer: the same sphere mesh, solid pass, and sprite pass are used; only the mesh subdivision and near-plane adapt.

***

## 2. Rendering: tight zoom

### 2.1 Problem

The dev window uses a fixed icosahedron mesh (`:subdivisions` from config, default 2). At close orbit distances the facets become visible as "weird lines," and the dynamic projection near-plane can clip the front of a small body.

### 2.2 Fix

- **Adaptive mesh subdivision:** `infra.dev.window.loop/ensure-resources` recomputes `:requested-subdivisions` from the selected body's screen-space size. The sphere mesh is regenerated only when the required subdivision crosses a threshold.
- **Near-plane:** `infra.render.scene.setup/camera-matrices` clamps the near-plane to a small fraction of the selected body's render radius (or a hard floor), so tight orbits do not clip. The far-plane remains dynamic by orbit distance.
- **Min approach:** keep `cam/min-approach-distance` at 2.5 radii; the artifact is mesh/near-plane, not the camera bound.

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
| `src/infra/dev/window/loop.clj` | Adaptive `:requested-subdivisions` from selected body screen size. |
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
