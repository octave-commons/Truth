---
category: "specs"
labels: ["phase0", "handoff", "epic-ecology-water-gate-snowline"]
write-id: "1784746480557-0.8j1q4p2672lux316c47"
source: "kanban/tasks/ecology-m5-phase2-orbit-stability.md"
title: "M5 Handoff Phase 2: orbit stability"
priority: "P2"
status: "done"
estimate: "3"
uuid: "ecology-m5-phase2-orbit-stability"
created_at: "2026-07-10T00:00:00Z"
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

---
Triage 2026-07-10: scoped 3pt, analytic proxy only. Ready for implementation.

Triage 2026-07-22 (Claude): Phase 1 landed (done); dispatching Sonnet impl agent for this analytic-proxy stability slice. ready -> in_progress.

Implementation complete + independently verified 2026-07-22 (Claude). domain.orbital-stability-test + classification tests 10/19 green; architecture-test 6/23 green; full suite 652/13482 (was 648/13475) 0 failures; reg/write-conflicts {} via REPL. Landed: pure domain.orbital.stability/orbit-stability (vis-viva two-body elements; periapsis>R_star+5R_star, apoapsis<100AU, no approach within 10 Hill radii using law.stellar/hill-radius; unbound=unstable). Design: FOLDED into :classification system (now sole writer of orbit-stable? too) rather than a second system — reuses the same candidate scan + star lookup, keeps reads minimal, write-conflicts empty. softened-circular-speed used in test fixtures; plain vis-viva inside the proxy is valid since the periapsis floor sits outside any Plummer softening length (documented). Committed c1b88c5 on branch m5-ecology-handoff. in_progress -> done. Unblocks phase4 (with phase3).
---