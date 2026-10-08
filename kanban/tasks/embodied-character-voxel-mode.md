---
category: "specs"
labels: ["specs", "phase5", "phase6", "voxel", "character", "gates", "epic"]
write-id: "1791430525302-0.goq7yihlz96wywhib2"
source: "kanban/tasks/embodied-character-voxel-mode.md"
title: "Epic: Embodied single-character voxel mode (the Gates horizon)"
priority: "P2"
status: "incoming"
estimate: "55"
uuid: "embodied-character-voxel-mode"
created_at: "2026-07-22T00:00:00Z"
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

User explicitly activated the playable Gate goal on 2026-10-06. Triage now prioritizes its prerequisite phase specifications. Preserve the existing prohibition on breaking down implementation before those specs exist; natural planets/life are verified, but actors, civilization, embodiment and Gate producers remain absent. Incoming bounded design/research children will make those causal boundaries explicit.

Human-approved finite milestone, verified 2026-10-08: one controlled player in the live ECS reaches one real Gate through ordinary input, interacts, receives a visible effect, and the native result is captured. Broader progression, civilization and wider polish are later. Root fixed inventory fda36c777b155058c2a9ca2b6d4523fdda3255b4891241172046fe62047c7aeb selects existing endpoint UUID 8e79b9ff-1cd4-4eb9-8a31-80b892fd0c98, embodied-character-voxel-mode, and native verification UUID f369c598-279c-498d-a64f-45d2ce16ad34 as ownership lanes. Planning must define a supported live starting scene with declared provenance, an allowed controlled player, one local activation/refusal/state-effect law and actual native acceptance; traversal and the full historical ladder are excluded. Existing cards keep their status and scope until reviewed reconciliation; this comment grants no production or runtime admission. Reuse at most 3 expensive checkouts/snapshot jobs; require >=20 GiB available before full snapshots or dependency installs. No merge, auto-merge, rebase or history rewrite. All released JVM/native processes are closed; G04 attempt-01 is consumed and failed, root closure 7f8647c09d2b1239709fa2d8e2ac6a2378aea95c5df5f42df20548f1fba57718; no automatic retry. Root records: .ημ/diagnostics/truth-vertical-slice/accepted-scope-20261008T0305.json and fixed-inventory-20261008T0315.json. This is the selected scope for planning, not milestone completion.

Finite milestone design is now recorded in Incoming 3-point child 59da3d85-c9b6-488f-b33b-5442c3e784a4 (kanban/tasks/specify-finite-native-gate-energization.md), backed by docs/designs/finite-native-gate-energization.md and docs/notes/2026-10-08-finite-native-gate-scope.md. This child defines the supported authored live Spark scene and local energization proposal; it does not duplicate endpoint UUID 8e79b9ff-1cd4-4eb9-8a31-80b892fd0c98 or claim receiver activation, traversal, natural prehistory or completed embodiment. Proposed implementation scopes remain domain5 / scene5 / input-projection3 / existing native verification3, all within fixed inventory fda36c77 and all pending canonical planning admission. No production or new runtime release is authorized by the design. Earlier horizon and research remain preserved as later work.

---