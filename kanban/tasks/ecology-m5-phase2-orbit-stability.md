---
uuid: "ecology-m5-phase2-orbit-stability"
title: "M5 Handoff Phase 2: orbit stability"
status: "accepted"
priority: "P2"
labels: ["phase0", "handoff", "epic-ecology-water-gate-snowline"]
created_at: "2026-07-10T00:00:00Z"
source: "kanban/tasks/ecology-m5-phase2-orbit-stability.md"
category: "specs"
estimate: 3
---

# M5 Handoff Phase 2: orbit stability

> Parent spec: `kanban/tasks/ecology-water-gate-snowline.md` (§3.3, §6 Phase 2)
> Parent kanban: `kanban/tasks/ecology-water-gate-snowline.md`

Mark each candidate planet `:orbit-stable?` using an **analytic proxy** (not a
full 10-Myr two-body integration — parent §6 Phase 4 note explicitly defers the
integration behind the proxy proving too coarse).

**Scope:**
- Add `domain.orbital.stability/orbit-stability` (pure): periapsis > star radius
  + 5 stellar radii; apoapsis < 100 AU; no close approach within 10 Hill radii
  of another candidate.
- Run it inside `classify-system` (or a small `stability-system`) as a fan-out
  emitter writing `:component/orbit-stable?`.
- Use `law.stellar/softened-circular-speed` where orbital speeds are needed
  (softened-field spawn-orbit rule).

**Done when (plus global DoD):**
- Tests: `circular-orbit-is-stable`, `plunging-orbit-is-unstable`,
  `close-planet-pair-is-unstable`.
- Single-writer preserved; `architecture-test` green.
