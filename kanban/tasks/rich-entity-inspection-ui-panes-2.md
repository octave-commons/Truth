---
uuid: "rich-entity-inspection-ui-panes-2"
title: "Rich Entity Inspection UI — Orbit / Hierarchy / Events panes"
status: "todo"
priority: "P2"
labels: ["specs", "render"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/rich-entity-inspection-ui-panes-2.md"
category: "specs"
---

# Rich Entity Inspection UI — Orbit / Hierarchy / Events panes

> Follow-up to `kanban/tasks/rich-entity-inspection-ui-spec.md`, which shipped the
> core slice (Header, Composition, Sparkline, Raw ECS). This card adds the
> remaining panes deferred at the 2026-07-23 scope decision.

## Scope

Extend the rich inspector panel (already built, gated by `:ui/rich-inspector?`)
with three more panes, reusing the same layout-tree + HUD-primitive approach.

1. **Orbit pane** (parent spec §6) — a/e/i/Ω/ω/period from `c/elements`; parent
   name from `c/orbit-ref`; top-down mini orbit map with parent at focus and
   current position marked; unbound bodies show a hyperbola segment + "unbound".
2. **Hierarchy pane** (parent spec §7) — tree from `c/orbit-ref` (or mass-weighted
   spatial proximity fallback), children sorted by mass desc, `· ` indent, rows
   clickable → `[:ui/select-entity eid]` via `infra.menu/apply-action`, cap 16 rows.
3. **Events pane** (parent spec §8) — filter world `:ledger` to entity-relevant
   events, show sim-time + short sentence, last 8.

## Tests

- `hierarchy-contains-children-of-selected-star` (moved from parent card §12 #4).
- `orbit-pane-shows-elements-when-c-elements-present`.
- `orbit-pane-flags-unbound-body`.
- `events-pane-filters-ledger-to-entity`.

## Done when

- Three panes render in the existing rich-inspector layout tree.
- Clicking a hierarchy row reselects that entity.
- `clojure -M:test` green; `test/architecture_test.clj` passes.
- Dev-window smoke: select a star, verify orbit map + hierarchy + events render.

## Out of scope

- Comparison mode (parent spec §14 decision 3 keeps it out).
