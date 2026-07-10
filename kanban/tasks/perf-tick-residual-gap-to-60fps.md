---
uuid: "perf-tick-residual-gap-to-60fps"
title: "Perf: residual tick-cost gap to the 16.6 ms 60 fps budget @1000"
status: "breakdown"
priority: "P1"
labels: ["perf", "phase0", "spec"]
created_at: "2026-07-10T00:00:00Z"
source: "kanban/tasks/perf-tick-residual-gap-to-60fps.md"
category: "specs"
estimate: 3
---

# Perf: residual tick-cost gap to the 16.6 ms 60 fps budget @1000

> Successor to the closed umbrella `kanban/tasks/perf-60fps-parallel-tick.md`.
> The concrete perf sub-tasks that umbrella tracked have landed:
> `persistent-neighbor-cache.md` (done), `spec-neighbor-cache-fan-out-lane.md`,
> `spec-soa-primitive-array-physics-cache.md`. This card carries only the
> **remaining gap** to a sustained 16.6 ms/tick at 1000 particles.

**Problem:** tick cost at 1000 particles is still above the 60 fps budget after
the cache/SoA/fan-out work. Exact current number must be re-measured — the
ledger holds conflicting figures (~17.6 ms and ~44 ms) taken in different
contexts/dates, so this card must **not** assume one.

**First slice (this is a profiling-then-fix task, not a blind optimization):**
1. `pm2 stop gates-of-truth-dev` (bench hygiene — core contention skews numbers),
   then `bin/bench :phase0` / `bin/bench :profile` to get a current, honest
   per-segment breakdown @500 and @1000. Restart the dev service after.
2. Identify the single largest remaining serial/hot segment.
3. Scope ONE ≤5-point fix against it; if the fix is itself >5, split.

**Done when (plus global DoD):**
- A current profiling breakdown is recorded on this card (segment → ms @1000).
- The next concrete optimization is scoped (or implemented if ≤5) with a
  before/after `bin/bench` delta.
- Physics unchanged: byte-equivalence / windowed-equivalence contract holds
  (see `persistent-neighbor-cache.md` §4 pattern).

---
Triage 2026-07-10: sized 3 but first slice is profiling, not a blind fix. Moved to breakdown to capture current benchmark baseline and scope the single largest hot segment.
---
