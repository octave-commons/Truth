---
uuid: "phase-0-player-focus-a-statistical-substrate"
title: "Player Focus A: statistical-cell substrate + lifecycle markers"
status: "ready"
priority: "P1"
labels: ["specs", "phase0", "player", "epic-player-focus-promotion-demotion"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/phase-0-player-focus-a-statistical-substrate.md"
category: "specs"
estimate: 2
---

# Player Focus A: statistical-cell substrate + lifecycle markers

> Parent epic: `kanban/tasks/phase-0-player-focus-promotion-demotion.md`
> Scope: the ECS substrate the focus-zone system needs, split out of the parent
> after a read-only discovery pass found the old `promotion.clj` was a false
> start requiring a rewrite.

**Goal:** Introduce the regional-cell representation and the spawn/despawn
lifecycle markers that promotion/demotion will emit — nothing that ticks yet.

## Scope

1. Add components to `domain.ecs.components`:
   - `spawn-request-promotion` (promotion spawn spec, one per source cell).
   - `consumed-demote` (marks a resolved body for aggregation + despawn).
   - `promoted-from-cell` (stamped on a promoted clump → source cell eid, so
     demotion returns mass to the right cell without a spatial lookup).
2. Register the two new lifecycle markers in `domain.genesis.bootstrap`:
   - `spawn-request-promotion` → `spawn-request-components` list.
   - `consumed-demote` → `consumed-markers` list.
   Both materialized/reaped in the single `materialize-lifecycle` pass.
3. Confirm/lean on the existing schemas (already present): `c/field-zone`,
   `c/statistical-mass`, `c/attention-shell` (`components.clj:170-172`);
   `law.field/statistical-cell-schema`, `attention-shell-schema`,
   `promotion-invariant?` (`law/field/schema.clj:156-196`).
4. A regional cell is an ordinary ECS entity carrying `c/statistical-mass` +
   `c/field-zone :regional` + `c/position`, and **deliberately no
   `c/matter-state`** — so gravity/hydro/classifier/integrator (all filter on
   `c/matter-state`) never see it. Add a helper to construct/seed such cells and
   a schema check.

## Done when

- New components exist and validate against `law/` schemas.
- Lifecycle marker lists updated; a cell entity round-trips through
  `materialize-lifecycle` without perturbing any existing system.
- Guard: `grep` confirms no existing system does a global
  `entities-with world c/position`/`c/mass`-only sweep that would treat a cell
  as a physical body.
- `clojure -M:test` green; `architecture-test` green; `reg/write-conflicts` `{}`.

---
Created 2026-07-22 (Claude): child A of the Player Focus rewrite. See parent for
the full discovery plan (false-start finding + target `:focus-zone` design).
---
