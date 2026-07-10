---
uuid: "perf-60fps-parallel-tick"
title: "60 fps: closing the gap between the Jacobi architecture and the wall clock"
status: "done"
priority: "P0"
labels: ["perf", "phase0", "spec"]
created_at: "2026-07-03T00:00:00Z"
source: "kanban/tasks/perf-60fps-parallel-tick.md"
category: "specs"
---

# 60 fps: closing the gap between the Jacobi architecture and the wall clock

Tracks `kanban/tasks/tick-perf-drift-profile.md`.

> **Status update (2026-07-10, Claude Code — code-state review):** Still
> genuinely `in_progress` — the 16.6 ms/tick target is not yet met. The tracked
> `tick-perf-drift-profile.md` is now `done` (Fixes 1–6 landed). Perf work that
> has since landed on `main` and moved the needle: the persistent neighbor cache
> (`persistent-neighbor-cache.md`, ~79% cheaper steady-state rebuilds, @1000
> 53.6 → 44.0 ms), its migration to a registry fan-out lane
> (`spec-neighbor-cache-fan-out-lane.md`), and the SoA gravity/kinematics cache
> split (`spec-soa-primitive-array-physics-cache.md`). This card remains the
> open umbrella for the residual gap to 60 fps @1000.

---
2026-07-10 → closed as umbrella. Concrete perf sub-tasks landed (persistent-neighbor-cache done; SoA cache + neighbor fan-out lane in main). 60fps@1000 not yet met; residual gap tracked by new card perf-tick-residual-gap-to-60fps.md (profile-then-fix). This vague umbrella retired to keep the board honest.
---
