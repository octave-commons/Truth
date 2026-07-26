---
category: "specs"
labels: ["specs", "phase1", "voxel", "epic-planetary-voxel-substrate"]
write-id: "1784762110516-0.q21hmtlmjocps2bbch"
source: "kanban/tasks/voxel-god-scale-sculpting-ops.md"
title: "Voxel 4: god-scale sculpting ops (palette -> field bias)"
priority: "P2"
status: "done"
estimate: "5"
uuid: "voxel-god-scale-sculpting-ops"
created_at: "2026-07-22T00:00:00Z"
---

# Voxel 4: god-scale sculpting ops (palette -> field bias)

> Parent epic: `kanban/tasks/planetary-voxel-substrate.md`
> Design: `docs/designs/planetary-voxel-substrate.md` §5 (macro-drives-local).
> Blocked on: `planet-candidate-to-voxel-seed`, `voxel-focus-promotion-demotion`.

**Goal:** Wire the Phase 1 ability palette (Tectonics/Hydrography/Atmosphere)
to bias the macro geology field and, where focus overlaps, drive the
resulting local voxel edits (erosion, uplift, volcanism).

## Scope

- Palette verbs bias the MACRO field statistically; local voxel edits fall
  out of the field change where the band is resolved — never direct
  god-finger voxel pokes (macro-drives-local rule, design §5).
- All voxel edits flow through the deferred queue (2 ms/tick cap).
- Resonance/Agency costs wire through the existing commitment/palette state
  (`c/palette`, Narrowing B) — check what the palette actually carries by
  then; gap honestly if verbs are still infra-side.

## Done when

- At least uplift + erosion + volcanism bias the field and produce queued
  local edits under focus; conservation respected; suite + architecture
  green; `write-conflicts {}`.

---
Created 2026-07-22 (resumed session): slice 4 of the approved breakdown.
Held until slices 1-3 prove out (owner sequencing call).

Triage 2026-07-22: substrate proven (Voxel 1-3 done, reviewed, committed thru f7f71fd) — owner hold condition met. Dispatching impl agent for palette->field-bias sculpting. blocked -> in_progress.

Complete + reviewed 2026-07-22. Three honest field carriers: uplift (plate convergence push + downwelling boost; column mass deep->shallow), erosion (share-preserving resource-cell mass export; sediment to rim columns), volcanism (upwelling boost + paid heat -> :melt cohesion 0). Macro-drives-local proven: no band => field biases, ZERO voxel edits. Local edits derive in mag x 512m influence disc, chunked via edits->jobs, provenance :sculpt. Resonance spend via player/spend-resonance gated on armed palette ability, cost monotone (base + per-mag x mag), unaffordable => world identical, exactly-once fold via serial clear. Review PASS-WITH-NITS: save hole (field biases invisible to §7.3) routed to NEW CARD voxel-field-bias-persistence; composition-drift docstring overclaim corrected; focus.clj re-indent nit + mid-review accidental revert recovered losslessly (742 green after restore). Suite 742/13958 green; architecture green; write-conflicts {}. in_progress -> done.
---