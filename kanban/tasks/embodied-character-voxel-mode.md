---
uuid: "embodied-character-voxel-mode"
title: "Epic: Embodied single-character voxel mode (the Gates horizon)"
status: "icebox"
priority: "P2"
labels: ["specs", "phase5", "phase6", "voxel", "character", "gates", "epic"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/embodied-character-voxel-mode.md"
category: "specs"
estimate: 55
---

# Epic: Embodied single-character voxel mode (the Gates horizon)

> Design anchors: `docs/designs/gates-of-truth-world-gen-phases.md` (Phases 5-6,
> "the character-creation screen has secretly been the entire game"),
> origin note `docs/notes/designs/2026.06.25.16.41.16-001-*.md` ("Gates of Aker"
> mode), `docs/designs/planetary-voxel-substrate.md` (the substrate).

**Goal (the far horizon):** The final rung of the Narrowing. When simulated
complexity is high enough that controlling one person is fun, the player
descends into a single character on the voxel world and plays at human scale —
mining, building, terrain-shaping — "like Minecraft, but amazing, in space, with
voxel-aware planetary physics." Reaching the Gates synchronizes time and opens
the many-worlds layer.

**Why iceboxed:** this is Phase 5-6, gated behind the entire ladder — the
planetary voxel substrate, life emergence (Phase 2), sentience/proto-culture
(Phase 3), and civilizational narrowing (Phase 4), none of which have
implementation-level specs yet. Recorded now so the ladder is legible and today's
substrate choices (voxel model, chemistry, conservation) stay forward-compatible
with human-scale interaction. Do not break down until the intervening phases have
specs.

## Continuity constraints (must hold when this is finally built)

- Same four controls (Camera / Focus / Interact / Release) as every prior rung —
  only their meaning narrows to a body's hands.
- Same ECS world; the character is the deepest `:immediate` resolution, not a
  separate game.
- Awe preserved: the world remains larger than the character's reach.

---
Created 2026-07-22 (Claude): the top of the ladder, iceboxed. See
`docs/designs/the-first-narrowing-star-to-planet.md` — this rung inherits that
rung's "felt, gradual, decision" template.
---
