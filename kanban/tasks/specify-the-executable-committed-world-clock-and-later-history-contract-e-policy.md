---
uuid: "committed-clock-executable-policy"
title: "Specify the executable committed-world clock and later history contract"
status: "incoming"
type: "task"
priority: "P1"
points: "3"
labels: "design, pacing, narrowing, playable-gate"
parent: "narrowing-commitment-horizon"
category: "tasks"
write-id: "1791317948597-0.pv92uuya2d8inqfv2tm"
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
