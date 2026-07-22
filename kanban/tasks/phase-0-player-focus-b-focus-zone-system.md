---
uuid: "phase-0-player-focus-b-focus-zone-system"
title: "Player Focus B: :focus-zone fan-out emitter (promotion + demotion)"
status: "blocked"
priority: "P1"
labels: ["specs", "phase0", "player", "epic-player-focus-promotion-demotion"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/phase-0-player-focus-b-focus-zone-system.md"
category: "specs"
estimate: 3
---

# Player Focus B: :focus-zone fan-out emitter (promotion + demotion)

> Parent epic: `kanban/tasks/phase-0-player-focus-promotion-demotion.md`
> Depends on: Player Focus A (substrate + markers).

**Goal:** Replace the false-start `domain.genesis.promotion` with a single
`:focus-zone` fan-out emitter that promotes statistical cells into resolved
clumps when the observer's immediate focus overlaps them, and demotes withdrawn
matter back into cells — conserving mass, momentum, angular momentum, magnetic
flux, and energy.

## Scope

1. Rewrite `domain.genesis.promotion` as ONE system constructor returning
   `{:id :focus-zone :reads ... :writes (registry-writes :focus-zone) :run fn}`.
   Promotion + demotion MUST be one system/id — both write `c/statistical-mass`,
   so two ids would trip `reg/write-conflicts`.
2. Promotion: scan regional cells overlapping the observer's immediate radius;
   emit `c/spawn-request-promotion` (reuse `spawn-entity`/`seeder/spawn-clump`
   via `materialize-lifecycle`) conserving mass, COM velocity, angular momentum;
   debit the source cell's `c/statistical-mass` in the same write-set; stamp
   each clump `c/promoted-from-cell`.
3. Demotion: bodies outside the immediate zone with no recent threshold event
   get `c/consumed-demote`; their mass/velocity/L (and flux/energy via the
   cell's moment of inertia + mean field) are folded into the target cell's
   `c/statistical-mass` in the same run. Keep the existing threshold-event-delay
   logic (`recent-threshold-entities`) verbatim.
4. **Drop all `c/matter-state` / `c/body-kind` writes** — the classifier owns
   `c/matter-state`; a second writer fails the architecture guard. Fidelity, not
   which physics runs.
5. Register `:focus-zone` in `domain.ecs.registry` and wire into
   `physics-lifecycle-systems` (`domain.genesis.systems`), sourcing `:writes`
   from `registry-writes :focus-zone` like `lod`/`debris` do.
6. Optional follow-up (note if deferred): hysteresis margin (e.g. demote only
   past `1.1 × immediate-r`) to stop promote/demote flapping at the boundary.

## Done when

- `:focus-zone` is the sole writer of `#{c/field-zone c/statistical-mass
  c/spawn-request-promotion c/consumed-demote}`; `reg/write-conflicts` `{}`.
- Promotion/demotion wired into the tick; no `c/matter-state` writes.
- `clojure -M:test` green; `architecture-test` green.

---
Created 2026-07-22 (Claude): child B. Blocked until child A lands (needs the new
markers + cell substrate).
---
