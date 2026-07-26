---
uuid: "voxel-sculpt-verb-palette-wiring"
title: "Wire god-scale sculpt verbs (uplift/erode/volcanism) to the palette"
status: "review"
priority: "P2"
labels: ["render", "input", "voxel", "integration-debt"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/voxel-sculpt-verb-palette-wiring.md"
category: "specs"
estimate: 2
---

# Wire god-scale sculpt verbs to the palette

> Integration-debt card (2026-07-23 re-triage). `domain.voxel.sculpt/request-op`
> (gated, Resonance-spending, tested) exists but is **called from nowhere** —
> `sculpt.clj`'s own docstring records the gap: "no keymap dispatches
> `request-op` yet."

## Root cause (investigation)
`src/infra/render/input.clj:23-31` `action-palette` has exactly four Phase-0
verbs (Well, Repulsor, Heat, Cool). No uplift/erosion/volcanism. The domain
gate/spend/enqueue path onto `:voxel/sculpt-ops` is complete; only infra
dispatch is missing. (Character-scale mine/construct verbs are a *different*,
honestly-unbuilt tier — see `embodied-character-voxel-mode` [icebox].)

## Done when (player-visible)
- With a committed world and the Phase-1 palette armed, pressing the mapped keys
  for Tectonics/Hydrography/Atmosphere calls `request-op` at the focus point,
  spends Resonance, and — combined with `voxel-band-render-path` — visibly
  changes the rendered terrain (uplift raises / erosion lowers material) within
  a few ticks.

## Scope
Extend `infra/render/input.clj`'s palette (or a Phase-1 palette keyed off
`c/palette`) to dispatch `:uplift :erosion :volcanism` per
`law.voxel/sculpt-verb-schema`; wire the HUD legend the same way `action-palette`
already does.

## Dependencies
`voxel-band-render-path` strongly recommended first — otherwise the effect of a
sculpt op is invisible (only trustable via REPL dump of
`c/voxel-field-diffs`/`c/voxel-edit-diffs`).

## Implementation notes (2026-07-23, Wave 0 build)

- **Keys:** `T` = Uplift (Tectonics), `Shift+T` = Volcanism (Tectonics — the
  G/Shift+G one-ability-two-verbs precedent), `Y` = Erode (Hydrography).
  Collision check vs design §7: T/Shift+T/Y touch nothing — movement
  WASD/Space/LCtrl/LShift, abilities G/Shift+G/H/J, focus arrows + `,`/`.`,
  shell C/R/L/`[`/`]`/Tab/Esc, reserved Q/E/F.
- Dispatch: `infra.render.input/action-palette` entries now carry either
  `:kind` (intervention, agency, unchanged path) or `:sculpt` + `:magnitude`
  (0.5) — sculpt entries enqueue `domain.voxel.sculpt/request-op` directly as
  an intent; the op gates itself on `c/palette`/Resonance/commitment.
- HUD legend (`controls-hud`) renders all seven rows from the same palette;
  sculpt rows show the Resonance op-cost and are lit only when the world's
  `c/palette` is `:planetary` with the verb's ability armed.

## Verification pass (2026-07-24)

Re-verified against the branch; dispatch intact (`T`/`Shift+T`/`Y` →
`request-op`, no keycap collisions, palette-gated HUD rows all covered in
`test/infra/render/input_test.clj`). Full suite green (879 tests / 15486
assertions, 0 failures).

**Stale docstring fixed:** `domain.voxel.sculpt`'s ns docstring still
declared "KNOWN GAP … no keymap dispatches `request-op` yet" — the gap this
card closed. It now records the actual keymap. Left as a docstring rather
than deleted so the gate reasoning survives.

**Seam covered:** added to `test/domain/pilot_resolve_seam_test.clj` — the
half that matters for "fly to it and resolve it" is that `request-op`
derives its `:anchor` from `(:focus-position obs)`, which manual mode pins
to the mote, so WHERE you fly is WHERE you sculpt. Now asserted: approach
the world's +x face and the anchor points +x; approach +z and it points +z
(and the two differ, so it is not a constant). Also asserted that an
uncommitted world makes a keypress a no-op that spends no Resonance — the
loop really is fly → commit → sculpt.

**Not green:** `bin/analyze --strict` fails on 4 pre-existing HARD breaches
in unrelated namespaces, owned by `epic-static-analysis-cleanup`. Not caused
by this card. `src/infra/render/input.clj` is clean in both the breach and
cljfmt lists.
