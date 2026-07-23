---
uuid: "voxel-band-render-path"
title: "Voxel band render path — draw the surface, not a sphere"
status: "todo"
priority: "P1"
labels: ["render", "voxel", "integration-debt"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/voxel-band-render-path.md"
category: "specs"
estimate: 5
---

# Voxel band render path

> Integration-debt card (2026-07-23 re-triage). The whole voxel epic (slices 1-5
> + persistence, ~82 tests) computes a `c/voxel-band` inside the ECS, but
> **nothing in `src/infra/` reads any voxel component** — there is no render
> path. Zooming into a focused planet shows a plain sphere. This is the terminal
> payoff of the visibility chain.

## Root cause (investigation)
`grep -rn voxel src/infra/` → only `infra/render/field.clj`'s unrelated
froxel-fog vocabulary; zero references to `c/voxel-band`/`c/voxel-field`/
`c/voxel-edit-diffs`. The design doc itself flags this as an unbuilt card
(`docs/designs/planetary-voxel-substrate.md:158-159`: "infra/render/ — a voxel
mesher/renderer analogous to volume.clj froxel fog, reading discrete voxel
data").

`c/voxel-band` (materialized in `domain/voxel/band.clj:156-172`) is a sparse map
`{[i j k] voxel}` of canonical-grid offsets (`law.voxel/canonical-voxel-edge-m`),
each voxel a `law.voxel/voxel-schema` record (`:material :density :temperature
:state :cohesion`), ≤8192 cells — a discrete, material-tagged, cap-shaped patch
on a planet's surface.

## Done when (player-visible)
- After crossing the commitment horizon and holding focus on a planet for ~1 s,
  the player sees a discrete cratered/material-tagged terrain patch at the focus
  point instead of a plain sphere: distinguishable material colors
  (basalt/granite/ore/ice/regolith per `render-material-color-element-keys`,
  already done), visible depth where the band has been carved, tracking the
  focus as the camera moves.
- Renders live in the pm2 dev window; `clojure -M:test` + architecture-test green.

## Scope
New `src/infra/render/scene/voxel.clj` (or extend `bodies.clj`): read
`c/voxel-band` off the committed world eid, map each `[i j k] → voxel` to a
world-space cube/quad via `domain.voxel.band/voxel-center`, batch-instance draw.
Reuse `infra/render/field.clj`'s grid→GL-buffer pattern and the new
`infra.render.asset`/`material` namespaces (mesh cache + material record) rather
than body sprites.

## Dependencies
- Builds on the just-landed `phase-0-renderer-asset-phases-5-6` refactor
  (asset/passes/material). Commit that first for a clean base.
- Practically needs `narrowing-hud-binding-commitment-readout` +
  `narrowing-worldscale-overlap-gate` to reliably *reach* a materialized band
  live for testing (otherwise reachable only by accident-prone dwelling).

## Out of scope
Full marching-cubes smooth mesh; character-scale detail. A cube/quad instance
draw is enough to prove topology.
