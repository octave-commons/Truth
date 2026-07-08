---
uuid: "phase-0-narrator-presence-spec"
title: "Phase 0 Narrator Presence Spec"
status: "todo"
priority: "P1"
labels: ["specs", "phase0", "myth"]
created_at: "2026-07-02T19:35:28.968199939Z"
source: "kanban/tasks/phase-0-narrator-presence-spec.md"
category: "specs"
---

# Phase 0 Narrator Presence Spec

**Status:** draft  
**Goal:** Add a discoverable AI storyteller to Phase 0 that the player can complete the slice without ever addressing, but that becomes legible as a presence when the player pays attention.  
**Principle:** The narrator exists whether or not the player talks back; it is an interpretive layer over the event ledger, not a drama director that overrides physics.

***

## 1. Background from notes

From `docs/designs/truth-phase-0-stellar-nebula-design.md`:

> The player should be able to complete the slice without ever explicitly using a chat interface.

> The AI storyteller should enter the experience in layers:
> - First as a pattern in the audiovisual field.
> - Then as occasional meaningful phrasing embedded into events.
> - Only later as something recognizable as an addressable presence.

> The AI exists whether or not the player talks back.

Current code (`domain.player/create-observer`) has a `:narrative-seeds {}` placeholder but no system, no phrasing, and no chat shell.

***

## 2. Layers of presence

| Layer | Signal | Trigger | Cost |
|---|---|---|---|
| Ambience | Color, tone, audio texture shifts with regime and coherence | Continuous | None |
| Embedded phrasing | Short text fragments in the HUD or near threshold events | Threshold events (`:stellar-ignition`, `:planet-formation`, `:phase-transition`) | None |
| Attributed address | The narrator speaks as "we" or "I" about what the player is witnessing | Sustained focus on a single region + high coherence | Low |
| Chat shell | Player can type a question or response; narrator answers from the ledger | Player presses a bound key | Requires UI scaffolding |

Layer 4 (chat shell) is explicitly out of scope for the first implementation.

***

## 3. Data model

### 3.1 New components

```clojure
;; On the observer singleton
(def narrative-state :component/narrative-state) ;; {:mood keyword :last-utterance-tick long :topics #{}}

;; On threshold events (already emitted by domain.phase0)
;; Events carry :event/type and :event/payload; narrator reads them.
```

### 3.2 law additions

- `law.narrative/mood-schema` — keywords like `:wonder`, `:dread`, `:tenderness`, `:sterility`, `:anticipation`.
- `law.narrative/utterance-schema` — map with `:text`, `:attribution` (`:ambient`/`:embedded`/`:addressed`), `:topic`, `:tick`, `:context`.
- `law.narrative/topic-schema` — e.g. `:collapse`, `:ignition`, `:disc`, `:planet`, `:decoherence`, `:drift`.

***

## 4. Implementation plan

### Phase 1 — Ambience mood

**Goal:** The narrator has a mood that shifts based on recent events and coherence, and that mood drives small rendering/audio parameters.

**Tests:**
- `mood-wonder-after-ignition`: after a `:stellar-ignition` event, mood becomes `:wonder`.
- `mood-dread-as-coherence-fades`: when coherence drops below 0.2 and no threshold events occur, mood drifts toward `:dread`.
- `mood-sterile-on-fadeout`: when `detect-phase` returns `:sterile`, mood becomes `:sterility`.

**Implementation:**
- `domain.narrative/mood-from-events` — pure function from `(events-since-last-tick, current-mood, coherence)` to new mood.
- `domain.narrative/narrative-system` — runs after `observer-system`, updates `c/narrative-state`.
- `infra.render` reads mood to tint the HUD border color and background fog subtly.

### Phase 2 — Embedded phrasing

**Goal:** Threshold events produce one short phrase, chosen from a topic table, displayed briefly in the HUD.

**Tests:**
- `ignition-emits-phrase`: a `:stellar-ignition` event yields an utterance whose topic is `:ignition`.
- `no-spam`: multiple events of the same type within 10 ticks produce at most one utterance.
- `phrase-context-contains-body-count`: utterance context includes the current number of resolved bodies.

**Implementation:**
- `domain.narrative/utterance-for-event` — pure function `(event, world-context) → utterance or nil`.
- Topic tables are simple maps of `:topic → [phrase-template …]`. Templates use `clojure.pprint/cl-format`-style slots filled from context.
- `infra.render` displays the most recent embedded utterance in the HUD for a few seconds.

### Phase 3 — Attributed address

**Goal:** When the player maintains tight focus and high coherence for a sustained period, the narrator speaks directly about the focused region.

**Tests:**
- `address-triggered-by-sustained-focus`: tight focus (< 1e13 m radius) for 50+ ticks with coherence > 0.6 triggers an `:addressed` utterance.
- `address-topic-matches-region`: an addressed utterance references the dominant regime or largest body in the focus volume.
- `no-address-while-coherence-low`: addressed utterances are suppressed when coherence < 0.3.

**Implementation:**
- `domain.narrative/address-trigger?` — checks observer focus history and coherence.
- `domain.narrative/address-utterance` — generates a sentence about the focus region using `domain.regime/classify` output and the largest body's matter-state.

***

## 5. Rendering / UI

- Embedded phrases appear as translucent text near the bottom center of the HUD, fading after 4 seconds.
- Addressed utterances use a slightly different color/font weight and stay on screen longer (8 seconds).
- Mood drives subtle tinting of the HUD and fog, never overriding the physics-driven colors.
- No explicit chat box in Phase 1–3.

***

## 6. Out of scope

- Chat shell / free-text response — requires LLM integration in `infra.myth-engine`, which is a later phase.
- Procedural voice audio — belongs to a future audio/sound-design spec.
- Myth Engine / Scribes / Facets persistence — those are cross-phase systems; this spec is only Phase 0 presence.

***

## 7. First deliverable

**Phase 1** (ambience mood) is the smallest step. It adds a pure mood function, a narrative system, and a HUD tint hook. It proves the narrator is present without requiring any text generation infrastructure.

Next action: approve this spec, then write schemas, failing tests, and Phase 1 implementation.
