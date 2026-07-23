---
uuid: "narrowing-hud-binding-commitment-readout"
title: "Narrowing: HUD binding / commitment readout"
status: "review"
priority: "P1"
labels: ["render", "narrowing", "ux", "integration-debt"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/narrowing-hud-binding-commitment-readout.md"
category: "specs"
estimate: 2
---

# Narrowing: HUD binding / commitment readout

> Integration-debt card (2026-07-23 re-triage). The Narrowing epic is
> domain-complete but the player has **no signal** that binding is accruing or
> that the commitment horizon has been crossed. This is the cheapest link in the
> visibility chain and unblocks live-testing of every other narrowing/voxel card.

## Root cause (investigation)
Grep of `src/infra/render/hud.clj`, `scene/hud.clj`, `menu*.clj`, `inspect*.clj`
for `binding`/`commitment` → **zero hits**. The only observer readout is
`focus-intensity` (`hud.clj:211`). Binding (`c/binding`) accrues invisibly; the
commitment horizon (`c/commitment-state`) is write-once/irreversible
(`domain.narrowing` :288ff) with no confirmation. A player can cross it by
accident with no feedback.

## Done when (player-visible)
- A HUD element shows the deepest candidate world's binding as a bar/percentage
  whenever `c/binding` on the observer is non-empty.
- It switches to a distinct "committed" readout once `c/commitment-state
  :committed` exists, and surfaces the already-emitted `:event/world-commitment`
  ledger event as a transient banner/line.
- Verified live in the pm2 dev window.

## Scope
Purely additive read in `src/infra/render/hud.clj`, following the existing
`observer-base-text` / `ambient-line-entry` pattern. **No change to
`domain.narrowing`.**

## Dependencies
None. Do first — it makes the currently-invisible mechanic legible so the other
cards can be smoke-tested.
