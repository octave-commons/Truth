---
category: "specs"
labels: ["specs", "phase0", "myth"]
write-id: "1784745364549-0.qkexof89yvaxxh55opk"
source: "kanban/tasks/phase-0-narrator-mood-ambience.md"
title: "Phase 0 Narrator Mood & Ambience"
priority: "P1"
status: "done"
estimate: "3"
uuid: "phase-0-narrator-mood-ambience"
created_at: "2026-07-10T12:00:00Z"
---

# Phase 0 Narrator Mood & Ambience

> Parent: `kanban/tasks/phase-0-narrator-presence-spec.md`
> Scope: the unbuilt Phase 1 mood/ambience layer from the parent spec.

**Goal:** Give the narrator a mood that shifts with recent events and coherence,
and let that mood drive subtle rendering/audio parameters. No embedded phrasing,
no addressed text, no chat shell.

## Scope

1. Add `law.narrative` schemas.
   - `law.narrative/mood-schema` — keywords: `:wonder`, `:dread`, `:tenderness`,
     `:sterility`, `:anticipation`.
   - `law.narrative/utterance-schema` — placeholder for later phases; not used
     in this slice.
   - `law.narrative/topic-schema` — placeholder for later phases.
2. Add `component/narrative-state` on the observer singleton.
   - Value: `{:mood keyword :last-utterance-tick long :topics #{}}`.
   - Only `:mood` is read/written in this slice.
3. Add `domain.narrative/mood-from-events` (pure).
   - Inputs: events since last tick, current mood, coherence.
   - Outputs: new mood.
   - Rules of thumb:
     - `:stellar-ignition` → `:wonder`.
     - Coherence `< 0.2` and no threshold events → drift toward `:dread`.
     - `detect-phase` returns `:sterile` → `:sterility`.
4. Add `domain.narrative/narrative-system`.
   - Runs after `observer-system`.
   - Updates `c/narrative-state`.
5. Add `infra.render` HUD tint hook.
   - Reads observer's `:mood`.
   - Subtly tints HUD border / background fog.
   - Does not override physics-driven colors.

## Tests

- `mood-wonder-after-ignition`: after a `:stellar-ignition` event, mood becomes
  `:wonder`.
- `mood-dread-as-coherence-fades`: coherence drops below 0.2 with no threshold
  events → mood drifts toward `:dread`.
- `mood-sterile-on-fadeout`: when `detect-phase` returns `:sterile`, mood becomes
  `:sterility`.
- `mood-tint-does-not-crash-render`: rendering continues with each mood.

## Out of scope

- Embedded phrasing (Phase 2).
- Attributed address (Phase 3).
- Chat shell / LLM integration (Phase 4+).

## Done when

- `domain.narrative/mood-from-events` is pure and tested.
- `narrative-system` runs every tick without conflicts.
- Mood drives a subtle HUD/fog tint.
- `clojure -M:test` green.
- `test/architecture_test.clj` passes.
- Parent card updated with link to this residual card.

---
Started 2026-07-10: moving to in_progress. Will inspect domain.arc and current narrative-state placeholder, then add law.narrative schemas, mood pure function, narrative system, and HUD tint hook.

Completed 2026-07-10: implemented narrator mood \u0026 ambience.\n- Added law.narrative with mood/narrative-state schemas.\n- Added :component/narrative-state and initialized it on observer spawn.\n- Created domain.narrative with mood-from-events pure function and narrative-system.\n- Wired narrative-system into domain.arc/tick-genesis after observer-system.\n- Added mood-tint rectangle to infra.render.scene.hud/hud-rects-from-world.\n- Added 5 tests covering wonder, dread, sterility, state write, render crash guard.\n- Verification: clojure -M:test 642 tests/13463 assertions green; architecture-test green; clj-kondo 0 warnings; cljfmt clean; bin/analyze --strict no blocking findings.

Review 2026-07-22 (Claude, verified by review agent): VERDICT PASS. All 6 code criteria met with file:line evidence — law.narrative mood-schema (law/narrative.clj:6-16), c/narrative-state initialized on observer spawn (player/state.clj:18), pure mood-from-events (narrative.clj:23-44), narrative-system wired after observer-system (arc.clj:277), subtle non-overriding HUD tint (hud.clj:44-73), 5 tests green. domain.narrative-test 5/13 green; architecture-test 6/23 green; full suite 642/13463 green at HEAD 8fbd078. Nit (non-blocking): sterility derived from arc-ending rather than a literal detect-phase call; functionally equivalent. Parent link present. review -> done.
---