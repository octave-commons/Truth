---
uuid: "phase-0-narrator-mood-ambience"
title: "Phase 0 Narrator Mood & Ambience"
status: "ready"
priority: "P1"
labels: ["specs", "phase0", "myth"]
created_at: "2026-07-10T12:00:00Z"
source: "kanban/tasks/phase-0-narrator-mood-ambience.md"
category: "specs"
estimate: 3
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
