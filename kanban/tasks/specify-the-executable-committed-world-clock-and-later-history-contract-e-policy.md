---
category: "tasks"
labels: "design, pacing, narrowing, playable-gate"
parent: "narrowing-commitment-horizon"
type: "task"
write-id: "1791349682800-0.t76k2zjuvq13eove9l"
points: "3"
title: "Specify the executable committed-world clock and later history contract"
priority: "P1"
status: "incoming"
uuid: "committed-clock-executable-policy"
created_at: "2026-10-06T20:19:08.597Z"
---

## Context
The existing `narrowing-commitment-horizon` card is DONE, but its own implementation and `law.narrowing/time-lock-schema` explicitly provide a data hook only. Canonical Rheos refuses reopening the terminal state. This follow-up owns the missing executable policy, preserving the existing capture record and historical ledger.

Grounding: `docs/notes/2026-10-06-playable-gate-route-audit.md`, `docs/designs/commitment-and-resonance.md` §5, `docs/designs/multi-timescale-integration.md`, and the master world-generation phase design. Audit source anchors identify the actual pacing, simulation loop, LOD, integrator and player-input owners.

## Outcome
A reviewed, implementable clock contract for the first committed world, reconciled with later accelerated history and the final Gate synchronization threshold.

## Scope
Determine the clock input and whether 1 s/s means elapsed wall time or nominal tick rate; specify the first locked tick and precedence over adaptive pacing, disabled pacing and time slip. Define immediate-neighborhood membership, skipped-body elapsed time, and the relationship between update cadence and time rate. Derive the necessary sub-second flight-input contract without changing the velocity writer. Split subsequent consumer work into independently verifiable slices of at most five points.

Bound this three-point deliverable to the first safe consumer policy. If later asynchronous neighborhood causality needs a new solver or exceeds this size, state that boundary explicitly and propose separate research; do not absorb it into this card or claim the complete local lock is solved.

## Non-goals
No production clock change, new scheduler, enabled test-only LOD, global freeze, velocity reset, Gate event, or historical completion rewrite. Do not implement a speculative asynchronous solver inside this design task.

## Acceptance criteria
- A source-to-owner table identifies the single writer and boundary for each clock decision.
- A decision table covers unlocked, first captured, steady locked, paused/slowed, adaptive-off, time-slip and later historical-progression states, including contradictory existing text and its explicit resolution.
- Dimensional derivations and worked traces cover loaded/uneven wall ticks and skipped updates; elapsed simulated time is neither lost nor double-counted.
- The contract states how the single world remains causally coherent across neighborhoods and when a proposed method requires separate primary numerical research.
- Consumer stories name producer/consumer shapes, deterministic RED scenarios, live acceptance and dependencies; each is at most five points and remains unimplemented pending review.

## Verification
Review against current production source and existing tests; provide executable pure-policy examples or worked traces appropriate to the selected design. Independent review checks timing units and conservation. Research or model examples are labeled, never presented as running game behavior.

## Risks
The existing 1e7-second pacing floor, assumed 60 Hz, dt>=1 flight clamp and LOD skipped-time behavior cannot be repaired by a flag reader alone. A local lock can accidentally make later civilization progression impossible unless the temporal relationship is explicit.

---

Planning refinement from independent review, included while this card is Incoming: bound this three-point deliverable to the first safe consumer policy. If later asynchronous neighborhood causality needs a new solver or exceeds this size, state that boundary explicitly and propose separate research; do not absorb it into this card or claim the complete local lock is solved. This append-only comment supplies supplemental provenance for the supported Markdown authoring step; creation history remains unchanged.

2026-10-07 source and arithmetic continuation: docs/notes/2026-10-07-committed-clock-accounting.md (noteb715ff4878bc053621e44773640e21441fab84ae7b6d86127d9b343de9ccf4f4). At illustrative absolute age4e14s, sixty double additions of1/60s preserve no delta; arc/tick-genesis currently subtracts absolute timestamps for observer elapsed time. The tick tail already accounts for the input dt before installing the next pacing value. Proposed first boundary retains historical epoch separately from exact local elapsed/credit, admits raw monotonic intervals under the governing pause/rate state, permits k steps but consumes only j successfully tick/on-step/published steps, with revision-bound completion. Isolated bb trace conserves140ms active credit through load and9s pause; all-success arithmetic only, no project/native run. Initial ambiguous input naming and intermediate total-label correction are preserved with exact sources/outputs, then independently reviewed final. First consumer proposes shared physical time/full updates, not independent regional fast-forward; contradictory10s/s wording is explicitly proposed for later amendment. First-capture precedence, adaptive-off, slip suppression, pause/backlog and observer precision are stated. Numeric representation/rate changes, host long-stall policy, integration quantum, absolute-reader migration and subsecond flight remain unresolved; Incoming3 body/status unchanged and specification not complete. No productionclock, tests, native lock, save path or implementation admission.

Readability correction for the 2026-10-07 source and arithmetic continuation, following MiMo review 5437719247. This restates the prior comment with normal word and unit spacing. The historical comment and event ledger remain unchanged; this adds no scope or design decision. The card remains Incoming, 3 points, and its canonical body is unchanged.

Grounding: docs/notes/2026-10-07-committed-clock-accounting.md, SHA-256 b715ff4878bc053621e44773640e21441fab84ae7b6d86127d9b343de9ccf4f4. At the illustrative absolute age of 4e14 s, sixty double additions of 1/60 s produce no observed delta. arc/tick-genesis currently subtracts absolute timestamps for observer elapsed time; the tick tail already accounts for the input dt before installing the next pacing value.

The proposed first boundary keeps the historical epoch separate from exact local elapsed time and credit. It admits raw monotonic intervals under the governing pause/rate state, permits k steps, and consumes only j steps successfully completed through tick, on-step and publication, with revision-bound completion. The isolated Babashka trace conserves 140 ms of active credit through load and a 9 s pause. This is an all-success arithmetic example, not a project or native run. The initial ambiguous input naming and intermediate total-label correction remain preserved with their exact sources and outputs; the final version received independent review.

The first consumer proposes shared physical time and full updates. Independent regional fast-forward is not included; the contradictory 10 s/s wording is explicitly proposed for later amendment. First-capture precedence, adaptive-off behavior, slip suppression, pause/backlog and observer precision are stated. Numeric representation and rate changes, host long-stall policy, integration quantum, absolute-reader migration and sub-second flight remain unresolved. The specification is unfinished. No production clock change, production test run, native lock, save path or implementation admission is claimed.

---