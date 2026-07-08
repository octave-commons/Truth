---
uuid: "rich-entity-inspection-ui-spec"
title: "Rich Entity Inspection UI Spec"
status: "todo"
priority: "P1"
labels: ["specs"]
created_at: "2026-07-08T02:24:29.786435454Z"
source: "kanban/tasks/rich-entity-inspection-ui-spec.md"
category: "specs"
---

# Rich Entity Inspection UI Spec

**Status:** specification  
**Scope:** replace/extend the text-only `infra.inspect/inspector-card` with a visual, pane-based panel for selected ECS entities.  
**Depends on:** `kanban/tasks/rich-entity-inspection-ui.md`

***

## 1. Goal

As chemistry, disk physics, and plasma state make the simulation state more complex, the player needs to verify bodies visually. The current inspector is a text card. This spec adds an optional rich panel rendered with existing LWJGL HUD primitives.

***

## 2. Invariants

1. The panel is render-thread only and read-only with respect to the ECS world.
2. It reuses existing HUD rendering (`render-hud`, `render-text`) and no external UI library.
3. The old minimal card remains available; the rich panel is gated by `:ui/rich-inspector?`.
4. Panel regions participate in the same input region system as `infra.menu` so world interaction is suppressed over the panel.
5. No simulation state is mutated by the inspector.

***

## 3. Panel layout

A vertical column of panes, anchored beside the selected body and clamped to screen bounds.

| Pane | Content |
|------|---------|
| **Header** | Name, state badge, mass/radius/temperature chips |
| **Composition** | Horizontal bars for H, He, metals, rock, ice, volatiles |
| **Thermal/Radius timeline** | Sparklines of temperature, radius, luminosity, mass over last N ticks |
| **Orbit** | Semi-major axis, eccentricity, inclination, period, mini orbit map |
| **Hierarchy** | Star → disk → planets → moons, clickable rows |
| **Events** | Last 8 entity-relevant ledger entries |
| **Raw ECS** | Collapsible key/value table of all components |

***

## 4. Composition pane

- One horizontal bar per element/group.
- Bar length ∝ mass fraction.
- Color map:

```clojure
(def element-color
  {:H         [0.91 0.96 1.00]
   :He        [1.00 0.97 0.84]
   :metals    [0.62 0.50 0.40]
   :rock      [0.60 0.55 0.50]
   :ice       [0.72 0.90 0.96]
   :volatiles [0.80 0.95 0.90]
   :default   [0.70 0.70 0.70]})
```

- Show numeric fraction to two decimals.
- Use `c/comp-condensed` and `c/composition`.

***

## 5. Sparkline pane

- Input: rolling history `[:inspect/history]` component or render-side cache.
- History sample: `{:tick :mass :temperature :radius :luminosity :matter-state}`.
- Window: last 128 samples by default.
- Y axis: linear if dynamic range < 10×, else log10.
- Dots mark `:matter-state` transitions.

***

## 6. Orbit pane

- If `c/elements` exists, show a, e, i, Ω, ω, period.
- If `c/orbit-ref` exists, show parent name.
- Mini map: top-down ellipse with parent at focus and current position marked.
- Unbound bodies show a short hyperbola segment and flag "unbound".

***

## 7. Hierarchy pane

- Build tree from `c/orbit-ref` or mass-weighted spatial proximity.
- Sort children by mass descending.
- Indent with `· `.
- Click dispatches `[:ui/select-entity eid]` via `infra.menu/apply-action`.
- Cap visible rows at 16.

***

## 8. Events pane

- Read world `:ledger` and filter to events involving the entity.
- Display simulation time + short sentence.
- Useful event kinds: matter-state transitions, merge/accretion, wind/flare, planet/disk spawn, ignition.

***

## 9. Raw ECS pane

- Collapsible, default collapsed.
- Two-column table: component key → truncated EDN value.
- Monospaced, dim color.

***

## 10. Component changes

### 10.1 New component: `c/inspect-history`

```clojure
(def inspect-history :component/inspect.history)
```

Value: rolling vector of samples, capped at 256 entries.

### 10.2 `c/orbit-ref`

Already exists; used for hierarchy.

***

## 11. System responsibilities

### 11.1 `domain.inspect-history` (new tiny system)

- Every tick, append a sample to `c/inspect-history` for tracked entities.
- Cap vector size at 256.
- Only run for entities flagged for inspection, or for all resolved bodies if cheap.

### 11.2 `infra.inspect`

- `rich-inspector-panel` returns a layout tree.
- `render-rich-inspector` draws it via `infra.render/render-hud` and `render-text`.
- `inspector-input-regions` returns clickable regions merged with menu regions.

### 11.3 `infra.dev.window`

- Toggle rich inspector with a key (e.g. `I`).
- Render rich inspector when `:ui/rich-inspector?` is true.

***

## 12. Tests

1. `panel-returns-layout-tree-with-correct-pane-count`.
2. `composition-pane-shows-non-zero-elements-only`.
3. `sparkline-scales-logarithmically-for-large-dynamic-range`.
4. `hierarchy-contains-children-of-selected-star`.
5. `raw-ecs-pane-is-collapsed-by-default`.

***

## 13. Promotion path

| File | Change |
|------|--------|
| `src/domain/ecs/components.clj` | Add `inspect-history`. |
| `src/domain/inspect_history.clj` | New system to append samples. |
| `src/infra/inspect.clj` | Add panel layout, composition bars, sparklines, orbit map, hierarchy, events, raw table. |
| `src/infra/menu.clj` | Merge inspector input regions. |
| `src/infra/dev/window.clj` | Toggle and render rich inspector. |
| `test/infra/inspect_test.clj` | Layout and pane tests. |

***

## 14. Decisions

1. **History lives as an ECS component** (`c/inspect-history`). It is testable, save-loadable, and keeps the render thread read-only.
2. **No auto-expand on state change.** Instead, flash the header badge and add a transient highlight row in the Events pane.
3. **Comparison mode is out of scope** for the first version; hierarchy switching is the substitute.

## 15. Open questions

1. Should `c/inspect-history` be written for all resolved bodies or only the selected/focused body?
2. What is the performance cost of rendering the sparklines and raw ECS table every frame?
3. Should the panel support collapse of individual panes to reduce visual clutter?
