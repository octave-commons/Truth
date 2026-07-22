---
category: "specs"
labels: ["phase0", "handoff", "epic-ecology-water-gate-snowline"]
write-id: "1784747761813-0.34vcyd4we27c2cm3pkj"
source: "kanban/tasks/ecology-m5-phase4-handoff-event.md"
title: "M5 Handoff Phase 4: planet-candidate record + handoff event"
priority: "P2"
status: "ready"
estimate: "5"
uuid: "ecology-m5-phase4-handoff-event"
created_at: "2026-07-10T00:00:00Z"
---

# M5 Handoff Phase 4: planet-candidate record + handoff event

> Parent spec: `kanban/tasks/ecology-water-gate-snowline.md` (§2, §5, §6 Phase 4)
> Parent kanban: `kanban/tasks/ecology-water-gate-snowline.md`

Assemble the canonical `:planet-candidate` output record and emit it as a
`:phase0-handoff` event when the handoff criteria are met. Depends on Phases
1–3 (material/thermal class, orbit stability, atmosphere class).

**Scope:**
- Add `domain.genesis/handoff-system` (NOT `domain.phase0` — renamed) wired into
  `genesis/physics-systems-parallel` as a fan-out emitter, sole writer of the
  handoff/candidate components.
- Build the full `:planet-candidate` record (parent §5): ids, class, thermal
  band, orbit, atmosphere, bulk composition, angular momentum, rotation axis,
  surface gravity, dynamo estimate, formation events.
- Gate emission on the handoff criteria (parent §2: stable star + ≥1 candidate +
  dynamically settled).
- Append `:phase0-handoff` event to the ledger; update `world-ending` to
  distinguish `:success` from `:sterile`/`:dispersal`/`:fadeout`.
- Downstream **consumer** stays capability-gated (D-consumer) — Phase 1 only.

**Done when (plus global DoD):**
- Tests: `handoff-emits-when-star-and-planet-exist`,
  `handoff-record-contains-required-keys`, `sterile-ending-does-not-emit-handoff`.
- Single-writer preserved; `reg/write-conflicts` empty; `architecture-test` green.

---
Triage 2026-07-10: scoped 5pt but depends on phase1-3. Moved to breakdown until dependencies are in progress/done.

Triage 2026-07-10: sized 5pt but depends on ecology-m5-phase1/2/3. Moved to blocked until dependencies advance.

Unblocked 2026-07-22 (Claude): dependencies satisfied — Phases 1 (material+thermal class), 2 (orbit-stable?), and 3 (atmosphere-class + retained-species) are all DONE and committed (c1b88c5, a73b483) on branch m5-ecology-handoff. This card now has the component inputs it needs to assemble the :planet-candidate record and emit :phase0-handoff. blocked -> ready. NOT started per Aaron's 'stop before the handoff-event' decision — awaiting go-ahead. When picked up: build the candidate record from material-class/thermal-band/orbit-stable?/atmosphere-class/retained-species + composition/angular-momentum, gate on handoff criteria (parent §2), emit as a single-writer fan-out emitter, and distinguish :success from :sterile/:dispersal/:fadeout endings.
---