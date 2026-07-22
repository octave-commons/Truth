---
uuid: "narrowing-frame-handoff"
title: "Narrowing C: camera/frame handoff + felt cues"
status: "blocked"
priority: "P2"
labels: ["specs", "phase1", "player", "ui", "narrowing", "epic-the-first-narrowing"]
created_at: "2026-07-22T00:00:00Z"
source: "kanban/tasks/narrowing-frame-handoff.md"
category: "specs"
estimate: 3
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
---
