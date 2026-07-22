---
category: "specs"
labels: ["specs", "phase1", "player", "ui", "narrowing", "epic-the-first-narrowing"]
write-id: "1784754412907-0.kfyxc7dcs1e0lhgr0qw"
source: "kanban/tasks/narrowing-frame-handoff.md"
title: "Narrowing C: camera/frame handoff + felt cues"
priority: "P2"
status: "done"
estimate: "3"
uuid: "narrowing-frame-handoff"
created_at: "2026-07-22T00:00:00Z"
---

# Narrowing C: camera/frame handoff + felt cues

> Parent epic: `kanban/tasks/the-first-narrowing-star-to-planet.md`
> Design: `docs/designs/the-first-narrowing-star-to-planet.md` §6.
> Blocked on: `narrowing-commitment-horizon`.

**Goal:** Make the narrowing *visible* without a cut — the frame tightens as
binding deepens; the sky simplifies as the system demotes; one ambient narrator
line at capture. Felt, never announced (`ux-architecture.md` hard rule).

## Scope

- Gradual auto-tether that follows binding depth (player can still fight it,
  per design §8.4). No hard cut at capture.
- Sky-simplification cue: as Regional bodies demote to statistical fields, the
  render reflects the collapse (probability clouds / dimmed) — reuse existing
  LOD/render paths; z-up, true-scale intact.
- One ambient narrator line at capture (via the narrator mood/ambience layer,
  no addressed text). No modal, no popup.

## Done when

- Camera tracks binding continuously; releasable; no jump-cut on commit.
- Demotion is visible in-frame; rendering does not regress (headless PNG works).
- Narrator emits one ambient line on `:event/world-commitment`.
- `architecture-test` green; suite green.

---
Created 2026-07-22 (Claude): child C of The First Narrowing. Depends on the UX
render conventions in docs/designs/ux-architecture.md.

Triage 2026-07-22 (resumed session): Narrowing B done + committed (d230bca) — commitment horizon live, :event/world-commitment firing. Dispatching impl agent for the frame handoff + felt cues. blocked -> in_progress.

Implemented 2026-07-22 (uncommitted, branch m5-ecology-handoff):
- Tether: pure `infra.camera.navigation.tether/tether-step` (strength = binding/capture-threshold, so fully engaged exactly at capture — no jump-cut by construction; `:input-active?` = player wins; lerp-rate re-engagement is gentle). Actuated in `infra.dev.window.loop` manual mode after `update-camera-for-world`. GAP: manual mode only (tracking modes overwrite target/distance every frame).
- Sky-simplification: `cell-cloud-shapes` in `infra.render.scene.bodies` renders every regional statistical cell as a dimmed probability-cloud sprite (0.30 × thermal/composition colour) in the one Phase 0 projection; `classify-body-lod` now honours pre-classified `:sprite` shapes (previously any non-body/line/particle shape fell into the solids pass and NPE'd). GAP: cells do not feed the froxel volume (gas-samples is matter-state-filtered) — sprite-only haze.
- Narrator: `:event/world-commitment` → mood `:tenderness` + ONE ambient utterance (`domain.narrative/commitment-utterance`, attribution :ambient, write-once) in `c/narrative-state :last-line`; surfaced as a dim 600-tick viewport float in `infra.render.hud/observer-hud-text`. No modal/popup/addressed text.
- Tests: +9 tests, +32 assertions (704/13654 green; baseline 695/13622). architecture-test green; write-conflicts {}. Headless PNG: `infra.render/render-to-file` → /tmp/truth-narrowing-c.png (baseline world) and /tmp/truth-narrowing-c-commitment.png (cell + commitment + ambient line), both 1280×720 RGBA.

Complete + independently verified 2026-07-22 (resumed session). +9 tests green; full suite 704/13654 (was 695/13622) 0 failures; architecture green; write-conflicts {}; headless PNG 1280x720 RGBA baseline + commitment worlds both render. Landed: pure tether-step (lerp 0.05 x binding/capture-threshold, reads c/binding only -> capture tick changes nothing, no jump-cut by construction; input wins while active, gentle re-engage; mouse-look untouched); cell-cloud-shapes renders demoted statistical cells as dimmed probability-cloud sprites through existing projection (z-up, true-scale intact) + fixed latent classify-body-lod :sprite NPE; narrator :event/world-commitment -> mood :tenderness + one write-once ambient line (:attribution :ambient, dim viewport float, no modal/popup/addressed text). GAPS docstringed: tether manual-mode only (tracking modes overwrite); cells sprite-only haze (not in froxel volume). in_progress -> done. EPIC COMPLETE.
---