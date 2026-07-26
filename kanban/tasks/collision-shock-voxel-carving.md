---
category: "specs"
labels: ["specs", "phase1", "voxel", "epic-planetary-voxel-substrate"]
write-id: "1784766746669-0.dskldolu28eey4n3f7h"
source: "kanban/tasks/collision-shock-voxel-carving.md"
title: "Voxel 5: collision shock -> voxel carving"
priority: "P2"
status: "done"
estimate: "5"
uuid: "collision-shock-voxel-carving"
created_at: "2026-07-22T00:00:00Z"
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

Triage 2026-07-22: substrate proven (1-4 done thru a3515f3); research pre-requisite LANDED — scaling-law set selected (docs/research/2026-07-22-crater-scaling-laws-for-voxel-carving.md: Schmidt-Housen/Collins-Melosh-Marcus pi-group, Pierazzo energy-scaled melt, Benz-Asphaug disruption gating, Kraus/Senft/Stewart ice fits; worked example verified vs Meteor-Crater scaling). Dispatching impl agent. blocked -> in_progress.

Complete + reviewed 2026-07-22. law.crater transcribes the researched set with citations + error-bar flags (Schmidt-Housen/Collins pi-group, Croft complex conversion, Pierazzo energy melt, Benz-Asphaug disruption, Kraus CTH ice); worked example pinned at 2% (D_tc 15.22km, D_fr 21.82km, melt 5.14e9 m3). Pipeline: sticky c/absorb-merge packet -> :voxel-carve classifies (:seen idempotent) -> carve plans -> chunked edits->jobs through the 2ms queue (2048-edit crater spans 2 ticks — visible formation per owner), provenance :collision; bowl nils to excavation depth, melt floor tagged, vapor suspended (material-aware temp); exponential cooling (tau = e2/kappa ~ 130yr) returns melt/vapor -> solid. Disruption branch accumulates a schema-valid report naming the missing pipeline (no silent stop). Review PASS-WITH-NITS, all 6 resolved incl. 2 real bugs (ice vapor ungated below 8 km/s; cooling-after-carve resurrection order) + chondrite-misclassification fix (ice gate needs H floor) + complex-relaxation loud-flagged UNUSED-PENDING + sticky-packet coupling documented. Suite 753/14039 green; architecture green; write-conflicts {}. in_progress -> done.
---