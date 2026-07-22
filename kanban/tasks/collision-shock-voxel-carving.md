---
uuid: "collision-shock-voxel-carving"
title: "Voxel 5: collision shock -> voxel carving"
status: "blocked"
priority: "P2"
labels: ["specs", "phase1", "voxel", "epic-planetary-voxel-substrate"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/collision-shock-voxel-carving.md"
category: "specs"
estimate: 5
---

# Voxel 5: collision shock -> voxel carving

> Parent epic: `kanban/tasks/planetary-voxel-substrate.md`
> Design: `docs/designs/planetary-voxel-substrate.md` §6.
> Blocked on: `voxel-substrate-law-schema`, `voxel-focus-promotion-demotion`.

**Goal:** Impacts carve, melt, vaporize, and re-cool voxels using scaling-law
approximations of shock physics (no hydrocode), reusing the collision events
the sim already emits.

## Scope

- Coupling-parameter/scaling-law classifier: regime + melt/vapor/excavation
  volumes from impactor mass/velocity/angle and target material.
- RESEARCH PRE-REQUISITE (design §7.6): the fitted scaling-law set
  (Holsapple/Housen-style vs others) is NOT selected — pick from the crater
  -scaling literature with citations before implementation, or dispatch a
  deep-research slice. Constants land in `law/`.
- Carving/melt-tag/cooling ops run through the deferred queue (2 ms/tick) —
  a big crater visibly forms over ~a second (owner-endorsed feel).
- Reuse existing `:event/collision` events; no new collision system.

## Done when

- A collision event on a resolved band produces a scaling-law-sized crater
  through the queue; melt/vapor tags cool back over time; suite +
  architecture green.

---
Created 2026-07-22 (resumed session): slice 5 of the approved breakdown.
Held until slices 1-3 prove out; scaling-law research is the long pole.
---
